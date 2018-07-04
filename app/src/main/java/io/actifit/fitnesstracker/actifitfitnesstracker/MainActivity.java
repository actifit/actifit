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
    private static final String TEXT_NUM_STEPS = "Total Steps Today: ";
    private TextView stepDisplay;

    private StepsDBHelper mStepsDBHelper;

    //to utilize built-in step sensors
    private Sensor stepSensor;

    public static boolean isStepSensorPresent = false;
    public static String ACCEL_SENSOR = "ACCEL_SENSOR";
    public static String STEP_SENSOR = "STEP_SENSOR";
    public static String activeSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stepDisplay = findViewById(R.id.step_display);
        Button BtnViewHistory = findViewById(R.id.btn_view_history);
        Button BtnPostSteemit = findViewById(R.id.btn_post_steemit);
        Button BtnSettings = findViewById(R.id.btn_settings);


        // Get an instance of the SensorManager
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        //retrieving prior settings if already saved before
        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        //if (sharedPreferences.contains("actifitUser")){
        activeSensor = (sharedPreferences.getString("activeSensor",""));

        //track if we set a sensor or not yet
        Boolean isSensorSet = false;

        //try to detect if the device supports a step sensor (TYPE_STEP_DETECTOR)
        if(sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
                != null)
        {
            stepSensor =
                    sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

            isStepSensorPresent = true;

            //need to consult with the settings also if it was adjusted manually to accel sensor
            if (!activeSensor.equals(MainActivity.ACCEL_SENSOR)) {
                sensorManager.registerListener(MainActivity.this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST);
                isSensorSet = true;
                activeSensor = MainActivity.STEP_SENSOR;
            }
        }
        if (!isSensorSet){
            // accSensor will host the default accelerometer sensor to be only used in case of earlier
            // SDK version / missing step_counter
            accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            simpleStepDetector = new SimpleStepDetector();
            simpleStepDetector.registerListener(this);
            sensorManager.registerListener(MainActivity.this, accSensor, SensorManager.SENSOR_DELAY_FASTEST);
            activeSensor = MainActivity.ACCEL_SENSOR;
//            isSensorSet = true;
        }

        mStepsDBHelper = new StepsDBHelper(this);

        //handle activity to move to step history screen
        BtnViewHistory.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Intent intent=new Intent(MainActivity.this, StepHistoryActivity.class);
                MainActivity.this.startActivity(intent);

            }
        });

        //handle activity to move to post to steemit screen
        BtnPostSteemit.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Intent intent=new Intent(MainActivity.this, PostSteemitActivity.class);
                MainActivity.this.startActivity(intent);

            }
        });

        BtnSettings.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                //sensorManager.unregisterListener(MainActivity.this);
                Intent intent=new Intent(MainActivity.this, SettingsActivity.class);
                MainActivity.this.startActivity(intent);

            }
        });

        //set initial steps display value
        stepDisplay.setText(TEXT_NUM_STEPS + mStepsDBHelper.fetchTodayStepCount());


    }


    /**
     * handle reinitiating the step counter, which will be called after coming back to screen
     */
    @Override
    public void onResume() {
        super.onResume();
        //retrieving prior settings if already saved before
        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        //if (sharedPreferences.contains("actifitUser")){

        //update the value of activeSensor
        activeSensor = (sharedPreferences.getString("activeSensor",""));

        //track if we set a sensor or not yet
        Boolean isSensorSet = false;

        //try to detect if the device supports a step sensor (TYPE_STEP_DETECTOR)
        if(sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
                != null)
        {
            stepSensor =
                    sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

            isStepSensorPresent = true;

            //need to consult with the settings also if it was adjusted manually to accel sensor
            if (!activeSensor.equals(MainActivity.ACCEL_SENSOR)) {
                sensorManager.unregisterListener(MainActivity.this);
                sensorManager.registerListener(MainActivity.this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST);
                isSensorSet = true;
                activeSensor = MainActivity.STEP_SENSOR;
            }
        }
        if (!isSensorSet){
            // accSensor will host the default accelerometer sensor to be only used in case of earlier
            // SDK version / missing step_counter
            accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            simpleStepDetector = new SimpleStepDetector();
            simpleStepDetector.registerListener(this);
            sensorManager.unregisterListener(MainActivity.this);
            sensorManager.registerListener(MainActivity.this, accSensor, SensorManager.SENSOR_DELAY_FASTEST);
            activeSensor = MainActivity.ACCEL_SENSOR;
//            isSensorSet = true;
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * handles the actual counting of steps relying on one of the two sensors,
     * whichever is active
     * @param event
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (activeSensor.equals(MainActivity.STEP_SENSOR) && event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            //if this is a Step detector event and we get a step sensor reading, store
            stepDisplay.setText(TEXT_NUM_STEPS + mStepsDBHelper.createStepsEntry());
        }else if (activeSensor.equals(MainActivity.ACCEL_SENSOR) && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER ) {
            //if this is an accel sensor mode, and we get an accel sensor reading, process
            simpleStepDetector.updateAccel(
                    event.timestamp, event.values[0], event.values[1], event.values[2]);
        }

    }

    /**
     * this is overriding the step function which only works in case of using accelerometer sensor
     * @param timeNs
     */
    @Override
    public void step(long timeNs) {
        stepDisplay.setText(TEXT_NUM_STEPS + mStepsDBHelper.createStepsEntry());
    }

}
