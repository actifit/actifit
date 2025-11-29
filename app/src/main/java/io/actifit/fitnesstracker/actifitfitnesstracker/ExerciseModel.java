package io.actifit.fitnesstracker.actifitfitnesstracker;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ExerciseModel {
    @SerializedName("bodyPart")
    private String bodyPart;
    @SerializedName("equipment")
    private String equipment;
    @SerializedName("id")
    private String id;
    @SerializedName("name")
    private String name;
    @SerializedName("target")
    private String target;
    @SerializedName("primaryMuscles")
    private List<String> primaryMuscles;
    @SerializedName("secondaryMuscles")
    private List<String> secondaryMuscles;
    @SerializedName("instructions")
    private List<String> instructions;
    @SerializedName("sets")
    private String sets;
    @SerializedName("reps")
    private String reps;
    @SerializedName("duration")
    private String duration;
    @SerializedName("images")
    private List<String> images;
    @SerializedName("day")
    private String day;
    private String image;

    private String force;
    private String level;
    private String mechanic;
    private String category;


    public String getBodyPart() {
        return bodyPart;
    }

    public void setBodyPart(String bodyPart) {
        this.bodyPart = bodyPart;
    }

    public String getEquipment() {
        return equipment;
    }

    public void setEquipment(String equipment) {
        this.equipment = equipment;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getForce() { return force; }
    public void setForce(String force) { this.force = force; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getMechanic() { return mechanic; }
    public void setMechanic(String mechanic) { this.mechanic = mechanic; }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }
    public List<String> getPrimaryMuscles() {
        return primaryMuscles;
    }

    public void setPrimaryMuscles(List<String> primaryMuscles) {
        this.primaryMuscles = primaryMuscles;
    }

    public List<String> getSecondaryMuscles() {
        return secondaryMuscles;
    }

    public void setSecondaryMuscles(List<String> secondaryMuscles) {
        this.secondaryMuscles = secondaryMuscles;
    }


    public List<String> getInstructions() {
        return instructions;
    }

    public void setInstructions(List<String> instructions) {
        this.instructions = instructions;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSets() {
        return sets;
    }

    public void setSets(String sets) {
        this.sets = sets;
    }

    public String getReps() {
        return reps;
    }

    public void setReps(String reps) {
        this.reps = reps;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }
    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public  void setFirstImage(String baseUrl){
        if(this.images != null && this.images.size() > 0){
            this.image =  baseUrl + this.images.get(0);
        }
    }
}