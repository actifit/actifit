package io.actifit.fitnesstracker.actifitfitnesstracker;

import java.util.regex.Pattern;

/**
 * What the app does with a reply from /app/signupQuote or /app/signupWithKeys. Kept free of
 * Android and JSON types so it can be unit tested.
 */
final class SignupOutcome {
    enum Kind {
        /** quote: memo (and amount, for a paid signup) received. */
        QUOTED,
        /** submit: the account exists. */
        CREATED,
        /** submit: no payment seen yet; ask again after the delay. */
        PENDING,
        /** a temporary server problem; ask again after the delay. */
        RETRY,
        /** submit: a payment arrived but was too small. */
        UNDERPAID,
        PROMO_INVALID,
        USERNAME_TAKEN,
        INVALID_REFERRER,
        INVALID_EMAIL,
        RATE_LIMITED,
        DISABLED,
        /** submit: this signup ended without an account; support refunds any payment. */
        FAILED,
        /** a request the server could not accept; should not happen. */
        INVALID
    }

    static final long DEFAULT_RETRY_MS = 10_000L;
    private static final long MIN_RETRY_MS = 5_000L;
    private static final long MAX_RETRY_MS = 60_000L;
    private static final Pattern MEMO = Pattern.compile("^signup:[0-9a-f]{24}$");

    private SignupOutcome() {}

    /** A memo the current server issues (older app versions made up their own). */
    static boolean isServerMemo(String memo) {
        return memo != null && MEMO.matcher(memo).matches();
    }

    static Kind ofQuote(String reason) {
        if (reason == null) return Kind.RETRY;
        switch (reason) {
            case "quoted": return Kind.QUOTED;
            case "username_taken": return Kind.USERNAME_TAKEN;
            case "promo_invalid": return Kind.PROMO_INVALID;
            case "invalid_referrer": return Kind.INVALID_REFERRER;
            case "invalid_email": return Kind.INVALID_EMAIL;
            case "rate_limited": return Kind.RATE_LIMITED;
            case "signup_disabled": return Kind.DISABLED;
            case "price_unavailable":
            case "server_error": return Kind.RETRY;
            default: return Kind.INVALID;
        }
    }

    static Kind ofSubmit(boolean accountCreated, String reason, boolean retryable) {
        if (accountCreated) return Kind.CREATED;
        if (reason == null) return Kind.RETRY;
        switch (reason) {
            case "payment_pending": return Kind.PENDING;
            case "payment_insufficient": return Kind.UNDERPAID;
            case "promo_invalid": return Kind.PROMO_INVALID;
            case "invalid_referrer": return Kind.INVALID_REFERRER;
            case "invalid_email": return Kind.INVALID_EMAIL;
            case "rate_limited": return Kind.RATE_LIMITED;
            case "signup_disabled": return Kind.DISABLED;
            // the name went to someone else after this signup was claimed: support refunds
            case "username_taken": return Kind.FAILED;
            default:
                // creation_failed / server_error are temporary when retryable; a stored failure
                // (memo_mismatch, invalid_keys, ...) is final
                return retryable ? Kind.RETRY : Kind.FAILED;
        }
    }

    /** How long to wait before asking again, from the server's retry_after (seconds). */
    static long retryDelayMs(int retryAfterSeconds) {
        if (retryAfterSeconds <= 0) return DEFAULT_RETRY_MS;
        return Math.max(MIN_RETRY_MS, Math.min(MAX_RETRY_MS, retryAfterSeconds * 1000L));
    }
}
