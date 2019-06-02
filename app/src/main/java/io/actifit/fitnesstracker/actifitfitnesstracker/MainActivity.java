/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.annotation.TargetApi;
import android.app.ActivityManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.hardware.SensorEventListener;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import com.crashlytics.android.Crashlytics;
import com.scottyab.rootbeer.RootBeer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import static android.os.Environment.getExternalStoragePublicDirectory;

/**
 * Implementation of this project was made possible via re-use, adoption and improvement of
 * following tutorials and resources:
 * - http://file.allitebooks.com/20170511/Android%20Sensor%20Programming%20By%20Example.pdf
 * - google's simple-pedometer github work licensed under Apache License
 * https://github.com/google/simple-pedometer (I initially found it under
 * http://gadgetsaint.com/android/create-pedometer-step-counter-android/ who seems to have
 * copied it without any reference to original source/work by google)
 * - https://notes.iopush.net/android-send-a-https-post-request/
 * - additional help and code has been utilized from
 * https://fabcirablog.weebly.com/blog/creating-a-never-ending-background-service-in-android
 * to help with services, but also relying on official Android documentation
 */

public class MainActivity extends AppCompatActivity{
    public static SensorManager sensorManager;
    private TextView stepDisplay;

    //tracks a reference to an instance of this class
    public static SensorEventListener mainActivitySensorList;

    public static final String TAG = "Actifit";

    //tracks if listener is active
    public static boolean isListenerActive = false;

    private StepsDBHelper mStepsDBHelper;

    //to utilize built-in step sensors
    private Sensor stepSensor;

    public static boolean isStepSensorPresent = false;
    public static String ACCEL_SENSOR = "ACCEL_SENSOR";
    public static String STEP_SENSOR = "STEP_SENSOR";

    //enforcing active sensor by default as ACC
    public static String activeSensor = MainActivity.ACCEL_SENSOR;

    /* items related to batch data capturing */

    private int curStepCount = 0;
    private static final String BUNDLE_LISTENER = "listener";

    private static Intent mServiceIntent;
    private static ActivityMonitorService mSensorService;
    private Context ctx;

    private BroadcastReceiver receiver;

    //flag if service is bound now
    boolean mBound = false;

    public Context getCtx() {
        return ctx;
    }

    static final int REQUEST_TAKE_PHOTO = 1;

    //required function to ask for proper read/write permissions on later Android versions
    protected boolean shouldAskPermissions() {
        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1);
    }

    public static ActivityMonitorService getmSensorService() {
        return mSensorService;
    }

    public static void setmSensorService(ActivityMonitorService sensorService) {
        mSensorService = sensorService;
    }

    public static Intent getmServiceIntent(){
        return mServiceIntent;
    }

    public static void setmServiceIntent(Intent serviceIntent){
        mServiceIntent = serviceIntent;
    }

    /**
     * function checks if the sensor service is running or not
     * @param serviceClass
     * @return
     */
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                Log.d(TAG,">>>>[Actifit]isMyServiceRunning?" + true+"");
                return true;
            }
        }
        Log.d(TAG,">>>>[Actifit]isMyServiceRunning?" + false+"");
        return false;
    }

    String mCurrentPhotoPath;
    //handles creating the snapped image file
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        //File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File storageDir = getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    //security function to detect emulators
    public static boolean isEmulator() {
        return Build.FINGERPRINT.contains("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic")
                && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT)
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.HARDWARE.contains("andy");
    }

    //function handles killing the app
    private void killActifit(String reason) {

        //display notification to user
        Toast toast = Toast.makeText(getApplicationContext(), reason,
                Toast.LENGTH_LONG);

        View view = toast.getView();

        TextView text = view.findViewById(android.R.id.message);

        try {
            //Gets the actual oval background of the Toast then sets the colour filter
            view.getBackground().setColorFilter(getResources().getColor(R.color.actifitRed), PorterDuff.Mode.SRC_IN);
        }catch(Exception e){
            e.printStackTrace();
        }

        text.setTextColor(Color.WHITE);

        toast.show();

        //kill gracefully
        finish();
    }


    @TargetApi(23)
    protected void askPermissions(String[] permissions) {
        int requestCode = 200;
        requestPermissions(permissions, requestCode);
    }

    //function handles checking if the SIM card is available
    public boolean isSimAvailable(){
        //standard case covering most phones
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager.getSimState() != TelephonyManager.SIM_STATE_ABSENT){
            return true;
        }
        //if we could not identify proper SIM (mostly due to multi-SIM), send an alert to user to fix his status
        return false;
    }

    /*
    public void crashMe(View v) {
        //throw new NullPointerException();
        //killActifit(getString(R.string.no_valid_sim));
        Crashlytics.getInstance().crash();
    }*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //hook up our standard thread catcher to allow auto-restart after crash
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandlerRestartApp(this));

        //notify user of app restart with a Toast
        if (getIntent().getBooleanExtra("crash", false)) {
            Toast toast = Toast.makeText(this,  getString(R.string.actifit_crash_restarted), Toast.LENGTH_SHORT);

            View view = toast.getView();

            TextView text = view.findViewById(android.R.id.message);

            try {
                //Gets the actual oval background of the Toast then sets the colour filter
                view.getBackground().setColorFilter(getResources().getColor(R.color.actifitRed), PorterDuff.Mode.SRC_IN);
            }catch(Exception e){
                e.printStackTrace();
            }

            text.setTextColor(Color.WHITE);

            toast.show();

        }

        //enforce test crash
        //Crashlytics.getInstance().crash();



        ctx = this;

        //retrieving account data for simple reuse. Data is not stored anywhere outside actifit App.
        final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

        /*************** security features ********************/

        //let's make sure this is a smart phone device by checking SIM Card

        if (!isSimAvailable()){
            //no valid active sim card detected
            Log.d(TAG,">>>>[Actifit] No valid SIM card detected");
            killActifit(getString(R.string.no_valid_sim));
        }

        //also let's try to detect if this is a known emulator
        if (isEmulator()){
            Log.d(TAG,">>>>[Actifit] Emulator detected");
            killActifit(getString(R.string.emulator_device));
        }

        //check if device is rooted
        RootBeer rootBeer = new RootBeer(this);
        if(rootBeer.isRootedWithoutBusyBoxCheck()){
            Log.d(TAG,">>>>[Actifit] Device is rooted");
            killActifit(getString(R.string.device_rooted));
        }

        //check if user has a proper unique ID already, if not generate one
        String actifitUserID = sharedPreferences.getString("actifitUserID","");
        if (actifitUserID.equals("")) {
            actifitUserID = UUID.randomUUID().toString();
            try{
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                String version = pInfo.versionName;
                actifitUserID += version;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("actifitUserID", actifitUserID);
            editor.apply();
        }

        //Log.d(TAG,"actifitUserID:"+actifitUserID);


        
            //initiate the monitoring service
            mSensorService = new ActivityMonitorService(getCtx());
            mServiceIntent = new Intent(getCtx(), mSensorService.getClass());
//only start the tracking service if the device sensors is picked as tracking medium
        String dataTrackingSystem = sharedPreferences.getString("dataTrackingSystem",getString(R.string.device_tracking));
        if (dataTrackingSystem.equals(getString(R.string.device_tracking))) {

            if (!isMyServiceRunning(mSensorService.getClass())) {
                startService(mServiceIntent);
            }
        }

        //grab pointers to specific elements/buttons to be able to capture events and take action
        stepDisplay = findViewById(R.id.step_display);
        Button BtnViewHistory = findViewById(R.id.btn_view_history);
        Button BtnPostSteemit = findViewById(R.id.btn_post_steemit);
        Button BtnLeaderboard = findViewById(R.id.btn_view_leaderboard);
        Button BtnWallet = findViewById(R.id.btn_view_wallet);
        Button BtnSettings = findViewById(R.id.btn_settings);

        Button BtnSnapActiPic = findViewById(R.id.btn_snap_picture);

        Log.d(TAG,">>>>[Actifit] Getting jiggy with it");

        mStepsDBHelper = new StepsDBHelper(this);


        //display current date
        displayDate();

        displayUserAndRank();

        //only display activity count from device if device mode is on
        if (dataTrackingSystem.equals(getString(R.string.device_tracking))) {
            //set initial steps display value
            int stepCount = mStepsDBHelper.fetchTodayStepCount();

            //display step count while ensuring we don't display negative value if no steps tracked yet
            stepDisplay.setText(getString(R.string.activity_today_string) + (stepCount < 0 ? 0 : stepCount));

            //adjust color of step account according to milestone achieved
            if (stepCount >= 10000 ){
                stepDisplay.setTextColor(getResources().getColor(R.color.actifitGreen));
            }else if (stepCount >= 5000 ){
                stepDisplay.setTextColor(getResources().getColor(R.color.actifitRed));
            }else {
                stepDisplay.setTextColor(getResources().getColor(android.R.color.tab_indicator_text));
            }

        }else{
            //inform user that fitbit mode is on
            stepDisplay.setText(getString(R.string.fitbit_tracking_mode_active));
        }

        //connecting the activity to the service to receive proper updates on move count
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int stepCount = intent.getIntExtra("move_count", 0);
                stepDisplay.setText(getString(R.string.activity_today_string) + (stepCount < 0 ? 0 : stepCount));
            }
        };

        //handle taking photos
        BtnSnapActiPic.setOnClickListener(new OnClickListener() {
              @Override
              public void onClick(View view) {

                  //make sure we have a cam on device
                  PackageManager pm = ctx.getPackageManager();

                  //if no cam, notify and leave
                  if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                      Toast.makeText(getApplicationContext(),getString(R.string.device_has_no_cam), Toast.LENGTH_SHORT).show();
                      return;
                  }

                  //ensure we have proper permissions for image upload
                  if (shouldAskPermissions()) {
                      String[] permissions = {
                              "android.permission.READ_EXTERNAL_STORAGE",
                              "android.permission.WRITE_EXTERNAL_STORAGE"
                      };
                      askPermissions(permissions);
                  }

                  Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                  // Ensure that there's a camera activity to handle the intent
                  if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                      // Create the File where the photo should go
                      File photoFile = null;
                      try {
                          photoFile = createImageFile();
                      } catch (IOException ex) {
                          // Error occurred while creating the File
                          ex.printStackTrace();
                      }
                      // Continue only if the File was successfully created
                      if (photoFile != null) {

                          Uri photoURI = FileProvider.getUriForFile(ctx,
                                  "io.actifit.fileprovider",
                                  photoFile);
                          takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                          startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
                      }
                  }
              }
          }
        );

        //handle activity to move to step history screen
        BtnViewHistory.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Intent intent = new Intent(MainActivity.this, StepHistoryActivity.class);
                MainActivity.this.startActivity(intent);

            }
        });

        //handle activity to move to post to steemit screen
        BtnPostSteemit.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Intent intent = new Intent(MainActivity.this, PostSteemitActivity.class);
                MainActivity.this.startActivity(intent);

            }
        });

        //handle activity to move over to the Leaderboard screen
        BtnLeaderboard.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Intent intent = new Intent(MainActivity.this, LeaderboardActivity.class);
                MainActivity.this.startActivity(intent);

            }
        });

        //handle activity to move over to the Wallet screen
        BtnWallet.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Intent intent = new Intent(MainActivity.this, WalletActivity.class);
                MainActivity.this.startActivity(intent);

            }
        });

        //handle activity to move over to the Settings screen
        BtnSettings.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                //sensorManager.unregisterListener(MainActivity.this);
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                MainActivity.this.startActivity(intent);

            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            galleryAddPic();
        }
    }

    //handle appending created pic to the gallery
    private void galleryAddPic() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(mCurrentPhotoPath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }

    //handles display of local date on front end
    private void displayDate(){
        String date_n = new SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault()).format(new Date());
        TextView date  = (TextView) findViewById(R.id.current_date);
        date.setText(date_n);
    }

    //handles fetching and displaying current user and rank
    private void displayUserAndRank(){
        //grab stored value, if any
        final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
        final String username = sharedPreferences.getString("actifitUser","");
        if (username != "") {
            //greet user if user identified
            final TextView welcomeUser = findViewById(R.id.welcome_user);
            final TextView userRankTV = findViewById(R.id.user_rank);

            //grab user rank if it is already stored today
            String userRank = sharedPreferences.getString("userRank", "");
            String userRankUpdateDate =
                    sharedPreferences.getString("userRankUpdateDate", "");
            Boolean fetchNewRankVal = false;
            if (userRank.equals("") || userRankUpdateDate.equals("")){
                fetchNewRankVal = true;
            }else{
                //make sure last value is at least within same day, otherwise grab new val
                Date date = new Date();
                DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                String strDate = dateFormat.format(date);
                if (Integer.parseInt(userRankUpdateDate)<Integer.parseInt(strDate)){
                    fetchNewRankVal = true;
                }

            }

            //set username
            welcomeUser.setText("@"+username);

            if (!fetchNewRankVal){
                //we already have the rank, display the message and the rank
                //welcomeUser.setText(getString(R.string.welcome_user).replace("USER_NAME", username).replace("USER_RANK","("+userRank+")"));
                userRankTV.setText(userRank+"/100");

            }else {
                //need to fetch user rank data from API
                RequestQueue queue = Volley.newRequestQueue(this);

                // This holds the url to connect to the API and grab the balance.
                // We append to it the username
                String userRankUrl = getString(R.string.user_rank_api_url) + username;

                // Request the rank of the user while expecting a JSON response
                JsonObjectRequest balanceRequest = new JsonObjectRequest
                        (Request.Method.GET, userRankUrl, null, new Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {

                                // Display the result
                                try {
                                    //grab current user rank
                                    String userRank = response.getString("user_rank");

                                    //store user rank along with date updated
                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.putString("userRank", userRank);

                                    Date date = new Date();
                                    DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                                    String strDate = dateFormat.format(date);

                                    editor.putString("userRankUpdateDate", strDate);
                                    editor.commit();

                                    //welcomeUser.setText(getString(R.string.welcome_user).replace("USER_NAME", username).replace("USER_RANK", "(" + userRank + ")"));
                                    userRankTV.setText(userRank+"/100");
                                } catch (JSONException e) {
                                    //hide dialog
                                    e.printStackTrace();
                                }
                            }
                        }, new Response.ErrorListener() {

                            @Override
                            public void onErrorResponse(VolleyError error) {
                                //hide dialog
                                error.printStackTrace();
                            }
                        });

                // Add balance request to be processed
                queue.add(balanceRequest);
            }

        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver((receiver),
                new IntentFilter("ACTIFIT_SERVICE")
        );

    }

    @Override
    protected void onResume() {
        super.onResume();
        displayDate();
        displayUserAndRank();

        //ensure our tracking is active particularly after leaving settings
        final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

        //only start the tracking service if the device sensors is picked as tracking medium
        String dataTrackingSystem = sharedPreferences.getString("dataTrackingSystem",getString(R.string.device_tracking));
        if (dataTrackingSystem.equals(getString(R.string.device_tracking))) {

            if (!isMyServiceRunning(mSensorService.getClass())) {
                //initiate the monitoring service
                startService(mServiceIntent);

                //enable aggressive mode if set
                String aggModeEnabled = sharedPreferences.getString("aggressiveBackgroundTracking",getString(R.string.aggr_back_tracking_off));
                if (aggModeEnabled.equals(getString(R.string.aggr_back_tracking_on))) {
                    //enable wake lock to ensure tracking functions in the background
                    PowerManager.WakeLock wl = ActivityMonitorService.getWakeLockInstance();
                    if (wl==null){
                        //initialize power manager and wake locks either way
                        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ACTIFIT:ACTIFIT_SPECIAL_LOCK");
                    }
                    if (!wl.isHeld()) {
                        Log.d(MainActivity.TAG, ">>>>[Actifit]Settings AGG MODE ON");
                        wl.acquire();
                    }
                }
            }
        }else{
            stepDisplay = findViewById(R.id.step_display);
            //inform user that fitbit mode is on
            stepDisplay.setText(getString(R.string.fitbit_tracking_mode_active));
        }


        //LocalBroadcastManager.getInstance(this).registerReceiver((receiver),
         //       new IntentFilter("ACTIFIT_SERVICE")
        //);
    }

    @Override
    protected void onStop() {
        //LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onStop();
    }

    /* preventing accidental single back button click leading to exiting the app and losing counter tracking */
    boolean doubleBackToExitPressedOnce = false;
    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, getString(R.string.back_exit_confirmation), Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }

    @Override
    protected void onDestroy(){
        //sensorManager.unregisterListener(this);
        isListenerActive = false;
        try{
            if (mServiceIntent!=null) {
                stopService(mServiceIntent);
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        super.onDestroy();

        Log.d(TAG,">>>> Actifit destroy state");
    }


}