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
import com.android.volley.VolleyError;
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

    private static final long CONFIRM_PAYMENT_DEADLINE_MS = 60_000L;
    private static final Object SIGNUP_REQUEST_TAG = SignupActivity.class.getName();
    private static final double SIGNUP_COST_USD = 2.0;
    private static final double AFIT_REWARD_LOT_USD = 5.0;
    private static final int MAX_AFIT_REWARD_PER_LOT = 100;
    private static final String HIVE_PRICE_URL = "https://api.actifit.io/hivePrice";
    private static final String HBD_PRICE_URL = "https://api.coingecko.com/api/v3/simple/price?ids=hive_dollar&vs_currencies=usd";
    private static final String AFIT_PRICE_URL = "https://api2.actifit.io/curAFITPrice";
    private static final String CONFIRM_PAYMENT_PATH = "app/confirmPayment";
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
    private String generatedMemo = "";
    private final String paymentRecipient = "actifit.signup";

    private String selectedCurrency = "HIVE";
    private double requiredCryptoAmount = 0.0;
    private boolean liveCurrencyPriceAvailable = false;
    private int afitReward = -1;
    private final Handler confirmDeadlineHandler = new Handler(Looper.getMainLooper());
    private JsonObjectRequest activeConfirmPaymentRequest;
    private Runnable confirmPaymentDeadline;
    private long confirmRequestGeneration = 0;
    private boolean confirmRequestInFlight = false;
    private boolean reconciliationInFlight = false;
    private boolean accountCreationHandled = false;
    private boolean accountCreationFailed = false;
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
        if (recoveryState != null
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

            if (recoveryState != null && recoveryState.requestSubmitted) {
                restorePersistedCurrencySelection();
                return;
            }

            if (checkedId == R.id.button_hive) {
                selectedCurrency = "HIVE";
            } else if (checkedId == R.id.button_hbd) {
                selectedCurrency = "HBD";
            }

            requiredCryptoAmount = 0.0;
            liveCurrencyPriceAvailable = false;
            persistCurrentRecoveryStateIfPresent();
            fetchSelectedCurrencyPriceAndUpdateAmount();
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
            preparePaymentInfo();
            if (!persistRecoveryState(SignupState.PHASE_READY_FOR_PAYMENT, false, false)) {
                return;
            }
            currentStep = 3;
            updateStepUI();
        } else if (currentStep == 3) {
            // "Check status" / "Check payment": (re)submit the confirmation so the server re-checks
            // the on-chain payment and creates the account once it arrives. The server is idempotent
            // (memo-guarded), so re-submitting after a resume is safe — and necessary, since a plain
            // existence check can't trigger account creation.
            startConfirmPaymentRequest();
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
            requiredCryptoAmount = state.requiredCryptoAmount;
            liveCurrencyPriceAvailable = state.requiredCryptoAmount > 0;
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

        if (refreshIncompletePricing && !state.accountCreated) {
            if (state.afitReward < 0) {
                fetchAfitReward();
            }
            if (state.requiredCryptoAmount <= 0) {
                fetchSelectedCurrencyPriceAndUpdateAmount();
            }
        }
    }

    private boolean persistRecoveryState(String phase, boolean requestSubmitted,
            boolean accountCreated) {
        if (recoveryUnavailable) {
            return false;
        }
        SignupState state = new SignupState(
                usernameInput.getText().toString().trim().toLowerCase(Locale.US),
                generatedMasterPassword,
                generatedMemo,
                selectedCurrency,
                requiredCryptoAmount,
                afitReward,
                promoInput.getText().toString().trim(),
                emailInput.getText().toString().trim(),
                referralInput.getText().toString().trim(),
                phase,
                !SignupState.PHASE_READY_FOR_PAYMENT.equals(phase),
                requestSubmitted,
                accountCreated);
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

    private void preparePaymentInfo() {
        // Generate a unique memo only once for this signup attempt.
        if (generatedMemo.isEmpty()) {
            final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
            SecureRandom random = new SecureRandom();
            StringBuilder memo = new StringBuilder("signup:");

            for (int i = 0; i < 10; i++) {
                memo.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }

            generatedMemo = memo.toString();
        }
        fetchAfitReward();
        fetchSelectedCurrencyPriceAndUpdateAmount();
    }

    private void fetchAfitReward() {
        afitReward = -1;
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                AFIT_PRICE_URL,
                null,
                response -> {
                    if (!canUpdateUi()) return;
                    double afitPrice = response.optDouble("unit_price_usd", 0.0);
                    if (afitPrice <= 0) {
                        showError(getString(R.string.error_afit_price));
                        return;
                    }

                    int lots = Math.max(1, (int) Math.floor(SIGNUP_COST_USD / AFIT_REWARD_LOT_USD));
                    int rewardCap = MAX_AFIT_REWARD_PER_LOT * lots;
                    afitReward = (int) Math.floor(Math.min(SIGNUP_COST_USD / afitPrice, rewardCap));
                    persistCurrentRecoveryStateIfPresent();
                },
                error -> {
                    if (canUpdateUi()) showError(getString(R.string.error_afit_price));
                }
        );
        request.setTag(SIGNUP_REQUEST_TAG);
        queue.add(request);
    }

    private void fetchSelectedCurrencyPriceAndUpdateAmount() {
        requiredCryptoAmount = 0.0;
        liveCurrencyPriceAvailable = false;
        updatePaymentInstructions();
        if ("HBD".equals(selectedCurrency)) {
            fetchHbdPriceAndUpdateAmount();
            return;
        }

        final String requestedCurrency = selectedCurrency;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                HIVE_PRICE_URL,
                null,
                response -> {
                    if (!canUpdateUi() || !requestedCurrency.equals(selectedCurrency)) return;
                    try {
                        JSONObject hive = response.getJSONObject("hive");
                        double hiveUsdPrice = hive.getDouble("usd");

                        if (hiveUsdPrice <= 0) {
                            handlePriceUnavailable(requestedCurrency, R.string.error_hive_price);
                            return;
                        }

                        requiredCryptoAmount = roundCryptoAmount(SIGNUP_COST_USD / hiveUsdPrice);
                        liveCurrencyPriceAvailable = true;
                        updatePaymentInstructions();
                        persistCurrentRecoveryStateIfPresent();

                    } catch (JSONException e) {
                        handlePriceUnavailable(requestedCurrency, R.string.error_hive_price);
                    }
                },
                error -> handlePriceUnavailable(requestedCurrency, R.string.error_hive_price)
        );

        request.setTag(SIGNUP_REQUEST_TAG);
        queue.add(request);
    }

    private void fetchHbdPriceAndUpdateAmount() {
        final String requestedCurrency = selectedCurrency;
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                HBD_PRICE_URL,
                null,
                response -> {
                    if (!canUpdateUi() || !requestedCurrency.equals(selectedCurrency)) return;
                    try {
                        double hbdUsdPrice = response.getJSONObject("hive_dollar").getDouble("usd");
                        if (hbdUsdPrice <= 0) {
                            handlePriceUnavailable(requestedCurrency, R.string.error_hbd_price);
                            return;
                        }

                        requiredCryptoAmount = roundCryptoAmount(SIGNUP_COST_USD / hbdUsdPrice);
                        liveCurrencyPriceAvailable = true;
                        updatePaymentInstructions();
                        persistCurrentRecoveryStateIfPresent();
                    } catch (JSONException e) {
                        handlePriceUnavailable(requestedCurrency, R.string.error_hbd_price);
                    }
                },
                error -> handlePriceUnavailable(requestedCurrency, R.string.error_hbd_price)
        );
        request.setTag(SIGNUP_REQUEST_TAG);
        queue.add(request);
    }

    private double roundCryptoAmount(double amount) {
        return Double.parseDouble(String.format(Locale.US, "%.3f", amount));
    }

    private void persistCurrentRecoveryStateIfPresent() {
        if (recoveryState != null) {
            persistRecoveryState(recoveryState.phase, recoveryState.requestSubmitted,
                    recoveryState.accountCreated);
        }
    }

    private void updateCurrencyToggleState() {
        boolean enabled = recoveryState == null || !recoveryState.requestSubmitted;
        currencyToggle.setEnabled(enabled);
        findViewById(R.id.button_hive).setEnabled(enabled);
        findViewById(R.id.button_hbd).setEnabled(enabled);
    }

    private void restorePersistedCurrencySelection() {
        isRestoringState = true;
        try {
            selectedCurrency = recoveryState.selectedCurrency;
            currencyToggle.check("HBD".equals(selectedCurrency)
                    ? R.id.button_hbd : R.id.button_hive);
        } finally {
            isRestoringState = false;
        }
    }

    private void handlePriceUnavailable(String requestedCurrency, int errorMessage) {
        if (!canUpdateUi() || !requestedCurrency.equals(selectedCurrency)) return;
        requiredCryptoAmount = 0.0;
        liveCurrencyPriceAvailable = false;
        updatePaymentInstructions();
        persistCurrentRecoveryStateIfPresent();
        showError(getString(errorMessage));
    }

    private void updatePaymentInstructions() {
        String instructions;
        if (liveCurrencyPriceAvailable && requiredCryptoAmount > 0) {
            String amount = String.format(Locale.US, "%.3f", requiredCryptoAmount);
            instructions = getString(R.string.signup_payment_instructions, amount,
                    selectedCurrency, paymentRecipient, generatedMemo);
        } else {
            instructions = getString(R.string.signup_payment_instructions_price_unavailable,
                    selectedCurrency, paymentRecipient, generatedMemo);
        }

        verificationDesc.setText(Html.fromHtml(instructions));
    }

    private void startConfirmPaymentRequest() {
        if (confirmRequestInFlight || reconciliationInFlight) return;
        String msg = promoInput.getText().toString().trim().length() > 0 ?
                getString(R.string.msg_applying_promo) : getString(R.string.msg_verifying_payment);
        showProgress(msg);
        submitConfirmPaymentRequest();
    }

    private void stopPaymentPolling() {
        confirmRequestGeneration++;
        if (confirmPaymentDeadline != null) {
            confirmDeadlineHandler.removeCallbacks(confirmPaymentDeadline);
            confirmPaymentDeadline = null;
        }
        if (activeConfirmPaymentRequest != null) {
            activeConfirmPaymentRequest.cancel();
            activeConfirmPaymentRequest = null;
        }
        confirmRequestInFlight = false;
        hideProgress();
    }

    private void submitConfirmPaymentRequest() {
        String promo = promoInput.getText().toString().trim();
        boolean isPromoSignup = !promo.isEmpty();
        if (afitReward < 0
                || (!isPromoSignup && (!liveCurrencyPriceAvailable || requiredCryptoAmount <= 0))) {
            stopPaymentPolling();
            updatePaymentInstructions();
            if (afitReward < 0) fetchAfitReward();
            if (!isPromoSignup && (!liveCurrencyPriceAvailable || requiredCryptoAmount <= 0)) {
                fetchSelectedCurrencyPriceAndUpdateAmount();
            }
            showError(getString(R.string.error_signup_pricing));
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("new_account", usernameInput.getText().toString().trim().toLowerCase());
            // NOTE: The server currently expects generatedMasterPassword to facilitate account creation.
            // This trust model implies that the backend manages the final account_create transaction.
            body.put("new_pass", generatedMasterPassword);
            body.put("sent_cur", selectedCurrency);
            body.put("usd_invest", promo.isEmpty() ? SIGNUP_COST_USD : 0.0);
            double cryptoAmountForRequest = isPromoSignup ? 0.0 : requiredCryptoAmount;
            body.put("steem_invest", String.format(Locale.US, "%.3f", cryptoAmountForRequest));
            body.put("memo", generatedMemo);
            String email = emailInput.getText().toString().trim();
            if (!email.isEmpty()) {
                body.put("email", email);
            }
            String referrer = referralInput.getText().toString().trim();
            if (!referrer.isEmpty()) {
                body.put("referrer", referrer);
            }
            body.put("afit_reward", afitReward);

            if (!promo.isEmpty()) {
                body.put("promo_code", promo);
            }

            body.put("cur_bchain", "HIVE|");
        } catch (JSONException e) {
            stopPaymentPolling();
            showError(getString(R.string.signup_error_request));
            return;
        }

        if (!persistRecoveryState(SignupState.PHASE_REQUEST_SUBMITTED, true, false)) {
            return;
        }
        updateStepUI();

        final long requestGeneration = ++confirmRequestGeneration;
        String confirmPaymentUrl = getString(R.string.live_server) + CONFIRM_PAYMENT_PATH;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, confirmPaymentUrl, body,
                response -> {
                    if (!isCurrentConfirmRequest(requestGeneration)) {
                        return;
                    }
                    boolean accountCreated =
                            response.optBoolean("accountCreated", false);
                    Object paymentReceivedValue = response.opt("paymentReceivedTx");
                    String paymentReceivedTx = paymentReceivedValue instanceof String
                            ? ((String) paymentReceivedValue).trim() : "";

                    if (accountCreated) {
                        completeAccountCreation();
                    } else if (!paymentReceivedTx.isEmpty()) {
                        handleAccountCreationFailed(requestGeneration);
                    } else if (isPromoSignup) {
                        // A promo signup involves no on-chain payment, so "no account + no tx"
                        // means the promo was invalid. Reset to payment entry (re-enables the
                        // currency toggle, reverts the button) and say so plainly.
                        finishCurrentConfirmRequest(requestGeneration);
                        resetToPaymentEntry();
                        showError(getString(R.string.error_invalid_promo));
                    } else {
                        finishCurrentConfirmRequest(requestGeneration);
                        showError(getString(R.string.signup_status_unknown));
                        reconcileAccountExistence();
                    }
                },
                error -> {
                    if (!isCurrentConfirmRequest(requestGeneration)) {
                        return;
                    }
                    finishCurrentConfirmRequest(requestGeneration);
                    if (error.networkResponse != null
                            && error.networkResponse.statusCode == 429) {
                        showError(getString(R.string.signup_confirmation_rate_limited));
                        return;
                    }
                    showError(getString(R.string.signup_status_unknown));
                    reconcileAccountExistence();
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("User-Agent", "Actifit-Android-App");
                return headers;
            }
        };

        // Disable Volley's internal retries; the explicit 60-second deadline owns cancellation.
        request.setRetryPolicy(new DefaultRetryPolicy(
                30000,
                0,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        request.setTag(SIGNUP_REQUEST_TAG);

        activeConfirmPaymentRequest = request;
        confirmRequestInFlight = true;
        confirmPaymentDeadline = () -> handleConfirmPaymentDeadline(requestGeneration);
        confirmDeadlineHandler.postDelayed(confirmPaymentDeadline, CONFIRM_PAYMENT_DEADLINE_MS);
        queue.add(request);
    }

    private boolean isCurrentConfirmRequest(long requestGeneration) {
        return !activityDestroyed && confirmRequestInFlight
                && requestGeneration == confirmRequestGeneration;
    }

    private void finishCurrentConfirmRequest(long requestGeneration) {
        if (!isCurrentConfirmRequest(requestGeneration)) {
            return;
        }
        if (confirmPaymentDeadline != null) {
            confirmDeadlineHandler.removeCallbacks(confirmPaymentDeadline);
            confirmPaymentDeadline = null;
        }
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
        confirmPaymentDeadline = null;
        confirmRequestInFlight = false;
        confirmRequestGeneration++;
        hideProgress();
        persistRecoveryState(SignupState.PHASE_REQUEST_SUBMITTED, true, false);
        updateStepUI();
        showError(getString(R.string.signup_confirmation_deadline_reached));
        reconcileAccountExistence();
    }

    private void reconcileAccountExistence() {
        if (activityDestroyed || reconciliationInFlight || accountCreationHandled) {
            return;
        }
        reconciliationInFlight = true;
        showProgress(getString(R.string.signup_checking_account_status));

        String username = usernameInput.getText().toString().trim().toLowerCase(Locale.US);
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
                    if (accountExists) {
                        completeAccountCreation();
                    } else {
                        updateStepUI();
                        showError(getString(R.string.signup_account_not_visible_yet));
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
                    });
                    return null;
                });
    }

    private void completeAccountCreation() {
        if (activityDestroyed || accountCreationHandled) {
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

    private void handleAccountCreationFailed(long requestGeneration) {
        if (!isCurrentConfirmRequest(requestGeneration) || accountCreationHandled
                || accountCreationFailed) {
            return;
        }
        finishCurrentConfirmRequest(requestGeneration);
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
                .setMessage(R.string.signup_account_creation_failed_message)
                .setPositiveButton(R.string.btn_check_signup_status,
                        (dialog, which) -> reconcileAccountExistence())
                .setNegativeButton(R.string.signup_leave_for_now,
                        (dialog, which) -> finish())
                .show();
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

    /**
     * Derives the Hive posting private key (WIF) from the master password exactly as the backend
     * does (dhive PrivateKey.fromLogin): sha256(username + role + password) -> WIF with the mainnet
     * 0x80 version byte, uncompressed. This is the key the user logs in to Actifit with.
     */
    private String derivePostingKey(String username, String masterPassword) {
        try {
            byte[] seed = (username + "posting" + masterPassword)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] priv = org.bitcoinj.core.Sha256Hash.hash(seed);
            org.bitcoinj.core.ECKey key = org.bitcoinj.core.ECKey.fromPrivate(priv, false);
            return key.getPrivateKeyAsWiF(org.bitcoinj.params.MainNetParams.get());
        } catch (Exception e) {
            android.util.Log.e(MainActivity.TAG, "posting key derivation failed: " + e.getMessage());
            return "";
        }
    }

    /** Populates the final "save your keys" screen: username, master password and posting key. */
    private void populateFinalKeys() {
        String username = usernameInput.getText().toString().trim().toLowerCase(Locale.US);
        if (signupUsernameDisplay != null) {
            signupUsernameDisplay.setText(getString(R.string.signup_username_display, username));
        }
        if (masterPasswordDisplay != null && masterPasswordDisplay.getText().length() == 0) {
            masterPasswordDisplay.setText(generatedMasterPassword);
        }
        if (postingKeyDisplay != null) {
            postingKeyDisplay.setText(derivePostingKey(username, generatedMasterPassword));
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

    private void handleNetworkError(com.android.volley.VolleyError error) {
        String errorMsg = getString(R.string.error_connection_failed);
        if (error.networkResponse != null) {
            errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
            try {
                String responseData = new String(error.networkResponse.data, "UTF-8");
                JSONObject errorObj = new JSONObject(responseData);
                if (errorObj.has("error")) {
                    errorMsg = errorObj.getString("error");
                }
            } catch (Exception e) {
                errorMsg = getString(R.string.error_connection_failed)
                        + " (Status: " + error.networkResponse.statusCode + ")";
            }
        }
        showError(errorMsg);
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
        if (confirmPaymentDeadline != null) {
            confirmDeadlineHandler.removeCallbacks(confirmPaymentDeadline);
            confirmPaymentDeadline = null;
        }
        confirmRequestInFlight = false;
        reconciliationInFlight = false;
        confirmRequestGeneration++;
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
