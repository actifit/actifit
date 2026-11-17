package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class BadgeLogicTest {

    private static Set<String> held(String... ids) {
        return BadgeLogic.dedupe(Arrays.asList(ids));
    }

    private static BadgeLogic.Eligibility elig(Boolean iso, Boolean lucky, Boolean charity, long posts) {
        return new BadgeLogic.Eligibility(iso, lucky, charity, posts);
    }

    // ── levels: must match the backend's `count > threshold[N-1]` ────────────────

    @Test
    public void levelBoundariesMatchTheServerRule() {
        assertEquals(0, BadgeLogic.levelFor(0));
        assertEquals(0, BadgeLogic.levelFor(9));
        assertEquals(1, BadgeLogic.levelFor(10));
        assertEquals(1, BadgeLogic.levelFor(29));
        assertEquals(2, BadgeLogic.levelFor(30));
        assertEquals(6, BadgeLogic.levelFor(180));
        assertEquals(9, BadgeLogic.levelFor(1079));
        assertEquals(10, BadgeLogic.levelFor(1080));
        assertEquals(10, BadgeLogic.levelFor(1081));
        assertEquals(10, BadgeLogic.levelFor(100000));
    }

    @Test
    public void levelForMirrorsTheWebCalcScore() {
        int[][] rules = {{9, 0}, {29, 1}, {59, 2}, {89, 3}, {119, 4}, {179, 5}, {359, 6},
                {539, 7}, {719, 8}, {1079, 9}, {1080, 10}};
        for (int count = 0; count <= 1200; count++) {
            int result = 0;
            for (int[] rule : rules) {
                result = rule[1];
                if (count <= rule[0]) break;
            }
            assertEquals("count " + count, result, BadgeLogic.levelFor(count));
        }
    }

    @Test
    public void postsNeededAndProgress() {
        assertEquals(10, BadgeLogic.postsForLevel(1));
        assertEquals(1080, BadgeLogic.postsForLevel(10));
        assertEquals(-1, BadgeLogic.postsForLevel(0));
        assertEquals(-1, BadgeLogic.postsForLevel(11));
        assertEquals(10, BadgeLogic.postsToNextLevel(0));
        assertEquals(1, BadgeLogic.postsToNextLevel(9));
        assertEquals(20, BadgeLogic.postsToNextLevel(10));
        assertEquals(1, BadgeLogic.postsToNextLevel(1079));
        assertEquals(0, BadgeLogic.postsToNextLevel(1080));
        assertEquals(-1, BadgeLogic.postsToNextLevel(-1));
    }

    @Test
    public void activityIdsRoundTrip() {
        assertEquals("rewarded_activity_lev_3", BadgeLogic.activityId(3));
        assertEquals(3, BadgeLogic.activityLevelOf("rewarded_activity_lev_3"));
        assertEquals(0, BadgeLogic.activityLevelOf("rewarded_activity_lev_11"));
        assertEquals(0, BadgeLogic.activityLevelOf("rewarded_activity_lev_x"));
        assertEquals(0, BadgeLogic.activityLevelOf("iso"));
        assertEquals(0, BadgeLogic.activityLevelOf(null));
        assertTrue(BadgeLogic.isKnown("charity_badge"));
        assertFalse(BadgeLogic.isKnown("something_else"));
    }

    // ── states ───────────────────────────────────────────────────────────────────

    @Test
    public void isoStates() {
        BadgeLogic.Eligibility yes = elig(true, null, null, -1);
        BadgeLogic.Eligibility no = elig(false, null, null, -1);
        assertEquals(BadgeLogic.State.CLAIMED, BadgeLogic.stateOf("iso", held("iso"), no, true));
        assertEquals(BadgeLogic.State.CLAIMABLE, BadgeLogic.stateOf("iso", held(), yes, true));
        assertEquals(BadgeLogic.State.MISSED, BadgeLogic.stateOf("iso", held(), no, true));
        // A failed fetch (unknown) is never reported as missed.
        assertEquals(BadgeLogic.State.LOCKED, BadgeLogic.stateOf("iso", held(), elig(null, null, null, -1), true));
        assertEquals(BadgeLogic.State.LOCKED, BadgeLogic.stateOf("iso", held(), null, true));
    }

    @Test
    public void luckyStates() {
        assertEquals(BadgeLogic.State.CLAIMABLE,
                BadgeLogic.stateOf("doubledup_badge", held(), elig(null, true, null, -1), true));
        assertEquals(BadgeLogic.State.MISSED,
                BadgeLogic.stateOf("doubledup_badge", held(), elig(null, false, null, -1), true));
        assertEquals(BadgeLogic.State.CLAIMED,
                BadgeLogic.stateOf("doubledup_badge", held("doubledup_badge"), elig(null, true, null, -1), true));
    }

    @Test
    public void charityHasNoMissedState() {
        assertEquals(BadgeLogic.State.CLAIMABLE,
                BadgeLogic.stateOf("charity_badge", held(), elig(null, null, true, -1), true));
        assertEquals(BadgeLogic.State.LOCKED,
                BadgeLogic.stateOf("charity_badge", held(), elig(null, null, false, -1), true));
    }

    @Test
    public void activityStates() {
        BadgeLogic.Eligibility e = elig(null, null, null, 30);   // level 2
        assertEquals(BadgeLogic.State.CLAIMABLE, BadgeLogic.stateOf("rewarded_activity_lev_1", held(), e, true));
        assertEquals(BadgeLogic.State.CLAIMABLE, BadgeLogic.stateOf("rewarded_activity_lev_2", held(), e, true));
        assertEquals(BadgeLogic.State.LOCKED, BadgeLogic.stateOf("rewarded_activity_lev_3", held(), e, true));
        assertEquals(BadgeLogic.State.LOCKED,
                BadgeLogic.stateOf("rewarded_activity_lev_1", held(), elig(null, null, null, -1), true));
        assertEquals(BadgeLogic.State.LOCKED,
                BadgeLogic.stateOf("rewarded_activity_lev_1", held(), elig(null, null, null, 9), true));
    }

    @Test
    public void otherUsersNeverSeeClaimOrMissed() {
        BadgeLogic.Eligibility all = elig(false, false, true, 5000);
        assertEquals(BadgeLogic.State.LOCKED, BadgeLogic.stateOf("iso", held(), all, false));
        assertEquals(BadgeLogic.State.LOCKED, BadgeLogic.stateOf("doubledup_badge", held(), all, false));
        assertEquals(BadgeLogic.State.LOCKED, BadgeLogic.stateOf("charity_badge", held(), all, false));
        assertEquals(BadgeLogic.State.CLAIMED, BadgeLogic.stateOf("charity_badge", held("charity_badge"), all, false));
    }

    // ── visible window, claimable list ───────────────────────────────────────────

    @Test
    public void ownProfileShowsUpToHighestClaimedPlusOne() {
        assertEquals(Collections.singletonList(1), BadgeLogic.visibleActivityLevels(held(), true));
        assertEquals(Arrays.asList(1, 2, 3, 4),
                BadgeLogic.visibleActivityLevels(held("rewarded_activity_lev_3"), true));
        assertEquals(10, BadgeLogic.visibleActivityLevels(held("rewarded_activity_lev_10"), true).size());
        assertEquals(10, BadgeLogic.visibleActivityLevels(held("rewarded_activity_lev_9"), true).size());
    }

    @Test
    public void otherProfileShowsOnlyHeldLevels() {
        assertEquals(Arrays.asList(1, 3),
                BadgeLogic.visibleActivityLevels(held("rewarded_activity_lev_3", "iso", "rewarded_activity_lev_1"), false));
        assertTrue(BadgeLogic.visibleActivityLevels(held(), false).isEmpty());
    }

    @Test
    public void claimableListsEverythingClaimableInOrder() {
        BadgeLogic.Eligibility e = elig(true, true, true, 500);   // level 7
        List<String> c = BadgeLogic.claimable(held("rewarded_activity_lev_1"), e);
        assertEquals(Arrays.asList("iso", "rewarded_activity_lev_2", "doubledup_badge", "charity_badge"), c);
        assertTrue(BadgeLogic.claimable(held("iso", "doubledup_badge", "charity_badge",
                "rewarded_activity_lev_1"), elig(true, true, true, 15)).isEmpty());
        assertTrue(BadgeLogic.claimable(held(), new BadgeLogic.Eligibility()).isEmpty());
        assertTrue(BadgeLogic.claimable(null, null).isEmpty());
    }

    // ── dedupe, art, claim JSON, reply classification ────────────────────────────

    @Test
    public void dedupeCollapsesDuplicateClaimDocs() {
        Set<String> s = BadgeLogic.dedupe(Arrays.asList("iso", "iso", " ", null, "charity_badge", "iso"));
        assertEquals(2, s.size());
        assertEquals(Arrays.asList("iso", "charity_badge"), new java.util.ArrayList<>(s));
        assertEquals(1, BadgeLogic.maxClaimedActivityLevel(held("rewarded_activity_lev_1", "rewarded_activity_lev_1")));
    }

    @Test
    public void artUrlsMatchTheWebFiles() {
        assertEquals("https://actifit.io/img/badges/actifit_iso_badge.png", BadgeLogic.artUrl("iso"));
        assertEquals("https://actifit.io/img/badges/actifit_doubled_up_badge.png", BadgeLogic.artUrl("doubledup_badge"));
        assertEquals("https://actifit.io/img/badges/actifit_charity_badge.png", BadgeLogic.artUrl("charity_badge"));
        assertEquals("https://actifit.io/img/badges/actifit_rew_act_lev_1_badge.png",
                BadgeLogic.artUrl("rewarded_activity_lev_1"));
        assertEquals("https://actifit.io/img/badges/actifit_rew_act_lev_10_badge.png",
                BadgeLogic.artUrl("rewarded_activity_lev_10"));
        assertNull(BadgeLogic.artUrl("rewarded_activity_lev_11"));
        assertNull(BadgeLogic.artUrl("unknown"));
    }

    @Test
    public void claimedJsonIsValidAndEscaped() throws Exception {
        assertEquals("{\"claimed_badge\":\"iso\"}", BadgeLogic.claimedJson("iso"));
        String weird = "a\"b\\c\nd";
        // quotes, backslashes and newlines are escaped; control chars hived rejects are dropped
        JSONObject o = new JSONObject(BadgeLogic.claimedJson(weird + "\u0001"));
        assertEquals(weird, o.getString("claimed_badge"));
        assertEquals(1, o.length());
    }

    @Test
    public void claimRepliesAreClassified() {
        assertEquals(BadgeLogic.ClaimReply.SUCCESS, BadgeLogic.classifyClaimReply("success", false, false));
        assertEquals(BadgeLogic.ClaimReply.NOT_ELIGIBLE, BadgeLogic.classifyClaimReply("error", false, false));
        // checkHdrs: {success:false, message:'Token is not valid'} / {error:'User mismatch'}
        assertEquals(BadgeLogic.ClaimReply.AUTH, BadgeLogic.classifyClaimReply(null, true, false));
        assertEquals(BadgeLogic.ClaimReply.AUTH, BadgeLogic.classifyClaimReply(null, false, true));
        assertEquals(BadgeLogic.ClaimReply.UNKNOWN, BadgeLogic.classifyClaimReply(null, false, false));
    }

    @Test
    public void parseClaimedKeepsOnlyThisUsersDocsDeduplicated() throws Exception {
        JSONArray arr = new JSONArray("[{\"user\":\"alice\",\"badge\":\"iso\",\"date_claimed\":\"2020-01-01\"},"
                + "{\"user\":\"alice\",\"badge\":\"iso\"},"
                + "{\"user\":\"bob\",\"badge\":\"charity_badge\"},"
                + "{\"user\":\"Alice\",\"badge\":\"rewarded_activity_lev_2\"}]");
        Set<String> s = BadgesApi.parseClaimed(arr, "alice");
        assertEquals(Arrays.asList("iso", "rewarded_activity_lev_2"), new java.util.ArrayList<>(s));
    }
}
