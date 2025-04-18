package io.actifit.fitnesstracker.actifitfitnesstracker;

public class WorkoutRequest {

    private String fitnessGoal;
    private String experienceLevel;
    private String weeklyTime;
    private String preferredWorkout;
    private String equipment;
    private String limitations;
    private String otherLimitations;
    private String dailyFrequency;

    public WorkoutRequest(String fitnessGoal, String experienceLevel, String weeklyTime, String preferredWorkout, String equipment, String limitations, String otherLimitations,String dailyFrequency) {
        this.fitnessGoal = fitnessGoal;
        this.experienceLevel = experienceLevel;
        this.weeklyTime = weeklyTime;
        this.preferredWorkout = preferredWorkout;
        this.equipment = equipment;
        this.limitations = limitations;
        this.otherLimitations = otherLimitations;
        this.dailyFrequency = dailyFrequency;
    }
    // Getters and setters
    public String getFitnessGoal() {
        return fitnessGoal;
    }

    public void setFitnessGoal(String fitnessGoal) {
        this.fitnessGoal = fitnessGoal;
    }

    public String getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(String experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public String getWeeklyTime() {
        return weeklyTime;
    }

    public void setWeeklyTime(String weeklyTime) {
        this.weeklyTime = weeklyTime;
    }

    public String getPreferredWorkout() {
        return preferredWorkout;
    }

    public void setPreferredWorkout(String preferredWorkout) {
        this.preferredWorkout = preferredWorkout;
    }

    public String getEquipment() {
        return equipment;
    }

    public void setEquipment(String equipment) {
        this.equipment = equipment;
    }

    public String getLimitations() {
        return limitations;
    }

    public void setLimitations(String limitations) {
        this.limitations = limitations;
    }
    public String getOtherLimitations() {
        return otherLimitations;
    }

    public void setOtherLimitations(String otherLimitations) {
        this.otherLimitations = otherLimitations;
    }
    public String getDailyFrequency() {
        return dailyFrequency;
    }

    public void setDailyFrequency(String dailyFrequency) {
        this.dailyFrequency = dailyFrequency;
    }
}