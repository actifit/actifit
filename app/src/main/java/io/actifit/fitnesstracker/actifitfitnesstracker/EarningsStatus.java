package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONObject;

/**
 * Rules for the "Your token earnings" bottom sheet: whether the user is earning each token
 * and, if not, why. Pure Java with no Android types, so it is covered by plain JUnit tests.
 */
public final class EarningsStatus {

    /** What the sheet shows in a token's chip. */
    public enum State { EARNING, NOT_EARNING, CHECKING, UNAVAILABLE }

    /** Why a token is not being earned. Only set when the state is NOT_EARNING. */
    public enum Reason { NONE, LOW_BALANCE, NO_ACCOUNT }

    /** Progress of a network lookup the status depends on. */
    public enum Lookup { PENDING, DONE, FAILED }

    public static final class TokenStatus {
        public final State state;
        public final Reason reason;
        /** Minimum balance needed (LOW_BALANCE only). */
        public final double required;
        /** The user's current balance (LOW_BALANCE only). */
        public final double current;

        TokenStatus(State state, Reason reason, double required, double current) {
            this.state = state;
            this.reason = reason;
            this.required = required;
            this.current = current;
        }

        static TokenStatus of(State state) {
            return new TokenStatus(state, Reason.NONE, 0, 0);
        }
    }

    /** The BLURT part of a getAccountData response. */
    public static final class BlurtAccount {
        public final Lookup lookup;
        public final boolean hasAccount;
        /** Liquid BLURT balance; set only when lookup is DONE and hasAccount is true. */
        public final Double balance;

        BlurtAccount(Lookup lookup, boolean hasAccount, Double balance) {
            this.lookup = lookup;
            this.hasAccount = hasAccount;
            this.balance = balance;
        }
    }

    private static final BlurtAccount BLURT_FAILED = new BlurtAccount(Lookup.FAILED, false, null);

    private EarningsStatus() {}

    /**
     * Maps a getAccountData response ({"HIVE":{...},"BLURT":{...}}) to the user's Blurt account.
     * "BLURT": null means the user has no Blurt account. The server sends "BLURT": {"error": true}
     * when every Blurt node failed, so an outage is not mistaken for a missing account. A missing
     * key, a non-object, or an object without a readable "balance" is also treated as a failure.
     */
    public static BlurtAccount fromAccountData(JSONObject response) {
        if (response == null || !response.has("BLURT")) return BLURT_FAILED;
        if (response.isNull("BLURT")) return new BlurtAccount(Lookup.DONE, false, null);
        JSONObject blurt = response.optJSONObject("BLURT");
        if (blurt == null || blurt.optBoolean("error", false)) return BLURT_FAILED;
        Double balance = parseAmount(blurt.optString("balance", null));
        if (balance == null) return BLURT_FAILED;
        return new BlurtAccount(Lookup.DONE, true, balance);
    }

    /**
     * The lookup state after a new result arrives. The balance and account lookups can run twice
     * at once (app start and onResume); once one has succeeded, a later failure keeps the good
     * result instead of turning the row into "Unavailable". A later success always applies.
     */
    public static Lookup merge(Lookup current, Lookup incoming) {
        return current == Lookup.DONE && incoming == Lookup.FAILED ? Lookup.DONE : incoming;
    }

    /** AFIT is earned only while the AFIT balance is at least {@code minRequired}. */
    public static TokenStatus afit(boolean loggedIn, Lookup lookup, Double balance, double minRequired) {
        if (!loggedIn) return TokenStatus.of(State.UNAVAILABLE);
        if (lookup == Lookup.PENDING) return TokenStatus.of(State.CHECKING);
        if (lookup == Lookup.FAILED || balance == null) return TokenStatus.of(State.UNAVAILABLE);
        if (balance < minRequired) {
            return new TokenStatus(State.NOT_EARNING, Reason.LOW_BALANCE, minRequired, balance);
        }
        return TokenStatus.of(State.EARNING);
    }

    /** HIVE (and Hive-Engine tokens such as SPORTS) are earned by any logged-in Hive user. */
    public static TokenStatus hive(boolean loggedIn) {
        return TokenStatus.of(loggedIn ? State.EARNING : State.UNAVAILABLE);
    }

    /**
     * BLURT needs a Blurt account with a liquid balance of at least {@code minRequired}.
     * {@code balance} may be null when the account exists but the balance could not be read.
     */
    public static TokenStatus blurt(boolean loggedIn, Lookup lookup, boolean hasAccount,
                                    Double balance, double minRequired) {
        if (!loggedIn) return TokenStatus.of(State.UNAVAILABLE);
        if (lookup == Lookup.PENDING) return TokenStatus.of(State.CHECKING);
        if (lookup == Lookup.FAILED) return TokenStatus.of(State.UNAVAILABLE);
        if (!hasAccount) return new TokenStatus(State.NOT_EARNING, Reason.NO_ACCOUNT, 0, 0);
        if (balance == null) return TokenStatus.of(State.UNAVAILABLE);
        if (balance < minRequired) {
            return new TokenStatus(State.NOT_EARNING, Reason.LOW_BALANCE, minRequired, balance);
        }
        return TokenStatus.of(State.EARNING);
    }

    /** True when any of the given tokens is definitely not being earned. */
    public static boolean anyNotEarning(TokenStatus... statuses) {
        for (TokenStatus s : statuses) {
            if (s != null && s.state == State.NOT_EARNING) return true;
        }
        return false;
    }

    /**
     * Parses a chain amount such as {@code "0.630 BLURT"} (or a bare {@code "0.630"}).
     * Returns null when the text is missing or not a number.
     */
    public static Double parseAmount(String amount) {
        if (amount == null) return null;
        String trimmed = amount.trim();
        if (trimmed.isEmpty()) return null;
        int space = trimmed.indexOf(' ');
        String number = space >= 0 ? trimmed.substring(0, space) : trimmed;
        try {
            double value = Double.parseDouble(number);
            return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
