package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;

import io.actifit.fitnesstracker.actifitfitnesstracker.SignupOutcome.Kind;

import org.junit.Test;

public class SignupOutcomeTest {

    @Test
    public void quoteReasons() {
        assertEquals(Kind.QUOTED, SignupOutcome.ofQuote("quoted"));
        assertEquals(Kind.USERNAME_TAKEN, SignupOutcome.ofQuote("username_taken"));
        assertEquals(Kind.PROMO_INVALID, SignupOutcome.ofQuote("promo_invalid"));
        assertEquals(Kind.INVALID_REFERRER, SignupOutcome.ofQuote("invalid_referrer"));
        assertEquals(Kind.INVALID_EMAIL, SignupOutcome.ofQuote("invalid_email"));
        assertEquals(Kind.RATE_LIMITED, SignupOutcome.ofQuote("rate_limited"));
        assertEquals(Kind.DISABLED, SignupOutcome.ofQuote("signup_disabled"));
        assertEquals(Kind.RETRY, SignupOutcome.ofQuote("price_unavailable"));
        assertEquals(Kind.RETRY, SignupOutcome.ofQuote("server_error"));
        assertEquals(Kind.RETRY, SignupOutcome.ofQuote(null));
        assertEquals(Kind.INVALID, SignupOutcome.ofQuote("invalid_keys"));
    }

    @Test
    public void createdWinsOverAnyReason() {
        assertEquals(Kind.CREATED, SignupOutcome.ofSubmit(true, "created", false));
        assertEquals(Kind.CREATED, SignupOutcome.ofSubmit(true, "already_created", false));
    }

    @Test
    public void submitReasons() {
        assertEquals(Kind.PENDING, SignupOutcome.ofSubmit(false, "payment_pending", true));
        assertEquals(Kind.UNDERPAID, SignupOutcome.ofSubmit(false, "payment_insufficient", false));
        assertEquals(Kind.PROMO_INVALID, SignupOutcome.ofSubmit(false, "promo_invalid", false));
        assertEquals(Kind.RATE_LIMITED, SignupOutcome.ofSubmit(false, "rate_limited", true));
        assertEquals(Kind.DISABLED, SignupOutcome.ofSubmit(false, "signup_disabled", true));
        assertEquals(Kind.INVALID_REFERRER, SignupOutcome.ofSubmit(false, "invalid_referrer", false));
    }

    @Test
    public void usernameTakenAfterClaimIsAFinalFailure() {
        // a paid signup whose name was taken is refunded by support: never "pick another name"
        assertEquals(Kind.FAILED, SignupOutcome.ofSubmit(false, "username_taken", false));
    }

    @Test
    public void temporaryVersusFinalFailures() {
        assertEquals(Kind.RETRY, SignupOutcome.ofSubmit(false, "creation_failed", true));
        assertEquals(Kind.RETRY, SignupOutcome.ofSubmit(false, "server_error", true));
        assertEquals(Kind.RETRY, SignupOutcome.ofSubmit(false, null, false));
        assertEquals(Kind.FAILED, SignupOutcome.ofSubmit(false, "creation_failed", false));
        assertEquals(Kind.FAILED, SignupOutcome.ofSubmit(false, "memo_mismatch", false));
        assertEquals(Kind.FAILED, SignupOutcome.ofSubmit(false, "invalid_keys", false));
    }

    @Test
    public void retryDelayIsClamped() {
        assertEquals(10_000L, SignupOutcome.retryDelayMs(0));
        assertEquals(5_000L, SignupOutcome.retryDelayMs(1));
        assertEquals(10_000L, SignupOutcome.retryDelayMs(10));
        assertEquals(60_000L, SignupOutcome.retryDelayMs(3600));
    }
}
