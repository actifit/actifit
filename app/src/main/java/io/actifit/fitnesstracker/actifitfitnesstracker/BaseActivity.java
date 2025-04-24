package io.actifit.fitnesstracker.actifitfitnesstracker;

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
import androidx.annotation.LayoutRes;

public class BaseActivity extends AppCompatActivity {

    private static final String TAG = "BaseActivity";

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
                storeNotifDate(new Date(), "chat"); // Added type for clarity
                storeNotifCount(MainActivity.lastChatCount); // Use variable now part of BaseActivity

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
        updateNavigationButtonStates(); // Highlight the current activity's button
    }


    public void storeNotifDate(Date date, String dateStr){
        SharedPreferences shPrefs = getSharedPreferences("actifitSets",MODE_PRIVATE);
        SharedPreferences.Editor editor = shPrefs.edit();

        //Date date = new Date();
        String strDate = "";
        if (!dateStr.equals("")){
            strDate = dateStr;
        }else{
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            strDate = dateFormat.format(date);
        }

        //String strDate = dateFormat.format(date);

        editor.putString(getString(R.string.sting_chat_update), strDate);
        editor.commit();
    }

    public void storeNotifCount(int commCount){
        SharedPreferences shPrefs = getSharedPreferences("actifitSets",MODE_PRIVATE);
        SharedPreferences.Editor editor = shPrefs.edit();
        editor.putInt(getString(R.string.sting_chat_comm_count), commCount);
        editor.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update states in onResume in case the user navigates back
        // Ensure the view is already created before calling this
        if (findViewById(R.id.btn_view_social) != null) {
            updateNavigationButtonStates();
        }
    }
}
