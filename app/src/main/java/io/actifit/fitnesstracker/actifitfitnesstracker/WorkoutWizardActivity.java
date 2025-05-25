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


public class WorkoutWizardActivity extends AppCompatActivity
    implements SavedWorkoutsAdapter.OnWorkoutSelectedListener{

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

    private LinearLayout savedWorkoutsSection; // Parent layout for saved workouts list
    private RecyclerView savedWorkoutsRecyclerView; // RecyclerView for the list
    private SavedWorkoutsAdapter savedWorkoutsAdapter; // Adapter for the list
    private ProgressBar savedWorkoutsProgressBar; // Progress bar for loading list
    private TextView noSavedWorkoutsMessage; // Message if list is empty
    private Button retryFetchWorkoutsButton;

    // NEW UI elements for the Accordion
    //private LinearLayout savedWorkoutsAccordionSection; // Parent container for Saved Workouts
    private LinearLayout savedWorkoutsHeader;         // Header for Saved Workouts
    private LinearLayout savedWorkoutsContent;        // Content for Saved Workouts
    private TextView savedWorkoutsExpandIconTextView;        // Expand icon for Saved Workouts

    //private LinearLayout generateWorkoutAccordionSection; // Parent container for Generate New Workout
    private LinearLayout generateWorkoutHeader;       // Header for Generate New Workout
    private LinearLayout generateWorkoutContent;      // Content for Generate New Workout
    private TextView generateWorkoutExpandIconTextView;      // Expand icon for Generate New Workout

    private ProgressBar mainLoadingProgressBar; // Main loading indicator

    // NEW: State variable to track payment status for retry
    private boolean hasPaidForGeneration = false;
    private String lastAttemptWorkoutName; // Store the name used for the paid attempt

    private Map<String, ExerciseModel> allExercisesMap = new HashMap<>();
    private static final String TAG = "WorkoutWizardActivity";

    private SharedPreferences sharedPreferences;
    private static final String KEY_HAS_PAID = "hasPaidForGeneration";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workout_wizard);

        // Find views
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

//        savedWorkoutsAccordionSection = findViewById(R.id.savedWorkoutsAccordionSection);
        savedWorkoutsHeader = findViewById(R.id.savedWorkoutsHeader);
        savedWorkoutsContent = findViewById(R.id.savedWorkoutsContent);
        savedWorkoutsExpandIconTextView = findViewById(R.id.savedWorkoutsExpandIconTextView);

        savedWorkoutsRecyclerView = findViewById(R.id.savedWorkoutsRecyclerView);
        savedWorkoutsProgressBar = findViewById(R.id.savedWorkoutsProgressBar); // List specific progress bar
        noSavedWorkoutsMessage = findViewById(R.id.noSavedWorkoutsMessage);

//        generateWorkoutAccordionSection = findViewById(R.id.generateWorkoutAccordionSection);
        generateWorkoutHeader = findViewById(R.id.generateWorkoutHeader);
        generateWorkoutContent = findViewById(R.id.generateWorkoutContent);
        generateWorkoutExpandIconTextView = findViewById(R.id.generateWorkoutExpandIconTextView);

        retryFetchWorkoutsButton = findViewById(R.id.retryFetchWorkoutsButton);


        // --- Setup RecyclerViews ---
        exercisesRecyclerView.setLayoutManager(new NonScrollingLinearLayoutManager(this));
        savedWorkoutsRecyclerView.setLayoutManager(new LinearLayoutManager(this)); // Set layout manager for saved workouts list

        // --- Initialize Adapter for Saved Workouts List ---
        // Pass 'this' as the listener because the Activity implements OnWorkoutSelectedListener
        savedWorkoutsAdapter = new SavedWorkoutsAdapter(new ArrayList<>(), this); // Pass empty list and listener
        savedWorkoutsRecyclerView.setAdapter(savedWorkoutsAdapter);

        // --- Set Accordion Header Click Listeners ---
        savedWorkoutsHeader.setOnClickListener(v -> toggleAccordionContent(savedWorkoutsContent, savedWorkoutsExpandIconTextView));
        generateWorkoutHeader.setOnClickListener(v -> toggleAccordionContent(generateWorkoutContent, generateWorkoutExpandIconTextView));
        // --- Initial State ---
        hideAllContentSections();

        // Load exercises from assets
        List<Exercise> allExercises = Utils.loadExercisesFromAssets(this);
        if(allExercises != null){
            for (Exercise exercise : allExercises) {
                allExercisesMap.put(exercise.getName(), Utils.getExerciseModel(exercise));
            }
        }

        aiService = new AiService();

        exercisesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        limitationsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedLimitation = (String) parent.getItemAtPosition(position);
                if(selectedLimitation.equals("Other")){
                    otherLimitationsEditText.setVisibility(View.VISIBLE);
                } else{
                    otherLimitationsEditText.setVisibility(View.GONE);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //Do nothing
            }
        });


        generateButton.setOnClickListener(v -> {
            String workoutName = workoutNameEditText.getText().toString().trim();

            // Validate workout name
            if (workoutName.isEmpty()) {
                workoutNameEditText.setError("Workout name is required."); // Show error on EditText
                // Optional: Show a Toast message as well
                Toast.makeText(this, "Please enter a name for your workout.", Toast.LENGTH_SHORT).show();
                return; // Stop the process
            } else {
                workoutNameEditText.setError(null); // Clear any previous error
            }

            sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
            hasPaidForGeneration = sharedPreferences.getBoolean(KEY_HAS_PAID, false);

            if (hasPaidForGeneration) {
                // User has already paid and previous generation failed. Allow retry.
                if (lastAttemptWorkoutName == null || lastAttemptWorkoutName.isEmpty()) {
                    Log.e(TAG, "hasPaidForGeneration is true but lastAttemptWorkoutName is null/empty!");
                    // Reset state and proceed with normal payment flow
                    resetGenerationState();
                    handleGenerateClick(workoutName); // Proceed with normal flow
                } else {
                    // Retry generation with the name from the last paid attempt
                    Log.d(TAG, "Retrying generation for paid attempt: " + lastAttemptWorkoutName);
                    // Pass the *last attempted name* and current request to AI generation
                    showLoading(); // Show loading indicator before AI call
                    processWorkoutTrx(workoutName);
                }
            } else {
                // Normal flow: User clicks generate, hasn't paid for this attempt yet
                handleGenerateClick(workoutName);
            }

            //grabBalanceAndProceed(workoutName);
        });

        setDefaultDailyFrequency();

        fetchAndDisplayUserWorkouts();

        retryFetchWorkoutsButton.setOnClickListener(v -> {
            fetchAndDisplayUserWorkouts(); // Call the fetch method again
        });

        noSavedWorkoutsMessage.setOnClickListener(v -> fetchAndDisplayUserWorkouts());
    }

    private void handleGenerateClick(String workoutName) {
        if (workoutName.isEmpty()) {
            workoutNameEditText.setError("Workout name is required.");
            Toast.makeText(this, "Please enter a name for your workout.", Toast.LENGTH_SHORT).show();
            return;
        } else {
            workoutNameEditText.setError(null);
        }
        // Proceed with balance check and payment flow
        grabBalanceAndProceed(workoutName);
    }


    // --- Accordion Toggle Logic ---
    private void toggleAccordionContent(View contentLayout, TextView expandIconTextView) {
        // Only toggle if the content isn't already hidden by being in a loading state or showing details
        if (mainLoadingProgressBar.getVisibility() == View.VISIBLE ||
                progressBar.getVisibility() == View.VISIBLE
                ) { // Prevent toggling if workout details are open
            return;
        }

        if (workoutDetailsLayout.getVisibility() == View.VISIBLE){
            hideWorkoutDetails();
            //workoutDetailsLayout.setVisibility(View.GONE);
        }

        if (contentLayout.getVisibility() == View.GONE) {
            // This content is closed, open it and close others

            // Collapse other content sections
            if (contentLayout == savedWorkoutsContent) {
                // Collapse the *other* content and its icon
                collapseContent(generateWorkoutContent, generateWorkoutExpandIconTextView);
            } else { // Must be generateWorkoutContent
                // Collapse the *other* content and its icon
                collapseContent(savedWorkoutsContent, savedWorkoutsExpandIconTextView);
            }

            // Expand the clicked content section
            expandContent(contentLayout, expandIconTextView);

        } else {
            // This content is open, close it
            collapseContent(contentLayout, expandIconTextView);
        }

        // After toggling, scroll to the top of the opened section's header
        ScrollView scrollView = findViewById(R.id.scrollView);
        if (scrollView != null) {
            View headerToScrollTo = (contentLayout == savedWorkoutsContent) ? savedWorkoutsHeader : generateWorkoutHeader;
            scrollView.post(() -> scrollView.requestChildFocus(headerToScrollTo, headerToScrollTo));
        }
    }

    // CHANGE: Accept TextView for the icon parameter and set rotation on TextView
    private void expandContent(View contentLayout, TextView expandIconTextView) {
        contentLayout.setVisibility(View.VISIBLE);
        expandIconTextView.setRotation(180); // Rotate TextView to point arrow down
    }

    // CHANGE: Accept TextView for the icon parameter and set rotation on TextView
    private void collapseContent(View contentLayout, TextView expandIconTextView) {
        contentLayout.setVisibility(View.GONE);
        expandIconTextView.setRotation(0); // Rotate TextView to point arrow up
    }

    // Hides all accordion sections, workout details, and all loading indicators
    private void hideAllContentSections() {
        //savedWorkoutsAccordionSection.setVisibility(View.GONE);
        //generateWorkoutAccordionSection.setVisibility(View.GONE);
        workoutDetailsLayout.setVisibility(View.GONE);
        mainLoadingProgressBar.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);

        // Ensure accordion content panels are also collapsed visually
        savedWorkoutsContent.setVisibility(View.GONE);
        generateWorkoutContent.setVisibility(View.GONE);
        // Reset expand icons (TextViews) rotation
        savedWorkoutsExpandIconTextView.setRotation(0);
        generateWorkoutExpandIconTextView.setRotation(0);

        // Hide list-specific loading/empty message within savedWorkoutsContent
        savedWorkoutsProgressBar.setVisibility(View.GONE);
        noSavedWorkoutsMessage.setVisibility(View.GONE);
        retryFetchWorkoutsButton.setVisibility(View.GONE);
    }


    // Shows the saved workouts accordion section and opens its content
    private void showSavedWorkoutsAccordion() {
        hideAllContentSections(); // Start fresh, hides everything including main loading
        //savedWorkoutsAccordionSection.setVisibility(View.VISIBLE);
        //generateWorkoutAccordionSection.setVisibility(View.VISIBLE); // Show both headers
        // Expand saved workouts content and rotate its icon
        expandContent(savedWorkoutsContent, savedWorkoutsExpandIconTextView);
        collapseContent(generateWorkoutContent, generateWorkoutExpandIconTextView);
    }

    // Shows the generate workout accordion section and opens its content
    private void showGenerateWorkoutAccordion() {
        hideAllContentSections(); // Start fresh, hides everything including main loading
        //savedWorkoutsAccordionSection.setVisibility(View.VISIBLE);
        //generateWorkoutAccordionSection.setVisibility(View.VISIBLE); // Show both headers
        // Expand generate workout content and rotate its icon
        expandContent(generateWorkoutContent, generateWorkoutExpandIconTextView);
        collapseContent(savedWorkoutsContent, savedWorkoutsExpandIconTextView);
        generateButton.setText("Generate Workout Plan");
    }


    // Show/Hide list specific loading/empty message within savedWorkoutsContent
    private void showListLoading() {
        // showSavedWorkoutsAccordion(); // This helper already shows the section and opens it
        savedWorkoutsProgressBar.setVisibility(View.VISIBLE);
        noSavedWorkoutsMessage.setVisibility(View.GONE);
        savedWorkoutsRecyclerView.setVisibility(View.GONE);
        retryFetchWorkoutsButton.setVisibility(View.GONE);
    }

    private void hideListLoading() {
        savedWorkoutsProgressBar.setVisibility(View.GONE);
        // Visibility of list/empty message within savedWorkoutsContent handled in fetch callback
    }

    private void showNoWorkoutsMessage(String message) {
        // Assumes savedWorkoutsAccordion is already shown and its content is open
        noSavedWorkoutsMessage.setText(message);
        noSavedWorkoutsMessage.setVisibility(View.VISIBLE);
        savedWorkoutsProgressBar.setVisibility(View.GONE);
        savedWorkoutsRecyclerView.setVisibility(View.GONE);
    }

    private void showWorkoutDetailsPanel() {
        hideAllContentSections(); // Hide all accordion sections
        workoutDetailsLayout.setVisibility(View.VISIBLE); // Show workout details section
        // Note: Back button will handle returning to the accordion
    }


    private void hideWorkoutDetails() {
        workoutDetailsLayout.setVisibility(View.GONE);
        // Clear adapter data when hiding details
        exercisesRecyclerView.setAdapter(null); // Set adapter to null or pass empty list
        retryFetchWorkoutsButton.setVisibility(View.GONE);
    }


    // --- Fetch Workouts List Logic ---
    private void fetchAndDisplayUserWorkouts() {
        String currentUserJwt = LoginActivity.accessToken;

        if (currentUserJwt == null || currentUserJwt.isEmpty()) {
            Log.w(TAG, "Cannot fetch workouts: JWT token missing.");
            showNoWorkoutsMessage("Authentication token missing."); // Show error message
            return;
        }

        showListLoading(); // Show loading indicator for the list

        WorkoutApiClient.fetchUserWorkouts(this, currentUserJwt, username,
                new WorkoutApiClient.FetchWorkoutsCallback() {
            @Override
            public void onSuccess(List<WorkoutPlan> workouts) {
                runOnUiThread(() -> {
                    hideListLoading(); // Hide loading

                    if (workouts != null && !workouts.isEmpty()) {
                        Log.d(TAG, "Fetched " + workouts.size() + " saved workouts.");
                        savedWorkoutsAdapter.setWorkoutList(workouts); // Update adapter data

                        showSavedWorkoutsAccordion(); // Show accordion and open saved workouts panel
                        savedWorkoutsRecyclerView.setVisibility(View.VISIBLE); // Ensure RecyclerView visible within content
                        noSavedWorkoutsMessage.setVisibility(View.GONE); // Hide empty message within content
                        retryFetchWorkoutsButton.setVisibility(View.GONE);

                        // Optional: Scroll to the top of the ScrollView to see the opened list header
                        ScrollView scrollView = findViewById(R.id.scrollView);
                        if (scrollView != null) {
                            scrollView.post(() -> scrollView.requestChildFocus(savedWorkoutsHeader, savedWorkoutsHeader));
                        }
                    } else {
                        Log.d(TAG, "No saved workouts found for the user.");
                        // No workouts found, show generate form by default
                        showNoWorkoutsMessage("No saved workouts yet."); // Show empty message within the *closed* saved content initially
                        retryFetchWorkoutsButton.setVisibility(View.GONE);
                        // Then show the generate workout section and open it
                        showGenerateWorkoutAccordion();

                        // Optional: Scroll to the top of the ScrollView to see the opened generate header
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
                    showNoWorkoutsMessage("Error loading workouts: " + errorMessage); // Show error message within *closed* saved content
                    // On failure, show generate form by default
                    //showGenerateWorkoutAccordion();
                    retryFetchWorkoutsButton.setVisibility(View.VISIBLE);
                    // Optional: Scroll to the top of the ScrollView
                    /*ScrollView scrollView = findViewById(R.id.scrollView);
                    if (scrollView != null) {
                        scrollView.post(() -> scrollView.requestChildFocus(generateWorkoutHeader, generateWorkoutHeader));
                    }*/
                });
            }
        });
    }


    // --- Implement the OnWorkoutSelectedListener Interface ---
    @Override
    public void onWorkoutSelected(WorkoutPlan workout) {
        // This method is called when a workout item in the saved list is clicked
        Log.d(TAG, "Workout item clicked: " + workout.getWorkoutName());

        // Since the GET /api/workouts endpoint returns full workout plans,
        // we can display it directly without another API call (fetchById).
        displayWorkoutPlan(workout);

        // Optional: Scroll to the top of the ScrollView to view the displayed workout details
        /*ScrollView scrollView = findViewById(R.id.scrollView); // Get reference to your ScrollView
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_UP));
        }*/
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

        //For other limitations, if other is selected, set otherLimitations, otherwise make it empty string
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
                    //also save workout plan
                    WorkoutApiClient.saveWorkoutPlan(
                            WorkoutWizardActivity.this, // Use Activity context for Volley requests
                            username,
                            LoginActivity.accessToken,
                            workoutName,
                            generatedPlan,
                            generatedExplanation,
                            new WorkoutApiClient.SaveWorkoutCallback() {
                                @Override
                                public void onSuccess() {
                                    runOnUiThread(() -> {
                                        Toast.makeText(WorkoutWizardActivity.this, "Workout plan saved successfully!", Toast.LENGTH_SHORT).show();
                                        // Enable button if you disabled it
                                        // findViewById(R.id.generateButton).setEnabled(true);
                                    });
                                }

                                @Override
                                public void onFailure(String errorMessage) {
                                    runOnUiThread(() -> {
                                        Log.e(TAG, "Failed to save workout plan to backend: " + errorMessage);
                                        // Optional: Inform the user saving failed (doesn't prevent them seeing the generated plan)
                                        Toast.makeText(WorkoutWizardActivity.this, "Failed to save workout: " + errorMessage, Toast.LENGTH_LONG).show();
                                        // Enable button if you disabled it
                                        // findViewById(R.id.generateButton).setEnabled(true);
                                    });
                                }
                            }
                    );

                } );
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    hideLoading(); // Hide main loading
                    showError("Workout generation failed: " + errorMessage); // Show specific error message

                    // --- Generation FAILURE: Keep paid state, update button ---
                    // hasPaidForGeneration is already true
                    // lastAttemptWorkoutName is already set
                    generateButton.setText("Retry Generation"); // Ensure button text is "Retry"

                    showGenerateWorkoutAccordion();
                });
            }
        });
    }


    private void displayWorkoutPlan(WorkoutPlan plan) { // Removed explanation parameter
        if (plan == null) {
            Log.w(TAG, "displayWorkoutPlan called with null plan.");
            hideWorkoutDetails();
            showGenerateWorkoutAccordion();
            return;
        }

        // Show the workout details layout
        workoutDetailsLayout.setVisibility(View.VISIBLE);

        // --- Update UI with Plan Details ---
        // Use getters from the updated WorkoutPlan model
        workoutPlanDescription.setText(plan.getDescription());
        workoutPlanExplanation.setText(plan.getExplanation());


        // --- Process and Display Exercises ---
        List<Exercise> exercises = plan.getExercises();

        Log.d(TAG, exercises.size()+"");
        Log.d(TAG, exercises.toString());

        if (!exercises.isEmpty()) {
            // --- Exercise Enhancement Loop ---
            // This loop enhances exercise objects using a local map (allExercisesMap)
            // Assuming allExercisesMap is a class member available here
            if (allExercisesMap != null && !allExercisesMap.isEmpty()) { // Check if map is loaded
                // Create a *copy* of the exercises list if you don't want to modify the original Plan object
                // List<Exercise> exercisesForDisplay = new ArrayList<>(exercises);
                // Use exercisesForDisplay in the loop and for the adapter

                for (int i = 0; i < exercises.size(); i++) {
                    Exercise exercise = exercises.get(i);
                    // Find matching local data by name
                    ExerciseModel matchingModel = Utils.findMatchingExercise(exercise.getName(), allExercisesMap);
                    if(matchingModel != null){
                        // Overwrite/add details from the local model for display
                        exercise.setImages(matchingModel.getImages());
                        exercise.setBodyPart(matchingModel.getBodyPart());
                        exercise.setEquipment(matchingModel.getEquipment());
                        // exercise.setId(matchingModel.getId()); // Be cautious about overwriting saved ID
                        exercise.setTarget(matchingModel.getTarget());
                        exercise.setPrimaryMuscles(matchingModel.getPrimaryMuscles());
                        exercise.setSecondaryMuscles(matchingModel.getSecondaryMuscles());
                        exercise.setInstructions(matchingModel.getInstructions());
                    } else {
                        Log.w(TAG, "No matching local ExerciseModel found for: " + exercise.getName());
                        // Exercises will be displayed with data only from the loaded/generated plan
                    }
                }
            } else {
                Log.w(TAG, "allExercisesMap is not loaded or empty. Cannot enhance exercises with local data.");
                // Exercises will be displayed with data only from the loaded/generated plan
            }
            // --- End Exercise Enhancement ---

            // Pass the potentially enhanced exercises to the adapter
            ExerciseAdapter adapter = new ExerciseAdapter(exercises);
            Log.d(TAG,"display:"+adapter.getItemCount());
            exercisesRecyclerView.setAdapter(adapter);

            // Calculate and set RecyclerView height after layout pass ---
            // Post to the RecyclerView to wait for it to be laid out
            exercisesRecyclerView.post(() -> {
                int totalHeight = 0;
                // Iterate through all visible items (or all items if laid out) and sum their heights
                // This is a pragmatic approach; getting the exact total height of *all* items
                // when wrap_content and canScrollVertically=false isn't working is tricky.
                // We can iterate through the adapter and measure views if necessary,
                // or rely on the LayoutManager if it provides a useful method despite canScrollVertically=false.
                // Let's try iterating through the children the LayoutManager HAS laid out.
                // For a NonScrollingLinearLayoutManager, it should attempt to lay out *all* items.

                // A more reliable way to get total height when canScrollVertically is false:
                // Iterate through the adapter's items, inflate and measure their view if not laid out yet.
                // This can be complex. A simpler heuristic might be needed if item heights are variable.
                // If item heights are relatively consistent, you might estimate.

                // *** Let's try a common pattern for this scenario: ***
                // Sum the height of children *after* the initial layout.
                // This often works because the LayoutManager *tries* to lay out all items
                // when told it cannot scroll.
                for (int i = 0; i < exercisesRecyclerView.getChildCount(); i++) {
                    totalHeight += exercisesRecyclerView.getChildAt(i).getHeight();
                }

                // If the RecyclerView's padding/margin needs to be accounted for
                // totalHeight += exercisesRecyclerView.getPaddingTop() + exercisesRecyclerView.getPaddingBottom();

                // Ensure height is not zero if there are items
                if (totalHeight > 0) {
                    // Get the current LayoutParams
                    ViewGroup.LayoutParams params = exercisesRecyclerView.getLayoutParams();
                    // Set the height to the calculated total
                    params.height = totalHeight;
                    // Apply the updated LayoutParams
                    exercisesRecyclerView.setLayoutParams(params);
                    Log.d(TAG, "Set exercisesRecyclerView height to: " + totalHeight);

                    // Optional: Scroll the main ScrollView to the end of the exercises section
                    // after setting the height
                    ScrollView scrollView = findViewById(R.id.scrollView);
                    if (scrollView != null) {
                        // Need to scroll past the header within the HorizontalScrollView
                        // Finding the right scroll target can be tricky here.
                        // Maybe scroll to the bottom of the workoutDetailsLayout
                        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                    }

                } else {
                    // If totalHeight is 0 (e.g., adapter set but layout not finished immediately)
                    // or if the list is empty, you might need a fallback or re-post
                    Log.w(TAG, "Calculated total height is 0 for exercisesRecyclerView. Height not set.");
                    // If the list is confirmed non-empty, this is a layout timing issue.
                    // You might need a more sophisticated approach or a slight delay before calculating.
                }

            }); // End exercisesRecyclerView.post()
            // notifyDataSetChanged is often called implicitly when setting adapter
            // adapter.notifyDataSetChanged();
        } else {
            Log.w(TAG, "Workout plan exercises list is null.");
            // Handle case where exercise list is null (e.g., clear adapter, show message)
            exercisesRecyclerView.setAdapter(null); // Clear existing adapter
            // Set height to 0 if the list is empty
            ViewGroup.LayoutParams params = exercisesRecyclerView.getLayoutParams();
            params.height = 0;
            exercisesRecyclerView.setLayoutParams(params);
            Log.d(TAG, "Set exercisesRecyclerView height to 0 as list is empty.");
        }

        showWorkoutDetailsPanel();


        // Optional: Scroll the ScrollView down to the workout details after display
        /*ScrollView scrollView = findViewById(R.id.scrollView); // Get reference to your ScrollView
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }*/
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
                // This code runs *after* the balance is successfully received

                // Check the balance here!
                if (balance < Constants.MIN_AFIT_PER_WORKOUT) {
                    // Insufficient funds, show the error dialog
                    mainLoadingProgressBar.setVisibility(View.GONE); // Hide loading
                    generateButton.setEnabled(true);
                    showInsufficientFundsDialog((long) balance); // Cast to long if your dialog expects long
                    showGenerateWorkoutAccordion();
                    // Hide progress and re-enable button *after* showing the dialog
                    // findViewById(R.id.progressBar).setVisibility(View.GONE);
                    // generateButton.setEnabled(true);

                } else {
                    // User has enough AFIT, show the payment confirmation dialog
                    showPaymentConfirmationDialog(workoutName);

                    // Hide progress and re-enable button *after* showing the dialog
                    // findViewById(R.id.progressBar).setVisibility(View.GONE);
                    // generateButton.setEnabled(true); // Or re-enable inside dialog listeners if needed
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

                // Hide progress and re-enable button *after* showing the error
                // findViewById(R.id.progressBar).setVisibility(View.GONE);
                //generateButton.setEnabled(true);
            }
        });
    }

    private void processWorkoutTrx(String workoutName){

        showLoading();

        Context ctx = getApplicationContext();

        RequestQueue queue = Volley.newRequestQueue(ctx);

        //first make sure if user is properly logged in as we need to connect to server
        if (LoginActivity.accessToken.isEmpty()){
            final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
            String pkey = sharedPreferences.getString("actifitPst", "");

            //authorize user login based on credentials if user is already verified
            if (!pkey.isEmpty()) {
                String loginAuthUrl = Utils.apiUrl(ctx)+ ctx.getString(R.string.login_auth);


                JSONObject loginSettings = new JSONObject();
                try {
                    loginSettings.put(ctx.getString(R.string.username_param), username);
                    loginSettings.put(ctx.getString(R.string.pkey_param), pkey);
                    loginSettings.put(ctx.getString(R.string.bchain_param), "HIVE");//default always HIVE
                    loginSettings.put(ctx.getString(R.string.keeploggedin_param), false);//TODO make dynamic
                    loginSettings.put(ctx.getString(R.string.login_source), ctx.getString(R.string.android) + BuildConfig.VERSION_NAME);
                } catch (JSONException e) {
                    //Log.e(TAG, e.getMessage());
                }

                //grab auth token for logged in user
                JsonObjectRequest loginRequest = new JsonObjectRequest(Request.Method.POST,
                        loginAuthUrl, loginSettings,
                        response -> {
                            //store token for reuse when saving settings
                            try {
                                if (response.has("success")) {
                                    Log.d(TAG, response.toString());
                                    LoginActivity.accessToken = response.getString(ctx.getString(R.string.login_token));
                                }
                            } catch (JSONException e) {
                                Log.e(TAG, Objects.requireNonNull(e.getMessage()));
                                //e.printStackTrace();
                            }
                        },
                        error -> {
                            // error
                            Log.e(TAG, "Login error");
                        });

                queue.add(loginRequest);

            }


        }

        //successfully bought product. Generate workout
        if (!hasPaidForGeneration ) {

            //prepare query and broadcast to bchain

            //param 1
            String op_name = "custom_json";

            //param 2
            JSONObject cstm_params = new JSONObject();
            try {

                JSONArray required_auths = new JSONArray();

                JSONArray required_posting_auths = new JSONArray();
                required_posting_auths.put(username);

                //cstm_params.put("required_auths", "[]");
                cstm_params.put("required_auths", required_auths);
                cstm_params.put("required_posting_auths", required_posting_auths);
                cstm_params.put("id", "actifit");
                //cstm_params.put("json", json_op_details);
                cstm_params.put("json", "{\"transaction\": \"generate-workout-wizard\"}");

                JSONArray operation = new JSONArray();
                operation.put(0, op_name);
                operation.put(1, cstm_params);

                String bcastUrl = (getString(R.string.test_mode).equals("on") ?
                        getString(R.string.test_server) : Utils.apiUrl(ctx)) +
                        ctx.getString(R.string.perform_trx_link) +
                        username +
                        "&operation=[" + operation + "]" +
                        "&bchain=HIVE";//hardcoded for now


                //send out transaction for payment
                JsonObjectRequest transRequest = new JsonObjectRequest(Request.Method.GET,
                        bcastUrl, null,
                        response -> {

                            Log.d(TAG, response.toString());
                            boolean isSuccessful = response.optBoolean("success", false);
                            //
                            if (isSuccessful) {
                                //successfully wrote to chain gadget purchase
                                try {
                                    JSONObject bcastRes = response.getJSONObject("trx").
                                            getJSONObject("tx");

                                    Log.d(TAG, LoginActivity.accessToken);

                                    String buyUrl = (getString(R.string.test_mode).equals("on") ?
                                            getString(R.string.test_server) : Utils.apiUrl(ctx)) +
                                            ctx.getString(R.string.generate_workout_link) +
                                            username + "/" +
                                            bcastRes.get("ref_block_num") + "/" +
                                            bcastRes.get("id") + "/" +
                                            "HIVE" +
                                            "/?user=" + username;


                                    //send out transaction
                                    JsonObjectRequest buyRequest = new JsonObjectRequest(Request.Method.GET,
                                            buyUrl, null,
                                            response1 -> {
                                                //progress.dismiss();
                                                //buyAFIT.clearAnimation();
                                                Log.d(TAG, response1.toString());
                                                //
                                                if (!response1.has("error")
                                                        &&
                                                        (response1.optBoolean("success", false)
                                                                || response1.optString("status", "")
                                                                .equalsIgnoreCase("success"))
                                                ) {
                                                    hasPaidForGeneration = true;
                                                    saveGenerationState();
                                                    //successfully bought product. Generate workout
                                                    WorkoutRequest workoutRequest = getUserInputFromUI();
                                                    generateWorkoutPlan(workoutName, workoutRequest);

                                                } else {
                                                    Log.e(TAG, response1.toString());
                                                    Toast.makeText(ctx, ctx.getString(R.string.error_transaction)
                                                            , Toast.LENGTH_LONG).show();
                                                    hideLoading();
                                                }
                                            },
                                            error -> {
                                                // error
                                                Log.e(TAG, error.toString());
                                                //progress.dismiss();
                                                //buyAFIT.clearAnimation();
                                                Toast.makeText(ctx, ctx.getString(R.string.error_transaction)
                                                        , Toast.LENGTH_LONG).show();
                                                hideLoading();
                                            }) {

                                        @Override
                                        public Map<String, String> getHeaders() {
                                            final Map<String, String> params = new HashMap<>();
                                            params.put("Content-Type", "application/json");
                                            params.put(ctx.getString(R.string.validation_header)
                                                    , ctx.getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken);
                                            return params;
                                        }
                                    };

                                    queue.add(buyRequest);


                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                //progress.dismiss();
                                // buyAFIT.clearAnimation();
                                Log.e(TAG, response.toString());
                                Toast.makeText(ctx, ctx.getString(R.string.error_transaction)
                                        , Toast.LENGTH_LONG).show();
                                hideLoading();
                            }

                        },
                        error -> {
                            // error
                            Log.d(TAG, error.toString());
                            //progress.dismiss();
                            //buyAFIT.clearAnimation();
                            Toast.makeText(ctx, ctx.getString(R.string.error_transaction)
                                    , Toast.LENGTH_LONG).show();
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
        }else{
            WorkoutRequest workoutRequest = getUserInputFromUI();
            generateWorkoutPlan(workoutName, workoutRequest);
        }

    }

    private void saveGenerationState() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_HAS_PAID, hasPaidForGeneration);
        editor.apply(); // Use apply() for asynchronous saving
        //Log.d(TAG, "Saved state: hasPaidForGeneration=" + hasPaidForGeneration + ", lastAttemptWorkoutName=" + lastAttemptWorkoutName);
    }

    private void clearGenerationState() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_HAS_PAID);
        editor.apply();
        Log.d(TAG, "Cleared state from SharedPreferences.");
    }

    // Helper method to reset the payment/generation state (in-memory and persistent)
    // MODIFIED: Now calls clearGenerationState()
    private void resetGenerationState() {
        hasPaidForGeneration = false;
        lastAttemptWorkoutName = null;
        generateButton.setText("Generate Workout Plan"); // Reset button text
        clearGenerationState(); // <-- Clear from SharedPreferences
    }

    private void showPaymentConfirmationDialog(String workoutName) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Workout Generation")
                .setMessage("Generating this custom workout plan costs " + Constants.MIN_AFIT_PER_WORKOUT + " AFIT. Do you want to proceed and pay?")
                .setPositiveButton("Pay " + Constants.MIN_AFIT_PER_WORKOUT + " AFIT", (dialog, which) -> {
                    lastAttemptWorkoutName = workoutName;
                    // Initiate the actual AFIT payment process here
                    processWorkoutTrx(workoutName);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    hideLoading(); // Hide loading if user cancels payment
                    showGenerateWorkoutAccordion(); // Return to generate form
                    // Re-enable button and hide progress bar if you disabled them
                    // generateButton.setEnabled(true);
                    // findViewById(R.id.progressBar).setVisibility(View.GONE);
                })
                .setIcon(getResources().getDrawable(R.drawable.actifit_logo)) // Or a relevant icon
                .show();
    }

    private void showInsufficientFundsDialog(long current) {
        new AlertDialog.Builder(this)
                .setTitle("Insufficient AFIT")
                .setMessage("You need " + Constants.MIN_AFIT_PER_WORKOUT +
                        " AFIT to generate a workout plan. Your current balance is "
                        + current + " AFIT. Please acquire more AFIT.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                // Optional: Add a button to navigate user to acquire AFIT
                // .setNegativeButton("Get AFIT", (dialog, which) -> { /* Navigate to acquire AFIT screen/dialog */ })
                .setIcon(android.R.drawable.ic_dialog_alert) // Or a relevant icon
                .show();
    }

    private void showLoading() {
        hideAllContentSections(); // Hide all accordion sections and workout details
        mainLoadingProgressBar.setVisibility(View.VISIBLE); // Show the main loading bar
        // generateButton.setEnabled(false); // Optional: disable generate button if it was visible
    }

    // Hides the main loading indicator.
    private void hideLoading() {
        mainLoadingProgressBar.setVisibility(View.GONE); // Hide the main loading bar
        // generateButton.setEnabled(true); // Optional: re-enable generate button if it's appropriate now
    }

    private void showError(String message) {
        Toast.makeText(this, "Error " + message, Toast.LENGTH_LONG).show();
    }

    // onBackPressed hides details and calls showFormOrList which decides which accordion panel to show
    @Override
    public void onBackPressed() {
        if (workoutDetailsLayout.getVisibility() == View.VISIBLE) {
            hideWorkoutDetails(); // Hide details
            showFormOrList(); // Decides whether to show form or list panel (and manages button visibility)
        } else {
            super.onBackPressed();
        }
    }

    // Helper to decide which accordion panel to show after hiding details or on failure
    private void showFormOrList() {
        // This helper is called when returning from workout details or after certain failures.
        // It checks if there are items currently in the saved workouts adapter.
        if (savedWorkoutsAdapter != null && savedWorkoutsAdapter.getItemCount() > 0) {
            showSavedWorkoutsAccordion(); // Show accordion and open list panel
        } else {
            showGenerateWorkoutAccordion(); // Show accordion and open generate form panel
        }
    }
}