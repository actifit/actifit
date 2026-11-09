package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GpsVerificationTest {

    @Test
    public void normalWalkIsVerified() {
        // 3.2 km in 4,300 steps = 0.74 m per step
        assertEquals(GpsVerification.Status.VERIFIED, GpsVerification.evaluate(3200, 4300));
    }

    @Test
    public void runIsVerified() {
        // 5 km in 4,000 steps = 1.25 m per step
        assertEquals(GpsVerification.Status.VERIFIED, GpsVerification.evaluate(5000, 4000));
    }

    @Test
    public void boundsAreInclusive() {
        assertEquals(GpsVerification.Status.VERIFIED, GpsVerification.evaluate(400, 1000)); // 0.4
        assertEquals(GpsVerification.Status.VERIFIED, GpsVerification.evaluate(1600, 1000)); // 1.6
    }

    @Test
    public void carOrBikeRideIsMismatch() {
        // 10 km with only 500 steps = 20 m per step
        assertEquals(GpsVerification.Status.MISMATCH, GpsVerification.evaluate(10000, 500));
        assertEquals(GpsVerification.Status.MISMATCH, GpsVerification.evaluate(1700, 1000)); // 1.7
    }

    @Test
    public void shakingInPlaceIsMismatch() {
        // 300 m with 5,000 steps = 0.06 m per step
        assertEquals(GpsVerification.Status.MISMATCH, GpsVerification.evaluate(300, 5000));
        assertEquals(GpsVerification.Status.MISMATCH, GpsVerification.evaluate(390, 1000)); // 0.39
    }

    @Test
    public void tinyRoutesAreNotEnoughData() {
        assertEquals(GpsVerification.Status.NOT_ENOUGH_DATA, GpsVerification.evaluate(199, 280));
        assertEquals(GpsVerification.Status.NOT_ENOUGH_DATA, GpsVerification.evaluate(150, 2000));
        assertEquals(GpsVerification.Status.NOT_ENOUGH_DATA, GpsVerification.evaluate(5000, 299));
        assertEquals(GpsVerification.Status.NOT_ENOUGH_DATA, GpsVerification.evaluate(5000, 0));
        assertEquals(GpsVerification.Status.NOT_ENOUGH_DATA, GpsVerification.evaluate(0, 0));
    }

    @Test
    public void minimumsThemselvesQualify() {
        // 200 m / 300 steps = 0.67 m per step
        assertEquals(GpsVerification.Status.VERIFIED, GpsVerification.evaluate(200, 300));
    }

    @Test
    public void stepBasedActivityTypes() {
        assertTrue(GpsVerification.isStepBased("Walking"));
        assertTrue(GpsVerification.isStepBased("running"));
        assertTrue(GpsVerification.isStepBased("Hiking"));
        assertTrue(GpsVerification.isStepBased(null));
        assertFalse(GpsVerification.isStepBased("Cycling"));
        assertFalse(GpsVerification.isStepBased("Kayaking"));
        assertFalse(GpsVerification.isStepBased("Skiing"));
    }

    @Test
    public void stepsInWindowCountsOverlappingSlotsOnly() {
        List<ActivitySlot> slots = Arrays.asList(
                new ActivitySlot("0945", 100),  // 09:45-10:00, before the route
                new ActivitySlot("1000", 1200), // 10:00-10:15, overlaps
                new ActivitySlot("1015", 1500), // overlaps
                new ActivitySlot("1030", 900),  // 10:30-10:45, overlaps (route ends 10:32)
                new ActivitySlot("1045", 400)); // after
        // route 10:05 - 10:32
        assertEquals(3600, GpsVerification.stepsInWindow(slots, 10 * 60 + 5, 10 * 60 + 32));
    }

    @Test
    public void stepsInWindowEdges() {
        List<ActivitySlot> slots = Arrays.asList(
                new ActivitySlot("0000", 50),
                new ActivitySlot("945", 70),    // unpadded slot string is 09:45
                new ActivitySlot("2345", 80),
                new ActivitySlot("bad", 999),
                new ActivitySlot("1200", -5));
        // window starting exactly where a slot ends excludes that slot
        assertEquals(0, GpsVerification.stepsInWindow(slots, 10 * 60, 10 * 60 + 10));
        assertEquals(70, GpsVerification.stepsInWindow(slots, 9 * 60 + 59, 10 * 60));
        assertEquals(50, GpsVerification.stepsInWindow(slots, 0, 5));
        assertEquals(80, GpsVerification.stepsInWindow(slots, 23 * 60 + 50, 24 * 60 - 1));
        assertEquals(0, GpsVerification.stepsInWindow(slots, 12 * 60, 12 * 60 + 10));
        assertEquals(0, GpsVerification.stepsInWindow(null, 0, 100));
        assertEquals(0, GpsVerification.stepsInWindow(new ArrayList<>(), 0, 100));
        assertEquals(0, GpsVerification.stepsInWindow(slots, 100, 50));
    }
}
