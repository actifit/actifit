package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * confirmed by the backend's *Keychain route, which finds the transaction on the chain,
 * checks its operations match the ?operation= we send, and ties it to the gadget.
 *
 * Money safety:
 *  - A broadcast action is remembered (per user, gadget and action) until the backend has
 *    confirmed it. Tapping again, or reopening the Market, RE-CONFIRMS that same
 *    transaction instead of signing a new one — so a lost reply or a killed app can never
 *    turn into a second payment. The server accepts a transaction for 7 hours and refuses
 *    to process one twice.
 *  - Paying in HIVE moves funds BEFORE the confirm, so its failures are never worded as
 *    "you were not charged"; they name the transaction id instead.
 *  - Only one action per gadget at a time.
 */
public final class GadgetApi {

    private GadgetApi() {}

    public interface Callback {
        /** Main thread. message is user-facing; null on success. */
        void onDone(boolean ok, String message);
    }

    public interface QuoteCallback {
        /** Main thread. quote is null (and error set) when the price couldn't be read. */
        void onQuote(HiveQuote quote, String error);
    }

    /** A HIVE price read from one API server; the confirm goes to the same server. */
    public static final class HiveQuote {
        public final String amount;   // "12.346 HIVE"
        final String apiBase;

        HiveQuote(String amount, String apiBase) {
            this.amount = amount;
            this.apiBase = apiBase;
        }
    }

    static final String ACT_BUY = "buy";
    static final String ACT_BUY_HIVE = "buyhive";
    static final String ACT_ACTIVATE = "activate";
    static final String ACT_DEACTIVATE = "deactivate";

    /** Head-room over the quoted rate, so a price refresh on the server between quote and
     *  confirm can't make an honest payment fall short. */
    private static final BigDecimal HIVE_PRICE_BUFFER = new BigDecimal("1.005");
    /** The server accepts a transaction for 7 hours; stop re-confirming a little before. */
    private static final long PENDING_MAX_AGE_MS = 6L * 60 * 60 * 1000;

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // The confirm routes poll the chain for up to 150 s before answering, so wait longer
    // than that: a client timeout first would abandon a purchase the server then completes.
    private static final OkHttpClient CONFIRM_HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(175, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build();
    private static final OkHttpClient QUICK_HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private static final Set<String> IN_FLIGHT = Collections.synchronizedSet(new HashSet<>());

    /** True while any action for this gadget is running — the card keeps its buttons off. */
    public static boolean isBusy(String user, String gadgetId) {
        return IN_FLIGHT.contains(user + "|" + gadgetId);
    }

    // ---------------------------------------------------------------- actions

    public static void buyWithAfit(Context ctx, String user, String gadgetId, Callback cb) {
        String base = Utils.apiUrl(ctx);
        start(ctx, ACT_BUY, user, gadgetId, GadgetOps.buy(user, gadgetId), HiveBroadcaster.KeyRole.POSTING,
                base + "buyGadgetKeychain/" + enc(user) + "/" + enc(gadgetId) + "/{tx}/HIVE", false, cb);
    }

    /**
     * Reads the current AFIT→HIVE rate (from the server that will confirm the payment) and
     * prices the gadget, so the user can see the exact amount before paying.
     */
    public static void quoteHive(Context ctx, double priceAfit, QuoteCallback cb) {
        final Context app = ctx.getApplicationContext();
        final String base = Utils.apiUrl(app);
        EXECUTOR.execute(() -> {
            HiveQuote q = null;
            try {
                Request req = new Request.Builder().url(base + app.getString(R.string.afit_price_he)).get().build();
                try (Response resp = QUICK_HTTP.newCall(req).execute()) {
                    JSONObject o = new JSONObject(resp.body() == null ? "" : resp.body().string());
                    double rate = o.getDouble("afitHiveLastPrice");
                    BigDecimal amount = BigDecimal.valueOf(priceAfit).multiply(BigDecimal.valueOf(rate))
                            .multiply(HIVE_PRICE_BUFFER).setScale(3, RoundingMode.CEILING);
                    if (amount.signum() > 0) q = new HiveQuote(amount.toPlainString() + " HIVE", base);
                }
            } catch (Exception ignored) {
                // q stays null
            }
            final HiveQuote fq = q;
            MAIN.post(() -> cb.onQuote(fq, fq == null ? app.getString(R.string.signing_error_network) : null));
        });
    }

    /**
     * True when an earlier HIVE payment for this gadget is on chain but not yet confirmed:
     * the card should re-confirm it (buyWithHive with a null quote) instead of quoting again.
     */
    public static boolean hasUnconfirmedHivePayment(Context ctx, String user, String gadgetId) {
        Pending p = Pending.parse(prefs(ctx.getApplicationContext())
                .getString(ACT_BUY_HIVE + "|" + user + "|" + gadgetId, null));
        return p != null && System.currentTimeMillis() - p.createdMs < PENDING_MAX_AGE_MS;
    }

    /**
     * Pays the quoted amount in HIVE (active key), then confirms with the same server.
     * quote null: only re-confirm an earlier payment (see hasUnconfirmedHivePayment).
     */
    public static void buyWithHive(Context ctx, String user, String gadgetId, HiveQuote quote, Callback cb) {
        start(ctx, ACT_BUY_HIVE, user, gadgetId,
                quote == null ? null
                        : GadgetOps.buyWithHive(user, ctx.getString(R.string.actifit_market), gadgetId, quote.amount),
                HiveBroadcaster.KeyRole.ACTIVE,
                quote == null ? null
                        : quote.apiBase + "buyGadgetHiveKeychain/" + enc(user) + "/" + enc(gadgetId) + "/{tx}/HIVE",
                true, cb);
    }

    /** benefic: the friend receiving the boost, or null. */
    public static void activate(Context ctx, String user, String gadgetId, String benefic, Callback cb) {
        String b = benefic == null ? "" : benefic.replace("@", "").trim();
        // The single-gadget *Keychain activate route arrives with actifit-bot #162; the web
        // uses the multi route with one id, so do the same until then.
        start(ctx, ACT_ACTIVATE, user, gadgetId, GadgetOps.activate(user, gadgetId, b),
                HiveBroadcaster.KeyRole.POSTING, Utils.apiUrl(ctx) + "activateMultiGadgetKeychain/" + enc(user)
                        + "/" + enc(gadgetId) + "/{tx}/HIVE/" + enc(b), false, cb);
    }

    public static void deactivate(Context ctx, String user, String gadgetId, Callback cb) {
        start(ctx, ACT_DEACTIVATE, user, gadgetId, GadgetOps.deactivate(user, gadgetId),
                HiveBroadcaster.KeyRole.POSTING, Utils.apiUrl(ctx) + "deactivateGadgetKeychain/" + enc(user)
                        + "/" + enc(gadgetId) + "/{tx}/HIVE", false, cb);
    }

    /**
     * Re-confirms every remembered, still-valid action for this user (e.g. a HIVE payment
     * whose confirm never got through because the app was closed). onConfirmed runs on the
     * main thread once per action that went through, so the caller can refresh.
     */
    public static void resumePending(Context ctx, String user, Callback onConfirmed) {
        final Context app = ctx.getApplicationContext();
        for (Map.Entry<String, ?> e : prefs(app).getAll().entrySet()) {
            final String key = e.getKey();
            final String[] parts = key.split("\\|", 3);
            if (parts.length != 3 || !parts[1].equals(user)) continue;
            final Pending p = Pending.parse(String.valueOf(e.getValue()));
            if (p == null || System.currentTimeMillis() - p.createdMs > PENDING_MAX_AGE_MS) {
                prefs(app).edit().remove(key).apply();
                continue;
            }
            final String lock = user + "|" + parts[2];
            if (!IN_FLIGHT.add(lock)) continue;
            EXECUTOR.execute(() -> {
                String msg = confirmAndSettle(app, key, p, ACT_BUY_HIVE.equals(parts[0]), true);
                IN_FLIGHT.remove(lock);
                if (msg == null) MAIN.post(() -> onConfirmed.onDone(true, null));
            });
        }
    }

    // ---------------------------------------------------------------- plumbing

    private static void start(Context ctx, String action, String user, String gadgetId, HiveTx.Op op,
                              HiveBroadcaster.KeyRole role, String urlTemplate, boolean paidOnChain, Callback cb) {
        final Context app = ctx.getApplicationContext();
        final String lock = user + "|" + gadgetId;
        if (!IN_FLIGHT.add(lock)) {
            cb.onDone(false, app.getString(R.string.gadget_action_in_progress));
            return;
        }
        final Callback done = (ok, msg) -> {
            IN_FLIGHT.remove(lock);
            cb.onDone(ok, msg);
        };
        final String key = action + "|" + user + "|" + gadgetId;

        // An earlier attempt at this exact action is already on chain: confirm it, don't pay again.
        final Pending earlier = Pending.parse(prefs(app).getString(key, null));
        if (earlier != null && System.currentTimeMillis() - earlier.createdMs < PENDING_MAX_AGE_MS) {
            EXECUTOR.execute(() -> {
                String msg = confirmAndSettle(app, key, earlier, paidOnChain, true);
                MAIN.post(() -> done.onDone(msg == null, msg));
            });
            return;
        }
        prefs(app).edit().remove(key).apply();
        if (op == null) { // a re-confirm whose payment record is gone: nothing to re-send
            done.onDone(false, app.getString(R.string.error_performing_operation));
            return;
        }

        List<HiveTx.Op> ops = HiveBroadcaster.ops(op);
        HiveBroadcaster.broadcast(app, ops, role, result -> {
            if (!result.ok) {
                done.onDone(false, result.error);
                return;
            }
            // Remember it BEFORE confirming: if the app dies now, the next visit confirms it.
            final Pending p = new Pending(result.txId, result.operationsJson, urlTemplate, System.currentTimeMillis());
            prefs(app).edit().putString(key, p.serialize()).commit();
            EXECUTOR.execute(() -> {
                String msg = confirmAndSettle(app, key, p, paidOnChain, false);
                MAIN.post(() -> done.onDone(msg == null, msg));
            });
        });
    }

    /**
     * Confirms a broadcast action and decides whether to keep remembering it.
     * @return null on success, else a user-facing message. Runs off the main thread.
     */
    private static String confirmAndSettle(Context ctx, String key, Pending p, boolean paidOnChain, boolean resumed) {
        String url = p.urlTemplate.replace("{tx}", enc(p.txId))
                // The routes read the chain from ?bchain= while the verifier uses the path
                // one: send both, identical.
                + "?bchain=HIVE&operation=" + enc(p.opsJson);
        JSONObject reply = null;
        boolean retried = false;
        // One retry, only when no JSON came back at all (dropped connection, a proxy's HTML
        // timeout page). Safe: the server refuses to process the same transaction twice.
        for (int attempt = 0; attempt < 2 && reply == null; attempt++) {
            retried = attempt > 0;
            Request req = new Request.Builder().url(url).get().build();
            try (Response resp = CONFIRM_HTTP.newCall(req).execute()) {
                if (resp.body() != null) reply = new JSONObject(resp.body().string());
            } catch (Exception ignored) {
                // maybe retry
            }
        }
        if (reply == null) return unconfirmed(ctx, p, paidOnChain); // keep: confirm again later

        Object err = reply.opt("error");
        boolean hasError = err != null && err != JSONObject.NULL && !Boolean.FALSE.equals(err);
        String errText = err instanceof String ? (String) err : "";
        Boolean success = reply.has("success") ? reply.optBoolean("success") : null;
        GadgetReply.Kind kind = GadgetReply.classify(hasError, errText, reply.optString("status", ""), success,
                reply.optString("code", ""));

        if (kind == GadgetReply.Kind.OK) {
            forget(ctx, key);
            return null;
        }
        if (GadgetReply.alreadyProcessed(errText) || ((resumed || retried) && kind == GadgetReply.Kind.FAILED)) {
            // The server has already used this transaction (most likely an earlier attempt of
            // ours went through). It won't process it again, so stop retrying it.
            forget(ctx, key);
            return ctx.getString(R.string.gadget_already_processed, p.txId);
        }
        if (paidOnChain) {
            // The HIVE has moved whatever the server said: never say "not charged". Keep the
            // transaction so tapping again (or reopening the Market) re-confirms it.
            if (kind == GadgetReply.Kind.GATE) return gateMessage(ctx, reply);
            return ctx.getString(R.string.gadget_hive_unconfirmed, p.txId);
        }
        // AFIT is only charged when the confirm succeeds; none of these charged anything.
        forget(ctx, key);
        switch (kind) {
            case GATE: return gateMessage(ctx, reply);
            case INSUFFICIENT_AFIT: return ctx.getString(R.string.gadget_buy_insufficient_afit);
            case BUSY: return ctx.getString(R.string.gadget_buy_busy);
            case UNAVAILABLE: return ctx.getString(R.string.gadget_buy_unavailable);
            case SESSION: return ctx.getString(R.string.gadget_session_expired);
            default: return ctx.getString(R.string.error_performing_operation);
        }
    }

    private static String unconfirmed(Context ctx, Pending p, boolean paidOnChain) {
        return ctx.getString(paidOnChain ? R.string.gadget_hive_unconfirmed : R.string.gadget_confirm_unknown, p.txId);
    }

    private static String gateMessage(Context ctx, JSONObject reply) {
        String m = reply.optString("message", reply.optString("error", ""));
        return m.trim().isEmpty() ? ctx.getString(R.string.error_performing_operation) : m;
    }

    private static void forget(Context ctx, String key) {
        prefs(ctx).edit().remove(key).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("gadgetPending", Context.MODE_PRIVATE);
    }

    /** A broadcast action awaiting its confirm. */
    static final class Pending {
        final String txId;
        final String opsJson;
        final String urlTemplate;
        final long createdMs;

        Pending(String txId, String opsJson, String urlTemplate, long createdMs) {
            this.txId = txId;
            this.opsJson = opsJson;
            this.urlTemplate = urlTemplate;
            this.createdMs = createdMs;
        }

        String serialize() {
            try {
                return new JSONObject().put("tx", txId).put("ops", opsJson).put("url", urlTemplate)
                        .put("at", createdMs).toString();
            } catch (Exception e) {
                return "";
            }
        }

        static Pending parse(String s) {
            if (s == null || s.isEmpty()) return null;
            try {
                JSONObject o = new JSONObject(s);
                return new Pending(o.getString("tx"), o.getString("ops"), o.getString("url"), o.getLong("at"));
            } catch (Exception e) {
                return null;
            }
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
