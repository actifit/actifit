package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.health.connect.client.records.ExerciseSessionRecord;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WorkoutMapperTest {

    private static final long MIN = 60_000L;
    private static final long DAY_START = 1_700_000_000_000L;
    private static final long DAY_END = DAY_START + 24 * 60 * MIN;

    private static WorkoutMapper.Session s(int type, long startMin, long durMin, String origin) {
        return new WorkoutMapper.Session(type, DAY_START + startMin * MIN,
                DAY_START + (startMin + durMin) * MIN, false, origin);
    }

    private static WorkoutMapper.Session manual(int type, long startMin, long durMin) {
        return new WorkoutMapper.Session(type, DAY_START + startMin * MIN,
                DAY_START + (startMin + durMin) * MIN, true, "com.example.manual");
    }

    // ---- mapping ----

    @Test
    public void mapsKnownTypesToActivityKeys() {
        assertEquals("Cycling", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_BIKING));
        assertEquals("Cycling", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY));
        assertEquals("Swimming", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL));
        assertEquals("Swimming", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER));
        assertEquals("WeightLifting", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING));
        assertEquals("WeightLifting", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING));
        assertEquals("Yoga", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_YOGA));
        assertEquals("Stretching", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_PILATES));
        assertEquals("Stretching", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING));
        assertEquals("Bootcamp", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING));
        assertEquals("Bootcamp", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP));
        assertEquals("Elliptical", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL));
        assertEquals("Gym", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE));
        assertEquals("Hiking", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_HIKING));
        assertEquals("Football", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_SOCCER));
    }

    @Test
    public void unknownTypesFallBackToDailyActivity() {
        assertEquals("DailyActivity", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT));
        assertEquals("DailyActivity", WorkoutMapper.activityKey(ExerciseSessionRecord.EXERCISE_TYPE_SCUBA_DIVING));
        assertEquals("DailyActivity", WorkoutMapper.activityKey(9999));
    }

    @Test
    public void allKeysAreTheWhitelistAndIncludeFallback() {
        assertTrue(WorkoutMapper.ALL_KEYS.contains("DailyActivity"));
        assertTrue(WorkoutMapper.ALL_KEYS.contains("Cycling"));
        assertFalse(WorkoutMapper.ALL_KEYS.contains("workout_minutes"));
        for (int t = -1; t < 120; t++) {
            assertTrue(WorkoutMapper.ALL_KEYS.contains(WorkoutMapper.activityKey(t)));
        }
    }

    @Test
    public void englishLabels() {
        assertEquals("Weight Lifting", WorkoutMapper.englishLabel("WeightLifting"));
        assertEquals("Daily Activity", WorkoutMapper.englishLabel("DailyActivity"));
        assertEquals("Cycling", WorkoutMapper.englishLabel("Cycling"));
    }

    // ---- window + duration filters ----

    @Test
    public void keepsOnlySessionsStartingInsideTheDay() {
        List<WorkoutMapper.Session> in = Arrays.asList(
                s(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, -30, 60, "a"),          // starts yesterday
                s(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, 0, 30, "a"),            // starts at midnight
                s(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, 23 * 60 + 50, 40, "a"), // crosses midnight
                s(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, 24 * 60, 30, "a"));  // starts tomorrow
        List<WorkoutMapper.Workout> out = WorkoutMapper.select(in, DAY_START, DAY_END);
        assertEquals(2, out.size());
        assertEquals("Yoga", out.get(0).key);
        assertEquals("Cycling", out.get(1).key);
        assertEquals(40, out.get(1).minutes());
    }

    @Test
    public void dropsTooShortAndTooLongSessions() {
        List<WorkoutMapper.Session> in = Arrays.asList(
                s(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, 60, 4, "a"),
                s(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, 120, 5, "a"),
                new WorkoutMapper.Session(ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
                        DAY_START + MIN, DAY_START + MIN + 24 * 60 * MIN + 1, false, "a"));
        List<WorkoutMapper.Workout> out = WorkoutMapper.select(in, DAY_START, DAY_END);
        assertEquals(1, out.size());
        assertEquals(5, out.get(0).minutes());
    }

    @Test
    public void handlesNullAndEmpty() {
        assertTrue(WorkoutMapper.select(null, DAY_START, DAY_END).isEmpty());
        assertTrue(WorkoutMapper.select(Collections.emptyList(), DAY_START, DAY_END).isEmpty());
    }

    // ---- dedupe ----

    @Test
    public void dedupesSameActivityAcrossAppsKeepingLongest() {
        List<WorkoutMapper.Session> in = Arrays.asList(
                s(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, 600, 40, "com.watch"),
                s(ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY, 605, 45, "com.phone"));
        List<WorkoutMapper.Workout> out = WorkoutMapper.select(in, DAY_START, DAY_END);
        assertEquals(1, out.size());
        assertEquals(45, out.get(0).minutes());
        assertEquals("com.phone", out.get(0).origin);
    }

    @Test
    public void dedupeTieBreakIsStable() {
        WorkoutMapper.Session a = s(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, 600, 30, "com.b");
        WorkoutMapper.Session b = s(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, 600, 30, "com.a");
        assertEquals("com.a", WorkoutMapper.select(Arrays.asList(a, b), DAY_START, DAY_END).get(0).origin);
        assertEquals("com.a", WorkoutMapper.select(Arrays.asList(b, a), DAY_START, DAY_END).get(0).origin);
    }

    @Test
    public void keepsDifferentActivitiesAndSmallOverlaps() {
        List<WorkoutMapper.Session> in = Arrays.asList(
                s(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, 600, 60, "a"),
                s(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, 610, 30, "a"),     // different activity
                s(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, 650, 60, "b"));  // overlap 10 of 60
        assertEquals(3, WorkoutMapper.select(in, DAY_START, DAY_END).size());
    }

    @Test
    public void overlapExactlyHalfIsNotADuplicate() {
        WorkoutMapper.Session a = s(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, 0, 40, "a");
        WorkoutMapper.Session b = s(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, 20, 40, "b");
        assertFalse(WorkoutMapper.overlapsMoreThanHalf(a, b));
        WorkoutMapper.Session c = s(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, 19, 40, "b");
        assertTrue(WorkoutMapper.overlapsMoreThanHalf(a, c));
    }

    @Test
    public void manualSessionsAreKeptAndFlagged() {
        List<WorkoutMapper.Workout> out = WorkoutMapper.select(
                Collections.singletonList(manual(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, 60, 30)),
                DAY_START, DAY_END);
        assertEquals(1, out.size());
        assertTrue(out.get(0).manual);
    }

    @Test
    public void capsAtTenKeepingLongestInStartOrder() {
        List<WorkoutMapper.Session> in = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            in.add(s(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, i * 100, 10 + i, "a"));
        }
        List<WorkoutMapper.Workout> out = WorkoutMapper.select(in, DAY_START, DAY_END);
        assertEquals(10, out.size());
        assertEquals(12, out.get(0).minutes()); // 10 and 11 min sessions dropped
        for (int i = 1; i < out.size(); i++) {
            assertTrue(out.get(i).startMs > out.get(i - 1).startMs);
        }
    }

    // ---- body line ----

    @Test
    public void postBodyLine() {
        List<WorkoutMapper.Workout> out = WorkoutMapper.select(Arrays.asList(
                s(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, 420, 45, "a"),
                s(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, 1080, 30, "a")), DAY_START, DAY_END);
        assertEquals("🏋️ Workouts (Health Connect): Cycling 45 min, Weight Lifting 30 min",
                WorkoutMapper.postBodyLine(out));
        assertNull(WorkoutMapper.postBodyLine(Collections.emptyList()));
        assertNull(WorkoutMapper.postBodyLine(null));
    }

    // ---- JSON ----

    @Test
    public void toJson() throws Exception {
        List<WorkoutMapper.Workout> ws = new ArrayList<>(WorkoutMapper.select(Arrays.asList(
                s(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, 420, 45, "a"),
                manual(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, 1080, 30)), DAY_START, DAY_END));
        ws.set(0, ws.get(0).withDistance(15234));
        JSONArray arr = WorkoutMapper.toJson(ws);
        assertEquals(2, arr.length());

        JSONObject ride = arr.getJSONObject(0);
        assertEquals("Cycling", ride.getString("type"));
        assertEquals(45, ride.getInt("min"));
        assertEquals(15234, ride.getLong("distance_m"));
        assertFalse(ride.has("manual"));

        JSONObject yoga = arr.getJSONObject(1);
        assertEquals("Yoga", yoga.getString("type"));
        assertEquals(30, yoga.getInt("min"));
        assertFalse(yoga.has("distance_m"));
        assertEquals(1, yoga.getInt("manual"));

        assertFalse(arr.toString().contains("workout_minutes"));
    }

    @Test
    public void toJsonDropsOutOfRangeDistance() throws Exception {
        WorkoutMapper.Workout w = new WorkoutMapper.Workout("Cycling", DAY_START, DAY_START + 30 * MIN,
                false, "a", 300_001);
        assertFalse(WorkoutMapper.toJson(Collections.singletonList(w)).getJSONObject(0).has("distance_m"));
    }

    @Test
    public void distinctKeys() {
        List<WorkoutMapper.Workout> out = WorkoutMapper.select(Arrays.asList(
                s(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, 60, 30, "a"),
                s(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, 300, 30, "a"),
                s(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, 900, 30, "a")), DAY_START, DAY_END);
        assertEquals(Arrays.asList("Yoga", "Cycling"), WorkoutMapper.distinctKeys(out));
    }
}
