package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Signs a transaction on the device with the user's own key and broadcasts it straight to
 * a Hive node, falling back through the app's node list.
 *
 * Signing happens here; the signed transaction (never the key) goes to the node. The result
 * carries the transaction id, which the backend's *Keychain routes then use to confirm the
 * operation from the chain.
 *
 * A broadcast whose fate is unclear (a node may have taken it before its reply was lost) is
 * never replaced by a newly signed one while it can still land: the same signed transaction
 * is re-sent until it expires, and after that the chain is asked whether it got in.
 */
public final class HiveBroadcaster {

    private HiveBroadcaster() {}

    /** Seconds until the signed transaction expires (hived allows up to 3600). */
    private static final int EXPIRATION_SEC = 120;
    /** After expiry, wait a few blocks before trusting "unknown transaction". */
    private static final long SETTLE_MS = 12_000;
    /** At most this many nodes per attempt, so a dead network fails in seconds, not minutes. */
    private static final int MAX_NODES = 4;

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    /** A signed transaction, as callers may store it to re-send later. */
    public static final class Signed {
        public final String txJson;
        public final String txId;
        public final int refBlockNum;
        public final String opsJson;
        /** Device time at which the transaction expires. */
        public final long expiresAtMs;

        public Signed(String txJson, String txId, int refBlockNum, String opsJson, long expiresAtMs) {
            this.txJson = txJson;
            this.txId = txId;
            this.refBlockNum = refBlockNum;
            this.opsJson = opsJson;
            this.expiresAtMs = expiresAtMs;
        }
    }

    /** Called on a worker thread right after signing, BEFORE anything is sent. */
    public interface SignedListener {
        void onSigned(Signed signed);
    }

    /** In-memory record of transactions whose broadcast is unresolved, keyed by role|ops. */
    private static final java.util.Map<String, Signed> PENDING = new java.util.concurrent.ConcurrentHashMap<>();

    public static final class Result {
        public final boolean ok;
        /** Set when ok. */
        public final String txId;
        /** The signed transaction's ref_block_num (some confirm routes take it in the path). */
        public final int refBlockNum;
        /** The ops array as JSON, for the backend's ?operation= confirm parameter. */
        public final String operationsJson;
        /** Set when !ok: a message fit to show the user. */
        public final String error;
        /** !ok but the transaction may still be (or get) on chain: do not sign a replacement. */
        public final boolean ambiguous;

        private Result(boolean ok, String txId, int refBlockNum, String operationsJson, String error,
                       boolean ambiguous) {
            this.ok = ok;
            this.txId = txId;
            this.refBlockNum = refBlockNum;
            this.operationsJson = operationsJson;
            this.error = error;
            this.ambiguous = ambiguous;
        }
    }

    public interface Callback {
        /** Always called on the main thread. */
        void onResult(Result result);
    }

    /** Which of the user's stored keys an operation needs. */
    public enum KeyRole { POSTING, ACTIVE }

    /** What the chain says about a transaction id. */
    enum TxStatus { FOUND, NOT_FOUND, UNKNOWN }

    /** The stored key for a role, or "" when the user hasn't provided one. */
    public static String storedKey(Context ctx, KeyRole role) {
        return ctx.getSharedPreferences("actifitSets", Context.MODE_PRIVATE)
                .getString(role == KeyRole.ACTIVE ? "actvKey" : "actifitPst", "");
    }

    public static void broadcast(Context ctx, List<HiveTx.Op> ops, KeyRole role, Callback cb) {
        broadcast(ctx, ops, role, null, cb);
    }

    /** explicitKey: a key the caller already holds (e.g. typed into a dialog); null = the stored one. */
    public static void broadcast(Context ctx, List<HiveTx.Op> ops, KeyRole role, String explicitKey, Callback cb) {
        final Context app = ctx.getApplicationContext();
        EXECUTOR.execute(() -> {
            Result r = signAndSend(app, ops, role, explicitKey, null);
            MAIN.post(() -> cb.onResult(r));
        });
    }

    /**
     * Blocking: signs with the stored (or explicit) key and broadcasts. Call off the main
     * thread. listener (may be null) gets the signed transaction before it is sent.
     */
    static Result signAndSend(Context app, List<HiveTx.Op> ops, KeyRole role, String explicitKey,
                              SignedListener listener) {
        String wif = explicitKey != null && !explicitKey.trim().isEmpty() ? explicitKey.trim() : storedKey(app, role);
        return run(app, ops, wif, role, nodes(app), listener);
    }

    /**
     * Blocking: settles a transaction signed earlier. Before it expires it is re-sent as is;
     * after, the chain is asked. ok = it is on chain; !ok && !ambiguous = it never landed.
     */
    static Result resend(Context app, Signed s, KeyRole role) {
        List<String> nodes = nodes(app);
        if (System.currentTimeMillis() < s.expiresAtMs) return send(app, null, s, nodes, role, false);
        return settleExpired(app, null, s, nodes);
    }

    /**
     * Claims the account's pending rewards: reads the exact pending balances from the chain
     * (the claim must not exceed them) and signs a claim_reward_balance with the posting key.
     * Result.ok with a null txId means there was nothing to claim.
     */
    public static void claimRewards(Context ctx, String account, Callback cb) {
        final Context app = ctx.getApplicationContext();
        final String wif = storedKey(app, KeyRole.POSTING);
        final List<String> nodes = nodes(app);
        EXECUTOR.execute(() -> {
            Result r;
            JSONObject acc = null;
            for (String node : nodes) {
                try {
                    JSONObject res = call(node, "condenser_api.get_accounts", "[[" + HiveTx.jsonString(account) + "]]");
                    if (res.has("result") && res.getJSONArray("result").length() > 0) {
                        acc = res.getJSONArray("result").getJSONObject(0);
                        break;
                    }
                } catch (Exception ignored) {
                    // next node
                }
            }
            if (acc == null) {
                r = fail(app.getString(R.string.signing_error_network));
            } else {
                String hive = acc.optString("reward_hive_balance", "0.000 HIVE");
                String hbd = acc.optString("reward_hbd_balance", "0.000 HBD");
                String vests = acc.optString("reward_vesting_balance", "0.000000 VESTS");
                if (isZero(hive) && isZero(hbd) && isZero(vests)) {
                    r = new Result(true, null, 0, null, null, false);
                } else {
                    r = run(app, ops(new HiveTx.ClaimRewardBalance(account, hive, hbd, vests)), wif,
                            KeyRole.POSTING, nodes, null);
                }
            }
            final Result fr = r;
            MAIN.post(() -> cb.onResult(fr));
        });
    }

    private static boolean isZero(String asset) {
        try {
            return new java.math.BigDecimal(asset.trim().split(" ")[0]).signum() == 0;
        } catch (Exception e) {
            return true;
        }
    }

    private static Result run(Context ctx, List<HiveTx.Op> ops, String wif, KeyRole role, List<String> nodes,
                              SignedListener listener) {
        if (wif == null || wif.trim().isEmpty()) {
            return fail(ctx.getString(role == KeyRole.ACTIVE
                    ? R.string.signing_error_no_active_key : R.string.signing_error_no_posting_key));
        }
        try {
            HiveTx.decodeWif(wif);
        } catch (IllegalArgumentException e) {
            return fail(ctx.getString(R.string.signing_error_bad_key));
        }

        final String opsJson = new HiveTx.Transaction(0, 0, 0, ops).operationsJson();
        final String key = role.name() + "|" + opsJson;
        Signed pending = PENDING.get(key);
        if (pending != null) {
            if (System.currentTimeMillis() < pending.expiresAtMs) {
                // A previous attempt at exactly this action may already be on chain: re-send it.
                return send(ctx, key, pending, nodes, role, false);
            }
            Result settled = settleExpired(ctx, key, pending, nodes);
            if (settled.ok || settled.ambiguous) return settled;
            // it never landed: sign afresh below
        }

        // 1. A recent block to anchor the transaction (TaPoS) and the chain's clock.
        JSONObject dgp = null;
        for (String node : nodes) {
            try {
                JSONObject res = call(node, "condenser_api.get_dynamic_global_properties", "[]");
                if (res.has("result")) {
                    dgp = res.getJSONObject("result");
                    break;
                }
            } catch (Exception ignored) {
                // next node
            }
        }
        if (dgp == null) return fail(ctx.getString(R.string.signing_error_network));

        // 2. Sign on the device.
        HiveTx.Transaction tx;
        try {
            tx = HiveTx.Transaction.forHeadBlock(dgp.getLong("head_block_number"), dgp.getString("head_block_id"),
                    HiveTx.parseIsoSeconds(dgp.getString("time")) + EXPIRATION_SEC, ops);
            tx.sign(wif);
        } catch (Exception e) {
            return fail(ctx.getString(R.string.signing_error_generic));
        }

        Signed s = new Signed(tx.toJson(), tx.txId(), tx.refBlockNum, tx.operationsJson(),
                System.currentTimeMillis() + EXPIRATION_SEC * 1000L);
        PENDING.put(key, s);
        if (listener != null) listener.onSigned(s);
        return send(ctx, key, s, nodes, role, true);
    }

    /**
     * 3. Broadcast. Re-sending the SAME signed transaction is safe: the chain refuses a
     * duplicate id. key (may be null) is the in-memory PENDING entry to settle. fresh: this
     * transaction has never been sent before, so a refusal by every node proves it isn't on chain.
     */
    private static Result send(Context ctx, String key, Signed s, List<String> nodes, KeyRole role,
                               boolean fresh) {
        String body = "[" + s.txJson + "]";
        boolean unclear = false;
        String refusal = null;
        for (String node : nodes) {
            JSONObject res;
            try {
                res = call(node, "condenser_api.broadcast_transaction", body);
            } catch (Exception e) {
                unclear = true; // the node may have taken it before the reply was lost
                continue;
            }
            if (!res.has("error")) return landed(key, s);
            String msg = res.optJSONObject("error") != null
                    ? res.optJSONObject("error").optString("message", "") : res.optString("error", "");
            String m = msg.toLowerCase(Locale.US);
            if (m.contains("duplicate")) return landed(key, s); // already on chain
            if (isDeterministicRefusal(m)) {
                // Missing authority, resource credits, balance: no node could have accepted it.
                forgetPending(key);
                return fail(chainErrorText(ctx, msg, role));
            }
            // Anything else (an internal error, a proxy timeout, "expired") says nothing
            // certain about whether it landed: try the next node.
            unclear = unclear || !isAssert(m);
            refusal = msg;
        }
        if (fresh && !unclear && refusal != null) {
            // Every node answered and refused it: it is not on chain.
            forgetPending(key);
            return fail(chainErrorText(ctx, refusal, role));
        }
        // Keep the signed tx: a retry re-sends it, or checks the chain once it expired.
        return new Result(false, null, 0, null, ctx.getString(R.string.signing_error_network), true);
    }

    /** After expiry: did it get in? Re-signing is only allowed on a clear "not found". */
    private static Result settleExpired(Context ctx, String key, Signed s, List<String> nodes) {
        TxStatus st = System.currentTimeMillis() < s.expiresAtMs + SETTLE_MS ? TxStatus.UNKNOWN : lookup(s.txId, nodes);
        if (st == TxStatus.FOUND) return landed(key, s);
        if (st == TxStatus.NOT_FOUND) {
            forgetPending(key);
            return fail(ctx.getString(R.string.signing_error_network));
        }
        return new Result(false, null, 0, null, ctx.getString(R.string.signing_error_network), true);
    }

    private static Result landed(String key, Signed s) {
        forgetPending(key);
        return new Result(true, s.txId, s.refBlockNum, s.opsJson, null, false);
    }

    private static void forgetPending(String key) {
        if (key != null) PENDING.remove(key);
    }

    private static boolean isDeterministicRefusal(String m) {
        return m.contains("missing required posting authority") || m.contains("missing required active authority")
                || m.contains("missing authority") || m.contains("rc mana") || m.contains("not enough rc")
                || m.contains("resource credit") || m.contains("insufficient")
                || m.contains("does not have sufficient funds");
    }

    /** hived's own assertion failures, as opposed to a node or proxy problem. */
    private static boolean isAssert(String m) {
        return m.contains("assert exception") || m.contains("exception:");
    }

    /** Turns the node's error into something a user can act on. */
    static String chainErrorText(Context ctx, String msg, KeyRole role) {
        String m = msg == null ? "" : msg.toLowerCase(Locale.US);
        if (m.contains("missing required posting authority") || m.contains("missing required active authority")
                || m.contains("missing authority")) {
            return ctx.getString(role == KeyRole.ACTIVE
                    ? R.string.signing_error_wrong_active_key : R.string.signing_error_wrong_posting_key);
        }
        if (m.contains("rc mana") || m.contains("not enough rc") || m.contains("resource credit")) {
            return ctx.getString(R.string.signing_error_rc);
        }
        if (m.contains("insufficient") || m.contains("does not have sufficient funds")) {
            return ctx.getString(R.string.signing_error_funds);
        }
        return ctx.getString(R.string.signing_error_generic);
    }

    /**
     * FOUND if a node has it; NOT_FOUND only when two nodes (or every node, if fewer) answer
     * "unknown transaction"; UNKNOWN otherwise (network trouble, a node without the API).
     */
    static TxStatus lookup(String txId, List<String> nodes) {
        int unknown = 0;
        for (String node : nodes) {
            try {
                JSONObject res = call(node, "condenser_api.get_transaction", "[" + HiveTx.jsonString(txId) + "]");
                if (res.has("result") && !res.isNull("result")) return TxStatus.FOUND;
                JSONObject err = res.optJSONObject("error");
                if (err != null && err.optString("message", "").toLowerCase(Locale.US).contains("unknown transaction")) {
                    unknown++;
                }
            } catch (Exception ignored) {
                // next node
            }
        }
        return unknown >= Math.min(2, nodes.size()) ? TxStatus.NOT_FOUND : TxStatus.UNKNOWN;
    }

    private static Result fail(String message) {
        return new Result(false, null, 0, null, message, false);
    }

    private static JSONObject call(String node, String method, String paramsJson) throws Exception {
        String payload = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + paramsJson + "}";
        Request req = new Request.Builder().url(node).post(RequestBody.create(payload, JSON)).build();
        try (Response resp = HTTP.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) throw new java.io.IOException("HTTP " + resp.code());
            return new JSONObject(resp.body().string());
        }
    }

    /** The app's default node first, then its fallbacks, without repeats. */
    private static List<String> nodes(Context ctx) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(ctx.getString(R.string.hive_default_node));
        for (String n : ctx.getResources().getStringArray(R.array.hive_rpc_nodes)) {
            if (n != null && !n.trim().isEmpty()) set.add(n.trim());
        }
        List<String> all = new ArrayList<>(set);
        return all.size() > MAX_NODES ? new ArrayList<>(all.subList(0, MAX_NODES)) : all;
    }

    /** Small helper so callers don't have to reach for org.json to build an ops list. */
    public static List<HiveTx.Op> ops(HiveTx.Op... ops) {
        List<HiveTx.Op> out = new ArrayList<>();
        for (HiveTx.Op o : ops) out.add(o);
        return out;
    }
}
