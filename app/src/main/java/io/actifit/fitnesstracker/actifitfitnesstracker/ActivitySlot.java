package io.actifit.fitnesstracker.actifitfitnesstracker;

public class ActivitySlot {
    String slot;
    int activityCount;

    public ActivitySlot(String slot, int activityCount) {
        this.slot = slot;
        this.activityCount = activityCount;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (obj instanceof ActivitySlot) {
            ActivitySlot cust = (ActivitySlot) obj;
            if ((cust.slot == null && slot == null) ||
                    (cust.slot.equals(slot) )) {
                return true;
            }
        }
        return false;
    }


}
