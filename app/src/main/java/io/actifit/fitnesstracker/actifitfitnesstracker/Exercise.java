package io.actifit.fitnesstracker.actifitfitnesstracker;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;


public class Exercise implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private String sets;
    private String reps;
    private String duration;
    private String[] images;
    private List<String> days;
    private String bodyPart;
    private String equipment;
    private String id;
    private String target;
    private ArrayList<String> primaryMuscles;
    private ArrayList<String> secondaryMuscles;
    private String[] instructions;
    private static final String IMAGE_BASE_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/";

    public Exercise(String name, String sets, String reps, String duration, String[] images, List<String> days, String bodyPart, String equipment, String id, String target, ArrayList<String> primaryMuscles, ArrayList<String> secondaryMuscles, String[] instructions) {
        this.name = name;
        this.sets = sets;
        this.reps = reps;
        this.duration = duration;
        this.images = images;
        this.days = days;
        this.bodyPart = bodyPart;
        this.equipment = equipment;
        this.id = id;
        this.target = target;
        this.primaryMuscles = primaryMuscles;
        this.secondaryMuscles = secondaryMuscles;
        this.instructions = instructions;
    }
    public Exercise(String name, String sets, String reps, String duration, String[] images, List<String> days) {
        this.name = name;
        this.sets = sets;
        this.reps = reps;
        this.duration = duration;
        this.images = images;
        this.days = days;
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public List<String> getDays() {
        return days;
    }

    public void setDays(List<String> days) {
        this.days = days;
    }
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

    public String getStartPositionImageUrl() {
        if(images == null || images.length < 1 ) return null;
        return IMAGE_BASE_URL + images[0];

    }
    public String getEndPositionImageUrl() {
        if(images == null || images.length < 2) return null;
        return IMAGE_BASE_URL + images[1];

    }
}