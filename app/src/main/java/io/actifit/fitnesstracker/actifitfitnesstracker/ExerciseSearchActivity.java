package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils; // Import TextUtils
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.RotateAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors; // Requires API level 24 for stream API

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton; // NEW: For FAB
import com.google.gson.Gson; // NEW: Import Gson
import java.util.Comparator;

import static io.actifit.fitnesstracker.actifitfitnesstracker.MainActivity.username; // Assuming username is globally accessible
import static io.actifit.fitnesstracker.actifitfitnesstracker.LoginActivity.accessToken; // Assuming accessToken is globally accessible

public class ExerciseSearchActivity extends BaseActivity
        implements ExerciseSearchAdapter.OnExerciseClickListener,
        ExerciseSearchAdapter.OnSelectionChangeListener {

    private static final String TAG = "ExerciseSearchActivity";
    public static final int REQUEST_CODE_SAVE_WORKOUT = 1;
    public static final String EXTRA_SELECTION_MODE = "selectionMode";

    // UI elements
    private EditText searchEditText;
    private Spinner bodyPartSpinner, equipmentSpinner, levelSpinner, categorySpinner, primaryMuscleSpinner;
    private RecyclerView exercisesRecyclerView;
    private ProgressBar loadingProgressBar;
    private TextView noResultsTextView;

    private LinearLayout filterOptionsHeader;
    private LinearLayout filterContentLayout;
    private TextView filterExpandIconTextView;

    private Button saveWorkoutFab;
    private boolean isSelectionMode = false;

    // Data
    private List<Exercise> allExercises;
    private List<Exercise> filteredExercises;
    private ExerciseSearchAdapter adapter;

    private ActivityResultLauncher<Intent> saveWorkoutResultLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise_search);

        isSelectionMode = getIntent().getBooleanExtra(EXTRA_SELECTION_MODE, false);

        saveWorkoutResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // This callback will be triggered if you start another activity
                    // from ExerciseSearchActivity and it returns a result.
                    // For example, if you navigate to ExerciseDetailActivity and it
                    // returns something, you can handle it here.
                    // In our current flow, ExerciseSearchActivity itself finishes with a result.
                });

        initUI();
        loadExercises();
        setupListeners();

        filterContentLayout.setVisibility(View.GONE);
        filterExpandIconTextView.setText(R.string.fa_chevron_right); // Set initial icon
        filterExpandIconTextView.setRotation(0); // Ensure initial rotation is 0 for right arrow

        if (isSelectionMode) {
            saveWorkoutFab.setText(getString(R.string.selected_exercises_done, 0)); // Initial text for selection mode
            saveWorkoutFab.setVisibility(View.GONE); // Will become visible when selections are made
        } else {
            saveWorkoutFab.setText(getString(R.string.action_save_workout));
            saveWorkoutFab.setVisibility(View.GONE); // Will become visible when selections are made
        }
    }

    private void initUI() {
        searchEditText = findViewById(R.id.searchEditText);
        bodyPartSpinner = findViewById(R.id.bodyPartSpinner);
        equipmentSpinner = findViewById(R.id.equipmentSpinner);
        levelSpinner = findViewById(R.id.levelSpinner);
        categorySpinner = findViewById(R.id.categorySpinner);
        primaryMuscleSpinner = findViewById(R.id.primaryMuscleSpinner);
        exercisesRecyclerView = findViewById(R.id.exercisesRecyclerView);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        noResultsTextView = findViewById(R.id.noResultsTextView);

        filterOptionsHeader = findViewById(R.id.filterOptionsHeader);
        filterContentLayout = findViewById(R.id.filterContentLayout);
        filterExpandIconTextView = findViewById(R.id.filterExpandIconTextView);

        saveWorkoutFab = findViewById(R.id.saveWorkoutFab);

        exercisesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExerciseSearchAdapter(this, new ArrayList<>(), this, this);
        exercisesRecyclerView.setAdapter(adapter);
    }

    private void loadExercises() {
        loadingProgressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            allExercises = Utils.loadExercisesFromAssets(this);
            runOnUiThread(() -> {
                loadingProgressBar.setVisibility(View.GONE);
                if (allExercises != null && !allExercises.isEmpty()) {
                    Log.d(TAG, "Loaded " + allExercises.size() + " exercises from assets.");
                    populateFilterSpinners();
                    filterExercises(); // Initial filtering
                } else {
                    noResultsTextView.setText(getString(R.string.error_loading_exercises, "No data found."));
                    noResultsTextView.setVisibility(View.VISIBLE);
                    Log.e(TAG, "Failed to load exercises from assets.");
                }
            });
        }).start();
    }

    private void populateFilterSpinners() {
        // Use LinkedHashSet to maintain insertion order while ensuring uniqueness
        // and add "All" as the first option
        Set<String> bodyParts = new HashSet<>(Collections.singletonList(getString(R.string.filter_none)));
        Set<String> equipments = new HashSet<>(Collections.singletonList(getString(R.string.filter_none)));
        Set<String> levels = new HashSet<>(Collections.singletonList(getString(R.string.filter_none)));
        Set<String> categories = new HashSet<>(Collections.singletonList(getString(R.string.filter_none)));
        Set<String> primaryMuscles = new HashSet<>(Collections.singletonList(getString(R.string.filter_none)));

        for (Exercise exercise : allExercises) {
            if (exercise.getBodyPart() != null && !exercise.getBodyPart().isEmpty()) {
                bodyParts.add(exercise.getBodyPart());
            }
            if (exercise.getEquipment() != null && !exercise.getEquipment().isEmpty()) {
                equipments.add(exercise.getEquipment());
            }
            if (exercise.getLevel() != null && !exercise.getLevel().isEmpty()) {
                levels.add(exercise.getLevel());
            }
            if (exercise.getCategory() != null && !exercise.getCategory().isEmpty()) {
                categories.add(exercise.getCategory());
            }
            if (exercise.getPrimaryMuscles() != null) {
                primaryMuscles.addAll(exercise.getPrimaryMuscles());
            }
        }

        setupSpinner(bodyPartSpinner, new ArrayList<>(bodyParts));
        setupSpinner(equipmentSpinner, new ArrayList<>(equipments));
        setupSpinner(levelSpinner, new ArrayList<>(levels));
        setupSpinner(categorySpinner, new ArrayList<>(categories));
        setupSpinner(primaryMuscleSpinner, new ArrayList<>(primaryMuscles));
    }

    private void setupSpinner(Spinner spinner, List<String> items) {
        // Sort items after "All" if it's present
        String allString = getString(R.string.filter_none);
        if (items.contains(allString)) {
            items.remove(allString);
            Collections.sort(items);
            items.add(0, allString);
        } else {
            Collections.sort(items);
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
    }

    private void setupListeners() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterExercises();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        AdapterView.OnItemSelectedListener spinnerListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filterExercises();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        bodyPartSpinner.setOnItemSelectedListener(spinnerListener);
        equipmentSpinner.setOnItemSelectedListener(spinnerListener);
        levelSpinner.setOnItemSelectedListener(spinnerListener);
        categorySpinner.setOnItemSelectedListener(spinnerListener);
        primaryMuscleSpinner.setOnItemSelectedListener(spinnerListener);

        filterOptionsHeader.setOnClickListener(v -> toggleAccordion(filterContentLayout, filterExpandIconTextView));

        saveWorkoutFab.setOnClickListener(v -> {
            if (isSelectionMode) {
                returnSelectedExercises(); // New action for selection mode
            } else {
                showSaveWorkoutDialog(); // Existing action for browsing mode
            }
        });
    }

    private void filterExercises() {
        if (allExercises == null) {
            return;
        }

        String searchText = searchEditText.getText().toString().toLowerCase(Locale.getDefault());
        String selectedBodyPart = bodyPartSpinner.getSelectedItem().toString();
        String selectedEquipment = equipmentSpinner.getSelectedItem().toString();
        String selectedLevel = levelSpinner.getSelectedItem().toString();
        String selectedCategory = categorySpinner.getSelectedItem().toString();
        String selectedPrimaryMuscle = primaryMuscleSpinner.getSelectedItem().toString();

        final String filterNone = getString(R.string.filter_none);

        filteredExercises = allExercises.stream()
                .filter(exercise -> {
                    // Search by name
                    boolean matchesSearch = TextUtils.isEmpty(searchText) ||
                            exercise.getName().toLowerCase(Locale.getDefault()).contains(searchText);

                    // Filter by body part
                    boolean matchesBodyPart = selectedBodyPart.equals(filterNone) ||
                            (exercise.getBodyPart() != null && exercise.getBodyPart().equals(selectedBodyPart));

                    // Filter by equipment
                    boolean matchesEquipment = selectedEquipment.equals(filterNone) ||
                            (exercise.getEquipment() != null && exercise.getEquipment().equals(selectedEquipment));

                    // Filter by level
                    boolean matchesLevel = selectedLevel.equals(filterNone) ||
                            (exercise.getLevel() != null && exercise.getLevel().equals(selectedLevel));

                    // Filter by category
                    boolean matchesCategory = selectedCategory.equals(filterNone) ||
                            (exercise.getCategory() != null && exercise.getCategory().equals(selectedCategory));

                    // Filter by primary muscle (can have multiple primary muscles)
                    boolean matchesPrimaryMuscle = selectedPrimaryMuscle.equals(filterNone) ||
                            (exercise.getPrimaryMuscles() != null && exercise.getPrimaryMuscles().contains(selectedPrimaryMuscle));

                    return matchesSearch && matchesBodyPart && matchesEquipment &&
                            matchesLevel && matchesCategory && matchesPrimaryMuscle;
                })
                .collect(Collectors.toList());

        adapter.setExerciseList(filteredExercises);
        updateNoResultsMessage();
    }

    private void updateNoResultsMessage() {
        if (filteredExercises == null || filteredExercises.isEmpty()) {
            noResultsTextView.setVisibility(View.VISIBLE);
            exercisesRecyclerView.setVisibility(View.GONE);
        } else {
            noResultsTextView.setVisibility(View.GONE);
            exercisesRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onExerciseClick(Exercise exercise) {
        if (!isSelectionMode) {
            Intent intent = new Intent(this, ExerciseDetailActivity.class);
            intent.putExtra("exercise", exercise); // Pass the entire Exercise object
            startActivity(intent);
        }
    }

    @Override
    public void onSelectionChanged(int selectedCount) {
        if (selectedCount > 0) {
            if (isSelectionMode) {
                saveWorkoutFab.setText(getString(R.string.selected_exercises_done, selectedCount));
                // We're using a Font Awesome icon for the FAB in regular mode,
                // but for selection mode, "Done (X)" is more descriptive.
                // If you want an icon here, you'd need to change its text and possibly drawable.
            } else {
                saveWorkoutFab.setText(getString(R.string.fa_save_disk) + " " + selectedCount);
            }
            saveWorkoutFab.setVisibility(View.VISIBLE);
        } else {
            saveWorkoutFab.setVisibility(View.GONE);
        }
    }

    private void toggleAccordion(View contentLayout, TextView expandIcon) {
        if (contentLayout.getVisibility() == View.VISIBLE) {
            contentLayout.setVisibility(View.GONE);
            rotateIcon(expandIcon, 90, 0); // Rotate up (from down to right)
            expandIcon.setText(R.string.fa_chevron_right); // Change icon to right arrow
        } else {
            contentLayout.setVisibility(View.VISIBLE);
            rotateIcon(expandIcon, 0, 90); // Rotate down (from right to down)
            expandIcon.setText(R.string.fa_chevron_down); // Change icon to down arrow
        }
    }

    // NEW: Icon rotation logic
    private void rotateIcon(TextView icon, float fromDegrees, float toDegrees) {
        RotateAnimation rotate = new RotateAnimation(fromDegrees, toDegrees,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(300);
        rotate.setFillAfter(true);
        icon.startAnimation(rotate);
    }

    private void showSaveWorkoutDialog() {
        Set<Exercise> selected = adapter.getSelectedExercises();
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.no_exercises_selected, Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_save_workout, null);
        builder.setView(dialogView);

        final EditText workoutTitleEditText = dialogView.findViewById(R.id.dialogWorkoutTitleEditText);
        final EditText workoutDescriptionEditText = dialogView.findViewById(R.id.dialogWorkoutDescriptionEditText);
        final TextView selectedExercisesTextView = dialogView.findViewById(R.id.dialogSelectedExercisesTextView);
        Button saveButton = dialogView.findViewById(R.id.dialogSaveButton);
        Button cancelButton = dialogView.findViewById(R.id.dialogCancelButton);

        // Display selected exercises
        List<Exercise> sortedSelected = new ArrayList<>(selected);
        Collections.sort(sortedSelected, Comparator.comparing(Exercise::getName)); // Sort alphabetically
        StringBuilder exercisesText = new StringBuilder();
        for (Exercise ex : sortedSelected) {
            exercisesText.append("• ").append(ex.getName()).append("\n");
        }
        selectedExercisesTextView.setText(exercisesText.toString());

        final AlertDialog dialog = builder.create();

        saveButton.setOnClickListener(v -> {
            String workoutName = workoutTitleEditText.getText().toString().trim();
            String workoutDescription = workoutDescriptionEditText.getText().toString().trim();

            if (workoutName.isEmpty()) {
                workoutTitleEditText.setError(getString(R.string.toast_enter_workout_title));
                workoutTitleEditText.requestFocus();
                return;
            }

            if (workoutDescription.isEmpty()) {
                workoutDescription = "No description provided."; // Or any other suitable placeholder
            }

            // Convert Set<Exercise> to List<Exercise> for WorkoutPlan
            List<Exercise> exercisesToSave = new ArrayList<>(selected);

            // Construct WorkoutPlan object
            // Note: The WorkoutPlan constructor expects 'explanation' for saveWorkoutPlan.
            // For a user-created workout, explanation might be null or derived from description.
            // Let's use the workoutDescription for both description and explanation in this context.
            WorkoutPlan newWorkoutPlan = new WorkoutPlan(
                    workoutName,
                    workoutDescription,
                    exercisesToSave,
                    workoutDescription // Using description as explanation for simplicity
            );

            // Call API to save workout
            saveNewWorkoutToApi(workoutName, newWorkoutPlan, dialog);
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void saveNewWorkoutToApi(String workoutName, WorkoutPlan workoutPlan, AlertDialog dialog) {
        String currentUserJwt = accessToken;
        String currentUserId = username;

        if (currentUserJwt == null || currentUserJwt.isEmpty() || currentUserId == null || currentUserId.isEmpty()) {
            Toast.makeText(this, "Authentication error. Please log in.", Toast.LENGTH_LONG).show();
            dialog.dismiss();
            return;
        }

        Toast.makeText(this, getString(R.string.workout_save_in_progress), Toast.LENGTH_SHORT).show();

        WorkoutApiClient.saveWorkoutPlan(
                this,
                currentUserId,
                currentUserJwt,
                workoutName,
                workoutPlan,
                workoutPlan.getExplanation(), // Use explanation from workoutPlan
                new WorkoutApiClient.SaveWorkoutCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(ExerciseSearchActivity.this,
                                    getString(R.string.toast_workout_save_success, workoutName),
                                    Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            adapter.clearSelections(); // Clear selections after successful save

                            // NEW: Send result back to WorkoutWizardActivity and finish this activity
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra(getString(R.string.result_workout_saved_refresh_key), true);
                            setResult(RESULT_OK, resultIntent);
                            finish();
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(() -> {
                            Toast.makeText(ExerciseSearchActivity.this,
                                    getString(R.string.toast_workout_save_failure, errorMessage),
                                    Toast.LENGTH_LONG).show();
                            // Keep dialog open for user to correct or retry, or dismiss based on UX preference
                        });
                    }
                }
        );
    }
    private void returnSelectedExercises() {
        Set<Exercise> selected = adapter.getSelectedExercises();
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.no_exercises_selected_for_add, Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED); // Return canceled if nothing selected
        } else {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("selectedExercises", new ArrayList<>(selected)); // Pass list of selected exercises
            setResult(RESULT_OK, resultIntent);
        }
        finish(); // Close ExerciseSearchActivity
    }
}