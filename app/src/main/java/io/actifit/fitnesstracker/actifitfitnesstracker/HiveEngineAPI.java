package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class HiveEngineAPI {

    //private static final String BASE_URL = "https://herpc.actifit.io/"; // Replace with your base URL
                                            //"https://api.primersion.com/";//
    private static RequestQueue requestQueue;

    final String CONTRACTAPI = "contracts";

    private static String URL = "https://herpc.actifit.io/contracts";


    private int id = 1;

    public HiveEngineAPI(Context ctx) {
        this.requestQueue = Volley.newRequestQueue(ctx);
    }

    public static void fetchAllTokens(VolleyCallback callback) {
        JSONObject params = new JSONObject();
        try {
            params.put("id", 1);
            params.put("jsonrpc", "2.0");
            params.put("method", "find");

            //fetches prices
            /*
            JSONObject innerParams = new JSONObject();
            innerParams.put("contract", "market");
            innerParams.put("table", "metrics");
             */

            JSONObject innerParams = new JSONObject();
            innerParams.put("contract", "tokens");
            innerParams.put("table", "tokens");

            JSONObject query = new JSONObject();
            //query.put("account", MainActivity.username);
            innerParams.put("query", query);
            //innerParams.put("limit", 1000);
            //innerParams.put("offset", 0);

            params.put("params", innerParams);

            System.out.println(">>>>>>>>>HE query ready");
            System.out.println(params.toString());

            sendRequest(callback, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendRequest(VolleyCallback callback, JSONObject params){
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, URL, params,
                response -> {
                    // Handle the response here
                    //System.out.println(">>>>>>>>>response");
                    //System.out.println(response.toString());
                    try {
                        callback.onSuccess(response.getJSONArray("result"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // Handle the error here
                //System.out.println(">>>>>>>>>error");
                //System.out.println(error.getMessage());
                callback.onFailure(error.getMessage());
            }
        });

        requestQueue.add(jsonObjectRequest);
    }


    //handles querying hive engine RPC
    public static void queryHEContract(String username, VolleyCallback callback) {

        JSONObject params = new JSONObject();
        try {
            params.put("id", 1);
            params.put("jsonrpc", "2.0");
            params.put("method", "find");

            //fetches prices
            /*
            JSONObject innerParams = new JSONObject();
            innerParams.put("contract", "market");
            innerParams.put("table", "metrics");
             */

            JSONObject innerParams = new JSONObject();
            innerParams.put("contract", "tokens");
            innerParams.put("table", "balances");

            JSONObject query = new JSONObject();
            query.put("account", MainActivity.username);


            innerParams.put("query", query);
            innerParams.put("limit", 1000);
            innerParams.put("offset", 0);

            params.put("params", innerParams);

            System.out.println(">>>>>>>>>HE query ready");
            System.out.println(params.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }

        sendRequest(callback, params);
    }

    public interface VolleyCallback {
        void onSuccess(JSONArray result);
        void onFailure(String error);
    }
}
