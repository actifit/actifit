package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static io.actifit.fitnesstracker.actifitfitnesstracker.MainActivity.username;
import static io.actifit.fitnesstracker.actifitfitnesstracker.LoginActivity.accessToken;


public class EditWorkoutActivity extends BaseActivity
        implements EditableExercisesAdapter.OnRemoveExerciseListener {

    private static final String TAG = "EditWorkoutActivity";

    // UI elements
    private EditText editWorkoutNameEditText;
    private EditText editWorkoutDescriptionEditText;
    private EditText editWorkoutExplanationEditText;
    private Button addExerciseButton;
    private Button saveChangesFab;
    private RecyclerView editableExercisesRecyclerView;
    private TextView noExercisesMessage;

    // Data
    private WorkoutPlan originalWorkoutPlan;
    private List<Exercise> currentExercises;
    private EditableExercisesAdapter adapter;

    // ActivityResultLauncher for adding exercises from ExerciseSearchActivity
    private ActivityResultLauncher<Intent> addExercisesLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_workout);

        // Initialize ActivityResultLauncher for adding exercises
        addExercisesLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<Exercise> newlySelectedExercises = (ArrayList<Exercise>) result.getData().getSerializableExtra("selectedExercises");
                        if (newlySelectedExercises != null && !newlySelectedExercises.isEmpty()) {
                            // Add new exercises, avoiding duplicates (based on exercise ID)
                            for (Exercise newEx : newlySelectedExercises) {
                                boolean exists = false;
                                for (Exercise existingEx : currentExercises) {
                                    if (existingEx.getId() != null && existingEx.getId().equals(newEx.getId())) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists) {
                                    currentExercises.add(newEx);
                                    Toast.makeText(this, getString(R.string.exercise_added_toast), Toast.LENGTH_SHORT).show();
                                }
                            }
                            updateExercisesListUI();
                        } else {
                            Toast.makeText(this, R.string.no_exercises_selected_for_add, Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        initUI();
        loadWorkoutData();
        setupListeners();
    }

    private void initUI() {
        editWorkoutNameEditText = findViewById(R.id.editWorkoutNameEditText);
        editWorkoutDescriptionEditText = findViewById(R.id.editWorkoutDescriptionEditText);
        editWorkoutExplanationEditText = findViewById(R.id.editWorkoutExplanationEditText);
        addExerciseButton = findViewById(R.id.addExerciseButton);
        saveChangesFab = findViewById(R.id.saveChangesFab);
        editableExercisesRecyclerView = findViewById(R.id.editableExercisesRecyclerView);
        noExercisesMessage = findViewById(R.id.noExercisesMessage);

        editableExercisesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        currentExercises = new ArrayList<>(); // Initialize empty list, will be populated from intent
        adapter = new EditableExercisesAdapter(this, currentExercises, this); // 'this' for OnRemoveExerciseListener
        editableExercisesRecyclerView.setAdapter(adapter);
    }

    private void loadWorkoutData() {
        originalWorkoutPlan = (WorkoutPlan) getIntent().getSerializableExtra("workoutPlan");

        if (originalWorkoutPlan == null) {
            Toast.makeText(this, "Error: No workout data provided.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setTitle(getString(R.string.edit_workout_title)); // Set activity title

        editWorkoutNameEditText.setText(originalWorkoutPlan.getWorkoutName());
        editWorkoutDescriptionEditText.setText(originalWorkoutPlan.getDescription());
        editWorkoutExplanationEditText.setText(originalWorkoutPlan.getExplanation());

        // Create a mutable copy of the exercises list
        currentExercises.clear();
        if (originalWorkoutPlan.getExercises() != null) {
            currentExercises.addAll(originalWorkoutPlan.getExercises());
        }
        updateExercisesListUI();
    }

    private void setupListeners() {
        addExerciseButton.setOnClickListener(v -> {
            Intent intent = new Intent(EditWorkoutActivity.this, ExerciseSearchActivity.class);
            intent.putExtra("selectionMode", true); // Tell ExerciseSearchActivity to go into selection mode
            // Optionally pass existing exercises to pre-select them in search or filter them out
            // intent.putExtra("existingExerciseIds", currentExercises.stream().map(Exercise::getId).collect(Collectors.toCollection(ArrayList::new)));
            addExercisesLauncher.launch(intent);
        });

        saveChangesFab.setOnClickListener(v -> saveEditedWorkout());
    }

    private void updateExercisesListUI() {
        adapter.setExercises(currentExercises); // Update adapter with current exercises
        if (currentExercises.isEmpty()) {
            noExercisesMessage.setVisibility(View.VISIBLE);
            editableExercisesRecyclerView.setVisibility(View.GONE);
        } else {
            noExercisesMessage.setVisibility(View.GONE);
            editableExercisesRecyclerView.setVisibility(View.VISIBLE);
            editableExercisesRecyclerView.post(() -> expandRecyclerViewHeight(editableExercisesRecyclerView));
        }
    }

    @Override
    public void onRemoveExercise(Exercise exercise) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_remove_exercise_title)
                .setMessage(getString(R.string.confirm_remove_exercise_message, exercise.getName()))
                .setPositiveButton(R.string.dialog_button_remove, (dialog, which) -> {
                    currentExercises.remove(exercise);
                    updateExercisesListUI();
                    Toast.makeText(EditWorkoutActivity.this, R.string.exercise_removed_toast, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.button_cancel, (dialog, which) -> dialog.dismiss())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void saveEditedWorkout() {
        String workoutId = originalWorkoutPlan.getId();
        String workoutName = editWorkoutNameEditText.getText().toString().trim();
        String workoutDescription = editWorkoutDescriptionEditText.getText().toString().trim();
        String workoutExplanation = editWorkoutExplanationEditText.getText().toString().trim();

        if (workoutName.isEmpty()) {
            editWorkoutNameEditText.setError(getString(R.string.toast_enter_workout_title));
            editWorkoutNameEditText.requestFocus();
            return;
        }
        if (workoutDescription.isEmpty()) {
            workoutDescription = "No description provided.";
        }
        if (workoutExplanation.isEmpty()) {
            workoutExplanation = "No explanation provided.";
        }

        if (currentExercises.isEmpty()) {
            Toast.makeText(this, R.string.no_exercises_in_workout, Toast.LENGTH_LONG).show();
            return;
        }

        // Create an updated WorkoutPlan object
        WorkoutPlan updatedWorkout = new WorkoutPlan(
                workoutId, // Important: Use the original ID for the PUT request
                workoutName,
                workoutDescription,
                originalWorkoutPlan.getTimestamp(), // Keep original timestamp or update to new Date()
                workoutExplanation,
                currentExercises
        );

        String currentUserJwt = accessToken;
        String currentUserId = username;

        if (currentUserJwt == null || currentUserJwt.isEmpty() || currentUserId == null || currentUserId.isEmpty()) {
            Toast.makeText(this, "Authentication error. Please log in.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, getString(R.string.workout_update_in_progress), Toast.LENGTH_SHORT).show();

        WorkoutApiClient.updateWorkoutPlan( // Assuming you'll create this method in WorkoutApiClient
                this,
                currentUserJwt,
                workoutId,
                currentUserId,
                updatedWorkout,
                new WorkoutApiClient.SaveWorkoutCallback() { // Reusing SaveWorkoutCallback for simplicity
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(EditWorkoutActivity.this,
                                    getString(R.string.toast_workout_updated_success, workoutName),
                                    Toast.LENGTH_SHORT).show();
                            // Send result back to WorkoutWizardActivity to refresh
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra(getString(R.string.result_workout_saved_refresh_key), true);
                            setResult(RESULT_OK, resultIntent);
                            finish(); // Close EditWorkoutActivity
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(() -> {
                            Toast.makeText(EditWorkoutActivity.this,
                                    getString(R.string.toast_workout_updated_failure, errorMessage),
                                    Toast.LENGTH_LONG).show();
                            Log.e(TAG, "Failed to update workout: " + errorMessage);
                        });
                    }
                }
        );
    }
    public void expandRecyclerViewHeight(RecyclerView recyclerView) {
        RecyclerView.Adapter adapter = recyclerView.getAdapter();
        if (adapter == null) {
            return;
        }
        int totalHeight = 0;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(recyclerView.getMeasuredWidth(), View.MeasureSpec.EXACTLY);

        // Fallback for cases where RecyclerView width is not yet measured (e.g. during initial layout)
        if (recyclerView.getMeasuredWidth() == 0) {
            // Estimate width: parent width minus horizontal padding. Adjust 16dp to your actual padding.
            int horizontalPadding = (int) (getResources().getDisplayMetrics().density * 16 * 2); // 16dp left + 16dp right
            widthSpec = View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().widthPixels - horizontalPadding, View.MeasureSpec.AT_MOST);
        }

        for (int i = 0; i < adapter.getItemCount(); i++) {
            RecyclerView.ViewHolder holder = adapter.createViewHolder(recyclerView, adapter.getItemViewType(i));
            adapter.onBindViewHolder(holder, i);
            holder.itemView.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            totalHeight += holder.itemView.getMeasuredHeight();
        }
        ViewGroup.LayoutParams params = recyclerView.getLayoutParams();
        params.height = totalHeight;
        recyclerView.setLayoutParams(params); // Corrected: setLayoutLayout -> setLayoutParams
        Log.d(TAG, "Forcing RecyclerView height to: " + totalHeight + " for " + adapter.getItemCount() + " items.");
    }
}