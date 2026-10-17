package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Arena home: a segmented toggle (Open | Mine | Results) over one list of challenge
 * cards. Open challenges are enriched from their detail call (participant count and the
 * viewer's membership); Mine and Results load the first time their tab is opened.
 */
public class ArenaActivity extends BaseActivity {

    private static final int TAB_OPEN = 0;
    private static final int TAB_MINE = 1;
    private static final int TAB_RESULTS = 2;
    // The settled list has no server-side paging yet, so cap what we render.
    private static final int MAX_RESULTS = 30;

    private ListView listView;
    private TextView tabOpen, tabMine, tabResults, emptyState;
    private View progress;

    private final ArrayList<ArenaChallenge> running = new ArrayList<>();
    private final ArrayList<ArenaChallenge> minePast = new ArrayList<>();
    private final ArrayList<ArenaChallenge> results = new ArrayList<>();
    private final ArrayList<ArenaChallenge> current = new ArrayList<>();
    private ArenaCardAdapter adapter;

    private int currentTab = TAB_OPEN;
    private String me;
    private RequestQueue arenaQueue;

    // Bumped on every reload; a response from a superseded load is ignored.
    private int generation = 0;
    private boolean runningLoading = false, runningFailed = false, runningLoaded = false;
    private boolean mineLoading = false, mineLoaded = false, mineFailed = false;
    private boolean resultsLoading = false, resultsLoaded = false, resultsFailed = false;
    private boolean refreshQueued = false;

    // "Ending soon" banner: ticks every second while the screen is visible.
    private View featured;
    private TextView featuredTitle, featuredSub, featuredCountdown;
    private ArenaChallenge featuredChallenge;
    private int tickCount = 0;
    private final android.os.Handler ticker = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            tickCount++;
            if (featuredChallenge != null) {
                if (ArenaLogic.hasEnded(featuredChallenge.endMs, System.currentTimeMillis())) {
                    // It just closed: the list (and the next one to feature) has changed.
                    featuredChallenge = null;
                    loadRunning();
                } else {
                    featuredCountdown.setText(ArenaCardAdapter.countdownText(ArenaActivity.this, featuredChallenge.endMs));
                }
            }
            // The cards show "Ends in 5d 3h": refresh them once a minute.
            if (tickCount % 60 == 0) adapter.notifyDataSetChanged();
            ticker.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arena);

        listView = findViewById(R.id.arena_list);
        tabOpen = findViewById(R.id.arena_tab_open);
        tabMine = findViewById(R.id.arena_tab_mine);
        tabResults = findViewById(R.id.arena_tab_results);
        emptyState = findViewById(R.id.arena_empty_state);
        progress = findViewById(R.id.arena_progress);

        me = getSharedPreferences("actifitSets", MODE_PRIVATE).getString("actifitUser", "");
        arenaQueue = Volley.newRequestQueue(this);

        adapter = new ArenaCardAdapter(this, current, me);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= current.size()) return;
            Intent i = new Intent(this, ArenaChallengeActivity.class);
            i.putExtra(ArenaChallengeActivity.EXTRA_CHALLENGE_ID, current.get(position).id);
            i.putExtra(ArenaChallengeActivity.EXTRA_FROM_ARENA, true);
            startActivity(i);
        });

        featured = findViewById(R.id.arena_featured);
        featuredTitle = findViewById(R.id.arena_featured_title);
        featuredSub = findViewById(R.id.arena_featured_sub);
        featuredCountdown = findViewById(R.id.arena_featured_countdown);
        featured.setOnClickListener(v -> {
            if (featuredChallenge == null) return;
            Intent i = new Intent(this, ArenaChallengeActivity.class);
            i.putExtra(ArenaChallengeActivity.EXTRA_CHALLENGE_ID, featuredChallenge.id);
            i.putExtra(ArenaChallengeActivity.EXTRA_FROM_ARENA, true);
            startActivity(i);
        });

        tabOpen.setOnClickListener(v -> selectTab(TAB_OPEN));
        tabMine.setOnClickListener(v -> selectTab(TAB_MINE));
        tabResults.setOnClickListener(v -> selectTab(TAB_RESULTS));
        emptyState.setOnClickListener(v -> {
            if (currentTab == TAB_RESULTS && resultsFailed) loadResults();
            else if (runningFailed) loadRunning();
            else if (currentTab == TAB_MINE && mineFailed) loadMinePast();
        });

        selectTab(TAB_OPEN);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-read the user: they may have logged in from a challenge screen.
        me = getSharedPreferences("actifitSets", MODE_PRIVATE).getString("actifitUser", "");
        adapter.setUser(me);
        // Also the first load. Coming back from a challenge, membership may have changed.
        loadRunning();
        ticker.removeCallbacks(tick);
        ticker.post(tick);
    }

    @Override
    protected void onPause() {
        // No point ticking a countdown nobody can see.
        ticker.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ticker.removeCallbacks(tick);
        if (arenaQueue != null) {
            arenaQueue.cancelAll(req -> true);
            arenaQueue.stop();
        }
        super.onDestroy();
    }

    private void selectTab(int tab) {
        currentTab = tab;
        int white = getResources().getColor(R.color.colorWhite);
        int idleText = getResources().getColor(R.color.md_theme_textSecondary);
        styleTab(tabOpen, tab == TAB_OPEN, white, idleText);
        styleTab(tabMine, tab == TAB_MINE, white, idleText);
        styleTab(tabResults, tab == TAB_RESULTS, white, idleText);
        if (tab == TAB_MINE && !mineLoaded && !mineLoading) loadMinePast();
        if (tab == TAB_RESULTS && !resultsLoaded && !resultsLoading) loadResults();
        render();
    }

    private void styleTab(TextView tab, boolean selected, int selText, int idleText) {
        tab.setBackgroundResource(selected ? R.drawable.segmented_selected : 0);
        tab.setTextColor(selected ? selText : idleText);
        tab.setSelected(selected);
    }

    /** Open + active challenges (the API filters one state per call), then their details. */
    private void loadRunning() {
        final int gen = ++generation;
        runningLoading = true;
        runningFailed = false;
        // Mine's past list depends on who is logged in and on what just settled; refetch lazily.
        mineLoaded = false;
        render();

        final List<ArenaChallenge> merged = new ArrayList<>();
        final int[] pending = {2};
        // Both halves must succeed: swapping in only the open or only the active list
        // would make the other half's challenges silently vanish.
        final boolean[] allOk = {true};
        ArenaApi.ListCallback onPart = list -> {
            if (gen != generation) return;
            if (list != null) merged.addAll(list);
            else allOk[0] = false;
            if (--pending[0] > 0) return;
            runningLoading = false;
            // A failed refresh keeps the list already on screen; it is only an error state
            // when there is nothing to show.
            runningFailed = !allOk[0] && !runningLoaded;
            if (allOk[0]) {
                runningLoaded = true;
                // Soonest to close first — that is the one worth acting on now.
                Collections.sort(merged, (a, b) -> Long.compare(a.endMs, b.endMs));
                // Keep what the last detail calls told us until the fresh ones land, so
                // "Joined" and the counts don't blink off on every return to this screen.
                for (ArenaChallenge fresh : merged) {
                    for (ArenaChallenge old : running) {
                        if (old.id.equals(fresh.id)) {
                            fresh.participantCount = old.participantCount;
                            fresh.serverJoined = old.serverJoined;
                            break;
                        }
                    }
                }
                running.clear();
                running.addAll(merged);
                for (ArenaChallenge c : merged) enrich(gen, c.id);
            }
            if (currentTab == TAB_MINE && !mineLoading) loadMinePast();
            render();
        };
        ArenaApi.listChallenges(this, arenaQueue, "open", null, onPart);
        ArenaApi.listChallenges(this, arenaQueue, "active", null, onPart);
    }

    /** Fills in participant count + my membership for one running card. */
    private void enrich(final int gen, final String id) {
        ArenaApi.getChallenge(this, arenaQueue, id, me, (detail, notFound) -> {
            if (gen != generation || detail == null) return;
            for (int i = 0; i < running.size(); i++) {
                if (id.equals(running.get(i).id)) {
                    running.set(i, detail);
                    break;
                }
            }
            scheduleRender();
        });
    }

    /** Settled challenges the user took part in. */
    private void loadMinePast() {
        if (me == null || me.isEmpty()) {
            mineLoaded = true;
            render();
            return;
        }
        final int gen = generation;
        mineLoading = true;
        mineFailed = false;
        render();
        ArenaApi.listChallenges(this, arenaQueue, "settled", me, list -> {
            mineLoading = false;
            if (gen != generation) {
                // Superseded by a reload that skipped Mine because this call was in flight.
                if (currentTab == TAB_MINE) loadMinePast();
                else render();
                return;
            }
            if (list == null) {
                // Keep whatever was already loaded; a network error is not "nothing joined".
                mineFailed = true;
            } else {
                mineLoaded = true;
                minePast.clear();
                sortNewestFirst(list);
                minePast.addAll(list.subList(0, Math.min(list.size(), MAX_RESULTS)));
            }
            render();
        });
    }

    private void loadResults() {
        resultsLoading = true;
        resultsFailed = false;
        render();
        ArenaApi.listChallenges(this, arenaQueue, "settled", null, list -> {
            resultsLoading = false;
            resultsFailed = list == null;
            resultsLoaded = list != null;
            results.clear();
            if (list != null) {
                sortNewestFirst(list);
                results.addAll(list.subList(0, Math.min(list.size(), MAX_RESULTS)));
            }
            render();
        });
    }

    private static void sortNewestFirst(List<ArenaChallenge> list) {
        Collections.sort(list, (a, b) -> Long.compare(b.endMs, a.endMs));
    }

    /** Several detail calls land close together; redraw once for the batch. */
    private void scheduleRender() {
        if (refreshQueued) return;
        refreshQueued = true;
        listView.post(() -> {
            refreshQueued = false;
            if (!isFinishing() && !isDestroyed()) render();
        });
    }

    private void render() {
        current.clear();
        boolean loading;
        boolean failed = false;
        int emptyText;
        if (currentTab == TAB_OPEN) {
            current.addAll(running);
            loading = runningLoading && !runningLoaded;
            failed = runningFailed;
            emptyText = R.string.arena_empty_open;
        } else if (currentTab == TAB_MINE) {
            for (ArenaChallenge c : running) {
                if (ArenaCardAdapter.isJoined(this, me, c)) current.add(c);
            }
            current.addAll(minePast);
            // Membership comes from the per-card detail calls, so until those land an
            // empty Mine list means "not known yet", not "nothing joined".
            boolean membershipPending = false;
            for (ArenaChallenge c : running) membershipPending |= c.participantCount < 0;
            loading = (runningLoading && !runningLoaded) || mineLoading
                    || (membershipPending && me != null && !me.isEmpty());
            failed = runningFailed || mineFailed;
            emptyText = (me == null || me.isEmpty())
                    ? R.string.arena_empty_mine_login : R.string.arena_empty_mine;
        } else {
            current.addAll(results);
            loading = resultsLoading;
            failed = resultsFailed;
            emptyText = R.string.arena_empty_results;
        }
        adapter.notifyDataSetChanged();
        renderFeatured();

        boolean showSpinner = loading && current.isEmpty();
        progress.setVisibility(showSpinner ? View.VISIBLE : View.GONE);
        if (current.isEmpty() && !showSpinner) {
            emptyState.setText(failed ? R.string.arena_load_error : emptyText);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            emptyState.setVisibility(View.GONE);
        }
    }

    /**
     * The open challenge closing next — preferring one the user hasn't joined, since the
     * point is to get them in — with a live countdown. Open tab only.
     */
    private void renderFeatured() {
        ArenaChallenge pick = null, joinedPick = null;
        long now = System.currentTimeMillis();
        if (currentTab == TAB_OPEN) {
            for (ArenaChallenge c : running) {   // already sorted soonest-closing first
                if (!ArenaLogic.isJoinable(c.state) || c.endMs < 0 || ArenaLogic.hasEnded(c.endMs, now)) continue;
                if (!ArenaCardAdapter.isJoined(this, me, c)) { pick = c; break; }
                if (joinedPick == null) joinedPick = c;
            }
        }
        boolean joined = pick == null && joinedPick != null;
        if (pick == null) pick = joinedPick;
        featuredChallenge = pick;
        if (pick == null) {
            featured.setVisibility(View.GONE);
            return;
        }
        featuredTitle.setText(pick.title);
        featuredSub.setText(joined ? R.string.arena_featured_joined : R.string.arena_featured_join);
        featuredCountdown.setText(ArenaCardAdapter.countdownText(this, pick.endMs));
        featured.setContentDescription(pick.title + ", " + getString(R.string.arena_ends_in_clock,
                ArenaCardAdapter.countdownText(this, pick.endMs)));
        featured.setVisibility(View.VISIBLE);
    }
}
