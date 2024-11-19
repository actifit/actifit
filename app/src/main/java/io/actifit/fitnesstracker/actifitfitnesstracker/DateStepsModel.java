package io.actifit.fitnesstracker.actifitfitnesstracker;

public class DateStepsModel {

    public String mDate;
    public int mStepCount;
    public String mtrackingDevice;
    public Boolean hasRelevantPost = false;
    public String relevantPostLink;
    public Boolean relevantPostChecked = false;

    public DateStepsModel(){

    }
    public DateStepsModel(String mDate, int mStepCount, String mtrackingDevice) {
        this.mDate = mDate;
        this.mStepCount = mStepCount;
        this.mtrackingDevice = mtrackingDevice;
    }

}