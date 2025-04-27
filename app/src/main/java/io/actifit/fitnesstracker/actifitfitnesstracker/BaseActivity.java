package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;


import static android.content.pm.PackageManager.GET_META_DATA;

import static io.actifit.fitnesstracker.actifitfitnesstracker.MainActivity.tutVidUrl;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.LayoutRes;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

public class BaseActivity extends AppCompatActivity {

    private static final String TAG = "BaseActivity";
    public static int lastChatCount = 0;

    ValueAnimator valueAnimator;
    TextView chatNotifCount;
    RequestQueue queue;

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);

        setupCommonActionButtons();
    }

    protected void updateNavigationButtonStates() {

        TextView btnHome = findViewById(R.id.btn_home);
        if (btnHome != null){
            btnHome.setSelected(this instanceof MainActivity);
        }

        TextView btnLeaderboard = findViewById(R.id.btn_view_leaderboard);
        if (btnLeaderboard != null) {
            btnLeaderboard.setSelected(this instanceof LeaderboardActivity); // Replace LeaderboardActivity
        }

        TextView btnMarket = findViewById(R.id.btn_view_market);
        if (btnMarket != null) {
            btnMarket.setSelected(this instanceof MarketActivity); // Replace LeaderboardActivity
        }

        TextView btnViewSocial = findViewById(R.id.btn_view_social);
        if (btnViewSocial != null) {
            btnViewSocial.setSelected(this instanceof SocialActivity); // Replace LeaderboardActivity
        }

        TextView btnStepHistory = findViewById(R.id.btn_view_history);
        if (btnStepHistory != null) {
            btnStepHistory.setSelected(this instanceof StepHistoryActivity); // Replace LeaderboardActivity
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    protected void resetTitles() {
        try {
            ActivityInfo info = getPackageManager().getActivityInfo(getComponentName(), GET_META_DATA);
            if (info.labelRes != 0) {
                setTitle(info.labelRes);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }


    protected void setupCommonActionButtons() {

        TextView btnHome = findViewById(R.id.btn_home);
        TextView btnSocials = findViewById(R.id.btn_socials);
        TextView btnChat = findViewById(R.id.btn_chat);
        TextView btnLeaderboard = findViewById(R.id.btn_view_leaderboard);
        TextView btnVideo = findViewById(R.id.btn_video);
        TextView btnHelp = findViewById(R.id.btn_help);
        TextView btnMarket = findViewById(R.id.btn_view_market);
        TextView btnStepHistory = findViewById(R.id.btn_view_history);
        TextView btnViewSocial = findViewById(R.id.btn_view_social);
        chatNotifCount = findViewById(R.id.chat_notif_count);


        if (btnHome != null){
            btnHome.setOnClickListener(view ->{
                if (!(this instanceof MainActivity)){
                    // Create an Intent to navigate to MainActivity
                    Intent intent = new Intent(this, MainActivity.class);

                    // --- Recommended Flags for "Home" navigation ---
                    // FLAG_ACTIVITY_CLEAR_TOP: If an instance of MainActivity already exists
                    // in the task's stack, this flag causes all activities on top of it
                    // to be closed, and MainActivity is brought to the front.
                    // FLAG_ACTIVITY_SINGLE_TOP: Works with CLEAR_TOP. If MainActivity was
                    // already at the top (which our 'if' check prevents, but good practice),
                    // it ensures a new instance isn't created, instead onNewIntent() is called.
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                    // Start the MainActivity
                    startActivity(intent);
                }
            });

        }

        // --- Chat Button Listener ---
        if (btnChat != null) {
            btnChat.setOnClickListener(view -> {
                // Call the methods now part of BaseActivity
                storeNotifDate(new Date(), ""); // Added type for clarity
                storeNotifCount(lastChatCount); // Use variable now part of BaseActivity

                // Open chat using 'this' as Context
                ChatDialogFragment chatDialog = ChatDialogFragment.newInstance(this);
                chatDialog.show(getSupportFragmentManager(), "chat_dialog");
            });
        } else {
            // Log.w(TAG, "BtnChat view not found...");
        }

        // --- Help Button Listener ---
        if (btnHelp != null) {
            btnHelp.setOnClickListener(view -> {
                // Use tutVidUrl variable defined in BaseActivity
                if (tutVidUrl[0] != null && !tutVidUrl[0].isEmpty()) {
                    VideoDialogFragment dialogFragment = VideoDialogFragment.newInstance(tutVidUrl[0]);
                    dialogFragment.show(getSupportFragmentManager(), "video_dialog");
                } else {
                    Log.e(TAG, "Help video URL is invalid or not configured.");
                    //Toast.makeText(this, R.string.help_video_unavailable, Toast.LENGTH_SHORT).show(); // Use string resource
                }
            });
        } else {
            // Log.w(TAG, "BtnHelp view not found...");
        }

        // --- Socials Button Listener ---
        if (btnSocials != null) {
            btnSocials.setOnClickListener(view -> {
                // Use 'this' for Context and LayoutInflater
                AlertDialog.Builder socialDialogBuilder = new AlertDialog.Builder(this);
                View socialLayout = getLayoutInflater().inflate(R.layout.social_actifit, null);

                socialLayout.findViewById(R.id.facebook_actifit).setOnClickListener(view1 -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.facebook_actifit))));
                    }catch(Exception e){
                        Log.e(MainActivity.TAG, "error opening social media");
                    }
                });
                socialLayout.findViewById(R.id.twitter_actifit).setOnClickListener(view1 -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.twitter_actifit))));
                    }catch(Exception e){
                        Log.e(MainActivity.TAG, "error opening social media");
                    }
                });
                socialLayout.findViewById(R.id.telegram_actifit).setOnClickListener(view1 -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.telegram_actifit))));
                    }catch(Exception e){
                        Log.e(MainActivity.TAG, "error opening social media");
                    }
                });
                socialLayout.findViewById(R.id.discord_actifit).setOnClickListener(view1 -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.discord_actifit))));
                    }catch(Exception e){
                        Log.e(MainActivity.TAG, "error opening social media");
                    }
                });
                socialLayout.findViewById(R.id.instagram_actifit).setOnClickListener(view1 -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.instagram_actifit))));
                    }catch(Exception e){
                        Log.e(MainActivity.TAG, "error opening social media");
                    }
                });
                socialLayout.findViewById(R.id.linkedin_actifit).setOnClickListener(view1 -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.linkedin_actifit))));
                    }catch(Exception e){
                        Log.e(MainActivity.TAG, "error opening social media");
                    }
                });
                socialLayout.findViewById(R.id.youtube_actifit).setOnClickListener(view1 -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.youtube_actifit))));
                    }catch(Exception e){
                        Log.e(MainActivity.TAG, "error opening social media");
                    }
                });

                socialDialogBuilder.setView(socialLayout)
                        .setTitle(getString(R.string.socials_note))
                        .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                        .setPositiveButton(getString(R.string.close_button),null).create();

                socialDialogBuilder.show();
            });
        } else {
            // Log.w(TAG, "BtnSocials view not found...");
        }

        // --- Market Button Listener ---
        if (btnMarket != null) {
            btnMarket.setOnClickListener(arg0 -> {
                if (!(this instanceof MarketActivity)) {
                    Intent intent = new Intent(this, MarketActivity.class); // Use 'this'
                    startActivity(intent);
                }
                //}
            });
        } else {
            // Log.w(TAG, "BtnMarket view not found...");
        }

        if (btnLeaderboard != null) {
            btnLeaderboard.setOnClickListener(arg0 -> {
                if (!(this instanceof LeaderboardActivity)) {
                    Intent intent = new Intent(this, LeaderboardActivity.class);
                    startActivity(intent);
                }
                //}
            });
        } else {
            // Log.w(TAG, "BtnMarket view not found...");
        }

        if (btnStepHistory != null) {
            btnStepHistory.setOnClickListener(arg0 -> {
                if (!(this instanceof StepHistoryActivity)) {
                    Intent intent = new Intent(this, StepHistoryActivity.class);
                    startActivity(intent);
                }
            });
        }

        if (btnViewSocial != null) {
            btnViewSocial.setOnClickListener(arg0 -> {
                if (!(this instanceof SocialActivity)) {
                    Intent intent = new Intent(this, SocialActivity.class);
                    startActivity(intent);
                }
            });
        }

        if (btnVideo != null){
            btnVideo.setOnClickListener(arg0 -> {
                //show video modal
                VideoUploadFragment dialog = new VideoUploadFragment(this, LoginActivity.accessToken, this, false);
                //dialog.getView().setMinimumWidth(400);
                dialog.show(getSupportFragmentManager(), "video_upload_fragment");
            });
        }


        new Thread(() -> {
            runOnUiThread(() -> {
                queue = Volley.newRequestQueue(this);
                //load chat notifications
                loadChatNotif(queue);
            });
        }).start();

        updateNavigationButtonStates(); // Highlight the current activity's button
    }


    public void storeNotifDate(Date date, String dateStr){
        SharedPreferences shPrefs = getSharedPreferences("actifitSets",MODE_PRIVATE);
        SharedPreferences.Editor editor = shPrefs.edit();

        //Date date = new Date();
        String strDate = "";
        if (!dateStr.isEmpty()){
            strDate = dateStr;
        }else{
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            strDate = dateFormat.format(date);
        }

        //String strDate = dateFormat.format(date);
        Log.d(TAG, "strDate:"+strDate);
        editor.putString(getString(R.string.sting_chat_update), strDate);
        editor.apply();
    }

    public void storeNotifCount(int commCount){
        SharedPreferences shPrefs = getSharedPreferences("actifitSets",MODE_PRIVATE);
        SharedPreferences.Editor editor = shPrefs.edit();
        editor.putInt(getString(R.string.sting_chat_comm_count), commCount);
        editor.apply();
    }


    //check community notifications
    private void loadCommunityNotif(RequestQueue queue, String lastChatDate, int commChatCount){

        String notificationsUrl = getString(R.string.sting_chat_query_comm);

        // Request the transactions of the user first via JsonArrayRequest
        // according to our data format
        JsonArrayRequest transactionRequest = new JsonArrayRequest(Request.Method.GET,
                notificationsUrl, null, notificationsListArray -> {
            try {
                JSONObject todayObj = null;
                if (notificationsListArray.length() > 0) {
                    //today's stats
                    todayObj = notificationsListArray.getJSONArray(1).getJSONArray(0).getJSONObject(6);//dual array structure
                    //we have new messages today
                    if (todayObj.has(getString(R.string.actifit_comm))){
                        //new messages today, notify user
                        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                        String todayDate = outputFormat.format(new Date());
                        lastChatCount = todayObj.getInt(getString(R.string.actifit_comm));
                        if ((Integer.parseInt(todayDate) > Integer.parseInt(lastChatDate))){
                            //storeNotifDate(null, notifDate);
                            renderChatData();
                        }else if ((Integer.parseInt(todayDate) == Integer.parseInt(lastChatDate))
                                && lastChatCount > commChatCount){
                            storeNotifCount(lastChatCount);
                            renderChatData();
                        }
                    }
                }

            }catch(Exception ex){
                ex.printStackTrace();
            }
        }, error -> {
            //hide dialog
            error.printStackTrace();

        });

        // Add transaction request to be processed
        queue.add(transactionRequest);
    }


    private void loadChatNotif(RequestQueue queue){
        //start by hiding notifierh
        hideChatData();



        //for testing, set custom date
        /*
        storeNotifDate(null, "20230924");
        storeNotifCount(0);

         */
        //grab last stored update date for sting chat notifications
        SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

        /*
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(getString(R.string.sting_chat_update));
        editor.apply();*/

        String lastChatDate = sharedPreferences.getString(getString(R.string.sting_chat_update),"");
        int commChatCount = sharedPreferences.getInt(getString(R.string.sting_chat_comm_count),0);
        Log.d(TAG, "commChatCount:"+commChatCount);
        Log.d(TAG, "lastChatDate:"+lastChatDate);
        if (!lastChatDate.isEmpty()){//(false){//
            //we have a stored date, grab it

            // This holds the url to connect to the API and grab the transactions.
            // We append to it the username
            String notificationsUrl = getString(R.string.sting_chat_query_user)
                    .replace("_USER_", MainActivity.username);

            // Request the transactions of the user first via JsonArrayRequest
            // according to our data format
            JsonArrayRequest transactionRequest = new JsonArrayRequest(Request.Method.GET,
                    notificationsUrl, null, notificationsListArray -> {

                boolean foundNew = false;
                // Handle the result
                try {
                    JSONArray innerArray = null;
                    if (notificationsListArray.length() > 0  ){
                        innerArray = notificationsListArray.getJSONArray(1);
                    }
                    for (int i = 0; i < innerArray.length(); i++) {
                        try{
                            // Retrieve each JSON object within the JSON array
                            JSONObject jsonObject = innerArray.getJSONObject(i);

                            //SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.getDefault());
                            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());
                            Date date = inputFormat.parse(jsonObject.getString("date"));

                            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                            String notifDate = outputFormat.format(date);
                            if ((Integer.parseInt(notifDate) > Integer.parseInt(lastChatDate))){
                                //storeNotifDate(null, notifDate);
                                renderChatData();
                                foundNew = true;
                                break;
                            }
                        }catch(Exception exc){
                            exc.printStackTrace();
                        }
                    }
                    if (!foundNew){
                        //check community notifications
                        loadCommunityNotif(queue, lastChatDate, commChatCount);
                    }

                    //actifitTransactions.setText("Response is: "+ response);
                }catch (Exception e) {
                    //hide dialog

                    e.printStackTrace();
                }
            }, error -> {
                //hide dialog
                error.printStackTrace();

            });


            // Add transaction request to be processed
            queue.add(transactionRequest);

        }else{
            //default case
            renderChatData();
        }

    }

    private void renderChatData(){
        if (chatNotifCount == null) return;
        chatNotifCount.setVisibility(View.VISIBLE);

        // Create a new ValueAnimator object.
        if (valueAnimator == null) {
            valueAnimator = new ValueAnimator();

            // Set the duration of the animation.
            valueAnimator.setDuration(1000);
            valueAnimator.setRepeatCount(ValueAnimator.INFINITE);
            valueAnimator.setRepeatMode(ValueAnimator.REVERSE);

            // Set the valueFrom and valueTo properties of the ValueAnimator object.
            //valueAnimator.setFloatValues(Color.RED, Color.parseColor("#FFFFFF"), Color.RED);
            valueAnimator.setFloatValues(getResources().getColor(R.color.actifitRed), getResources().getColor(R.color.colorWhite));//Color.parseColor("#FFFFFF"));


            // Add an AnimatorUpdateListener to the ValueAnimator object.
            valueAnimator.addUpdateListener(animator -> {
                // Set the background color of the TextView object to the current value of the ValueAnimator object.
                int animatedValue = (int) (float) animator.getAnimatedValue();

                chatNotifCount.setTextColor(animatedValue);
            });
        }

// Start the ValueAnimator object.
        if (!valueAnimator.isRunning()) {
            valueAnimator.start();
        }

    }

    private void hideChatData(){
        if (chatNotifCount==null) return;
        chatNotifCount.setVisibility(View.GONE);
        if (valueAnimator != null && valueAnimator.isRunning()) {
            valueAnimator.cancel();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update states in onResume in case the user navigates back
        // Ensure the view is already created before calling this
        if (findViewById(R.id.btn_view_social) != null) {
            updateNavigationButtonStates();
        }
        new Thread(() -> {
            runOnUiThread(() -> {
                //load chat notifications
                loadChatNotif(queue);
            });
        }).start();
    }
}
