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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Charity charity = (Charity) o;
        return charityName.equals(charity.charityName) &&
                displayName.equals(charity.displayName);
    }

}
