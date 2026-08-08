package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

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

    private static final String TAG = "SignupActivity";
    private static final int MAX_POLL_ATTEMPTS = 60; // 5 minutes with 5s delay
    private static final double SIGNUP_COST_USD = 2.0;
    private static final double PROMO_HIVE_FALLBACK_USD_PRICE = 0.10;
    private static final double PROMO_HBD_FALLBACK_USD_PRICE = 1.00;
    private static final double AFIT_REWARD_LOT_USD = 5.0;
    private static final int MAX_AFIT_REWARD_PER_LOT = 100;
    private static final String HIVE_PRICE_URL = "https://api.actifit.io/hivePrice";
    private static final String HBD_PRICE_URL = "https://api.coingecko.com/api/v3/simple/price?ids=hive_dollar&vs_currencies=usd";
    private static final String AFIT_PRICE_URL = "https://api2.actifit.io/curAFITPrice";
    private static final String CONFIRM_PAYMENT_URL = "https://api.actifit.io/confirmPayment";
    private static final String TERMS_URL = "https://actifit.io/terms-conditions";
    private static final String PRIVACY_URL = "https://actifit.io/privacy-policy";

    private int currentStep = 1;
    private LinearProgressIndicator progressBar;
    private MaterialCardView step1Container, step2Container, step3Container, step4Container;
    private TextInputLayout emailInputLayout;
    private TextInputEditText usernameInput, emailInput, referralInput, promoInput, masterPasswordDisplay;
    private TextView usernameStatus, verificationDesc;
    private Button btnNext, btnPrev, btnCheckUsername, btnCopyKeys;
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
    private Handler pollHandler = new Handler(Looper.getMainLooper());
    private boolean isPolling = false;
    private int pollAttempts = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        hiveRequests = new HiveRequests(this);
        queue = Volley.newRequestQueue(this);

        initViews();
        updateStepUI();
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
        usernameStatus = findViewById(R.id.username_status);
        verificationDesc = findViewById(R.id.verification_desc);
        currencyToggle = findViewById(R.id.currency_toggle);
        currencyToggle.check(R.id.button_hive);

        currencyToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }

            if (checkedId == R.id.button_hive) {
                selectedCurrency = "HIVE";
            } else if (checkedId == R.id.button_hbd) {
                selectedCurrency = "HBD";
            }

            requiredCryptoAmount = 0.0;
            liveCurrencyPriceAvailable = false;
            fetchSelectedCurrencyPriceAndUpdateAmount();
        });

        btnNext = findViewById(R.id.btn_next);
        btnPrev = findViewById(R.id.btn_prev);
        btnCheckUsername = findViewById(R.id.btn_check_username);
        btnCopyKeys = findViewById(R.id.btn_copy_keys);
        cbTos = findViewById(R.id.cb_tos);
        setupTermsLinks();
        cbBackedUp = findViewById(R.id.cb_backed_up);

        btnCheckUsername.setOnClickListener(v -> checkUsernameAvailability());
        btnNext.setOnClickListener(v -> handleNextStep());
        btnPrev.setOnClickListener(v -> handlePrevStep());
        btnCopyKeys.setOnClickListener(v -> copyKeysToClipboard());

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
        int termsEnd = termsStart + termsText.length();

        int privacyStart = text.indexOf(privacyText);
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

        cbTos.setText(spannable);
        cbTos.setMovementMethod(LinkMovementMethod.getInstance());
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
            String username = usernameInput.getText().toString().trim().toLowerCase();
            if (username.isEmpty()) {
                usernameInput.setError(getString(R.string.field_required));
                return;
            }
            if (!username.matches("^[a-z][a-z0-9\\-.]{2,15}$")) {
                usernameInput.setError(getString(R.string.username_invalid));
                return;
            }
            if (!isUsernameAvailable || !username.equals(availableUsername)) {
                showUsernameStatus(
                        getString(R.string.username_check_required),
                        Color.RED
                );
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
            currentStep = 3;
            updateStepUI();
        } else if (currentStep == 3) {
            startPaymentPolling();
        } else if (currentStep == 4) {
            // Secure keys step finished, show final success dialog
            showSuccessDialog();
        }
    }

    private void handlePrevStep() {
        if (currentStep > 1) {
            if (currentStep == 3) stopPaymentPolling();
            currentStep--;
            updateStepUI();
        } else {
            finish();
        }
    }

    private void updateStepUI() {
        // Smooth transitions
        animateTransition(step1Container, currentStep == 1);
        animateTransition(step2Container, currentStep == 2);
        animateTransition(step3Container, currentStep == 3);
        animateTransition(step4Container, currentStep == 4);

        progressBar.setProgress(currentStep * 25);

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
        if (promoInput.getText().toString().trim().length() > 0) {
            btnNext.setText(R.string.btn_claim_promo);
        } else {
            btnNext.setText(R.string.btn_check_payment);
        }
    }

    private void validateStep2() {
        if (currentStep != 2) return;
        boolean isValid = cbTos.isChecked();
        btnNext.setEnabled(isValid);
    }

    private void checkUsernameAvailability() {
        String username = usernameInput.getText().toString().trim().toLowerCase();
        if (username.isEmpty()) {
            usernameInput.setError(getString(R.string.field_required));
            return;
        }

        if (!username.matches("^[a-z][a-z0-9\\-.]{2,15}$")) {
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
                    btnCheckUsername.setEnabled(true);
                    String currentUsername = usernameInput.getText().toString().trim().toLowerCase(Locale.US);
                    if (!username.equals(currentUsername)) {
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
                        btnCheckUsername.setEnabled(true);
                        isUsernameAvailable = false;
                        availableUsername = "";
                        showUsernameStatus(getString(R.string.error_check_availability), Color.RED);
                    });
                    return null;
                });
    }

    private void showUsernameStatus(String message, int color) {
        usernameStatus.setVisibility(View.VISIBLE);
        usernameStatus.setText(message);
        usernameStatus.setTextColor(color);
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
                    double afitPrice = response.optDouble("unit_price_usd", 0.0);
                    if (afitPrice <= 0) {
                        showError(getString(R.string.error_afit_price));
                        return;
                    }

                    int lots = Math.max(1, (int) Math.floor(SIGNUP_COST_USD / AFIT_REWARD_LOT_USD));
                    int rewardCap = MAX_AFIT_REWARD_PER_LOT * lots;
                    afitReward = (int) Math.floor(Math.min(SIGNUP_COST_USD / afitPrice, rewardCap));
                },
                error -> showError(getString(R.string.error_afit_price))
        );
        queue.add(request);
    }

    private void fetchSelectedCurrencyPriceAndUpdateAmount() {
        requiredCryptoAmount = 0.0;
        liveCurrencyPriceAvailable = false;
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
                    try {
                        JSONObject hive = response.getJSONObject("hive");
                        double hiveUsdPrice = hive.getDouble("usd");

                        if (hiveUsdPrice <= 0) {
                            showError(getString(R.string.error_hive_price));
                            return;
                        }

                        if (!requestedCurrency.equals(selectedCurrency)) {
                            return;
                        }
                        requiredCryptoAmount = roundCryptoAmount(SIGNUP_COST_USD / hiveUsdPrice);
                        liveCurrencyPriceAvailable = true;
                        updatePaymentInstructions();

                    } catch (JSONException e) {
                        showError(getString(R.string.error_hive_price));
                    }
                },
                error -> showError(getString(R.string.error_hive_price))
        );

        queue.add(request);
    }

    private void fetchHbdPriceAndUpdateAmount() {
        final String requestedCurrency = selectedCurrency;
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                HBD_PRICE_URL,
                null,
                response -> {
                    try {
                        double hbdUsdPrice = response.getJSONObject("hive_dollar").getDouble("usd");
                        if (hbdUsdPrice <= 0) {
                            showError(getString(R.string.error_hbd_price));
                            return;
                        }

                        if (!requestedCurrency.equals(selectedCurrency)) {
                            return;
                        }
                        requiredCryptoAmount = roundCryptoAmount(SIGNUP_COST_USD / hbdUsdPrice);
                        liveCurrencyPriceAvailable = true;
                        updatePaymentInstructions();
                    } catch (JSONException e) {
                        showError(getString(R.string.error_hbd_price));
                    }
                },
                error -> showError(getString(R.string.error_hbd_price))
        );
        queue.add(request);
    }

    private double roundCryptoAmount(double amount) {
        return Double.parseDouble(String.format(Locale.US, "%.3f", amount));
    }

    private double getPromoCryptoAmount() {
        if (liveCurrencyPriceAvailable && requiredCryptoAmount > 0) {
            return requiredCryptoAmount;
        }

        double fallbackUsdPrice = "HBD".equals(selectedCurrency)
                ? PROMO_HBD_FALLBACK_USD_PRICE
                : PROMO_HIVE_FALLBACK_USD_PRICE;
        return roundCryptoAmount(SIGNUP_COST_USD / fallbackUsdPrice);
    }

    private void updatePaymentInstructions() {
        String instructions =
                "To create your account, please send <b>" +
                        String.format(Locale.US, "%.3f", requiredCryptoAmount) +
                        " " + selectedCurrency + "</b> to:<br/><br/>" +
                        "Account: <b>" + paymentRecipient + "</b><br/>" +
                        "Memo: <b>" + generatedMemo + "</b><br/><br/>" +
                        "Alternatively, if you have a <b>Promo Code</b>, enter it below to skip the payment.";

        verificationDesc.setText(Html.fromHtml(instructions));
    }

    private void startPaymentPolling() {
        if (isPolling) return;
        isPolling = true;
        pollAttempts = 0;
        String msg = promoInput.getText().toString().trim().length() > 0 ? 
                getString(R.string.msg_applying_promo) : getString(R.string.msg_verifying_payment);
        showProgress(msg);
        pollPayment();
    }

    private void stopPaymentPolling() {
        isPolling = false;
        pollHandler.removeCallbacksAndMessages(null);
        hideProgress();
    }

    private void pollPayment() {
        if (!isPolling) return;

        String promo = promoInput.getText().toString().trim();
        boolean isPromoSignup = !promo.isEmpty();
        if (afitReward < 0
                || (!isPromoSignup && (!liveCurrencyPriceAvailable || requiredCryptoAmount <= 0))) {
            stopPaymentPolling();
            showError(getString(R.string.error_signup_pricing));
            return;
        }

        if (pollAttempts >= MAX_POLL_ATTEMPTS) {
            stopPaymentPolling();
            showError(getString(R.string.payment_timeout));
            return;
        }

        pollAttempts++;
        JSONObject body = new JSONObject();
        try {
            body.put("new_account", usernameInput.getText().toString().trim().toLowerCase());
            // NOTE: The server currently expects generatedMasterPassword to facilitate account creation.
            // This trust model implies that the backend manages the final account_create transaction.
            body.put("new_pass", generatedMasterPassword);
            body.put("sent_cur", selectedCurrency);
            body.put("usd_invest", promo.isEmpty() ? SIGNUP_COST_USD : 0.0);
            double cryptoAmountForRequest = isPromoSignup
                    ? getPromoCryptoAmount()
                    : requiredCryptoAmount;
            body.put("steem_invest", String.format(Locale.US, "%.3f", cryptoAmountForRequest));
            body.put("memo", generatedMemo);
            body.put("email", emailInput.getText().toString().trim());
            body.put("referrer", referralInput.getText().toString().trim());
            body.put("afit_reward", afitReward);

            if (!promo.isEmpty()) {
                body.put("promo_code", promo);
            }

            body.put("confirm_payment_token", getString(R.string.sec_param_val));
            body.put("cur_bchain", "HIVE|");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, CONFIRM_PAYMENT_URL, body,
                response -> {
                    boolean accountCreated =
                            response.optBoolean("accountCreated", false);

                    if (accountCreated) {
                        stopPaymentPolling();
                        currentStep = 4;
                        updateStepUI();
                    } else {
                        if (!promo.isEmpty()) {
                            stopPaymentPolling();
                            showError(getString(R.string.error_invalid_promo));
                        } else {
                            pollHandler.postDelayed(this::pollPayment, 5000);
                        }
                    }
                },
                error -> {
                    if (!promoInput.getText().toString().trim().isEmpty()) {
                        stopPaymentPolling();
                        handleNetworkError(error);
                    } else {
                        pollHandler.postDelayed(this::pollPayment, 5000);
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("User-Agent", "Actifit-Android-App");
                return headers;
            }
        };

        // FIX: Set timeout to 30 seconds and disable Volley's internal retries 
        // to prevent overlapping requests
        request.setRetryPolicy(new DefaultRetryPolicy(
                30000, 
                0, 
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(request);
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
        ClipData clip = ClipData.newPlainText("Actifit Master Password", generatedMasterPassword);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.copy_success, Toast.LENGTH_SHORT).show();
    }

    private void handleNetworkError(com.android.volley.VolleyError error) {
        String errorMsg = getString(R.string.error_connection_failed);
        if (error.networkResponse != null) {
            errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
            try {
                String responseData = new String(error.networkResponse.data, "UTF-8");
                Log.e(TAG, "Error Response Body: " + responseData);
                JSONObject errorObj = new JSONObject(responseData);
                if (errorObj.has("error")) {
                    errorMsg = errorObj.getString("error");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse error body");
            }
        }
        showError(errorMsg);
    }

    private void showSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.signup_title)
                .setMessage(R.string.signup_success)
                .setPositiveButton(R.string.login_title, (dialog, which) -> {
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showProgress(String message) {
        if (progress == null) {
            progress = new ProgressDialog(this);
            // Allow canceling to give user a way out as requested
            progress.setCancelable(true);
            progress.setOnCancelListener(dialog -> stopPaymentPolling());
        }
        progress.setMessage(message);
        progress.show();
    }

    private void hideProgress() {
        if (progress != null && progress.isShowing()) {
            progress.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        stopPaymentPolling();
        super.onDestroy();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}
