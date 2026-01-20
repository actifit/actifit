package io.actifit.fitnesstracker.actifitfitnesstracker;

import static io.actifit.fitnesstracker.actifitfitnesstracker.MainActivity.username;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

public class WorkoutWizardActivity extends BaseActivity
        implements SavedWorkoutsAdapter.OnWorkoutActionListener { // Changed from OnWorkoutSelectedListener

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

    private ActivityResultLauncher<Intent> exerciseSearchLauncher;
    private ActivityResultLauncher<Intent> editWorkoutLauncher;



    // --- State and Data Variables ---
    private AiService aiService;
    private boolean hasPaidForGeneration = false;
    private Map<String, ExerciseModel> allExercisesMap = new HashMap<>();
    private static final String TAG = "WorkoutWizardActivity";
    private SharedPreferences sharedPreferences;
    private static final String KEY_HAS_PAID = "hasPaidForGeneration";
    private TextView browseExercisesIconTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workout_wizard);

        editWorkoutLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        boolean refreshWorkouts = result.getData().getBooleanExtra(
                                getString(R.string.result_workout_saved_refresh_key), false); // Using the same key for refresh
                        if (refreshWorkouts) {
                            Log.d(TAG, "Refreshing workouts after editing from EditWorkoutActivity.");
                            fetchAndDisplayUserWorkouts(true);
                        }
                    }
                });

        exerciseSearchLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        // Check if the result indicates a workout was saved and refresh is needed
                        boolean refreshWorkouts = result.getData().getBooleanExtra(
                                getString(R.string.result_workout_saved_refresh_key), false);
                        if (refreshWorkouts) {
                            Log.d(TAG, "Refreshing workouts after saving from ExerciseSearchActivity.");
                            fetchAndDisplayUserWorkouts(true);
                        }
                    }
                });

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

        browseExercisesIconTextView = findViewById(R.id.browseExercisesIconTextView);

        sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);
        hasPaidForGeneration = sharedPreferences.getBoolean(KEY_HAS_PAID, false);
        updateGenerateButtonText();

        exercisesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        savedWorkoutsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // MODIFICATION 2: Initialize adapter with 'this' as the OnWorkoutActionListener
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
                workoutNameEditText.setError(getString(R.string.error_workout_name_required));
                Toast.makeText(this, getString(R.string.prompt_enter_workout_name), Toast.LENGTH_SHORT).show();
                return;
            }
            workoutNameEditText.setError(null);

            if (hasPaidForGeneration) {
                Log.d(TAG, getString(R.string.log_pending_paid_attempt_retry) + workoutName);
                processWorkoutGeneration(workoutName);
            } else {
                grabBalanceAndProceed(workoutName);
            }
        });

        if (browseExercisesIconTextView != null) {
            browseExercisesIconTextView.setOnClickListener(v -> {
                Intent intent = new Intent(WorkoutWizardActivity.this, ExerciseSearchActivity.class);
                startActivity(intent);
            });
        }

        setDefaultDailyFrequency();
        fetchAndDisplayUserWorkouts();
        retryFetchWorkoutsButton.setOnClickListener(v -> fetchAndDisplayUserWorkouts());
        noSavedWorkoutsMessage.setOnClickListener(v -> fetchAndDisplayUserWorkouts());
    }

    @Override
    public void onEditWorkout(WorkoutPlan workout) {
        Log.d(TAG, "Edit workout button clicked for: " + workout.getWorkoutName() + " (ID: " + workout.getId() + ")");
        Intent intent = new Intent(this, EditWorkoutActivity.class);
        intent.putExtra("workoutPlan", workout); // Pass the entire WorkoutPlan object
        editWorkoutLauncher.launch(intent); // Launch for result
    }

    private void processWorkoutTrx(String workoutName) {
        showLoading();
        Log.d(TAG, getString(R.string.log_new_generation_proceed_payment) + workoutName);
        performAfitsDeduction(workoutName);
    }

    private void processWorkoutGeneration(String workoutName) {
        showLoading();
        WorkoutRequest workoutRequest = getUserInputFromUI();
        callGeminiApi(workoutName, workoutRequest);
    }

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

            Log.d(TAG, bcastUrl);

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
                                                Log.d(TAG, getString(R.string.log_payment_successful_saving_state));
                                                setPaidState(true);
                                                processWorkoutGeneration(workoutName);
                                            } else {
                                                Log.e(TAG, getString(R.string.error_payment_confirmation_step) + response1.toString());
                                                Toast.makeText(WorkoutWizardActivity.this, getString(R.string.error_transaction), Toast.LENGTH_LONG).show();
                                                hideLoading();
                                            }
                                        },
                                        error -> {
                                            Log.e(TAG, getString(R.string.error_payment_network_confirmation_step) + error.toString());
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
                                Log.e(TAG, getString(R.string.log_json_exception_payment_broadcast) + e.getMessage());
                                hideLoading();
                            }
                        } else {
                            Log.e(TAG, getString(R.string.error_payment_broadcast_step) + response.toString());
                            Toast.makeText(WorkoutWizardActivity.this, getString(R.string.error_transaction), Toast.LENGTH_LONG).show();
                            hideLoading();
                        }
                    },
                    error -> {
                        Log.d(TAG, getString(R.string.error_payment_network_broadcast_step) + error.toString());
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
            Log.e(TAG, getString(R.string.log_exception_setting_up_payment) + excep.getMessage());
            hideLoading();
        }
    }

    private void callGeminiApi(String workoutName, WorkoutRequest request) {
        aiService.generateWorkoutPlan(request, new AiService.ResponseCallback() {
            @Override
            public void onSuccess(AiResponse response) {
                runOnUiThread(() -> {
                    Log.d(TAG, getString(R.string.log_ai_generation_successful_clearing_state));
                    setPaidState(false);
                    hideLoading();
                    WorkoutPlan generatedPlan = response.getWorkoutPlan();
                    displayWorkoutPlan(generatedPlan);

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
                                    fetchAndDisplayUserWorkouts(false);
                                    Toast.makeText(WorkoutWizardActivity.this, getString(R.string.success_workout_plan_saved), Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onFailure(String errorMessage) {
                                    Toast.makeText(WorkoutWizardActivity.this, getString(R.string.error_failed_to_save_workout) + " " + errorMessage, Toast.LENGTH_LONG).show();
                                }
                            }
                    );
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    hideLoading();
                    showError(getString(R.string.error_workout_generation_failed) + " " + errorMessage);
                    updateGenerateButtonText();
                    showGenerateWorkoutAccordion();
                });
            }
        });
    }

    private void setPaidState(boolean hasPaid) {
        this.hasPaidForGeneration = hasPaid;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (hasPaid) {
            editor.putBoolean(KEY_HAS_PAID, true);
            Log.d(TAG, "Saved state to SharedPreferences: hasPaidForGeneration=true");
        } else {
            editor.remove(KEY_HAS_PAID);
            Log.d(TAG, "Cleared 'hasPaidForGeneration' state from SharedPreferences.");
        }
        editor.apply();
        updateGenerateButtonText();
    }

    private void updateGenerateButtonText() {
        if (hasPaidForGeneration) {
            generateButton.setText(R.string.regenerate_workout_free);
        } else {
            generateButton.setText(R.string.generate_workout_plan);
        }
    }

    private void fetchAndDisplayUserWorkouts() {
        fetchAndDisplayUserWorkouts(true);
    }

    private void fetchAndDisplayUserWorkouts(boolean refreshDisplay) {
        String currentUserJwt = LoginActivity.accessToken;
        // The API endpoint uses `userId` which maps to `username` in your context
        String currentUserId = username;

        if (currentUserJwt == null || currentUserJwt.isEmpty()) {
            Log.w(TAG, getString(R.string.log_cannot_fetch_workouts_jwt_missing));
            showNoWorkoutsMessage(getString(R.string.error_authentication_token_missing));
            return;
        }
        if (currentUserId == null || currentUserId.isEmpty()) { // Also check for userId
            Log.w(TAG, "Cannot fetch workouts: User ID (username) is missing.");
            showNoWorkoutsMessage("User ID missing. Please log in.");
            return;
        }


        if (refreshDisplay) {
            showListLoading();
        }

        WorkoutApiClient.fetchUserWorkouts(this, currentUserJwt, currentUserId, // Pass currentUserId here
                new WorkoutApiClient.FetchWorkoutsCallback() {
                    @Override
                    public void onSuccess(List<WorkoutPlan> workouts) {
                        runOnUiThread(() -> {
                            hideListLoading();
                            if (workouts != null && !workouts.isEmpty()) {
                                Log.d(TAG, "Fetched " + workouts.size() + " saved workouts.");
                                // Sort by timestamp if not already sorted by API
                                // Collections.sort(workouts, (w1, w2) -> w2.getTimestamp().compareTo(w1.getTimestamp()));
                                savedWorkoutsAdapter.setWorkoutList(workouts);
                                if (refreshDisplay) {
                                    showSavedWorkoutsAccordion();
                                    savedWorkoutsRecyclerView.setVisibility(View.VISIBLE);
                                    noSavedWorkoutsMessage.setVisibility(View.GONE);
                                    retryFetchWorkoutsButton.setVisibility(View.GONE);
                                    ScrollView scrollView = findViewById(R.id.scrollView);
                                    if (scrollView != null) {
                                        scrollView.post(() -> scrollView.requestChildFocus(savedWorkoutsHeader, savedWorkoutsHeader));
                                    }
                                }
                            } else {
                                Log.d(TAG, getString(R.string.info_no_saved_workouts_user));
                                showNoWorkoutsMessage(getString(R.string.info_no_saved_workouts_yet));
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
                            mainLoadingProgressBar.setVisibility(View.GONE);
                            Log.e(TAG, getString(R.string.error_failed_to_fetch_user_workouts) + " " + errorMessage);
                            showNoWorkoutsMessage(getString(R.string.error_loading_workouts) + " " + errorMessage);
                            retryFetchWorkoutsButton.setVisibility(View.VISIBLE);
                        });
                    }
                });
    }

    private void grabBalanceAndProceed(String workoutName) {
        Context ctx = getApplicationContext();
        String currentUserId = username; // Get the user ID
        if (currentUserId == null || currentUserId.isEmpty()) { // Ensure userId is available
            Toast.makeText(ctx, getString(R.string.username_missing), Toast.LENGTH_LONG).show();
            showGenerateWorkoutAccordion();
            return;
        }
        Utils.fetchUserBalance(this, currentUserId, false, new Utils.BalanceFetchListener() { // Pass currentUserId
            @Override
            public void onBalanceFetched(double balance) {
                if (balance < Constants.MIN_AFIT_PER_WORKOUT) {
                    mainLoadingProgressBar.setVisibility(View.GONE);
                    generateButton.setEnabled(true);
                    showInsufficientFundsDialog((long) balance);
                    showGenerateWorkoutAccordion();
                } else {
                    showPaymentConfirmationDialog(workoutName);
                }
            }

            @Override
            public void onBalanceFetchFailed(String errorMessage) {
                mainLoadingProgressBar.setVisibility(View.GONE);
                generateButton.setEnabled(true);
                Log.e(TAG, getString(R.string.error_failed_to_fetch_user_balance) + " " + errorMessage);
                showError(getString(R.string.error_fetching_balance) + " " + errorMessage);
                showGenerateWorkoutAccordion();
            }
        });
    }

    // MODIFICATION 3: Renamed from onWorkoutSelected to onWorkoutClick to match new interface
    @Override
    public void onWorkoutClick(WorkoutPlan workout) {
        Log.d(TAG, "Workout item clicked: " + workout.getWorkoutName());
        displayWorkoutPlan(workout);
    }

    // MODIFICATION 4: New method to handle delete button clicks from the adapter
    @Override
    public void onDeleteWorkout(WorkoutPlan workout) {
        // Show a confirmation dialog before deleting
        showDeleteConfirmationDialog(workout);
    }

    // MODIFICATION 5: Method to show the delete confirmation dialog
    private void showDeleteConfirmationDialog(final WorkoutPlan workoutToDelete) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_workout_title)
                .setMessage(getString(R.string.dialog_delete_workout_message, workoutToDelete.getWorkoutName()))
                .setPositiveButton(R.string.dialog_delete_workout_positive, (dialog, which) -> {
                    // User confirmed deletion, proceed with API call
                    performDeleteWorkout(workoutToDelete);
                })
                .setNegativeButton(R.string.dialog_delete_workout_negative, (dialog, which) -> {
                    // User cancelled deletion
                    dialog.dismiss();
                })
                .setIcon(android.R.drawable.ic_dialog_alert) // Optional: add an alert icon
                .show();
    }

    // MODIFICATION 6: Method to perform the actual delete API call
    private void performDeleteWorkout(final WorkoutPlan workoutToDelete) {
        String currentUserJwt = LoginActivity.accessToken;
        String currentUserId = username; // Assuming username is the userId

        if (currentUserJwt == null || currentUserJwt.isEmpty()) {
            Toast.makeText(this, "Authentication error. Cannot delete workout.", Toast.LENGTH_LONG).show();
            return;
        }
        if (currentUserId == null || currentUserId.isEmpty()) {
            Toast.makeText(this, "User ID missing. Cannot delete workout.", Toast.LENGTH_LONG).show();
            return;
        }
        if (workoutToDelete.getId() == null) {
            Toast.makeText(this, "Workout ID is missing. Cannot delete.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress or a temporary toast
        Toast.makeText(this, getString(R.string.workout_delete_in_progress), Toast.LENGTH_SHORT).show();

        WorkoutApiClient.deleteWorkoutPlan(this, currentUserJwt, workoutToDelete.getId(), currentUserId, new WorkoutApiClient.DeleteWorkoutCallback() {
            @Override
            public void onSuccess(String deletedWorkoutId) {
                runOnUiThread(() -> {
                    // Remove the workout from the adapter's data source and update UI
                    savedWorkoutsAdapter.removeWorkout(workoutToDelete);
                    Toast.makeText(WorkoutWizardActivity.this,
                            getString(R.string.toast_workout_deleted_success, workoutToDelete.getWorkoutName()),
                            Toast.LENGTH_SHORT).show();

                    // Check if the list is now empty and update UI accordingly
                    if (savedWorkoutsAdapter.getItemCount() == 0) {
                        noSavedWorkoutsMessage.setVisibility(View.VISIBLE);
                        noSavedWorkoutsMessage.setText(R.string.no_saved_workouts_yet);
                        savedWorkoutsRecyclerView.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    Toast.makeText(WorkoutWizardActivity.this,
                            getString(R.string.toast_workout_deleted_failure, errorMessage),
                            Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Delete workout failed: " + errorMessage);
                });
            }
        });
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
            Log.w(TAG, getString(R.string.log_display_workout_plan_null));
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
                        Log.w(TAG, getString(R.string.log_no_matching_local_exercise_model) + exercise.getName());
                    }
                }
            } else {
                Log.w(TAG, getString(R.string.log_all_exercises_map_not_loaded));
            }

            ExerciseAdapter adapter = new ExerciseAdapter(exercises);
            exercisesRecyclerView.setAdapter(adapter);

            exercisesRecyclerView.post(() -> {
                expandRecyclerViewHeight(exercisesRecyclerView);
                ScrollView scrollView = findViewById(R.id.scrollView);
                if (scrollView != null) {
                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                }
            });

        } else {
            Log.w(TAG, getString(R.string.log_workout_plan_exercises_null_or_empty));
            exercisesRecyclerView.setAdapter(null);
        }
        showWorkoutDetailsPanel();
    }

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
        String defaultFrequency = getString(R.string.default_workout_frequency_3_days);
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

    private void showPaymentConfirmationDialog(String workoutName) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_confirm_workout_generation))
                .setMessage(getString(R.string.dialog_message_confirm_workout_generation_part1)  + " "
                        + Constants.MIN_AFIT_PER_WORKOUT  + " "
                        + getString(R.string.dialog_message_confirm_workout_generation_part2))
                .setPositiveButton(getString(R.string.proceed), (dialog, which) -> {
                    processWorkoutTrx(workoutName);
                })
                .setNegativeButton(getString(R.string.dialog_button_cancel), (dialog, which) -> {
                    dialog.dismiss();
                    hideLoading();
                    showGenerateWorkoutAccordion();
                })
                .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                .show();
    }

    private void showInsufficientFundsDialog(long current) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_insufficient_afit))
                .setMessage(getString(R.string.dialog_message_insufficient_afit_part1) + Constants.MIN_AFIT_PER_WORKOUT + getString(R.string.dialog_message_insufficient_afit_part2) + current + getString(R.string.dialog_message_insufficient_afit_part3))
                .setPositiveButton(getString(R.string.dialog_button_ok), (dialog, which) -> dialog.dismiss())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void toggleAccordionContent(View contentLayout, TextView expandIconTextView) {
        if (mainLoadingProgressBar.getVisibility() == View.VISIBLE || progressBar.getVisibility() == View.VISIBLE) {
            return;
        }
        if (workoutDetailsLayout.getVisibility() == View.VISIBLE) {
            hideWorkoutDetails();
            showFormOrList();
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
        exercisesRecyclerView.setAdapter(null);
    }

    private void showLoading() {
        hideAllContentSections();
        mainLoadingProgressBar.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        mainLoadingProgressBar.setVisibility(View.GONE);
    }

    private void showError(String message) {
        Toast.makeText(this, getString(R.string.toast_prefix_error) + " " + message, Toast.LENGTH_LONG).show();
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
        Log.d(TAG,"showFormOrList");
        if (savedWorkoutsAdapter != null && savedWorkoutsAdapter.getItemCount() > 0) {
            Log.d(TAG,"show>>");
            showSavedWorkoutsAccordion();
        } else {
            showGenerateWorkoutAccordion();
        }
    }
}