package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The profile badges backend (the same endpoints the web profile uses).
 *
 * Only the logged-in user's badge ids are cached (in their own small prefs file).
 *
 * Reads are public GETs: /userBadges, and for the logged-in user's own profile the
 * eligibility sources /isoParticipant, /luckyWinner, /charityDonor and
 * /userRewardedPostCount. A claim is GET /claimBadge with the login session token; the server
 * re-checks eligibility and stores the badge. After a successful claim the app also writes the
 * web's on-chain record (custom_json id "actifit", {"claimed_badge": id}) — best effort: a
 * failed write never undoes the claim, and it is skipped when no posting key is stored.
 *
 * Callbacks arrive on the main thread (Volley). For reads, callers own the queue and cancel it
 * in onDestroy, so a callback never lands on a dead activity; a claim runs on an app-lifetime
 * queue, so its callback must check that the activity is still alive.
 */
public final class BadgesApi {

    private BadgesApi() {}

    private static final int READ_TIMEOUT_MS = 15000;
    private static final int CLAIM_TIMEOUT_MS = 20000;
    // Own small file, not actifitSets: it holds only the logged-in user's badge ids.
    private static final String CACHE_PREFS = "actifitBadges";
    private static final String CACHE_USER = "user";
    private static final String CACHE_IDS = "ids";

    /** Claims in flight, app-wide, with everyone waiting on each (main thread only). */
    private static final Map<String, List<ClaimCallback>> IN_FLIGHT = new HashMap<>();

    /** Everything the badge section needs. */
    public static final class Snapshot {
        /** Badge ids the user holds (deduplicated); null when /userBadges failed. */
        public Set<String> claimed;
        public final BadgeLogic.Eligibility eligibility = new BadgeLogic.Eligibility();
    }

    public interface SnapshotCallback {
        void onResult(Snapshot snapshot);
    }

    public interface ClaimCallback {
        void onResult(BadgeLogic.ClaimReply reply);
    }

    /** The logged-in user's cached badge ids; null for anyone else or when nothing is cached. */
    public static Set<String> cachedClaimed(Context ctx, String user) {
        SharedPreferences p = cache(ctx);
        if (user == null || !user.equals(p.getString(CACHE_USER, null))) return null;
        Set<String> ids = p.getStringSet(CACHE_IDS, null);
        return ids == null ? null : BadgeLogic.dedupe(ids);
    }

    /** Caches {@code ids} only for the logged-in user, replacing any other user's entry. */
    private static void saveCache(Context ctx, String user, Set<String> ids) {
        String me = ctx.getSharedPreferences("actifitSets", Context.MODE_PRIVATE).getString("actifitUser", "");
        if (user == null || !user.equals(me)) return;
        cache(ctx).edit().putString(CACHE_USER, user).putStringSet(CACHE_IDS, new HashSet<>(ids)).apply();
    }

    /** Adds a just-claimed badge to the cache, so the next screen shows it at once. */
    private static void rememberClaim(Context ctx, String user, String badge) {
        Set<String> ids = cachedClaimed(ctx, user);
        Set<String> next = ids == null ? new HashSet<>() : new HashSet<>(ids);
        next.add(badge);
        saveCache(ctx, user, next);
    }

    /** True while a claim for this badge is in flight (from any screen). */
    public static boolean isClaiming(String badge) {
        return IN_FLIGHT.containsKey(badge);
    }

    public static boolean isAnyClaiming() {
        return !IN_FLIGHT.isEmpty();
    }

    /**
     * Loads the user's badges and, when {@code withEligibility}, the four eligibility sources,
     * all in parallel. Each failed source stays unknown in the snapshot.
     */
    public static void load(Context ctx, RequestQueue queue, String user, boolean withEligibility,
                            SnapshotCallback cb) {
        final Snapshot snap = new Snapshot();
        final int[] pending = {withEligibility ? 5 : 1};
        final Runnable done = () -> {
            if (--pending[0] == 0) cb.onResult(snap);
        };
        final Context app = ctx.getApplicationContext();
        String base = Utils.apiUrl(ctx);
        String u = enc(user);

        addRead(queue, new JsonArrayRequest(Request.Method.GET, base + "userBadges/" + u, null,
                arr -> {
                    snap.claimed = parseClaimed(arr, user);
                    saveCache(app, user, snap.claimed);
                    done.run();
                },
                err -> done.run()));
        if (!withEligibility) return;

        addRead(queue, new JsonArrayRequest(Request.Method.GET, base + "isoParticipant/" + u, null,
                arr -> { snap.eligibility.isoParticipant = arr.length() > 0; done.run(); },
                err -> done.run()));
        addRead(queue, new JsonArrayRequest(Request.Method.GET, base + "luckyWinner/" + u, null,
                arr -> { snap.eligibility.luckyWinner = arr.length() > 0; done.run(); },
                err -> done.run()));
        addRead(queue, new JsonArrayRequest(Request.Method.GET, base + "charityDonor/" + u, null,
                arr -> { snap.eligibility.charityDonor = arr.length() > 0; done.run(); },
                err -> done.run()));
        // The backend sends this one as a JSON string without a JSON content type.
        addRead(queue, new StringRequest(Request.Method.GET, base + "userRewardedPostCount/" + u,
                body -> {
                    try {
                        snap.eligibility.rewardedPosts =
                                Math.max(0, new JSONObject(body).getLong("rewarded_post_count"));
                    } catch (Exception ignored) {
                        // stays unknown
                    }
                    done.run();
                },
                err -> done.run()));
    }

    /**
     * Claims a badge for the logged-in user. On SUCCESS the on-chain record is written in the
     * background (best effort). Other replies leave nothing behind. While a claim for the same
     * badge is already in flight (e.g. started from a profile screen since closed), nothing is
     * sent again: {@code cb} just joins and gets that claim's reply.
     *
     * When the session token is missing (the process was restored without LoginActivity) or
     * refused, a fresh one is fetched once from the stored posting key and the claim retried.
     */
    public static void claim(Context ctx, String user, String badge, ClaimCallback cb) {
        List<ClaimCallback> waiting = IN_FLIGHT.get(badge);
        if (waiting != null) {
            waiting.add(cb);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(cb);
        IN_FLIGHT.put(badge, waiting);
        final Context app = ctx.getApplicationContext();
        final ClaimCallback finish = reply -> {
            List<ClaimCallback> all = IN_FLIGHT.remove(badge);
            if (all != null) for (ClaimCallback c : all) c.onResult(reply);
        };
        String token = LoginActivity.accessToken;
        if (token == null || token.isEmpty()) {
            refreshThenSend(app, user, badge, finish);
        } else {
            send(app, user, badge, true, finish);
        }
    }

    private static void refreshThenSend(Context app, String user, String badge, ClaimCallback finish) {
        refreshToken(app, user, ok -> {
            if (ok) send(app, user, badge, false, finish);
            else finish.onResult(BadgeLogic.ClaimReply.AUTH);
        });
    }

    private static void send(Context app, String user, String badge, boolean mayRefresh, ClaimCallback finish) {
        final String header = app.getString(R.string.validation_header);
        final String headerValue = app.getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken;
        String url = Utils.apiUrl(app) + "claimBadge/?user=" + enc(user) + "&badge=" + enc(badge);
        StringRequest req = new StringRequest(Request.Method.GET, url,
                body -> {
                    BadgeLogic.ClaimReply reply = BadgeLogic.ClaimReply.UNKNOWN;
                    try {
                        JSONObject o = new JSONObject(body);
                        reply = BadgeLogic.classifyClaimReply(
                                o.has("status") ? o.optString("status") : null,
                                o.has("success") && !o.optBoolean("success", true),
                                o.has("error"));
                    } catch (Exception ignored) {
                        // not JSON (proxy page etc.) -> UNKNOWN, offered as a retry
                    }
                    if (reply == BadgeLogic.ClaimReply.AUTH && mayRefresh) {
                        // The token was refused before anything was stored: safe to retry once.
                        refreshThenSend(app, user, badge, finish);
                        return;
                    }
                    if (reply == BadgeLogic.ClaimReply.SUCCESS) {
                        rememberClaim(app, user, badge);
                        BadgeHomeNudge.invalidate();
                        writeOnChain(app, user, badge);
                    }
                    finish.onResult(reply);
                },
                err -> finish.onResult(BadgeLogic.ClaimReply.UNKNOWN)) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put(header, headerValue);
                return h;
            }
        };
        // No automatic retry: a claim the server stored but whose reply was lost must not be
        // sent again blindly (the profile refetches the badge list before offering a retry).
        req.setRetryPolicy(new DefaultRetryPolicy(CLAIM_TIMEOUT_MS, 0, 1f));
        req.setShouldCache(false);
        // An app-lifetime queue, not the screen's: leaving the profile mid-claim must not drop
        // the reply, or the on-chain record that follows a stored claim would never be written.
        claimQueue(app).add(req);
    }

    interface TokenCallback {
        void onDone(boolean ok);
    }

    /**
     * Re-issues the session token from the stored posting key: the same login_auth request
     * Wallet/Market/Settings/Social send when they open. No stored key -> not ok ("log in again").
     */
    static void refreshToken(Context app, String user, TokenCallback cb) {
        String pkey = HiveBroadcaster.storedKey(app, HiveBroadcaster.KeyRole.POSTING);
        if (user == null || user.isEmpty() || pkey.isEmpty()) {
            cb.onDone(false);
            return;
        }
        JSONObject loginSettings = new JSONObject();
        try {
            loginSettings.put(app.getString(R.string.username_param), user);
            loginSettings.put(app.getString(R.string.pkey_param), pkey);
            loginSettings.put(app.getString(R.string.bchain_param), "HIVE");
            loginSettings.put(app.getString(R.string.keeploggedin_param), false);
            loginSettings.put(app.getString(R.string.login_source),
                    app.getString(R.string.android) + BuildConfig.VERSION_NAME);
        } catch (Exception e) {
            cb.onDone(false);
            return;
        }
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST,
                Utils.apiUrl(app) + app.getString(R.string.login_auth), loginSettings,
                response -> {
                    String token = response.has("success")
                            ? response.optString(app.getString(R.string.login_token), "") : "";
                    if (!token.isEmpty()) LoginActivity.accessToken = token;
                    cb.onDone(!token.isEmpty());
                },
                error -> cb.onDone(false));
        req.setRetryPolicy(new DefaultRetryPolicy(CLAIM_TIMEOUT_MS, 0, 1f));
        req.setShouldCache(false);
        claimQueue(app).add(req);
    }

    private static RequestQueue claimQueue;

    static synchronized RequestQueue claimQueue(Context app) {
        if (claimQueue == null) claimQueue = Volley.newRequestQueue(app);
        return claimQueue;
    }

    /** The web's on-chain record of the claim. Silent: the claim already stands either way. */
    private static void writeOnChain(Context app, String user, String badge) {
        if (HiveBroadcaster.storedKey(app, HiveBroadcaster.KeyRole.POSTING).isEmpty()) return;
        HiveTx.Op op = HiveTx.CustomJson.posting(user, "actifit", BadgeLogic.claimedJson(badge));
        HiveBroadcaster.broadcast(app, HiveBroadcaster.ops(op), HiveBroadcaster.KeyRole.POSTING, result -> {
            // nothing to do: the badge is claimed on the server whatever happens here
        });
    }

    /** Badge ids from a /userBadges reply, keeping only this user's docs, deduplicated. */
    static Set<String> parseClaimed(JSONArray arr, String user) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String owner = o.optString("user", "");
            if (!owner.isEmpty() && !owner.equalsIgnoreCase(user)) continue;
            ids.add(o.optString("badge", ""));
        }
        return BadgeLogic.dedupe(ids);
    }

    private static void addRead(RequestQueue queue, Request<?> req) {
        req.setRetryPolicy(new DefaultRetryPolicy(READ_TIMEOUT_MS, 1, 1f));
        // Claims must show up at once, not behind Volley's disk cache.
        req.setShouldCache(false);
        queue.add(req);
    }

    private static SharedPreferences cache(Context ctx) {
        return ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
