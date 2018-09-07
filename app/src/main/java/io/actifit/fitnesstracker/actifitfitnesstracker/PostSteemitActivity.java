package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.icu.text.BreakIterator;
import android.os.AsyncTask;
import android.os.StrictMode;

import android.support.v4.widget.NestedScrollView;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;



public class PostSteemitActivity extends AppCompatActivity {

    private StepsDBHelper mStepsDBHelper;
    private String notification = "";
    private int min_step_limit = 1000;
    private int min_char_count = 100;
    private Context steemit_post_context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_steemit);

        //setting context
        this.steemit_post_context = this;

        //getting an instance of DB handler
        mStepsDBHelper = new StepsDBHelper(this);

        //grabbing instances of input data sources
        final EditText stepCountContainer = findViewById(R.id.steemit_step_count);

        //set initial steps display value
        int stepCount = mStepsDBHelper.fetchTodayStepCount();
        //display step count while ensuring we don't display negative value if no steps tracked yet
        stepCountContainer.setText(String.valueOf((stepCount<0?0:stepCount)), TextView.BufferType.EDITABLE);


        EditText steemitPostTitle = findViewById(R.id.steemit_post_title);
        EditText steemitUsername = findViewById(R.id.steemit_username);
        EditText steemitPostingKey = findViewById(R.id.steemit_posting_key);
        final EditText steemitPostContent = findViewById(R.id.steemit_post_text);
        TextView measureSectionLabel = findViewById(R.id.measurements_section_lbl);

        TextView heightSizeUnit = findViewById(R.id.measurements_height_unit);
        TextView weightSizeUnit = findViewById(R.id.measurements_weight_unit);
        TextView waistSizeUnit = findViewById(R.id.measurements_waistsize_unit);
        TextView chestSizeUnit = findViewById(R.id.measurements_chest_unit);
        TextView thighsSizeUnit = findViewById(R.id.measurements_thighs_unit);

        //Adding default title content for the daily post

        //generating today's date
        Calendar mCalendar = Calendar.getInstance();
        String postTitle = getString(R.string.default_post_title);
        postTitle += " "+new SimpleDateFormat("MMMM d yyyy").format(mCalendar.getTime());

        //postTitle += String.valueOf(mCalendar.get(Calendar.MONTH)+1)+" " +
                //String.valueOf(mCalendar.get(Calendar.DAY_OF_MONTH))+"/"+String.valueOf(mCalendar.get(Calendar.YEAR));
        steemitPostTitle.setText(postTitle);

        //initializing activity options
        String[] activity_type = {
                "Walking", "Jogging", "Running", "Cycling", "Rope Skipping",
                "Dancing","Basketball", "Football", "Boxing", "Tennis", "Table Tennis",
                "Martial Arts", "House Chores", "Moving Around Office", "Shopping","Daily Activity",
                "Aerobics", "Weight Lifting", "Treadmill","Stair Mill", "Elliptical"
                };

        //sort options in alpha order
        Arrays.sort(activity_type);

        MultiSelectionSpinner activityTypeSelector = (MultiSelectionSpinner) findViewById(R.id.steemit_activity_type);
        activityTypeSelector.setItems(activity_type);

        //retrieving account data for simple reuse. Data is not stored anywhere outside actifit App.
        SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

        steemitUsername.setText(sharedPreferences.getString("actifitUser",""));
        steemitPostingKey.setText(sharedPreferences.getString("actifitPst",""));

        //grab current selection for measure system
        String activeSystem = sharedPreferences.getString("activeSystem",getString(R.string.metric_system));
        //adjust units accordingly
        if (activeSystem.equals(getString(R.string.metric_system))){
            weightSizeUnit.setText("kg");
            heightSizeUnit.setText("cm");
            waistSizeUnit.setText("cm");
            chestSizeUnit.setText("cm");
            thighsSizeUnit.setText("cm");
        }else{
            weightSizeUnit.setText("lb");
            heightSizeUnit.setText("ft");
            waistSizeUnit.setText("in");
            chestSizeUnit.setText("in");
            thighsSizeUnit.setText("in");
        }

        final Activity currentActivity = this;


        //capturing steemit post submission
        Button BtnSubmitSteemit = findViewById(R.id.btn_submit_steemit);
        BtnSubmitSteemit.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(final View arg0) {

                //ned to grab new updated activity count before posting
                int stepCount = mStepsDBHelper.fetchTodayStepCount();
                //display step count while ensuring we don't display negative value if no steps tracked yet
                stepCountContainer.setText(String.valueOf((stepCount<0?0:stepCount)), TextView.BufferType.EDITABLE);

                //we need to check first if we have a charity setup
                SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

                final String currentCharity = (sharedPreferences.getString("selectedCharity",""));
                final String currentCharityDisplayName = (sharedPreferences.getString("selectedCharityDisplayName",""));

                if (!currentCharity.equals("")){
                        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (which) {
                                    case DialogInterface.BUTTON_POSITIVE:
                                        //go ahead posting
                                        new PostSteemitRequest(steemit_post_context, currentActivity).execute();
                                        break;

                                    case DialogInterface.BUTTON_NEGATIVE:
                                        //cancel
                                        break;
                                }
                            }
                        };

                        AlertDialog.Builder builder = new AlertDialog.Builder(steemit_post_context);
                        builder.setMessage(getString(R.string.current_workout_going_charity) + " "
                                + currentCharityDisplayName + " "
                                + getString(R.string.current_workout_settings_based))
                                .setPositiveButton("Yes", dialogClickListener)
                                .setNegativeButton("No", dialogClickListener).show();
                }else {
                    //connect to the server via a thread to prevent application hangup
                    new PostSteemitRequest(steemit_post_context, currentActivity).execute();
                }
            }
        });

        /* fixing scrollability of content within the post content section */

        steemitPostContent.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (v.getId() == R.id.steemit_post_text) {

                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    switch (event.getAction() & MotionEvent.ACTION_MASK) {
                        case MotionEvent.ACTION_UP:
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                            break;
                    }

                }
                if (v.getId() == R.id.steemit_post_text || v.getId() == R.id.steemit_post_tags
                        || v.getId() == R.id.measurements_bodyfat || v.getId() == R.id.measurements_chest
                        || v.getId() == R.id.measurements_height || v.getId() == R.id.measurements_weight
                        || v.getId() == R.id.measurements_thighs || v.getId() == R.id.measurements_waistsize) {
                    ((NestedScrollView)findViewById(R.id.nestedScrollView)).smoothScrollTo(0,v.getBottom());
                }
                return false;
            }
        });
    }


    /**
     * function handling the display of popup notification
     * @param notification
     */
    void displayNotification(final String notification, final ProgressDialog progress,
                             final Context context, final Activity currentActivity,
                             final String success){
        //render result
        currentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //hide the progressDialog
                progress.dismiss();
                /*spinner=findViewById(R.id.progressBar);
                spinner.setVisibility(View.GONE);*/

                AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
                builder1.setMessage(notification);

                builder1.setCancelable(true);

                builder1.setPositiveButton(
                        "Dismiss",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                if (success.equals("success")) {
                                    //close current screen
                                    System.out.println(">>>Finish");
                                    currentActivity.finish();
                                }
                            }
                        });
                //create and display alert window
                AlertDialog alert11 = builder1.create();
                alert11.show();
            }
        });

        //finish();
    }

    private class PostSteemitRequest extends AsyncTask<String, Void, Void> {
        ProgressDialog progress;
        private final Context context;
        private Activity currentActivity;
        public PostSteemitRequest(Context c, Activity currentActivity){
                this.context = c;
                this.currentActivity = currentActivity;
        }
        protected void onPreExecute(){
            //create a new progress dialog to show action is underway
            progress = new ProgressDialog(this.context);
            progress.setMessage(getString(R.string.sending_post));
            progress.show();
        }
        protected Void doInBackground(String... params) {
            try {
                System.out.println("click");

                //disable button to prevent multiple clicks
                //arg0.setEnabled(false);

                EditText steemitPostTitle = findViewById(R.id.steemit_post_title);
                EditText steemitUsername = findViewById(R.id.steemit_username);
                EditText steemitPostingKey = findViewById(R.id.steemit_posting_key);
                EditText steemitPostContent = findViewById(R.id.steemit_post_text);
                EditText steemitPostTags = findViewById(R.id.steemit_post_tags);
                EditText steemitStepCount = findViewById(R.id.steemit_step_count);
                MultiSelectionSpinner activityTypeSelector = findViewById(R.id.steemit_activity_type);

                EditText heightSize = findViewById(R.id.measurements_height);
                EditText weightSize = findViewById(R.id.measurements_weight);
                EditText bodyFat = findViewById(R.id.measurements_bodyfat);
                EditText chestSize = findViewById(R.id.measurements_chest);
                EditText thighsSize = findViewById(R.id.measurements_thighs);
                EditText waistSize = findViewById(R.id.measurements_waistsize);

                TextView heightSizeUnit = findViewById(R.id.measurements_height_unit);
                TextView weightSizeUnit = findViewById(R.id.measurements_weight_unit);
                TextView waistSizeUnit = findViewById(R.id.measurements_waistsize_unit);
                TextView chestSizeUnit = findViewById(R.id.measurements_chest_unit);
                TextView thighsSizeUnit = findViewById(R.id.measurements_thighs_unit);


                //storing account data for simple reuse. Data is not stored anywhere outside actifit App.
                SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                //skip on spaces, upper case, and @ symbols to properly match steem username patterns
                editor.putString("actifitUser", steemitUsername.getText().toString()
                        .trim().toLowerCase().replace("@",""));
                editor.putString("actifitPst", steemitPostingKey.getText().toString());
                editor.commit();

                //this runs only on live mode
                if (getString(R.string.test_mode).equals("off")){
                    //make sure we have reached the min movement amount
                    if (Integer.parseInt(steemitStepCount.getText().toString()) < min_step_limit) {
                        notification = "You have not reached the minimum " +
                                NumberFormat.getNumberInstance(Locale.US).format(min_step_limit) + " activity yet";
                        displayNotification(notification, progress, context, currentActivity, "");

                        return null;
                    }

                    //make sure the post content has at least the min_char_count
                    if (steemitPostContent.getText().toString().length()
                            <= min_char_count){
                        notification = getString(R.string.min_char_count_error)
                                +" "+ min_char_count
                                +" "+ getString(R.string.characters_plural_label);
                        displayNotification(notification, progress, context, currentActivity, "");

                        return null;
                    }

                    //make sure the user has not posted today already,
                    //and also avoid potential abuse of changing phone clock via comparing to older dates
                    String lastPostDate = sharedPreferences.getString("actifitLastPostDate","");
                    String currentDate = new SimpleDateFormat("yyyyMMdd").format(
                            Calendar.getInstance().getTime());

                    System.out.println(">>>>[Actifit]lastPostDate:"+lastPostDate);
                    System.out.println(">>>>[Actifit]currentDate:"+currentDate);
                    if (!lastPostDate.equals("")){
                        if (Integer.parseInt(lastPostDate) >= Integer.parseInt(currentDate)) {
                            notification = getString(R.string.one_post_per_day_error);
                            displayNotification(notification, progress, context, currentActivity, "");
                            return null;
                        }
                    }

                }

                //let us check if user has selected activities yet
                if (activityTypeSelector.getSelectedIndicies().size()<1){
                    notification = getString(R.string.error_need_select_one_activity);
                    displayNotification(notification, progress, context, currentActivity, "");

                    //reset to enabled
                    //arg0.setEnabled(true);
                    return null;
                }




                //prepare data to be sent along post
                final JSONObject data = new JSONObject();
                try {
                    //skip on spaces, upper case, and @ symbols to properly match steem username patterns
                    data.put("author", steemitUsername.getText().toString()
                            .trim().toLowerCase().replace("@",""));
                    data.put("posting_key", steemitPostingKey.getText());
                    data.put("title", steemitPostTitle.getText());
                    data.put("content", steemitPostContent.getText());
                    data.put("tags", steemitPostTags.getText());
                    data.put("step_count", steemitStepCount.getText());
                    data.put("activity_type", activityTypeSelector.getSelectedItemsAsString());

                    data.put("height", heightSize.getText());
                    data.put("weight", weightSize.getText());
                    data.put("chest", chestSize.getText());
                    data.put("waist", waistSize.getText());
                    data.put("thighs", thighsSize.getText());
                    data.put("bodyfat", bodyFat.getText());

                    data.put("heightUnit", heightSizeUnit.getText());
                    data.put("weightUnit", weightSizeUnit.getText());
                    data.put("chestUnit", chestSizeUnit.getText());
                    data.put("waistUnit", waistSizeUnit.getText());
                    data.put("thighsUnit", thighsSizeUnit.getText());

                    data.put("appType", "Android");

                    //grab app version number
                    try {
                        PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                        String version = pInfo.versionName;
                        data.put("appVersion",version);
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }


                    //choose a charity if one is already selected before

                    sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

                    final String currentCharity = (sharedPreferences.getString("selectedCharity",""));

                    if (!currentCharity.equals("")){
                        data.put("charity", currentCharity);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                String inputLine;
                String result = "";
                //use test url only if testing mode is on
                String urlStr = getString(R.string.test_api_url);
                if (getString(R.string.test_mode).equals("off")) {
                    urlStr = getString(R.string.api_url);
                }
                // Headers
                ArrayList<String[]> headers = new ArrayList<>();

                headers.add(new String[]{"Content-Type", "application/json"});
                HttpResultHelper httpResult = new HttpResultHelper();

                httpResult = httpResult.httpPost(urlStr, null, null, data.toString(), headers, 20000);
                BufferedReader in = new BufferedReader(new InputStreamReader(httpResult.getResponse()));
                while ((inputLine = in.readLine()) != null) {
                    result += inputLine;
                }

                System.out.println(">>>test:" + result);

                //check result of action
                if (result.equals("success")) {
                    notification = getString(R.string.success_post);

                    //store date of last successful post to prevent multiple posts per day

                    //storing account data for simple reuse. Data is not stored anywhere outside actifit App.
                    sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);
                    editor = sharedPreferences.edit();
                    editor.putString("actifitLastPostDate",
                            new SimpleDateFormat("yyyyMMdd").format(
                                    Calendar.getInstance().getTime()));
                    editor.commit();
                } else {
                    // notification = getString(R.string.failed_post);
                    notification = result;
                }

                //display proper notification
                displayNotification(notification, progress, context, currentActivity, result);

            }catch (Exception e){

                //display proper notification
                notification = getString(R.string.failed_post);
                displayNotification(notification, progress, context, currentActivity, "");

                System.out.println("Error connecting:"+e.getMessage());
                e.printStackTrace();
            }
            return null;
        }
    }


}

