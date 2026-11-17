package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HcRewardStepsTest {

    @Test
    public void noManualEntries_keepsAggregate() {
        assertEquals(8000L, HcRewardSteps.rewardSteps(8000, 0, 8000));
        assertEquals(0L, HcRewardSteps.excludedSteps(8000, 0, 8000));
    }

    @Test
    public void noManualEntries_keepsAggregateEvenWhenOriginsDisagree() {
        // aggregate is the de-duplicated truth; without manual data nothing changes
        assertEquals(9000L, HcRewardSteps.rewardSteps(9000, 0, 6000));
    }

    @Test
    public void manualOnly_givesZero() {
        assertEquals(0L, HcRewardSteps.rewardSteps(5000, 5000, -1));
        assertEquals(5000L, HcRewardSteps.excludedSteps(5000, 5000, -1));
    }

    @Test
    public void manualAddedOnTopOfDevice_subtracted() {
        // phone counted 6000, user typed 4000 more
        assertEquals(6000L, HcRewardSteps.rewardSteps(10000, 4000, 6000));
        assertEquals(4000L, HcRewardSteps.excludedSteps(10000, 4000, 6000));
    }

    @Test
    public void manualHiddenByDedupe_mainDeviceCountSurvives() {
        // Health Connect's priority dedupe already dropped most of the manual entry, so the
        // aggregate is barely above the phone's own count. Plain subtraction would give 3000;
        // the main device's 7000 must survive.
        assertEquals(7000L, HcRewardSteps.rewardSteps(7500, 4500, 7000));
    }

    @Test
    public void bestOriginLargerThanAggregate_cappedAtAggregate() {
        assertEquals(7000L, HcRewardSteps.rewardSteps(7000, 3000, 9000));
        assertEquals(0L, HcRewardSteps.excludedSteps(7000, 3000, 9000));
    }

    @Test
    public void manualLargerThanAggregate_floorsAtZero() {
        assertEquals(0L, HcRewardSteps.rewardSteps(2000, 5000, 0));
        assertEquals(0L, HcRewardSteps.rewardSteps(2000, 5000, -1));
    }

    @Test
    public void negativeAggregate_treatedAsZero() {
        assertEquals(0L, HcRewardSteps.rewardSteps(-5, 0, 0));
        assertEquals(0L, HcRewardSteps.rewardSteps(-5, 100, 0));
    }
}
