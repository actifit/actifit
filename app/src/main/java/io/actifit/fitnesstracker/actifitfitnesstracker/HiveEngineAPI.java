package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HiveEngineAPI {

    private static final String TAG = "HiveEngineAPI";
    private static final String URL = "https://herpc.actifit.io/contracts";

    private static RequestQueue requestQueue;

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

            params.put("params", innerParams);

            sendRequest(callback, params);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(e.getMessage());
        }
    }

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
            query.put("account", username);

            innerParams.put("query", query);
            innerParams.put("limit", 1000);
            innerParams.put("offset", 0);

            params.put("params", innerParams);

            sendRequest(callback, params);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(e.getMessage());
        }
    }

    private static void sendRequest(VolleyCallback callback, JSONObject params) {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, URL, params,
                response -> {
                    try {
                        callback.onSuccess(response.getJSONArray("result"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                        callback.onFailure("Invalid response: " + e.getMessage());
                    }
                },
                error -> {
                    Log.w(TAG, "HE request failed: " + error.getMessage());
                    callback.onFailure(error.getMessage());
                });

        requestQueue.add(request);
    }

    public interface VolleyCallback {
        void onSuccess(JSONArray result);
        void onFailure(String error);
    }
}
