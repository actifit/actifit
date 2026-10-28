package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
 */
public final class HiveBroadcaster {

    private HiveBroadcaster() {}

    /** Seconds until the signed transaction expires (hived allows up to 3600). */
    private static final int EXPIRATION_SEC = 120;

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    /**
     * A signed transaction whose broadcast could not be confirmed (every node failed at the
     * network level). A retry of the SAME operations re-sends this exact transaction instead
     * of signing a new one: if the first attempt actually landed, the chain refuses the
     * duplicate and we report success — so a lost reply can never turn into a second payment.
     */
    private static final class Pending {
        final String txJson;
        final String txId;
        final int refBlockNum;
        final String opsJson;
        final long reuseUntilMs;

        Pending(String txJson, String txId, int refBlockNum, String opsJson, long reuseUntilMs) {
            this.txJson = txJson;
            this.txId = txId;
            this.refBlockNum = refBlockNum;
            this.opsJson = opsJson;
            this.reuseUntilMs = reuseUntilMs;
        }
    }

    private static final java.util.Map<String, Pending> PENDING = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

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

        private Result(boolean ok, String txId, int refBlockNum, String operationsJson, String error) {
            this.ok = ok;
            this.txId = txId;
            this.refBlockNum = refBlockNum;
            this.operationsJson = operationsJson;
            this.error = error;
        }
    }

    public interface Callback {
        /** Always called on the main thread. */
        void onResult(Result result);
    }

    /** Which of the user's stored keys an operation needs. */
    public enum KeyRole { POSTING, ACTIVE }

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
        final String wif = explicitKey != null && !explicitKey.trim().isEmpty() ? explicitKey.trim() : storedKey(app, role);
        final List<String> nodes = nodes(app);
        EXECUTOR.execute(() -> {
            Result r = run(app, ops, wif, role, nodes);
            MAIN.post(() -> cb.onResult(r));
        });
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
                    r = new Result(true, null, 0, null, null);
                } else {
                    r = run(app, ops(new HiveTx.ClaimRewardBalance(account, hive, hbd, vests)), wif, KeyRole.POSTING, nodes);
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

    private static Result run(Context ctx, List<HiveTx.Op> ops, String wif, KeyRole role, List<String> nodes) {
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
        Pending pending = PENDING.get(key);
        if (pending != null) {
            if (System.currentTimeMillis() < pending.reuseUntilMs) {
                // A previous attempt at exactly this action may already be on chain: re-send it.
                return send(ctx, key, pending, nodes, role);
            }
            // Too close to expiry to re-send. If it landed, that is the answer; never sign twice.
            if (onChain(pending.txId, nodes)) {
                PENDING.remove(key);
                return new Result(true, pending.txId, pending.refBlockNum, pending.opsJson, null);
            }
            PENDING.remove(key);
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

        // Remember it until shortly before it expires, in case every node fails below.
        Pending p = new Pending(tx.toJson(), tx.txId(), tx.refBlockNum, tx.operationsJson(),
                System.currentTimeMillis() + (EXPIRATION_SEC - 20) * 1000L);
        PENDING.put(key, p);
        return send(ctx, key, p, nodes, role);
    }

    /** 3. Broadcast. Re-sending the SAME signed transaction is safe: the chain refuses a duplicate id. */
    private static Result send(Context ctx, String key, Pending p, List<String> nodes, KeyRole role) {
        String body = "[" + p.txJson + "]";
        boolean networkTrouble = false;
        for (String node : nodes) {
            JSONObject res;
            try {
                res = call(node, "condenser_api.broadcast_transaction", body);
            } catch (Exception e) {
                networkTrouble = true;
                continue; // network trouble on this node: try the next one
            }
            if (!res.has("error")) {
                PENDING.remove(key);
                return new Result(true, p.txId, p.refBlockNum, p.opsJson, null);
            }
            String msg = res.optJSONObject("error") != null
                    ? res.optJSONObject("error").optString("message", "") : res.optString("error", "");
            if (msg.toLowerCase(java.util.Locale.US).contains("duplicate")) {
                PENDING.remove(key);
                return new Result(true, p.txId, p.refBlockNum, p.opsJson, null); // already on chain
            }
            // An earlier node may have accepted it before its reply was lost; then this refusal
            // (e.g. expired / TaPoS) says nothing about whether it landed. Ask the chain.
            if (networkTrouble && onChain(p.txId, nodes)) {
                PENDING.remove(key);
                return new Result(true, p.txId, p.refBlockNum, p.opsJson, null);
            }
            // The chain refused it (key, resource credits, balance…): another node won't differ,
            // and it never landed, so the next attempt may sign afresh.
            PENDING.remove(key);
            return fail(chainErrorText(ctx, msg, role));
        }
        // Every node failed at the network level: keep the signed tx so a retry re-sends it.
        return fail(ctx.getString(R.string.signing_error_network));
    }

    /** Turns the node's error into something a user can act on. */
    static String chainErrorText(Context ctx, String msg, KeyRole role) {
        String m = msg == null ? "" : msg.toLowerCase(java.util.Locale.US);
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

    private static Result fail(String message) {
        return new Result(false, null, 0, null, message);
    }

    private static JSONObject call(String node, String method, String paramsJson) throws Exception {
        String payload = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + paramsJson + "}";
        Request req = new Request.Builder().url(node).post(RequestBody.create(payload, JSON)).build();
        try (Response resp = HTTP.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) throw new java.io.IOException("HTTP " + resp.code());
            return new JSONObject(resp.body().string());
        }
    }

    /** True if a node knows this transaction id (it was included in a block). */
    private static boolean onChain(String txId, List<String> nodes) {
        for (String node : nodes) {
            try {
                JSONObject res = call(node, "condenser_api.get_transaction", "[" + HiveTx.jsonString(txId) + "]");
                if (res.has("result") && !res.isNull("result")) return true;
                // an error is "unknown transaction" or a node without that API: ask the next one
            } catch (Exception ignored) {
                // next node
            }
        }
        return false;
    }

    /** At most this many nodes per attempt, so a dead network fails in seconds, not minutes. */
    private static final int MAX_NODES = 4;

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
