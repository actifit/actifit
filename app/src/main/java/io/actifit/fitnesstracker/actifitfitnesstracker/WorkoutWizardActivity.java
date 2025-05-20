package io.actifit.fitnesstracker.actifitfitnesstracker;

import static io.actifit.fitnesstracker.actifitfitnesstracker.MainActivity.username;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
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

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


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
    private EditText workoutNameEditText;
    private Button generateButton;
    private Map<String, ExerciseModel> allExercisesMap = new HashMap<>();
    private static final String TAG = "WorkoutWizardActivity";

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

            grabBalanceAndProceed();
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
        aiService.generateWorkoutPlan(request, new AiService.ResponseCallback() {
            @Override
            public void onSuccess(AiResponse response) {
                runOnUiThread(() ->{
                    hideLoading();
                    WorkoutPlan generatedPlan = response.getWorkoutPlan();
                    String generatedExplanation = response.getExplanation();
                    displayWorkoutPlan(generatedPlan, generatedExplanation);

                    String workoutName = workoutNameEditText.getText().toString().trim();

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

    private void grabBalanceAndProceed(){
        Context ctx = getApplicationContext();
        if (username == null || username.isEmpty()){
            Toast.makeText(ctx, ctx.getString(R.string.username_missing), Toast.LENGTH_LONG).show();
            return;
        }

        Utils.fetchUserBalance(this, username, false, new Utils.BalanceFetchListener() {

            @Override
            public void onBalanceFetched(double balance) {
                // This code runs *after* the balance is successfully received

                // Check the balance here!
                if (balance < Constants.MIN_AFIT_PER_WORKOUT) {
                    // Insufficient funds, show the error dialog
                    showInsufficientFundsDialog((long) balance); // Cast to long if your dialog expects long
                    // Hide progress and re-enable button *after* showing the dialog
                    // findViewById(R.id.progressBar).setVisibility(View.GONE);
                    // generateButton.setEnabled(true);

                } else {
                    // User has enough AFIT, show the payment confirmation dialog
                    showPaymentConfirmationDialog();

                    // Hide progress and re-enable button *after* showing the dialog
                    // findViewById(R.id.progressBar).setVisibility(View.GONE);
                    // generateButton.setEnabled(true); // Or re-enable inside dialog listeners if needed
                }
            }

            @Override
            public void onBalanceFetchFailed(String errorMessage) {
                // This code runs if fetching the balance failed (network error, JSON error etc.)

                Log.e(TAG, "Failed to fetch user balance: " + errorMessage);
                Toast.makeText(WorkoutWizardActivity.this, "Error fetching balance: " + errorMessage, Toast.LENGTH_LONG).show(); // Use Activity.this for context

                // Hide progress and re-enable button *after* showing the error
                // findViewById(R.id.progressBar).setVisibility(View.GONE);
                //generateButton.setEnabled(true);
            }
        });
    }

    private void processWorkoutTrx(){

        showLoading();

        Context ctx = getApplicationContext();
        //buyAFIT.startAnimation(scaler);
        //buyAFIT.animate().scaleX(0.5f).scaleY(0.5f).setDuration(3000).;
        //buyAFIT.animate().scaleXBy(1).setDuration(3000); //.startAnimation();

        //progress.setMessage(getContext().getString(R.string.processingBuyGadget));
        //progress.show();

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

        //prepare query and broadcast to bchain

        //param 1
        String op_name = "custom_json";

        //param 2
        JSONObject cstm_params = new JSONObject();
        try {

            JSONArray required_auths= new JSONArray();

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

            String bcastUrl = getString(R.string.test_mode).equals("on")?
                                    getString(R.string.test_server):Utils.apiUrl(ctx)+
                                ctx.getString(R.string.perform_trx_link) +
                                username +
                                "&operation=[" + operation + "]" +
                                "&bchain=HIVE";//hardcoded for now


            //send out transaction
            JsonObjectRequest transRequest = new JsonObjectRequest(Request.Method.GET,
                    bcastUrl, null,
                    response -> {

                        Log.d(TAG, response.toString());
                        boolean isSuccessful = response.optBoolean("success", false);
                        //
                        if (isSuccessful){
                            //successfully wrote to chain gadget purchase
                            try {
                                JSONObject bcastRes = response.getJSONObject("trx").
                                        getJSONObject("tx");

                                Log.d(TAG, LoginActivity.accessToken);

                                String buyUrl = getString(R.string.test_mode).equals("on")?
                                        getString(R.string.test_server):Utils.apiUrl(ctx)+
                                        ctx.getString(R.string.generate_workout_link)+
                                        username+"/"+
                                        bcastRes.get("ref_block_num")+"/"+
                                        bcastRes.get("id")+"/"+
                                        "HIVE"+
                                        "/?user="+username;


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
                                                //successfully bought product. Generate workout
                                                WorkoutRequest workoutRequest = getUserInputFromUI();
                                                generateWorkoutPlan(workoutRequest);

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
                                        }){

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
                        }else{
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
        }  catch (Exception excep) {
            excep.printStackTrace();
        }

    }

    private void showPaymentConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Workout Generation")
                .setMessage("Generating this custom workout plan costs " + Constants.MIN_AFIT_PER_WORKOUT + " AFIT. Do you want to proceed and pay?")
                .setPositiveButton("Pay " + Constants.MIN_AFIT_PER_WORKOUT + " AFIT", (dialog, which) -> {
                    // Initiate the actual AFIT payment process here
                    processWorkoutTrx();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
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