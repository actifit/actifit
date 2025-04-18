package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.Button;

import java.util.List;
import java.util.HashMap;
import java.util.Map;


public class WorkoutWizardActivity extends AppCompatActivity {

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
    private Button generateButton;
    private Map<String, ExerciseModel> allExercisesMap = new HashMap<>();


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
            WorkoutRequest workoutRequest = getUserInputFromUI();
            generateWorkoutPlan(workoutRequest);
        });

        setDefaultDailyFrequency();
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

        return new WorkoutRequest(fitnessGoal, experienceLevel, weeklyTime, preferredWorkout, equipment, limitations, otherLimitations,dailyFrequency);
    }


    private void generateWorkoutPlan(WorkoutRequest request) {
        showLoading();
        aiService.generateWorkoutPlan(request, new AiService.ResponseCallback() {
            @Override
            public void onSuccess(AiResponse response) {
                runOnUiThread(() ->{
                    hideLoading();
                    displayWorkoutPlan(response.getWorkoutPlan(), response.getExplanation());
                } );
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    hideLoading();
                    showError(errorMessage);
                });
            }
        });
    }


    private void displayWorkoutPlan(WorkoutPlan plan, String explanation) {
        workoutPlanDescription.setText(plan.getDescription());
        workoutPlanExplanation.setText(explanation);
        workoutDetailsLayout.setVisibility(View.VISIBLE);
        List<Exercise> exercises = plan.getExercises();
        for (int i = 0; i < exercises.size(); i++) {
            Exercise exercise = exercises.get(i);
            ExerciseModel matchingModel = Utils.findMatchingExercise(exercise.getName(), allExercisesMap);
            if(matchingModel != null){
                exercise.setImages(matchingModel.getImages());
                exercise.setBodyPart(matchingModel.getBodyPart());
                exercise.setEquipment(matchingModel.getEquipment());
                exercise.setId(matchingModel.getId());
                exercise.setTarget(matchingModel.getTarget());
                exercise.setPrimaryMuscles(matchingModel.getPrimaryMuscles());
                exercise.setSecondaryMuscles(matchingModel.getSecondaryMuscles());
                exercise.setInstructions(matchingModel.getInstructions());
            }
        }
        ExerciseAdapter adapter = new ExerciseAdapter(plan.getExercises());
        exercisesRecyclerView.setAdapter(adapter);
        adapter.notifyDataSetChanged();
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

    private void showLoading(){
        progressBar.setVisibility(View.VISIBLE);
    }

    private void hideLoading(){
        progressBar.setVisibility(View.GONE);
    }

    private void showError(String message) {
        Toast.makeText(this, "Error " + message, Toast.LENGTH_LONG).show();
    }
}