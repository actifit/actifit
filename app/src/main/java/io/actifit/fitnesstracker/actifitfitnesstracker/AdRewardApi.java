package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A one-time rewarded-ad id from the bot (/adRewardIntent). The ad's server-side verification
 * data then carries only "r1_&lt;id&gt;": the step tiers unlock on Health Connect steps, so the
 * ad network must not see the username or the tier. The bot picks the amount when it pays
 * (after the ad has closed), so the app reads it back from /adRewardsToday to show it.
 */
final class AdRewardApi {

    private AdRewardApi() {
    }

    enum Error { DISABLED, ALREADY_CLAIMED, AUTH, FAILED }

    interface Callback {
        void onReady(String id);

        void onError(Error error);
    }

    /**
     * Today's paid amounts by tier (1 free, 2 5K, 3 7K, 4 10K); empty when none or on a failed
     * call, null when the session couldn't be renewed (asking again would only log in again).
     */
    interface AmountsCallback {
        void onAmounts(Map<Integer, String> amounts);
    }

    private interface BodyCallback {
        void onBody(String body);

        void onError(Error error);
    }

    static final class Reply {
        final String id;
        final Error error;

        private Reply(String id, Error error) {
            this.id = id;
            this.error = error;
        }
    }

    private static final Pattern ID = Pattern.compile("[0-9a-f]{32}");
    private static final int TIMEOUT_MS = 20000;
    private static final int HTTP_UNAVAILABLE = 503;

    static String customData(String id) {
        return "r1_" + id;
    }

    /** Classifies an /adRewardIntent reply body. */
    static Reply parse(String body) {
        try {
            JSONObject o = new JSONObject(body);
            String id = o.optString("id", "");
            if ("success".equals(o.optString("status")) && ID.matcher(id).matches()) {
                return new Reply(id, null);
            }
            // checkHdrs: {success: false} for a missing or expired token; the two error texts
            // for a valid token whose session is gone (key rotation, logout elsewhere)
            String error = o.optString("error");
            if ((o.has("success") && !o.optBoolean("success", true))
                    || "Authentication failed. Key not found".equals(error)
                    || "Invalid token payload".equals(error)) {
                return new Reply(null, Error.AUTH);
            }
            if ("already_claimed".equals(error)) return new Reply(null, Error.ALREADY_CLAIMED);
        } catch (Exception ignored) {
            // not JSON (proxy page etc.)
        }
        return new Reply(null, Error.FAILED);
    }

    /** A failed HTTP status: 503 is the bot's "temporarily disabled" (securityDisabled). */
    static Error classifyHttp(int status) {
        return status == HTTP_UNAVAILABLE ? Error.DISABLED : Error.FAILED;
    }

    /** Today's paid amounts by tier from an /adRewardsToday reply; empty when none or unreadable. */
    static Map<Integer, String> parseToday(String body) {
        Map<Integer, String> out = new HashMap<>();
        try {
            JSONObject o = new JSONObject(body);
            JSONObject rewards = "success".equals(o.optString("status")) ? o.optJSONObject("rewards") : null;
            if (rewards == null) return out;
            for (Iterator<String> it = rewards.keys(); it.hasNext(); ) {
                String key = it.next();
                try {
                    int tier = Integer.parseInt(key);
                    double amount = rewards.getDouble(key);
                    if (tier >= 1 && tier <= 4 && amount > 0 && !Double.isInfinite(amount)) {
                        out.put(tier, formatAmount(amount));
                    }
                } catch (Exception ignored) {
                    // one unreadable entry doesn't hide the others
                }
            }
        } catch (Exception ignored) {
            // not JSON: nothing paid as far as we can tell
        }
        return out;
    }

    /** At most 3 decimals (the bot floors to 3), no trailing zeros: 3.1 → "3.1", 2.0 → "2". */
    static String formatAmount(double amount) {
        return BigDecimal.valueOf(amount).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    static void request(Context ctx, String user, int tier, Callback cb) {
        get(ctx.getApplicationContext(), user,
                "adRewardIntent?user=" + android.net.Uri.encode(user) + "&tier=" + tier, new BodyCallback() {
                    @Override
                    public void onBody(String body) {
                        Reply r = parse(body);
                        if (r.error != null) cb.onError(r.error);
                        else cb.onReady(r.id);
                    }

                    @Override
                    public void onError(Error error) {
                        cb.onError(error);
                    }
                });
    }

    static void today(Context ctx, String user, AmountsCallback cb) {
        get(ctx.getApplicationContext(), user, "adRewardsToday?user=" + android.net.Uri.encode(user),
                new BodyCallback() {
                    @Override
                    public void onBody(String body) {
                        cb.onAmounts(parseToday(body));
                    }

                    @Override
                    public void onError(Error error) {
                        cb.onAmounts(error == Error.AUTH ? null : new HashMap<>());
                    }
                });
    }

    /** A GET with the session token; an expired token is refreshed once and the call retried. */
    private static void get(Context app, String user, String path, BodyCallback cb) {
        String token = LoginActivity.accessToken;
        if (token == null || token.isEmpty()) {
            refreshThenSend(app, user, path, cb);
        } else {
            send(app, user, path, true, cb);
        }
    }

    private static void refreshThenSend(Context app, String user, String path, BodyCallback cb) {
        BadgesApi.refreshToken(app, user, ok -> {
            if (ok) send(app, user, path, false, cb);
            else cb.onError(Error.AUTH);
        });
    }

    private static void send(Context app, String user, String path, boolean mayRefresh, BodyCallback cb) {
        final String header = app.getString(R.string.validation_header);
        final String headerValue = app.getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken;
        StringRequest req = new StringRequest(Request.Method.GET, Utils.apiUrl(app) + path,
                body -> {
                    if (mayRefresh && parse(body).error == Error.AUTH) {
                        refreshThenSend(app, user, path, cb);
                    } else {
                        cb.onBody(body);
                    }
                },
                err -> cb.onError(err.networkResponse != null
                        ? classifyHttp(err.networkResponse.statusCode) : Error.FAILED)) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put(header, headerValue);
                return h;
            }
        };
        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, 1f));
        req.setShouldCache(false);
        BadgesApi.claimQueue(app).add(req);
    }
}
