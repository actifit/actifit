package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class LeaderboardActivity extends AppCompatActivity {

    private ListView mAccountsListView;
    private StepsDBHelper mStepsDBHelper;
    private ArrayList<DateStepsModel> mStepCountList;
    private ArrayList<String> mAccountsFinalList ;
    private Context leadership_post_context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        mAccountsListView = findViewById(R.id.accounts_list);
        mAccountsFinalList = new ArrayList<String>();

        this.leadership_post_context = this;
        final Activity currentActivity = this;

        //connect to the server via a thread to prevent application hangup
        //and grab the data to be displayed in the list
        new LeaderboardActivity.LeaderboardDataRequest(leadership_post_context, currentActivity).execute();
    }

    /**
     * function handles fetching the top 5 actifit account as a thread
     */

    private class LeaderboardDataRequest extends AsyncTask<String, Void, Void> {
        ProgressDialog progress;
        private final Context context;
        private Activity currentActivity;
        private String notification;

        public LeaderboardDataRequest(Context c, Activity currentActivity){
            this.context = c;
            this.currentActivity = currentActivity;
        }

        protected void onPreExecute(){
            //create a new progress dialog to show action is underway
            progress = new ProgressDialog(this.context);
            progress.setMessage(getString(R.string.fetching_leaderboard));
            progress.show();
        }
        protected Void doInBackground(String... params) {
            try {

                String inputLine;

                String result = "";
                //connect to our leaderboard API
                String urlStr = getString(R.string.leaderboard_url);
                if (!getString(R.string.test_mode).equals("off")){
                    urlStr = getString(R.string.test_leaderboard_url);
                }

                // Headers
                ArrayList<String[]> headers = new ArrayList<>();

                headers.add(new String[]{"Content-Type", "application/json"});

                HttpResultHelper httpResult = new HttpResultHelper();

                httpResult = httpResult.httpPost(urlStr, null, null, "", headers, 20000);
                BufferedReader in = new BufferedReader(new InputStreamReader(httpResult.getResponse()));
                while ((inputLine = in.readLine()) != null) {
                    result += inputLine;
                }

                System.out.println(">>>test:"+result);

                //check result of action
                if (result.equals("zero")){
                    notification = getString(R.string.leader_no_results);

                    //display proper notification
                    displayNotification(notification, progress, context, currentActivity, result);
                }else if (result.equals("error")){
                    notification = getString(R.string.leader_error);

                    //display proper notification
                    displayNotification(notification, progress, context, currentActivity, result);
                }else{
                    final String api_outcome = result;
                    currentActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //hide the progressDialog
                            progress.dismiss();

                            //need to render the result
                            List<String> items = Arrays.asList(api_outcome.split(";"));
                            mAccountsFinalList.addAll(items);

                            ArrayAdapter<String> arrayAdapter =
                                    new ArrayAdapter<String>(currentActivity, android.R.layout.simple_list_item_1, mAccountsFinalList);

                            mAccountsListView.setAdapter(arrayAdapter);
                        }
                    });
                }


            }catch (Exception e){

                //display proper notification
                notification = getString(R.string.leader_error);
                displayNotification(notification, progress, context, currentActivity, "error");

                System.out.println("Error connecting:"+e.getMessage());
                e.printStackTrace();
            }
            return null;
        }

    }
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
                                if (success.equals("zero") || success.equals("error")) {
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
}
