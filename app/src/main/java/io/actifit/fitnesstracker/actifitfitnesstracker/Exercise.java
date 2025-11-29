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
    private List<String> images;
    private List<String> days;
    private String bodyPart;
    private String equipment;
    private String id;
    private String target;
    private List<String> primaryMuscles; // Corrected: List<String>
    private List<String> secondaryMuscles; // Corrected: List<String>
    private List<String> instructions;
    private String level;    // NEW
    private String category; // NEW
    private String force;    // NEW
    private String mechanic; // NEW
    private static final String IMAGE_BASE_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/";

    public Exercise(String name, String sets, String reps, String duration, List<String> images,
                    List<String> days, String bodyPart, String equipment, String id, String target,
                    List<String> primaryMuscles, List<String> secondaryMuscles, List<String> instructions) {
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
    public Exercise(String name, String sets, String reps, String duration, List<String> images, List<String> days) {
        this.name = name;
        this.sets = sets;
        this.reps = reps;
        this.duration = duration;
        this.images = images;
        this.days = days;
    }

    public Exercise(String name, String sets, String reps, String duration, List<String> images,
                    List<String> days, String bodyPart, String equipment, String id, String target,
                    List<String> primaryMuscles, List<String> secondaryMuscles, List<String> instructions,
                    String level, String category, String force, String mechanic) {
        this(name, sets, reps, duration, images, days, bodyPart, equipment, id, target, primaryMuscles, secondaryMuscles, instructions);
        this.level = level;
        this.category = category;
        this.force = force;
        this.mechanic = mechanic;
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

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
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

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getForce() { return force; }
    public void setForce(String force) { this.force = force; }

    public String getMechanic() { return mechanic; }
    public void setMechanic(String mechanic) { this.mechanic = mechanic; }

    public String getStartPositionImageUrl() {
        if(images == null || images.size() < 1 ) return null;
        return IMAGE_BASE_URL + images.get(0);

    }
    public String getEndPositionImageUrl() {
        if(images == null || images.size() < 2) return null;
        return IMAGE_BASE_URL + images.get(1);

    }

    @Override
    public String toString() {
        return "Exercise{" +
                "name='" + name + '\'' +
                ", bodyPart='" + bodyPart + '\'' +
                ", equipment='" + equipment + '\'' +
                ", level='" + level + '\'' +
                ", category='" + category + '\'' +
                '}';
    }
}