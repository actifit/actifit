package io.actifit.fitnesstracker.actifitfitnesstracker;

/**
 * One row in the Friends screen. `type` decides how the row renders and what action it offers.
 */
public class FriendModel {
    public enum Type {
        FRIEND,     // an established friend        -> Unfriend
        RECEIVED,   // they sent ME a request       -> Accept
        SENT,       // I sent THEM a request         -> Cancel
        SUGGESTED,  // a recommended account         -> Add
        HEADER      // a section title row (no user, no action)
    }

    public final String username;   // for HEADER rows this holds the section title
    public final Type type;

    // Enrichment for SUGGESTED rows (null until the async detail fetch completes).
    public Integer activityCount;   // rewarded (verified) Actifit reports
    public String afit;             // AFIT token balance, pre-formatted
    public Integer mutualCount;     // friends shared with the logged-in user
    public boolean enriched = false;

    public FriendModel(String username, Type type) {
        this.username = username;
        this.type = type;
    }

    public static FriendModel header(String title) {
        return new FriendModel(title, Type.HEADER);
    }
}
