package io.actifit.fitnesstracker.actifitfitnesstracker;

import androidx.health.connect.client.records.ExerciseSessionRecord;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure helpers for Health Connect workouts (exercise sessions).
 *
 * Workouts are shown on the dashboard and in the report, and stored in the post metadata, but
 * they NEVER count toward rewards. Activity keys are the English string-resource names used by
 * PostSteemitActivity's activity list (e.g. "Cycling", "WeightLifting"). The post service
 * whitelists exactly {@link #ALL_KEYS}, so keep the two in sync.
 *
 * Never add a "workout_minutes" field anywhere: the bot's arena scoring reads that name.
 */
public final class WorkoutMapper {

    private WorkoutMapper() {
    }

    public static final long MIN_DURATION_MS = 5 * 60_000L;
    public static final long MAX_DURATION_MS = 24 * 60 * 60_000L;
    /** The post service accepts at most this many entries. */
    public static final int MAX_WORKOUTS = 10;
    public static final long MAX_DISTANCE_M = 300_000L;

    public static final String FALLBACK_KEY = "DailyActivity";

    private static final Map<Integer, String> TYPE_TO_KEY = new HashMap<>();
    private static final Map<String, String> KEY_TO_LABEL = new HashMap<>();

    static {
        map("Cycling", ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
                ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY);
        map("Swimming", ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
                ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER);
        map("WeightLifting", ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING);
        map("Yoga", ExerciseSessionRecord.EXERCISE_TYPE_YOGA);
        map("Stretching", ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
                ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING);
        map("Bootcamp", ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
                ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP);
        map("Elliptical", ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL);
        map("Gym", ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
                ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS);
        map("Hiking", ExerciseSessionRecord.EXERCISE_TYPE_HIKING);
        map("Walking", ExerciseSessionRecord.EXERCISE_TYPE_WALKING);
        map("Running", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING);
        map("Treadmill", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL);
        map("StairClimbing", ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING);
        map("StairMill", ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE);
        map("Dancing", ExerciseSessionRecord.EXERCISE_TYPE_DANCING);
        map("Basketball", ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL);
        map("Football", ExerciseSessionRecord.EXERCISE_TYPE_SOCCER,
                ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN,
                ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN);
        map("Boxing", ExerciseSessionRecord.EXERCISE_TYPE_BOXING);
        map("Tennis", ExerciseSessionRecord.EXERCISE_TYPE_TENNIS);
        map("TableTennis", ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS);
        map("MartialArts", ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS);
        map("Golf", ExerciseSessionRecord.EXERCISE_TYPE_GOLF);
        map("Volleyball", ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL);
        map("Skiing", ExerciseSessionRecord.EXERCISE_TYPE_SKIING);
        map("Skating", ExerciseSessionRecord.EXERCISE_TYPE_SKATING,
                ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING);
        map("Hockey", ExerciseSessionRecord.EXERCISE_TYPE_ICE_HOCKEY,
                ExerciseSessionRecord.EXERCISE_TYPE_ROLLER_HOCKEY);
        map("Badminton", ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON);
        map("Snowshoeing", ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING);
        map("Sailing", ExerciseSessionRecord.EXERCISE_TYPE_SAILING);
        map("Kayaking", ExerciseSessionRecord.EXERCISE_TYPE_PADDLING);
        map("Calisthenics", ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS);
        map("Cricket", ExerciseSessionRecord.EXERCISE_TYPE_CRICKET);

        // English display labels (values/strings.xml) for the English post body line
        String[][] labels = {
                {"WeightLifting", "Weight Lifting"}, {"StairClimbing", "Stair Climbing"},
                {"StairMill", "Stair Mill"}, {"TableTennis", "Table Tennis"},
                {"MartialArts", "Martial Arts"}, {"DailyActivity", "Daily Activity"},
        };
        for (String[] l : labels) KEY_TO_LABEL.put(l[0], l[1]);
    }

    private static void map(String key, int... types) {
        for (int t : types) TYPE_TO_KEY.put(t, key);
    }

    /** Every activity key this mapper can produce (the post service whitelist). */
    public static final Set<String> ALL_KEYS;

    static {
        Set<String> keys = new LinkedHashSet<>(TYPE_TO_KEY.values());
        keys.add(FALLBACK_KEY);
        ALL_KEYS = Collections.unmodifiableSet(keys);
    }

    /** Health Connect exercise type to an Actifit activity key; unknown types become DailyActivity. */
    public static String activityKey(int exerciseType) {
        String key = TYPE_TO_KEY.get(exerciseType);
        return key != null ? key : FALLBACK_KEY;
    }

    /** English label for a key, e.g. "WeightLifting" -> "Weight Lifting". */
    public static String englishLabel(String key) {
        String label = KEY_TO_LABEL.get(key);
        return label != null ? label : key;
    }

    /** Whether a distance makes sense for this activity (only then is it read and sent). */
    public static boolean hasDistance(String key) {
        return Arrays.asList("Cycling", "Swimming", "Hiking", "Walking", "Running", "Treadmill",
                "Skiing", "Skating", "Kayaking", "Snowshoeing", "Sailing").contains(key);
    }

    /** One exercise session as read from Health Connect, before filtering. */
    public static final class Session {
        public final int exerciseType;
        public final long startMs;
        public final long endMs;
        public final boolean manual;
        public final String origin;

        public Session(int exerciseType, long startMs, long endMs, boolean manual, String origin) {
            this.exerciseType = exerciseType;
            this.startMs = startMs;
            this.endMs = endMs;
            this.manual = manual;
            this.origin = origin != null ? origin : "";
        }

        long durationMs() {
            return endMs - startMs;
        }
    }

    /** A workout kept for display / the report. distanceM is -1 when unknown. */
    public static final class Workout {
        public final String key;
        public final long startMs;
        public final long endMs;
        public final boolean manual;
        public final String origin;
        public final long distanceM;

        public Workout(String key, long startMs, long endMs, boolean manual, String origin, long distanceM) {
            this.key = key;
            this.startMs = startMs;
            this.endMs = endMs;
            this.manual = manual;
            this.origin = origin != null ? origin : "";
            this.distanceM = distanceM;
        }

        public int minutes() {
            return (int) Math.min(1440, Math.max(1, Math.round((endMs - startMs) / 60_000.0)));
        }

        public Workout withDistance(long meters) {
            return new Workout(key, startMs, endMs, manual, origin, meters);
        }
    }

    /**
     * Keeps sessions that START inside [dayStartMs, dayEndMs), last 5 min to 24 h, and removes
     * duplicates: when two sessions of the same activity overlap by more than half of the shorter
     * one (e.g. the same ride written by a watch app and a phone app), only the longest is kept.
     * Ties go to the earlier start, then the origin package name, so the result is stable.
     * At most {@link #MAX_WORKOUTS} (the longest) are returned, ordered by start time.
     */
    public static List<Workout> select(List<Session> sessions, long dayStartMs, long dayEndMs) {
        List<Session> candidates = new ArrayList<>();
        if (sessions != null) {
            for (Session s : sessions) {
                if (s == null) continue;
                if (s.startMs < dayStartMs || s.startMs >= dayEndMs) continue;
                long d = s.durationMs();
                if (d < MIN_DURATION_MS || d > MAX_DURATION_MS) continue;
                candidates.add(s);
            }
        }
        // longest first; stable tie-break
        Comparator<Session> preferred = (a, b) -> {
            int c = Long.compare(b.durationMs(), a.durationMs());
            if (c != 0) return c;
            c = Long.compare(a.startMs, b.startMs);
            if (c != 0) return c;
            c = a.origin.compareTo(b.origin);
            if (c != 0) return c;
            return Integer.compare(a.exerciseType, b.exerciseType);
        };
        Collections.sort(candidates, preferred);

        List<Session> kept = new ArrayList<>();
        for (Session s : candidates) {
            boolean duplicate = false;
            for (Session k : kept) {
                if (activityKey(k.exerciseType).equals(activityKey(s.exerciseType))
                        && overlapsMoreThanHalf(k, s)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) kept.add(s);
        }
        if (kept.size() > MAX_WORKOUTS) kept = new ArrayList<>(kept.subList(0, MAX_WORKOUTS));

        Collections.sort(kept, (a, b) -> {
            int c = Long.compare(a.startMs, b.startMs);
            return c != 0 ? c : preferred.compare(a, b);
        });
        List<Workout> out = new ArrayList<>();
        for (Session s : kept) {
            out.add(new Workout(activityKey(s.exerciseType), s.startMs, s.endMs, s.manual, s.origin, -1));
        }
        return out;
    }

    static boolean overlapsMoreThanHalf(Session a, Session b) {
        long overlap = Math.min(a.endMs, b.endMs) - Math.max(a.startMs, b.startMs);
        if (overlap <= 0) return false;
        long shorter = Math.min(a.durationMs(), b.durationMs());
        return shorter > 0 && overlap * 2 > shorter;
    }

    /** "🏋️ Workouts (Health Connect): Cycling 45 min, Yoga 30 min" — always English; null if none. */
    public static String postBodyLine(List<Workout> workouts) {
        if (workouts == null || workouts.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("🏋️ Workouts (Health Connect): ");
        for (int i = 0; i < workouts.size(); i++) {
            Workout w = workouts.get(i);
            if (i > 0) sb.append(", ");
            sb.append(englishLabel(w.key)).append(' ').append(w.minutes()).append(" min");
        }
        return sb.toString();
    }

    /**
     * The "workouts" request field: [{"type":"Cycling","min":45,"distance_m":15000,"manual":1}].
     * distance_m only when known and in range; manual only when the session was typed in by hand.
     */
    public static JSONArray toJson(List<Workout> workouts) throws JSONException {
        JSONArray arr = new JSONArray();
        if (workouts == null) return arr;
        for (Workout w : workouts) {
            if (arr.length() >= MAX_WORKOUTS) break;
            JSONObject o = new JSONObject();
            o.put("type", w.key);
            o.put("min", w.minutes());
            if (w.distanceM >= 0 && w.distanceM <= MAX_DISTANCE_M) o.put("distance_m", w.distanceM);
            if (w.manual) o.put("manual", 1);
            arr.put(o);
        }
        return arr;
    }

    /** Distinct activity keys, in order, for pre-selecting the report's activity chips. */
    public static List<String> distinctKeys(List<Workout> workouts) {
        Set<String> keys = new LinkedHashSet<>();
        if (workouts != null) for (Workout w : workouts) keys.add(w.key);
        return new ArrayList<>(keys);
    }
}
