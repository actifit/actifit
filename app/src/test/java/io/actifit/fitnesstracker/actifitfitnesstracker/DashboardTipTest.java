package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DashboardTipTest {

    @Test
    public void aStreakAtRiskComesFirst() {
        assertEquals(DashboardTip.KEEP_STREAK, DashboardTip.kind(0, 4, 8000));
        assertEquals(DashboardTip.KEEP_STREAK, DashboardTip.kind(4999, 1, 0));
    }

    @Test
    public void noStepsAndNoStreakIsAStartTip() {
        assertEquals(DashboardTip.START, DashboardTip.kind(0, 0, 6000));
    }

    @Test
    public void tenThousandBeatsTheAverageTips() {
        assertEquals(DashboardTip.TEN_K, DashboardTip.kind(10000, 5, 12000));
    }

    @Test
    public void aboveAndBelowTheAverage() {
        assertEquals(DashboardTip.ABOVE_AVG, DashboardTip.kind(7000, 0, 6000));
        assertEquals(DashboardTip.BELOW_AVG, DashboardTip.kind(5000, 0, 6000));
    }

    @Test
    public void aLongStreakIsPraisedWhenBelowTheAverage() {
        assertEquals(DashboardTip.STREAK, DashboardTip.kind(6000, 3, 9000));
        assertEquals(DashboardTip.BELOW_AVG, DashboardTip.kind(6000, 2, 9000));
    }

    @Test
    public void noHistoryFallsBackToTheGenericTip() {
        assertEquals(DashboardTip.GENERIC, DashboardTip.kind(6000, 0, 0));
        assertEquals(DashboardTip.GENERIC, DashboardTip.kind(6000, 0, 6000));
    }
}
