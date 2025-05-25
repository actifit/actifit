package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;

import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SettingsActivity extends BaseActivity {

    private NumberPicker hourOptions, minOptions;
    private AlarmManager alarmManager;
    private PendingIntent alarmIntent;
    private String fullSPPay = "full_SP_Pay";
    private String sbdSPPay = "50_50_SBD_SP_Pay";
    private String liquidPay = "liquid_Pay";
    private String declinePay = "decline_Pay";
    public static boolean languageModified = false;
    public static int langChoice = -1;
    private RequestQueue queue;
    public static JSONObject userServerSettings;

    private ListView notifListView;
    public JSONArray notificationTypes;
    private NotificationTypeEntryAdapter notificationAdapter;
    private ArrayList<SingleNotificationModel> finalList;
    private Context cntxt = this;

    private int notifSettingsHeight = 0;

    private String accessToken;

    private EditText activeKey, fundsPassword, voteWeight;

    Button qrCodeBtn;
    GmsBarcodeScanner scanner;

    TextView logoutLink;

    //private ImageView iconSun; // Optional
    //private ImageView iconMoon;
    private static final String PREF_KEY_DARK_MODE = "theme_mode";

    /*@Bind(R.id.main_toolbar)
    Toolbar toolbar;*/


    /*public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        case "shared_network":
        if (sharedPreferences.getBoolean(key, false) == true) {
            com.exerpic.si.aar.Activity.create(this);
        } else {
            com.exerpic.si.aar.Activity.cancel(this);
        }

        break;
    }*/

    private void prepQRCode(){
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_AZTEC)
                .enableAutoZoom()
                .build();
        scanner = GmsBarcodeScanning.getClient(this, options);
        //GmsBarcodeScanning.getClient(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        activeKey = findViewById(R.id.activeKey);
        fundsPassword = findViewById(R.id.fundsPassword);
        voteWeight = findViewById(R.id.votePercent);

        //grab instances of settings components
        final RadioButton metricSysRadioBtn = findViewById(R.id.metric_system);
        final RadioButton usSystemRadioBtn = findViewById(R.id.us_system);

        final RadioButton notificationsActive = findViewById(R.id.notifications_active);
        final RadioButton notificationsInactive = findViewById(R.id.notifications_inactive);

        final CheckBox aggBgTrackingChckBox = findViewById(R.id.background_tracking);

        final CheckBox donateCharityChckBox = findViewById(R.id.donate_charity);

        final CheckBox reminderSetChckBox = findViewById(R.id.reminder_settings);

        final RadioButton deviceSensorsBtn = findViewById(R.id.device_sensors);
        final RadioButton fitbitBtn = findViewById(R.id.fitbit);
        final LinearLayout aggModeSection = findViewById(R.id.background_tracking_section);

        final LinearLayout fitbitSettingsSection = findViewById(R.id.fitbit_settings_section);
        final CheckBox fitbitMeasurementsChckBox = findViewById(R.id.fitbit_measurements);

        final RadioButton fullSPayRadioBtn = findViewById(R.id.full_sp_pay);
        final RadioButton sbdSPPayRadioBtn = findViewById(R.id.sbd_sp_pay);
        final RadioButton liquidPayRadioBtn = findViewById(R.id.liquid_pay);
        final RadioButton declinePayRadioBtn = findViewById(R.id.decline_pay);

        final Spinner languageSelected = findViewById(R.id.language_picker);

        //final RadioButton hiveSteemOptionRadioBtn = findViewById(R.id.hive_steem_option);
        //final RadioButton hiveOnlyOptionRadioBtn = findViewById(R.id.hive_only_option);
        final CheckBox hiveOptionCheckbox = findViewById(R.id.hive_option);
        final CheckBox steemOptionCheckbox = findViewById(R.id.steem_option);
        final CheckBox blurtOptionCheckbox = findViewById(R.id.blurt_option);

        final CheckBox showPendingRewardsCheckbox = findViewById(R.id.show_pending_rewards_main);

        final CheckBox showDailyTipsCheckbox = findViewById(R.id.show_daily_tips_main);

        final CheckBox showBatteryOptimizationTipCheckbox = findViewById(R.id.show_battery_optimization_tip);

        logoutLink = findViewById(R.id.logout_action);

        notifListView = findViewById(R.id.notif_settings_list);

        Spinner charitySelected = findViewById(R.id.charity_options);

        SharedPreferences sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);
        boolean isDarkModeEnabled = sharedPreferences.getBoolean(PREF_KEY_DARK_MODE, false);
        if (isDarkModeEnabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); // Or MODE_NIGHT_FOLLOW_SYSTEM
        }

        SwitchCompat darkModeSwitch = findViewById(R.id.darkModeSwitch);
        //iconSun = findViewById(R.id.icon_sun);
        //iconMoon = findViewById(R.id.icon_moon);

        darkModeSwitch.setChecked(isDarkModeEnabled);
        //updateSunMoonIcons(isDarkModeEnabled);

        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // isChecked is the new state of the switch

            // 1. Save the new preference value
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(PREF_KEY_DARK_MODE, isChecked);
            editor.apply(); // Use apply() for asynchronous save

            // 2. Update the icon visibility (Optional)
            //updateSunMoonIcons(isChecked);

            // 3. Apply the new theme mode
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                // When dark mode is toggled off, switch back to Light Mode
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                // If you want turning off the toggle to mean "follow system setting":
                // AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }

            // Note: Calling setDefaultNightMode() typically causes the Activity to be recreated,
            // so the new theme is applied. The onCreate() method will run again.
        });


        qrCodeBtn = findViewById(R.id.qrCodeButton);

        prepQRCode();

        qrCodeBtn.setOnClickListener(view ->{

            if (scanner != null) {
                scanner
                        .startScan()
                        .addOnSuccessListener(
                                barcode -> {
                                    // Task completed successfully
                                    String rawValue = barcode.getRawValue();
                                    //inject value into the active key text
                                    activeKey.setText(rawValue);
                                    //keyEntry.setText(rawValue);
                                    //attempt login
                                    //loginBtn.performClick();

                                })
                        .addOnCanceledListener(
                                () -> {
                                    // Task canceled
                                })
                        .addOnFailureListener(
                                e -> {
                                    // Task failed with an exception
                                });
            }
        });

        finalList = new ArrayList<>();

        logoutLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //remove logged in credentials

                if (!MainActivity.username.equals("")) {

                    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which) {
                                case DialogInterface.BUTTON_POSITIVE:
                                    //cancel
                                    final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);
                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.remove("actifitUser");
                                    editor.remove("actifitPst");

                                    editor.remove("userRank");
                                    editor.remove("userRankUpdateDate");
                                    editor.remove("actvKey");
                                    editor.remove("fundsPass");
                                    editor.remove(getString(R.string.three_speak_saved_token));
                                    editor.remove(getString(R.string.sting_chat_comm_count));

                                    editor.remove(getString(R.string.daily_free_reward));
                                    editor.remove(getString(R.string.daily_5k_reward));
                                    editor.remove(getString(R.string.daily_7k_reward));
                                    editor.remove(getString(R.string.daily_10k_reward));

                                    editor.apply();
                                    LoginActivity.accessToken = "";
                                    MainActivity.username = "";
                                    MainActivity.userRank = "";
                                    MainActivity.userFullBalance = 0.0;
                                    LoginActivity.accessToken = "";
                                    finish();
                                    overridePendingTransition(0, 0);
                                    //startActivity(getIntent());
                                    Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                                    SettingsActivity.this.startActivity(intent);
                                    overridePendingTransition(0, 0);
                                    break;
                            }
                        }
                    };

                    //verify with user first
                    AlertDialog.Builder builder = new AlertDialog.Builder(cntxt);
                    builder.setMessage(getString(R.string.logout_confirmation) + " ")
                            .setPositiveButton(getString(R.string.yes_button), dialogClickListener)
                            .setNegativeButton(getString(R.string.no_button), dialogClickListener).show();

                }

            }

        });

        //oxylabs preferences
        /*SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.edit().putBoolean("shared_network", com.exerpic.si.aar.Activity.isEnabled()).apply();
        PreferenceManager.addPreferencesFromResource(R.xml.prefs);*/

        //display version number
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            TextView version_info = findViewById(R.id.version_info);
            version_info.setText(getString(R.string.app_version_string) + " " + version);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        //grab charity list
        // Instantiate the RequestQueue.
        queue = Volley.newRequestQueue(this);

        //if there is an assigned user, fetch his settings
        //final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
        final String username = sharedPreferences.getString("actifitUser","");

        activeKey.setText(sharedPreferences.getString("actvKey", ""));
        fundsPassword.setText(sharedPreferences.getString("fundsPass", ""));

        //display whether user has elected not to get main screen earnings notification
        showPendingRewardsCheckbox.setChecked( !(sharedPreferences.getBoolean(getString(R.string.donotshowrewards),true)));

        showDailyTipsCheckbox.setChecked( !(sharedPreferences.getBoolean(getString(R.string.donotshowtips),true)));

        showBatteryOptimizationTipCheckbox.setChecked( !(sharedPreferences.getBoolean(getString(R.string.donotshowbatteryoptimization),true)));

        if (!username.equals("")) {
            //fetch user global settings - server based

            String pkey = sharedPreferences.getString("actifitPst","");

            //authorize user login based on credentials if user is already verified
            if (!pkey.equals("")) {
                String loginAuthUrl = Utils.apiUrl(this)+getString(R.string.login_auth);


                JSONObject loginSettings = new JSONObject();
                try {
                    loginSettings.put(getString(R.string.username_param), username);
                    loginSettings.put(getString(R.string.pkey_param), pkey);
                    loginSettings.put(getString(R.string.bchain_param), "HIVE");//default always HIVE
                    loginSettings.put(getString(R.string.keeploggedin_param), false);//TODO make dynamic
                    loginSettings.put(getString(R.string.login_source), getString(R.string.android) + BuildConfig.VERSION_NAME);
                } catch (JSONException e) {
                    //Log.e(MainActivity.TAG, e.getMessage());
                }

                //grab auth token for logged in user
                JsonObjectRequest loginRequest = new JsonObjectRequest(Request.Method.POST,
                        loginAuthUrl, loginSettings,
                        new Response.Listener<JSONObject>() {

                            @Override
                            public void onResponse(JSONObject response) {
                                //store token for reuse when saving settings
                                try {
                                    if (response.has("success")) {
                                        Log.d(MainActivity.TAG, response.toString());
                                        accessToken = response.getString(getString(R.string.login_token));
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }

                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                // error
                                Log.e(MainActivity.TAG, "Login error");
                            }
                        });

                queue.add(loginRequest);

            }


            // This holds the url to connect to the API and grab the settings.
            String notTypeUrl = Utils.apiUrl(this)+ getString(R.string.notification_types);

            JsonArrayRequest notificationTypeRequest = new JsonArrayRequest(Request.Method.GET,
                    notTypeUrl, null, new Response.Listener<JSONArray>() {

                @Override
                public void onResponse(JSONArray _notificationTypes) {
                    notificationTypes = _notificationTypes;
                    Log.d(MainActivity.TAG, "Fetched notification types");
                    Log.d(MainActivity.TAG, notificationTypes.toString());

                    //populate adapter for proper display
                    // Handle the result
                    try {



                        for (int i = 0; i < _notificationTypes.length(); i++) {
                            JSONObject jsonObject = _notificationTypes.getJSONObject(i);
                            SingleNotificationModel notfEntry = new SingleNotificationModel(jsonObject, false);
                            finalList.add(notfEntry);
                        }

                        // Create the adapter to convert the array to views
                        notificationAdapter = new NotificationTypeEntryAdapter(cntxt, finalList);

                        // This holds the url to connect to the API and grab the settings.
                        String settingsUrl = Utils.apiUrl(cntxt)+ getString(R.string.fetch_settings)
                                +"/" + username;

                        JsonObjectRequest settingsRequest = new JsonObjectRequest(Request.Method.GET,
                                settingsUrl, null, new Response.Listener<JSONObject>() {

                            @Override
                            public void onResponse(JSONObject settingsList) {
                                userServerSettings = settingsList;
                                Log.d(MainActivity.TAG, "Fetched settings");
                                Log.d(MainActivity.TAG, userServerSettings.toString());

                                JSONObject setgs = null;
                                try {
                                    setgs = userServerSettings.getJSONObject("settings");
                                    MainActivity.userSettings = setgs;

                                    if (setgs != null){


                                        //load default vote weight percentage
                                        voteWeight.setText(Utils.grabUserDefaultVoteWeight());


                                        try {
                                            if (setgs.has("notifications_active") && !setgs.getBoolean("notifications_active")){
                                                notificationsInactive.setChecked(true);
                                                notifListView.setVisibility(View.GONE);
                                            }else{
                                                notificationsActive.setChecked(true);
                                                notifListView.setVisibility(View.VISIBLE);
                                            }
                                        } catch (JSONException ex) {
                                            ex.printStackTrace();
                                        }


                                        try {
                                            //hive posting always enabled
                                            hiveOptionCheckbox.setChecked(true);

                                            if (setgs.has("post_target_bchain") ){

                                                if (setgs.getString("post_target_bchain").equals("BOTH")) {
                                                    //Array chainArray = setgs.getJSONArray("post_target_bchain");
                                                    //&& (Array)(setgs.getString("post_target_bchain")){
                                                    steemOptionCheckbox.setChecked(true);
                                                    blurtOptionCheckbox.setChecked(true);
                                                }else{
                                                    if (setgs.getString("post_target_bchain").contains("Steem")
                                                        || setgs.getString("post_target_bchain").contains("STEEM") ) {
                                                        steemOptionCheckbox.setChecked(true);
                                                    }else{
                                                        steemOptionCheckbox.setChecked(false);
                                                    }
                                                    if (setgs.getString("post_target_bchain").contains("Blurt")
                                                            || setgs.getString("post_target_bchain").contains("BLURT") ) {
                                                        blurtOptionCheckbox.setChecked(true);
                                                    }else{
                                                        blurtOptionCheckbox.setChecked(false);
                                                    }
                                                }
                                                /*if (setgs.getString("post_target_bchain").equals("HIVE")) {
                                                    hiveOnlyOptionRadioBtn.setChecked(true);
                                                } else {
                                                    hiveSteemOptionRadioBtn.setChecked(true);
                                                }*/
                                            }else{
                                                //default all enabled
                                                steemOptionCheckbox.setChecked(true);
                                                blurtOptionCheckbox.setChecked(true);
                                            }
                                        } catch (JSONException ex) {
                                            ex.printStackTrace();
                                        }
                                    }

                                    //adjust height to fit content

                                    int desiredWidth = View.MeasureSpec.makeMeasureSpec(notifListView.getWidth(), View.MeasureSpec.AT_MOST);
                                    for (int i = 0; i < notificationAdapter.getCount(); i++) {
                                        SingleNotificationModel entry = notificationAdapter.getItem(i);

                                        View listItem = notificationAdapter.getView(i, null, notifListView);
                                        //TextView optionVal = listItem.findViewById(R.id.notification_type);
                                        String notifCat = entry.type;
                                        Boolean isSel = true;
                                        //set as off only if set by user, otherwise turn on
                                        if (setgs != null){
                                            Log.d(MainActivity.TAG, notifCat);
                                            try {
                                                if (setgs.has(notifCat) && !setgs.getBoolean(notifCat)) {
                                                    isSel = false;
                                                }
                                            }catch(JSONException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        entry.isChecked = isSel;

                                        listItem.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
                                        notifSettingsHeight += listItem.getMeasuredHeight();
                                    }

                                    ViewGroup.LayoutParams params = notifListView.getLayoutParams();
                                    params.height = notifSettingsHeight + (notifListView.getDividerHeight() * (notificationAdapter.getCount() - 1));
                                    notifListView.setLayoutParams(params);
                                    //notifListView.requestLayout();
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }


                                //set as ready view adapter for rendering
                                notifListView.setAdapter(notificationAdapter);
                            }
                        }, new Response.ErrorListener() {

                            @Override
                            public void onErrorResponse(VolleyError error) {
                                // TODO: Handle error
                                Log.e(MainActivity.TAG, "error connecting");
                            }
                        });

                        // Add request to be processed
                        queue.add(settingsRequest);



                        //notifListView.requestLayout();

                    } catch (Exception error) {
                        //Log.e(MainActivity.TAG, error.getMessage());


                    }
                }
            }, new Response.ErrorListener() {

                @Override
                public void onErrorResponse(VolleyError error) {
                    // TODO: Handle error
                    Log.e(MainActivity.TAG, "network error");
                }
            });

            // Add request to be processed
            queue.add(notificationTypeRequest);
        }


        //retrieving prior settings if already saved before
        //SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

        String currentSystem = (sharedPreferences.getString("activeSystem",""));

        //check which is the current active system
        //if the setting is manually set as US System or default Metric value (else)
        if (currentSystem.equals(getString(R.string.us_system_ntt))){
            usSystemRadioBtn.setChecked(true);
        }else{
            metricSysRadioBtn.setChecked(true);
        }

        //set proper selection for notification status
        /*
        Boolean currentNotifStatus = (sharedPreferences.getBoolean(getString(R.string.notification_status),true));

        if (currentNotifStatus){
            notificationsActive.setChecked(true);
        }else{
            notificationsInactive.setChecked(true);
        }
        */

        //handle beneficiaries

        String data = sharedPreferences.getString("AdditionalBeneficiaries", "");

        // Populate the beneficiary table
        updateBeneficiaryTable(data);

        Button addBeneficBtn = findViewById(R.id.addBeneficBtn);
        addBeneficBtn.setOnClickListener(v -> {
            EditText beneficiaryField = findViewById(R.id.extraBeneficiary);
            EditText percentageField = findViewById(R.id.beneficPerct);

            String beneficiary = beneficiaryField.getText().toString().trim();
            String percentageStr = percentageField.getText().toString().trim();

            /*SharedPreferences.Editor editor1 = sharedPreferences.edit();
            editor1.remove("AdditionalBeneficiaries");
            editor1.apply();*/

            if (!beneficiary.isEmpty() && !percentageStr.isEmpty()) {
                try {
                    int percentage = Integer.parseInt(percentageStr);

                    // Convert percentage to weight (percentage * 100)
                    int weight = percentage * 100;

                    String existingData = sharedPreferences.getString("AdditionalBeneficiaries", "[]");

                    // Parse existing JSON array
                    JSONArray beneficiariesArray = new JSONArray(existingData);

                    // Calculate current total weight
                    int totalWeight = 0;
                    for (int i = 0; i < beneficiariesArray.length(); i++) {
                        JSONObject beneficiaryObj = beneficiariesArray.getJSONObject(i);
                        totalWeight += beneficiaryObj.getInt("weight");
                    }

                    // Validate that adding this weight doesn't exceed 9500
                    if (totalWeight + weight > 9500) {
                        Toast.makeText(this, "Total weight cannot exceed 95%", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Create new beneficiary object
                    JSONObject newBeneficiary = new JSONObject();
                    newBeneficiary.put("account", beneficiary);
                    newBeneficiary.put("weight", weight);

                    // Add new beneficiary to array
                    beneficiariesArray.put(newBeneficiary);

                    // Save updated JSON array back to SharedPreferences
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("AdditionalBeneficiaries", beneficiariesArray.toString());
                    editor.apply();

                    // Update UI
                    updateBeneficiaryTable(beneficiariesArray.toString());
                    beneficiaryField.setText("");
                    percentageField.setText("");

                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Enter a valid percentage", Toast.LENGTH_SHORT).show();
                } catch (JSONException e) {
                    Toast.makeText(this, "Error saving data", Toast.LENGTH_SHORT).show();
                }
            }
        });

        //select proper language

        languageSelected.setSelection(LocaleManager.getSelectedLang(this));

        //hook for the change event to ensure we update language on main screen
        languageSelected.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                //invalidate current language
                SettingsActivity.languageModified = true;
                SettingsActivity.langChoice = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        //check which pay mode for reports to be used
        String reportPayMode = sharedPreferences.getString("reportSTEEMPayMode",sbdSPPay);
        if (reportPayMode.equals(fullSPPay)){
            fullSPayRadioBtn.setChecked(true);
        }else if (reportPayMode.equals(liquidPay)){
            liquidPayRadioBtn.setChecked(true);
        }else if (reportPayMode.equals(declinePay)){
            declinePayRadioBtn.setChecked(true);
        }else{
            //default
            sbdSPPayRadioBtn.setChecked(true);
        }


        //check which data source is active now

        String dataTrackingSystem = sharedPreferences.getString("dataTrackingSystem",
                getString(R.string.device_tracking_ntt));
        if (dataTrackingSystem.equals(getString(R.string.fitbit_tracking_ntt))){
            fitbitBtn.setChecked(true);

            //also hide aggressive mode if fitbit is on, and show fitbit configuration
            aggModeSection.setVisibility(View.INVISIBLE);
            fitbitSettingsSection.setVisibility(View.VISIBLE);
        }else{
            deviceSensorsBtn.setChecked(true);
            //alternatively hide fitbit settings and show aggressive mode settings
            aggModeSection.setVisibility(View.VISIBLE);
            fitbitSettingsSection.setVisibility(View.INVISIBLE);
        }

        RadioGroup trackingModeRadiogroup = findViewById(R.id.tracking_mode_radiogroup);

        //capture change event for radiobutton group to reflect on user available options
        trackingModeRadiogroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {

                if (deviceSensorsBtn.isChecked()){
                    aggModeSection.setVisibility(View.VISIBLE);
                    fitbitSettingsSection.setVisibility(View.INVISIBLE);
                }else{
                    aggModeSection.setVisibility(View.INVISIBLE);
                    fitbitSettingsSection.setVisibility(View.VISIBLE);
                }
            }
        });

        //capture change event for radiobutton group to reflect on user available options
        notificationsActive.setOnCheckedChangeListener(new RadioButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton group, boolean checked) {

                if (notificationsActive.isChecked()){
                    notifListView.setVisibility(View.VISIBLE);
                }else{
                    notifListView.setVisibility(View.GONE);
                }
            }
        });

        //grab aggressive mode setting and update checkbox accordingly
        String aggModeEnabled = sharedPreferences.getString("aggressiveBackgroundTracking",getString(R.string.aggr_back_tracking_off_ntt));
        Log.d(MainActivity.TAG,">>>>[Actifit] Agg Mode:"+aggModeEnabled);
        Log.d(MainActivity.TAG,">>>>[Actifit] Agg Mode Test:"+aggModeEnabled.equals(getString(R.string.aggr_back_tracking_on_ntt)));

        aggBgTrackingChckBox.setChecked(aggModeEnabled.equals(getString(R.string.aggr_back_tracking_on_ntt)));

        //grab fitbit setting and update checkbox accordingly
        String fitbitMeasurements = sharedPreferences.getString("fitbitMeasurements",getString(R.string.fitbit_measurements_on_ntt));
        fitbitMeasurementsChckBox.setChecked(fitbitMeasurements.equals(getString(R.string.fitbit_measurements_on_ntt)));


        final Activity currentActivity = this;

        //need to update the info based on charity selection
        charitySelected.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            TextView charityInfo = findViewById(R.id.charity_info);
            //Spinner charitySelected = findViewById(R.id.charity_options);

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
        BtnSaveSettings.setOnClickListener(arg0 -> {
            //need to adjust the selection of the sensors and store it

            //store as new preferences
            SharedPreferences sharedPreferences1 = getSharedPreferences("actifitSets",MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences1.edit();

            //test for which option the user has set
            if (metricSysRadioBtn.isChecked()) {
                editor.putString("activeSystem", getString(R.string.metric_system_ntt));
            }else{
                editor.putString("activeSystem", getString(R.string.us_system_ntt));
            }

            //store whether user wants to get rewards popup notification on main screen
            editor.putBoolean(getString(R.string.donotshowrewards), !showPendingRewardsCheckbox.isChecked());

            editor.putBoolean(getString(R.string.donotshowtips), !showDailyTipsCheckbox.isChecked());

            editor.putBoolean(getString(R.string.donotshowbatteryoptimization), !showBatteryOptimizationTipCheckbox.isChecked());

            //store selected STEEM pay mode

            //check which pay mode for reports to be used and store it
            if (fullSPayRadioBtn.isChecked()){
                editor.putString("reportSTEEMPayMode", fullSPPay);
            }else if (liquidPayRadioBtn.isChecked()){
                editor.putString("reportSTEEMPayMode", liquidPay);
            }else if (declinePayRadioBtn.isChecked()){
                editor.putString("reportSTEEMPayMode", declinePay);
            }else{
                //default
                editor.putString("reportSTEEMPayMode", sbdSPPay);
            }



            //store active key to use where and if needed
            editor.putString("actvKey",activeKey.getText().toString());

            //store funds password to use where and if needed
            editor.putString("fundsPass", fundsPassword.getText().toString());

            //store vote weight percentage


            //update language

            //SettingsActivity.langChoice = languageSelected.getSelectedItemPosition();

            //store selected tracking system
            if (fitbitBtn.isChecked()) {
                editor.putString("dataTrackingSystem", getString(R.string.fitbit_tracking_ntt));

                //also deactivate running sensors if any instance is running
                try {
                    ActivityMonitorService mSensorService = MainActivity.getmSensorService();
                    if (mSensorService != null) {
                        stopService(MainActivity.getmServiceIntent());
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
            }else{
                editor.putString("dataTrackingSystem", getString(R.string.device_tracking_ntt));
            }


            //PowerManager pm = ActivityMonitorService.getPowerManagerInstance();
            PowerManager.WakeLock  wl = ActivityMonitorService.getWakeLockInstance();

            //we need to enable aggressive checking only if device sensors are functioning,
            //otherwise it's pointless
            if (aggBgTrackingChckBox.isChecked()){
                editor.putString("aggressiveBackgroundTracking", getString(R.string.aggr_back_tracking_on_ntt));

            }else{
                editor.putString("aggressiveBackgroundTracking", getString(R.string.aggr_back_tracking_off_ntt));
                //enable wake lock to ensure tracking functions in the background
                if (wl!=null && wl.isHeld()) {
                    Log.d(MainActivity.TAG,">>>>[Actifit]Settings AGG MODE OFF");
                    wl.release();
                }
            }

            //reset value first
            editor.putString("selectedCharity", "");

            //check if charity mode is on and a charity has been selected
            if (donateCharityChckBox.isChecked()){
                //Spinner charitySelected1 = findViewById(R.id.charity_options);
                if (charitySelected.getSelectedItem() !=null){
                    editor.putString("selectedCharity", ((Charity) charitySelected.getSelectedItem()).getCharityName());
                    editor.putString("selectedCharityDisplayName", charitySelected.getSelectedItem().toString());
                }
            }

            //unset alarm and the need to restart Actifit notification reminder after reboot
            alarmManager = (AlarmManager) getApplicationContext()
                    .getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(getApplicationContext(), ReminderNotificationService.class);
            if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.S) {
                alarmIntent = PendingIntent.getService(getApplicationContext()
                        , ReminderNotificationService.NOTIFICATION_ID, intent, PendingIntent.FLAG_MUTABLE);
            }else {
                alarmIntent = PendingIntent.getService(getApplicationContext()
                        , ReminderNotificationService.NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
            //unset any existing alarms first
            alarmManager.cancel(alarmIntent);

            //check if reminder setting is on and only set it
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

                Log.d(MainActivity.TAG,">>>>[Actifit]: set alarm manager"+hourOptions.getValue()+" "+minOptions.getValue());

                if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.S) {
                    alarmIntent = PendingIntent.getBroadcast(getApplicationContext()
                            , 0, intent, PendingIntent.FLAG_MUTABLE);
                }else{
                    alarmIntent = PendingIntent.getBroadcast(getApplicationContext()
                            , 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                }

                alarmManager = (AlarmManager) getApplicationContext()
                        .getSystemService(Context.ALARM_SERVICE);

                //specify alarm interval to be every 24 hours at user defined slot
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                        1000 * 60 * 60 * 24, alarmIntent);
            }else{
                //cancellation case
                editor.putString("selectedReminderHour", "");
                editor.putString("selectedReminderMin", "");
            }

            //store fitbit setting to see if user wants to grab measurements too
            //CheckBox fitbitMeasurements1 = findViewById(R.id.fitbit_measurements);
            if (fitbitMeasurementsChckBox.isChecked()){
                editor.putString("fitbitMeasurements", getString(R.string.fitbit_measurements_on_ntt));
            }else{
                editor.putString("fitbitMeasurements", getString(R.string.fitbit_measurements_off_ntt));
            }

            //adjust notification status
            if (notificationsActive.isChecked()){
                FirebaseMessaging.getInstance().subscribeToTopic(getString(R.string.actif_def_not_topic));
                editor.putBoolean(getString(R.string.notification_status), true);
            }else{
                FirebaseMessaging.getInstance().unsubscribeFromTopic(getString(R.string.actif_def_not_topic));
                editor.putBoolean(getString(R.string.notification_status), false);
            }



            //commit to server

            editor.apply();

            //store to user's settings
            if (!username.equals("")) {


                //build up settings data to be sent
                JSONObject innerSettingsData = new JSONObject();

                //check bchain posting preferences
                try {
                    String chain_selection = "['HIVE'";
                    if (steemOptionCheckbox.isChecked()){
                        chain_selection += ",'STEEM'";
                    }
                    if (blurtOptionCheckbox.isChecked()){
                        chain_selection += ",'BLURT'";
                    }
                    chain_selection += "]";
                    innerSettingsData.put("post_target_bchain", chain_selection);

                    try {
                        int vw = Integer.parseInt(voteWeight.getText().toString());

                        if (vw < 0 || vw > 100) {
                            Toast.makeText(cntxt, cntxt.getString(R.string.vote_percent_incorrect), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        innerSettingsData.put("default_vote_weight", vw+"");

                    }catch(Exception execp){
                        Log.e("SettingsActivity","vote weight is not an integer");
                        //execp.printStackTrace();
                    }
                    /*Array chains = new Array();
                    if (hiveOptionCheckbox.isChecked()){
                        chains[] = 'HIVE';
                    }
                    innerSettingsData.put("post_target_bchain", Array)*/
                    /*if (hiveSteemOptionRadioBtn.isChecked()) {
                        innerSettingsData.put("post_target_bchain", "BOTH");
                    } else {
                        innerSettingsData.put("post_target_bchain", "HIVE");
                    }*/
                }catch(Exception e){
                    //Log.e(MainActivity.TAG, e.getMessage());
                }

                //check standard notification preferences
                try {
                    if (notificationsActive.isChecked()){
                        innerSettingsData.put("notifications_active", true);
                    }else{
                        innerSettingsData.put("notifications_active", false);
                    }
                }catch(JSONException e){
                    //Log.e(MainActivity.TAG, e.getMessage());
                }
                try {
                    for (int i = 0; i < notificationAdapter.getCount(); i++) {
                        SingleNotificationModel entry = notificationAdapter.getItem(i);
                        //Toast.makeText(cntxt, entry.type + " " + entry.isChecked,Toast.LENGTH_LONG);

                            innerSettingsData.put(entry.type, entry.isChecked);

                    }
                } catch (JSONException e) {
                    //Log.e(MainActivity.TAG, e.getMessage());
                }

                //innerSettingsData.
                try{
                    //check if we already have the user's settings data
                    if (userServerSettings == null){
                        userServerSettings = new JSONObject();
                        userServerSettings.put("user",username);
                    }else{
                        if (userServerSettings.has("settings")){
                            userServerSettings.remove("settings");
                        }
                    }
                    userServerSettings.put("settings", innerSettingsData);

                }catch(JSONException e){
                    //Log.e(MainActivity.TAG, e.getMessage());
                }

                // This holds the url to connect to the API and grab the settings.
                String saveSettingsUrl = Utils.apiUrl(cntxt)+getString(R.string.save_settings) + "?user=" + username
                        + "&settings=" + innerSettingsData.toString();


                //save settings
                JsonObjectRequest postRequest = new JsonObjectRequest(Request.Method.GET,
                        saveSettingsUrl, null,
                        new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {

                        Log.d(MainActivity.TAG, response.toString());
                        overridePendingTransition(0,0);
                        currentActivity.finish();
                        overridePendingTransition(0,0);

                    }

                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // error
                        Log.d( MainActivity.TAG, "save settings error");
                        Toast.makeText(getApplicationContext(), getString(R.string.error_saving_settings),Toast.LENGTH_LONG);
                    }
                }) {
                    @NonNull
                    @Override
                    public Map<String, String> getHeaders() throws AuthFailureError {
                        final Map<String, String> params = new HashMap<>();
                        params.put("Content-Type", "application/json");
                        params.put(getString(R.string.validation_header), getString(R.string.validation_pre_data) + " " + accessToken);
                        return params;
                    }

/*
                    @Override
                    public byte[] getBody() {
                        try {
                            String mRequestBody = userServerSettings.toString();
                            return mRequestBody == null ? null : mRequestBody.getBytes("utf-8");
                        } catch (UnsupportedEncodingException uee) {
                            Log.e(MainActivity.TAG, "Unsupported Encoding while trying to get the bytes ");
                            return null;
                        }

                    }
*/
                };
                /*{
                    @Override
                    protected Map<String, String> getParams()
                    {
                        Map<String, String>  params = new HashMap<String, String>();
                        params.put("name", "vv");
                        params.put("domain", "fff");

                        return params;
                    }
                };*/

                queue.add(postRequest);
            }else{
                overridePendingTransition(0,0);
                currentActivity.finish();
                overridePendingTransition(0,0);
            }

            /*finish();
            overridePendingTransition( 0, 0);
            //startActivity(getIntent());
            Intent mainIntent = new Intent(SettingsActivity.this, MainActivity.class);
            SettingsActivity.this.startActivity(mainIntent);
            overridePendingTransition( 0, 0);*/

        });

        //grab charity list
        // Instantiate the RequestQueue.
        //queue = Volley.newRequestQueue(this);

        // This holds the url to connect to the API and grab the balance.
        String charityUrl = Utils.apiUrl(this)+getString(R.string.charity_list_api_url);

        JsonArrayRequest charitiesRequest = new JsonArrayRequest(Request.Method.GET,
                charityUrl, null, new Response.Listener<JSONArray>(){

            @Override
            public void onResponse(JSONArray transactionListArray) {

                ArrayList<Charity> transactionList = new ArrayList<Charity>();
                //Spinner charityOptions = findViewById(R.id.charity_options);
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

                    //charityOptions.setAdapter(arrayAdapter);
                    charitySelected.setAdapter(arrayAdapter);

                    //choose a charity if one is already selected before

                    SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

                    String currentCharity = (sharedPreferences.getString("selectedCharity",""));
                    String currentCharityDisplayName = (sharedPreferences.getString("selectedCharityDisplayName",""));

                    if (!currentCharity.equals("")){
                        //Spinner charitySelected = findViewById(R.id.charity_options);
                        TextView charityInfo = findViewById(R.id.charity_info);

                        donateCharityChckBox.setChecked(true);
                        charitySelected.setSelection(arrayAdapter.getPosition(new Charity(currentCharity,currentCharityDisplayName)), false);
                        String fullUrl = getString(R.string.steemit_url)+'@'+currentCharity;
                        charityInfo.setText(fullUrl);
                        charityInfo.setMovementMethod(LinkMovementMethod.getInstance());
                    }

                    //actifitTransactions.setText("Response is: "+ response);
                }catch (Exception e) {
                    Log.d(MainActivity.TAG,">>>>[Actifit]: Volley error");
                    e.printStackTrace();
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(MainActivity.TAG,">>>>[Actifit]: Volley response error");
                //error.printStackTrace();
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
        //if the setting is manually set as US System or default Metric value (else)
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

    private void removeBeneficiary(int index, String data) {
        try {
            JSONArray beneficiariesArray = new JSONArray(data);

            // Remove the specified beneficiary
            beneficiariesArray.remove(index); // Only available in API 19+

            // Save updated JSON array back to SharedPreferences
            SharedPreferences sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("AdditionalBeneficiaries", beneficiariesArray.toString());
            editor.apply();

            // Update UI
            updateBeneficiaryTable(beneficiariesArray.toString());
        } catch (JSONException e) {
            Toast.makeText(this, "Error removing data", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateBeneficiaryTable(String data) {
        LinearLayout tableLayout = findViewById(R.id.beneficiaryTable); // Ensure this layout exists in XML
        tableLayout.removeAllViews(); // Clear existing rows

        try {
            if (data.isEmpty()) return;
            JSONArray beneficiariesArray = new JSONArray(data);
            for (int i = 0; i < beneficiariesArray.length(); i++) {
                JSONObject beneficiaryObj = beneficiariesArray.getJSONObject(i);

                String account = beneficiaryObj.getString("account");
                int weight = beneficiaryObj.getInt("weight");
                int percentage = weight / 100; // Convert weight back to percentage for display

                // Create a horizontal row layout
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(20, 0, 0, 0); // Add padding to the row

                // Define LayoutParams for fixed widths
                LinearLayout.LayoutParams usernameParams = new LinearLayout.LayoutParams(
                        250, // Width for username
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                LinearLayout.LayoutParams percentageParams = new LinearLayout.LayoutParams(
                        100, // Width for percentage
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                        150, // Width for button
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

                // Create TextView for username
                TextView beneficiaryText = new TextView(this);
                beneficiaryText.setText("@" + account);
                beneficiaryText.setLayoutParams(usernameParams);

                // Create TextView for percentage
                TextView percentageText = new TextView(this);
                percentageText.setText(percentage + "%");
                percentageText.setLayoutParams(percentageParams);

                // Create "Remove" button
                Button removeButton = new Button(this);
                removeButton.setText("-");
                removeButton.setTextSize(16); // Matches your XML size
                removeButton.setTextColor(getResources().getColor(android.R.color.white));
                removeButton.setBackgroundTintList(getResources().getColorStateList(android.R.color.darker_gray));
                removeButton.setLayoutParams(buttonParams);
                int finalI = i;
                removeButton.setOnClickListener(v -> {
                    // Remove entry logic
                    removeBeneficiary(finalI, data);
                });

                // Add components to the row layout
                row.addView(beneficiaryText);
                row.addView(percentageText);
                row.addView(removeButton);

                // Add the row to the table layout
                tableLayout.addView(row);
            }
        } catch (JSONException e) {
            Toast.makeText(this, "Error loading data" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // Apply custom fade-out animation when navigating back to the main activity
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }


}
