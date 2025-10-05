package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils; // Import TextUtils
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors; // Requires API level 24 for stream API

public class ExerciseSearchActivity extends BaseActivity
        implements ExerciseSearchAdapter.OnExerciseClickListener {

    private static final String TAG = "ExerciseSearchActivity";

    // UI elements
    private EditText searchEditText;
    private Spinner bodyPartSpinner, equipmentSpinner, levelSpinner, categorySpinner, primaryMuscleSpinner;
    private RecyclerView exercisesRecyclerView;
    private ProgressBar loadingProgressBar;
    private TextView noResultsTextView;

    // Data
    private List<Exercise> allExercises;
    private List<Exercise> filteredExercises;
    private ExerciseSearchAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise_search);

        initUI();
        loadExercises();
        setupListeners();
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

        exercisesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExerciseSearchAdapter(this, new ArrayList<>(), this);
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
        Toast.makeText(this, getString(R.string.exercise_clicked_toast, exercise.getName()), Toast.LENGTH_SHORT).show();
        // Here you would typically start a new activity to show full exercise details
        // Intent intent = new Intent(this, ExerciseDetailActivity.class);
        // intent.putExtra("exerciseId", exercise.getId());
        // startActivity(intent);
    }
}