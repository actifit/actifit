package io.actifit.fitnesstracker.actifitfitnesstracker;

import java.time.Instant;

/**
 * Pure Arena rules — no Android or JSON imports, so it is unit-testable on the JVM and
 * doubles as the reference for the iOS port. Anything user-facing is returned as a kind
 * or a number; the activities map those onto string resources.
 */
public final class ArenaLogic {

    private ArenaLogic() {}

    public static final String ART_BASE_URL = "https://actifit.io/img/arena/";

    /** How long an un-indexed join/leave stays optimistic before we stop trusting it. */
    public static final long PENDING_TTL_MS = 5 * 60 * 1000L;

    // Fallback copy buckets for challenges that carry no tagline/how-it-works of their own.
    public static final int KIND_LEAGUE = 0;
    public static final int KIND_DAILY = 1;
    public static final int KIND_EVENT = 2;
    public static final int KIND_DEFAULT = 3;

    public static final int UNIT_DAYS = 0;
    public static final int UNIT_WEEKS = 1;
    public static final int UNIT_MONTHS = 2;

    public enum Membership { NOT_JOINED, JOINED, PENDING_JOIN, PENDING_LEAVE }

    public static final String OP_JOIN = "join";
    public static final String OP_LEAVE = "leave";

    /** Only open/active challenges accept a join or leave; any unknown state is closed. */
    public static boolean isJoinable(String state) {
        return "open".equals(state) || "active".equals(state);
    }

    public static int catalogKind(String type) {
        if ("league_fixture".equals(type)) return KIND_LEAGUE;
        if ("daily_focus".equals(type)) return KIND_DAILY;
        if ("liveops".equals(type)) return KIND_EVENT;
        return KIND_DEFAULT;
    }

    /** The banner key: the challenge's own art, else a per-type default. */
    public static String artKey(String art, String type) {
        if (art != null && art.matches("[a-z0-9-]{1,40}")) return art;
        switch (catalogKind(type)) {
            case KIND_DAILY: return "daily-focus";
            case KIND_EVENT: return "monthly-event";
            default: return "step-league";
        }
    }

    public static String artUrl(String art, String type) {
        return ART_BASE_URL + artKey(art, type) + ".webp";
    }

    /**
     * Epoch millis for an API timestamp, or -1 if it can't be read. The API sends both
     * "2026-09-23T13:06:26.509Z" and a zone-less "2026-09-23T13:06:30" (UTC).
     */
    public static long parseIsoMs(String iso) {
        if (iso == null || iso.isEmpty()) return -1;
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (Exception e) {
            try {
                return Instant.parse(iso + "Z").toEpochMilli();
            } catch (Exception e2) {
                return -1;
            }
        }
    }

    /**
     * Window length as {unit, count} (e.g. {UNIT_WEEKS, 2}), or null for a missing or
     * inverted window. Mirrors the web's formatDuration thresholds.
     */
    public static int[] duration(long startMs, long endMs) {
        if (startMs < 0 || endMs < 0 || endMs <= startMs) return null;
        long days = Math.round((endMs - startMs) / 86400000.0);
        if (days <= 1) return new int[]{UNIT_DAYS, 1};
        if (days < 14) return new int[]{UNIT_DAYS, (int) days};
        if (days < 28) return new int[]{UNIT_WEEKS, (int) Math.round(days / 7.0)};
        return new int[]{UNIT_MONTHS, (int) Math.max(1, Math.round(days / 30.0))};
    }

    /** Time left as {days, hours, minutes}; all zero once the window has closed. */
    public static int[] remaining(long endMs, long nowMs) {
        long left = endMs - nowMs;
        if (endMs < 0 || left <= 0) return new int[]{0, 0, 0};
        long minutes = left / 60000L;
        return new int[]{(int) (minutes / 1440), (int) ((minutes % 1440) / 60), (int) (minutes % 60)};
    }

    public static boolean hasEnded(long endMs, long nowMs) {
        return endMs >= 0 && nowMs >= endMs;
    }

    /** A pending op the server now agrees with is confirmed and can be dropped. */
    public static boolean pendingConfirmed(boolean serverJoined, String pendingOp) {
        return (OP_JOIN.equals(pendingOp) && serverJoined)
                || (OP_LEAVE.equals(pendingOp) && !serverJoined);
    }

    /** A pending op the server still hasn't applied after the TTL — treat it as not landed. */
    public static boolean pendingExpired(boolean serverJoined, String pendingOp, long pendingAtMs, long nowMs) {
        if (pendingOp == null || pendingConfirmed(serverJoined, pendingOp)) return false;
        return nowMs - pendingAtMs >= PENDING_TTL_MS;
    }

    /**
     * What to show for the viewer. The backend indexes a broadcast a few seconds later, so
     * until then a fresh local join/leave overrides the server's (stale) answer.
     */
    public static Membership membership(boolean serverJoined, String pendingOp, long pendingAtMs, long nowMs) {
        boolean live = pendingOp != null
                && !pendingConfirmed(serverJoined, pendingOp)
                && !pendingExpired(serverJoined, pendingOp, pendingAtMs, nowMs);
        if (live && OP_JOIN.equals(pendingOp)) return Membership.PENDING_JOIN;
        if (live && OP_LEAVE.equals(pendingOp)) return Membership.PENDING_LEAVE;
        return serverJoined ? Membership.JOINED : Membership.NOT_JOINED;
    }

    /** Machine enum → readable label ("league_fixture" → "league fixture"). */
    public static String humanize(String s) {
        return s == null ? "" : s.replace('_', ' ');
    }
}
