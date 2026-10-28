package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Gadget buy / activate / deactivate.
 *
 * Each action is signed and broadcast on the device ({@link HiveBroadcaster}), then
 * confirmed by the backend's *Keychain route, which finds the transaction on the chain and
 * checks its operations match the ?operation= we send (deep equality), before buying,
 * activating or deactivating. The ops sent are exactly the ones that were signed.
 *
 * The web (components/Product.vue) builds the same operations; keep them in step.
 */
public final class GadgetApi {

    private GadgetApi() {}

    public interface Callback {
        /** Main thread. message is user-facing; null on success. */
        void onDone(boolean ok, String message);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // The confirm routes poll the chain for up to 150 s before answering, so wait longer
    // than that: a client timeout first would abandon a purchase the server then completes.
    private static final OkHttpClient CONFIRM_HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(175, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build();

    // ---------------------------------------------------------------- actions

    public static void buyWithAfit(Context ctx, String user, String gadgetId, Callback cb) {
        run(ctx, GadgetOps.buy(user, gadgetId), HiveBroadcaster.KeyRole.POSTING,
                "buyGadgetKeychain/" + enc(user) + "/" + enc(gadgetId) + "/{tx}/HIVE", cb);
    }

    /** @param hiveAmount the price in HIVE, e.g. 12.3456 — sent with 3 decimals. */
    public static void buyWithHive(Context ctx, String user, String gadgetId, double hiveAmount, Callback cb) {
        String amount;
        try {
            amount = HiveTx.formatAmount(hiveAmount, "HIVE");
        } catch (IllegalArgumentException e) {
            cb.onDone(false, ctx.getString(R.string.error_buy_product));
            return;
        }
        run(ctx, GadgetOps.buyWithHive(user, ctx.getString(R.string.actifit_market), gadgetId, amount), HiveBroadcaster.KeyRole.ACTIVE,
                "buyGadgetHiveKeychain/" + enc(user) + "/" + enc(gadgetId) + "/{tx}/HIVE", cb);
    }

    /** benefic: the friend receiving the boost, or null. */
    public static void activate(Context ctx, String user, String gadgetId, String benefic, Callback cb) {
        String b = benefic == null ? "" : benefic.replace("@", "").trim();
        // There is no single-gadget *Keychain activate route on every server yet; the web uses
        // the multi route with one id, so do the same.
        run(ctx, GadgetOps.activate(user, gadgetId, b), HiveBroadcaster.KeyRole.POSTING,
                "activateMultiGadgetKeychain/" + enc(user) + "/" + enc(gadgetId) + "/{tx}/HIVE/" + enc(b), cb);
    }

    public static void deactivate(Context ctx, String user, String gadgetId, Callback cb) {
        run(ctx, GadgetOps.deactivate(user, gadgetId), HiveBroadcaster.KeyRole.POSTING,
                "deactivateGadgetKeychain/" + enc(user) + "/" + enc(gadgetId) + "/{tx}/HIVE", cb);
    }

    // ---------------------------------------------------------------- plumbing

    private static void run(Context ctx, HiveTx.Op op, HiveBroadcaster.KeyRole role, String pathFormat, Callback cb) {
        final Context app = ctx.getApplicationContext();
        List<HiveTx.Op> ops = HiveBroadcaster.ops(op);
        HiveBroadcaster.broadcast(app, ops, role, result -> {
            if (!result.ok) {
                cb.onDone(false, result.error);
                return;
            }
            final String url = Utils.apiUrl(app) + pathFormat.replace("{tx}", enc(result.txId))
                    // The routes read the chain from ?bchain= while the verifier uses the path one:
                    // send both, identical.
                    + "?bchain=HIVE&operation=" + enc(result.operationsJson);
            EXECUTOR.execute(() -> {
                String message = confirm(app, url, result.txId);
                MAIN.post(() -> cb.onDone(message == null, message));
            });
        });
    }

    /** @return null on success, else a user-facing message. Runs off the main thread. */
    private static String confirm(Context ctx, String url, String txId) {
        JSONObject reply = null;
        // One retry, only when no reply came back at all. Safe: the server records each
        // verified transaction id and refuses to process it twice.
        for (int attempt = 0; attempt < 2 && reply == null; attempt++) {
            Request req = new Request.Builder().url(url).get().build();
            try (Response resp = CONFIRM_HTTP.newCall(req).execute()) {
                if (resp.body() != null) reply = new JSONObject(resp.body().string());
            } catch (Exception ignored) {
                // network or non-JSON reply: maybe retry
            }
        }
        if (reply == null) {
            // The transaction is on chain but we can't tell whether the server processed it.
            return ctx.getString(R.string.gadget_confirm_unknown, txId);
        }
        Object err = reply.opt("error");
        boolean hasError = err != null && err != JSONObject.NULL && !Boolean.FALSE.equals(err);
        Boolean success = reply.has("success") ? reply.optBoolean("success") : null;
        GadgetReply.Kind kind = GadgetReply.classify(hasError, err instanceof String ? (String) err : "",
                reply.optString("status", ""), success, reply.optString("code", ""));
        switch (kind) {
            case OK: return null;
            case GATE: {
                String m = reply.optString("message", reply.optString("error", ""));
                return m.trim().isEmpty() ? ctx.getString(R.string.error_performing_operation) : m;
            }
            case INSUFFICIENT_AFIT: return ctx.getString(R.string.gadget_buy_insufficient_afit);
            case BUSY: return ctx.getString(R.string.gadget_buy_busy);
            case UNAVAILABLE: return ctx.getString(R.string.gadget_buy_unavailable);
            case SESSION: return ctx.getString(R.string.gadget_session_expired);
            default: return ctx.getString(R.string.error_performing_operation);
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return "";
        }
    }
}
