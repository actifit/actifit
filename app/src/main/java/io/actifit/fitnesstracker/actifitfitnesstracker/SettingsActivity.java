package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;

import static io.actifit.fitnesstracker.actifitfitnesstracker.MainActivity.isStepSensorPresent;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);


        RadioButton step_sensor_rdbtn = findViewById(R.id.step_detector_sensor_rdbtn);
        RadioButton accl_sensor_rdbtn = findViewById(R.id.accelerometer_sensor_rdbtn);

        //grab the value of the isStepSensorPresent based on MainActivity to check if
        //step sensor is available or not
        if (isStepSensorPresent){
            //if it's available, enable the option to use it
            step_sensor_rdbtn.setEnabled(true);
            //also set it as default in this case. This will be adjusted in selection below
            step_sensor_rdbtn.setChecked(true);
        }else{
            step_sensor_rdbtn.setEnabled(false);
            //in this case, acc sensor needs to be checked as well
            accl_sensor_rdbtn.setChecked(true);
            //also set it as default in this case. This will be adjusted in selection below
            accl_sensor_rdbtn.setChecked(true);
        }

        //retrieving prior settings if already saved before
        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        //if (sharedPreferences.contains("actifitUser")){
        String activeSensor = (sharedPreferences.getString("activeSensor",""));

        //check which is the current active sensor
        //if the setting is manually set as ACCEL_SENSOR or if there is not step sensor, check it
        if (activeSensor.equals(MainActivity.ACCEL_SENSOR)){
            accl_sensor_rdbtn.setChecked(true);
        }else if (activeSensor.equals(MainActivity.STEP_SENSOR)){
            step_sensor_rdbtn.setChecked(true);
        }

        final Activity currentActivity = this;

        Button BtnSaveSettings = findViewById(R.id.btn_save_settings);
        BtnSaveSettings.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(final View arg0) {
                //need to adjust the selection of the sensors and store it

                //store as new preferences
                SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                //if (sharedPreferences.contains("actifitUser")){
                RadioButton accl_sensor_rdbtn = findViewById(R.id.accelerometer_sensor_rdbtn);
                //test for which option the user has set
                if (accl_sensor_rdbtn.isChecked()) {
                    editor.putString("activeSensor", MainActivity.ACCEL_SENSOR);
                }else{
                    editor.putString("activeSensor", MainActivity.STEP_SENSOR);
                }
                editor.commit();
                currentActivity.finish();

            }
        });

    }

}
