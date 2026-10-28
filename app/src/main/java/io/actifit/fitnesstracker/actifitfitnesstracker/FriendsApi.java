package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.content.Context;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import java.net.URLEncoder;

/**
 * Friend write-actions.
 *
 * Each action is two steps:
 *   1. Sign a posting-auth `custom_json` (id "actifit", {transaction, target}) on this phone
 *      and broadcast it straight to Hive ({@link HiveBroadcaster}). The backend no longer
 *      signs for the app.
 *   2. Call the friend endpoint's *HiveKeychain variant with the transaction id and the
 *      exact signed operation; the backend finds it on the chain and checks it matches.
 */
public class FriendsApi {

    public interface Callback {
        void onDone(boolean success, String message);
    }

    // The confirm route polls the chain for up to 150 s before answering.
    private static final int CONFIRM_TIMEOUT_MS = 175000;

    public static void addFriend(Context ctx, Activity act, String me, String target, Callback cb) {
        doFriendAction(ctx, act, me, target, "add-friend-request", "addFriendHiveKeychain", cb);
    }

    public static void acceptFriend(Context ctx, Activity act, String me, String target, Callback cb) {
        doFriendAction(ctx, act, me, target, "accept-friendship", "acceptFriendHiveKeychain", cb);
    }

    public static void cancelRequest(Context ctx, Activity act, String me, String target, Callback cb) {
        doFriendAction(ctx, act, me, target, "cancel-friend-request", "cancelFriendRequestHiveKeychain", cb);
    }

    public static void unfriend(Context ctx, Activity act, String me, String target, Callback cb) {
        doFriendAction(ctx, act, me, target, "cancel-friendship", "dropFriendshipHiveKeychain", cb);
    }

    /** The signed operation's json — the same shape the app has always sent. */
    static String friendJson(String txName, String target) {
        return "{\"transaction\":" + HiveTx.jsonString(txName) + ",\"target\":" + HiveTx.jsonString(target) + "}";
    }

    private static void doFriendAction(Context ctx, Activity act, String me, String target,
                                       String txName, String endpoint, Callback cb) {
        if (me == null || me.isEmpty() || target == null || target.isEmpty()) {
            finish(act, cb, false, ctx.getString(R.string.friends_error_login));
            return;
        }
        HiveTx.Op op = HiveTx.CustomJson.posting(me, "actifit", friendJson(txName, target));
        HiveBroadcaster.broadcast(ctx, HiveBroadcaster.ops(op), HiveBroadcaster.KeyRole.POSTING, result -> {
            if (!result.ok) {
                finish(act, cb, false, result.error);
                return;
            }
            String url = Utils.apiUrl(ctx) + endpoint + "/" + enc(me) + "/" + enc(target) + "/"
                    + result.refBlockNum + "/" + enc(result.txId) + "/HIVE?operation=" + enc(result.operationsJson);
            confirm(ctx, act, Volley.newRequestQueue(ctx), url, cb, 2);
        });
    }

    private static void confirm(Context ctx, Activity act, RequestQueue queue, String url, Callback cb, int attemptsLeft) {
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    boolean ok = "success".equals(response.optString("status"));
                    finish(act, cb, ok, response.optString("message", null));
                },
                error -> {
                    // The op is already on chain, so only ever retry this confirm call — with
                    // the SAME transaction id (the server refuses an id it has processed).
                    if (attemptsLeft > 1) {
                        confirm(ctx, act, queue, url, cb, attemptsLeft - 1);
                    } else {
                        finish(act, cb, false, null);
                    }
                });
        req.setRetryPolicy(new DefaultRetryPolicy(CONFIRM_TIMEOUT_MS, 0, 1f));
        req.setShouldCache(false);
        queue.add(req);
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return "";
        }
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
