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


        final RadioButton metricSysRadioBtn = findViewById(R.id.metric_system);
        RadioButton usSystemRadioBtn = findViewById(R.id.us_system);

        //retrieving prior settings if already saved before
        SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

        String currentSystem = (sharedPreferences.getString("activeSystem",""));

        //check which is the current active system
        //if the setting is manually set as US System or default Merci value (else)
        if (currentSystem.equals(getString(R.string.us_system))){
            usSystemRadioBtn.setChecked(true);
        }else{
            metricSysRadioBtn.setChecked(true);
        }

        final Activity currentActivity = this;

        Button BtnSaveSettings = findViewById(R.id.btn_save_settings);
        BtnSaveSettings.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(final View arg0) {
                //need to adjust the selection of the sensors and store it

                //store as new preferences
                SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();

                //test for which option the user has set
                if (metricSysRadioBtn.isChecked()) {
                    editor.putString("activeSystem", getString(R.string.metric_system));
                }else{
                    editor.putString("activeSystem", getString(R.string.us_system));
                }
                editor.commit();

                //currentActivity.finish();

            }
        });

    }

}
