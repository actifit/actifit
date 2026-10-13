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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final int BROADCAST_TIMEOUT_MS = 30000;

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
        if (LoginActivity.accessToken == null || LoginActivity.accessToken.isEmpty()) {
            // The token is a static filled at app start; it is empty if that login call
            // failed or the process was restored straight into this screen. Fetch it once.
            refreshToken(ctx, queue, me, ok -> {
                if (ok) broadcast(ctx, queue, me, op, challengeId, cb);
                else cb.onDone(false);
            });
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("op", op);
            body.put("v", 1);
            body.put("challenge_id", challengeId);

            JSONArray posting = new JSONArray();
            posting.put(me);
            JSONObject cj = new JSONObject();
            cj.put("required_auths", new JSONArray());
            cj.put("required_posting_auths", posting);
            cj.put("id", ARENA_JSON_ID);
            cj.put("json", body.toString());
            JSONArray operation = new JSONArray();
            operation.put(0, "custom_json");
            operation.put(1, cj);

            String url = Utils.apiUrl(ctx) + ctx.getString(R.string.perform_trx_link)
                    + me + "&operation=[" + URLEncoder.encode(operation.toString(), "UTF-8") + "]&bchain=HIVE";

            JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                    response -> {
                        JSONObject trx = response.optJSONObject("trx");
                        JSONObject tx = trx == null ? null : trx.optJSONObject("tx");
                        cb.onDone(response.has("success") && tx != null && tx.has("ref_block_num"));
                    },
                    error -> cb.onDone(false)) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> h = new HashMap<>();
                    h.put("Content-Type", "application/json");
                    h.put(ctx.getString(R.string.validation_header),
                            ctx.getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken);
                    return h;
                }
            };
            // This GET signs and broadcasts — an automatic retry after a slow response
            // would put a second op on chain, so it gets a long timeout and NO retries.
            req.setRetryPolicy(new DefaultRetryPolicy(BROADCAST_TIMEOUT_MS, 0, 1f));
            req.setShouldCache(false);
            queue.add(req);
        } catch (Exception e) {
            cb.onDone(false);
        }
    }

    /** Re-authenticates with the stored posting key, as SocialActivity/MarketActivity do. */
    private static void refreshToken(Context ctx, RequestQueue queue, String me, ActionCallback cb) {
        String pkey = ctx.getSharedPreferences("actifitSets", Context.MODE_PRIVATE).getString("actifitPst", "");
        if (pkey.isEmpty()) {
            cb.onDone(false);
            return;
        }
        try {
            JSONObject login = new JSONObject();
            login.put(ctx.getString(R.string.username_param), me);
            login.put(ctx.getString(R.string.pkey_param), pkey);
            login.put(ctx.getString(R.string.bchain_param), "HIVE");
            login.put(ctx.getString(R.string.keeploggedin_param), false);
            login.put(ctx.getString(R.string.login_source), ctx.getString(R.string.android) + BuildConfig.VERSION_NAME);
            JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST,
                    Utils.apiUrl(ctx) + ctx.getString(R.string.login_auth), login,
                    response -> {
                        String token = response.has("success")
                                ? response.optString(ctx.getString(R.string.login_token), "") : "";
                        if (!token.isEmpty()) LoginActivity.accessToken = token;
                        cb.onDone(!token.isEmpty());
                    },
                    error -> cb.onDone(false));
            addRead(queue, req);
        } catch (Exception e) {
            cb.onDone(false);
        }
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
