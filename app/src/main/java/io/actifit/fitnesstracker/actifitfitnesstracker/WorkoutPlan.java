package io.actifit.fitnesstracker.actifitfitnesstracker;

import java.util.List;

public class WorkoutPlan {
    private List<Exercise> exercises;
    private String description;

    public WorkoutPlan(List<Exercise> exercises, String description) {
        this.exercises = exercises;
        this.description = description;
    }
    //getters and setters...
    public List<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(List<Exercise> exercises) {
        this.exercises = exercises;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
