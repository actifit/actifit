package io.actifit.fitnesstracker.actifitfitnesstracker;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;
import java.util.Date;

public class WorkoutPlan implements Serializable {

    @SerializedName("_id")
    private String id;

    private String workoutName;
    private Date timestamp;

    private List<Exercise> exercises;
    private String description;
    private String explanation;

    public WorkoutPlan(String workoutName, String description, List<Exercise> exercises, String explanation) {
        this.workoutName = workoutName;
        this.description = description;
        this.exercises = exercises;
        this.explanation = explanation;
        // id and timestamp will be set when fetching FROM the API
    }

    // Constructor including fields received from the API
    public WorkoutPlan(String id, String workoutName, String description, Date timestamp, String explanation, List<Exercise> exercises) {
        this.id = id;
        this.workoutName = workoutName;
        this.description = description;
        this.timestamp = timestamp;
        this.explanation = explanation;
        this.exercises = exercises;
    }

    public WorkoutPlan(List<Exercise> exercises, String description) {
        this.exercises = exercises;
        this.description = description;
    }

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkoutName() { return workoutName; }
    public void setWorkoutName(String workoutName) { this.workoutName = workoutName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public List<Exercise> getExercises() { return exercises; }
    public void setExercises(List<Exercise> exercises) { this.exercises = exercises; }

    // Optional: Add toString() for debugging
    @Override
    public String toString() {
        return "WorkoutPlan{" +
                "id='" + id + '\'' +
                ", workoutName='" + workoutName + '\'' +
                ", description='" + description + '\'' +
                ", timestamp=" + timestamp +
                ", explanation='" + explanation + '\'' +
                ", exercises=" + (exercises != null ? exercises.size() : 0) + " exercises" +
                '}';
    }
}
