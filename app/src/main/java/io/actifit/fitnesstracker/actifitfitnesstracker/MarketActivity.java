package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.ListView;

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

public class MarketActivity extends BaseActivity {

    private ListView marketView;
    private ArrayList<SingleProductModel> productList;
    private ProgressDialog progress;
    private ProductAdapter productAdapter;
    private JSONArray consumedProducts;
    private JSONArray nonConsumedProducts;
    public static Double afitBalance = 0.0;

    JSONObject afitPrice;
    RequestQueue queue = null;

    @Override
    protected void onResume() {
        super.onResume();
        if (queue==null){
            queue = Volley.newRequestQueue(this);
        }
        loadBalance(queue);
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
                        if (progress != null && progress.isShowing())
                            progress.hide();
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
        setContentView(R.layout.market_page);

        marketView = findViewById(R.id.productList);
        productList = new ArrayList<SingleProductModel>();

        // Instantiate the RequestQueue.
        queue = Volley.newRequestQueue(this);


        //load AFIT price
        //grab AFIT price to calculate HIVE price
        String afitPriceUrl = getString(R.string.live_server)
                + getString(R.string.afit_price_he);

        //grab auth token for logged in user
        JsonObjectRequest afitPriceReq = new JsonObjectRequest(Request.Method.GET,
                afitPriceUrl, null,
                new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        //store token for reuse when saving settings
                        Log.d(MainActivity.TAG, response.toString());
                        afitPrice = response;
                        proceedLoading();
                    }

                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // error
                        Log.e(MainActivity.TAG, "AFIT price load error");
                        proceedLoading();
                    }
                });

        queue.add(afitPriceReq);


    }

    private void proceedLoading(){

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

            //load non-consumed products
            String nonConsumedProductsUrl = getString(R.string.non_consumed_gadgets_link) + MainActivity.username;
            JsonArrayRequest nonConsumedProductsReq = new JsonArrayRequest(Request.Method.GET,
                    nonConsumedProductsUrl, null, new Response.Listener<JSONArray>() {

                @Override
                public void onResponse(JSONArray productListArray) {

                    // Handle the result
                    try {
                        nonConsumedProducts = productListArray;
                        // Create the adapter to convert the array to views
                        //actifitTransactions.setText("Response is: "+ response);
                    } catch (Exception e) {
                        //hide dialog
                        //progress.hide();
                        //actifitTransactionsError.setVisibility(View.VISIBLE);
                        e.printStackTrace();
                    }

                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    //hide dialog
                    //progress.hide();
                    //actifitTransactionsView.setText("Unable to fetch balance");
                    //actifitTransactionsError.setVisibility(View.VISIBLE);
                }
            });


            queue.add(nonConsumedProductsReq);


            //load consumed products
            String consumedProductsUrl = getString(R.string.consumed_gadgets_link) + MainActivity.username;
            JsonArrayRequest consumedProductsReq = new JsonArrayRequest(Request.Method.GET,
                    consumedProductsUrl, null, new Response.Listener<JSONArray>() {

                @Override
                public void onResponse(JSONArray productListArray) {

                    // Handle the result
                    try {
                        consumedProducts = productListArray;
                        // Create the adapter to convert the array to views
                        //actifitTransactions.setText("Response is: "+ response);
                    } catch (Exception e) {
                        //hide dialog
                        //progress.hide();
                        //actifitTransactionsError.setVisibility(View.VISIBLE);
                        e.printStackTrace();
                    }

                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    //hide dialog
                    //progress.hide();
                    //actifitTransactionsView.setText("Unable to fetch balance");
                    //actifitTransactionsError.setVisibility(View.VISIBLE);
                }
            });


            queue.add(consumedProductsReq);
        }

        //connect to our products API
        String productsUrl = getString(R.string.products_link);

        progress = new ProgressDialog(this);

        progress.setMessage(getString(R.string.loading_products));
        progress.show();


        // Request the transactions of the user first via JsonArrayRequest
        // according to our data format
        JsonArrayRequest transactionRequest = new JsonArrayRequest(Request.Method.GET,
                productsUrl, null, new Response.Listener<JSONArray>(){

            @Override
            public void onResponse(JSONArray productListArray) {

                // Handle the result
                try {

                    //grab user rank
                    String userRank = sharedPreferences.getString("userRank", "0");

                    for (int i = 0; i < productListArray.length(); i++) {
                        // Retrieve each JSON object within the JSON array
                        JSONObject jsonObject = productListArray.getJSONObject(i);

                        SingleProductModel postEntry = new SingleProductModel(jsonObject, afitPrice);

                        postEntry.nonConsumedCopy = SingleProductModel.NOCOPY;
                        int totalBought = 0;

                        //intialize remainingboosts
                        postEntry.remainingBoosts = postEntry.validityVal;

                        if (nonConsumedProducts!=null) {

                            //check if we have a bought or active version of this product already to flag it accordingly
                            for (int ii = 0; ii < nonConsumedProducts.length(); ii++) {
                                JSONObject nonConsumedEntry = nonConsumedProducts.getJSONObject(ii);
                                if (nonConsumedEntry.get("gadget").equals(postEntry.id)) {
                                    //match found, let's flag accordingly as bought or active
                                    if (postEntry.nonConsumedCopy == SingleProductModel.NOCOPY) {
                                        postEntry.nonConsumedCopy = (nonConsumedEntry.get("status").equals("active") ? SingleProductModel.ACTIVECOPY : SingleProductModel.BOUGHTCOPY);
                                        JSONArray postsConsumed = nonConsumedEntry.getJSONArray("posts_consumed");
                                        if (postsConsumed != null && postsConsumed.length() > 0) {
                                            int span = nonConsumedEntry.getInt("span");
                                            int consumed = postsConsumed.length();
                                            postEntry.remainingBoosts -= consumed;
                                        }
                                    }
                                    //break;
                                    totalBought += 1;
                                }
                            }
                        }
                        postEntry.totalBought = totalBought;

                        int consumed_cnt = 0;

                        if (consumedProducts!=null) {
                            for (int k = 0; k < consumedProducts.length(); k++) {
                                // Retrieve each JSON object within the JSON array
                                JSONObject jsObject = consumedProducts.getJSONObject(k);
                                //validate this is a matching consumed gadget
                                if (postEntry.id.equals(jsObject.getString("gadget"))) {
                                    consumed_cnt += 1;
                                }
                            }
                        }
                        postEntry.totalConsumed = consumed_cnt;

                        //only go through item if its active and is a game
                        if (postEntry.active && postEntry.type.equals(getString(R.string.gameGadget))) {
                            //by default, set as true. Turn false if any does not meet
                            postEntry.allReqtsMet = true;
                            if (postEntry.requirements != null) {
                                for (int j = 0; j < postEntry.requirements.length(); j++) {
                                    JSONObject reqEntry = postEntry.requirements.getJSONObject(j);


                                    //fill in requirements status
                                    if (reqEntry.has("item")) {
                                        if (reqEntry.getString("item").equals("User Rank")) {
                                            if (Double.parseDouble(userRank) >= Integer.parseInt(reqEntry.getString("level"))) {
                                                reqEntry.put("reqtMet", true);
                                            } else {
                                                reqEntry.put("reqtMet", false);
                                                postEntry.allReqtsMet = false;
                                            }
                                        } else if (reqEntry.has("AFIT")) {
                                            if (MainActivity.userFullBalance >= Integer.parseInt(reqEntry.getString("count"))) {
                                                reqEntry.put("reqtMet", true);
                                            } else {
                                                reqEntry.put("reqtMet", false);
                                                postEntry.allReqtsMet = false;
                                            }
                                        } else {
                                            //check if requirement is met
                                            int consumed_count = 0;
                                            if (consumedProducts!=null) {
                                                for (int k = 0; k < consumedProducts.length(); k++) {
                                                    // Retrieve each JSON object within the JSON array
                                                    JSONObject jsObject = consumedProducts.getJSONObject(k);
                                                    //validate this is a matching consumed gadget
                                                    if (reqEntry.getString("item").equals(jsObject.getString("gadget_name"))
                                                            && reqEntry.getString("level").equals(jsObject.getString("gadget_level"))) {
                                                        consumed_count += 1;
                                                    }
                                                }
                                            }
                                            //postEntry.totalConsumed = consumed_count;
                                            int consumed_target = Integer.parseInt(reqEntry.getString("count"));
                                            if (consumed_count >= consumed_target) {
                                                reqEntry.put("reqtMet", true);
                                            } else {
                                                reqEntry.put("reqtMet", false);
                                                postEntry.allReqtsMet = false;
                                            }
                                        }
                                    }
                                }
                            }

                            //check if product has beneficiary (friend) recipient
                            for (int j=0;j< postEntry.boosts.length();j++) {
                                JSONObject reqEntry = postEntry.boosts.getJSONObject(j);

                                if (reqEntry.getString("boost_beneficiary").equals("friend")) {
                                    //flag it as friend rewarding
                                    postEntry.isFriendRewarding = true;
                                }
                            }

                            //check whether product is purchasable or not


                            //only append product if it is active and is a gaming product
                            //if (postEntry.active && postEntry.type.equals(getString(R.string.gameGadget))) {
                            //skip products that are event specific
                            if (!postEntry.specialevent) {
                                productList.add(postEntry);
                            }
                        }
                    }

                    Collections.sort(productList);
                    // Create the adapter to convert the array to views
                    String pkey = sharedPreferences.getString("actifitPst", "");
                    productAdapter = new ProductAdapter(getApplicationContext(), productList,
                            MainActivity.username, pkey);

                    marketView.setAdapter(productAdapter);

                    //hide dialog
                    progress.hide();
                    //actifitTransactions.setText("Response is: "+ response);

                }catch(JSONException jsex){
                    jsex.printStackTrace();
                }catch (Exception e) {
                    //hide dialog
                    progress.hide();
                    //actifitTransactionsError.setVisibility(View.VISIBLE);
                    e.printStackTrace();
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //hide dialog
                progress.hide();
                //actifitTransactionsView.setText("Unable to fetch balance");
                //actifitTransactionsError.setVisibility(View.VISIBLE);
            }
        });


        //transactionRequest.setRetryPolicy(new DefaultRetryPolicy(15000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        // Add transaction request to be processed
        queue.add(transactionRequest);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if ( progress!=null){
            progress.dismiss();
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if ( progress!=null){
            progress.dismiss();
        }
    }

}
