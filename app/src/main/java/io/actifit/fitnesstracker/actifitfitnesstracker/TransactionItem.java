package io.actifit.fitnesstracker.actifitfitnesstracker;

import java.util.Date;

// TransactionItem.java
public class TransactionItem {
    public String activityType;
    public double tokenCount; // Store as a number!
    public String user;
    public String recipient;
    public String date; // Keep original string
    public Date parsedDate; // Store parsed Date object
    public String note;
    public String url; // Add URL field

    // Constructor or getters/setters can be added, or keep fields public for simplicity
}