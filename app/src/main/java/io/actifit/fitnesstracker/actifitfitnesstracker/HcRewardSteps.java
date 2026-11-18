package io.actifit.fitnesstracker.actifitfitnesstracker;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure arithmetic for the Health Connect reward step count.
 *
 * Rewards come only from steps a device or tracker actually recorded. Steps typed in by hand
 * (Health Connect RECORDING_METHOD_MANUAL_ENTRY) are excluded.
 *
 * Health Connect's aggregate is de-duplicated across apps but includes manual entries, and an
 * aggregate can't be told to skip them. So when manual records exist we take a second aggregate
 * limited to the apps that wrote at least one automatic (non-manual) record. That drops apps that
 * only hold typed-in steps, while Health Connect still de-duplicates between the remaining apps.
 * From it we subtract only the manual steps of those same apps (apps that wrote both kinds).
 * The result never exceeds the unfiltered aggregate, never drops below the largest single app's
 * automatic total (capped at the filtered aggregate), and is never negative.
 */
public final class HcRewardSteps {

    private HcRewardSteps() {
    }

    private static long sum(Map<String, Long> byOrigin) {
        long s = 0;
        if (byOrigin != null) {
            for (Long v : byOrigin.values()) if (v != null && v > 0) s += v;
        }
        return s;
    }

    /** Whether any steps were typed in by hand. */
    public static boolean hasManualSteps(Map<String, Long> manualByOrigin) {
        return sum(manualByOrigin) > 0;
    }

    /**
     * Whether the second, origin-filtered aggregate is needed: only when there are manual steps
     * and at least one app wrote automatic records.
     */
    public static boolean needsFilteredAggregate(Map<String, Long> autoByOrigin,
                                                 Map<String, Long> manualByOrigin) {
        return hasManualSteps(manualByOrigin) && autoByOrigin != null && !autoByOrigin.isEmpty();
    }

    /** Apps to aggregate over: those with at least one automatic (non-manual) record. */
    public static Set<String> filterOrigins(Map<String, Long> autoByOrigin) {
        if (autoByOrigin == null) return Collections.emptySet();
        return new HashSet<>(autoByOrigin.keySet());
    }

    /**
     * @param aggregate         unfiltered Health Connect COUNT_TOTAL for the day
     * @param autoByOrigin      automatic (non-manual) step totals per app; an app is present when
     *                          it wrote at least one automatic record
     * @param manualByOrigin    manually entered step totals per app
     * @param filteredAggregate COUNT_TOTAL limited to {@link #filterOrigins}; ignored unless
     *                          {@link #needsFilteredAggregate} is true
     * @return the step count that may be used for rewards
     */
    public static long rewardSteps(long aggregate, Map<String, Long> autoByOrigin,
                                   Map<String, Long> manualByOrigin, long filteredAggregate) {
        long agg = Math.max(0L, aggregate);
        if (!hasManualSteps(manualByOrigin)) return agg;
        if (autoByOrigin == null || autoByOrigin.isEmpty()) return 0L;

        long filtered = Math.max(0L, filteredAggregate);
        long mixedManual = 0;
        long bestOrigin = 0;
        for (Map.Entry<String, Long> e : autoByOrigin.entrySet()) {
            Long manual = manualByOrigin.get(e.getKey());
            if (manual != null && manual > 0) mixedManual += manual;
            if (e.getValue() != null && e.getValue() > bestOrigin) bestOrigin = e.getValue();
        }
        long reward = Math.max(filtered - mixedManual, Math.min(filtered, bestOrigin));
        return Math.max(0L, Math.min(reward, agg));
    }

    /** Whether a sync that left {@code excluded} steps out should queue the one-time note. */
    public static boolean shouldFlagNote(long excluded, boolean alreadyShown, boolean alreadyPending) {
        return excluded > 0 && !alreadyShown && !alreadyPending;
    }

    /** Whether the one-time note should be shown now. */
    public static boolean shouldShowNote(boolean pending, boolean alreadyShown) {
        return pending && !alreadyShown;
    }
}
