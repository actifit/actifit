package io.actifit.fitnesstracker.actifitfitnesstracker;

import java.util.Locale;
import java.util.Objects;

/**
 * Every field a signup memo is computed from (besides the keys, which follow from the username
 * and the master password). A quote is taken for one of these, and the submit for that memo
 * must send the same one, so both are built from this value, never from the live inputs.
 */
final class SignupDetails {
    final String username;
    final String email;
    final String referrer;
    final String currency;
    final String promo;

    SignupDetails(String username, String email, String referrer, String currency, String promo) {
        this.username = clean(username).toLowerCase(Locale.US);
        this.email = clean(email);
        this.referrer = clean(referrer).toLowerCase(Locale.US);
        this.currency = clean(currency);
        this.promo = clean(promo);
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    SignupDetails withCurrencyAndPromo(String newCurrency, String newPromo) {
        return new SignupDetails(username, email, referrer, newCurrency, newPromo);
    }

    /** Same person-entered fields (username, email, referrer); currency and promo may differ. */
    boolean sameEntries(SignupDetails o) {
        // the server lowercases the email before computing the memo
        return o != null && username.equals(o.username) && referrer.equals(o.referrer)
                && email.toLowerCase(Locale.US).equals(o.email.toLowerCase(Locale.US));
    }

    /** True when both would get the same memo from the server. */
    boolean sameMemoAs(SignupDetails o) {
        return sameEntries(o) && currency.equals(o.currency) && promo.equals(o.promo);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SignupDetails)) return false;
        SignupDetails d = (SignupDetails) o;
        return username.equals(d.username) && email.equals(d.email) && referrer.equals(d.referrer)
                && currency.equals(d.currency) && promo.equals(d.promo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, email, referrer, currency, promo);
    }
}
