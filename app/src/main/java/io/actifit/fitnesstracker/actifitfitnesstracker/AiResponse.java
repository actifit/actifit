package io.actifit.fitnesstracker.actifitfitnesstracker;

public class AiResponse {
    private WorkoutPlan workoutPlan;
    private String explanation;

    private String rawText;//i added this

    public AiResponse(WorkoutPlan workoutPlan, String explanation) {
        this.workoutPlan = workoutPlan;
        this.explanation = explanation;
    }
    //getters and setters

    public AiResponse(String rawText) {
        this.rawText = rawText;
    }//I added this
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

    public String getRawText() {
        return rawText;
    }//i added this

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }//i added this
}
