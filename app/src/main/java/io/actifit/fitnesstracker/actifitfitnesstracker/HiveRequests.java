package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.actifit.fitnesstracker.actifitfitnesstracker.MainActivity.TAG;

public class HiveRequests {

    public String hiveRPCUrl;
    private Context ctx;
    private RequestQueue queue;
    private JSONObject options;

    public HiveRequests(Context ctx) {
        this.ctx = ctx;
        //default RPC node
        hiveRPCUrl = ctx.getString(R.string.hive_default_node);
        // Instantiate the RequestQueue.
        queue = Volley.newRequestQueue(ctx);
    }
    
    public void setOptions(JSONObject options){
        this.options = options;
        if (options.has("setRPC")){
            try {
                this.hiveRPCUrl = options.getString("setRPC");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public JSONArray getComments(JSONObject params) {
        JSONArray outcome = new JSONArray();
        CompletableFuture<JSONArray> future = this.processRequest(ctx.getString(R.string.get_post_comments), params);
        try {
            JSONArray result = future.join(); // Waits for the future to complete and returns the result
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return outcome;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public JSONArray getRankedPosts(JSONObject params){
        JSONArray outcome = new JSONArray();
        CompletableFuture<JSONArray> future = this.processRequest(ctx.getString(R.string.get_ranked_posts), params);
        try {
            JSONArray result = future.join(); // Waits for the future to complete and returns the result
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return outcome;
        }
            /*future.thenAccept(new Consumer<JSONObject>() {
                @Override
                public void accept(JSONObject result) {
                    try {
                        System.out.println("hello");
                        JSONArray postArray = result.getJSONArray("result");
                        return postArray;
                        //return (JSONArray)result;

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            future.thenApply(result -> {
                // Perform the desired action and return a value
                System.out.println("Result: " + result);
                return result;
            }).exceptionally(error -> {
                // Handle the error response here
                System.out.println(error.getMessage());
                Log.d(TAG, ">>>test:" + error.getMessage());
                return null;
            });*/
            //return outcome;
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    public CompletableFuture<JSONArray> processRequest(String method, JSONObject params) {
        CompletableFuture<JSONArray> future = new CompletableFuture<>();
        JsonObjectRequest request;
        try {
            // Request the transactions of the user first via JsonArrayRequest
            // according to our data format
            request = new JsonObjectRequest(Request.Method.POST,
                    hiveRPCUrl, null, response -> {
                try {
                    JSONArray postArray = response.getJSONArray("result");
                    future.complete(postArray);
                    //future.complete(response);
                    //return future;
                } catch (JSONException e) {
                //} catch (Exception e) {
                    e.printStackTrace();
                }
            },error -> {
                    error.printStackTrace();
                    future.completeExceptionally(error);
            }){
                @Override
                public byte[] getBody() {
                    return prepareJSONReq(method, params);
                }
                @Override
                public String getBodyContentType() {
                    return "application/json";
                }

            };

            //transactionRequest.setRetryPolicy(new DefaultRetryPolicy(15000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

            // Add transaction request to be processed
            queue.add(request);

        }catch(Exception exception){
            exception.printStackTrace();
            future.completeExceptionally(exception);
        }
        //return future;
        return future;
    }

    private byte[] prepareJSONReq(String method, JSONObject params){
        JSONObject jsonRequest = new JSONObject();
        try {
            jsonRequest.put("jsonrpc", "2.0");

            jsonRequest.put("method", method);
            jsonRequest.put("params", params);
            jsonRequest.put("id", 1);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonRequest.toString().getBytes();
    }
}
