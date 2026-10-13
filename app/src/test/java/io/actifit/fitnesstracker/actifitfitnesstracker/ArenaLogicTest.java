package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArenaLogicTest {

    private static final long DAY = 86400000L;
    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void joinableOnlyWhileOpenOrActive() {
        assertTrue(ArenaLogic.isJoinable("open"));
        assertTrue(ArenaLogic.isJoinable("active"));
        assertFalse(ArenaLogic.isJoinable("settled"));
        assertFalse(ArenaLogic.isJoinable("some_future_state"));
        assertFalse(ArenaLogic.isJoinable(null));
    }

    @Test
    public void artPrefersTheChallengeKeyThenFallsBackByType() {
        assertEquals("weekend-warrior", ArenaLogic.artKey("weekend-warrior", "liveops"));
        assertEquals("daily-focus", ArenaLogic.artKey(null, "daily_focus"));
        assertEquals("monthly-event", ArenaLogic.artKey("", "liveops"));
        assertEquals("step-league", ArenaLogic.artKey(null, "duel"));
        assertEquals("https://actifit.io/img/arena/step-league.webp", ArenaLogic.artUrl(null, null));
    }

    @Test
    public void artKeyThatCouldEscapeTheImageFolderIsIgnored() {
        assertEquals("step-league", ArenaLogic.artKey("../../evil", "league_fixture"));
        assertEquals("step-league", ArenaLogic.artKey("a/b", null));
    }

    @Test
    public void parsesBothTimestampShapesTheApiSends() {
        assertEquals(1790168786509L, ArenaLogic.parseIsoMs("2026-09-23T13:06:26.509Z"));
        assertEquals(1790168790000L, ArenaLogic.parseIsoMs("2026-09-23T13:06:30"));
        assertEquals(-1, ArenaLogic.parseIsoMs("not a date"));
        assertEquals(-1, ArenaLogic.parseIsoMs(null));
    }

    @Test
    public void durationUsesTheWebThresholds() {
        assertArrayEquals(new int[]{ArenaLogic.UNIT_DAYS, 1}, ArenaLogic.duration(0, DAY));
        assertArrayEquals(new int[]{ArenaLogic.UNIT_DAYS, 2}, ArenaLogic.duration(0, 2 * DAY));
        assertArrayEquals(new int[]{ArenaLogic.UNIT_DAYS, 7}, ArenaLogic.duration(0, 7 * DAY));
        assertArrayEquals(new int[]{ArenaLogic.UNIT_WEEKS, 2}, ArenaLogic.duration(0, 14 * DAY));
        assertArrayEquals(new int[]{ArenaLogic.UNIT_MONTHS, 1}, ArenaLogic.duration(0, 30 * DAY));
        assertNull(ArenaLogic.duration(DAY, DAY));
        assertNull(ArenaLogic.duration(-1, DAY));
    }

    @Test
    public void remainingBreaksDownAndClampsAtZero() {
        long end = NOW + DAY + 5 * 3600000L + 12 * 60000L;
        assertArrayEquals(new int[]{1, 5, 12}, ArenaLogic.remaining(end, NOW));
        assertArrayEquals(new int[]{0, 0, 0}, ArenaLogic.remaining(NOW - 1, NOW));
        assertArrayEquals(new int[]{0, 0, 0}, ArenaLogic.remaining(-1, NOW));
        assertTrue(ArenaLogic.hasEnded(NOW, NOW));
        assertFalse(ArenaLogic.hasEnded(-1, NOW));
    }

    @Test
    public void membershipFollowsTheServerWhenNothingIsPending() {
        assertEquals(ArenaLogic.Membership.JOINED, ArenaLogic.membership(true, null, 0, NOW));
        assertEquals(ArenaLogic.Membership.NOT_JOINED, ArenaLogic.membership(false, null, 0, NOW));
    }

    @Test
    public void freshJoinShowsAsPendingUntilTheServerCatchesUp() {
        assertEquals(ArenaLogic.Membership.PENDING_JOIN,
                ArenaLogic.membership(false, ArenaLogic.OP_JOIN, NOW - 10_000, NOW));
        // indexed: the server row now exists
        assertEquals(ArenaLogic.Membership.JOINED,
                ArenaLogic.membership(true, ArenaLogic.OP_JOIN, NOW - 10_000, NOW));
        assertTrue(ArenaLogic.pendingConfirmed(true, ArenaLogic.OP_JOIN));
    }

    @Test
    public void freshLeaveShowsAsPendingUntilTheServerCatchesUp() {
        assertEquals(ArenaLogic.Membership.PENDING_LEAVE,
                ArenaLogic.membership(true, ArenaLogic.OP_LEAVE, NOW - 10_000, NOW));
        assertEquals(ArenaLogic.Membership.NOT_JOINED,
                ArenaLogic.membership(false, ArenaLogic.OP_LEAVE, NOW - 10_000, NOW));
    }

    @Test
    public void unconfirmedJoinStopsBeingTrustedAfterTheTtl() {
        long old = NOW - ArenaLogic.PENDING_TTL_MS;
        assertTrue(ArenaLogic.pendingExpired(false, ArenaLogic.OP_JOIN, old, NOW));
        assertEquals(ArenaLogic.Membership.NOT_JOINED,
                ArenaLogic.membership(false, ArenaLogic.OP_JOIN, old, NOW));
        // a confirmed op is never "expired", however old
        assertFalse(ArenaLogic.pendingExpired(true, ArenaLogic.OP_JOIN, old, NOW));
        assertFalse(ArenaLogic.pendingExpired(false, null, old, NOW));
    }

    @Test
    public void catalogKindCoversKnownTypesAndDefaultsTheRest() {
        assertEquals(ArenaLogic.KIND_LEAGUE, ArenaLogic.catalogKind("league_fixture"));
        assertEquals(ArenaLogic.KIND_DAILY, ArenaLogic.catalogKind("daily_focus"));
        assertEquals(ArenaLogic.KIND_EVENT, ArenaLogic.catalogKind("liveops"));
        assertEquals(ArenaLogic.KIND_DEFAULT, ArenaLogic.catalogKind("brawl"));
        assertEquals(ArenaLogic.KIND_DEFAULT, ArenaLogic.catalogKind(null));
    }
}
