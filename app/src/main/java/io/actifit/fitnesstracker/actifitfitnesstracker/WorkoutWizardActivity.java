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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.Button;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;




public class WorkoutWizardActivity extends AppCompatActivity implements SavedWorkoutsAdapter.OnWorkoutSelectedListener {

    // ... (All your variable declarations are correct)
    private ProgressBar progressBar;
    private TextView workoutPlanDescription;
    private TextView workoutPlanExplanation;
    private LinearLayout workoutDetailsLayout;
    private RecyclerView exercisesRecyclerView;
    private AiService aiService;
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
    private boolean hasPaidForGeneration = false;
    private String lastAttemptWorkoutName;
    private Map<String, ExerciseModel> allExercisesMap = new HashMap<>();
    private static final String TAG = "WorkoutWizardActivity";
    private SharedPreferences sharedPreferences;
    private static final String KEY_HAS_PAID = "hasPaidForGeneration";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workout_wizard);

        // --- Find all views ---
        // ... (All your findViewById calls are correct)
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


        // <<<< SIMPLIFIED SETUP >>>>
        // Use the standard LinearLayoutManager. The forceful measurement will handle the rest.
        exercisesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        savedWorkoutsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // ... (The rest of your onCreate is correct)
        savedWorkoutsAdapter = new SavedWorkoutsAdapter(new ArrayList<>(), this);
        savedWorkoutsRecyclerView.setAdapter(savedWorkoutsAdapter);

        savedWorkoutsHeader.setOnClickListener(v -> toggleAccordionContent(savedWorkoutsContent, savedWorkoutsExpandIconTextView));
        generateWorkoutHeader.setOnClickListener(v -> toggleAccordionContent(generateWorkoutContent, generateWorkoutExpandIconTextView));
        hideAllContentSections();

        List<Exercise> allExercises = Utils.loadExercisesFromAssets(this);
        if(allExercises != null){
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

        // ... (Your generateButton listener and other setup calls are correct)
        generateButton.setOnClickListener(v -> {
            String workoutName = workoutNameEditText.getText().toString().trim();
            if (workoutName.isEmpty()) {
                workoutNameEditText.setError("Workout name is required.");
                Toast.makeText(this, "Please enter a name for your workout.", Toast.LENGTH_SHORT).show();
                return;
            } else {
                workoutNameEditText.setError(null);
            }
            sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);
            hasPaidForGeneration = sharedPreferences.getBoolean(KEY_HAS_PAID, false);
            if (hasPaidForGeneration) {
                if (lastAttemptWorkoutName == null || lastAttemptWorkoutName.isEmpty()) {
                    Log.e(TAG, "hasPaidForGeneration is true but lastAttemptWorkoutName is null/empty!");
                    resetGenerationState();
                    handleGenerateClick(workoutName);
                } else {
                    Log.d(TAG, "Retrying generation for paid attempt: " + lastAttemptWorkoutName);
                    showLoading();
                    processWorkoutTrx(workoutName);
                }
            } else {
                handleGenerateClick(workoutName);
            }
        });

        setDefaultDailyFrequency();
        fetchAndDisplayUserWorkouts();
        retryFetchWorkoutsButton.setOnClickListener(v -> fetchAndDisplayUserWorkouts());
        noSavedWorkoutsMessage.setOnClickListener(v -> fetchAndDisplayUserWorkouts());
    }

    // <<<< THE FINAL, GUARANTEED FIX IS HERE >>>>
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
                // ... (your enhancement loop is correct)
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
                    }
                }
            }

            ExerciseAdapter adapter = new ExerciseAdapter(exercises);
            exercisesRecyclerView.setAdapter(adapter);

            // This block forces the RecyclerView to fully expand by measuring all its items.
            // This is the most reliable way to solve the layout race condition.
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
     * This powerful helper method manually measures every single item in the adapter
     * to calculate the total required height, then forces the RecyclerView to that height.
     * This defeats the layout race condition.
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

    // --- All other methods below this point are correct and do not need changes ---
    // ... (Your existing methods: handleGenerateClick, toggleAccordionContent, etc.) ...

    private void handleGenerateClick(String workoutName) {
        if (workoutName.isEmpty()) {
            workoutNameEditText.setError("Workout name is required.");
            Toast.makeText(this, "Please enter a name for your workout.", Toast.LENGTH_SHORT).show();
            return;
        } else {
            workoutNameEditText.setError(null);
        }
        grabBalanceAndProceed(workoutName);
    }

    private void toggleAccordionContent(View contentLayout, TextView expandIconTextView) {
        if (mainLoadingProgressBar.getVisibility() == View.VISIBLE || progressBar.getVisibility() == View.VISIBLE) {
            return;
        }

        if (workoutDetailsLayout.getVisibility() == View.VISIBLE){
            hideWorkoutDetails();
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

        ScrollView scrollView = findViewById(R.id.scrollView);
        if (scrollView != null) {
            View headerToScrollTo = (contentLayout == savedWorkoutsContent) ? savedWorkoutsHeader : generateWorkoutHeader;
            scrollView.post(() -> scrollView.requestChildFocus(headerToScrollTo, headerToScrollTo));
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
        generateButton.setText(getString(R.string.generate_workout_plan));
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
        retryFetchWorkoutsButton.setVisibility(View.GONE);
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
                            mainLoadingProgressBar.setVisibility(View.GONE);
                            Log.e(TAG, "Failed to fetch user workouts: " + errorMessage);
                            showNoWorkoutsMessage("Error loading workouts: " + errorMessage);
                            retryFetchWorkoutsButton.setVisibility(View.VISIBLE);
                        });
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
        if (!limitations.equals("Other")){
            otherLimitations = "";
        }
        return new WorkoutRequest(fitnessGoal, experienceLevel, weeklyTime,
                preferredWorkout, equipment, limitations, otherLimitations,dailyFrequency);
    }

    private void generateWorkoutPlan(String workoutName, WorkoutRequest request) {
        aiService.generateWorkoutPlan(request, new AiService.ResponseCallback() {
            @Override
            public void onSuccess(AiResponse response) {
                runOnUiThread(() ->{
                    hideLoading();
                    WorkoutPlan generatedPlan = response.getWorkoutPlan();
                    String generatedExplanation = response.getExplanation();
                    displayWorkoutPlan(generatedPlan);
                    Log.d(TAG, "workoutname: "+workoutName);
                    WorkoutApiClient.saveWorkoutPlan(
                            WorkoutWizardActivity.this,
                            username,
                            LoginActivity.accessToken,
                            workoutName,
                            generatedPlan,
                            generatedExplanation,
                            new WorkoutApiClient.SaveWorkoutCallback() {
                                @Override
                                public void onSuccess() {
                                    runOnUiThread(() -> Toast.makeText(WorkoutWizardActivity.this, "Workout plan saved successfully!", Toast.LENGTH_SHORT).show());
                                }

                                @Override
                                public void onFailure(String errorMessage) {
                                    runOnUiThread(() -> {
                                        Log.e(TAG, "Failed to save workout plan to backend: " + errorMessage);
                                        Toast.makeText(WorkoutWizardActivity.this, "Failed to save workout: " + errorMessage, Toast.LENGTH_LONG).show();
                                    });
                                }
                            }
                    );
                } );
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    hideLoading();
                    showError("Workout generation failed: " + errorMessage);
                    generateButton.setText("Retry Generation");
                    showGenerateWorkoutAccordion();
                });
            }
        });
    }

    private void setDefaultDailyFrequency(){
        String defaultFrequency = "3 days a week";
        String[] dailyFrequencies = getResources().getStringArray(R.array.daily_frequencies);
        int defaultPosition = -1;
        for (int i = 0; i < dailyFrequencies.length; i++){
            if (dailyFrequencies[i].equals(defaultFrequency)){
                defaultPosition = i;
                break;
            }
        }
        if(defaultPosition != -1){
            dailyFrequencySpinner.setSelection(defaultPosition);
        }
    }

    private void grabBalanceAndProceed(String workoutName){
        Context ctx = getApplicationContext();
        if (username == null || username.isEmpty()){
            Toast.makeText(ctx, ctx.getString(R.string.username_missing), Toast.LENGTH_LONG).show();
            showGenerateWorkoutAccordion();
            return;
        }

        Utils.fetchUserBalance(this, username, false, new Utils.BalanceFetchListener() {
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
                Log.e(TAG, "Failed to fetch user balance: " + errorMessage);
                showError("Error fetching balance: " + errorMessage);
                showGenerateWorkoutAccordion();
            }
        });
    }

    private void processWorkoutTrx(String workoutName){
        showLoading();
        Context ctx = getApplicationContext();
        RequestQueue queue = Volley.newRequestQueue(ctx);

        if (LoginActivity.accessToken.isEmpty()){
            final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
            String pkey = sharedPreferences.getString("actifitPst", "");
            if (!pkey.isEmpty()) {
                String loginAuthUrl = Utils.apiUrl(ctx)+ ctx.getString(R.string.login_auth);
                JSONObject loginSettings = new JSONObject();
                try {
                    loginSettings.put(ctx.getString(R.string.username_param), username);
                    loginSettings.put(ctx.getString(R.string.pkey_param), pkey);
                    loginSettings.put(ctx.getString(R.string.bchain_param), "HIVE");
                    loginSettings.put(ctx.getString(R.string.keeploggedin_param), false);
                    loginSettings.put(ctx.getString(R.string.login_source), ctx.getString(R.string.android) + BuildConfig.VERSION_NAME);
                } catch (JSONException e) {
                }
                JsonObjectRequest loginRequest = new JsonObjectRequest(Request.Method.POST,
                        loginAuthUrl, loginSettings,
                        response -> {
                            try {
                                if (response.has("success")) {
                                    Log.d(TAG, response.toString());
                                    LoginActivity.accessToken = response.getString(ctx.getString(R.string.login_token));
                                }
                            } catch (JSONException e) {
                                Log.e(TAG, Objects.requireNonNull(e.getMessage()));
                            }
                        },
                        error -> Log.e(TAG, "Login error"));
                queue.add(loginRequest);
            }
        }

        if (!hasPaidForGeneration ) {
            String op_name = "custom_json";
            JSONObject cstm_params = new JSONObject();
            try {
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
                                                    hasPaidForGeneration = true;
                                                    saveGenerationState();
                                                    WorkoutRequest workoutRequest = getUserInputFromUI();
                                                    generateWorkoutPlan(workoutName, workoutRequest);
                                                } else {
                                                    Log.e(TAG, response1.toString());
                                                    Toast.makeText(ctx, ctx.getString(R.string.error_transaction), Toast.LENGTH_LONG).show();
                                                    hideLoading();
                                                }
                                            },
                                            error -> {
                                                Log.e(TAG, error.toString());
                                                Toast.makeText(ctx, ctx.getString(R.string.error_transaction), Toast.LENGTH_LONG).show();
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
                                    Log.e(TAG, Objects.requireNonNull(e.getMessage()));
                                    e.printStackTrace();
                                }
                            } else {
                                Log.e(TAG, response.toString());
                                Toast.makeText(ctx, ctx.getString(R.string.error_transaction), Toast.LENGTH_LONG).show();
                                hideLoading();
                            }
                        },
                        error -> {
                            Log.d(TAG, error.toString());
                            Toast.makeText(ctx, ctx.getString(R.string.error_transaction), Toast.LENGTH_LONG).show();
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
                excep.printStackTrace();
            }
        } else {
            WorkoutRequest workoutRequest = getUserInputFromUI();
            generateWorkoutPlan(workoutName, workoutRequest);
        }
    }

    private void saveGenerationState() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_HAS_PAID, hasPaidForGeneration);
        editor.apply();
    }

    private void clearGenerationState() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_HAS_PAID);
        editor.apply();
        Log.d(TAG, "Cleared state from SharedPreferences.");
    }

    private void resetGenerationState() {
        hasPaidForGeneration = false;
        lastAttemptWorkoutName = null;
        generateButton.setText(getString(R.string.generate_workout_plan));
        clearGenerationState();
    }

    private void showPaymentConfirmationDialog(String workoutName) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Workout Generation")
                .setMessage("Generating this custom workout plan costs " + Constants.MIN_AFIT_PER_WORKOUT + " AFIT. Do you want to proceed and pay?")
                .setPositiveButton(getString(R.string.proceed), (dialog, which) -> {
                    lastAttemptWorkoutName = workoutName;
                    processWorkoutTrx(workoutName);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    hideLoading();
                    showGenerateWorkoutAccordion();
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
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void showLoading() {
        hideAllContentSections();
        mainLoadingProgressBar.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        mainLoadingProgressBar.setVisibility(View.GONE);
    }

    private void showError(String message) {
        Toast.makeText(this, "Error " + message, Toast.LENGTH_LONG).show();
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