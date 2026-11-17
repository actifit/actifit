package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class HcRewardStepsTest {

    private static Map<String, Long> m(Object... kv) {
        Map<String, Long> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], ((Number) kv[i + 1]).longValue());
        return map;
    }

    private static final Map<String, Long> NONE = Collections.emptyMap();

    @Test
    public void noManualEntries_keepsAggregateUnchanged() {
        Map<String, Long> auto = m("phone", 6000, "watch", 7000);
        assertFalse(HcRewardSteps.needsFilteredAggregate(auto, NONE));
        assertEquals(9000L, HcRewardSteps.rewardSteps(9000, auto, NONE, 0));
        // zero-step manual records don't count as manual steps
        assertEquals(9000L, HcRewardSteps.rewardSteps(9000, auto, m("fit", 0), 0));
    }

    @Test
    public void twoAutomaticApps_manualAlreadyDroppedByDedupe_keepsFullTotal() {
        // (a) phone 6000 + watch 6000 aggregate to 12000; a 3000 manual entry from another app was
        // already dropped by Health Connect's dedupe. The old single-origin floor gave 9000.
        Map<String, Long> auto = m("phone", 6000, "watch", 6000);
        Map<String, Long> manual = m("fit", 3000);
        assertTrue(HcRewardSteps.needsFilteredAggregate(auto, manual));
        assertEquals(new HashSet<>(Arrays.asList("phone", "watch")), HcRewardSteps.filterOrigins(auto));
        assertEquals(12000L, HcRewardSteps.rewardSteps(12000, auto, manual, 12000));
    }

    @Test
    public void manualFromAppNotInAggregate_isNotSubtracted() {
        // (b) manual records from an app left out of the aggregate (e.g. removed from the data
        // priority list) were never counted, so nothing is taken off. The old formula gave 4000.
        Map<String, Long> auto = m("phone", 4000, "watch", 4000);
        Map<String, Long> manual = m("other", 5000);
        assertEquals(8000L, HcRewardSteps.rewardSteps(8000, auto, manual, 8000));
    }

    @Test
    public void manualOnlyAppCountedInAggregate_isRemovedByOriginFilter() {
        // phone counted 6000, a separate app holds 4000 typed-in steps that the aggregate included
        Map<String, Long> auto = m("phone", 6000);
        Map<String, Long> manual = m("fit", 4000);
        assertEquals(6000L, HcRewardSteps.rewardSteps(10000, auto, manual, 6000));
    }

    @Test
    public void mixedOrigin_manualAndAutomaticFromSameApp_subtractsItsManualSteps() {
        Map<String, Long> auto = m("fit", 5000);
        Map<String, Long> manual = m("fit", 2000);
        assertEquals(5000L, HcRewardSteps.rewardSteps(7000, auto, manual, 7000));
    }

    @Test
    public void mixedOrigin_withOtherApp_onlyMixedManualIsSubtracted() {
        // fit wrote 3000 automatic + 2000 manual, watch 6000 automatic, other app 1000 manual only
        Map<String, Long> auto = m("fit", 3000, "watch", 6000);
        Map<String, Long> manual = m("fit", 2000, "other", 1000);
        // filtered aggregate over {fit, watch} after HC dedupe = 8000 (incl. fit's 2000 manual)
        assertEquals(6000L, HcRewardSteps.rewardSteps(9000, auto, manual, 8000));
    }

    @Test
    public void mixedOrigin_manualHiddenByDedupe_largestAppStillSurvives() {
        // dedupe already hid most of the manual entry; plain subtraction would give 3000
        Map<String, Long> auto = m("phone", 7000);
        Map<String, Long> manual = m("phone", 4500);
        assertEquals(7000L, HcRewardSteps.rewardSteps(7500, auto, manual, 7500));
    }

    @Test
    public void manualOnly_noAutomaticApp_givesZero() {
        assertFalse(HcRewardSteps.needsFilteredAggregate(NONE, m("fit", 5000)));
        assertEquals(0L, HcRewardSteps.rewardSteps(5000, NONE, m("fit", 5000), 0));
    }

    @Test
    public void neverExceedsUnfilteredAggregate() {
        Map<String, Long> auto = m("phone", 9000);
        assertEquals(7000L, HcRewardSteps.rewardSteps(7000, auto, m("fit", 3000), 9000));
    }

    @Test
    public void neverNegative() {
        Map<String, Long> auto = m("phone", 0);
        assertEquals(0L, HcRewardSteps.rewardSteps(2000, auto, m("phone", 5000), 2000));
        assertEquals(0L, HcRewardSteps.rewardSteps(-5, NONE, NONE, 0));
        assertEquals(0L, HcRewardSteps.rewardSteps(-5, auto, m("x", 100), -3));
    }

    @Test
    public void noteFlagDecision() {
        assertTrue(HcRewardSteps.shouldFlagNote(1, false, false));
        assertFalse(HcRewardSteps.shouldFlagNote(0, false, false));
        assertFalse(HcRewardSteps.shouldFlagNote(500, true, false));
        assertFalse(HcRewardSteps.shouldFlagNote(500, false, true));

        assertTrue(HcRewardSteps.shouldShowNote(true, false));
        assertFalse(HcRewardSteps.shouldShowNote(true, true));
        assertFalse(HcRewardSteps.shouldShowNote(false, false));
    }
}
