package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.StrictMode;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_steemit);

        //Intent myIntent = getIntent();

        mStepsDBHelper = new StepsDBHelper(this);

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


        //capturing steemit post submission
        Button BtnSubmitSteemit = findViewById(R.id.btn_submit_steemit);
        BtnSubmitSteemit.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                System.out.println("click");

                //disable button to prevent multiple clicks
                arg0.setEnabled(false);

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

                if (Integer.parseInt(steemitStepCount.getText().toString()) < min_step_limit){
                    notification = "You have not reached the minimum "+
                            NumberFormat.getNumberInstance(Locale.US).format(min_step_limit)+" steps yet";
                    displayNotification(notification);

                    //reset to enabled
                    arg0.setEnabled(true);
                    return;
                }

                JSONObject data = new JSONObject();
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

                try {
                    String urlStr = "";
                    String inputLine;
                    String result = "";
// Headers
                    ArrayList<String[]> headers = new ArrayList<>();
                    //headers.add(new String[]{"custom-header", "custom value"});
                    headers.add(new String[]{"Content-Type", "application/json"});
                    HttpResultHelper httpResult = new HttpResultHelper();
                    httpResult = httpResult.httpPost(urlStr, null, null, data.toString(), headers, 7000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(httpResult.getResponse()));
                    while ((inputLine = in.readLine()) != null) {
                        result += inputLine;
                    }

                    System.out.println(">>>test:"+result);

                    if (result.equals("success")){
                        notification = "Your post has been successfully submitted to the Steem blockchain";
                    }else{
                        notification = "There was an error submitting your post to the Steem blockchain ";
                    }

                    //reset to enabled
                    arg0.setEnabled(true);

                    displayNotification(notification);

                }catch (Exception e){
                    //reset to enabled
                    arg0.setEnabled(true);

                    System.out.println("Error connecting:"+e.getMessage());
                    e.printStackTrace();
                }

            }
        });
    }

    void displayNotification(String notification){
        AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
        builder1.setMessage(notification);

        builder1.setCancelable(true);

        builder1.setPositiveButton(
                "Dismiss",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        AlertDialog alert11 = builder1.create();
        alert11.show();
    }

}
