package io.actifit.fitnesstracker.actifitfitnesstracker;

/**
 * Class handles storage and rendering of relevant charities
 */
public class Charity {

    private String charityName;
    private String displayName;

    public Charity(String charityName, String displayName) {
        this.charityName = charityName;
        this.displayName = displayName;
    }

    public String getCharityName() {
        return charityName;
    }

    public void setCharityName(String charityName) {
        this.charityName = charityName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String toString()
    {
        return getDisplayName();
    }
}
