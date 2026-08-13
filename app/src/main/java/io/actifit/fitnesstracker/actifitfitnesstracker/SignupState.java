package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONException;
import org.json.JSONObject;

final class SignupState {
    static final String PHASE_READY_FOR_PAYMENT = "READY_FOR_PAYMENT";
    static final String PHASE_REQUEST_SUBMITTED = "REQUEST_SUBMITTED";
    static final String PHASE_ACCOUNT_CREATED = "ACCOUNT_CREATED";
    static final String PHASE_ACCOUNT_CREATION_FAILED = "ACCOUNT_CREATION_FAILED";

    final String username;
    final String masterPassword;
    final String memo;
    final String selectedCurrency;
    final double requiredCryptoAmount;
    final int afitReward;
    final String promoCode;
    final String email;
    final String referrer;
    final String phase;
    final boolean irreversible;
    final boolean requestSubmitted;
    final boolean accountCreated;

    SignupState(String username, String masterPassword, String memo, String selectedCurrency,
            double requiredCryptoAmount, int afitReward, String promoCode, String email,
            String referrer, String phase, boolean irreversible, boolean requestSubmitted,
            boolean accountCreated) {
        this.username = username;
        this.masterPassword = masterPassword;
        this.memo = memo;
        this.selectedCurrency = selectedCurrency;
        this.requiredCryptoAmount = requiredCryptoAmount;
        this.afitReward = afitReward;
        this.promoCode = promoCode;
        this.email = email;
        this.referrer = referrer;
        this.phase = phase;
        this.irreversible = irreversible;
        this.requestSubmitted = requestSubmitted;
        this.accountCreated = accountCreated;
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("username", username);
        json.put("masterPassword", masterPassword);
        json.put("memo", memo);
        json.put("selectedCurrency", selectedCurrency);
        json.put("requiredCryptoAmount", requiredCryptoAmount);
        json.put("afitReward", afitReward);
        json.put("promoCode", promoCode);
        json.put("email", email);
        json.put("referrer", referrer);
        json.put("phase", phase);
        json.put("irreversible", irreversible);
        json.put("requestSubmitted", requestSubmitted);
        json.put("accountCreated", accountCreated);
        return json;
    }

    static SignupState fromJson(JSONObject json) throws JSONException {
        SignupState state = new SignupState(
                json.getString("username"),
                json.getString("masterPassword"),
                json.getString("memo"),
                json.getString("selectedCurrency"),
                json.getDouble("requiredCryptoAmount"),
                json.getInt("afitReward"),
                json.optString("promoCode", ""),
                json.optString("email", ""),
                json.optString("referrer", ""),
                json.getString("phase"),
                json.getBoolean("irreversible"),
                json.getBoolean("requestSubmitted"),
                json.getBoolean("accountCreated"));
        if (state.username.isEmpty() || state.masterPassword.isEmpty() || state.memo.isEmpty()
                || !("HIVE".equals(state.selectedCurrency) || "HBD".equals(state.selectedCurrency))
                || !state.irreversible
                || !(PHASE_READY_FOR_PAYMENT.equals(state.phase)
                || PHASE_REQUEST_SUBMITTED.equals(state.phase)
                || PHASE_ACCOUNT_CREATED.equals(state.phase)
                || PHASE_ACCOUNT_CREATION_FAILED.equals(state.phase))
                || (PHASE_READY_FOR_PAYMENT.equals(state.phase)
                && (state.requestSubmitted || state.accountCreated))
                || (PHASE_REQUEST_SUBMITTED.equals(state.phase)
                && (!state.requestSubmitted || state.accountCreated))
                || (PHASE_ACCOUNT_CREATED.equals(state.phase)
                && (!state.requestSubmitted || !state.accountCreated))
                || (PHASE_ACCOUNT_CREATION_FAILED.equals(state.phase)
                && (!state.requestSubmitted || state.accountCreated))) {
            throw new JSONException("Invalid signup recovery state");
        }
        return state;
    }
}
