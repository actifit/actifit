package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.util.ArrayList;


public class NotificationsActivity extends BaseActivity{
    //private ProgressDialog progress;
    public String username;
    private ArrayList<NotificationModel> notificationList ;
    private NotificationEntryAdapter listingAdapter;
    private ProgressBar loader;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loader = findViewById(R.id.loader);
        notificationList = new ArrayList<>();

        SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
        // SharedPreferences.Editor editor = sharedPreferences.edit();

        //grab stored value, if any
        username = sharedPreferences.getString("actifitUser","");

        final Context callerContext = this;
        RequestQueue queue = Volley.newRequestQueue(this);

        final ListView actifitNotificationsView = findViewById(R.id.actifit_notifications);
        final TextView actifitNotificationsError = findViewById(R.id.actifit_notifications_error);

        /*progress = new ProgressDialog(this);

        progress.setMessage(getString(R.string.fetching_notifications));
        progress.show();*/

        // This holds the url to connect to the API and grab the transactions.
        // We append to it the username
        String notificationsUrl = Utils.apiUrl(this)+getString(R.string.user_all_notifications_api_url)+username;

        // Request the transactions of the user first via JsonArrayRequest
        // according to our data format
        JsonArrayRequest transactionRequest = new JsonArrayRequest(Request.Method.GET,
                notificationsUrl, null, notificationsListArray -> {

            // Handle the result
            try {

                for (int i = 0; i < notificationsListArray.length(); i++) {
                    // Retrieve each JSON object within the JSON array
                    JSONObject jsonObject = notificationsListArray.getJSONObject(i);

                    NotificationModel postEntry = new NotificationModel(jsonObject);
                    notificationList.add(postEntry);

                }
                // Create the adapter to convert the array to views
                listingAdapter = new NotificationEntryAdapter(callerContext,
                        NotificationsActivity.this , notificationList);

                actifitNotificationsView.setAdapter(listingAdapter);
                loader.setVisibility(View.GONE);
                //hide dialog
                //progress.hide();
                //actifitTransactions.setText("Response is: "+ response);
            }catch (Exception e) {
                //hide dialog
                //progress.hide();
                //actifitTransactionsError.setVisibility(View.VISIBLE);
                actifitNotificationsError.setVisibility(View.VISIBLE);
                loader.setVisibility(View.GONE);
                e.printStackTrace();
            }
        }, error -> {
            //hide dialog
            //progress.hide();
            //actifitTransactionsView.setText("Unable to fetch balance");
            loader.setVisibility(View.GONE);
            actifitNotificationsError.setVisibility(View.VISIBLE);

        });


        // Add transaction request to be processed
        queue.add(transactionRequest);

        //display a progress dialog not to keep the user waiting
        //progress.setMessage(getString(R.string.fetching_notifications));
        //progress.show();
    }


}
