package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Arena card on the home dashboard. For a user who is in a challenge it shows the one
 * closing soonest with their rank and score; otherwise it invites them to the open ones.
 *
 * A successful load is cached for a couple of minutes so returning to the dashboard doesn't
 * refetch every time. A load in which any call failed is shown but never cached, so a
 * network error can't pin "not in a challenge" on a participant.
 */
public final class ArenaHomeCard {

    private ArenaHomeCard() {}

    private static final long CACHE_MS = 2 * 60 * 1000L;
    // After a join/leave the backend needs a few blocks to index it; refetch often until then.
    private static final long SETTLING_CACHE_MS = 10 * 1000L;
    private static final long SETTLING_WINDOW_MS = 60 * 1000L;
    // `entity=` also returns challenges the user has since left, so candidates are
    // confirmed against the detail call. Bounded, to cap the requests per load.
    private static final int MAX_CANDIDATES = 8;

    private static class State {
        String user;
        long loadedAtMs;
        boolean complete = true;  // false once any call in the load failed
        ArenaChallenge joined;    // null when the user is in nothing
        int rank;                 // 0 = not on the board (or the board couldn't be read)
        double score;
        int openCount;
    }

    private static State cache;
    private static long settlingUntilMs;
    // Bumped per load and on invalidate; a chain started earlier must not publish its result.
    private static int generation;
    // One app-lifetime queue: the dashboard resumes often, and a queue per resume leaks threads.
    private static RequestQueue queue;

    /** Call when the user's membership changed (a join or leave was sent). */
    public static void invalidate() {
        cache = null;
        generation++;
        settlingUntilMs = System.currentTimeMillis() + SETTLING_WINDOW_MS;
    }

    /** True when the last successful load found {@code user} in a running challenge. */
    public static boolean inChallenge(String user) {
        return cache != null && cache.joined != null && cache.user.equals(user);
    }

    public static void refresh(Activity act, String me) {
        View card = act.findViewById(R.id.arena_home_card);
        if (card == null) return;
        if (queue == null) queue = Volley.newRequestQueue(act.getApplicationContext());
        String user = me == null ? "" : me;

        long now = System.currentTimeMillis();
        long ttl = now < settlingUntilMs ? SETTLING_CACHE_MS : CACHE_MS;
        if (cache != null && cache.user.equals(user) && now - cache.loadedAtMs < ttl) {
            bind(act, card, cache);
            return;
        }
        // Never leave another account's challenge on screen while this one loads.
        if (cache != null && !cache.user.equals(user)) {
            cache = null;
            card.setVisibility(View.GONE);
        }

        final int gen = ++generation;
        State s = new State();
        s.user = user;
        if (user.isEmpty()) {
            loadOpenCount(act, card, s, gen);
            return;
        }
        // The API filters one state per call; a started challenge may be "active".
        final List<ArenaChallenge> mine = new ArrayList<>();
        final int[] pending = {2};
        ArenaApi.ListCallback onPart = list -> {
            if (list != null) mine.addAll(list);
            else s.complete = false;
            if (--pending[0] > 0) return;
            // Soonest to close first; an unknown end sorts last.
            Collections.sort(mine, (a, b) -> Long.compare(
                    a.endMs < 0 ? Long.MAX_VALUE : a.endMs, b.endMs < 0 ? Long.MAX_VALUE : b.endMs));
            confirmJoined(act, card, s, gen,
                    new ArrayList<>(mine.subList(0, Math.min(mine.size(), MAX_CANDIDATES))));
        };
        ArenaApi.listChallenges(act, queue, "open", user, onPart);
        ArenaApi.listChallenges(act, queue, "active", user, onPart);
    }

    /** Walks the candidates until one is confirmed as still joined. */
    private static void confirmJoined(Activity act, View card, State s, int gen,
                                      List<ArenaChallenge> candidates) {
        if (candidates.isEmpty()) {
            loadOpenCount(act, card, s, gen);
            return;
        }
        ArenaChallenge next = candidates.remove(0);
        ArenaApi.getChallenge(act, queue, next.id, s.user, (detail, notFound) -> {
            if (detail == null) s.complete = false;
            // isJoined also honours a join/leave that has been sent but not indexed yet.
            if (detail == null || !ArenaCardAdapter.isJoined(act, s.user, detail)) {
                confirmJoined(act, card, s, gen, candidates);
                return;
            }
            s.joined = detail;
            ArenaApi.getStandings(act, queue, detail.id, (rows, computedAtMs) -> {
                if (rows == null) {
                    s.complete = false;
                } else {
                    for (ArenaChallenge.StandingRow r : rows) {
                        if (r.entity.equals(s.user)) {
                            s.rank = r.rank;
                            s.score = r.score;
                            break;
                        }
                    }
                }
                done(act, card, s, gen);
            });
        });
    }

    private static void loadOpenCount(Activity act, View card, State s, int gen) {
        ArenaApi.listChallenges(act, queue, "open", null, open -> {
            if (open == null) {
                // Couldn't reach the Arena: leave the card as it was rather than guess.
                return;
            }
            s.openCount = open.size();
            done(act, card, s, gen);
        });
    }

    private static void done(Activity act, View card, State s, int gen) {
        if (gen != generation) return;
        if (s.complete) {
            s.loadedAtMs = System.currentTimeMillis();
            cache = s;
        } else if (s.joined == null) {
            // A call failed and we found nothing: we don't know the user is in nothing,
            // so don't replace whatever the card shows with an invitation.
            return;
        }
        if (!act.isFinishing() && !act.isDestroyed()) bind(act, card, s);
    }

    private static void bind(Activity act, View card, State s) {
        TextView title = card.findViewById(R.id.arena_home_title);
        TextView status = card.findViewById(R.id.arena_home_status);
        TextView time = card.findViewById(R.id.arena_home_time);

        if (s.joined != null) {
            final String id = s.joined.id;
            title.setText(s.joined.title);
            if (s.rank > 0) {
                status.setText(act.getString(R.string.arena_home_rank, s.rank,
                        NumberFormat.getInstance().format(s.score)));
            } else {
                // Not on the board — or the board couldn't be read, in which case don't
                // claim the user has no score.
                status.setText(s.complete ? act.getString(R.string.arena_home_no_score)
                        : ArenaCardAdapter.taglineText(act, s.joined));
            }
            time.setText(ArenaCardAdapter.timeText(act, s.joined));
            time.setVisibility(View.VISIBLE);
            card.setOnClickListener(v -> {
                Intent i = new Intent(act, ArenaChallengeActivity.class);
                i.putExtra(ArenaChallengeActivity.EXTRA_CHALLENGE_ID, id);
                act.startActivity(i);
            });
        } else if (s.openCount > 0) {
            title.setText(R.string.arena_home_join_title);
            status.setText(act.getResources().getQuantityString(
                    R.plurals.arena_home_open, s.openCount, s.openCount));
            time.setVisibility(View.GONE);
            card.setOnClickListener(v -> act.startActivity(new Intent(act, ArenaActivity.class)));
        } else {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);
    }
}
