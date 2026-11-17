package io.actifit.fitnesstracker.actifitfitnesstracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure rules for the Actifit profile badges (ISO, rewarded-activity levels, lucky doubled-up,
 * charity) — no Android or JSON imports, so it is unit-testable on the JVM and doubles as the
 * reference for the iOS port. Mirrors the web profile's Badges tab and the backend's
 * /claimBadge eligibility checks; the activities map the results onto views and strings.
 */
public final class BadgeLogic {

    private BadgeLogic() {}

    public static final String ISO = "iso";
    public static final String DOUBLED_UP = "doubledup_badge";
    public static final String CHARITY = "charity_badge";
    public static final String ACTIVITY_PREFIX = "rewarded_activity_lev_";

    public static final String ART_BASE_URL = "https://actifit.io/img/badges/";

    /**
     * Rewarded-activity level N is earned once the rewarded-report count is strictly greater
     * than ACTIVITY_THRESHOLDS[N - 1] — the backend's rule ({@code count > rule[0]} for the rule
     * whose level is N - 1) and the web's calcScore. Level 10 is the top: there is no art and
     * no web tile beyond it.
     */
    static final int[] ACTIVITY_THRESHOLDS = {9, 29, 59, 89, 119, 179, 359, 539, 719, 1079};
    public static final int MAX_ACTIVITY_LEVEL = ACTIVITY_THRESHOLDS.length;

    public enum State {
        /** The user holds it. */
        CLAIMED,
        /** Own profile, eligible, not yet claimed. */
        CLAIMABLE,
        /** Own profile, the one-off chance is known to have passed (ISO) or not come (lucky). */
        MISSED,
        /** Not held, and either not eligible or not the viewer's to claim: shown greyed. */
        LOCKED
    }

    /** What the server reported about the user's eligibility. Null / -1 = unknown (fetch failed). */
    public static final class Eligibility {
        public Boolean isoParticipant;
        public Boolean luckyWinner;
        public Boolean charityDonor;
        public long rewardedPosts = -1;

        public Eligibility() {}

        public Eligibility(Boolean iso, Boolean lucky, Boolean charity, long rewardedPosts) {
            this.isoParticipant = iso;
            this.luckyWinner = lucky;
            this.charityDonor = charity;
            this.rewardedPosts = rewardedPosts;
        }
    }

    // ── catalog ──────────────────────────────────────────────────────────────────

    public static String activityId(int level) {
        return ACTIVITY_PREFIX + level;
    }

    /** The level of a rewarded-activity badge id, or 0 when it is not one (or out of range). */
    public static int activityLevelOf(String id) {
        if (id == null || !id.startsWith(ACTIVITY_PREFIX)) return 0;
        try {
            int lvl = Integer.parseInt(id.substring(ACTIVITY_PREFIX.length()));
            return lvl >= 1 && lvl <= MAX_ACTIVITY_LEVEL ? lvl : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isKnown(String id) {
        return ISO.equals(id) || DOUBLED_UP.equals(id) || CHARITY.equals(id) || activityLevelOf(id) > 0;
    }

    /** Badge art on actifit.io (the same files the web profile shows); null for an unknown id. */
    public static String artUrl(String id) {
        if (ISO.equals(id)) return ART_BASE_URL + "actifit_iso_badge.png";
        if (DOUBLED_UP.equals(id)) return ART_BASE_URL + "actifit_doubled_up_badge.png";
        if (CHARITY.equals(id)) return ART_BASE_URL + "actifit_charity_badge.png";
        int lvl = activityLevelOf(id);
        if (lvl > 0) return ART_BASE_URL + "actifit_rew_act_lev_" + lvl + "_badge.png";
        return null;
    }

    // ── activity levels ──────────────────────────────────────────────────────────

    /** Highest rewarded-activity level earned with this many rewarded reports (0..10). */
    public static int levelFor(long rewardedPosts) {
        int level = 0;
        for (int threshold : ACTIVITY_THRESHOLDS) {
            if (rewardedPosts > threshold) level++;
            else break;
        }
        return level;
    }

    /** Rewarded reports needed to reach {@code level} (1..10), i.e. threshold + 1. */
    public static long postsForLevel(int level) {
        if (level < 1 || level > MAX_ACTIVITY_LEVEL) return -1;
        return ACTIVITY_THRESHOLDS[level - 1] + 1L;
    }

    /** Reports still missing for the next level; 0 when the top level is reached, -1 if unknown. */
    public static long postsToNextLevel(long rewardedPosts) {
        if (rewardedPosts < 0) return -1;
        int next = levelFor(rewardedPosts) + 1;
        if (next > MAX_ACTIVITY_LEVEL) return 0;
        return postsForLevel(next) - rewardedPosts;
    }

    public static int maxClaimedActivityLevel(Collection<String> claimed) {
        int max = 0;
        if (claimed == null) return 0;
        for (String id : claimed) max = Math.max(max, activityLevelOf(id));
        return max;
    }

    /**
     * The activity tiles to draw. Own profile (as on the web): every level up to the highest
     * claimed one plus one, so levels are claimed in order. Another user's profile: only the
     * levels they hold.
     */
    public static List<Integer> visibleActivityLevels(Collection<String> claimed, boolean isSelf) {
        List<Integer> out = new ArrayList<>();
        if (isSelf) {
            int top = Math.min(maxClaimedActivityLevel(claimed) + 1, MAX_ACTIVITY_LEVEL);
            for (int i = 1; i <= top; i++) out.add(i);
        } else if (claimed != null) {
            for (int i = 1; i <= MAX_ACTIVITY_LEVEL; i++) {
                if (claimed.contains(activityId(i))) out.add(i);
            }
        }
        return out;
    }

    // ── states ───────────────────────────────────────────────────────────────────

    /**
     * The server keeps one document per claim and does not guard against duplicates, so
     * collapse them (and drop blanks) while keeping first-seen order.
     */
    public static Set<String> dedupe(Collection<String> badgeIds) {
        Set<String> out = new LinkedHashSet<>();
        if (badgeIds == null) return out;
        for (String id : badgeIds) {
            if (id != null && !id.trim().isEmpty()) out.add(id.trim());
        }
        return out;
    }

    /** Whether the server would accept a claim for this badge, given what it reported. */
    public static boolean isEligible(String id, Eligibility e) {
        if (e == null) return false;
        if (ISO.equals(id)) return Boolean.TRUE.equals(e.isoParticipant);
        if (DOUBLED_UP.equals(id)) return Boolean.TRUE.equals(e.luckyWinner);
        if (CHARITY.equals(id)) return Boolean.TRUE.equals(e.charityDonor);
        int lvl = activityLevelOf(id);
        return lvl > 0 && e.rewardedPosts >= 0 && levelFor(e.rewardedPosts) >= lvl;
    }

    public static State stateOf(String id, Collection<String> claimed, Eligibility e, boolean isSelf) {
        if (claimed != null && claimed.contains(id)) return State.CLAIMED;
        if (!isSelf) return State.LOCKED;
        if (isEligible(id, e)) return State.CLAIMABLE;
        // Only a confirmed "no" (not a failed fetch) counts as missed.
        if (e != null && ISO.equals(id) && Boolean.FALSE.equals(e.isoParticipant)) return State.MISSED;
        if (e != null && DOUBLED_UP.equals(id) && Boolean.FALSE.equals(e.luckyWinner)) return State.MISSED;
        return State.LOCKED;
    }

    /**
     * Every badge the user can claim now, in display order. Activity levels count only when
     * visible, matching what the profile offers a button for.
     */
    public static List<String> claimable(Collection<String> claimed, Eligibility e) {
        List<String> out = new ArrayList<>();
        Collection<String> held = claimed == null ? Collections.<String>emptySet() : claimed;
        if (stateOf(ISO, held, e, true) == State.CLAIMABLE) out.add(ISO);
        for (int lvl : visibleActivityLevels(held, true)) {
            String id = activityId(lvl);
            if (stateOf(id, held, e, true) == State.CLAIMABLE) out.add(id);
        }
        if (stateOf(DOUBLED_UP, held, e, true) == State.CLAIMABLE) out.add(DOUBLED_UP);
        if (stateOf(CHARITY, held, e, true) == State.CLAIMABLE) out.add(CHARITY);
        return out;
    }

    // ── claim ────────────────────────────────────────────────────────────────────

    /** The on-chain record of a claim: custom_json id "actifit", as the web writes it. */
    public static String claimedJson(String id) {
        return "{\"claimed_badge\":" + HiveTx.jsonString(id) + "}";
    }

    public enum ClaimReply {
        SUCCESS,
        /** status:error — the server re-checked and the requirements are not met. */
        NOT_ELIGIBLE,
        /** The session token was refused (expired, missing, or for another user). */
        AUTH,
        /** Anything else: treat like a network failure and offer a retry. */
        UNKNOWN
    }

    /**
     * Classifies a /claimBadge reply. {@code status} is the "status" field (or null);
     * {@code successFalse} is true when the reply carries {"success": false} (checkHdrs' token
     * errors); {@code hasError} is true when it carries an "error" field (key not found, user
     * mismatch, bad token payload).
     */
    public static ClaimReply classifyClaimReply(String status, boolean successFalse, boolean hasError) {
        if ("success".equals(status)) return ClaimReply.SUCCESS;
        if ("error".equals(status)) return ClaimReply.NOT_ELIGIBLE;
        if (successFalse || hasError) return ClaimReply.AUTH;
        return ClaimReply.UNKNOWN;
    }
}
