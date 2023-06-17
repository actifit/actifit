package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.CompletableFuture;

public class HiveRequests {

    public String hiveRPCUrl;
    private Context ctx;
    private RequestQueue queue;

    public HiveRequests(Context ctx) {
         hiveRPCUrl = ctx.getString(R.string.hive_default_node);
         this.ctx = ctx;
        // Instantiate the RequestQueue.
        queue = Volley.newRequestQueue(ctx);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public CompletableFuture<JSONArray> getRankedPosts(JSONObject params) {
        CompletableFuture<JSONArray> future = new CompletableFuture<>();
        JsonObjectRequest postsRequest;
        try {
            JSONObject jsonRequest = new JSONObject();
            try {
                jsonRequest.put("jsonrpc", "2.0");

                jsonRequest.put("method", ctx.getString(R.string.get_ranked_posts));
                jsonRequest.put("params", params);
                jsonRequest.put("id", 1);

            } catch (JSONException e) {
                e.printStackTrace();
            }


            // Request the transactions of the user first via JsonArrayRequest
            // according to our data format
            postsRequest = new JsonObjectRequest(Request.Method.POST,
                    hiveRPCUrl, null, response -> {
                try {
                    JSONArray postArray = response.getJSONArray("result");
                    future.complete(postArray);
                    //return future;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            },error -> {
                    error.printStackTrace();
                    future.completeExceptionally(error);
            }){
                @Override
                public byte[] getBody() {
                    JSONObject jsonRequest = new JSONObject();
                    try {
                        jsonRequest.put("jsonrpc", "2.0");

                        jsonRequest.put("method", ctx.getString(R.string.get_ranked_posts));
                        jsonRequest.put("params", params);
                        jsonRequest.put("id", 1);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    return jsonRequest.toString().getBytes();
                }
                @Override
                public String getBodyContentType() {
                    return "application/json";
                }

            };

            //transactionRequest.setRetryPolicy(new DefaultRetryPolicy(15000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

            // Add transaction request to be processed
            queue.add(postsRequest);

        }catch(Exception exception){
            exception.printStackTrace();
            future.completeExceptionally(exception);
        }
        //return future;
        return future;

    }
}
