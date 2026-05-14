package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HiveEngineAPI {

    private static final String TAG = "HiveEngineAPI";

    private static RequestQueue requestQueue;

    private static final String[] HE_NODES = {
        "https://herpc.actifit.io/",
        "https://herpc.dtools.dev/",
        "https://api.primersion.com/",
        "https://engine.rishipanthee.com/"
    };

    private static int currentNodeIndex = 0;

    public HiveEngineAPI(Context ctx) {
        requestQueue = Volley.newRequestQueue(ctx);
    }

    public static void fetchAllTokens(VolleyCallback callback) {
        JSONObject params = new JSONObject();
        try {
            params.put("id", 1);
            params.put("jsonrpc", "2.0");
            params.put("method", "find");

            JSONObject innerParams = new JSONObject();
            innerParams.put("contract", "tokens");
            innerParams.put("table", "tokens");
            innerParams.put("query", new JSONObject());
            innerParams.put("limit", 1000);
            innerParams.put("offset", 0);

            params.put("params", innerParams);

            sendRequest(callback, params, 0);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(e.getMessage());
        }
    }

    // handles querying hive engine RPC for a user's token balances
    public static void queryHEContract(String username, VolleyCallback callback) {
        JSONObject params = new JSONObject();
        try {
            params.put("id", 1);
            params.put("jsonrpc", "2.0");
            params.put("method", "find");

            JSONObject innerParams = new JSONObject();
            innerParams.put("contract", "tokens");
            innerParams.put("table", "balances");

            JSONObject query = new JSONObject();
            query.put("account", username);  // use the passed username, not a static field

            innerParams.put("query", query);
            innerParams.put("limit", 1000);
            innerParams.put("offset", 0);

            params.put("params", innerParams);

            sendRequest(callback, params, 0);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(e.getMessage());
        }
    }

    // Tries each HE node in order; retryCount tracks how many nodes have been attempted
    private static void sendRequest(VolleyCallback callback, JSONObject params, int retryCount) {
        String url = HE_NODES[currentNodeIndex % HE_NODES.length];

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, params,
                response -> {
                    try {
                        callback.onSuccess(response.getJSONArray("result"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                        callback.onFailure("Invalid response from " + url);
                    }
                },
                error -> {
                    Log.w(TAG, "HE node failed: " + url + " — " + error.getMessage());
                    if (retryCount + 1 < HE_NODES.length) {
                        // Try next node
                        currentNodeIndex = (currentNodeIndex + 1) % HE_NODES.length;
                        sendRequest(callback, params, retryCount + 1);
                    } else {
                        // All nodes exhausted
                        currentNodeIndex = 0;
                        callback.onFailure("All HE nodes failed");
                    }
                });

        requestQueue.add(request);
    }

    public interface VolleyCallback {
        void onSuccess(JSONArray result);
        void onFailure(String error);
    }
}
