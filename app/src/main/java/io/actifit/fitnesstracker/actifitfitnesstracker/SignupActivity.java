package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.activity.OnBackPressedCallback;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SignupActivity extends BaseActivity {

    /** Off until the server signup route is back; "Create an account" opens the website instead. */
    static final boolean ENABLED = false;

    /** How long one "Check payment" tap keeps asking the server before handing back to the user. */
    private static final long CONFIRM_PAYMENT_DEADLINE_MS = 60_000L;
    private static final Object SIGNUP_REQUEST_TAG = SignupActivity.class.getName();
    private static final String QUOTE_PATH = "app/signupQuote";
    private static final String SUBMIT_PATH = "app/signupWithKeys";
    private static final String TERMS_URL = "https://actifit.io/terms-conditions";
    private static final String PRIVACY_URL = "https://actifit.io/privacy-policy";

    private int currentStep = 1;
    private LinearProgressIndicator progressBar;
    private MaterialCardView step1Container, step2Container, step3Container, step4Container;
    private TextInputLayout emailInputLayout;
    private TextInputEditText usernameInput, emailInput, referralInput, promoInput, masterPasswordDisplay, postingKeyDisplay;
    private TextView usernameStatus, verificationDesc, tosText, signupUsernameDisplay;
    private Button btnNext, btnPrev, btnCheckUsername, btnCopyKeys, btnCopyPostingKey;
    private MaterialCheckBox cbTos, cbBackedUp;

    private MaterialButtonToggleGroup currencyToggle;
    private HiveRequests hiveRequests;
    private RequestQueue queue;
    private ProgressDialog progress;

    private String generatedMasterPassword = "";
    private boolean isUsernameAvailable = false;
    private String availableUsername = "";
    // The memo and everything it was quoted for. The server recomputes the memo from the submitted
    // fields, so a submit always sends quotedDetails, never the live inputs.
    private SignupDetails quotedDetails = null;
    private String generatedMemo = "";
    private String selectedCurrency = "HIVE";
    private String quotedPromo = "";
    private String signupAccount = SignupState.DEFAULT_SIGNUP_ACCOUNT;
    private double requiredCryptoAmount = 0.0;
    private int afitReward = -1;
    private boolean quoteInFlight = false;
    private long quoteGeneration = 0;
    private boolean refreshQuoteInFlight = false;
    private JsonObjectRequest activeQuoteRequest;
    /** What the last submit said, so a deadline can say why the account is not there yet. */
    private SignupOutcome.Kind lastSubmitKind = null;

    private final Handler confirmDeadlineHandler = new Handler(Looper.getMainLooper());
    private JsonObjectRequest activeConfirmPaymentRequest;
    private Runnable confirmPaymentDeadline;
    private Runnable nextSubmitAttempt;
    private long confirmRequestGeneration = 0;
    private boolean confirmRequestInFlight = false;
    private boolean reconciliationInFlight = false;
    private boolean accountCreationHandled = false;
    private boolean accountCreationFailed = false;
    /** Why the server ended this signup, when it said so in this session. */
    private String failureReason = "";
    /** An older-version signup waiting for the chain check before its dialog is shown. */
    private boolean olderVersionCheckPending = false;
    private long lastAmountRefreshAt = 0;
    private static final long AMOUNT_REFRESH_INTERVAL_MS = 5 * 60_000L;
    private boolean activityDestroyed = false;
    private SignupStateStore signupStateStore;
    private SignupState recoveryState;
    private boolean isRestoringState = false;
    private boolean recoveryUnavailable = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // FLAG_SECURE is applied per-step in updateStepUI() — only the keys screen (step 4) is
        // screenshot-protected, so the rest of the wizard stays capturable for support/debugging.
        setContentView(R.layout.activity_signup);

        hiveRequests = new HiveRequests(this);
        hiveRequests.setRequestTag(SIGNUP_REQUEST_TAG);
        queue = Volley.newRequestQueue(this);
        signupStateStore = new SignupStateStore(this);

        initViews();
        configureBackHandling();
        if (!restoreRecoverableSignup()) {
            return;
        }
        updateStepUI();
        if (recoveryState != null && !recoveryState.accountCreated
                && !SignupOutcome.isServerMemo(recoveryState.memo)) {
            if (recoveryState.requestSubmitted) {
                // the old server may have created it: keep the keys if so
                olderVersionCheckPending = true;
                reconcileAccountExistence();
            } else {
                showOlderVersionSignupDialog(recoveryState.memo);
            }
        } else if (recoveryState != null
                && SignupState.PHASE_ACCOUNT_CREATION_FAILED.equals(recoveryState.phase)) {
            showAccountCreationFailedDialog();
        } else if (recoveryState != null && recoveryState.requestSubmitted
                && !recoveryState.accountCreated) {
            reconcileAccountExistence();
        }
    }

    private void initViews() {
        progressBar = findViewById(R.id.signup_progress);
        step1Container = findViewById(R.id.step_username_container);
        step2Container = findViewById(R.id.step_email_container);
        step3Container = findViewById(R.id.step_verification_container);
        step4Container = findViewById(R.id.step_keys_container);

        usernameInput = findViewById(R.id.username_input);
        emailInputLayout = findViewById(R.id.email_input_layout);
        emailInput = findViewById(R.id.email_input);
        referralInput = findViewById(R.id.referral_input);
        promoInput = findViewById(R.id.promo_input);
        masterPasswordDisplay = findViewById(R.id.master_password_display);
        masterPasswordDisplay.setSaveEnabled(false);
        usernameStatus = findViewById(R.id.username_status);
        verificationDesc = findViewById(R.id.verification_desc);
        currencyToggle = findViewById(R.id.currency_toggle);
        currencyToggle.check(R.id.button_hive);

        currencyToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || isRestoringState) {
                return;
            }

            String currency = checkedId == R.id.button_hbd ? "HBD" : "HIVE";
            if (isLocked() || quoteInFlight) {
                restorePersistedCurrencySelection();
                return;
            }
            if (currency.equals(selectedCurrency)) return;
            // the memo covers the currency: switching needs a new quote, kept only if it succeeds
            requestQuote(currency, quotedPromo, this::persistCurrentRecoveryStateIfPresent);
            restorePersistedCurrencySelection();
        });

        btnNext = findViewById(R.id.btn_next);
        btnPrev = findViewById(R.id.btn_prev);
        btnCheckUsername = findViewById(R.id.btn_check_username);
        btnCopyKeys = findViewById(R.id.btn_copy_keys);
        postingKeyDisplay = findViewById(R.id.posting_key_display);
        postingKeyDisplay.setSaveEnabled(false);
        btnCopyPostingKey = findViewById(R.id.btn_copy_posting_key);
        tosText = findViewById(R.id.tos_text);
        signupUsernameDisplay = findViewById(R.id.signup_username_display);
        cbTos = findViewById(R.id.cb_tos);
        setupTermsLinks();
        cbBackedUp = findViewById(R.id.cb_backed_up);

        btnCheckUsername.setOnClickListener(v -> checkUsernameAvailability());
        btnNext.setOnClickListener(v -> handleNextStep());
        btnPrev.setOnClickListener(v -> handlePrevStep());
        btnCopyKeys.setOnClickListener(v -> copyKeysToClipboard());
        btnCopyPostingKey.setOnClickListener(v -> copyPostingKeyToClipboard());

        cbTos.setOnCheckedChangeListener((buttonView, isChecked) -> validateStep2());
        cbBackedUp.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (currentStep == 4) btnNext.setEnabled(isChecked);
        });

        emailInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (emailInputLayout != null) emailInputLayout.setError(null);
                validateStep2();
            }
        });

        usernameInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isUsernameAvailable = false;
                availableUsername = "";
                usernameStatus.setVisibility(View.GONE);
            }
        });

        promoInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (currentStep == 3) {
                    updateNextButtonStep3();
                }
            }
        });
    }

    private void setupTermsLinks() {
        String text = getString(R.string.tos_accept);
        SpannableString spannable = new SpannableString(text);

        String termsText = getString(R.string.terms_of_service);
        String privacyText = getString(R.string.privacy_policy);

        int termsStart = text.indexOf(termsText);
        int privacyStart = text.indexOf(privacyText);

        if (termsStart < 0 || privacyStart < 0) {
            tosText.setText(text);
            return;
        }
        int termsEnd = termsStart + termsText.length();
        int privacyEnd = privacyStart + privacyText.length();

        ClickableSpan termsSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                openExternalUrl(TERMS_URL);
            }
        };

        ClickableSpan privacySpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                openExternalUrl(PRIVACY_URL);
            }
        };

        spannable.setSpan(
                termsSpan,
                termsStart,
                termsEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        spannable.setSpan(
                privacySpan,
                privacyStart,
                privacyEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        tosText.setText(spannable);
        tosText.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void openExternalUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(
                    this,
                    R.string.error_opening_link,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void handleNextStep() {
        if (isLocked() && (currentStep == 1 || currentStep == 2)) {
            // a payment may be on its way for the saved details: they can't change any more
            currentStep++;
            updateStepUI();
            return;
        }
        if (currentStep == 1) {
            String username = usernameInput.getText().toString().trim();
            if (username.isEmpty()) {
                usernameInput.setError(getString(R.string.field_required));
                return;
            }
            if (!isValidHiveAccountName(username)) {
                usernameInput.setError(getString(R.string.username_invalid));
                return;
            }
            if (!isUsernameAvailable || !username.equals(availableUsername)) {
                // Don't just tell the user to check — run the availability check for them and focus
                // the field, showing the same result as the "Check availability" button.
                usernameInput.requestFocus();
                checkUsernameAvailability();
                return;
            }
            currentStep = 2;
            updateStepUI();
        } else if (currentStep == 2) {
            String email = emailInput.getText().toString().trim();

            if (!email.isEmpty() &&
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

                if (emailInputLayout != null) {
                    emailInputLayout.setError(getString(R.string.error_email_invalid));
                } else {
                    emailInput.setError(getString(R.string.error_email_invalid));
                }

                return;
            }
            emailInputLayout.setError(null);

            if (!cbTos.isChecked()) {
                showError(getString(R.string.error_tos_required));
                return;
            }
            generateKeys();
            // the server returns the memo, amount and payment account for these exact details
            requestQuote(selectedCurrency, "", () -> {
                if (!persistRecoveryState(SignupState.PHASE_READY_FOR_PAYMENT, false, false)) {
                    return;
                }
                currentStep = 3;
                updateStepUI();
            });
        } else if (currentStep == 3) {
            // "Check payment" / "Claim promo" / "Check status": submit so the server checks the
            // payment (or promo code) and creates the account. Submitting again is safe: the server
            // keys everything on the memo.
            String promo = promoInput.getText().toString().trim();
            if (!isLocked() && (!SignupOutcome.isServerMemo(generatedMemo) || !promo.equals(quotedPromo))) {
                requestQuote(selectedCurrency, promo, () -> {
                    if (persistRecoveryState(SignupState.PHASE_READY_FOR_PAYMENT, false, false)) {
                        startConfirmPaymentRequest();
                    }
                });
            } else {
                startConfirmPaymentRequest();
            }
        } else if (currentStep == 4) {
            // Secure keys step finished, show final success dialog
            showSuccessDialog();
        }
    }

    private void handlePrevStep() {
        // Back always steps the wizard back one screen. Only at the first step, where there's
        // nowhere further back, do we handle leaving: if a payment/account may be in progress we
        // keep the encrypted credentials (leave-for-now) rather than silently deleting them.
        if (currentStep > 1) {
            currentStep--;
            updateStepUI();
            return;
        }
        if (recoveryState != null && recoveryState.irreversible) {
            showLeaveForNowDialog();
        } else if (paidMemoShown()) {
            // the memo may already have been paid from a wallet: don't drop it without asking
            new AlertDialog.Builder(this)
                    .setTitle(R.string.signup_memo_change_title)
                    .setMessage(getString(R.string.signup_leave_paid_memo_message,
                            String.format(Locale.US, "%.3f", requiredCryptoAmount), selectedCurrency, generatedMemo))
                    .setPositiveButton(R.string.signup_leave_for_now, (dialog, which) -> finish())
                    .setNeutralButton(R.string.signup_discard_action, (dialog, which) -> cancelBeforeIrreversibleBoundary())
                    .setNegativeButton(R.string.signup_continue, null)
                    .show();
        } else {
            cancelBeforeIrreversibleBoundary();
        }
    }

    private void configureBackHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Mirror the on-screen Back button: step back a screen, or offer leave-for-now
                // only once we've crossed the irreversible boundary.
                handlePrevStep();
            }
        });
    }

    private void showLeaveForNowDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.signup_recovery_leave_title)
                .setMessage(R.string.signup_recovery_leave_message)
                .setPositiveButton(R.string.signup_leave_for_now, (dialog, which) -> {
                    stopPaymentPolling();
                    finish();
                })
                .setNegativeButton(R.string.signup_continue, null)
                .show();
    }

    private void cancelBeforeIrreversibleBoundary() {
        try {
            signupStateStore.clear();
            finish();
        } catch (SignupStateStore.SignupStateStoreException e) {
            showError(getString(R.string.signup_recovery_cleanup_error));
        }
    }

    private void updateStepUI() {
        // Smooth transitions
        animateTransition(step1Container, currentStep == 1);
        animateTransition(step2Container, currentStep == 2);
        animateTransition(step3Container, currentStep == 3);
        animateTransition(step4Container, currentStep == 4);

        progressBar.setProgress(currentStep * 25);
        // Screenshot-protect only the keys screen (master password + posting key).
        if (currentStep == 4) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        updateCurrencyToggleState();
        boolean editable = !isLocked();
        usernameInput.setEnabled(editable);
        btnCheckUsername.setEnabled(editable);
        emailInput.setEnabled(editable);
        referralInput.setEnabled(editable);
        promoInput.setEnabled(editable);

        // Always show "Back" label
        btnPrev.setText(R.string.back_button);

        // Hide Back button on final step once account is created to prevent confusion
        if (currentStep == 4) {
            btnPrev.setVisibility(View.GONE);
        } else {
            btnPrev.setVisibility(View.VISIBLE);
        }

        if (currentStep == 1) {
            btnNext.setText(R.string.proceed);
            btnNext.setEnabled(true);
        } else if (currentStep == 2) {
            btnNext.setText(R.string.proceed);
            validateStep2();
        } else if (currentStep == 3) {
            updateNextButtonStep3();
            btnNext.setEnabled(true);
        } else {
            btnNext.setText(R.string.btn_go_to_login);
            btnNext.setEnabled(cbBackedUp.isChecked());
            populateFinalKeys();
        }
    }

    private void animateTransition(View view, boolean visible) {
        if (visible) {
            if (view.getVisibility() != View.VISIBLE) {
                view.setVisibility(View.VISIBLE);
                AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
                fadeIn.setDuration(400);
                view.startAnimation(fadeIn);
            }
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private void updateNextButtonStep3() {
        if (accountCreationFailed) {
            btnNext.setText(R.string.btn_check_signup_status);
        } else if (recoveryState != null && recoveryState.requestSubmitted) {
            btnNext.setText(R.string.btn_check_signup_status);
        } else if (promoInput.getText().toString().trim().length() > 0) {
            btnNext.setText(R.string.btn_claim_promo);
        } else {
            btnNext.setText(R.string.btn_check_payment);
        }
    }

    private void validateStep2() {
        if (currentStep != 2) return;
        // Keep Proceed enabled so tapping it without accepting the terms surfaces a clear error
        // (handleNextStep enforces the TOS check) rather than a silently-disabled button.
        btnNext.setEnabled(true);
    }

    /**
     * Resets step-3 state back to payment entry (e.g. after an invalid promo). Clears the
     * submitted/failed flags so the currency toggle re-enables and the primary button reverts
     * from "Check status" back to "Claim Promo" / "Check payment".
     */
    private void resetToPaymentEntry() {
        accountCreationFailed = false;
        persistRecoveryState(SignupState.PHASE_READY_FOR_PAYMENT, false, false);
        updateStepUI();
    }

    /** True once a submit was sent: a payment may be on its way for the saved memo. */
    private boolean isLocked() {
        return recoveryState != null && recoveryState.requestSubmitted;
    }

    private void checkUsernameAvailability() {
        // Close the soft keyboard so the availability result below the field is visible.
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }

        String username = usernameInput.getText().toString().trim();
        if (username.isEmpty()) {
            usernameInput.setError(getString(R.string.field_required));
            return;
        }

        if (!isValidHiveAccountName(username)) {
            showUsernameStatus(getString(R.string.username_invalid), Color.RED);
            return;
        }

        btnCheckUsername.setEnabled(false);
        isUsernameAvailable = false;
        availableUsername = "";
        usernameStatus.setVisibility(View.VISIBLE);
        usernameStatus.setText(R.string.loading);
        usernameStatus.setTextColor(Color.GRAY);

        JSONArray params = new JSONArray();
        JSONArray accounts = new JSONArray();
        accounts.put(username);
        params.put(accounts);

        hiveRequests.processRequest("condenser_api.get_accounts", params)
                .thenAccept(result -> runOnUiThread(() -> {
                    if (!canUpdateUi()) return;
                    btnCheckUsername.setEnabled(true);
                    String currentUsername = usernameInput.getText().toString().trim();
                    if (!username.equals(currentUsername) || !isValidHiveAccountName(currentUsername)) {
                        return;
                    }
                    if (result.length() == 0) {
                        isUsernameAvailable = true;
                        availableUsername = username;
                        showUsernameStatus(getString(R.string.username_available), Color.parseColor("#4CAF50"));
                    } else {
                        isUsernameAvailable = false;
                        availableUsername = "";
                        showUsernameStatus(getString(R.string.username_taken), Color.RED);
                    }
                }))
                .exceptionally(ex -> {
                    runOnUiThread(() -> {
                        if (!canUpdateUi()) return;
                        btnCheckUsername.setEnabled(true);
                        isUsernameAvailable = false;
                        availableUsername = "";
                        showUsernameStatus(getString(R.string.error_check_availability), Color.RED);
                    });
                    return null;
                });
    }

    private boolean isValidHiveAccountName(String username) {
        if (username.length() < 3 || username.length() > 16) {
            return false;
        }
        if (username.startsWith("uid") || username.matches(".*\\d{10}.*")) {
            return false;
        }
        String[] segments = username.split("\\.", -1);
        for (String segment : segments) {
            if (segment.length() < 3
                    || !segment.matches("^[a-z][a-z0-9-]*[a-z0-9]$")
                    || segment.contains("--")) {
                return false;
            }
        }
        return true;
    }

    private void showUsernameStatus(String message, int color) {
        usernameStatus.setVisibility(View.VISIBLE);
        usernameStatus.setText(message);
        usernameStatus.setTextColor(color);
    }

    private boolean restoreRecoverableSignup() {
        try {
            SignupState storedState = signupStateStore.load();
            if (storedState == null) {
                return true;
            }
            applyRecoveryState(storedState, true);
            return true;
        } catch (SignupStateStore.SignupStateStoreException e) {
            recoveryUnavailable = true;
            btnNext.setEnabled(false);
            btnPrev.setEnabled(false);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.signup_recovery_error_title)
                    .setMessage(R.string.signup_recovery_corrupt_error)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                    .setCancelable(false)
                    .show();
            return false;
        }
    }

    private void applyRecoveryState(SignupState state, boolean refreshIncompletePricing) {
        recoveryState = state;
        isRestoringState = true;
        try {
            generatedMasterPassword = state.masterPassword;
            generatedMemo = state.memo;
            selectedCurrency = state.selectedCurrency;
            quotedPromo = state.promoCode;
            failureReason = state.failureReason;
            quotedDetails = new SignupDetails(state.username, state.email, state.referrer,
                    state.selectedCurrency, state.promoCode);
            signupAccount = state.signupAccount;
            requiredCryptoAmount = state.requiredCryptoAmount;
            afitReward = state.afitReward;
            currentStep = state.accountCreated ? 4 : 3;
            accountCreationHandled = state.accountCreated;
            accountCreationFailed = SignupState.PHASE_ACCOUNT_CREATION_FAILED.equals(state.phase);

            usernameInput.setText(state.username);
            availableUsername = state.username;
            isUsernameAvailable = true;
            emailInput.setText(state.email);
            referralInput.setText(state.referrer);
            promoInput.setText(state.promoCode);
            cbTos.setChecked(true);
            masterPasswordDisplay.setText(state.masterPassword);
            currencyToggle.check("HBD".equals(state.selectedCurrency)
                    ? R.id.button_hbd : R.id.button_hive);
            updateCurrencyToggleState();
            updatePaymentInstructions();
        } finally {
            isRestoringState = false;
        }

        // (a memo from an older app version is handled by showOlderVersionSignupDialog)
        if (refreshIncompletePricing && !state.accountCreated && state.promoCode.isEmpty()
                && SignupOutcome.isServerMemo(state.memo)) {
            // a submitted signup refreshes its amount after the chain check (refreshLockedAmountIfDue)
            if (!state.requestSubmitted && state.requiredCryptoAmount <= 0) {
                requestQuote(state.selectedCurrency, "", this::persistCurrentRecoveryStateIfPresent);
            }
        }
    }

    private boolean persistRecoveryState(String phase, boolean requestSubmitted,
            boolean accountCreated) {
        if (recoveryUnavailable) {
            return false;
        }
        SignupDetails d = quotedDetails != null ? quotedDetails : liveDetails(selectedCurrency, quotedPromo);
        SignupState state = new SignupState(
                d.username,
                generatedMasterPassword,
                generatedMemo,
                selectedCurrency,
                requiredCryptoAmount,
                afitReward,
                quotedPromo,
                d.email,
                d.referrer,
                phase,
                !SignupState.PHASE_READY_FOR_PAYMENT.equals(phase),
                requestSubmitted,
                accountCreated,
                signupAccount,
                failureReason);
        try {
            signupStateStore.save(state);
            recoveryState = state;
            return true;
        } catch (SignupStateStore.SignupStateStoreException e) {
            recoveryUnavailable = true;
            stopPaymentPolling();
            btnNext.setEnabled(false);
            showError(getString(R.string.signup_recovery_save_error));
            return false;
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (recoveryState != null) {
            applyRecoveryState(recoveryState, false);
            updateStepUI();
        }
    }

    private void persistCurrentRecoveryStateIfPresent() {
        if (recoveryState != null) {
            persistRecoveryState(recoveryState.phase, recoveryState.requestSubmitted,
                    recoveryState.accountCreated);
        }
    }

    private void updateCurrencyToggleState() {
        boolean enabled = !isLocked();
        currencyToggle.setEnabled(enabled);
        findViewById(R.id.button_hive).setEnabled(enabled);
        findViewById(R.id.button_hbd).setEnabled(enabled);
    }

    private void restorePersistedCurrencySelection() {
        isRestoringState = true;
        try {
            currencyToggle.check("HBD".equals(selectedCurrency)
                    ? R.id.button_hbd : R.id.button_hive);
        } finally {
            isRestoringState = false;
        }
    }

    private void updatePaymentInstructions() {
        String instructions;
        if (!quotedPromo.isEmpty()) {
            instructions = getString(R.string.signup_promo_instructions);
        } else if (SignupOutcome.isServerMemo(generatedMemo) && requiredCryptoAmount > 0) {
            String amount = String.format(Locale.US, "%.3f", requiredCryptoAmount);
            instructions = getString(R.string.signup_payment_instructions, amount,
                    selectedCurrency, signupAccount, generatedMemo);
        } else {
            instructions = getString(R.string.signup_payment_instructions_price_unavailable,
                    selectedCurrency);
        }
        verificationDesc.setText(Html.fromHtml(instructions));
    }

    /** The username the keys were made for. */
    private String signupUsername() {
        return quotedDetails != null ? quotedDetails.username : liveDetails("", "").username;
    }

    private SignupDetails liveDetails(String currency, String promo) {
        return new SignupDetails(usernameInput.getText().toString(), emailInput.getText().toString(),
                referralInput.getText().toString(), currency, promo);
    }

    /**
     * The request both signup routes take. Only the four PUBLIC keys are sent; the master
     * password and private keys never leave the device.
     */
    private JSONObject signupBody(SignupDetails d, @Nullable String memo) throws JSONException {
        JSONObject keys = new JSONObject();
        for (Map.Entry<String, String> k : SignupKeys.publicKeys(d.username, generatedMasterPassword).entrySet()) {
            keys.put(k.getKey(), k.getValue());
        }
        JSONObject body = new JSONObject();
        body.put("new_account", d.username);
        body.put("keys", keys);
        body.put("sent_cur", d.currency);
        if (!d.email.isEmpty()) body.put("email", d.email);
        if (!d.referrer.isEmpty()) body.put("referrer", d.referrer);
        if (!d.promo.isEmpty()) body.put("promo_code", d.promo);
        if (memo != null) body.put("memo", memo);
        return body;
    }

    private interface SignupReply {
        /** The parsed reply, or null when the server could not be reached or replied garbage. */
        void on(@Nullable JSONObject reply);
    }

    /** POSTs to a signup route. Error statuses carry a JSON reply too, so both paths parse it. */
    private JsonObjectRequest postSignup(String path, JSONObject body, SignupReply callback) {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST,
                getString(R.string.live_server) + path, body,
                callback::on,
                error -> {
                    JSONObject parsed = null;
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            parsed = new JSONObject(new String(error.networkResponse.data,
                                    java.nio.charset.StandardCharsets.UTF_8));
                        } catch (JSONException ignored) {
                            // not a signup reply (proxy page, timeout): treat as unreachable
                        }
                    }
                    callback.on(parsed);
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("User-Agent", "Actifit-Android-App");
                return headers;
            }
        };
        // no silent Volley retries: a retry is always our own, timed by the server's retry_after
        request.setRetryPolicy(new DefaultRetryPolicy(30000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        request.setTag(SIGNUP_REQUEST_TAG);
        queue.add(request);
        return request;
    }

    /**
     * Asks the server for the memo (and, for a paid signup, the amount and payment account) for
     * the current details with this currency and promo code. Only a successful quote for details
     * still on screen changes the stored memo; `onQuoted` then runs.
     */
    private void requestQuote(String currency, String promo, Runnable onQuoted) {
        if (quoteInFlight || activityDestroyed) return;
        SignupDetails details = liveDetails(currency, promo);
        if (paidMemoShown() && !details.sameMemoAs(quotedDetails)) {
            // the user may already have paid the memo on screen: changing details makes a new one
            new AlertDialog.Builder(this)
                    .setTitle(R.string.signup_memo_change_title)
                    .setMessage(getString(R.string.signup_memo_change_message,
                            String.format(Locale.US, "%.3f", requiredCryptoAmount), selectedCurrency, generatedMemo))
                    .setPositiveButton(R.string.signup_memo_change_confirm, (dlg, w) -> sendQuote(details, onQuoted, false))
                    .setNegativeButton(R.string.signup_memo_change_keep, (dlg, w) -> restoreQuotedInputs())
                    .setOnCancelListener(dlg -> restoreQuotedInputs())
                    .show();
            return;
        }
        sendQuote(details, onQuoted, false);
    }

    /** A paid memo with its amount is on screen, so a payment may already be on its way. */
    private boolean paidMemoShown() {
        return quotedDetails != null && quotedPromo.isEmpty() && requiredCryptoAmount > 0
                && SignupOutcome.isServerMemo(generatedMemo);
    }

    /** Puts the quoted details back in the inputs and returns to the payment step. */
    private void restoreQuotedInputs() {
        if (quotedDetails == null) return;
        usernameInput.setText(quotedDetails.username);
        availableUsername = quotedDetails.username;
        isUsernameAvailable = true;
        emailInput.setText(quotedDetails.email);
        referralInput.setText(quotedDetails.referrer);
        promoInput.setText(quotedDetails.promo);
        restorePersistedCurrencySelection();
        if (recoveryState != null) {
            currentStep = 3;
            updateStepUI();
        }
    }

    /**
     * Re-quotes the saved details of a submitted signup to refresh the amount (the price moves;
     * the memo does not). Changes nothing unless the server returns the same memo. `after` always
     * runs.
     */
    private void refreshLockedAmount(Runnable after) {
        if (quotedDetails == null || quoteInFlight || activityDestroyed) {
            after.run();
            return;
        }
        sendQuote(quotedDetails, after, true);
    }

    private boolean refreshInFlight() {
        return quoteInFlight && refreshQuoteInFlight;
    }

    /** Refreshes a submitted signup's amount at most every few minutes (after a chain check, on return). */
    private void refreshLockedAmountIfDue() {
        if (!isLocked() || accountCreationHandled || accountCreationFailed || !quotedPromo.isEmpty()
                || !SignupOutcome.isServerMemo(generatedMemo)
                || confirmRequestInFlight || reconciliationInFlight || quoteInFlight
                || System.currentTimeMillis() - lastAmountRefreshAt < AMOUNT_REFRESH_INTERVAL_MS) {
            return;
        }
        refreshLockedAmount(() -> { });
    }

    private void sendQuote(SignupDetails details, Runnable onQuoted, boolean refreshOnly) {
        if (quoteInFlight || activityDestroyed) return;
        JSONObject body;
        try {
            body = signupBody(details, null);
        } catch (JSONException e) {
            if (refreshOnly) onQuoted.run(); else showError(getString(R.string.signup_error_request));
            return;
        }
        final long generation = ++quoteGeneration;
        quoteInFlight = true;
        refreshQuoteInFlight = refreshOnly;
        if (refreshOnly) lastAmountRefreshAt = System.currentTimeMillis();
        else showProgress(getString(R.string.signup_preparing_payment));
        activeQuoteRequest = postSignup(QUOTE_PATH, body, reply -> {
            if (generation != quoteGeneration || !canUpdateUi()) return; // cancelled or replaced
            quoteInFlight = false;
            activeQuoteRequest = null;
            if (!refreshOnly) hideProgress();
            SignupOutcome.Kind kind = SignupOutcome.ofQuote(reply == null ? null : reply.optString("reason", null));
            String memo = reply == null ? "" : reply.optString("memo");
            double amount = kind == SignupOutcome.Kind.QUOTED ? quotedAmount(reply, details.currency) : 0.0;
            if (refreshOnly) {
                if (kind == SignupOutcome.Kind.QUOTED && memo.equals(generatedMemo) && amount > 0) {
                    requiredCryptoAmount = amount;
                    updatePaymentInstructions();
                    persistCurrentRecoveryStateIfPresent();
                }
                onQuoted.run();
                return;
            }
            if (kind == SignupOutcome.Kind.QUOTED && SignupOutcome.isServerMemo(memo)) {
                if (!details.sameEntries(liveDetails(details.currency, details.promo))) {
                    return; // the user changed the details meanwhile: this memo is for old ones
                }
                if (details.promo.isEmpty() && !(amount > 0)) {
                    onQuoteFailed(getString(R.string.signup_quote_failed));
                    return;
                }
                quotedDetails = details;
                generatedMemo = memo;
                selectedCurrency = details.currency;
                quotedPromo = details.promo;
                requiredCryptoAmount = amount;
                afitReward = reply.optInt("afit_reward", 0);
                signupAccount = reply.optString("signup_account", SignupState.DEFAULT_SIGNUP_ACCOUNT);
                restorePersistedCurrencySelection();
                updatePaymentInstructions();
                onQuoted.run();
                return;
            }
            switch (kind) {
                case USERNAME_TAKEN:
                    isUsernameAvailable = false;
                    availableUsername = "";
                    currentStep = 1;
                    updateStepUI();
                    showUsernameStatus(getString(R.string.username_taken), Color.RED);
                    restorePersistedCurrencySelection();
                    break;
                case PROMO_INVALID: onQuoteFailed(getString(R.string.error_invalid_promo)); break;
                case INVALID_REFERRER: onQuoteFailed(getString(R.string.signup_invalid_referrer)); break;
                case INVALID_EMAIL: onQuoteFailed(getString(R.string.error_email_invalid)); break;
                case RATE_LIMITED: onQuoteFailed(getString(R.string.signup_rate_limited)); break;
                case DISABLED: onQuoteFailed(getString(R.string.signup_paused)); break;
                default: onQuoteFailed(getString(R.string.signup_quote_failed)); break;
            }
        });
    }

    /** The quoted amount in this currency, or 0 when missing or malformed. */
    private static double quotedAmount(JSONObject reply, String currency) {
        JSONObject amounts = reply.optJSONObject("amounts");
        if (amounts == null) return 0.0;
        try {
            return Double.parseDouble(amounts.optString(currency, "0"));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void onQuoteFailed(String message) {
        restorePersistedCurrencySelection();
        showError(message);
    }

    private void startConfirmPaymentRequest() {
        if (confirmRequestInFlight || reconciliationInFlight || quoteInFlight) return;
        if (!SignupOutcome.isServerMemo(generatedMemo) || quotedDetails == null) {
            showError(getString(R.string.signup_quote_failed));
            return;
        }
        if (!persistRecoveryState(SignupState.PHASE_REQUEST_SUBMITTED, true, false)) {
            return;
        }
        updateStepUI();
        showProgress(getString(quotedPromo.isEmpty()
                ? R.string.msg_verifying_payment : R.string.msg_applying_promo));
        final long requestGeneration = ++confirmRequestGeneration;
        lastSubmitKind = null;
        confirmRequestInFlight = true;
        confirmPaymentDeadline = () -> handleConfirmPaymentDeadline(requestGeneration);
        confirmDeadlineHandler.postDelayed(confirmPaymentDeadline, CONFIRM_PAYMENT_DEADLINE_MS);
        submitConfirmPaymentRequest(requestGeneration);
    }

    private void cancelSubmitTimers() {
        if (confirmPaymentDeadline != null) {
            confirmDeadlineHandler.removeCallbacks(confirmPaymentDeadline);
            confirmPaymentDeadline = null;
        }
        if (nextSubmitAttempt != null) {
            confirmDeadlineHandler.removeCallbacks(nextSubmitAttempt);
            nextSubmitAttempt = null;
        }
    }

    private void stopPaymentPolling() {
        confirmRequestGeneration++;
        cancelSubmitTimers();
        if (activeConfirmPaymentRequest != null) {
            activeConfirmPaymentRequest.cancel();
            activeConfirmPaymentRequest = null;
        }
        confirmRequestInFlight = false;
        hideProgress();
    }

    /** One submit attempt; repeats itself on "not yet" replies until the deadline handler stops it. */
    private void submitConfirmPaymentRequest(long requestGeneration) {
        nextSubmitAttempt = null;
        JSONObject body;
        try {
            body = signupBody(quotedDetails, generatedMemo);
        } catch (JSONException e) {
            finishCurrentConfirmRequest(requestGeneration);
            showError(getString(R.string.signup_error_request));
            return;
        }
        activeConfirmPaymentRequest = postSignup(SUBMIT_PATH, body, reply -> {
            if (!isCurrentConfirmRequest(requestGeneration)) return;
            activeConfirmPaymentRequest = null;
            if (reply == null) {
                // unreachable: the account may still have been created, so look on chain
                finishCurrentConfirmRequest(requestGeneration);
                showError(getString(R.string.signup_status_unknown));
                reconcileAccountExistence();
                return;
            }
            SignupOutcome.Kind kind = SignupOutcome.ofSubmit(reply.optBoolean("accountCreated", false),
                    reply.optString("reason", null), reply.optBoolean("retryable", false));
            switch (kind) {
                case CREATED:
                    completeAccountCreation();
                    break;
                case PENDING:
                case RETRY:
                    nextSubmitAttempt = () -> {
                        if (isCurrentConfirmRequest(requestGeneration)) submitConfirmPaymentRequest(requestGeneration);
                    };
                    confirmDeadlineHandler.postDelayed(nextSubmitAttempt,
                            SignupOutcome.retryDelayMs(reply.optInt("retry_after", 0)));
                    lastSubmitKind = kind;
                    break;
                case UNDERPAID:
                    finishCurrentConfirmRequest(requestGeneration);
                    // the price may have moved since the quote: show the current amount
                    refreshLockedAmount(this::showUnderpaidDialog);
                    break;
                case PROMO_INVALID:
                    finishCurrentConfirmRequest(requestGeneration);
                    resetToPaymentEntry();
                    showError(getString(R.string.error_invalid_promo));
                    break;
                case FAILED:
                    handleAccountCreationFailed(requestGeneration, reply.optString("reason", ""));
                    break;
                case RATE_LIMITED:
                    finishCurrentConfirmRequest(requestGeneration);
                    showError(getString(R.string.signup_rate_limited));
                    break;
                case DISABLED:
                    finishCurrentConfirmRequest(requestGeneration);
                    showError(getString(R.string.signup_paused));
                    break;
                default:
                    finishCurrentConfirmRequest(requestGeneration);
                    showError(getString(R.string.signup_error_request));
                    break;
            }
        });
    }

    private void showUnderpaidDialog() {
        if (!canUpdateUi()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.signup_underpaid_title)
                .setMessage(getString(R.string.signup_underpaid_message,
                        String.format(Locale.US, "%.3f", requiredCryptoAmount), selectedCurrency))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private boolean isCurrentConfirmRequest(long requestGeneration) {
        return !activityDestroyed && confirmRequestInFlight
                && requestGeneration == confirmRequestGeneration;
    }

    private void finishCurrentConfirmRequest(long requestGeneration) {
        if (!isCurrentConfirmRequest(requestGeneration)) {
            return;
        }
        cancelSubmitTimers();
        activeConfirmPaymentRequest = null;
        confirmRequestInFlight = false;
        confirmRequestGeneration++;
        hideProgress();
    }

    private void handleConfirmPaymentDeadline(long requestGeneration) {
        if (!isCurrentConfirmRequest(requestGeneration)) {
            return;
        }
        if (activeConfirmPaymentRequest != null) {
            activeConfirmPaymentRequest.cancel();
            activeConfirmPaymentRequest = null;
        }
        cancelSubmitTimers();
        confirmRequestInFlight = false;
        confirmRequestGeneration++;
        hideProgress();
        updateStepUI();
        if (lastSubmitKind == SignupOutcome.Kind.PENDING) {
            showError(getString(R.string.signup_payment_not_seen));
        } else if (lastSubmitKind == SignupOutcome.Kind.RETRY) {
            showError(getString(R.string.signup_server_busy));
        } else {
            // no reply at all: the account may still have been created, so look on chain
            showError(getString(R.string.signup_confirmation_deadline_reached));
            reconcileAccountExistence();
        }
    }

    private void reconcileAccountExistence() {
        if (activityDestroyed || reconciliationInFlight || accountCreationHandled) {
            return;
        }
        reconciliationInFlight = true;
        showProgress(getString(R.string.signup_checking_account_status));

        String username = signupUsername();
        JSONArray accounts = new JSONArray();
        accounts.put(username);
        JSONArray params = new JSONArray();
        params.put(accounts);

        hiveRequests.processRequest("condenser_api.get_accounts", params)
                .thenAccept(result -> runOnUiThread(() -> {
                    if (activityDestroyed || !reconciliationInFlight) {
                        return;
                    }
                    reconciliationInFlight = false;
                    hideProgress();
                    boolean accountExists = false;
                    for (int i = 0; i < result.length(); i++) {
                        JSONObject account = result.optJSONObject(i);
                        if (account != null && username.equals(account.optString("name"))) {
                            accountExists = true;
                            break;
                        }
                    }
                    boolean olderVersion = olderVersionCheckPending;
                    olderVersionCheckPending = false;
                    if (accountExists) {
                        completeAccountCreation();
                    } else if (olderVersion) {
                        showOlderVersionSignupDialog(generatedMemo);
                    } else {
                        updateStepUI();
                        showError(getString(R.string.signup_account_not_visible_yet));
                        // the price may have moved since the quote: refresh the amount
                        refreshLockedAmountIfDue();
                    }
                }))
                .exceptionally(error -> {
                    runOnUiThread(() -> {
                        if (activityDestroyed || !reconciliationInFlight) {
                            return;
                        }
                        reconciliationInFlight = false;
                        hideProgress();
                        updateStepUI();
                        showError(getString(R.string.signup_status_check_unavailable));
                        if (olderVersionCheckPending) {
                            olderVersionCheckPending = false;
                            showOlderVersionSignupDialog(generatedMemo);
                        }
                    });
                    return null;
                });
    }

    private void completeAccountCreation() {
        if (!canUpdateUi() || accountCreationHandled) {
            return;
        }
        stopPaymentPolling();
        reconciliationInFlight = false;
        if (!persistRecoveryState(SignupState.PHASE_ACCOUNT_CREATED, true, true)) {
            return;
        }
        accountCreationHandled = true;
        currentStep = 4;
        updateStepUI();
    }

    private void handleAccountCreationFailed(long requestGeneration, String reason) {
        if (!isCurrentConfirmRequest(requestGeneration) || accountCreationHandled) {
            return;
        }
        finishCurrentConfirmRequest(requestGeneration);
        failureReason = reason == null ? "" : reason;
        if (accountCreationFailed) {
            // asked again after a final failure: same answer
            showAccountCreationFailedDialog();
            return;
        }
        reconciliationInFlight = false;
        if (!persistRecoveryState(SignupState.PHASE_ACCOUNT_CREATION_FAILED, true, false)) {
            return;
        }
        accountCreationFailed = true;
        updateStepUI();
        showAccountCreationFailedDialog();
    }

    private void showAccountCreationFailedDialog() {
        if (activityDestroyed || isFinishing() || isDestroyed()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.signup_account_creation_failed_title)
                .setMessage("username_taken".equals(failureReason)
                        ? R.string.signup_failed_username_taken : R.string.signup_account_creation_failed_message)
                .setPositiveButton(R.string.btn_check_signup_status,
                        (dialog, which) -> reconcileAccountExistence())
                .setNegativeButton(R.string.signup_leave_for_now,
                        (dialog, which) -> finish())
                .setNeutralButton(R.string.signup_start_over,
                        (dialog, which) -> confirmStartOver(this::showAccountCreationFailedDialog))
                .show();
    }

    /** A signup saved by an older app version: its memo is not one the server can match. */
    private void showOlderVersionSignupDialog(String oldMemo) {
        if (!canUpdateUi()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.signup_older_version_title)
                .setMessage(getString(R.string.signup_older_version_message, oldMemo))
                .setPositiveButton(R.string.signup_start_over,
                        (dialog, which) -> confirmStartOver(() -> showOlderVersionSignupDialog(oldMemo)))
                .setNegativeButton(R.string.signup_leave_for_now, (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    /** Start over wipes the only copy of the master password, so it asks first and offers a copy. */
    private void confirmStartOver(Runnable onCancel) {
        if (!canUpdateUi()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.signup_start_over_title)
                .setMessage(R.string.signup_start_over_message)
                .setPositiveButton(R.string.signup_start_over, (dialog, which) -> startOver())
                .setNeutralButton(R.string.copy_master_password, (dialog, which) -> {
                    copyKeysToClipboard();
                    confirmStartOver(onCancel);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> onCancel.run())
                .setCancelable(false)
                .show();
    }

    /** Forgets this signup on the device and opens a fresh wizard. */
    private void startOver() {
        try {
            stopPaymentPolling();
            reconciliationInFlight = false; // a late chain reply must not save the old signup back
            signupStateStore.clear();
            recoveryState = null;
            generatedMasterPassword = "";
            startActivity(new Intent(this, SignupActivity.class));
            finish();
        } catch (SignupStateStore.SignupStateStoreException e) {
            showError(getString(R.string.signup_recovery_cleanup_error));
        }
    }

    private void generateKeys() {
        if (!generatedMasterPassword.isEmpty()) return;
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[40];
        random.nextBytes(bytes);
        String encoded = java.util.Base64.getEncoder().encodeToString(bytes);
        generatedMasterPassword = "P5" + encoded.substring(0, Math.min(encoded.length(), 51));
        masterPasswordDisplay.setText(generatedMasterPassword);
    }

    private void copyKeysToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getString(R.string.signup_master_password_clip_label),
                generatedMasterPassword);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PersistableBundle extras = clip.getDescription().getExtras();
            if (extras == null) {
                extras = new PersistableBundle();
            }
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.copy_success, Toast.LENGTH_SHORT).show();
    }

    /** Populates the final "save your keys" screen: username, master password and posting key. */
    private void populateFinalKeys() {
        String username = signupUsername();
        if (signupUsernameDisplay != null) {
            signupUsernameDisplay.setText(getString(R.string.signup_username_display, username));
        }
        if (masterPasswordDisplay != null && masterPasswordDisplay.getText().length() == 0) {
            masterPasswordDisplay.setText(generatedMasterPassword);
        }
        if (postingKeyDisplay != null) {
            postingKeyDisplay.setText(SignupKeys.postingWif(username, generatedMasterPassword));
        }
    }

    private void copyPostingKeyToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getString(R.string.signup_posting_key_clip_label),
                postingKeyDisplay.getText().toString());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PersistableBundle extras = clip.getDescription().getExtras();
            if (extras == null) {
                extras = new PersistableBundle();
            }
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.copy_success, Toast.LENGTH_SHORT).show();
    }

    private void showSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.signup_title)
                .setMessage(R.string.signup_success)
                .setPositiveButton(R.string.login_title, (dialog, which) -> {
                    try {
                        signupStateStore.clear();
                        recoveryState = null;
                        generatedMasterPassword = "";
                        generatedMemo = "";
                        masterPasswordDisplay.setText("");
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    } catch (SignupStateStore.SignupStateStoreException e) {
                        showError(getString(R.string.signup_recovery_cleanup_error));
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void showError(String message) {
        if (!canUpdateUi()) return;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showProgress(String message) {
        if (!canUpdateUi()) return;
        if (progress == null) {
            progress = new ProgressDialog(this);
            // Interruptible: the user can dismiss the loader (back button or the button below) to
            // return to the payment screen and re-read the memo / recipient. Cancelling aborts the
            // in-flight check; they can re-run "Check status" at any time.
            progress.setCancelable(true);
            progress.setCanceledOnTouchOutside(false);
            progress.setOnCancelListener(d -> onProgressCancelled());
            progress.setButton(android.content.DialogInterface.BUTTON_NEGATIVE,
                    getString(R.string.signup_view_payment_details), (d, w) -> d.cancel());
        }
        progress.setMessage(message);
        progress.show();
    }

    private void onProgressCancelled() {
        // Abort any in-flight confirmation / reconciliation and return to the payment screen so the
        // user can review the memo and recipient. A stale late response is ignored via the bumped
        // request generation.
        if (activeConfirmPaymentRequest != null) {
            activeConfirmPaymentRequest.cancel();
            activeConfirmPaymentRequest = null;
        }
        cancelSubmitTimers();
        confirmRequestInFlight = false;
        reconciliationInFlight = false;
        confirmRequestGeneration++;
        if (quoteInFlight && !refreshInFlight()) {
            // a late quote reply must not apply to details the user may change next
            quoteGeneration++;
            quoteInFlight = false;
            if (activeQuoteRequest != null) activeQuoteRequest.cancel();
            activeQuoteRequest = null;
            restorePersistedCurrencySelection();
        }
        if (currentStep == 3) {
            updateStepUI();
        }
    }

    private void hideProgress() {
        if (progress != null && progress.isShowing()) {
            progress.dismiss();
        }
    }

    private boolean canUpdateUi() {
        return !activityDestroyed && !isFinishing() && !isDestroyed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLockedAmountIfDue();
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        reconciliationInFlight = false;
        confirmDeadlineHandler.removeCallbacksAndMessages(null);
        if (queue != null) queue.cancelAll(SIGNUP_REQUEST_TAG);
        if (hiveRequests != null) hiveRequests.cancelRequests(SIGNUP_REQUEST_TAG);
        stopPaymentPolling();
        super.onDestroy();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}
