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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
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
 */

public class MainActivity extends AppCompatActivity implements SensorEventListener, StepListener {
    private SimpleStepDetector simpleStepDetector;
    public static SensorManager sensorManager;
    private Sensor accSensor;
    private static final String TEXT_NUM_STEPS = "Total Activity Today: ";
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //grab pointers to specific elements/buttons to be able to capture events and take action
        stepDisplay = findViewById(R.id.step_display);
        Button BtnViewHistory = findViewById(R.id.btn_view_history);
        Button BtnPostSteemit = findViewById(R.id.btn_post_steemit);
        Button BtnLeaderboard = findViewById(R.id.btn_view_leaderboard);
        Button BtnWallet = findViewById(R.id.btn_view_wallet);
        Button BtnSettings = findViewById(R.id.btn_settings);

        System.out.println(">>>> Getting jiggy with it");

        //adding a reference to this class to allow unregistering listeners outside it
        mainActivitySensorList = this;

        // Get an instance of the SensorManager
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Fragment is being restored, reinitialise its state with data from the bundle
        if (savedInstanceState != null) {
            //resetCounter();
            /*curStepCount = savedInstanceState.getInt(BUNDLE_STEPS);
            */
            isListenerActive = savedInstanceState.getBoolean(BUNDLE_LISTENER);
            System.out.println(">>>>turning to inactive");
            //unregister existing sensors
            sensorManager.unregisterListener(MainActivity.mainActivitySensorList);
            //now it's inactive
            isListenerActive = false;
        }
        System.out.println(">>>onstart - isListenerActive"+isListenerActive);


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
        stepDisplay.setText(TEXT_NUM_STEPS + (stepCount < 0 ? 0 : stepCount));

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
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        System.out.println(">>>onRestore");
    }



    /**
     * handle reinitiating the step counter, which will be called after coming back to screen
     */
    @Override
    public void onResume() {
        super.onResume();
        registerEventListener();
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * handles the actual counting of steps relying on one of the two sensors,
     * whichever is active
     *
     * @param event
     */
    @Override
    public void onSensorChanged(SensorEvent event) {

        //System.out.println(">>>> onSensorChanged Event: activeSensor:"+activeSensor+" Type:"+event.sensor.getType());

        // store the delay of this event
        /*recordDelay(event);
        final String delayString = getDelayString();*/

        if (activeSensor.equals(MainActivity.STEP_SENSOR) && event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            //if this is a Step detector event and we get a step sensor reading, store
            curStepCount = mStepsDBHelper.createStepsEntry(event.values.length);
            //display step count while ensuring we don't display negative value if no steps tracked yet
            stepDisplay.setText(TEXT_NUM_STEPS + (curStepCount < 0 ? 0 : curStepCount));
        } else if (activeSensor.equals(MainActivity.ACCEL_SENSOR) && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            //System.out.println(">>>>>ACCELERATOR:"+event.sensor.getName());
            //if this is an accel sensor mode, and we get an accel sensor reading, process
            simpleStepDetector.updateAccel(
                    event.timestamp, event.values[0], event.values[1], event.values[2]);
        }

    }

    /**
     * this is overriding the step function which only works in case of using accelerometer sensor
     *
     * @param timeNs
     */
    @Override
    public void step(long timeNs) {
        curStepCount = mStepsDBHelper.createStepsEntry();
        stepDisplay.setText(TEXT_NUM_STEPS + (curStepCount < 0 ? 0 : curStepCount));
    }

    private void registerEventListener() {

        //retrieving prior settings if already saved before
        SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
        //if (sharedPreferences.contains("actifitUser")){

        //update the value of activeSensor
        //activeSensor = (sharedPreferences.getString("activeSensor", ""));

        System.out.println(">>>>activeSensor:"+activeSensor);

        //track if we set a sensor or not yet
        Boolean isSensorSet = false;

        //try to detect if the device supports a step sensor (TYPE_STEP_DETECTOR)
        if (sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
                != null) {

            isStepSensorPresent = true;

            //need to consult with the settings also if it was adjusted manually to accel sensor
            if (!activeSensor.equals(MainActivity.ACCEL_SENSOR)) {
                //only register a new one if an old one is not active
                if ( !isListenerActive){
                    //enforcing wake up enabled in versions that support it
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        stepSensor =
                                sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR, true);
                    }
                    //if we couldn't grab a wake-up sensor, or the version is too low, get a non-wake up sensor
                    if (stepSensor == null) {
                        System.out.println(">>>>No-wake up STEP sensor");
                        stepSensor =
                                sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
                    }

                    sensorManager.unregisterListener(MainActivity.this);
                    // Register the listener for this sensor in batch mode.
                    // If the max delay is 0, events will be delivered in continuous mode without batching.
                    // requires at least KITKAT version
                    /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        sensorManager.registerListener(MainActivity.this, stepSensor,
                                SensorManager.SENSOR_DELAY_FASTEST, maxDelay);
                    } else {*/
                        sensorManager.registerListener(MainActivity.this, stepSensor,
                                SensorManager.SENSOR_DELAY_FASTEST);
                   // }

                }
                isSensorSet = true;
                activeSensor = MainActivity.STEP_SENSOR;
            }
        }
        //check if we still need to register a sensor
        if (!isSensorSet) {
            // accSensor will host the default accelerometer sensor to be only used in case of earlier
            // SDK version / missing step_counter

            //only register anew if no existing listener is active
            if (!isListenerActive) {
                //enforcing wake up enabled in versions that support it
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    accSensor =
                            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true);
                }
                //if we couldn't grab a wake-up sensor, or the version is too low, get a non-wake up sensor
                if (accSensor == null) {
                    System.out.println(">>>>No-wake up ACC sensor");
                    accSensor =
                            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                }


                simpleStepDetector = new SimpleStepDetector();
                simpleStepDetector.registerListener(this);
                sensorManager.unregisterListener(MainActivity.this);
                sensorManager.registerListener(MainActivity.this, accSensor, SensorManager.SENSOR_DELAY_FASTEST);
            }else{
                //initiate step detector either way
                simpleStepDetector = new SimpleStepDetector();
                simpleStepDetector.registerListener(this);
            }
            activeSensor = MainActivity.ACCEL_SENSOR;
//            isSensorSet = true;
        }

        //setting listener var as active
        isListenerActive = true;
    }


    @Override
    public void onPause() {
        super.onPause();
        // Unregister listener when application is paused
        //sensorManager.unregisterListener(MainActivity.this);
        //save that a listener is active already so that we don't start another one
        //isListenerActive

    }

    @Override
    protected void onStop() {
        // call the superclass method first
        super.onStop();
        System.out.println(">>>> Actifit stop state");
    }


    @Override
    protected void onDestroy(){
        sensorManager.unregisterListener(this);
        isListenerActive = false;
        super.onDestroy();
        System.out.println(">>>> Actifit destroy state");
    }

    /**
     * Returns a string describing the sensor delays recorded in
     * {@link #recordDelay(android.hardware.SensorEvent)}.
     *
     * @return
     */
    /*private String getDelayString() {
        // Empty the StringBuffer
        mDelayStringBuffer.setLength(0);

        // Loop over all recorded delays and append them to the buffer as a decimal
        for (int i = 0; i < mEventLength; i++) {
            if (i > 0) {
                mDelayStringBuffer.append(", ");
            }
            final int index = (mEventData + i) % EVENT_QUEUE_LENGTH;
            final float delay = mEventDelays[index] / 1000f; // convert delay from ms into s
            mDelayStringBuffer.append(String.format("%1.1f", delay));
        }

        return mDelayStringBuffer.toString();
    }*/

    /**
     * Records the delay for the event.
     *
     * @param event
     */
    /*private void recordDelay(SensorEvent event) {
        // Calculate the delay from when event was recorded until it was received here in ms
        // Event timestamp is recorded in us accuracy, but ms accuracy is sufficient here
        mEventDelays[mEventData] = System.currentTimeMillis() - (event.timestamp / 1000000L);

        // Increment length counter
        mEventLength = Math.min(EVENT_QUEUE_LENGTH, mEventLength + 1);
        // Move pointer to the next (oldest) location
        mEventData = (mEventData + 1) % EVENT_QUEUE_LENGTH;
    }*/

    /**
     * Records the state of the application into the {@link android.os.Bundle}.
     *
     * @param outState
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Store step count to restore the state of the application
        //outState.putInt(BUNDLE_STEPS, curStepCount);

        //save our listener state
        outState.getBoolean(BUNDLE_LISTENER, isListenerActive);
        System.out.println(">>>onSave - isListenerActive"+isListenerActive);
        // END_INCLUDE(saveinstance)
    }

    /**
     * Resets the step counter by clearing all counting variables and lists.
     */
    /*private void resetCounter() {
        // BEGIN_INCLUDE(reset)
        curStepCount = 0;
        mEventLength = 0;
        mEventDelays = new float[EVENT_QUEUE_LENGTH];
        // END_INCLUDE(reset)
    }*/
}