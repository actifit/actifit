package io.actifit.fitnesstracker.actifitfitnesstracker;

/**
 * Pure arithmetic for the Health Connect reward step count.
 *
 * Rewards come only from steps a device or tracker actually recorded. Steps typed in by hand
 * (Health Connect RECORDING_METHOD_MANUAL_ENTRY) are excluded.
 *
 * Health Connect's aggregate total is de-duplicated across apps, but it includes manual entries
 * and we cannot ask it to leave them out. So we subtract the manual steps we find in the raw
 * records. Because the aggregate de-duplicates and the raw manual sum does not, a plain
 * subtraction can undercount. To protect against that, the result never drops below the
 * largest single non-manual source (the main device's own count), capped at the aggregate.
 */
public final class HcRewardSteps {

    private HcRewardSteps() {
    }

    /**
     * @param aggregate       Health Connect COUNT_TOTAL for the day (includes manual entries)
     * @param manualSum       sum of steps in records whose recording method is MANUAL_ENTRY
     * @param bestOriginTotal largest per-app total of non-manual records (or -1/0 if none)
     * @return the step count that may be used for rewards, never negative
     */
    public static long rewardSteps(long aggregate, long manualSum, long bestOriginTotal) {
        if (aggregate < 0) aggregate = 0;
        if (manualSum <= 0) return aggregate;
        long reward = Math.max(aggregate - manualSum, Math.min(aggregate, bestOriginTotal));
        return Math.max(0L, reward);
    }

    /** How many steps were left out of the reward count. */
    public static long excludedSteps(long aggregate, long manualSum, long bestOriginTotal) {
        long agg = Math.max(0L, aggregate);
        return agg - rewardSteps(aggregate, manualSum, bestOriginTotal);
    }
}
