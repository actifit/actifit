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
 * The key never leaves the phone. The result carries the transaction id, which the
 * backend's *Keychain routes then use to confirm the operation from the chain.
 */
public final class HiveBroadcaster {

    private HiveBroadcaster() {}

    /** Seconds until the signed transaction expires (hived allows up to 3600). */
    private static final int EXPIRATION_SEC = 120;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
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

        // 3. Broadcast. Re-sending the SAME signed transaction to another node is safe:
        //    the chain rejects a duplicate id, so it can never land twice.
        String body = "[" + tx.toJson() + "]";
        for (String node : nodes) {
            JSONObject res;
            try {
                res = call(node, "condenser_api.broadcast_transaction", body);
            } catch (Exception e) {
                continue; // network trouble on this node: try the next one
            }
            if (!res.has("error")) return new Result(true, tx.txId(), tx.refBlockNum, tx.operationsJson(), null);
            String msg = res.optJSONObject("error") != null
                    ? res.optJSONObject("error").optString("message", "") : res.optString("error", "");
            if (msg.toLowerCase(java.util.Locale.US).contains("duplicate")) {
                return new Result(true, tx.txId(), tx.refBlockNum, tx.operationsJson(), null); // already on chain
            }
            // The chain refused it (key, resource credits, balance…): another node won't differ.
            return fail(chainErrorText(ctx, msg, role));
        }
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

    /** The app's default node first, then its fallbacks, without repeats. */
    private static List<String> nodes(Context ctx) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(ctx.getString(R.string.hive_default_node));
        for (String n : ctx.getResources().getStringArray(R.array.hive_rpc_nodes)) {
            if (n != null && !n.trim().isEmpty()) set.add(n.trim());
        }
        return new ArrayList<>(set);
    }

    /** Small helper so callers don't have to reach for org.json to build an ops list. */
    public static List<HiveTx.Op> ops(HiveTx.Op... ops) {
        List<HiveTx.Op> out = new ArrayList<>();
        for (HiveTx.Op o : ops) out.add(o);
        return out;
    }
}
