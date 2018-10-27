package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.NumberPicker;
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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Random;

import static io.actifit.fitnesstracker.actifitfitnesstracker.MainActivity.isStepSensorPresent;

public class SettingsActivity extends AppCompatActivity {

    private NumberPicker hourOptions, minOptions;
    private AlarmManager alarmManager;
    private PendingIntent alarmIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);


        //display version number
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            TextView version_info = findViewById(R.id.version_info);
            version_info.setText("Actifit App Version: "+version);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        //grab instances of settings components
        final RadioButton metricSysRadioBtn = findViewById(R.id.metric_system);
        final RadioButton usSystemRadioBtn = findViewById(R.id.us_system);

        final CheckBox aggBgTrackingChckBox = findViewById(R.id.background_tracking);

        final CheckBox donateCharityChckBox = findViewById(R.id.donate_charity);

        final CheckBox reminderSetChckBox = findViewById(R.id.reminder_settings);

        Spinner charitySelected = findViewById(R.id.charity_options);

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

        //need to update the info based on charity selection
        charitySelected.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            TextView charityInfo = findViewById(R.id.charity_info);
            Spinner charitySelected = findViewById(R.id.charity_options);

            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                String fullUrl = getString(R.string.steemit_url)+'@'+((Charity)charitySelected.getSelectedItem()).getCharityName();
                charityInfo.setText(fullUrl);
                charityInfo.setMovementMethod(LinkMovementMethod.getInstance());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                charityInfo.setText("");
            }
        });


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
                    Spinner charitySelected = findViewById(R.id.charity_options);
                    if (charitySelected.getSelectedItem() !=null){
                        editor.putString("selectedCharity", ((Charity)charitySelected.getSelectedItem()).getCharityName());
                        editor.putString("selectedCharityDisplayName", charitySelected.getSelectedItem().toString());
                    }
                }

                //unset alarm and the need to restart Actifit notification reminder after reboot
                alarmManager = (AlarmManager) getApplicationContext()
                        .getSystemService(Context.ALARM_SERVICE);
                Intent intent = new Intent(getApplicationContext(), ReminderNotificationService.class);
                alarmIntent = PendingIntent.getService(getApplicationContext()
                        , ReminderNotificationService.NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);

                //unset any existing alarms first
                alarmManager.cancel(alarmIntent);



                //check if reminder setting is on
                if (reminderSetChckBox.isChecked()) {
                    editor.putString("selectedReminderHour", "" + hourOptions.getValue());
                    editor.putString("selectedReminderMin", "" + minOptions.getValue());

                    //set the alarm at user defined value
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(System.currentTimeMillis());
                    calendar.set(Calendar.HOUR_OF_DAY, hourOptions.getValue());
                    calendar.set(Calendar.MINUTE, minOptions.getValue());

                    //PendingIntent.getService(currentActivity, ReminderNotificationService.NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                    intent.putExtra("NOTIFICATION_ID", ReminderNotificationService.NOTIFICATION_ID);

                    System.out.println(">>>>[Actifit]: set alarm manager"+hourOptions.getValue()+" "+minOptions.getValue());

                    alarmIntent = PendingIntent.getBroadcast(getApplicationContext()
                            , 0, intent, 0);

                    alarmManager = (AlarmManager) getApplicationContext()
                            .getSystemService(Context.ALARM_SERVICE);

                    //specify alarm interval to be every 24 hours at user defined slot
                    alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                            1000 * 60 * 60 * 24, alarmIntent);

                    /*try {
                        alarmIntent.send();
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }

                    Intent sampleIntent = new Intent(getApplicationContext(), MainActivity.class);
                    PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, sampleIntent, 0);


                    NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext(), getString(R.string.actifit_channel_remind_ID))
                            .setSmallIcon(R.drawable.actifit_logo)
                            .setContentTitle("sample")
                            .setContentText("notify me")
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT);

                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());

                    notificationManager.notify(ReminderNotificationService.NOTIFICATION_ID, mBuilder.build());

                        */


                }

                editor.apply();

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

                ArrayList<Charity> transactionList = new ArrayList<Charity>();
                Spinner charityOptions = findViewById(R.id.charity_options);
                // Handle the result
                try {

                    for (int i = 0; i < transactionListArray.length(); i++) {
                        // Retrieve each JSON object within the JSON array
                        JSONObject jsonObject = transactionListArray.getJSONObject(i);

                        // Adds strings from the current object to the data string
                        transactionList.add(new Charity(jsonObject.getString("charity_name"), jsonObject.getString("display_name")));
                    }
                    // convert content to adapter display, and render it
                    ArrayAdapter<Charity> arrayAdapter  =
                            new ArrayAdapter<Charity>(getApplicationContext(),android.R.layout.simple_list_item_1, transactionList ){
                                @NonNull
                                @Override
                                public View getView(int position, View convertView, @NonNull ViewGroup parent){
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
                    String currentCharityDisplayName = (sharedPreferences.getString("selectedCharityDisplayName",""));

                    if (!currentCharity.equals("")){
                        Spinner charitySelected = findViewById(R.id.charity_options);
                        TextView charityInfo = findViewById(R.id.charity_info);

                        donateCharityChckBox.setChecked(true);
                        charitySelected.setSelection(arrayAdapter.getPosition(new Charity(currentCharity,currentCharityDisplayName)));
                        String fullUrl = getString(R.string.steemit_url)+'@'+((Charity)charitySelected.getSelectedItem()).getCharityName();
                        charityInfo.setText(fullUrl);
                        charityInfo.setMovementMethod(LinkMovementMethod.getInstance());
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


        //set proper reminder times

        hourOptions = findViewById(R.id.reminder_hour_options);

        hourOptions.setMinValue(0);
        hourOptions.setMaxValue(23);
        //hourOptions.setWrapSelectorWheel(false);

        minOptions = findViewById(R.id.reminder_min_options);

        minOptions.setMinValue(0);
        minOptions.setMaxValue(59);
        //minOptions.setWrapSelectorWheel(false);

        //formatting display of reminder times to add extra left zeros (hours and mins)
        NumberPicker.Formatter formatter = new NumberPicker.Formatter(){
            @Override
            public String format(int i) {
                if (i<10){
                    return "0"+i;
                }
                return ""+i;
            }
        };

        hourOptions.setFormatter(formatter);
        minOptions.setFormatter(formatter);

        //get pre-saved values for reminder setting
        String reminderHour = (sharedPreferences.getString("selectedReminderHour",""));
        String reminderMin = (sharedPreferences.getString("selectedReminderMin",""));

        //check which is the current active system
        //if the setting is manually set as US System or default Merci value (else)
        if (!reminderHour.equals("") && !reminderMin.equals("")){
            try {
                hourOptions.setValue(Integer.parseInt(reminderHour));
                minOptions.setValue(Integer.parseInt(reminderMin));
                //we were able to grab proper values, set as checked
                reminderSetChckBox.setChecked(true);
            }catch(Exception e){
                e.printStackTrace();
            }
        }else{
            metricSysRadioBtn.setChecked(true);
        }

    }

}
