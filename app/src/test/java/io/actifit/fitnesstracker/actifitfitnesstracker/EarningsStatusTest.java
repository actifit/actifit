package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import io.actifit.fitnesstracker.actifitfitnesstracker.EarningsStatus.Lookup;
import io.actifit.fitnesstracker.actifitfitnesstracker.EarningsStatus.Reason;
import io.actifit.fitnesstracker.actifitfitnesstracker.EarningsStatus.State;
import io.actifit.fitnesstracker.actifitfitnesstracker.EarningsStatus.TokenStatus;

public class EarningsStatusTest {

    private static final double MIN_AFIT = 5;
    private static final double MIN_BLURT = 5;

    // ---- AFIT ----

    @Test
    public void afitIsCheckingUntilTheBalanceLoads() {
        // userFullBalance defaults to 0.0 before the request returns; that must not read as "not earning"
        assertEquals(State.CHECKING, EarningsStatus.afit(true, Lookup.PENDING, 0.0, MIN_AFIT).state);
    }

    @Test
    public void afitBelowMinimumIsNotEarningWithBalances() {
        TokenStatus s = EarningsStatus.afit(true, Lookup.DONE, 2.5, MIN_AFIT);
        assertEquals(State.NOT_EARNING, s.state);
        assertEquals(Reason.LOW_BALANCE, s.reason);
        assertEquals(5.0, s.required, 0);
        assertEquals(2.5, s.current, 0);
    }

    @Test
    public void afitAtOrAboveMinimumIsEarning() {
        assertEquals(State.EARNING, EarningsStatus.afit(true, Lookup.DONE, 5.0, MIN_AFIT).state);
        assertEquals(State.EARNING, EarningsStatus.afit(true, Lookup.DONE, 1234.5, MIN_AFIT).state);
    }

    @Test
    public void afitLookupFailureIsUnavailableNotNotEarning() {
        assertEquals(State.UNAVAILABLE, EarningsStatus.afit(true, Lookup.FAILED, 0.0, MIN_AFIT).state);
        assertEquals(State.UNAVAILABLE, EarningsStatus.afit(true, Lookup.DONE, null, MIN_AFIT).state);
    }

    // ---- HIVE ----

    @Test
    public void hiveIsEarningForAnyLoggedInUser() {
        assertEquals(State.EARNING, EarningsStatus.hive(true).state);
        assertEquals(State.UNAVAILABLE, EarningsStatus.hive(false).state);
    }

    // ---- BLURT ----

    @Test
    public void blurtIsCheckingUntilAccountDataLoads() {
        assertEquals(State.CHECKING, EarningsStatus.blurt(true, Lookup.PENDING, false, null, MIN_BLURT).state);
    }

    @Test
    public void blurtWithoutAccountIsNotEarningNoAccount() {
        TokenStatus s = EarningsStatus.blurt(true, Lookup.DONE, false, null, MIN_BLURT);
        assertEquals(State.NOT_EARNING, s.state);
        assertEquals(Reason.NO_ACCOUNT, s.reason);
    }

    @Test
    public void blurtBelowMinimumIsNotEarningLowBalance() {
        TokenStatus s = EarningsStatus.blurt(true, Lookup.DONE, true, 0.63, MIN_BLURT);
        assertEquals(State.NOT_EARNING, s.state);
        assertEquals(Reason.LOW_BALANCE, s.reason);
        assertEquals(5.0, s.required, 0);
        assertEquals(0.63, s.current, 1e-9);
    }

    @Test
    public void blurtAtOrAboveMinimumIsEarning() {
        assertEquals(State.EARNING, EarningsStatus.blurt(true, Lookup.DONE, true, 5.0, MIN_BLURT).state);
        assertEquals(State.EARNING, EarningsStatus.blurt(true, Lookup.DONE, true, 812.1, MIN_BLURT).state);
    }

    @Test
    public void blurtFailureOrUnreadableBalanceIsUnavailable() {
        assertEquals(State.UNAVAILABLE, EarningsStatus.blurt(true, Lookup.FAILED, false, null, MIN_BLURT).state);
        assertEquals(State.UNAVAILABLE, EarningsStatus.blurt(true, Lookup.DONE, true, null, MIN_BLURT).state);
    }

    @Test
    public void loggedOutShowsNothingAsNotEarning() {
        assertEquals(State.UNAVAILABLE, EarningsStatus.afit(false, Lookup.DONE, 0.0, MIN_AFIT).state);
        assertEquals(State.UNAVAILABLE, EarningsStatus.blurt(false, Lookup.DONE, false, null, MIN_BLURT).state);
    }

    // ---- helpers ----

    @Test
    public void anyNotEarning() {
        TokenStatus earning = EarningsStatus.hive(true);
        TokenStatus checking = EarningsStatus.afit(true, Lookup.PENDING, 0.0, MIN_AFIT);
        TokenStatus notEarning = EarningsStatus.blurt(true, Lookup.DONE, false, null, MIN_BLURT);
        assertFalse(EarningsStatus.anyNotEarning(earning, checking));
        assertTrue(EarningsStatus.anyNotEarning(earning, notEarning));
    }

    @Test
    public void parseAmountReadsChainBalances() {
        assertEquals(0.63, EarningsStatus.parseAmount("0.630 BLURT"), 1e-9);
        assertEquals(12.5, EarningsStatus.parseAmount(" 12.500 "), 1e-9);
        assertNull(EarningsStatus.parseAmount(null));
        assertNull(EarningsStatus.parseAmount(""));
        assertNull(EarningsStatus.parseAmount("BLURT"));
        assertNull(EarningsStatus.parseAmount("NaN BLURT"));
    }

    /**
     * getAccountData returns {"HIVE":null,"BLURT":null} for a user with no account on a chain:
     * the key is present, so has() is true. ApiManager must use isNull().
     */
    @Test
    public void accountDataNullChainMeansNoAccount() throws Exception {
        JSONObject none = new JSONObject("{\"HIVE\":{\"name\":\"u\"},\"BLURT\":null}");
        assertTrue(none.has("BLURT"));
        assertTrue(none.isNull("BLURT"));

        JSONObject some = new JSONObject("{\"HIVE\":{},\"BLURT\":{\"balance\":\"0.630 BLURT\"}}");
        assertFalse(some.isNull("BLURT"));
        assertEquals(0.63, EarningsStatus.parseAmount(
                some.getJSONObject("BLURT").optString("balance", null)), 1e-9);
    }
}
