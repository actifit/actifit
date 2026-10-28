package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;

import java.util.List;

/**
 * The Arena (challenge engine) backend.
 *
 * Reads are public GETs. Writes are chain-first: a join/leave is a posting-auth
 * `custom_json` with id "actifit_arena", broadcast server-side through performTrx with the
 * login access token (the same signing path as {@link FriendsApi}). There is NO second
 * "index" call — the backend tails the chain and applies the op a few seconds later, so
 * callers keep the new state optimistically (see {@link ArenaPendingStore}).
 *
 * Callbacks arrive on the main thread (Volley). Callers own the queue and cancel it in
 * onDestroy, so a callback never lands on a dead activity.
 */
public final class ArenaApi {

    private ArenaApi() {}

    private static final String ARENA_JSON_ID = "actifit_arena";
    private static final int READ_TIMEOUT_MS = 15000;

    public interface ListCallback {
        /** @param challenges null when the request failed */
        void onResult(List<ArenaChallenge> challenges);
    }

    public interface DetailCallback {
        /** @param challenge null when missing or the request failed */
        void onResult(ArenaChallenge challenge, boolean notFound);
    }

    public interface StandingsCallback {
        /** @param rows empty when no board exists yet; null when the fetch failed */
        void onResult(List<ArenaChallenge.StandingRow> rows, long computedAtMs);
    }

    public interface BadgesCallback {
        /** @param total every badge earned; {@code badges} is the newest page of them */
        void onResult(List<ArenaChallenge.Badge> badges, int total);
    }

    public interface ActionCallback {
        void onDone(boolean success);
    }

    /** Public challenges, optionally filtered by one state and/or a participant. */
    public static void listChallenges(Context ctx, RequestQueue queue, String state, String entity,
                                      ListCallback cb) {
        StringBuilder url = new StringBuilder(Utils.apiUrl(ctx)).append("arena/challenges");
        char sep = '?';
        if (state != null) {
            url.append(sep).append("state=").append(enc(state));
            sep = '&';
        }
        if (entity != null && !entity.isEmpty()) url.append(sep).append("entity=").append(enc(entity));

        JsonArrayRequest req = new JsonArrayRequest(Request.Method.GET, url.toString(), null,
                response -> {
                    List<ArenaChallenge> out = new ArrayList<>();
                    for (int i = 0; i < response.length(); i++) {
                        JSONObject o = response.optJSONObject(i);
                        if (o == null) continue;
                        ArenaChallenge c = ArenaChallenge.fromJson(o);
                        if (!c.id.isEmpty()) out.add(c);
                    }
                    cb.onResult(out);
                },
                error -> cb.onResult(null));
        addRead(queue, req);
    }

    /** One challenge with its participants; {@code viewer} marks whether they are in it. */
    public static void getChallenge(Context ctx, RequestQueue queue, String id, String viewer,
                                    DetailCallback cb) {
        String url = Utils.apiUrl(ctx) + "arena/challenges/" + enc(id);
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> cb.onResult(ArenaChallenge.fromDetail(response, viewer), false),
                error -> cb.onResult(null,
                        error.networkResponse != null && error.networkResponse.statusCode == 404));
        addRead(queue, req);
    }

    /** The standings board. The backend sends an empty body until one has been computed. */
    public static void getStandings(Context ctx, RequestQueue queue, String id, StandingsCallback cb) {
        String url = Utils.apiUrl(ctx) + "arena/standings?id=" + enc(id);
        StringRequest req = new StringRequest(Request.Method.GET, url,
                body -> {
                    List<ArenaChallenge.StandingRow> rows = new ArrayList<>();
                    long computedAt = -1;
                    try {
                        if (body != null && body.trim().startsWith("{")) {
                            JSONObject doc = new JSONObject(body);
                            rows = ArenaChallenge.standingsFromJson(doc);
                            computedAt = ArenaLogic.parseIsoMs(doc.optString("computed_at", ""));
                        }
                    } catch (Exception ignored) {
                        // malformed board → show the empty state
                    }
                    cb.onResult(rows, computedAt);
                },
                error -> cb.onResult(null, -1));
        addRead(queue, req);
    }

    /** Badges a user has earned in public challenges; empty on failure. */
    public static void getBadges(Context ctx, RequestQueue queue, String user, BadgesCallback cb) {
        String url = Utils.apiUrl(ctx) + "arena/badges/" + enc(user) + "?limit=20";
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    List<ArenaChallenge.Badge> badges = ArenaChallenge.badgesFromJson(response);
                    cb.onResult(badges, Math.max(response.optInt("count", 0), badges.size()));
                },
                error -> cb.onResult(new ArrayList<>(), 0));
        addRead(queue, req);
    }

    public static void join(Context ctx, RequestQueue queue, String me, String challengeId, ActionCallback cb) {
        broadcast(ctx, queue, me, ArenaLogic.OP_JOIN, challengeId, cb);
    }

    public static void leave(Context ctx, RequestQueue queue, String me, String challengeId, ActionCallback cb) {
        broadcast(ctx, queue, me, ArenaLogic.OP_LEAVE, challengeId, cb);
    }

    private static void broadcast(Context ctx, RequestQueue queue, String me, String op,
                                  String challengeId, ActionCallback cb) {
        if (me == null || me.isEmpty() || challengeId == null || challengeId.isEmpty()) {
            cb.onDone(false);
            return;
        }
        // Signed on this phone with the posting key and broadcast straight to Hive: the
        // backend no longer signs for the app. No confirm call is needed — the Arena tailer
        // indexes the op from the chain.
        String json = "{\"op\":" + HiveTx.jsonString(op) + ",\"v\":1,\"challenge_id\":"
                + HiveTx.jsonString(challengeId) + "}";
        HiveBroadcaster.broadcast(ctx, HiveBroadcaster.ops(HiveTx.CustomJson.posting(me, ARENA_JSON_ID, json)),
                HiveBroadcaster.KeyRole.POSTING, result -> {
                    if (!result.ok && result.error != null) {
                        android.widget.Toast.makeText(ctx, result.error, android.widget.Toast.LENGTH_LONG).show();
                    }
                    cb.onDone(result.ok);
                });
    }

    private static void addRead(RequestQueue queue, Request<?> req) {
        req.setRetryPolicy(new DefaultRetryPolicy(READ_TIMEOUT_MS, 1, 1f));
        // Membership and standings must reflect the server, not Volley's disk cache.
        req.setShouldCache(false);
        queue.add(req);
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
