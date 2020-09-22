package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;

import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

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

public class WalletActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        //grab links to layout items for later use
        final TextView steemitUsername = findViewById(R.id.steemit_username);
        Button BtnCheckBalance = findViewById(R.id.btn_get_balance);

        //try to check first if we had a user defined already and saved to preferences
        //retrieving account data for simple reuse. Data is not stored anywhere outside actifit App.
        SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
       // SharedPreferences.Editor editor = sharedPreferences.edit();

        //grab stored value, if any
        String curUser = sharedPreferences.getString("actifitUser","");
        steemitUsername.setText(curUser);

        final Activity callerActivity = this;
        final Context callerContext = this;

        //make sure we have a value, and if so, automatically grab it
        if (!curUser.equals("")) {
            //if we already have data, emulate a click to grab the info
            loadAccountBalance(steemitUsername, callerActivity, callerContext);
        }

        //handle activity to fetch balance
        BtnCheckBalance.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                loadAccountBalance(steemitUsername, callerActivity, callerContext);

            }
        });
    }

    void loadAccountBalance(TextView steemitUsername, Activity callerActivity, Context callerContext){
        //make sure we have a value, and if so, automatically grab it
        if (!steemitUsername.getText().equals("")) {
            //skip on spaces, upper case, and @ symbols to properly match steem username patterns
            String username = steemitUsername.getText().toString()
                            .trim().toLowerCase().replace("@","");
            //connect to the interface to display result
            final TextView actifitBalance = findViewById(R.id.actifit_balance);
            final TextView actifitBalanceLbl = findViewById(R.id.actifit_balance_lbl);
            final TextView actifitTransactionsLbl = findViewById(R.id.actifit_transactions_lbl);
            final ListView actifitTransactionsView = findViewById(R.id.actifit_transactions);
            final TextView actifitTransactionsError = findViewById(R.id.actifit_transactions_error);

            //hide if this is a recurring call
            actifitTransactionsError.setVisibility(View.INVISIBLE);

            //initialize progress dialog
            final ProgressDialog progress = new ProgressDialog(callerContext);

            // Instantiate the RequestQueue.
            RequestQueue queue = Volley.newRequestQueue(callerActivity);

            // This holds the url to connect to the API and grab the balance.
            // We append to it the username
            String balanceUrl = getString(R.string.user_balance_api_url)+username;

            //display header
            actifitBalanceLbl.setVisibility(View.VISIBLE);
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
                                actifitBalance.setText(" " + response.getString("tokens"));
                            }catch(JSONException e){
                                //hide dialog
                                progress.hide();
                                actifitBalance.setText(getString(R.string.unable_fetch_balance));
                            }
                        }
                    }, new Response.ErrorListener() {

                        @Override
                        public void onErrorResponse(VolleyError error) {
                            //hide dialog
                            progress.hide();
                            actifitBalance.setText(getString(R.string.unable_fetch_balance));
                        }
                    });

            // Add balance request to be processed
            queue.add(balanceRequest);

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
                                        tv.setTextColor(Color.BLACK);
                                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);

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

                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    //hide dialog
                    progress.hide();
                    //actifitTransactionsView.setText("Unable to fetch balance");
                    actifitTransactionsError.setVisibility(View.VISIBLE);
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
