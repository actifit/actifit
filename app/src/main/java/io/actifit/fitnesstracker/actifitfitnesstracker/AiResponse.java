package io.actifit.fitnesstracker.actifitfitnesstracker;

public class AiResponse {
    private WorkoutPlan workoutPlan;
    private String explanation;

    public AiResponse(WorkoutPlan workoutPlan, String explanation) {
        this.workoutPlan = workoutPlan;
        this.explanation = explanation;
    }
    //getters and setters

    public WorkoutPlan getWorkoutPlan() {
        return workoutPlan;
    }

    public void setWorkoutPlan(WorkoutPlan workoutPlan) {
        this.workoutPlan = workoutPlan;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
}