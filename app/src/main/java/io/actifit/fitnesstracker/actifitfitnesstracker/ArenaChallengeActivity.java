package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;

import java.text.NumberFormat;
import java.util.List;

/**
 * One Arena challenge: hero, facts, how it works, prizes, standings and Join / Leave.
 * Launch with the string extra {@link #EXTRA_CHALLENGE_ID}.
 *
 * Join/leave is broadcast on-chain and indexed by the backend a few seconds later, so the
 * new state is shown optimistically from {@link ArenaPendingStore} until the server agrees.
 */
public class ArenaChallengeActivity extends BaseActivity {

    public static final String EXTRA_CHALLENGE_ID = "challenge_id";

    // The board is capped on screen; the viewer's own row is appended if it falls below.
    private static final int MAX_STANDING_ROWS = 50;

    private String challengeId;
    private String me;
    private RequestQueue arenaQueue;
    private ArenaChallenge challenge;
    private List<ArenaChallenge.StandingRow> standings;
    private long standingsAtMs = -1;
    private boolean acting = false;
    private int statusRes = 0;
    // Bumped per load(); an older response arriving late must not overwrite a newer one.
    private int loadGeneration = 0;

    private View scroll, progress, actionBar;
    private TextView message, actionStatus;
    private Button actionButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arena_challenge);

        scroll = findViewById(R.id.arena_detail_scroll);
        progress = findViewById(R.id.arena_detail_progress);
        message = findViewById(R.id.arena_detail_message);
        actionBar = findViewById(R.id.arena_action_bar);
        actionStatus = findViewById(R.id.arena_action_status);
        actionButton = findViewById(R.id.arena_action_button);
        findViewById(R.id.arena_back_button).setOnClickListener(v -> finish());
        message.setOnClickListener(v -> load());
        actionButton.setOnClickListener(v -> onActionClicked());

        challengeId = getIntent().getStringExtra(EXTRA_CHALLENGE_ID);
        arenaQueue = Volley.newRequestQueue(this);
        if (challengeId == null || challengeId.isEmpty()) {
            showMessage(R.string.arena_not_found, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-read the user each time: they may have just come back from logging in.
        me = getSharedPreferences("actifitSets", MODE_PRIVATE).getString("actifitUser", "");
        if (challengeId != null && !challengeId.isEmpty()) load();
    }

    @Override
    protected void onDestroy() {
        actionButton.removeCallbacks(reload);
        if (arenaQueue != null) {
            // A join/leave still in flight is NOT cancelled server-side; its optimistic
            // state was stored before sending, so the next visit still reflects it.
            arenaQueue.cancelAll(req -> true);
            arenaQueue.stop();
        }
        super.onDestroy();
    }

    private void load() {
        if (challenge == null) {
            progress.setVisibility(View.VISIBLE);
            message.setVisibility(View.GONE);
        }
        final int gen = ++loadGeneration;
        ArenaApi.getChallenge(this, arenaQueue, challengeId, me, (detail, notFound) -> {
            if (gen != loadGeneration) return;
            progress.setVisibility(View.GONE);
            if (detail == null) {
                // Keep showing what we have if only a refresh failed.
                if (challenge == null) {
                    showMessage(notFound ? R.string.arena_not_found : R.string.arena_load_error, !notFound);
                }
                return;
            }
            challenge = detail;
            // The "couldn't confirm" note belongs to one load; a failed-action note stays
            // until the next tap.
            if (statusRes == R.string.arena_pending_expired) statusRes = 0;
            noteExpiredPending();
            render();
        });
        ArenaApi.getStandings(this, arenaQueue, challengeId, (rows, computedAtMs) -> {
            if (gen != loadGeneration) return;
            if (rows == null) {
                // Failed refresh: keep the board we have rather than claim there is none.
                if (standings != null) return;
                rows = new java.util.ArrayList<>();
            }
            standings = rows;
            standingsAtMs = computedAtMs;
            if (challenge != null) renderStandings();
        });
    }

    /** A join/leave the backend never applied: say so once, before the store forgets it. */
    private void noteExpiredPending() {
        if (me == null || me.isEmpty()) return;
        ArenaPendingStore.Pending p = ArenaPendingStore.get(this, me, challenge.id);
        if (ArenaLogic.pendingExpired(challenge.serverJoined, p.op, p.atMs, System.currentTimeMillis())) {
            statusRes = R.string.arena_pending_expired;
        }
    }

    private void showMessage(int textRes, boolean retryable) {
        progress.setVisibility(View.GONE);
        scroll.setVisibility(View.GONE);
        actionBar.setVisibility(View.GONE);
        message.setText(textRes);
        message.setClickable(retryable);
        message.setVisibility(View.VISIBLE);
    }

    private void render() {
        ArenaChallenge c = challenge;
        message.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);

        ImageView art = findViewById(R.id.arena_hero_art);
        Glide.with(this).load(ArenaLogic.artUrl(c.art, c.type)).centerCrop().into(art);

        setText(R.id.arena_chip_recurrence, ArenaCardAdapter.recurrenceText(this, c));
        setText(R.id.arena_chip_time, ArenaCardAdapter.timeText(this, c));
        setText(R.id.arena_detail_title, c.title);
        setText(R.id.arena_detail_tagline, ArenaCardAdapter.taglineText(this, c));
        setText(R.id.arena_detail_participants, getResources().getQuantityString(
                R.plurals.arena_participants, Math.max(0, c.participantCount), Math.max(0, c.participantCount)));

        setText(R.id.arena_fact_length, durationText(c));
        setText(R.id.arena_fact_scored_by, scoredByText(c));
        setText(R.id.arena_fact_starts, ArenaCardAdapter.dateText(this, c.startMs));
        setText(R.id.arena_fact_ends, ArenaCardAdapter.dateText(this, c.endMs));

        setText(R.id.arena_detail_how, ArenaCardAdapter.howItWorksText(this, c));
        setText(R.id.arena_detail_prizes, ArenaCardAdapter.prizeText(this, c));
        TextView badges = findViewById(R.id.arena_detail_badges);
        if (c.offeredBadges.isEmpty()) {
            badges.setVisibility(View.GONE);
        } else {
            badges.setText(getString(R.string.arena_badge_award) + " " + TextUtils.join(", ", c.offeredBadges));
            badges.setVisibility(View.VISIBLE);
        }
        // The reminder only helps while there is still something to score.
        findViewById(R.id.arena_report_note).setVisibility(
                ArenaLogic.isJoinable(c.state) ? View.VISIBLE : View.GONE);

        renderOwnResult();
        renderAction();
        renderStandings();
    }

    private void renderOwnResult() {
        TextView out = findViewById(R.id.arena_your_result);
        ArenaChallenge.Participant mine = challenge.participant(me);
        if (mine == null || mine.resultRank <= 0) {
            out.setVisibility(View.GONE);
            return;
        }
        out.setText(mine.rewardAfit > 0
                ? getString(R.string.arena_your_result_afit, mine.resultRank, formatNumber(mine.rewardAfit))
                : getString(R.string.arena_your_result, mine.resultRank));
        out.setVisibility(View.VISIBLE);
    }

    private void renderAction() {
        ArenaChallenge c = challenge;
        boolean loggedIn = me != null && !me.isEmpty();
        ArenaLogic.Membership m = ArenaPendingStore.resolve(this, me, c.id, c.serverJoined);
        boolean joined = m == ArenaLogic.Membership.JOINED || m == ArenaLogic.Membership.PENDING_JOIN;
        boolean joinable = ArenaLogic.isJoinable(c.state)
                && !ArenaLogic.hasEnded(c.endMs, System.currentTimeMillis());

        int status = statusRes;
        if (status == 0) {
            if (m == ArenaLogic.Membership.PENDING_JOIN) status = R.string.arena_join_pending;
            else if (m == ArenaLogic.Membership.PENDING_LEAVE) status = R.string.arena_leave_pending;
            else if (joined) status = R.string.arena_joined;
            else if (!joinable) status = R.string.arena_closed;
        }
        if (status == 0) {
            actionStatus.setVisibility(View.GONE);
        } else {
            actionStatus.setText(status);
            actionStatus.setVisibility(View.VISIBLE);
        }

        if (!joinable) {
            // Closed: nothing to do, but a participant still sees that they were in.
            actionButton.setVisibility(View.GONE);
        } else {
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setEnabled(!acting);
            if (acting) actionButton.setText(R.string.arena_working);
            else if (!loggedIn) actionButton.setText(R.string.arena_login_to_join);
            else actionButton.setText(joined ? R.string.arena_leave : R.string.arena_join);
        }
        actionBar.setVisibility(actionButton.getVisibility() == View.VISIBLE || status != 0
                ? View.VISIBLE : View.GONE);
    }

    private void onActionClicked() {
        if (challenge == null || acting) return;
        if (me == null || me.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        ArenaLogic.Membership m = ArenaPendingStore.resolve(this, me, challenge.id, challenge.serverJoined);
        final boolean leaving = m == ArenaLogic.Membership.JOINED || m == ArenaLogic.Membership.PENDING_JOIN;
        final String id = challenge.id;
        acting = true;
        statusRes = 0;
        // Stored BEFORE sending: if the user leaves mid-request the op may still land, and
        // the next visit must not offer the same action again.
        ArenaPendingStore.put(this, me, id, leaving ? ArenaLogic.OP_LEAVE : ArenaLogic.OP_JOIN);
        renderAction();
        ArenaApi.ActionCallback done = success -> {
            acting = false;
            if (!success) {
                ArenaPendingStore.clear(this, me, id);
                statusRes = R.string.arena_action_failed;
            }
            // The backend indexes the op within a few blocks; look again so the screen
            // settles on its own. Also after a failure — a timed-out request may have landed.
            actionButton.postDelayed(reload, 10000);
            actionButton.postDelayed(reload, 30000);
            renderAction();
        };
        if (leaving) ArenaApi.leave(this, arenaQueue, me, id, done);
        else ArenaApi.join(this, arenaQueue, me, id, done);
    }

    private final Runnable reload = () -> {
        if (!isFinishing() && !isDestroyed() && !acting) load();
    };

    private void renderStandings() {
        LinearLayout container = findViewById(R.id.arena_standings_container);
        TextView empty = findViewById(R.id.arena_standings_empty);
        TextView updated = findViewById(R.id.arena_standings_updated);
        container.removeAllViews();
        if (standings == null) return;   // still loading

        if (standings.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            updated.setVisibility(View.GONE);
            return;
        }
        empty.setVisibility(View.GONE);
        if (standingsAtMs > 0) {
            updated.setText(getString(R.string.arena_standings_updated, DateUtils.getRelativeTimeSpanString(
                    standingsAtMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)));
            updated.setVisibility(View.VISIBLE);
        } else {
            updated.setVisibility(View.GONE);
        }

        String avatarTpl = getString(R.string.hive_image_host_url);
        boolean mineShown = false;
        for (int i = 0; i < standings.size(); i++) {
            ArenaChallenge.StandingRow r = standings.get(i);
            boolean isMe = r.entity.equals(me);
            if (i >= MAX_STANDING_ROWS && !(isMe && !mineShown)) continue;
            if (isMe) mineShown = true;
            container.addView(buildRow(container, r, isMe, avatarTpl));
        }
    }

    private View buildRow(LinearLayout parent, ArenaChallenge.StandingRow r, boolean isMe, String avatarTpl) {
        View row = getLayoutInflater().inflate(R.layout.arena_standing_row, parent, false);
        TextView rank = row.findViewById(R.id.arena_row_rank);
        TextView name = row.findViewById(R.id.arena_row_name);
        TextView score = row.findViewById(R.id.arena_row_score);
        TextView reward = row.findViewById(R.id.arena_row_reward);

        rank.setText(getString(R.string.arena_rank_format, r.rank));
        name.setText("@" + r.entity);
        score.setText(formatNumber(r.score));
        if (r.rank <= 3) rank.setTextColor(getResources().getColor(R.color.md_theme_primary));
        if (isMe) {
            name.setTypeface(name.getTypeface(), Typeface.BOLD);
            row.setBackgroundResource(R.drawable.profile_stat_tile);
        }

        // What this finisher earned, once the challenge is settled.
        ArenaChallenge.Participant p = challenge.participant(r.entity);
        if (p != null && (p.rewardAfit > 0 || !p.earnedBadges.isEmpty())) {
            StringBuilder sb = new StringBuilder();
            if (p.rewardAfit > 0) sb.append(getString(R.string.arena_reward_afit, formatNumber(p.rewardAfit)));
            if (!p.earnedBadges.isEmpty()) {
                if (sb.length() > 0) sb.append("  ·  ");
                sb.append(TextUtils.join(", ", p.earnedBadges));
            }
            reward.setText(sb);
            reward.setVisibility(View.VISIBLE);
        }

        Glide.with(this)
                .load(avatarTpl.replace("USERNAME", r.entity))
                .placeholder(R.drawable.default_pic)
                .error(R.drawable.default_pic)
                .into((ImageView) row.findViewById(R.id.arena_row_avatar));

        row.setOnClickListener(v -> {
            Intent i = new Intent(this, ProfileActivity.class);
            i.putExtra(ProfileActivity.EXTRA_USERNAME, r.entity);
            startActivity(i);
        });
        return row;
    }

    private String durationText(ArenaChallenge c) {
        int[] d = ArenaLogic.duration(c.startMs, c.endMs);
        if (d == null) return "";
        int plural = d[0] == ArenaLogic.UNIT_WEEKS ? R.plurals.arena_duration_weeks
                : d[0] == ArenaLogic.UNIT_MONTHS ? R.plurals.arena_duration_months
                : R.plurals.arena_duration_days;
        return getResources().getQuantityString(plural, d[1], d[1]);
    }

    private String scoredByText(ArenaChallenge c) {
        switch (c.metric) {
            case "activity_count": return getString(R.string.arena_metric_activity);
            case "steps": return getString(R.string.arena_metric_steps);
            case "distance": return getString(R.string.arena_metric_distance);
            case "goal_hit":
                // The target moves between instances, so it always comes from the challenge.
                return c.threshold > 0
                        ? getString(R.string.arena_metric_goal_target, formatNumber(c.threshold))
                        : getString(R.string.arena_metric_goal);
            default: return ArenaLogic.humanize(c.metric);
        }
    }

    private static String formatNumber(double n) {
        NumberFormat f = NumberFormat.getInstance();
        f.setMaximumFractionDigits(2);
        return f.format(n);
    }

    private void setText(int id, CharSequence text) {
        ((TextView) findViewById(id)).setText(text);
    }
}
