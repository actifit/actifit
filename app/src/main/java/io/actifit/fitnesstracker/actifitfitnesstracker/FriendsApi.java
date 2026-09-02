package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.content.Context;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Friend write-actions against the Actifit backend.
 *
 * Each action is TWO server-signed steps, reusing the app's existing auth (the login
 * access token as `x-acti-token: Bearer`) — no on-device key handling:
 *   1. Broadcast a posting-auth `custom_json` (id "actifit", {transaction, target}) via
 *      performTrx. The server signs+broadcasts AND auto-stores the tx in `verified_tx`,
 *      returning {trx:{tx:{id, ref_block_num}}}.
 *   2. Call the friend endpoint (addFriend/acceptFriend/cancelFriendRequest/dropFriendship)
 *      with the resulting block + trxId, which the backend verifies against `verified_tx`.
 */
public class FriendsApi {

    public interface Callback {
        void onDone(boolean success, String message);
    }

    public static void addFriend(Context ctx, Activity act, String me, String target, Callback cb) {
        doFriendAction(ctx, act, me, target, "add-friend-request", "addFriend", cb);
    }

    public static void acceptFriend(Context ctx, Activity act, String me, String target, Callback cb) {
        doFriendAction(ctx, act, me, target, "accept-friendship", "acceptFriend", cb);
    }

    public static void cancelRequest(Context ctx, Activity act, String me, String target, Callback cb) {
        doFriendAction(ctx, act, me, target, "cancel-friend-request", "cancelFriendRequest", cb);
    }

    public static void unfriend(Context ctx, Activity act, String me, String target, Callback cb) {
        doFriendAction(ctx, act, me, target, "cancel-friendship", "dropFriendship", cb);
    }

    private static void doFriendAction(Context ctx, Activity act, String me, String target,
                                       String txName, String endpoint, Callback cb) {
        if (me == null || me.isEmpty() || target == null || target.isEmpty()
                || LoginActivity.accessToken == null || LoginActivity.accessToken.isEmpty()) {
            finish(act, cb, false, ctx.getString(R.string.friends_error_login));
            return;
        }
        try {
            // custom_json op: [ "custom_json", { required_posting_auths:[me], id:"actifit", json:"{...}" } ]
            JSONArray posting = new JSONArray();
            posting.put(me);
            JSONObject inner = new JSONObject();
            inner.put("transaction", txName);
            inner.put("target", target);
            JSONObject cj = new JSONObject();
            cj.put("required_auths", new JSONArray());
            cj.put("required_posting_auths", posting);
            cj.put("id", "actifit");
            cj.put("json", inner.toString());
            JSONArray operation = new JSONArray();
            operation.put(0, "custom_json");
            operation.put(1, cj);

            String bcastUrl = Utils.apiUrl(ctx) + ctx.getString(R.string.perform_trx_link)
                    + me + "&operation=[" + URLEncoder.encode(operation.toString(), "UTF-8") + "]&bchain=HIVE";

            final RequestQueue queue = Volley.newRequestQueue(ctx);
            JsonObjectRequest bcastReq = new JsonObjectRequest(Request.Method.GET, bcastUrl, null,
                    response -> {
                        try {
                            if (response.has("success")) {
                                JSONObject tx = response.getJSONObject("trx").getJSONObject("tx");
                                String trxId = tx.getString("id");
                                // The backend verifies the friend op by tx_id (looked up in
                                // verified_tx, which performTrx auto-populates); ref_block_num is
                                // passed only to satisfy the route signature and is not used.
                                long block = tx.optLong("ref_block_num", 0);
                                // Broadcast already landed on-chain — from here we only retry the
                                // index step, never re-broadcast, to avoid duplicate custom_json ops.
                                indexFriendAction(ctx, act, queue, me, target, block, trxId, endpoint, cb, 2);
                            } else {
                                finish(act, cb, false, null);
                            }
                        } catch (Exception e) {
                            finish(act, cb, false, null);
                        }
                    },
                    error -> finish(act, cb, false, null)) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> h = new HashMap<>();
                    h.put("Content-Type", "application/json");
                    h.put(ctx.getString(R.string.validation_header),
                            ctx.getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken);
                    return h;
                }
            };
            bcastReq.setRetryPolicy(new DefaultRetryPolicy(MainActivity.connectTimeout,
                    MainActivity.connectMaxRetries, MainActivity.connectSubsequentRetryDelay));
            queue.add(bcastReq);
        } catch (Exception e) {
            finish(act, cb, false, null);
        }
    }

    private static void indexFriendAction(Context ctx, Activity act, RequestQueue queue, String me,
                                          String target, long block, String trxId, String endpoint,
                                          Callback cb, int attemptsLeft) {
        String url = Utils.apiUrl(ctx) + endpoint + "/" + me + "/" + target + "/" + block + "/" + trxId + "/HIVE";
        JsonObjectRequest idxReq = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    boolean ok = "success".equals(response.optString("status"));
                    finish(act, cb, ok, response.optString("message", null));
                },
                error -> {
                    // The op is already on-chain and stored in verified_tx, so a failed index call
                    // is safe to re-attempt with the SAME trxId — no duplicate broadcast.
                    if (attemptsLeft > 1) {
                        indexFriendAction(ctx, act, queue, me, target, block, trxId, endpoint, cb, attemptsLeft - 1);
                    } else {
                        finish(act, cb, false, null);
                    }
                });
        idxReq.setRetryPolicy(new DefaultRetryPolicy(MainActivity.connectTimeout,
                MainActivity.connectMaxRetries, MainActivity.connectSubsequentRetryDelay));
        queue.add(idxReq);
    }

    private static void finish(Activity act, Callback cb, boolean ok, String msg) {
        if (cb == null) return;
        if (act != null) {
            // Don't deliver onto a dead activity — its callback mutates now-invalid views.
            if (act.isFinishing() || act.isDestroyed()) return;
            act.runOnUiThread(() -> cb.onDone(ok, msg));
        } else {
            cb.onDone(ok, msg);
        }
    }
}
