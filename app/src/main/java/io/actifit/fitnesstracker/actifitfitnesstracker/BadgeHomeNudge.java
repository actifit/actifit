package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

/**
 * The dashboard's "N badges to claim" chip. Shown only when the logged-in user can claim at
 * least one profile badge; a tap opens their profile, where the claim buttons are.
 *
 * Network only (Volley, off the main thread); a result is cached for a few minutes so
 * returning to the dashboard doesn't refetch every time. A load with a failed source simply
 * finds fewer badges to claim — the chip never claims more than the server confirmed.
 */
public final class BadgeHomeNudge {

    private BadgeHomeNudge() {}

    private static final long CACHE_MS = 5 * 60 * 1000L;

    private static String cachedUser;
    private static int cachedCount;
    private static long loadedAtMs;
    // Bumped per load and on invalidate; a load started earlier must not publish its result.
    private static int generation;
    // One app-lifetime queue: the dashboard resumes often, and a queue per resume leaks threads.
    private static RequestQueue queue;

    /** Call after a claim, so the dashboard recounts on its next resume. */
    public static void invalidate() {
        cachedUser = null;
        generation++;
    }

    public static void refresh(Activity act, String me) {
        TextView chip = act.findViewById(R.id.badges_home_nudge);
        if (chip == null) return;
        String user = me == null ? "" : me;
        if (user.isEmpty()) {
            chip.setVisibility(View.GONE);
            return;
        }
        long now = System.currentTimeMillis();
        if (user.equals(cachedUser) && now - loadedAtMs < CACHE_MS) {
            bind(act, chip, cachedCount);
            return;
        }
        if (!user.equals(cachedUser)) chip.setVisibility(View.GONE);
        if (queue == null) queue = Volley.newRequestQueue(act.getApplicationContext());
        final int gen = ++generation;
        BadgesApi.load(act, queue, user, true, snap -> {
            if (gen != generation) return;
            if (snap.claimed == null) {
                // Without the held list a claimable badge may already be claimed: show nothing,
                // and don't cache it, so the next resume tries again.
                if (!act.isFinishing() && !act.isDestroyed()) chip.setVisibility(View.GONE);
                return;
            }
            int count = BadgeLogic.claimable(snap.claimed, snap.eligibility).size();
            cachedUser = user;
            cachedCount = count;
            loadedAtMs = System.currentTimeMillis();
            if (act.isFinishing() || act.isDestroyed()) return;
            bind(act, chip, count);
        });
    }

    private static void bind(Activity act, TextView chip, int count) {
        if (count <= 0) {
            chip.setVisibility(View.GONE);
            return;
        }
        chip.setText("🏅  " + act.getResources().getQuantityString(
                R.plurals.badges_home_nudge, count, count));
        chip.setOnClickListener(v -> act.startActivity(new Intent(act, ProfileActivity.class)));
        chip.setVisibility(View.VISIBLE);
    }
}
