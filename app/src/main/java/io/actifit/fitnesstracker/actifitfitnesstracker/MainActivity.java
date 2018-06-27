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
    private SensorManager sensorManager;
    private Sensor accSensor;
    private static final String TEXT_NUM_STEPS = "Total Steps Today: ";
    private TextView stepDisplay;

    private StepsDBHelper mStepsDBHelper;

    //to utilize built-in step sensors
    private Sensor stepSensor;

    boolean isStepSensorPresent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stepDisplay = findViewById(R.id.step_display);
        Button BtnViewHistory = findViewById(R.id.btn_view_history);
        Button BtnPostSteemit = findViewById(R.id.btn_post_steemit);


        // Get an instance of the SensorManager
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        if(sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)//TYPE_STEP_COUNTER
                != null)
        {
            stepSensor =
                    sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);//TYPE_STEP_COUNTER
            sensorManager.registerListener(MainActivity.this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST);
            isStepSensorPresent = true;
        }else{
            // accSensor will host the default accelerometer sensor to be only used in case of earlier
            // SDK version / missing step_counter
            accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            simpleStepDetector = new SimpleStepDetector();
            simpleStepDetector.registerListener(this);
            sensorManager.registerListener(MainActivity.this, accSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }

        mStepsDBHelper = new StepsDBHelper(this);

        BtnViewHistory.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Intent intent=new Intent(MainActivity.this, StepHistoryActivity.class);
                //startActivityForResult(intent, 2);
                MainActivity.this.startActivity(intent);
                /*numSteps = 0;

                if (isStepSensorPresent){
                    sensorManager.registerListener(MainActivity.this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
                }else{
                    sensorManager.registerListener(MainActivity.this, accSensor, SensorManager.SENSOR_DELAY_FASTEST);
                }

                stepDisplay.setText("is step sensor available?"+isStepSensorPresent);*/
            }
        });


        BtnPostSteemit.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                //sensorManager.unregisterListener(MainActivity.this);
                Intent intent=new Intent(MainActivity.this, PostSteemitActivity.class);

                MainActivity.this.startActivity(intent);

            }
        });

        //set initial steps display value
        stepDisplay.setText(TEXT_NUM_STEPS + mStepsDBHelper.fetchTodayStepCount());


    }



    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isStepSensorPresent) {
            stepDisplay.setText(TEXT_NUM_STEPS + mStepsDBHelper.createStepsEntry());
        }else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            simpleStepDetector.updateAccel(
                    event.timestamp, event.values[0], event.values[1], event.values[2]);
        }

    }

    @Override
    public void step(long timeNs) {
        stepDisplay.setText(TEXT_NUM_STEPS + mStepsDBHelper.createStepsEntry());
    }

}
