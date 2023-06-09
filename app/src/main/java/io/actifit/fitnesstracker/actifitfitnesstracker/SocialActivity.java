package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;

public class SocialActivity extends BaseActivity {

    private ListView socialView;
    private ArrayList<SingleHivePostModel> posts;
    private ProgressBar progress;
    //private ProgressDialog progress;
    private PostAdapter postAdapter;

    public static Double afitBalance = 0.0;

    JSONObject afitPrice;
    RequestQueue queue = null;

    @Override
    protected void onResume() {
        super.onResume();
        if (queue==null){
            queue = Volley.newRequestQueue(this);
        }
        progress.setVisibility(View.VISIBLE);
        //progress.show();
        //loadBalance(queue);
    }

    private void loadBalance(RequestQueue queue){
        //fetch user balance on actifit.io
        if (MainActivity.username.equals("") || MainActivity.username.length() <1){
            return;
        }
        String balanceUrl = getString(R.string.user_balance_api_url)+MainActivity.username;

        //display header
        //actifitBalanceLbl.setVisibility(View.VISIBLE);
        // Request the balance of the user while expecting a JSON response
        JsonObjectRequest balanceRequest = new JsonObjectRequest
                (Request.Method.GET, balanceUrl, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        //hide dialog
                        progress.setVisibility(View.GONE);
                        //progress.hide();
                        // Display the result
                        try {
                            //grab current token count
                            afitBalance = Double.parseDouble(response.getString("tokens"));
                        }catch(JSONException e){
                            //hide dialog
                            Log.e(MainActivity.TAG, "AFIT balance load error");
                        }
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        //hide dialog
                        Log.e(MainActivity.TAG, "AFIT balance load error");
                    }
                });

        // Add balance request to be processed
        queue.add(balanceRequest);
    }

        @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.social_page);

        socialView = findViewById(R.id.postList);

        progress = findViewById(R.id.loader);

        posts = new ArrayList<SingleHivePostModel>();

        // Instantiate the RequestQueue.
        queue = Volley.newRequestQueue(this);

        final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

        //refresh user login
        if (!MainActivity.username.equals("")) {
            String pkey = sharedPreferences.getString("actifitPst", "");

            //authorize user login based on credentials if user is already verified
            if (!pkey.equals("")) {
                String loginAuthUrl = getString(R.string.live_server)
                        + getString(R.string.login_auth);


                JSONObject loginSettings = new JSONObject();
                try {
                    loginSettings.put(getString(R.string.username_param), MainActivity.username);
                    loginSettings.put(getString(R.string.pkey_param), pkey);
                    loginSettings.put(getString(R.string.bchain_param), "HIVE");//default always HIVE
                    loginSettings.put(getString(R.string.keeploggedin_param), false);//TODO make dynamic
                    loginSettings.put(getString(R.string.login_source), getString(R.string.android) + BuildConfig.VERSION_NAME);
                } catch (JSONException e) {
                    //Log.e(MainActivity.TAG, e.getMessage());
                }

                //grab auth token for logged in user
                JsonObjectRequest loginRequest = new JsonObjectRequest(Request.Method.POST,
                        loginAuthUrl, loginSettings,
                        new Response.Listener<JSONObject>() {

                            @Override
                            public void onResponse(JSONObject response) {
                                //store token for reuse when saving settings
                                try {
                                    if (response.has("success")) {
                                        Log.d(MainActivity.TAG, response.toString());
                                        LoginActivity.accessToken = response.getString(getString(R.string.login_token));
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }

                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                // error
                                Log.e(MainActivity.TAG, "Login error");
                            }
                        });

                queue.add(loginRequest);
            }


            loadBalance(queue);


        }

        //fetch posts directly from chain
        String hiveRPCUrl = getString(R.string.hive_default_node);
        String productsCall = getString(R.string.get_ranked_posts);

        String sort = "created";
        String tag = getString(R.string.actifit_community);

        String start_author = "";
        String start_permlink = "";
        JSONObject jsonRequest = new JSONObject();

        try {


            jsonRequest.put("jsonrpc", "2.0");
            jsonRequest.put("method", productsCall);
            JSONObject params = new JSONObject();


            params.put("sort", sort);
            params.put("tag", tag);
            params.put("start_author",start_author);
            params.put("start_permlink",start_permlink);
            jsonRequest.put("params", params);
            jsonRequest.put("id", 1);

        } catch (JSONException e) {
            e.printStackTrace();
        }


        //progress = new ProgressBar(this);
            // progress = new ProgressDialog(this);
        progress.setVisibility(View.VISIBLE);
            //progress.show();

        // progress.setMessage(getString(R.string.loading));
        //progress.show();


        // Request the transactions of the user first via JsonArrayRequest
        // according to our data format
        JsonObjectRequest postsRequest = new JsonObjectRequest(Request.Method.POST,
                hiveRPCUrl, null, postArray -> {

                    // Handle the result
                    try {

                        //grab array from result
                        JSONArray result = postArray.getJSONArray("result");

                        for (int i = 0; i < result.length(); i++) {
                            // Retrieve each JSON object within the JSON array
                            //JSONObject jsonObject = new JSONObject()

                            SingleHivePostModel postEntry = new SingleHivePostModel((result.getJSONObject(i)));

                            //append post
                            posts.add(postEntry);

                        }

                        //Collections.sort(posts);
                        // Create the adapter to convert the array to views
                        String pkey = sharedPreferences.getString("actifitPst", "");
                        postAdapter = new PostAdapter(getApplicationContext(), posts,
                                MainActivity.username, pkey);

                        socialView.setAdapter(postAdapter);

                        //hide dialog
                        //progress.setVisibility(View.GONE);
                        //progress.hide();
                        //actifitTransactions.setText("Response is: "+ response);


                    }catch (Exception e) {
                        //hide dialog
                        progress.setVisibility(View.GONE);
                        //progress.hide();
                        //actifitTransactionsError.setVisibility(View.VISIBLE);
                        e.printStackTrace();
                    }

                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //hide dialog
                progress.setVisibility(View.GONE);
                //progress.hide();
                //actifitTransactionsView.setText("Unable to fetch balance");
                //actifitTransactionsError.setVisibility(View.VISIBLE);
            }
        }){
            @Override
            public byte[] getBody() {
                JSONObject jsonRequest = new JSONObject();
                try {
                    jsonRequest.put("jsonrpc", "2.0");
                    jsonRequest.put("method", productsCall);

                    JSONObject params = new JSONObject();

                    params.put("sort", sort);
                    params.put("tag", tag);
                    params.put("start_author",start_author);
                    params.put("start_permlink",start_permlink);

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

    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if ( progress!=null){
            progress.setVisibility(View.GONE);
            //progress.hide();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        progress.setVisibility(View.VISIBLE);
        //progress.show();
        // The activity is about to become visible to the user
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if ( progress!=null){
            progress.setVisibility(View.GONE);
            //progress.hide();
        }
    }

}
