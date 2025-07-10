
package io.actifit.fitnesstracker.actifitfitnesstracker;

import static io.actifit.fitnesstracker.actifitfitnesstracker.MainActivity.username;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WorkoutWizardActivity extends BaseActivity
    implements SavedWorkoutsAdapter.OnWorkoutSelectedListener {

    // --- View Variables ---
    private ProgressBar progressBar;
    private TextView workoutPlanDescription;
    private TextView workoutPlanExplanation;
    private LinearLayout workoutDetailsLayout;
    private RecyclerView exercisesRecyclerView;
    private Spinner fitnessGoalSpinner;
    private Spinner experienceLevelSpinner;
    private Spinner weeklyTimeSpinner;
    private Spinner preferredWorkoutSpinner;
    private Spinner equipmentSpinner;
    private Spinner dailyFrequencySpinner;
    private Spinner limitationsSpinner;
    private EditText otherLimitationsEditText;
    private EditText workoutNameEditText;
    private Button generateButton;
    private RecyclerView savedWorkoutsRecyclerView;
    private SavedWorkoutsAdapter savedWorkoutsAdapter;
    private ProgressBar savedWorkoutsProgressBar;
    private TextView noSavedWorkoutsMessage;
    private Button retryFetchWorkoutsButton;
    private LinearLayout savedWorkoutsHeader;
    private LinearLayout savedWorkoutsContent;
    private TextView savedWorkoutsExpandIconTextView;
    private LinearLayout generateWorkoutHeader;
    private LinearLayout generateWorkoutContent;
    private TextView generateWorkoutExpandIconTextView;
    private ProgressBar mainLoadingProgressBar;

    // --- State and Data Variables ---
    private AiService aiService;
    private boolean hasPaidForGeneration = false;
    private Map<String, ExerciseModel> allExercisesMap = new HashMap<>();
    private static final String TAG = "WorkoutWizardActivity";
    private SharedPreferences sharedPreferences;
    private static final String KEY_HAS_PAID = "hasPaidForGeneration";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workout_wizard);

        // --- Find all views ---
        progressBar = findViewById(R.id.progressBar);
        workoutPlanDescription = findViewById(R.id.workoutPlanDescription);
        workoutPlanExplanation = findViewById(R.id.workoutPlanExplanation);
        workoutDetailsLayout = findViewById(R.id.workoutDetailsLayout);
        exercisesRecyclerView = findViewById(R.id.exercisesRecyclerView);
        fitnessGoalSpinner = findViewById(R.id.fitnessGoalSpinner);
        experienceLevelSpinner = findViewById(R.id.experienceLevelSpinner);
        weeklyTimeSpinner = findViewById(R.id.weeklyTimeSpinner);
        preferredWorkoutSpinner = findViewById(R.id.preferredWorkoutSpinner);
        equipmentSpinner = findViewById(R.id.equipmentSpinner);
        dailyFrequencySpinner = findViewById(R.id.dailyFrequencySpinner);
        limitationsSpinner = findViewById(R.id.limitationsSpinner);
        otherLimitationsEditText = findViewById(R.id.otherLimitationsEditText);
        generateButton = findViewById(R.id.generateButton);
        workoutNameEditText = findViewById(R.id.workoutNameEditText);
        mainLoadingProgressBar = findViewById(R.id.mainLoadingProgressBar);
        savedWorkoutsHeader = findViewById(R.id.savedWorkoutsHeader);
        savedWorkoutsContent = findViewById(R.id.savedWorkoutsContent);
        savedWorkoutsExpandIconTextView = findViewById(R.id.savedWorkoutsExpandIconTextView);
        savedWorkoutsRecyclerView = findViewById(R.id.savedWorkoutsRecyclerView);
        savedWorkoutsProgressBar = findViewById(R.id.savedWorkoutsProgressBar);
        noSavedWorkoutsMessage = findViewById(R.id.noSavedWorkoutsMessage);
        generateWorkoutHeader = findViewById(R.id.generateWorkoutHeader);
        generateWorkoutContent = findViewById(R.id.generateWorkoutContent);
        generateWorkoutExpandIconTextView = findViewById(R.id.generateWorkoutExpandIconTextView);
        retryFetchWorkoutsButton = findViewById(R.id.retryFetchWorkoutsButton);

        // Load the persistent state as soon as the activity is created.
        sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);
        hasPaidForGeneration = sharedPreferences.getBoolean(KEY_HAS_PAID, false);
        updateGenerateButtonText(); // Update the button text based on the loaded state

        // Use the standard LinearLayoutManager. The forceful measurement will handle the rest.
        exercisesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        savedWorkoutsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        savedWorkoutsAdapter = new SavedWorkoutsAdapter(new ArrayList<>(), this);
        savedWorkoutsRecyclerView.setAdapter(savedWorkoutsAdapter);

        savedWorkoutsHeader.setOnClickListener(v -> toggleAccordionContent(savedWorkoutsContent, savedWorkoutsExpandIconTextView));
        generateWorkoutHeader.setOnClickListener(v -> toggleAccordionContent(generateWorkoutContent, generateWorkoutExpandIconTextView));
        hideAllContentSections();

        List<Exercise> allExercises = Utils.loadExercisesFromAssets(this);
        if (allExercises != null) {
            for (Exercise exercise : allExercises) {
                allExercisesMap.put(exercise.getName(), Utils.getExerciseModel(exercise));
            }
        }
        aiService = new AiService();

        limitationsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedLimitation = (String) parent.getItemAtPosition(position);
                otherLimitationsEditText.setVisibility(selectedLimitation.equals("Other") ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        generateButton.setOnClickListener(v -> {
            String workoutName = workoutNameEditText.getText().toString().trim();
            if (workoutName.isEmpty()) {
                workoutNameEditText.setError("Workout name is required.");
                Toast.makeText(this, "Please enter a name for your workout.", Toast.LENGTH_SHORT).show();
                return;
            }
            workoutNameEditText.setError(null);

            // The state is already loaded. Use the 'hasPaidForGeneration' variable directly.
            if (hasPaidForGeneration) {
                // It's a free retry. Call the AI generation directly.
                Log.d(TAG, "User has a pending paid attempt. Retrying AI generation for free for: " + workoutName);
                processWorkoutGeneration(workoutName);
            } else {
                // It's a new attempt. Start the flow by checking the user's balance.
                grabBalanceAndProceed(workoutName);
            }
        });

        setDefaultDailyFrequency();
        fetchAndDisplayUserWorkouts();
        retryFetchWorkoutsButton.setOnClickListener(v -> fetchAndDisplayUserWorkouts());
        noSavedWorkoutsMessage.setOnClickListener(v -> fetchAndDisplayUserWorkouts());
    }

    // Called from the payment confirmation dialog to start the payment process.
    private void processWorkoutTrx(String workoutName) {
        showLoading();
        Log.d(TAG, "New generation attempt. Proceeding with payment for: " + workoutName);
        performAfitsDeduction(workoutName);
    }

    // A dedicated method for the generation part, used for both initial and retry attempts.
    private void processWorkoutGeneration(String workoutName) {
        showLoading();
        WorkoutRequest workoutRequest = getUserInputFromUI();
        callGeminiApi(workoutName, workoutRequest);
    }

    // The Payment Function: Contains YOUR Volley code for the payment transaction.
    private void performAfitsDeduction(String workoutName) {
        Context ctx = getApplicationContext();
        RequestQueue queue = Volley.newRequestQueue(ctx);

        try {
            String op_name = "custom_json";
            JSONObject cstm_params = new JSONObject();
            JSONArray required_auths = new JSONArray();
            JSONArray required_posting_auths = new JSONArray();
            required_posting_auths.put(username);
            cstm_params.put("required_auths", required_auths);
            cstm_params.put("required_posting_auths", required_posting_auths);
            cstm_params.put("id", "actifit");
            cstm_params.put("json", "{\"transaction\": \"generate-workout-wizard\"}");
            JSONArray operation = new JSONArray();
            operation.put(0, op_name);
            operation.put(1, cstm_params);

            String bcastUrl = (getString(R.string.test_mode).equals("on") ?
                    getString(R.string.test_server) : Utils.apiUrl(ctx)) +
                    ctx.getString(R.string.perform_trx_link) +
                    username +
                    "&operation=[" + operation + "]" +
                    "&bchain=HIVE";

            JsonObjectRequest transRequest = new JsonObjectRequest(Request.Method.GET,
                    bcastUrl, null,
                    response -> {
                        if (response.optBoolean("success", false)) {
                            try {
                                JSONObject bcastRes = response.getJSONObject("trx").getJSONObject("tx");
                                String buyUrl = (getString(R.string.test_mode).equals("on") ?
                                        getString(R.string.test_server) : Utils.apiUrl(ctx)) +
                                        ctx.getString(R.string.generate_workout_link) +
                                        username + "/" +
                                        bcastRes.get("ref_block_num") + "/" +
                                        bcastRes.get("id") + "/" +
                                        "HIVE" +
                                        "/?user=" + username;

                                JsonObjectRequest buyRequest = new JsonObjectRequest(Request.Method.GET,
                                        buyUrl, null,
                                        response1 -> {
                                            if (!response1.has("error") && (response1.optBoolean("success", false) || response1.optString("status", "").equalsIgnoreCase("success"))) {

                                                // Payment is complete. Set the flag and save it IMMEDIATELY.
                                                Log.d(TAG, "Payment successful. Saving 'paid' state before calling AI.");
                                                setPaidState(true);

                                                // Now, call the AI service.
                                                processWorkoutGeneration(workoutName);

                                            } else {
                                                Log.e(TAG, "Payment failed at confirmation step: " + response1.toString());
                                                Toast.makeText(WorkoutWizardActivity.this, getString(R.string.error_transaction), Toast.LENGTH_LONG).show();
                                                hideLoading();
                                            }
                                        },
                                        error -> {
                                            Log.e(TAG, "Payment network error at confirmation step: " + error.toString());
                                            Toast.makeText(WorkoutWizardActivity.this, getString(R.string.error_transaction), Toast.LENGTH_LONG).show();
                                            hideLoading();
                                        }) {
                                    @Override
                                    public Map<String, String> getHeaders() {
                                        final Map<String, String> params = new HashMap<>();
                                        params.put("Content-Type", "application/json");
                                        params.put(ctx.getString(R.string.validation_header), ctx.getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken);
                                        return params;
                                    }
                                };
                                queue.add(buyRequest);
                            } catch (JSONException e) {
                                Log.e(TAG, "JSON Exception after payment broadcast: " + e.getMessage());
                                hideLoading();
                            }
                        } else {
                            Log.e(TAG, "Payment failed at broadcast step: " + response.toString());
                            Toast.makeText(WorkoutWizardActivity.this, getString(R.string.error_transaction), Toast.LENGTH_LONG).show();
                            hideLoading();
                        }
                    },
                    error -> {
                        Log.d(TAG, "Payment network error at broadcast step: " + error.toString());
                        Toast.makeText(WorkoutWizardActivity.this, getString(R.string.error_transaction), Toast.LENGTH_LONG).show();
                        hideLoading();
                    }) {
                @Override
                public Map<String, String> getHeaders() {
                    final Map<String, String> params = new HashMap<>();
                    params.put("Content-Type", "application/json");
                    params.put(ctx.getString(R.string.validation_header), ctx.getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken);
                    return params;
                }
            };
            queue.add(transRequest);
        } catch (Exception excep) {
            Log.e(TAG, "Exception setting up payment transaction: " + excep.getMessage());
            hideLoading();
        }
    }


    // The AI Function: Contains YOUR AI service call.
    private void callGeminiApi(String workoutName, WorkoutRequest request) {
        aiService.generateWorkoutPlan(request, new AiService.ResponseCallback() {
            @Override
            public void onSuccess(AiResponse response) {
                runOnUiThread(() -> {
                    // SUCCESS! The user got their plan. Consume the payment by resetting the state.
                    Log.d(TAG, "AI generation successful. Clearing the 'paid' state.");
                    setPaidState(false);

                    hideLoading();
                    WorkoutPlan generatedPlan = response.getWorkoutPlan();
                    displayWorkoutPlan(generatedPlan);

                    // Now, save the successfully generated plan to the backend.
                    WorkoutApiClient.saveWorkoutPlan(
                            WorkoutWizardActivity.this,
                            username,
                            LoginActivity.accessToken,
                            workoutName,
                            generatedPlan,
                            response.getExplanation(),
                            new WorkoutApiClient.SaveWorkoutCallback() {
                                @Override
                                public void onSuccess() {
                                    Toast.makeText(WorkoutWizardActivity.this, "Workout plan saved!", Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onFailure(String errorMessage) {
                                    Toast.makeText(WorkoutWizardActivity.this, "Failed to save workout: " + errorMessage, Toast.LENGTH_LONG).show();
                                }
                            }
                    );
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    // FAILURE! The AI failed, but the user has paid.
                    // DO NOTHING to the state. The 'hasPaidForGeneration' flag is still true and saved.
                    hideLoading();
                    showError("Workout generation failed: " + errorMessage);

                    // Update UI to make it clear a free retry is available.
                    updateGenerateButtonText();
                    showGenerateWorkoutAccordion();
                });
            }
        });
    }

    // --- Helper methods to manage the payment/generation state ---

    /**
     * Sets the paid state in memory and persists it to SharedPreferences.
     *
     * @param hasPaid true if payment was successful, false to reset/clear the state.
     */
    private void setPaidState(boolean hasPaid) {
        this.hasPaidForGeneration = hasPaid;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (hasPaid) {
            // Save the flag to indicate a pending free retry is available.
            editor.putBoolean(KEY_HAS_PAID, true);
            Log.d(TAG, "Saved state to SharedPreferences: hasPaidForGeneration=true");
        } else {
            // Clear the flag because the transaction is complete (or was reset).
            editor.remove(KEY_HAS_PAID);
            Log.d(TAG, "Cleared 'hasPaidForGeneration' state from SharedPreferences.");
        }
        editor.apply();

        // Always update the button text after changing the state.
        updateGenerateButtonText();
    }

    /**
     * A single, reliable place to update the generate button's text based on the current state.
     */
    private void updateGenerateButtonText() {
        if (hasPaidForGeneration) {
            generateButton.setText(R.string.regenerate_workout_free);
        } else {
            generateButton.setText(R.string.generate_workout_plan);
        }
    }

    private void fetchAndDisplayUserWorkouts() {
        String currentUserJwt = LoginActivity.accessToken;
        if (currentUserJwt == null || currentUserJwt.isEmpty()) {
            Log.w(TAG, "Cannot fetch workouts: JWT token missing.");
            showNoWorkoutsMessage("Authentication token missing.");
            return;
        }
        showListLoading();

        WorkoutApiClient.fetchUserWorkouts(this, currentUserJwt, username,
                new WorkoutApiClient.FetchWorkoutsCallback() {
                    @Override
                    public void onSuccess(List<WorkoutPlan> workouts) {
                        runOnUiThread(() -> {
                            hideListLoading();
                            if (workouts != null && !workouts.isEmpty()) {
                                Log.d(TAG, "Fetched " + workouts.size() + " saved workouts.");
                                savedWorkoutsAdapter.setWorkoutList(workouts);
                                showSavedWorkoutsAccordion();
                                savedWorkoutsRecyclerView.setVisibility(View.VISIBLE);
                                noSavedWorkoutsMessage.setVisibility(View.GONE);
                                retryFetchWorkoutsButton.setVisibility(View.GONE);
                                ScrollView scrollView = findViewById(R.id.scrollView);
                                if (scrollView != null) {
                                    scrollView.post(() -> scrollView.requestChildFocus(savedWorkoutsHeader, savedWorkoutsHeader));
                                }
                            } else {
                                Log.d(TAG, "No saved workouts found for the user.");
                                showNoWorkoutsMessage("No saved workouts yet.");
                                retryFetchWorkoutsButton.setVisibility(View.GONE);
                                showGenerateWorkoutAccordion();
                                ScrollView scrollView = findViewById(R.id.scrollView);
                                if (scrollView != null) {
                                    scrollView.post(() -> scrollView.requestChildFocus(generateWorkoutHeader, generateWorkoutHeader));
                                }
                            }
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(() -> {
                            mainLoadingProgressBar.setVisibility(View.GONE); // Hide main loading
                            Log.e(TAG, "Failed to fetch user workouts: " + errorMessage);
                            showNoWorkoutsMessage("Error loading workouts: " + errorMessage); // Show error message
                            retryFetchWorkoutsButton.setVisibility(View.VISIBLE);
                        });
                    }
                });
    }

    private void grabBalanceAndProceed(String workoutName) {
        Context ctx = getApplicationContext();
        if (username == null || username.isEmpty()) {
            Toast.makeText(ctx, getString(R.string.username_missing), Toast.LENGTH_LONG).show();
            showGenerateWorkoutAccordion();
            return;
        }
        Utils.fetchUserBalance(this, username, false, new Utils.BalanceFetchListener() {

            @Override
            public void onBalanceFetched(double balance) {
                // Check the balance here!
                if (balance < Constants.MIN_AFIT_PER_WORKOUT) {
                    // Insufficient funds, show the error dialog
                    mainLoadingProgressBar.setVisibility(View.GONE); // Hide loading
                    generateButton.setEnabled(true);
                    showInsufficientFundsDialog((long) balance); // Cast to long if your dialog expects long
                    showGenerateWorkoutAccordion();
                } else {
                    // User has enough AFIT, show the payment confirmation dialog
                    showPaymentConfirmationDialog(workoutName);
                }
            }

            @Override
            public void onBalanceFetchFailed(String errorMessage) {
                // This code runs if fetching the balance failed (network error, JSON error etc.)
                mainLoadingProgressBar.setVisibility(View.GONE);
                generateButton.setEnabled(true);
                Log.e(TAG, "Failed to fetch user balance: " + errorMessage);
                showError("Error fetching balance: " + errorMessage); // Shows Toast
                showGenerateWorkoutAccordion();
            }
        });
    }

    @Override
    public void onWorkoutSelected(WorkoutPlan workout) {
        Log.d(TAG, "Workout item clicked: " + workout.getWorkoutName());
        displayWorkoutPlan(workout);
    }

    private WorkoutRequest getUserInputFromUI() {
        String fitnessGoal = fitnessGoalSpinner.getSelectedItem().toString();
        String experienceLevel = experienceLevelSpinner.getSelectedItem().toString();
        String weeklyTime = weeklyTimeSpinner.getSelectedItem().toString();
        String preferredWorkout = preferredWorkoutSpinner.getSelectedItem().toString();
        String equipment = equipmentSpinner.getSelectedItem().toString();
        String dailyFrequency = dailyFrequencySpinner.getSelectedItem().toString();
        String limitations = limitationsSpinner.getSelectedItem().toString();
        String otherLimitations = otherLimitationsEditText.getText().toString();
        if (!limitations.equals("Other")) {
            otherLimitations = "";
        }
        return new WorkoutRequest(fitnessGoal, experienceLevel, weeklyTime,
                preferredWorkout, equipment, limitations, otherLimitations, dailyFrequency);
    }

    private void displayWorkoutPlan(WorkoutPlan plan) {
        if (plan == null) {
            Log.w(TAG, "displayWorkoutPlan called with null plan.");
            hideWorkoutDetails();
            showFormOrList();
            return;
        }

        workoutDetailsLayout.setVisibility(View.VISIBLE);
        workoutPlanDescription.setText(plan.getDescription());
        workoutPlanExplanation.setText(plan.getExplanation());

        List<Exercise> exercises = plan.getExercises();
        if (exercises != null && !exercises.isEmpty()) {
            if (allExercisesMap != null && !allExercisesMap.isEmpty()) {
                for (Exercise exercise : exercises) {
                    ExerciseModel matchingModel = Utils.findMatchingExercise(exercise.getName(), allExercisesMap);
                    if (matchingModel != null) {
                        exercise.setImages(matchingModel.getImages());
                        exercise.setBodyPart(matchingModel.getBodyPart());
                        exercise.setEquipment(matchingModel.getEquipment());
                        exercise.setTarget(matchingModel.getTarget());
                        exercise.setPrimaryMuscles(matchingModel.getPrimaryMuscles());
                        exercise.setSecondaryMuscles(matchingModel.getSecondaryMuscles());
                        exercise.setInstructions(matchingModel.getInstructions());
                    } else {
                        Log.w(TAG, "No matching local ExerciseModel found for: " + exercise.getName());
                    }
                }
            } else {
                Log.w(TAG, "allExercisesMap is not loaded or empty. Cannot enhance exercises with local data.");
            }

            ExerciseAdapter adapter = new ExerciseAdapter(exercises);
            exercisesRecyclerView.setAdapter(adapter);

            // This block forces the RecyclerView to fully expand by measuring all its items.
            exercisesRecyclerView.post(() -> {
                expandRecyclerViewHeight(exercisesRecyclerView);

                // Scroll to the bottom for a good user experience
                ScrollView scrollView = findViewById(R.id.scrollView);
                if (scrollView != null) {
                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                }
            });

        } else {
            Log.w(TAG, "Workout plan exercises list is null or empty.");
            exercisesRecyclerView.setAdapter(null);
        }

        showWorkoutDetailsPanel();
    }


    /**
     * This helper method manually measures every single item in the adapter
     * to calculate the total required height, then forces the RecyclerView to that height.
     */
    public void expandRecyclerViewHeight(RecyclerView recyclerView) {
        RecyclerView.Adapter adapter = recyclerView.getAdapter();
        if (adapter == null) {
            return;
        }

        int totalHeight = 0;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(recyclerView.getWidth(), View.MeasureSpec.EXACTLY);

        for (int i = 0; i < adapter.getItemCount(); i++) {
            RecyclerView.ViewHolder holder = adapter.createViewHolder(recyclerView, adapter.getItemViewType(i));
            adapter.onBindViewHolder(holder, i);
            holder.itemView.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            totalHeight += holder.itemView.getMeasuredHeight();
        }

        ViewGroup.LayoutParams params = recyclerView.getLayoutParams();
        params.height = totalHeight;
        recyclerView.setLayoutParams(params);
        Log.d(TAG, "Forcing RecyclerView height to: " + totalHeight + " for " + adapter.getItemCount() + " items.");
    }


    private void setDefaultDailyFrequency() {
        String defaultFrequency = "3 days a week";
        String[] dailyFrequencies = getResources().getStringArray(R.array.daily_frequencies);
        int defaultPosition = -1;
        for (int i = 0; i < dailyFrequencies.length; i++) {
            if (dailyFrequencies[i].equals(defaultFrequency)) {
                defaultPosition = i;
                break;
            }
        }
        if (defaultPosition != -1) {
            dailyFrequencySpinner.setSelection(defaultPosition);
        }
    }

    // --- UI Helper and Dialog Functions ---
    private void showPaymentConfirmationDialog(String workoutName) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Workout Generation")
                .setMessage("Generating this custom workout plan costs " + Constants.MIN_AFIT_PER_WORKOUT + " AFIT. Do you want to proceed and pay?")
                .setPositiveButton(getString(R.string.proceed), (dialog, which) -> {
                    processWorkoutTrx(workoutName);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    hideLoading(); // Hide loading if user cancels payment
                    showGenerateWorkoutAccordion(); // Return to generate form
                })
                .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                .show();
    }

    private void showInsufficientFundsDialog(long current) {
        new AlertDialog.Builder(this)
                .setTitle("Insufficient AFIT")
                .setMessage("You need " + Constants.MIN_AFIT_PER_WORKOUT +
                        " AFIT to generate a workout plan. Your current balance is "
                        + current + " AFIT. Please acquire more AFIT.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setIcon(android.R.drawable.ic_dialog_alert) // Or a relevant icon
                .show();
    }

    private void toggleAccordionContent(View contentLayout, TextView expandIconTextView) {
        if (mainLoadingProgressBar.getVisibility() == View.VISIBLE || progressBar.getVisibility() == View.VISIBLE) {
            return;
        }
        if (workoutDetailsLayout.getVisibility() == View.VISIBLE) {
            hideWorkoutDetails();
            showFormOrList(); // Go back to the form/list view after hiding details
            return;
        }
        if (contentLayout.getVisibility() == View.GONE) {
            if (contentLayout == savedWorkoutsContent) {
                collapseContent(generateWorkoutContent, generateWorkoutExpandIconTextView);
            } else {
                collapseContent(savedWorkoutsContent, savedWorkoutsExpandIconTextView);
            }
            expandContent(contentLayout, expandIconTextView);
        } else {
            collapseContent(contentLayout, expandIconTextView);
        }
    }

    private void expandContent(View contentLayout, TextView expandIconTextView) {
        contentLayout.setVisibility(View.VISIBLE);
        expandIconTextView.setRotation(180);
    }

    private void collapseContent(View contentLayout, TextView expandIconTextView) {
        contentLayout.setVisibility(View.GONE);
        expandIconTextView.setRotation(0);
    }

    private void hideAllContentSections() {
        workoutDetailsLayout.setVisibility(View.GONE);
        mainLoadingProgressBar.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        savedWorkoutsContent.setVisibility(View.GONE);
        generateWorkoutContent.setVisibility(View.GONE);
        savedWorkoutsExpandIconTextView.setRotation(0);
        generateWorkoutExpandIconTextView.setRotation(0);
        savedWorkoutsProgressBar.setVisibility(View.GONE);
        noSavedWorkoutsMessage.setVisibility(View.GONE);
        retryFetchWorkoutsButton.setVisibility(View.GONE);
    }

    private void showSavedWorkoutsAccordion() {
        hideAllContentSections();
        expandContent(savedWorkoutsContent, savedWorkoutsExpandIconTextView);
        collapseContent(generateWorkoutContent, generateWorkoutExpandIconTextView);
    }

    private void showGenerateWorkoutAccordion() {
        hideAllContentSections();
        expandContent(generateWorkoutContent, generateWorkoutExpandIconTextView);
        collapseContent(savedWorkoutsContent, savedWorkoutsExpandIconTextView);
        // Ensure button text is always correct when this view is shown
        updateGenerateButtonText();
    }

    private void showListLoading() {
        savedWorkoutsProgressBar.setVisibility(View.VISIBLE);
        noSavedWorkoutsMessage.setVisibility(View.GONE);
        savedWorkoutsRecyclerView.setVisibility(View.GONE);
        retryFetchWorkoutsButton.setVisibility(View.GONE);
    }

    private void hideListLoading() {
        savedWorkoutsProgressBar.setVisibility(View.GONE);
    }

    private void showNoWorkoutsMessage(String message) {
        noSavedWorkoutsMessage.setText(message);
        noSavedWorkoutsMessage.setVisibility(View.VISIBLE);
        savedWorkoutsProgressBar.setVisibility(View.GONE);
        savedWorkoutsRecyclerView.setVisibility(View.GONE);
    }

    private void showWorkoutDetailsPanel() {
        hideAllContentSections();
        workoutDetailsLayout.setVisibility(View.VISIBLE);
    }

    private void hideWorkoutDetails() {
        workoutDetailsLayout.setVisibility(View.GONE);
        exercisesRecyclerView.setAdapter(null); // Clear the adapter to free up views
    }

    private void showLoading() {
        hideAllContentSections(); // Hide all accordion sections and workout details
        mainLoadingProgressBar.setVisibility(View.VISIBLE); // Show the main loading bar
    }

    private void hideLoading() {
        mainLoadingProgressBar.setVisibility(View.GONE); // Hide the main loading bar
    }

    private void showError(String message) {
        Toast.makeText(this, "Error: " + message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        if (workoutDetailsLayout.getVisibility() == View.VISIBLE) {
            hideWorkoutDetails();
            showFormOrList();
        } else {
            super.onBackPressed();
        }
    }

    private void showFormOrList() {
        if (savedWorkoutsAdapter != null && savedWorkoutsAdapter.getItemCount() > 0) {
            showSavedWorkoutsAccordion();
        } else {
            showGenerateWorkoutAccordion();
        }
    }
}