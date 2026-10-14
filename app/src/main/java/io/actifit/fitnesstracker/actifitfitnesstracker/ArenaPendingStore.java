package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers a join/leave the user just broadcast, per user and challenge. The backend only
 * indexes the op a few seconds later, so without this the "Joined" state would vanish the
 * moment the screen is reopened.
 */
public final class ArenaPendingStore {

    private static final String PREFS = "arenaPending";

    private ArenaPendingStore() {}

    /** A stored optimistic op; op is null when nothing is pending. */
    public static class Pending {
        public final String op;
        public final long atMs;

        Pending(String op, long atMs) {
            this.op = op;
            this.atMs = atMs;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String user, String challengeId) {
        return user + "|" + challengeId;
    }

    public static void put(Context ctx, String user, String challengeId, String op) {
        prefs(ctx).edit().putString(key(user, challengeId), op + ":" + System.currentTimeMillis()).apply();
    }

    public static Pending get(Context ctx, String user, String challengeId) {
        String raw = prefs(ctx).getString(key(user, challengeId), null);
        if (raw == null) return new Pending(null, 0);
        int sep = raw.indexOf(':');
        try {
            return new Pending(raw.substring(0, sep), Long.parseLong(raw.substring(sep + 1)));
        } catch (Exception e) {
            return new Pending(null, 0);
        }
    }

    /**
     * The membership to show for {@code user}, given what the server last said. Drops the
     * stored op once the server has caught up with it, or once it has gone unconfirmed
     * past the TTL.
     */
    public static ArenaLogic.Membership resolve(Context ctx, String user, String challengeId, boolean serverJoined) {
        if (user == null || user.isEmpty()) return ArenaLogic.Membership.NOT_JOINED;
        Pending p = get(ctx, user, challengeId);
        long now = System.currentTimeMillis();
        if (p.op != null && (ArenaLogic.pendingConfirmed(serverJoined, p.op)
                || ArenaLogic.pendingExpired(serverJoined, p.op, p.atMs, now))) {
            clear(ctx, user, challengeId);
        }
        return ArenaLogic.membership(serverJoined, p.op, p.atMs, now);
    }

    public static void clear(Context ctx, String user, String challengeId) {
        prefs(ctx).edit().remove(key(user, challengeId)).apply();
    }
}
