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
import java.util.Collections;


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

        loadNotificationsWithRetry(queue, callerContext, actifitNotificationsView, actifitNotificationsError, 0);
    }

    private void loadNotificationsWithRetry(RequestQueue queue, Context callerContext,
            ListView notificationsView, TextView errorView, int attempt) {
        final int MAX_RETRIES = 3;
        final int BASE_DELAY_MS = 2000;

        String notificationsUrl = Utils.apiUrl(this) + getString(R.string.user_all_notifications_api_url) + username;

        JsonArrayRequest transactionRequest = new JsonArrayRequest(Request.Method.GET,
                notificationsUrl, null, notificationsListArray -> {
            try {
                notificationList.clear();
                for (int i = 0; i < notificationsListArray.length(); i++) {
                    JSONObject jsonObject = notificationsListArray.getJSONObject(i);
                    notificationList.add(new NotificationModel(jsonObject));
                }
                Collections.reverse(notificationList);
                listingAdapter = new NotificationEntryAdapter(callerContext,
                        NotificationsActivity.this, notificationList);
                notificationsView.setAdapter(listingAdapter);
                loader.setVisibility(View.GONE);
            } catch (Exception e) {
                errorView.setVisibility(View.VISIBLE);
                loader.setVisibility(View.GONE);
                e.printStackTrace();
            }
        }, error -> {
            if (attempt < MAX_RETRIES) {
                int delayMs = BASE_DELAY_MS * (1 << attempt);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() ->
                        loadNotificationsWithRetry(queue, callerContext, notificationsView, errorView, attempt + 1),
                        delayMs);
            } else {
                loader.setVisibility(View.GONE);
                errorView.setVisibility(View.VISIBLE);
            }
        });

        queue.add(transactionRequest);

        //display a progress dialog not to keep the user waiting
        //progress.setMessage(getString(R.string.fetching_notifications));
        //progress.show();
    }


}
