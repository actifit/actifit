package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.SharedPreferences;

/**
 * Single source of truth for the "living companion" aura logic, shared by the profile screen
 * ({@link ProfileActivity}) and the dashboard header ({@link MainActivity}) so the element and
 * vitality tier always agree.
 */
public final class CompanionUtil {

    public static final String PREF_COMPANION = "auraCompanion";

    private CompanionUtil() {}

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
     * The companion index to render for a user: the logged-in user uses their stored pick,
     * everyone else falls back to a stable element derived from their name.
     */
    public static int resolveCompanion(SharedPreferences prefs, String username, boolean isSelf) {
        int fallback = companionForName(username);
        if (isSelf && prefs != null) {
            return prefs.getInt(PREF_COMPANION, fallback);
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
