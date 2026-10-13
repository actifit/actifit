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
 * The result is cached for a couple of minutes so returning to the dashboard doesn't
 * refetch every time; a join or leave calls {@link #invalidate()}.
 */
public final class ArenaHomeCard {

    private ArenaHomeCard() {}

    private static final long CACHE_MS = 2 * 60 * 1000L;
    // `entity=` also returns challenges the user has since left, so candidates are
    // confirmed against the detail call — but only a few, to bound the requests.
    private static final int MAX_CANDIDATES = 3;

    private static class State {
        String user;
        long loadedAtMs;
        ArenaChallenge joined;   // null when the user is in nothing
        int rank;                // 0 = not on the board yet
        double score;
        int openCount;
    }

    private static State cache;
    // One app-lifetime queue: the dashboard resumes often, and a queue per resume leaks threads.
    private static RequestQueue queue;

    public static void invalidate() {
        cache = null;
    }

    /** True when the last dashboard load found the user in a running challenge. */
    public static boolean inChallenge() {
        return cache != null && cache.joined != null;
    }

    public static void refresh(Activity act, String me) {
        View card = act.findViewById(R.id.arena_home_card);
        if (card == null) return;
        if (queue == null) queue = Volley.newRequestQueue(act.getApplicationContext());
        String user = me == null ? "" : me;
        if (cache != null && cache.user.equals(user)
                && System.currentTimeMillis() - cache.loadedAtMs < CACHE_MS) {
            bind(act, card, cache);
            return;
        }
        State s = new State();
        s.user = user;
        if (user.isEmpty()) {
            loadOpenCount(act, queue, card, s);
            return;
        }
        ArenaApi.listChallenges(act, queue, "open", user, mine -> {
            if (mine == null || mine.isEmpty()) {
                loadOpenCount(act, queue, card, s);
                return;
            }
            Collections.sort(mine, (a, b) -> Long.compare(a.endMs, b.endMs));
            confirmJoined(act, queue, card, s,
                    new ArrayList<>(mine.subList(0, Math.min(mine.size(), MAX_CANDIDATES))));
        });
    }

    /** Walks the candidates until one is confirmed as still joined. */
    private static void confirmJoined(Activity act, RequestQueue queue, View card, State s,
                                      List<ArenaChallenge> candidates) {
        if (candidates.isEmpty()) {
            loadOpenCount(act, queue, card, s);
            return;
        }
        ArenaChallenge next = candidates.remove(0);
        ArenaApi.getChallenge(act, queue, next.id, s.user, (detail, notFound) -> {
            if (detail == null || !detail.serverJoined) {
                confirmJoined(act, queue, card, s, candidates);
                return;
            }
            s.joined = detail;
            ArenaApi.getStandings(act, queue, detail.id, (rows, computedAtMs) -> {
                if (rows != null) {
                    for (ArenaChallenge.StandingRow r : rows) {
                        if (r.entity.equals(s.user)) {
                            s.rank = r.rank;
                            s.score = r.score;
                            break;
                        }
                    }
                }
                done(act, card, s);
            });
        });
    }

    private static void loadOpenCount(Activity act, RequestQueue queue, View card, State s) {
        ArenaApi.listChallenges(act, queue, "open", null, open -> {
            if (open == null) {
                // Couldn't reach the Arena: leave the card as it was rather than guess.
                return;
            }
            s.openCount = open.size();
            done(act, card, s);
        });
    }

    private static void done(Activity act, View card, State s) {
        s.loadedAtMs = System.currentTimeMillis();
        cache = s;
        if (!act.isFinishing() && !act.isDestroyed()) bind(act, card, s);
    }

    private static void bind(Activity act, View card, State s) {
        TextView title = card.findViewById(R.id.arena_home_title);
        TextView status = card.findViewById(R.id.arena_home_status);
        TextView time = card.findViewById(R.id.arena_home_time);

        if (s.joined != null) {
            final String id = s.joined.id;
            title.setText(s.joined.title);
            status.setText(s.rank > 0
                    ? act.getString(R.string.arena_home_rank, s.rank, NumberFormat.getInstance().format(s.score))
                    : act.getString(R.string.arena_home_no_score));
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
