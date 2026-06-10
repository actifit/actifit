package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import java.util.Random;

/**
 * Encourages app users who have NOT voted for the Actifit DHF proposal to vote on it.
 *
 * <p>Decision/caching brain shared by every trigger (app-open, after-post, dashboard card).
 * The vote itself is performed on the web (Hivesigner one-click or PeakD) because the app
 * does not currently broadcast {@code update_proposal_votes} natively. Vote status is read
 * on-chain via {@link HiveRequests#hasVotedForProposal} and cached, so existing voters are
 * never prompted, and the audience self-retires once a user votes (even if they voted
 * elsewhere). Target audience: non-voters with at least one post.
 */
public class DhfVotePromptManager {

    private static final String TAG = "DhfVotePrompt";

    // --- Config (hardcoded for now; move to Firebase Remote Config later) ---------------
    public static final int     PROPOSAL_ID         = 360;
    private static final boolean ENABLED            = true;
    private static final long   CHECK_TTL_MS        = 24L * 60 * 60 * 1000;     // re-check vote status daily
    private static final long   PROMPT_COOLDOWN_MS  = 3L * 24 * 60 * 60 * 1000; // min gap between prompts
    private static final int    MAX_DISMISSALS      = 3;                        // give up after N dismissals
    private static final double APPOPEN_PROBABILITY = 0.20;                     // random app-open roll
    private static final long   AFTERPOST_COOLDOWN_MS = 24L * 60 * 60 * 1000;   // after-post popup at most once/day
    private static final long   CARD_SNOOZE_MS      = 10L * 24 * 60 * 60 * 1000; // card reappears 10 days after dismiss

    private static final String PREFS = "actifitSets";
    private static final String K_VOTED        = "dhfVoted_" + PROPOSAL_ID;
    private static final String K_CHECKED_AT   = "dhfVotedCheckedAt";
    private static final String K_LAST_SHOWN   = "dhfPromptLastShownAt";
    private static final String K_DISMISS_CNT  = "dhfPromptDismissCount";
    private static final String K_AFTERPOST_LAST_SHOWN = "dhfAfterPostLastShownAt";
    private static final String K_HAS_POSTED   = "dhfHasPosted";
    private static final String K_WEB_PENDING  = "dhfWebVotePending";
    private static final String K_CARD_SNOOZED_AT = "dhfCardSnoozedAt";
    private static final String K_CARD_COLLAPSED = "dhfCardCollapsed";

    private final Context appCtx;
    private final SharedPreferences prefs;
    private final HiveRequests hiveRequests;
    private final Random random = new Random();

    public DhfVotePromptManager(Context ctx) {
        this.appCtx = ctx.getApplicationContext();
        this.prefs = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.hiveRequests = new HiveRequests(appCtx);
    }

    // --- Public entry points -------------------------------------------------------------

    /**
     * Called on app open (e.g. MainActivity). Refreshes stale vote status, then — for an
     * eligible non-voter — shows the prompt with a random probability so we don't nag on
     * every launch.
     */
    public void maybeShowOnAppOpen(final Activity activity, final Runnable afterRefresh) {
        if (!ENABLED || getUsername().isEmpty() || hasVoted()) {
            run(afterRefresh);
            return;
        }
        refreshIfStale(() -> {
            run(afterRefresh);                  // card reflects latest vote status
            ensureHasPosted(() -> {
                run(afterRefresh);              // card reflects the has-posted gate too
                if (!hasVoted() && passesFrequencyGates()
                        && random.nextDouble() < APPOPEN_PROBABILITY) {
                    showPrompt(activity);
                }
            });
        });
    }

    /**
     * Called right after the user successfully publishes a post (a peak-positive,
     * high-intent moment, and a guarantee that they have ≥1 post). No random roll.
     */
    public void maybeShowAfterPost(final Activity activity, final Runnable afterRefresh) {
        if (!ENABLED || getUsername().isEmpty()) {
            run(afterRefresh);
            return;
        }
        prefs.edit().putBoolean(K_HAS_POSTED, true).apply();
        refreshIfStale(() -> {
            run(afterRefresh);
            if (passesAfterPostGates()) {
                prefs.edit().putLong(K_AFTERPOST_LAST_SHOWN, System.currentTimeMillis()).apply();
                showPrompt(activity);
            }
        });
    }

    /**
     * After-post is the highest-intent moment, so it has its own short cooldown
     * (independent of the 3-day app-open cooldown). Still respects the not-voted state
     * and the overall dismissal cap.
     */
    private boolean passesAfterPostGates() {
        if (hasVoted()) return false;
        if (prefs.getInt(K_DISMISS_CNT, 0) >= MAX_DISMISSALS) return false;
        long lastShown = prefs.getLong(K_AFTERPOST_LAST_SHOWN, 0);
        return System.currentTimeMillis() - lastShown > AFTERPOST_COOLDOWN_MS;
    }

    /**
     * Called from onResume after the user was sent to a web vote page. We can't get a
     * success callback from the browser, so force a re-check to detect the vote and stop
     * prompting.
     */
    public void onResumeRecheck(final Activity activity, final Runnable afterRefresh) {
        if (!prefs.getBoolean(K_WEB_PENDING, false)) {
            run(afterRefresh);
            return;
        }
        prefs.edit().putBoolean(K_WEB_PENDING, false).apply();
        refreshVoteStatus(() -> {
            if (hasVoted()) {
                Toast.makeText(activity, R.string.dhf_vote_thanks, Toast.LENGTH_LONG).show();
            }
            run(afterRefresh);
        });
    }

    // --- Vote status caching -------------------------------------------------------------

    public boolean hasVoted() {
        return prefs.getBoolean(K_VOTED, false);
    }

    private boolean isVoteStatusStale() {
        return System.currentTimeMillis() - prefs.getLong(K_CHECKED_AT, 0) > CHECK_TTL_MS;
    }

    private void refreshIfStale(Runnable onDone) {
        if (isVoteStatusStale()) {
            refreshVoteStatus(onDone);
        } else {
            onDone.run();
        }
    }

    /** Queries the chain for the user's vote on PROPOSAL_ID and caches the result. */
    public void refreshVoteStatus(final Runnable onDone) {
        hiveRequests.hasVotedForProposal(getUsername(), PROPOSAL_ID,
                new HiveRequests.VoteStatusListener() {
                    @Override
                    public void onResult(boolean voted) {
                        prefs.edit()
                                .putBoolean(K_VOTED, voted)
                                .putLong(K_CHECKED_AT, System.currentTimeMillis())
                                .apply();
                        if (onDone != null) onDone.run();
                    }

                    @Override
                    public void onError() {
                        Log.d(TAG, "vote status check failed; leaving cache as-is");
                        if (onDone != null) onDone.run();
                    }
                });
    }

    // --- "Has at least one post" gate ----------------------------------------------------

    private void ensureHasPosted(final Runnable onDone) {
        if (prefs.getBoolean(K_HAS_POSTED, false)) {
            onDone.run();
            return;
        }
        try {
            JSONObject params = new JSONObject();
            params.put("sort", "posts");
            params.put("account", getUsername());
            params.put("limit", 1);
            hiveRequests.processRequest(appCtx.getString(R.string.get_account_posts), params)
                    .thenAccept(result -> {
                        boolean hasPost = result != null && result.length() > 0;
                        if (hasPost) prefs.edit().putBoolean(K_HAS_POSTED, true).apply();
                        if (hasPost) onDone.run();   // only proceed for users with a post
                    })
                    .exceptionally(ex -> { ex.printStackTrace(); return null; });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Frequency control ---------------------------------------------------------------

    private boolean passesFrequencyGates() {
        if (hasVoted()) return false;
        if (prefs.getInt(K_DISMISS_CNT, 0) >= MAX_DISMISSALS) return false;
        long lastShown = prefs.getLong(K_LAST_SHOWN, 0);
        return System.currentTimeMillis() - lastShown > PROMPT_COOLDOWN_MS;
    }

    private void recordShown() {
        prefs.edit().putLong(K_LAST_SHOWN, System.currentTimeMillis()).apply();
    }

    private void recordDismissed() {
        prefs.edit().putInt(K_DISMISS_CNT, prefs.getInt(K_DISMISS_CNT, 0) + 1).apply();
    }

    // --- UI ------------------------------------------------------------------------------

    /** Shows the prompt now (ignores frequency gates — callers gate beforehand). */
    public void showPrompt(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        recordShown();
        new AlertDialog.Builder(activity)
                .setTitle(R.string.dhf_vote_title)
                .setMessage(R.string.dhf_vote_message)
                .setPositiveButton(R.string.dhf_vote_btn_hivesigner,
                        (d, w) -> launchWebVote(activity, hivesignerUrl()))
                .setNeutralButton(R.string.dhf_vote_btn_peakd,
                        (d, w) -> launchWebVote(activity, peakdUrl()))
                .setNegativeButton(R.string.dhf_vote_btn_later,
                        (d, w) -> recordDismissed())
                .setOnCancelListener(d -> recordDismissed())
                .show();
    }

    private void launchWebVote(Activity activity, String url) {
        prefs.edit().putBoolean(K_WEB_PENDING, true).apply();
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Log.e(TAG, "Could not open vote url: " + url, e);
        }
    }

    private static String hivesignerUrl() {
        // One-click approve screen.
        return "https://hivesigner.com/sign/update-proposal-votes?proposal_ids=%5B"
                + PROPOSAL_ID + "%5D&approve=true";
    }

    private static String peakdUrl() {
        // Proposal page — lets the user read it before voting.
        return "https://peakd.com/proposals/" + PROPOSAL_ID;
    }

    // --- Dashboard card ------------------------------------------------------------------

    /** Whether the passive dashboard card should be visible: an eligible, un-dismissed non-voter. */
    public boolean isCardEligible() {
        return ENABLED
                && !getUsername().isEmpty()
                && !hasVoted()
                && prefs.getBoolean(K_HAS_POSTED, false)
                && !isCardSnoozed();
    }

    /** True while the card is within its 10-day snooze window after an "x" dismiss. */
    private boolean isCardSnoozed() {
        long snoozedAt = prefs.getLong(K_CARD_SNOOZED_AT, 0);
        return System.currentTimeMillis() - snoozedAt < CARD_SNOOZE_MS;
    }

    public boolean isCardCollapsed() {
        return prefs.getBoolean(K_CARD_COLLAPSED, false);
    }

    public void setCardCollapsed(boolean collapsed) {
        prefs.edit().putBoolean(K_CARD_COLLAPSED, collapsed).apply();
    }

    /** Snoozes the card for 10 days; it reappears afterward unless the user has voted. */
    public void dismissCard() {
        prefs.edit().putLong(K_CARD_SNOOZED_AT, System.currentTimeMillis()).apply();
    }

    /** Card "Vote now" tap — reuses the same Hivesigner/PeakD chooser. */
    public void onCardVoteClicked(Activity activity) {
        showPrompt(activity);
    }

    // --- Helpers -------------------------------------------------------------------------

    private static void run(Runnable r) {
        if (r != null) r.run();
    }

    private String getUsername() {
        return prefs.getString("actifitUser", "");
    }
}
