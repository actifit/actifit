package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import static io.actifit.fitnesstracker.actifitfitnesstracker.MainActivity.isStepSensorPresent;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);


        //grab instances of settings components
        final RadioButton metricSysRadioBtn = findViewById(R.id.metric_system);
        final RadioButton usSystemRadioBtn = findViewById(R.id.us_system);

        final CheckBox aggBgTrackingChckBox = findViewById(R.id.background_tracking);

        final CheckBox donateCharityChckBox = findViewById(R.id.donate_charity);
        final Spinner charitySelected = findViewById(R.id.charity_options);

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

        //grab aggressive mode setting and update checkbox accordingly
        String aggModeEnabled = sharedPreferences.getString("aggressiveBackgroundTracking",getString(R.string.aggr_back_tracking_off));
        System.out.println(">>>>[Actifit] Agg Mode:"+aggModeEnabled);
        System.out.println(">>>>[Actifit] Agg Mode Test:"+aggModeEnabled.equals(getString(R.string.aggr_back_tracking_on)));

        aggBgTrackingChckBox.setChecked(aggModeEnabled.equals(getString(R.string.aggr_back_tracking_on)));

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

                //PowerManager pm = ActivityMonitorService.getPowerManagerInstance();
                PowerManager.WakeLock  wl = ActivityMonitorService.getWakeLockInstance();

                if (aggBgTrackingChckBox.isChecked()){
                    editor.putString("aggressiveBackgroundTracking", getString(R.string.aggr_back_tracking_on));

                    //enable wake lock to ensure tracking functions in the background
                    if (!wl.isHeld()) {
                        System.out.println(">>>>[Actifit]Settings AGG MODE ON");
                        wl.acquire();
                    }
                }else{
                    editor.putString("aggressiveBackgroundTracking", getString(R.string.aggr_back_tracking_off));
                    //enable wake lock to ensure tracking functions in the background
                    if (wl.isHeld()) {
                        System.out.println(">>>>[Actifit]Settings AGG MODE OFF");
                        wl.release();
                    }
                }

                //reset value first
                editor.putString("selectedCharity", "");

                //check if charity mode is on and a charity has been selected
                if (donateCharityChckBox.isChecked()){
                    if (charitySelected.getSelectedItem() !=null){
                        editor.putString("selectedCharity", charitySelected.getSelectedItem().toString());
                    }
                }

                editor.commit();

                currentActivity.finish();

            }
        });

        //grab charity list
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);

        // This holds the url to connect to the API and grab the balance.
        String charityUrl = getString(R.string.charity_list_api_url);

        JsonArrayRequest charitiesRequest = new JsonArrayRequest(Request.Method.GET,
                charityUrl, null, new Response.Listener<JSONArray>(){

            @Override
            public void onResponse(JSONArray transactionListArray) {

                ArrayList<String> transactionList = new ArrayList<String>();
                Spinner charityOptions = findViewById(R.id.charity_options);
                // Handle the result
                try {

                    for (int i = 0; i < transactionListArray.length(); i++) {
                        // Retrieve each JSON object within the JSON array
                        JSONObject jsonObject = transactionListArray.getJSONObject(i);

                        // Adds strings from the current object to the data string
                        transactionList.add(jsonObject.getString("charity_name"));
                    }
                    // convert content to adapter display, and render it
                    ArrayAdapter<String> arrayAdapter =
                            new ArrayAdapter<String>(getApplicationContext(),android.R.layout.simple_list_item_1, transactionList){
                                @Override
                                public View getView(int position, View convertView, ViewGroup parent){
                                    // Get the Item from ListView
                                    View view = super.getView(position, convertView, parent);

                                    // Initialize a TextView for ListView each Item
                                    TextView tv = view.findViewById(android.R.id.text1);

                                    // Set the text color of TextView (ListView Item)
                                    tv.setTextColor(Color.BLACK);
                                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);

                                    // Generate ListView Item using TextView
                                    return view;
                                }
                            };

                    charityOptions.setAdapter(arrayAdapter);

                    //choose a charity if one is already selected before

                    SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

                    String currentCharity = (sharedPreferences.getString("selectedCharity",""));

                    if (!currentCharity.equals("")){
                        donateCharityChckBox.setChecked(true);
                        charitySelected.setSelection(arrayAdapter.getPosition(currentCharity));
                    }

                    //actifitTransactions.setText("Response is: "+ response);
                }catch (Exception e) {
                    System.out.println(">>>>[Actifit]: Volley error"+e.getMessage());
                    e.printStackTrace();
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                System.out.println(">>>>[Actifit]: Volley response error"+error.getMessage());
                error.printStackTrace();
            }
        });

        // Add charities request to be processed
        queue.add(charitiesRequest);

    }

}
