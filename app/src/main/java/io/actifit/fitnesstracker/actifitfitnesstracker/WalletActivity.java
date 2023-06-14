package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.browser.customtabs.CustomTabsIntent;

import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
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
import org.w3c.dom.Text;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WalletActivity extends BaseActivity {

    private ProgressDialog progress;
    private JSONObject hiveChainInfo;
    private JSONObject steemChainInfo;
    private JSONObject blurtChainInfo;

    private Double blurtPrice = 0.02;
    private AlertDialog.Builder pendingRewardsDialogBuilder;
    private AlertDialog pendingRewardsDialog;
    private JSONObject innerRewards = new JSONObject();
    private String username;
    private String accessToken;

    TextView BtnCheckBalance, loadPendingRewards, claimRewards;

    RotateAnimation rotate;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        //define standard rotate animation

        rotate = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(2000);
        rotate.setInterpolator(new LinearInterpolator());
        rotate.setRepeatCount(Animation.INFINITE);

        //grab links to layout items for later use
        //final TextView steemitUsername = findViewById(R.id.steemit_username);
        //Button BtnCheckBalance = findViewById(R.id.btn_get_balance);

        BtnCheckBalance = findViewById(R.id.btn_refresh_balance);

        //try to check first if we had a user defined already and saved to preferences
        //retrieving account data for simple reuse. Data is not stored anywhere outside actifit App.
        SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
       // SharedPreferences.Editor editor = sharedPreferences.edit();

        //grab stored value, if any
        username = sharedPreferences.getString("actifitUser","");
        //steemitUsername.setText(curUser);

        final Activity callerActivity = this;
        final Context callerContext = this;

        RequestQueue queue = Volley.newRequestQueue(this);

        loadPendingRewards = findViewById(R.id.btn_get_pending_rewards);

        loadPendingRewards.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                displayPendingRewards();

            }
        });

        //make sure we have a value, and if so, automatically grab it
        if (!username.equals("")) {
            //if we already have data, emulate a click to grab the info
            loadAccountBalance(username, callerActivity, callerContext);

            //fetch user global settings - server based

            String pkey = sharedPreferences.getString("actifitPst", "");

            //authorize user login based on credentials if user is already verified
            if (!pkey.equals("")) {
                String loginAuthUrl = getString(R.string.live_server)
                        + getString(R.string.login_auth);


                JSONObject loginSettings = new JSONObject();
                try {
                    loginSettings.put(getString(R.string.username_param), username);
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
                                        accessToken = response.getString(getString(R.string.login_token));
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

            claimRewards = findViewById(R.id.btn_claim_pending_rewards);

            claimRewards.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View arg0) {

                    progress = new ProgressDialog(callerContext);
                    progress.setMessage(getString(R.string.claiming_rewards));
                    progress.show();

                    claimRewards.startAnimation(rotate);

                    RequestQueue queue = Volley.newRequestQueue(callerContext);

                    //fetch blurt price
                    String claimRewardsUrl = getString(R.string.claim_rewards_url) + username;
                    final String success_notification = getString(R.string.rewards_claimed_successfully);
                    final String error_notification = getString(R.string.rewards_claim_error);

                    // Process claim rewards request
                    JsonObjectRequest claimRewardsReq = new JsonObjectRequest
                            (Request.Method.GET, claimRewardsUrl, null, new Response.Listener<JSONObject>() {
                                JSONObject hiveClaim;
                                @Override
                                public void onResponse(JSONObject response) {

                                    // Display the result
                                    try {
                                        //hive is main claim indicator
                                        hiveClaim = response.getJSONObject("hive");
                                        if (hiveClaim.has("success")) {
                                            displayNotification(success_notification, null, callerContext, callerActivity, false);

                                            //update all balances after 5 seconds
                                            new android.os.Handler().postDelayed(
                                                    new Runnable() {
                                                        public void run() {
                                                            loadAccountBalance(username, callerActivity, callerContext);
                                                        }
                                                    }, 5000);
                                        } else if (!hiveClaim.getString("error").equals("")) {
                                            displayNotification(hiveClaim.getString("error"), null, callerContext, callerActivity, false);
                                        } else {
                                            displayNotification(error_notification, null, callerContext, callerActivity, false);
                                        }
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                        displayNotification(error_notification, null, callerContext, callerActivity, false);
                                    }

                                    if (progress!=null &&  progress.isShowing()) {
                                        progress.dismiss();
                                    }
                                    claimRewards.clearAnimation();
                                }
                            }, new Response.ErrorListener() {

                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    //hide dialog
                                    //error.printStackTrace();
                                    Log.e(MainActivity.TAG, "error claiming rewards");
                                    displayNotification(error_notification, null, callerContext, callerActivity, false);
                                    claimRewards.clearAnimation();
                                }
                            }) {
                                @NonNull

                                @Override
                                public Map<String, String> getHeaders() throws AuthFailureError {
                                    final Map<String, String> params = new HashMap<>();
                                    params.put("Content-Type", "application/json");
                                    params.put(getString(R.string.validation_header), getString(R.string.validation_pre_data) + " " + accessToken);
                                    return params;
                                }
                    };

                    queue.add(claimRewardsReq);


                }
            });


            //prepare pending rewards dialog
            pendingRewardsDialogBuilder = new AlertDialog.Builder(this);
            pendingRewardsDialogBuilder.setMessage(getString(R.string.loading));
        }
        //handle activity to fetch balance
        BtnCheckBalance.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                loadAccountBalance(username, callerActivity, callerContext);

            }
        });
    }


    int queriesFetchedPendingRewards = 0;
    int totalQueryCountPendingRewards = 0;

    //handles fetching and displaying pending user rewards
    public void displayPendingRewards(){
        //grab stored value, if any
        final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
        username = sharedPreferences.getString("actifitUser","");

        final Context ctx = this;


        if (username != "") {

            queriesFetchedPendingRewards = 0;
            totalQueryCountPendingRewards = 2;

            loadPendingRewards.startAnimation(rotate);

            progress = new ProgressDialog(ctx);
            progress.setMessage(getString(R.string.fetching_pending_rewards));
            progress.show();

            //handles sending out API query requests
            RequestQueue queue = Volley.newRequestQueue(this);

            //fetch blurt price
            String blurtPriceUrl = getString(R.string.coingecko_price).replace("CURRENCY", "BLURT");

            // Request the rank of the user while expecting a JSON response
            JsonObjectRequest blurtPriceReq = new JsonObjectRequest
                    (Request.Method.GET, blurtPriceUrl, null, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {

                            // Display the result
                            try {
                                blurtPrice = response.getJSONObject("blurt").getDouble("usd");
                                //update text as dialog should already be showing in case call returns after
                                if (pendingRewardsDialog != null && pendingRewardsDialog.isShowing()){
                                    String hiveRewards = MainActivity.parseRewards(innerRewards, "HIVE", "HBD", 1.0);
                                    String steemRewards = MainActivity.parseRewards(innerRewards, "STEEM", "SBD", 1.0);
                                    String blurtRewards = MainActivity.parseRewards(innerRewards, "BLURT", "BLURT", blurtPrice);
                                    //update the text message as dialog is already showing
                                    String msg = "";

                                    msg += !hiveRewards.equals("") ? hiveRewards:"";
                                    msg += !steemRewards.equals("") ? steemRewards:"";
                                    msg += !blurtRewards.equals("") ? blurtRewards:"";

                                    pendingRewardsDialogBuilder.setMessage(Html.fromHtml(msg));

                                    if (!msg.equals("")){
                                        //pending rewards exist
                                        pendingRewardsDialogBuilder.setMessage(Html.fromHtml(msg));

                                    }/*else{
                                        Toast.makeText(ctx, getString(R.string.no_pending_rewards),Toast.LENGTH_LONG);
                                    }*/
                                }
                            } catch (JSONException jsex) {
                                jsex.printStackTrace();
                            }
                            queriesFetchedPendingRewards +=1;
                            if (queriesFetchedPendingRewards >= totalQueryCountPendingRewards){
                                loadPendingRewards.clearAnimation();
                            }
                        }
                    }, new Response.ErrorListener() {

                        @Override
                        public void onErrorResponse(VolleyError error) {
                            //hide dialog
                            //error.printStackTrace();
                            Log.e(MainActivity.TAG, "error fetching blurt price");
                            queriesFetchedPendingRewards +=1;
                            if (queriesFetchedPendingRewards >= totalQueryCountPendingRewards){
                                loadPendingRewards.clearAnimation();
                            }
                        }
                    });

            queue.add(blurtPriceReq);

            //fetch user pending rewards and display notification

            // This holds the url to connect to the API and grab the pending rewards.
            // We append to it the username
            String userPendingRewardsUrl = getString(R.string.user_pending_rewards_url) + username;

            // Request the rank of the user while expecting a JSON response
            JsonObjectRequest pendRewardsRequest = new JsonObjectRequest
                    (Request.Method.GET, userPendingRewardsUrl, null, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {

                            // Display the result
                            try {
                                innerRewards = response.getJSONObject("pendingRewards");

                                String hiveRewards = MainActivity.parseRewards(innerRewards, "HIVE", "HBD", 1.0);
                                String steemRewards = MainActivity.parseRewards(innerRewards, "STEEM", "SBD", 1.0);
                                String blurtRewards = MainActivity.parseRewards(innerRewards, "BLURT", "BLURT", blurtPrice);
                                //update the text message as dialog is already showing
                                String msg = "";

                                msg += !hiveRewards.equals("") ? hiveRewards:"";
                                msg += !steemRewards.equals("") ? steemRewards:"";
                                msg += !blurtRewards.equals("") ? blurtRewards:"";

                                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        switch (which) {
                                            case DialogInterface.BUTTON_POSITIVE:
                                                //take user to activity list on web

                                                //private void openUserRank(SharedPreferences sharedPreferences){
                                                username = sharedPreferences.getString("actifitUser", "");
                                                if (username != "") {
                                                    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

                                                    builder.setToolbarColor(getResources().getColor(R.color.actifitRed));

                                                    //animation for showing and closing fitbit authorization screen
                                                    builder.setStartAnimations(ctx, R.anim.slide_in_right, R.anim.slide_out_left);

                                                    //animation for back button clicks
                                                    builder.setExitAnimations(ctx, android.R.anim.slide_in_left,
                                                            android.R.anim.slide_out_right);

                                                    CustomTabsIntent customTabsIntent = builder.build();

                                                    customTabsIntent.launchUrl(ctx, Uri.parse(MainActivity.ACTIFIT_CORE_URL + "/" + getString(R.string.activity_url_link) + "/" + username));
                                                }
                                                //}

                                                break;

                                            case DialogInterface.BUTTON_NEGATIVE:
                                                //cancel
                                                break;
                                        }
                                    }
                                };

                                if (progress!=null &&  progress.isShowing()) {
                                    progress.dismiss();
                                }


                                if (!msg.equals("")){
                                    //pending rewards exist
                                    pendingRewardsDialog = pendingRewardsDialogBuilder.setMessage(Html.fromHtml(msg))
                                            .setTitle(getString(R.string.pending_rewards_title))
                                            .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                                            .setPositiveButton(getString(R.string.my_activity_button), dialogClickListener)
                                            .setNegativeButton(getString(R.string.close_button), dialogClickListener).create();

                                    pendingRewardsDialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
                                    pendingRewardsDialog.getWindow().getDecorView().setBackground(getDrawable(R.drawable.dialog_shape));
                                    //if (pointer.getWindow().isActive()) {
                                    pendingRewardsDialog.show();

                                }else{
                                    if (progress!=null &&  progress.isShowing()) {
                                        progress.dismiss();
                                    }
                                    Toast.makeText(ctx, getString(R.string.no_pending_rewards),Toast.LENGTH_LONG).show();
                                }



                            }catch (JSONException e) {
                                //hide dialog
                                e.printStackTrace();
                            }catch (Exception ex){
                                ex.printStackTrace();
                            }
                            if (progress!=null &&  progress.isShowing()) {
                                progress.dismiss();
                            }
                            queriesFetchedPendingRewards +=1;
                            if (queriesFetchedPendingRewards >= totalQueryCountPendingRewards){
                                loadPendingRewards.clearAnimation();
                            }
                        }
                    }, new Response.ErrorListener() {

                        @Override
                        public void onErrorResponse(VolleyError error) {
                            //hide dialog
                            //error.printStackTrace();
                            Log.e(MainActivity.TAG, "error fetching pending rewards");
                            if (progress!=null &&  progress.isShowing()) {
                                progress.dismiss();
                                Toast.makeText(ctx, getString(R.string.error_fetching_data),Toast.LENGTH_LONG).show();
                            }
                            queriesFetchedPendingRewards +=1;
                            if (queriesFetchedPendingRewards >= totalQueryCountPendingRewards){
                                loadPendingRewards.clearAnimation();
                            }
                        }
                    });

            //to enable waiting for longer time with extra retry
            pendRewardsRequest.setRetryPolicy(new DefaultRetryPolicy(
                    10000,//10 seconds default timeout
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

            queue.add(pendRewardsRequest);

        }
    }

    int queriesFetched = 0;
    int totalQueryCount = 0;
    TextView hiveBalance;
    TextView steemBalance;
    TextView blurtBalance;
    TextView sportsBalance;
    TextView actifitBalance;
    boolean chainInfoFetched = false;
    JSONObject balanceData = null;

    void loadAccountBalance(String username, Activity callerActivity, Context callerContext){

        if (!username.equals("")) {

            chainInfoFetched = false;

            queriesFetched = 0;
            totalQueryCount = 4;
            balanceData = null;

            //rotate.setRepeatMode(Animation.REVERSE);


            BtnCheckBalance.startAnimation(rotate);

            //skip on spaces, upper case, and @ symbols to properly match steem username patterns
            username = username.trim().toLowerCase().replace("@","");
            //connect to the interface to display result
            actifitBalance = findViewById(R.id.actifit_balance);
            //final TextView actifitBalanceLbl = findViewById(R.id.actifit_balance_lbl);
            final TextView actifitTransactionsLbl = findViewById(R.id.actifit_transactions_lbl);
            final ListView actifitTransactionsView = findViewById(R.id.actifit_transactions);
            final TextView actifitTransactionsError = findViewById(R.id.actifit_transactions_error);

            hiveBalance = findViewById(R.id.hive_balance);
            steemBalance = findViewById(R.id.steem_balance);
            blurtBalance = findViewById(R.id.blurt_balance);
            sportsBalance = findViewById(R.id.sports_balance);

            //hide if this is a recurring call
            actifitTransactionsError.setVisibility(View.GONE);

            //initialize progress dialog
            progress = new ProgressDialog(callerContext);

            // Instantiate the RequestQueue.
            RequestQueue queue = Volley.newRequestQueue(callerActivity);

            // This holds the url to connect to the API and grab the balance.
            // We append to it the username
            String balanceUrl = getString(R.string.user_balance_api_url)+username;

            //display header
            //actifitBalanceLbl.setVisibility(View.VISIBLE);
            // Request the balance of the user while expecting a JSON response
            JsonObjectRequest balanceRequest = new JsonObjectRequest
                    (Request.Method.GET, balanceUrl, null, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            //hide dialog
                            progress.hide();
                            // Display the result
                            try {
                                //grab current token count
                                actifitBalance.setText(" " + response.getString("tokens")+" AFIT");
                            }catch(JSONException e){
                                //hide dialog
                                progress.hide();
                                actifitBalance.setText(getString(R.string.unable_fetch_afit_balance));
                            }
                            queriesFetched +=1;
                            if (queriesFetched >= totalQueryCount){
                                BtnCheckBalance.clearAnimation();
                            }
                        }
                    }, new Response.ErrorListener() {

                        @Override
                        public void onErrorResponse(VolleyError error) {
                            //hide dialog
                            progress.hide();
                            actifitBalance.setText(getString(R.string.unable_fetch_afit_balance));
                            queriesFetched +=1;
                            if (queriesFetched >= totalQueryCount){
                                BtnCheckBalance.clearAnimation();
                            }
                        }
                    });

            // Add balance request to be processed
            queue.add(balanceRequest);

            //grab chain info to convert vests to power value
            String chainDataUrl = getString(R.string.get_chain_info);
            JsonObjectRequest chainInfoRequest = new JsonObjectRequest
                    (Request.Method.GET, chainDataUrl, null, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            //hide dialog
                            //progress.hide();
                            try {
                                chainInfoFetched = true;
                                hiveChainInfo = response.getJSONObject("HIVE");
                                steemChainInfo = response.getJSONObject("STEEM");
                                blurtChainInfo = response.getJSONObject("BLURT");
                                loadData();
                            }catch(Exception e){
                                //hide dialog
                                e.printStackTrace();
                            }
                            queriesFetched +=1;
                            if (queriesFetched >= totalQueryCount){
                                BtnCheckBalance.clearAnimation();
                            }
                        }
                    }, new Response.ErrorListener() {

                        @Override
                        public void onErrorResponse(VolleyError error) {
                            //hide dialog
                            progress.hide();
                            actifitBalance.setText(getString(R.string.unable_fetch_balance));
                            queriesFetched +=1;
                            if (queriesFetched >= totalQueryCount){
                                BtnCheckBalance.clearAnimation();
                            }
                        }
                    });

            // Add balance request to be processed
            queue.add(chainInfoRequest);


            // This holds the url to connect to the API and grab the balance.
            // We append to it the username
            String accountDataUrl = getString(R.string.get_account_api_url)+username;

            //display header
            //actifitBalanceLbl.setVisibility(View.VISIBLE);
            // Request the balance of the user while expecting a JSON response
            JsonObjectRequest userDataRequest = new JsonObjectRequest
                    (Request.Method.GET, accountDataUrl, null, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            //hide dialog
                            //progress.hide();
                            balanceData = response;
                            loadData();
                        }
                    }, new Response.ErrorListener() {

                        @Override
                        public void onErrorResponse(VolleyError error) {
                            //hide dialog
                            progress.hide();
                            actifitBalance.setText(getString(R.string.unable_fetch_balance));
                            queriesFetched +=1;
                            if (queriesFetched >= totalQueryCount){
                                BtnCheckBalance.clearAnimation();
                            }
                        }
                    });

            // Add balance request to be processed
            queue.add(userDataRequest);



            // This holds the url to connect to the API and grab the transactions.
            // We append to it the username
            String transactionUrl = getString(R.string.user_transactions_api_url)+username;

            //display header
            actifitTransactionsLbl.setVisibility(View.VISIBLE);

            // Request the transactions of the user first via JsonArrayRequest
            // according to our data format
            JsonArrayRequest transactionRequest = new JsonArrayRequest(Request.Method.GET,
                    transactionUrl, null, new Response.Listener<JSONArray>(){

                @Override
                public void onResponse(JSONArray transactionListArray) {
                    //hide dialog
                    progress.hide();

                    ArrayList<String> transactionList = new ArrayList<String>();
                    // Handle the result
                    try {

                        for (int i = 0; i < transactionListArray.length(); i++) {
                            // Retrieve each JSON object within the JSON array
                            JSONObject jsonObject = transactionListArray.getJSONObject(i);

                            // Build output
                            String transactionString = "";
                            // Capture individual values
                            transactionString += jsonObject.has("reward_activity") ? getString(R.string.activity_type_lbl) + ": " + jsonObject.getString("reward_activity") + "\n":"";
                            transactionString += jsonObject.has("token_count") ? getString(R.string.token_count_lbl) + ": " + jsonObject.getString("token_count") + " AFIT(s)\n":"";
                            transactionString += jsonObject.has("date") ?  getString(R.string.date_added_lbl) + ": " + jsonObject.getString("date") + "\n":"";
                            //transactionString += jsonObject.has("url")?"Relevant Post: <a href='"+jsonObject.getString("url") + "'>Post</a>\n":"";
                            transactionString += jsonObject.has("note") ? getString(R.string.note_lbl) + ": " +jsonObject.getString("note") + "\n":"";
                                    /*String url = jsonObject.getString("url");

                                    String note = jsonObject.getString("note");*/
                            // Adds strings from the current object to the data string
                            transactionList.add(transactionString);
                        }
                        // convert content to adapter display, and render it
                        ArrayAdapter<String> arrayAdapter =
                                new ArrayAdapter<String>(getApplicationContext(),android.R.layout.simple_list_item_1, transactionList){
                                    @Override
                                    public View getView(int position, View convertView, ViewGroup parent){
                                        // Get the Item from ListView
                                        View view = super.getView(position, convertView, parent);

                                        // Initialize a TextView for ListView each Item
                                        TextView tv = view.findViewById(android.R.id.text1);

                                        // Set the text color of TextView (ListView Item)
                                        tv.setPadding(0,10,0,0);
                                        tv.setTextColor(Color.GRAY);
                                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);

                                        // Generate ListView Item using TextView
                                        return view;
                                    }
                                };

                        actifitTransactionsView.setAdapter(arrayAdapter);
                        //actifitTransactions.setText("Response is: "+ response);
                    }catch (Exception e) {
                        //hide dialog
                        progress.hide();
                        actifitTransactionsError.setVisibility(View.VISIBLE);
                        e.printStackTrace();
                    }
                    queriesFetched +=1;
                    if (queriesFetched >= totalQueryCount){
                        BtnCheckBalance.clearAnimation();
                    }
                }
            }, error -> {
                //hide dialog
                progress.hide();
                //actifitTransactionsView.setText("Unable to fetch balance");
                actifitTransactionsError.setVisibility(View.VISIBLE);
                queriesFetched +=1;
                if (queriesFetched >= totalQueryCount){
                    BtnCheckBalance.clearAnimation();
                }
            });


            // Add transaction request to be processed
            queue.add(transactionRequest);

            //display a progress dialog not to keep the user waiting
            progress.setMessage(getString(R.string.fetching_user_balance));
            progress.show();


        }else{
            displayNotification(getString(R.string.username_missing),null,
                    callerContext, callerActivity, false);

        }
    }

    private void loadData(){
        try {
            if (!chainInfoFetched){
                return;
            }
            if (balanceData == null){
                return;
            }
            JSONObject hiveData = balanceData.getJSONObject("HIVE");

            // Display the result

            String hiveBalances = hiveData.getString("balance")+ " ";
            hiveBalances += hiveData.getString("hbd_balance");
            hiveChainInfo.put("chainName", "hive");
            //add HP balances
            hiveBalances += " " + MainActivity.formatValue(vestsToPower(hiveChainInfo, hiveData.getString("vesting_shares"))) + " HP";

            hiveBalances += " \r\n";

            //grab current token count
            //actifitBalance.setText(" " + response.getString("tokens"));
            hiveBalance.setText(" "+ Html.fromHtml( hiveBalances));

            //sportsBalance.setText("sports");

            //also display claimable balances
            String hiveRewards = "";
            hiveRewards += hiveData.getString("reward_hbd_balance") +" ";
            hiveRewards += hiveData.getString("reward_hive_balance") + " ";
            hiveRewards += hiveData.getString("reward_vesting_hive").replace("HIVE","HP");

            TextView hiveRewardsTxt = findViewById(R.id.hive_rewards);
            hiveRewardsTxt.setText(hiveRewards);

            if (balanceData.has("STEEM")) {
                JSONObject steemData = balanceData.getJSONObject("STEEM");
                String steemBalances = steemData.getString("balance") + " ";
                steemChainInfo.put("chainName", "steem");
                steemBalances += steemData.getString("sbd_balance");
                steemBalances += " " + MainActivity.formatValue(vestsToPower(steemChainInfo, steemData.getString("vesting_shares"))) + " SP";

                steemBalances += " \r\n";

                steemBalance.setText(" " + Html.fromHtml(steemBalances));


                String steemRewards = "";
                steemRewards += steemData.getString("reward_sbd_balance") + " ";
                steemRewards += steemData.getString("reward_steem_balance") + " ";
                steemRewards += steemData.getString("reward_vesting_steem").replace("STEEM", "SP");

                TextView steemRewardsTxt = findViewById(R.id.steem_rewards);
                steemRewardsTxt.setText(steemRewards);
            }

            if (balanceData.has("BLURT")) {
                JSONObject blurtData = balanceData.getJSONObject("BLURT");
                String blurtBalances = blurtData.getString("balance");
                blurtChainInfo.put("chainName", "blurt");
                blurtBalances += " " + MainActivity.formatValue(vestsToPower(blurtChainInfo, blurtData.getString("vesting_shares"))) + " BP";

                blurtBalances += " \r\n";
                blurtBalance.setText(" " + Html.fromHtml(blurtBalances));

                String blurtRewards = "";
                blurtRewards += blurtData.getString("reward_blurt_balance") + " ";
                blurtRewards += blurtData.getString("reward_vesting_blurt").replace("BLURT", "BP");
                TextView blurtRewardsTxt = findViewById(R.id.blurt_rewards);
                blurtRewardsTxt.setText(blurtRewards);
            }

        }catch(Exception e){
            //hide dialog
            progress.hide();
            actifitBalance.setText(getString(R.string.unable_fetch_balance));
        }
        queriesFetched +=1;
        if (queriesFetched >= totalQueryCount){
            BtnCheckBalance.clearAnimation();
        }
    }

    private Double vestsToPower(JSONObject chain, String vestsValue){
        Double powerVal = 0.0;
        Double totalVests = 1.0;
        Double vests = 0.0;
        //DecimalFormat df = new DecimalFormat(".00");

        try {
            String vestingFund = chain.getString("total_vesting_fund_"+chain.getString("chainName"));
            String [] entries = vestingFund.split(" ");
            powerVal = Double.parseDouble(entries[0]);
            //.split(" ")[0];

            String totalVestsStr = chain.getString("total_vesting_shares");
            String [] vals = totalVestsStr.split(" ");
            totalVests = Double.parseDouble(vals[0]);

            vestsValue = vestsValue.split(" ")[0];
            vests = Double.parseDouble(vestsValue);

            return powerVal * vests / totalVests;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0.0;
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

    void displayNotification(final String notification, final ProgressDialog progress,
                             final Context context, final Activity currentActivity,
                             final Boolean closeScreen){
        //render result
        currentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //hide the progressDialog
                if (progress!=null) {
                    progress.dismiss();
                }

                AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
                builder1.setMessage(notification);

                builder1.setCancelable(true);

                builder1.setPositiveButton(
                        getString(R.string.dismiss_button),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                //if we need to close current Activity
                                if (closeScreen) {
                                    //close current screen
                                    Log.d(MainActivity.TAG,">>>Finish");
                                    currentActivity.finish();
                                }
                            }
                        });
                //create and display alert window
                AlertDialog alert11 = builder1.create();
                alert11.show();
            }
        });

    }

}
