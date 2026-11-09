package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "GPS-verified" check for a recorded route: do the steps counted during the route's time
 * window agree with the distance GPS measured?
 *
 * Implied step length = GPS distance / steps in the route window. A real walk or run lands
 * inside a plausible band; a route recorded in a car or on a bike (few steps, long distance)
 * or a phone shaken in place (many steps, no distance) falls outside it.
 *
 * Steps come from the 15-minute activity slots (HHmm). A slot is counted when it overlaps the
 * route window at all, so the step total can include a few minutes before/after the route.
 * That only ever pushes the step length DOWN, which is why the lower bound is generous.
 *
 * The pure methods ({@link #evaluate}, {@link #stepsInWindow}) have no Android dependencies
 * and are covered by GpsVerificationTest. {@link #verifyRoute} reads the DB: call it off the
 * main thread.
 */
public final class GpsVerification {

    /**
     * Verification thresholds, all in one place.
     *
     *  - MIN_STEP_LENGTH_M 0.4 m: below normal walking (~0.6-0.85 m) to absorb slot rounding
     *    (extra steps from the partially overlapped 15-min slots) and short shuffling steps.
     *  - MAX_STEP_LENGTH_M 1.6 m: above a fast run (~1.0-1.5 m) to absorb GPS drift, which
     *    tends to add distance.
     *  - MIN_DISTANCE_M 200 m and MIN_STEPS 300: tiny routes are dominated by GPS noise and
     *    slot rounding, so they are reported as "not enough data" instead of a verdict.
     */
    public static final double MIN_STEP_LENGTH_M = 0.4;
    public static final double MAX_STEP_LENGTH_M = 1.6;
    public static final double MIN_DISTANCE_M = 200;
    public static final int MIN_STEPS = 300;

    /** Length of one activity slot in minutes (see StepsDBHelper.getTimeSlot). */
    static final int SLOT_MINUTES = 15;

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

    /** Pure verdict from GPS distance and the steps counted in the route window. */
    public static Status evaluate(double distanceMeters, int steps) {
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
     * Sums slot counts for slots overlapping [startMinute, endMinute] (minutes of the day,
     * inclusive). A slot "HHmm" covers [HH*60+mm, HH*60+mm+15).
     */
    public static int stepsInWindow(List<ActivitySlot> slots, int startMinute, int endMinute) {
        if (slots == null || endMinute < startMinute) return 0;
        int total = 0;
        for (ActivitySlot s : slots) {
            if (s == null || s.slot == null || s.activityCount <= 0) continue;
            int slotStart;
            try {
                int hhmm = Integer.parseInt(s.slot.trim());
                slotStart = (hhmm / 100) * 60 + (hhmm % 100);
            } catch (NumberFormatException e) {
                continue;
            }
            if (slotStart <= endMinute && slotStart + SLOT_MINUTES > startMinute) {
                total += s.activityCount;
            }
        }
        return total;
    }

    /** Whether the user's step data comes from Health Connect (HC slot table) or the device. */
    public static boolean usesHealthConnect(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("actifitSets", Context.MODE_PRIVATE);
        String mode = prefs.getString("dataTrackingSystem",
                ctx.getString(R.string.device_tracking_ntt));
        return ctx.getString(R.string.health_connect_tracking_ntt).equals(mode);
    }

    /**
     * Verifies a stored route against the step slots of its day(s). Reads the DB: never call
     * on the main thread.
     */
    public static Result verifyRoute(StepsDBHelper db, RouteModel route, boolean useHealthConnect) {
        if (route == null) return new Result(Status.NOT_APPLICABLE, 0, 0, 0);
        int minutes = (int) Math.round(route.getDurationMs() / 60000.0);
        if (!RouteModel.SOURCE_GPS.equals(route.sourceType) || !isStepBased(route.activityType)) {
            return new Result(Status.NOT_APPLICABLE, route.distanceMeters, 0, minutes);
        }
        if (db == null || route.getDurationMs() <= 0) {
            return new Result(Status.NOT_ENOUGH_DATA, route.distanceMeters, 0, minutes);
        }

        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(route.startTimeMs);
        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(route.endTimeMs);
        String startDay = dayKey(start);
        String endDay = dayKey(end);
        int startMin = start.get(Calendar.HOUR_OF_DAY) * 60 + start.get(Calendar.MINUTE);
        int endMin = end.get(Calendar.HOUR_OF_DAY) * 60 + end.get(Calendar.MINUTE);

        int steps;
        if (startDay.equals(endDay)) {
            steps = stepsInWindow(slotsFor(db, startDay, useHealthConnect), startMin, endMin);
        } else {
            Calendar nextDay = (Calendar) start.clone();
            nextDay.add(Calendar.DATE, 1);
            if (!dayKey(nextDay).equals(endDay)) {
                // longer than a day: not a single walk/run we can judge
                return new Result(Status.NOT_ENOUGH_DATA, route.distanceMeters, 0, minutes);
            }
            steps = stepsInWindow(slotsFor(db, startDay, useHealthConnect), startMin, 24 * 60 - 1)
                    + stepsInWindow(slotsFor(db, endDay, useHealthConnect), 0, endMin);
        }
        return new Result(evaluate(route.distanceMeters, steps), route.distanceMeters, steps, minutes);
    }

    /**
     * The longest GPS-verified route recorded on a date (yyyyMMdd), or null if none.
     * Reads the DB: never call on the main thread.
     */
    public static Result bestVerifiedRouteForDate(StepsDBHelper db, int date, boolean useHealthConnect) {
        if (db == null) return null;
        Result best = null;
        for (RouteModel route : db.getRoutesForDate(date)) {
            Result r = verifyRoute(db, route, useHealthConnect);
            if (r.isVerified() && (best == null || r.distanceMeters > best.distanceMeters)) {
                best = r;
            }
        }
        return best;
    }

    private static List<ActivitySlot> slotsFor(StepsDBHelper db, String day, boolean hc) {
        return hc ? db.fetchHCDateTimeSlotActivity(day) : db.fetchDateTimeSlotActivity(day);
    }

    private static String dayKey(Calendar c) {
        return String.format(Locale.ENGLISH, "%04d%02d%02d", c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
}
