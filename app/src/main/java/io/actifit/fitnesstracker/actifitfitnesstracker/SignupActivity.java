package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
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
import java.util.Map;

public class SignupActivity extends BaseActivity {

    private static final String TAG = "SignupActivity";

    private int currentStep = 1;
    private LinearProgressIndicator progressBar;
    private MaterialCardView step1Container, step2Container, step3Container, step4Container;
    private TextInputLayout emailInputLayout;
    private TextInputEditText usernameInput, emailInput, referralInput, promoInput, masterPasswordDisplay;
    private TextView usernameStatus, verificationDesc;
    private Button btnNext, btnPrev, btnCheckUsername, btnCopyKeys;
    private MaterialCheckBox cbTos, cbBackedUp;

    private HiveRequests hiveRequests;
    private RequestQueue queue;
    private ProgressDialog progress;

    private String generatedMasterPassword = "";
    private boolean isUsernameAvailable = false;
    private String generatedMemo = "";
    private final String paymentRecipient = "actifit.signup";
    private final double signupCostUsd = 2.0;
    
    private Handler pollHandler = new Handler(Looper.getMainLooper());
    private boolean isPolling = false;

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

        btnNext = findViewById(R.id.btn_next);
        btnPrev = findViewById(R.id.btn_prev);
        btnCheckUsername = findViewById(R.id.btn_check_username);
        btnCopyKeys = findViewById(R.id.btn_copy_keys);
        cbTos = findViewById(R.id.cb_tos);
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

        promoInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (currentStep == 3) {
                    updateNextButtonStep3();
                }
            }
        });
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
            currentStep = 2;
            updateStepUI();
        } else if (currentStep == 2) {
            String email = emailInput.getText().toString().trim();
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                if (emailInputLayout != null) {
                    emailInputLayout.setError("Please enter a correct email address");
                } else {
                    emailInput.setError("Please enter a correct email address");
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
            // Secure keys step finished, go to login
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
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
        // Smooth card transitions using AlphaAnimation
        animateTransition(step1Container, currentStep == 1);
        animateTransition(step2Container, currentStep == 2);
        animateTransition(step3Container, currentStep == 3);
        animateTransition(step4Container, currentStep == 4);

        progressBar.setProgress(currentStep * 25);

        // Consistency: Always label button as "Back"
        btnPrev.setText("Back");
        
        // Hide Back button on final step once account is confirmed to prevent navigation errors
        if (currentStep == 4) {
            btnPrev.setVisibility(View.GONE);
        } else {
            btnPrev.setVisibility(View.VISIBLE);
        }

        if (currentStep == 1) {
            btnNext.setText(R.string.proceed);
            btnNext.setEnabled(true); // Proceed without mandatory availability check as requested
        } else if (currentStep == 2) {
            btnNext.setText(R.string.proceed);
            validateStep2();
        } else if (currentStep == 3) {
            updateNextButtonStep3();
            btnNext.setEnabled(true);
        } else {
            btnNext.setText("Go to Login");
            btnNext.setEnabled(cbBackedUp.isChecked()); // Force key security confirmation
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
            btnNext.setText("Claim Promo");
        } else {
            btnNext.setText("Check Payment");
        }
    }

    private void validateStep2() {
        if (currentStep != 2) return;
        // Proceed button enabled if ToS is checked. Email format error handled in handleNextStep for better UX.
        btnNext.setEnabled(cbTos.isChecked());
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
                    if (result.length() == 0) {
                        isUsernameAvailable = true;
                        showUsernameStatus(getString(R.string.username_available), Color.parseColor("#4CAF50"));
                    } else {
                        isUsernameAvailable = false;
                        showUsernameStatus(getString(R.string.username_taken), Color.RED);
                    }
                }))
                .exceptionally(ex -> {
                    runOnUiThread(() -> {
                        btnCheckUsername.setEnabled(true);
                        showUsernameStatus("Error checking availability", Color.RED);
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
        // Generate a unique memo for this signup attempt
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        String encoded = java.util.Base64.getEncoder().encodeToString(bytes);
        generatedMemo = "signup:" + encoded.substring(0, Math.min(encoded.length(), 10));

        String instructions = "To create your account, please send <b>$" + signupCostUsd + " USD</b> worth of HIVE or HBD to: <br/><br/>" +
                "Account: <b>" + paymentRecipient + "</b><br/>" +
                "Memo: <b>" + generatedMemo + "</b><br/><br/>" +
                "Alternatively, if you have a <b>Promo Code</b>, enter it below to skip the payment.";
        
        verificationDesc.setText(Html.fromHtml(instructions));
    }

    private void startPaymentPolling() {
        if (isPolling) return;
        isPolling = true;
        String msg = promoInput.getText().toString().trim().length() > 0 ? "Applying Promo Code..." : "Verifying payment...";
        showProgress(msg);
        pollPayment();
    }

    private void stopPaymentPolling() {
        isPolling = false;
        hideProgress();
    }

    private void pollPayment() {
        if (!isPolling) return;

        JSONObject body = new JSONObject();
        try {
            body.put("new_account", usernameInput.getText().toString().trim().toLowerCase());
            body.put("new_pass", generatedMasterPassword);
            body.put("sent_cur", "HIVE"); 
            body.put("usd_invest", signupCostUsd);
            body.put("memo", generatedMemo);
            body.put("email", emailInput.getText().toString().trim());
            body.put("referrer", referralInput.getText().toString().trim());
            body.put("promo_code", promoInput.getText().toString().trim());
            body.put("cur_bchain", "HIVE|");
            body.put(getString(R.string.sec_param), getString(R.string.sec_param_val));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        String url = "https://actifit.io/api/proxy/confirmPayment";

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    try {
                        boolean created = response.optBoolean("accountCreated", false);
                        String tx = response.optString("paymentReceivedTx", "");
                        String errorMsg = response.optString("error", "");

                        if (created) {
                            stopPaymentPolling();
                            currentStep = 4;
                            updateStepUI();
                        } else if (!tx.isEmpty()) {
                            // Payment detected but account not yet created
                            progress.setMessage("Payment detected. Creating account...");
                            pollHandler.postDelayed(this::pollPayment, 3000);
                        } else {
                            // No payment yet
                            hideProgress();
                            if (!errorMsg.isEmpty()) {
                                showError(errorMsg);
                            } else if (promoInput.getText().toString().trim().length() > 0) {
                                showError("Invalid or expired Promo Code.");
                            } else {
                                showError("Payment not detected yet. Please ensure you sent the correct amount and memo.");
                            }
                            isPolling = false;
                        }
                    } catch (Exception e) {
                        handleNetworkError(new VolleyError("Parsing error"));
                    }
                },
                error -> {
                    hideProgress();
                    handleNetworkError(error);
                    isPolling = false;
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("User-Agent", "Actifit-Android-App");
                return headers;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(15000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(req);
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
        String errorMsg = "Connection failed";
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
            progress.setCancelable(false);
        }
        progress.setMessage(message);
        progress.show();
    }

    private void hideProgress() {
        if (progress != null && progress.isShowing()) {
            progress.dismiss();
        }
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}
