package io.actifit.fitnesstracker.actifitfitnesstracker;

/**
 * One row in the Friends screen. `type` decides how the row renders and what action it offers.
 */
public class FriendModel {
    public enum Type {
        FRIEND,     // an established friend        -> Unfriend
        RECEIVED,   // they sent ME a request       -> Accept
        SENT,       // I sent THEM a request         -> Cancel
        HEADER      // a section title row (no user, no action)
    }

    public final String username;   // for HEADER rows this holds the section title
    public final Type type;

    public FriendModel(String username, Type type) {
        this.username = username;
        this.type = type;
    }

    public static FriendModel header(String title) {
        return new FriendModel(title, Type.HEADER);
    }
}
