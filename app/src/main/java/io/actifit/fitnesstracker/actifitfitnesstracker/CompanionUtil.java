package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.SharedPreferences;

/**
 * Single source of truth for the "living companion" aura logic, shared by the profile screen
 * ({@link ProfileActivity}) and the dashboard header ({@link MainActivity}) so the element and
 * vitality tier always agree.
 */
public final class CompanionUtil {

    public static final String PREF_COMPANION = "auraCompanion";       // explicit user pick
    public static final String PREF_COMPANION_AUTO = "auraCompanionAuto"; // sport-derived cache
    public static final int ACTIVE_THRESHOLD = 5000;

    // spirit-animal indices (must match AuraView's animal arrays)
    private static final int WOLF = 0, CHEETAH = 1, MUSTANG = 2, DOLPHIN = 3,
            GORILLA = 4, TIGER = 5, FLAMINGO = 6, EAGLE = 7;

    private CompanionUtil() {}

    /** Maps a reported activity_type to the spirit animal that best represents it. */
    public static int animalForActivity(String activityType) {
        if (activityType == null) return EAGLE;
        String a = activityType.toLowerCase();
        if (containsAny(a, "run", "jog", "sprint", "marathon")) return CHEETAH;
        if (containsAny(a, "walk", "hik", "trek", "step")) return WOLF;
        if (containsAny(a, "cycl", "bike", "bik", "spin")) return MUSTANG;
        if (containsAny(a, "swim", "dive", "aqua")) return DOLPHIN;
        if (containsAny(a, "weight", "strength", "lift", "crossfit", "gym",
                "box", "mma", "martial", "karate", "judo", "bootcamp")) return GORILLA;
        if (containsAny(a, "yoga", "pilates", "stretch", "balance", "medit", "barre")) return FLAMINGO;
        if (containsAny(a, "basket", "foot", "soccer", "tennis", "cricket", "sport",
                "hiit", "aerobic", "cardio", "dance", "zumba", "badminton")) return TIGER;
        return EAGLE;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }

    /**
     * The companion "wilts" when a live streak is at risk: the user has a streak to protect,
     * hasn't secured today's activity yet, and the day is running out. Drives a fading/flicker
     * state on the aura as a loss-aversion nudge.
     */
    public static boolean isWilting(int streak, int todaySteps, int hourOfDay) {
        return streak >= 1 && todaySteps < ACTIVE_THRESHOLD && hourOfDay >= 16;
    }

    /** Deterministic element for a user without a stored choice (0..count-1). */
    public static int companionForName(String name) {
        if (name == null || name.isEmpty()) return 0;
        int h = 0;
        for (int i = 0; i < name.length(); i++) {
            h = h * 31 + name.charAt(i);
        }
        return Math.abs(h) % AuraView.companionCount();
    }

    /**
     * The companion index to render for a user. For the logged-in user: an explicit pick wins,
     * otherwise the sport-derived cache (written by the profile screen), otherwise a stable
     * name-derived fallback. Other users fall back to their name-derived animal until the
     * profile screen resolves their sport from live activity data.
     */
    public static int resolveCompanion(SharedPreferences prefs, String username, boolean isSelf) {
        int fallback = companionForName(username);
        if (isSelf && prefs != null) {
            if (prefs.contains(PREF_COMPANION)) {
                return prefs.getInt(PREF_COMPANION, fallback);
            }
            return prefs.getInt(PREF_COMPANION_AUTO, fallback);
        }
        return fallback;
    }

    /** Vitality tier 0..5 from a consecutive-day streak. */
    public static int levelFromStreak(int streak) {
        if (streak <= 0) return 0;
        if (streak <= 2) return 1;
        if (streak <= 6) return 2;
        if (streak <= 29) return 3;
        if (streak <= 99) return 4;
        return 5;
    }

    /** Vitality tier 0..5 from a user rank value (string as returned by /getRank). */
    public static int levelFromRank(String rankStr) {
        float rank;
        try {
            rank = Float.parseFloat(rankStr);
        } catch (Exception e) {
            return 0;
        }
        if (rank < 20) return 0;
        if (rank < 60) return 1;
        if (rank < 150) return 2;
        if (rank < 400) return 3;
        if (rank < 1000) return 4;
        return 5;
    }
}
