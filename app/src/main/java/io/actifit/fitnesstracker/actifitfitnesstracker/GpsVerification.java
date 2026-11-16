package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * "GPS-verified" check for a recorded route: do the steps counted during the route's time
 * window agree with the distance GPS measured?
 *
 * Implied step length = GPS distance / steps in the route window. A real walk or run lands
 * inside a plausible band; a route recorded in a car or on a bike (few steps, long distance)
 * or a phone shaken in place (many steps, no distance) falls outside it.
 *
 * Steps come from the 15-minute activity slots (HHmm), which only say how many steps fell in
 * each quarter hour, not when. A slot partly covered by the route is weighted by the covered
 * fraction (overlap minutes / 15), i.e. we assume steps were spread evenly over the slot. That
 * is unbiased when activity is steady, but the estimate can go either way:
 *  - steps taken in the slot OUTSIDE the route (e.g. walking to the car) still leak in, which
 *    shortens the step length;
 *  - when all the slot's steps were INSIDE the route (the usual case for a walk), weighting
 *    undercounts them, which lengthens the step length. For a route of 15+ minutes the worst
 *    case is about half of the true count (15 min split evenly over two slots), 25% at 30 min.
 * The minimum duration keeps that error bounded; the band is wide for the same reason. The
 * pure helpers (evaluate, stepsInWindow, stepsForRoute, evaluateRoute, postBodyLine,
 * suppressStaleHcMismatch) are covered by GpsVerificationTest. verifyRoute and
 * bestVerifiedRouteForDate read the DB: call them off the main thread.
 */
public final class GpsVerification {

    /**
     * Verification thresholds, all in one place.
     *
     *  - MIN_STEP_LENGTH_M 0.4 m / MAX_STEP_LENGTH_M 1.6 m: plausible step length band. Normal
     *    walking is ~0.6-0.85 m and a fast run ~1.0-1.5 m; the margins absorb slot weighting
     *    error (see class comment) and GPS drift, which tends to add distance.
     *  - MIN_DISTANCE_M 500 m and MIN_STEPS 300: below either, the route is "not enough data"
     *    (GPS noise and slot error dominate) ...
     *  - DRIVE_DISTANCE_M 1 km: ... except that a route of 1 km or more with fewer than
     *    MIN_STEPS steps is a MISMATCH, not "not enough data". Under 300 steps for 1 km means a
     *    step length over 3.3 m: the phone travelled without walking (car, bus, bike).
     *  - MIN_DURATION_MS 15 min: shorter routes get no verdict at all, because slot weighting
     *    error grows quickly below one slot length.
     *  - HC_SETTLE_MS 30 min: in Health Connect mode, a MISMATCH is only shown once the app's
     *    last HC sync happened at least this long after the route ended. Before that the HC
     *    slots may not include the route yet (the source app writes to HC with a delay), and a
     *    missing step count would read as "travelled without walking".
     */
    public static final double MIN_STEP_LENGTH_M = 0.4;
    public static final double MAX_STEP_LENGTH_M = 1.6;
    public static final double MIN_DISTANCE_M = 500;
    public static final int MIN_STEPS = 300;
    public static final double DRIVE_DISTANCE_M = 1000;
    public static final long MIN_DURATION_MS = 15 * 60 * 1000L;
    public static final long HC_SETTLE_MS = 30 * 60 * 1000L;

    /** Length of one activity slot in minutes (see StepsDBHelper.getTimeSlot). */
    static final int SLOT_MINUTES = 15;

    private static final double METERS_PER_MILE = 1609.344;

    /** Route activity types (from MainActivity's picker) where steps are expected. */
    private static final Set<String> STEP_BASED_TYPES = new HashSet<>(Arrays.asList(
            "walking", "running", "hiking", "jogging", "geocaching", "photowalking", "plogging"));

    public enum Status {
        /** Steps and GPS distance agree. */
        VERIFIED,
        /** Enough data, but the implied step length is implausible. */
        MISMATCH,
        /** Route too short / too few steps / no step data to judge. */
        NOT_ENOUGH_DATA,
        /** Not a GPS route, or an activity without steps (cycling, kayaking, ...). */
        NOT_APPLICABLE
    }

    public static final class Result {
        public final Status status;
        public final double distanceMeters;
        public final int steps;
        public final int routeMinutes;

        Result(Status status, double distanceMeters, int steps, int routeMinutes) {
            this.status = status;
            this.distanceMeters = distanceMeters;
            this.steps = steps;
            this.routeMinutes = routeMinutes;
        }

        public boolean isVerified() {
            return status == Status.VERIFIED;
        }
    }

    private GpsVerification() {}

    /** Pure verdict from GPS distance, steps in the route window and route duration. */
    public static Status evaluate(double distanceMeters, int steps, long durationMs) {
        if (durationMs < MIN_DURATION_MS) {
            return Status.NOT_ENOUGH_DATA;
        }
        if (distanceMeters >= DRIVE_DISTANCE_M && steps < MIN_STEPS) {
            return Status.MISMATCH; // travelled a long way with (almost) no steps
        }
        if (distanceMeters < MIN_DISTANCE_M || steps < MIN_STEPS) {
            return Status.NOT_ENOUGH_DATA;
        }
        double stepLength = distanceMeters / steps;
        if (stepLength >= MIN_STEP_LENGTH_M && stepLength <= MAX_STEP_LENGTH_M) {
            return Status.VERIFIED;
        }
        return Status.MISMATCH;
    }

    /** True when the activity type is one where a step count is expected (null = unknown = yes). */
    public static boolean isStepBased(String activityType) {
        if (activityType == null || activityType.trim().isEmpty()) return true;
        return STEP_BASED_TYPES.contains(activityType.trim().toLowerCase(Locale.ENGLISH));
    }

    /**
     * Weighted step count for the window [startMinute, endMinute) of one day (minutes of the
     * day, fractional, end exclusive). A slot "HHmm" covers [HH*60+mm, HH*60+mm+15) and
     * contributes count * overlapMinutes / 15.
     */
    public static double stepsInWindow(List<ActivitySlot> slots, double startMinute, double endMinute) {
        if (slots == null || endMinute <= startMinute) return 0;
        double total = 0;
        for (ActivitySlot s : slots) {
            if (s == null || s.slot == null || s.activityCount <= 0) continue;
            int slotStart;
            try {
                int hhmm = Integer.parseInt(s.slot.trim());
                slotStart = (hhmm / 100) * 60 + (hhmm % 100);
            } catch (NumberFormatException e) {
                continue;
            }
            double overlap = Math.min(slotStart + SLOT_MINUTES, endMinute)
                    - Math.max(slotStart, startMinute);
            if (overlap > 0) {
                total += s.activityCount * overlap / SLOT_MINUTES;
            }
        }
        return total;
    }

    /** The yyyyMMdd day keys covered by [startMs, endMs), or an empty list for > 2 days. */
    public static List<String> dayKeys(long startMs, long endMs, TimeZone tz) {
        List<String> keys = new ArrayList<>();
        Calendar start = calendar(startMs, tz);
        String startKey = dayKey(start);
        keys.add(startKey);
        String endKey = dayKey(calendar(Math.max(startMs, endMs - 1), tz));
        if (!endKey.equals(startKey)) {
            Calendar next = (Calendar) start.clone();
            next.add(Calendar.DATE, 1);
            if (!dayKey(next).equals(endKey)) {
                return new ArrayList<>(); // longer than a day: not a single walk/run
            }
            keys.add(endKey);
        }
        return keys;
    }

    /**
     * Weighted steps in [startMs, endMs), split across midnight when needed.
     * slotsByDay maps yyyyMMdd to that day's slots. Returns -1 when the window spans more
     * than two calendar days or is empty.
     */
    public static int stepsForRoute(long startMs, long endMs,
                                    Map<String, List<ActivitySlot>> slotsByDay, TimeZone tz) {
        if (endMs <= startMs) return -1;
        List<String> days = dayKeys(startMs, endMs, tz);
        if (days.isEmpty()) return -1;
        double startMin = minuteOfDay(calendar(startMs, tz));
        Calendar end = calendar(endMs, tz);
        // a route ending exactly at midnight ends at minute 1440 of its last day
        double endMin = dayKey(end).equals(days.get(days.size() - 1)) ? minuteOfDay(end) : 24 * 60;
        double steps;
        if (days.size() == 1) {
            steps = stepsInWindow(slotsByDay.get(days.get(0)), startMin, endMin);
        } else {
            steps = stepsInWindow(slotsByDay.get(days.get(0)), startMin, 24 * 60)
                    + stepsInWindow(slotsByDay.get(days.get(1)), 0, endMin);
        }
        return (int) Math.round(steps);
    }

    /** Pure verification of a route's fields against its days' slots. */
    public static Result evaluateRoute(String activityType, String sourceType, long startMs,
                                       long endMs, double distanceMeters,
                                       Map<String, List<ActivitySlot>> slotsByDay, TimeZone tz) {
        long durationMs = Math.max(0, endMs - startMs);
        int minutes = (int) Math.round(durationMs / 60000.0);
        if (!RouteModel.SOURCE_GPS.equals(sourceType) || !isStepBased(activityType)) {
            return new Result(Status.NOT_APPLICABLE, distanceMeters, 0, minutes);
        }
        int steps = stepsForRoute(startMs, endMs, slotsByDay, tz);
        if (steps < 0) {
            return new Result(Status.NOT_ENOUGH_DATA, distanceMeters, 0, minutes);
        }
        return new Result(evaluate(distanceMeters, steps, durationMs), distanceMeters, steps, minutes);
    }

    /**
     * In Health Connect mode a MISMATCH is downgraded to NOT_ENOUGH_DATA until the last HC
     * sync happened at least HC_SETTLE_MS after the route ended (see thresholds).
     */
    public static Status suppressStaleHcMismatch(Status status, boolean useHealthConnect,
                                                 long routeEndMs, long hcLastSyncMs) {
        if (status == Status.MISMATCH && useHealthConnect
                && hcLastSyncMs < routeEndMs + HC_SETTLE_MS) {
            return Status.NOT_ENOUGH_DATA;
        }
        return status;
    }

    /**
     * The line appended to the post body. Always English with a dot decimal (owner decision:
     * the on-chain post text is not localized); km/mi follows the user's unit setting.
     */
    public static String postBodyLine(double distanceMeters, boolean metric) {
        String distance;
        if (metric) {
            distance = distanceMeters < 1000
                    ? String.format(Locale.US, "%.0f m", distanceMeters)
                    : String.format(Locale.US, "%.2f km", distanceMeters / 1000.0);
        } else {
            distance = String.format(Locale.US, "%.2f mi", distanceMeters / METERS_PER_MILE);
        }
        return "📍 GPS-verified route: " + distance; // 📍
    }

    /** Whether the user's step data comes from Health Connect (HC slot table) or the device. */
    public static boolean usesHealthConnect(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("actifitSets", Context.MODE_PRIVATE);
        String mode = prefs.getString("dataTrackingSystem",
                ctx.getString(R.string.device_tracking_ntt));
        return ctx.getString(R.string.health_connect_tracking_ntt).equals(mode);
    }

    /** Time of the last successful Health Connect sync (set by TrackingManager), 0 if never. */
    public static long hcLastSyncMs(Context ctx) {
        return ctx.getSharedPreferences("actifitSets", Context.MODE_PRIVATE)
                .getLong("healthConnectLastSyncTime", 0L);
    }

    /**
     * Verifies a stored route against the step slots of its day(s). Reads the DB: never call
     * on the main thread. hcLastSyncMs is only used in Health Connect mode.
     */
    public static Result verifyRoute(StepsDBHelper db, RouteModel route, boolean useHealthConnect,
                                     long hcLastSyncMs) {
        if (route == null) return new Result(Status.NOT_APPLICABLE, 0, 0, 0);
        TimeZone tz = TimeZone.getDefault();
        Map<String, List<ActivitySlot>> slotsByDay = new HashMap<>();
        boolean applicable = RouteModel.SOURCE_GPS.equals(route.sourceType)
                && isStepBased(route.activityType);
        if (db != null && applicable) {
            for (String day : dayKeys(route.startTimeMs, route.endTimeMs, tz)) {
                slotsByDay.put(day, useHealthConnect
                        ? db.fetchHCDateTimeSlotActivity(day) : db.fetchDateTimeSlotActivity(day));
            }
        }
        Result r = evaluateRoute(route.activityType, route.sourceType, route.startTimeMs,
                route.endTimeMs, route.distanceMeters, slotsByDay, tz);
        Status s = suppressStaleHcMismatch(r.status, useHealthConnect, route.endTimeMs, hcLastSyncMs);
        return s == r.status ? r : new Result(s, r.distanceMeters, r.steps, r.routeMinutes);
    }

    /**
     * The longest GPS-verified route recorded on a date (yyyyMMdd), or null if none.
     * Reads the DB: never call on the main thread.
     */
    public static Result bestVerifiedRouteForDate(StepsDBHelper db, int date, boolean useHealthConnect) {
        if (db == null) return null;
        Result best = null;
        for (RouteModel route : db.getRoutesForDate(date)) {
            // only VERIFIED matters here, so the HC mismatch suppression is irrelevant
            Result r = verifyRoute(db, route, useHealthConnect, Long.MAX_VALUE / 2);
            if (r.isVerified() && (best == null || r.distanceMeters > best.distanceMeters)) {
                best = r;
            }
        }
        return best;
    }

    private static Calendar calendar(long ms, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(ms);
        return c;
    }

    private static double minuteOfDay(Calendar c) {
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
                + c.get(Calendar.SECOND) / 60.0 + c.get(Calendar.MILLISECOND) / 60000.0;
    }

    private static String dayKey(Calendar c) {
        return String.format(Locale.ENGLISH, "%04d%02d%02d", c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
}
