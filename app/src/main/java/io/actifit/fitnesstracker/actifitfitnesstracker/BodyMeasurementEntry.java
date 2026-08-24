package io.actifit.fitnesstracker.actifitfitnesstracker;

import java.util.Date;

/**
 * One activity report's body-metric snapshot, parsed from the
 * {@code /trackedMeasurements/:user} endpoint. Every numeric field is nullable —
 * a report may record only some measurements (e.g. weight but not thighs), and a
 * metric with no value across all reports simply gets no chart series.
 */
public class BodyMeasurementEntry {
    public Date date;

    public Float weight;
    public Float waist;
    public Float chest;
    public Float thighs;
    public Float bodyFat;
    public Float height;

    public String weightUnit;
    public String waistUnit;
    public String chestUnit;
    public String thighsUnit;
    public String heightUnit;
}
