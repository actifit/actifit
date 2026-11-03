package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class SignupDetailsTest {
    private static final SignupDetails QUOTED =
            new SignupDetails("newguy", "New@Example.com", "friend", "HIVE", "");

    @Test
    public void normalisesLikeTheServer() {
        SignupDetails d = new SignupDetails("  NewGuy ", " a@b.co ", " Friend ", "HIVE", null);
        assertEquals("newguy", d.username);
        assertEquals("a@b.co", d.email);
        assertEquals("friend", d.referrer);
        assertEquals("", d.promo);
    }

    @Test
    public void sameMemoOnlyWhenEveryMemoFieldMatches() {
        // email case doesn't change the memo: the server lowercases it
        assertTrue(QUOTED.sameMemoAs(new SignupDetails("newguy", "new@example.com", "friend", "HIVE", "")));
        assertFalse(QUOTED.sameMemoAs(new SignupDetails("newguy2", "New@Example.com", "friend", "HIVE", "")));
        assertFalse(QUOTED.sameMemoAs(new SignupDetails("newguy", "other@example.com", "friend", "HIVE", "")));
        assertFalse(QUOTED.sameMemoAs(new SignupDetails("newguy", "New@Example.com", "", "HIVE", "")));
        assertFalse(QUOTED.sameMemoAs(QUOTED.withCurrencyAndPromo("HBD", "")));
        assertFalse(QUOTED.sameMemoAs(QUOTED.withCurrencyAndPromo("HIVE", "CODE")));
        assertFalse(QUOTED.sameMemoAs(null));
    }

    @Test
    public void sameEntriesIgnoresCurrencyAndPromo() {
        assertTrue(QUOTED.sameEntries(QUOTED.withCurrencyAndPromo("HBD", "CODE")));
        assertFalse(QUOTED.sameEntries(new SignupDetails("newguy", "New@Example.com", "other", "HIVE", "")));
    }

    @Test
    public void signupStateKeepsThePaymentAccount() throws Exception {
        SignupState s = new SignupState("newguy", "P5pw", "signup:0123456789abcdef01234567", "HBD",
                2.0, 50, "", "a@b.co", "friend", SignupState.PHASE_REQUEST_SUBMITTED,
                true, true, false, "actifit.pay", "username_taken");
        SignupState back = SignupState.fromJson(new JSONObject(s.toJson().toString()));
        assertEquals("actifit.pay", back.signupAccount);
        assertEquals("username_taken", back.failureReason);
        assertEquals("signup:0123456789abcdef01234567", back.memo);
        assertEquals("HBD", back.selectedCurrency);
        assertEquals("friend", back.referrer);
        assertTrue(back.requestSubmitted);
    }

    @Test
    public void signupStateFromAnOlderVersionLoadsWithTheDefaultAccount() throws Exception {
        JSONObject old = new JSONObject()
                .put("username", "newguy").put("masterPassword", "P5pw").put("memo", "signup:ABCDEFGHJK")
                .put("selectedCurrency", "HIVE").put("requiredCryptoAmount", 7.5).put("afitReward", 40)
                .put("phase", SignupState.PHASE_READY_FOR_PAYMENT).put("irreversible", false)
                .put("requestSubmitted", false).put("accountCreated", false);
        SignupState s = SignupState.fromJson(old);
        assertEquals(SignupState.DEFAULT_SIGNUP_ACCOUNT, s.signupAccount);
        assertEquals("", s.failureReason);
        assertFalse(SignupOutcome.isServerMemo(s.memo));
    }

    private static SignupState readyState(String memo, double amount, String promo) {
        return new SignupState("newguy", "P5pw", memo, "HIVE", amount, 40, promo, "", "",
                SignupState.PHASE_READY_FOR_PAYMENT, false, false, false, "actifit.signup", "");
    }

    @Test
    public void aShownPaymentMemoIsNeverDiscardedWithoutAWarning() {
        // the user may have paid from a wallet before the app submitted anything
        assertFalse(readyState("signup:0123456789abcdef01234567", 7.5, "").canSafelyDiscard());
        assertFalse(readyState("signup:ABCDEFGHJK", 7.5, "").canSafelyDiscard()); // older version
        // older versions saved the typed promo, and 0 after a failed price fetch
        assertFalse(readyState("signup:ABCDEFGHJK", 0, "TYPO").canSafelyDiscard());
    }

    @Test
    public void aPromoSignupNotYetSubmittedCanBeDiscarded() {
        assertTrue(readyState("signup:0123456789abcdef01234567", 0, "CODE1").canSafelyDiscard());
        assertTrue(readyState("signup:0123456789abcdef01234567", 7.5, "CODE1").canSafelyDiscard());
    }
}
