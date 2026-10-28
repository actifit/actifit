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
 *  - The signed transaction is saved (per user, gadget and action) BEFORE it is sent, and
 *    kept until the backend confirms it. Tapping again, or reopening the Market, settles
 *    that same transaction — re-sends it, checks the chain, or re-confirms it — instead of
 *    signing a new one, so a lost reply or a killed app can never become a second payment.
 *  - While any purchase of a gadget is unresolved, every buy button of that gadget settles
 *    it rather than starting another purchase.
 *  - Paying in HIVE moves funds BEFORE the confirm. The backend answers a bare
 *    {status:'error'} for every confirm failure (including "this tx was already used"), so a
 *    HIVE payment is only forgotten on an explicit success; until then the user is shown the
 *    transaction id and told not to pay again.
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

    /**
     * True when a purchase of this gadget (AFIT or HIVE) is signed but not yet confirmed:
     * a buy tap should settle it (buyWithHive with a null quote) instead of quoting again.
     */
    public static boolean hasUnresolvedPurchase(Context ctx, String user, String gadgetId) {
        return unresolvedBuyKey(ctx.getApplicationContext(), user, gadgetId) != null;
    }

    // ---------------------------------------------------------------- actions

    public static void buyWithAfit(Context ctx, String user, String gadgetId, Callback cb) {
        String base = Utils.apiUrl(ctx);
        start(ctx, ACT_BUY, user, gadgetId, GadgetOps.buy(user, gadgetId), HiveBroadcaster.KeyRole.POSTING,
                base + "buyGadgetKeychain/" + enc(user) + "/" + enc(gadgetId) + "/{tx}/HIVE", cb);
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
     * Pays the quoted amount in HIVE (active key), then confirms with the same server.
     * quote null: only settle an unresolved purchase (see hasUnresolvedPurchase).
     */
    public static void buyWithHive(Context ctx, String user, String gadgetId, HiveQuote quote, Callback cb) {
        start(ctx, ACT_BUY_HIVE, user, gadgetId,
                quote == null ? null
                        : GadgetOps.buyWithHive(user, ctx.getString(R.string.actifit_market), gadgetId, quote.amount),
                HiveBroadcaster.KeyRole.ACTIVE,
                quote == null ? null
                        : quote.apiBase + "buyGadgetHiveKeychain/" + enc(user) + "/" + enc(gadgetId) + "/{tx}/HIVE",
                cb);
    }

    /** benefic: the friend receiving the boost, or null. */
    public static void activate(Context ctx, String user, String gadgetId, String benefic, Callback cb) {
        String b = benefic == null ? "" : benefic.replace("@", "").trim();
        // The single-gadget *Keychain activate route arrives with actifit-bot #162; the web
        // uses the multi route with one id, so do the same until then.
        start(ctx, ACT_ACTIVATE, user, gadgetId, GadgetOps.activate(user, gadgetId, b),
                HiveBroadcaster.KeyRole.POSTING, Utils.apiUrl(ctx) + "activateMultiGadgetKeychain/" + enc(user)
                        + "/" + enc(gadgetId) + "/{tx}/HIVE/" + enc(b), cb);
    }

    public static void deactivate(Context ctx, String user, String gadgetId, Callback cb) {
        start(ctx, ACT_DEACTIVATE, user, gadgetId, GadgetOps.deactivate(user, gadgetId),
                HiveBroadcaster.KeyRole.POSTING, Utils.apiUrl(ctx) + "deactivateGadgetKeychain/" + enc(user)
                        + "/" + enc(gadgetId) + "/{tx}/HIVE", cb);
    }

    /**
     * Settles every remembered action for this user (e.g. a HIVE payment whose confirm never
     * got through because the app was closed). cb runs on the main thread once per action
     * that ended: ok when it went through, else with a message worth showing (may be null).
     */
    public static void resumePending(Context ctx, String user, Callback cb) {
        final Context app = ctx.getApplicationContext();
        for (Map.Entry<String, ?> e : prefs(app).getAll().entrySet()) {
            final String key = e.getKey();
            final String[] parts = key.split("\\|", 3);
            if (parts.length != 3 || !parts[1].equals(user)) continue;
            final String lock = user + "|" + parts[2];
            if (!IN_FLIGHT.add(lock)) continue;
            EXECUTOR.execute(() -> {
                String msg;
                try {
                    msg = settle(app, key, Pending.parse(String.valueOf(e.getValue())));
                } finally {
                    IN_FLIGHT.remove(lock);
                }
                final String m = msg;
                MAIN.post(() -> cb.onDone(m == null, m));
            });
        }
    }

    // ---------------------------------------------------------------- plumbing

    private static void start(Context ctx, String action, String user, String gadgetId, HiveTx.Op op,
                              HiveBroadcaster.KeyRole role, String urlTemplate, Callback cb) {
        final Context app = ctx.getApplicationContext();
        final String lock = user + "|" + gadgetId;
        if (!IN_FLIGHT.add(lock)) {
            cb.onDone(false, app.getString(R.string.gadget_action_in_progress));
            return;
        }
        final boolean isBuy = ACT_BUY.equals(action) || ACT_BUY_HIVE.equals(action);
        EXECUTOR.execute(() -> {
            String msg;
            try {
                // An earlier attempt is unresolved: settle THAT one, don't start another.
                // For a buy, either currency's purchase counts, so the other button can't buy twice.
                String key = isBuy ? unresolvedBuyKey(app, user, gadgetId) : action + "|" + user + "|" + gadgetId;
                Pending earlier = key == null ? null : Pending.parse(prefs(app).getString(key, null));
                if (earlier != null && expired(earlier) && !isHiveBuy(key)) {
                    forget(app, key); // nothing was charged and the server won't take it any more
                    earlier = null;
                }
                if (earlier != null) {
                    msg = settle(app, key, earlier);
                } else if (op == null) {
                    msg = app.getString(R.string.error_performing_operation); // nothing left to settle
                } else {
                    msg = signSendConfirm(app, action + "|" + user + "|" + gadgetId, op, role, urlTemplate);
                }
            } catch (Exception e) {
                msg = app.getString(R.string.error_performing_operation);
            } finally {
                IN_FLIGHT.remove(lock);
            }
            final String m = msg;
            MAIN.post(() -> cb.onDone(m == null, m));
        });
    }

    /** Fresh action. Blocking; returns null on success, else a user-facing message. */
    private static String signSendConfirm(Context app, String key, HiveTx.Op op, HiveBroadcaster.KeyRole role,
                                          String urlTemplate) {
        final long now = System.currentTimeMillis();
        HiveBroadcaster.Result r = HiveBroadcaster.signAndSend(app, HiveBroadcaster.ops(op), role, null, signed ->
                // Saved BEFORE sending: if the reply is lost or the app dies, it is settled later.
                prefs(app).edit().putString(key, new Pending(signed, urlTemplate, now, false).serialize()).commit());
        if (!r.ok) {
            if (!r.ambiguous) {
                forget(app, key); // it never reached the chain: nothing to settle
                return r.error;
            }
            Pending kept = Pending.parse(prefs(app).getString(key, null));
            return kept == null ? r.error : app.getString(R.string.gadget_still_checking, kept.signed.txId);
        }
        Pending p = Pending.parse(prefs(app).getString(key, null));
        if (p == null) return app.getString(R.string.error_performing_operation);
        p = p.markSent();
        prefs(app).edit().putString(key, p.serialize()).commit();
        return confirmAndSettle(app, key, p, false);
    }

    /** An unresolved action from an earlier attempt. Blocking. */
    private static String settle(Context app, String key, Pending p) {
        boolean hive = isHiveBuy(key);
        if (p == null) {
            forget(app, key);
            return null;
        }
        if (expired(p)) {
            // Past the server's window: it can no longer confirm this. Never drop a HIVE
            // payment silently (even one never seen accepted — it may have landed): the user
            // needs the id to ask support.
            forget(app, key);
            return hive ? app.getString(R.string.gadget_hive_expired, p.signed.txId) : null;
        }
        if (!p.sent) {
            HiveBroadcaster.Result r = HiveBroadcaster.resend(app, p.signed, roleOf(key));
            if (!r.ok) {
                if (r.ambiguous) return app.getString(R.string.gadget_still_checking, p.signed.txId);
                forget(app, key); // it never landed: nothing was paid
                return app.getString(R.string.gadget_previous_not_landed);
            }
            p = p.markSent();
            prefs(app).edit().putString(key, p.serialize()).commit();
        }
        return confirmAndSettle(app, key, p, true);
    }

    /**
     * Confirms a broadcast action and decides whether to keep remembering it.
     * @return null on success, else a user-facing message. Runs off the main thread.
     */
    private static String confirmAndSettle(Context ctx, String key, Pending p, boolean resumed) {
        final boolean paidOnChain = isHiveBuy(key);
        String url = p.urlTemplate.replace("{tx}", enc(p.signed.txId))
                // The routes read the chain from ?bchain= while the verifier uses the path
                // one: send both, identical.
                + "?bchain=HIVE&operation=" + enc(p.signed.opsJson);
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
        if (reply == null) {
            // keep: confirm again later
            return ctx.getString(paidOnChain ? R.string.gadget_hive_unconfirmed : R.string.gadget_confirm_unknown,
                    p.signed.txId);
        }

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
        if (paidOnChain) {
            // The HIVE has moved whatever the server said. Its failure reply can't tell "already
            // bought with this payment" from "failed after taking it", so keep the payment on
            // record (until the server's window closes) and never let the user pay again.
            if (kind == GadgetReply.Kind.GATE) return gateMessage(ctx, reply);
            return ctx.getString(resumed || retried ? R.string.gadget_hive_needs_support
                    : R.string.gadget_hive_unconfirmed, p.signed.txId);
        }
        if (GadgetReply.alreadyProcessed(errText) || ((resumed || retried) && kind == GadgetReply.Kind.FAILED)) {
            // The server has already used this transaction (most likely an earlier attempt of
            // ours went through). It won't process it again, so stop retrying it.
            forget(ctx, key);
            return ctx.getString(R.string.gadget_already_processed, p.signed.txId);
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

    /** The stored key of an unresolved purchase of this gadget (HIVE first), or null. */
    private static String unresolvedBuyKey(Context app, String user, String gadgetId) {
        for (String action : new String[]{ACT_BUY_HIVE, ACT_BUY}) {
            String key = action + "|" + user + "|" + gadgetId;
            if (prefs(app).contains(key)) return key;
        }
        return null;
    }

    private static String gateMessage(Context ctx, JSONObject reply) {
        String m = reply.optString("message", reply.optString("error", ""));
        return m.trim().isEmpty() ? ctx.getString(R.string.error_performing_operation) : m;
    }

    private static void forget(Context ctx, String key) {
        Pending p = Pending.parse(prefs(ctx).getString(key, null));
        if (p != null) HiveBroadcaster.forget(roleOf(key), p.signed.opsJson);
        prefs(ctx).edit().remove(key).commit();
    }

    private static boolean isHiveBuy(String key) {
        return key.startsWith(ACT_BUY_HIVE + "|");
    }

    private static HiveBroadcaster.KeyRole roleOf(String key) {
        return isHiveBuy(key) ? HiveBroadcaster.KeyRole.ACTIVE : HiveBroadcaster.KeyRole.POSTING;
    }

    private static boolean expired(Pending p) {
        return System.currentTimeMillis() - p.createdMs > PENDING_MAX_AGE_MS;
    }

    /**
     * The Market shows this gadget as owned, so any purchase of it still on record has gone
     * through (the buy buttons are hidden while a copy is owned): stop re-confirming it.
     */
    public static void clearSettledPurchase(Context ctx, String user, String gadgetId) {
        if (isBusy(user, gadgetId)) return;
        Context app = ctx.getApplicationContext();
        String key;
        while ((key = unresolvedBuyKey(app, user, gadgetId)) != null) forget(app, key);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("gadgetPending", Context.MODE_PRIVATE);
    }

    /** A signed action awaiting its confirm. sent: a node has accepted it. */
    static final class Pending {
        final HiveBroadcaster.Signed signed;
        final String urlTemplate;
        final long createdMs;
        final boolean sent;

        Pending(HiveBroadcaster.Signed signed, String urlTemplate, long createdMs, boolean sent) {
            this.signed = signed;
            this.urlTemplate = urlTemplate;
            this.createdMs = createdMs;
            this.sent = sent;
        }

        Pending markSent() {
            return new Pending(signed, urlTemplate, createdMs, true);
        }

        String serialize() {
            try {
                return new JSONObject().put("tx", signed.txId).put("txJson", signed.txJson)
                        .put("ref", signed.refBlockNum).put("ops", signed.opsJson).put("exp", signed.expiresAtMs)
                        .put("blk", signed.startBlock).put("expSec", signed.expirationSec)
                        .put("url", urlTemplate).put("at", createdMs).put("sent", sent).toString();
            } catch (Exception e) {
                return "";
            }
        }

        static Pending parse(String s) {
            if (s == null || s.isEmpty()) return null;
            try {
                JSONObject o = new JSONObject(s);
                return new Pending(new HiveBroadcaster.Signed(o.getString("txJson"), o.getString("tx"),
                        o.getInt("ref"), o.getString("ops"), o.getLong("exp"), o.getLong("blk"),
                        o.getLong("expSec")),
                        o.getString("url"), o.getLong("at"), o.getBoolean("sent"));
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
