package io.actifit.fitnesstracker.actifitfitnesstracker;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

public class WorkoutPlan implements Serializable {

    @SerializedName("_id")
    private String mongoId;

    private String workoutName;
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
