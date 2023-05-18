package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.ListView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class LeaderboardActivity extends BaseActivity {

    private ListView mAccountsListView;
    private ArrayList<SinglePostModel> mAccountsFinalList ;
    private Context leadership_post_context;
    private LeaderboardEntryAdapter listingAdapter;
    private ProgressDialog progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        mAccountsListView = findViewById(R.id.accounts_list);
        mAccountsFinalList = new ArrayList<>();

        this.leadership_post_context = this;
        final Activity currentActivity = this;

        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(currentActivity);

        //connect to our leaderboard API
        String urlStr = getString(R.string.leaderboard_v2_url);
        if (!getString(R.string.test_mode).equals("off")){
            urlStr = getString(R.string.test_leaderboard_v2_url);
        }

        progress = new ProgressDialog(this);

        progress.setMessage(getString(R.string.fetching_leaderboard));
        progress.show();

        // Request the transactions of the user first via JsonArrayRequest
        // according to our data format
        JsonArrayRequest transactionRequest = new JsonArrayRequest(Request.Method.POST,
                urlStr, null, new Response.Listener<JSONArray>(){

            @Override
            public void onResponse(JSONArray transactionListArray) {

                // Handle the result
                try {

                    for (int i = 0; i < transactionListArray.length(); i++) {
                        // Retrieve each JSON object within the JSON array
                        JSONObject jsonObject = transactionListArray.getJSONObject(i);

                        SinglePostModel postEntry = new SinglePostModel(jsonObject);

                        mAccountsFinalList.add(postEntry);

                    }
                    // Create the adapter to convert the array to views
                    listingAdapter = new LeaderboardEntryAdapter(leadership_post_context, mAccountsFinalList);

                    mAccountsListView.setAdapter(listingAdapter);

                    //hide dialog
                    progress.hide();
                    //actifitTransactions.setText("Response is: "+ response);
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


        transactionRequest.setRetryPolicy(new DefaultRetryPolicy(15000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));


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
