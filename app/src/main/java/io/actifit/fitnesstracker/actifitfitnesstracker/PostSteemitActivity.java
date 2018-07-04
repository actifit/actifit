package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;


public class PostSteemitActivity extends AppCompatActivity {

    private StepsDBHelper mStepsDBHelper;
    private String notification = "";
    private int min_step_limit = 1000;
    private Context steemit_post_context;
    private ProgressBar spinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_steemit);

        //Intent myIntent = getIntent();

        //initializing ProgressBar/Spinner
        /*spinner=findViewById(R.id.progressBar);
        spinner.setVisibility(View.GONE);*/

        //setting context
        this.steemit_post_context = this;

        //getting an instance of DB handler
        mStepsDBHelper = new StepsDBHelper(this);

        //grabbing instances of input data sources
        EditText stepCountContainer = findViewById(R.id.steemit_step_count);
        stepCountContainer.setText(String.valueOf(mStepsDBHelper.fetchTodayStepCount()), TextView.BufferType.EDITABLE);


        EditText steemitPostTitle = findViewById(R.id.steemit_post_title);
        EditText steemitUsername = findViewById(R.id.steemit_username);
        EditText steemitPostingKey = findViewById(R.id.steemit_posting_key);
        EditText steemitPostContent = findViewById(R.id.steemit_post_content);
        EditText steemitPostTags = findViewById(R.id.steemit_post_tags);
        EditText steemitStepCount = findViewById(R.id.steemit_step_count);

        //retrieving account data for simple reuse. Data is not stored anywhere outside actifit App.
        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        //if (sharedPreferences.contains("actifitUser")){
            steemitUsername.setText(sharedPreferences.getString("actifitUser",""));
            steemitPostingKey.setText(sharedPreferences.getString("actifitPst",""));
       // }

        final Activity currentActivity = this;
        //capturing steemit post submission
        Button BtnSubmitSteemit = findViewById(R.id.btn_submit_steemit);
        BtnSubmitSteemit.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(final View arg0) {
                //connect to the server via a thread to prevent application hangup
                new PostSteemitRequest(steemit_post_context, currentActivity).execute();
            }
        });

        /* fixing scrollability of content within the post content section */

        steemitPostContent.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (v.getId() == R.id.steemit_post_content) {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    switch (event.getAction() & MotionEvent.ACTION_MASK) {
                        case MotionEvent.ACTION_UP:
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                            break;
                    }
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
            /*spinner=findViewById(R.id.progressBar);
            spinner.setVisibility(View.VISIBLE);*/
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
                EditText steemitPostContent = findViewById(R.id.steemit_post_content);
                EditText steemitPostTags = findViewById(R.id.steemit_post_tags);
                EditText steemitStepCount = findViewById(R.id.steemit_step_count);

                //storing account data for simple reuse. Data is not stored anywhere outside actifit App.
                SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("actifitUser", steemitUsername.getText().toString());
                editor.putString("actifitPst", steemitPostingKey.getText().toString());
                editor.commit();

                //this runs only on live mode
                if (getString(R.string.test_mode).equals("off")){
                    if (Integer.parseInt(steemitStepCount.getText().toString()) < min_step_limit) {
                        notification = "You have not reached the minimum " +
                                NumberFormat.getNumberInstance(Locale.US).format(min_step_limit) + " steps yet";
                        displayNotification(notification, progress, context, currentActivity, "");

                        //reset to enabled
                        //arg0.setEnabled(true);
                        return null;
                    }
                }
                //prepare data to be sent along post
                final JSONObject data = new JSONObject();
                try {
                    data.put("author", steemitUsername.getText());
                    data.put("posting_key", steemitPostingKey.getText());
                    data.put("title", steemitPostTitle.getText());
                    data.put("content", steemitPostContent.getText());
                    data.put("tags", steemitPostTags.getText());
                    data.put("step_count", steemitStepCount.getText());
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                String inputLine;
                String result = "";
                //use test url only if testing mode is on
                String urlStr = getString(R.string.test_api_url);
                if (getString(R.string.test_mode).equals("off")){
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

                System.out.println(">>>test:"+result);

                //check result of action
                if (result.equals("success")){
                    notification = getString(R.string.success_post);
                }else{
                    notification = getString(R.string.failed_post);
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

