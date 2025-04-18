package io.actifit.fitnesstracker.actifitfitnesstracker;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;

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
    private ArrayList<String> primaryMuscles;
    @SerializedName("secondaryMuscles")
    private ArrayList<String> secondaryMuscles;
    @SerializedName("instructions")
    private String[] instructions;
    @SerializedName("sets")
    private String sets;
    @SerializedName("reps")
    private String reps;
    @SerializedName("duration")
    private String duration;
    @SerializedName("images")
    private String[] images;
    @SerializedName("day")
    private String day;
    private String image;


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

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }
    public ArrayList<String> getPrimaryMuscles() {
        return primaryMuscles;
    }

    public void setPrimaryMuscles(ArrayList<String> primaryMuscles) {
        this.primaryMuscles = primaryMuscles;
    }

    public ArrayList<String> getSecondaryMuscles() {
        return secondaryMuscles;
    }

    public void setSecondaryMuscles(ArrayList<String> secondaryMuscles) {
        this.secondaryMuscles = secondaryMuscles;
    }


    public String[] getInstructions() {
        return instructions;
    }

    public void setInstructions(String[] instructions) {
        this.instructions = instructions;
    }

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

    public String[] getImages() {
        return images;
    }

    public void setImages(String[] images) {
        this.images = images;
    }
    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public  void setFirstImage(String baseUrl){
        if(this.images != null && this.images.length > 0){
            this.image =  baseUrl + this.images[0];
        }
    }
}