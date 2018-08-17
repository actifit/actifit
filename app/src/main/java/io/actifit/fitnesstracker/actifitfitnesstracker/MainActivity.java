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

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.hardware.SensorEventListener;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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

    private Intent mServiceIntent;
    private ActivityMonitorService mSensorService;
    private Context ctx;

    private BroadcastReceiver receiver;

    //flag if service is bound now
    boolean mBound = false;

    public Context getCtx() {
        return ctx;
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
                System.out.println(">>>>[Actifit]isMyServiceRunning?" + true+"");
                return true;
            }
        }
        System.out.println(">>>>[Actifit]isMyServiceRunning?" + false+"");
        return false;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ctx = this;

        //initiate the monitoring service
        mSensorService = new ActivityMonitorService(getCtx());
        mServiceIntent = new Intent(getCtx(), mSensorService.getClass());

        if (!isMyServiceRunning(mSensorService.getClass())) {
            startService(mServiceIntent);
            //bindService(mServiceIntent, mConnection, Context.BIND_AUTO_CREATE);
        }

        //grab pointers to specific elements/buttons to be able to capture events and take action
        stepDisplay = findViewById(R.id.step_display);
        Button BtnViewHistory = findViewById(R.id.btn_view_history);
        Button BtnPostSteemit = findViewById(R.id.btn_post_steemit);
        Button BtnLeaderboard = findViewById(R.id.btn_view_leaderboard);
        Button BtnWallet = findViewById(R.id.btn_view_wallet);
        Button BtnSettings = findViewById(R.id.btn_settings);

        System.out.println(">>>>[Actifit] Getting jiggy with it");

        mStepsDBHelper = new StepsDBHelper(this);

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


        BtnSettings.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                //sensorManager.unregisterListener(MainActivity.this);
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                MainActivity.this.startActivity(intent);

            }
        });


        //set initial steps display value
        int stepCount = mStepsDBHelper.fetchTodayStepCount();
        //display step count while ensuring we don't display negative value if no steps tracked yet
        stepDisplay.setText(getString(R.string.activity_today_string) + (stepCount < 0 ? 0 : stepCount));

        //connecting the activity to the service to receive proper updates on move count
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int stepCount = intent.getIntExtra("move_count", 0);
                stepDisplay.setText(getString(R.string.activity_today_string) + (stepCount < 0 ? 0 : stepCount));
            }
        };

    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver((receiver),
                new IntentFilter("ACTIFIT_SERVICE")
        );

    }

    @Override
    protected void onResume() {  // We will use the onResume method to implement our rootchecker class.
        super.onResume();
        //LocalBroadcastManager.getInstance(this).registerReceiver((receiver),
         //       new IntentFilter("ACTIFIT_SERVICE")
        //);
        if(new RootChecker().isDeviceRooted()){ //If device fails test, or I guess, passes :P
            new AlertDialog.Builder(this) //Create a new alert, displaying to the user that we do not allow root devices to use the app.
                    .setIcon(android.R.drawable.ic_dialog_alert) //Icon to use in alert.
                    .setTitle("Root Activity")  //Title
                    .setMessage("Your Device Is Rooted. Please Unroot Your Device, and Try Again.") //Message to display
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() //code to run on click
                            {
                                @Override
                                public void onClick(DialogInterface dialog, int which) { 
                                    finish(); //closes application
                                }



                            }

                    )
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            finish();//closes application
                        }
                    })


                    .show() //launch alert.
        } 
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

        stopService(mServiceIntent);

        super.onDestroy();
        System.out.println(">>>> Actifit destroy state");
    }


}
