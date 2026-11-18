package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A one-time rewarded-ad id from the bot (/adRewardIntent). The ad's server-side verification
 * data then carries only "r1_&lt;id&gt;": the step tiers unlock on Health Connect steps, so the
 * ad network must not see the username or the tier. The bot also picks the amount.
 */
final class AdRewardApi {

    private AdRewardApi() {
    }

    enum Error { DISABLED, ALREADY_CLAIMED, AUTH, FAILED }

    interface Callback {
        void onReady(String id, double amount);

        void onError(Error error);
    }

    static final class Reply {
        final String id;
        final double amount;
        final Error error;

        private Reply(String id, double amount, Error error) {
            this.id = id;
            this.amount = amount;
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
                return new Reply(id, o.optDouble("amount", 0), null);
            }
            // checkHdrs refuses a missing or expired token with {success: false}
            if (o.has("success") && !o.optBoolean("success", true)) return new Reply(null, 0, Error.AUTH);
            if ("already_claimed".equals(o.optString("error"))) return new Reply(null, 0, Error.ALREADY_CLAIMED);
        } catch (Exception ignored) {
            // not JSON (proxy page etc.)
        }
        return new Reply(null, 0, Error.FAILED);
    }

    static void request(Context ctx, String user, int tier, Callback cb) {
        final Context app = ctx.getApplicationContext();
        String token = LoginActivity.accessToken;
        if (token == null || token.isEmpty()) {
            refreshThenSend(app, user, tier, cb);
        } else {
            send(app, user, tier, true, cb);
        }
    }

    private static void refreshThenSend(Context app, String user, int tier, Callback cb) {
        BadgesApi.refreshToken(app, user, ok -> {
            if (ok) send(app, user, tier, false, cb);
            else cb.onError(Error.AUTH);
        });
    }

    private static void send(Context app, String user, int tier, boolean mayRefresh, Callback cb) {
        final String header = app.getString(R.string.validation_header);
        final String headerValue = app.getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken;
        String url = Utils.apiUrl(app) + "adRewardIntent?user=" + android.net.Uri.encode(user) + "&tier=" + tier;
        StringRequest req = new StringRequest(Request.Method.GET, url,
                body -> {
                    Reply r = parse(body);
                    if (r.error == Error.AUTH && mayRefresh) {
                        refreshThenSend(app, user, tier, cb);
                    } else if (r.error != null) {
                        cb.onError(r.error);
                    } else {
                        cb.onReady(r.id, r.amount);
                    }
                },
                err -> cb.onError(err.networkResponse != null
                        && err.networkResponse.statusCode == HTTP_UNAVAILABLE ? Error.DISABLED : Error.FAILED)) {
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
