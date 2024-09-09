package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;

public class StepHistoryActivity extends BaseActivity {
    private ListView mStepsListView;
    private StepsDBHelper mStepsDBHelper;
    private ArrayList<DateStepsModel> mStepCountList;
    private ArrayList<DateStepsModel> mStepFinalList;
    private ActivityEntryAdapter listingAdapter;
    private JSONArray userPosts;
    private RelativeLayout progressBarRelLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_history);

        mStepsListView = findViewById(R.id.steps_list);
        //mStepFinalList = new ArrayList<String>();
        mStepFinalList = new ArrayList<>();

        StepHistoryAsyncTask stepHistoryAsyncTask = new StepHistoryAsyncTask();
        stepHistoryAsyncTask.execute();

        //hook chart activity button
        Button BtnViewChart = findViewById(R.id.chart_view);
        progressBarRelLayout = findViewById(R.id.progressBarRelLayout);

        BtnViewChart.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Intent intent = new Intent(StepHistoryActivity.this, HistoryChartActivity.class);
                startActivity(intent);

            }
        });

    }

    /**
     * function handles preparing the proper data to the mStepCountList ArrayList
     */
    public void getDataForList() {
        mStepsDBHelper = new StepsDBHelper(this);
        mStepCountList = mStepsDBHelper.readStepsEntries();
    }

    private class StepHistoryAsyncTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            //grab the data to be displayed in the list
            getDataForList();

            //load user's post data
            loadPosts();

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            //initializing date conversion components
            String dateDisplay;
            //existing date format
            SimpleDateFormat dateFormIn = new SimpleDateFormat("yyyyMMdd");
            //output format
            SimpleDateFormat dateFormOut = new SimpleDateFormat("MM/dd/yyyy");

            //loop through the data to prepare it for proper display
            for (int position=0;position<mStepCountList.size();position++){
                try {
                    //grab date entry according to stored format
                    Date feedingDate = dateFormIn.parse((mStepCountList.get(position)).mDate);
                    //convert it to new format for display
                    dateDisplay = dateFormOut.format(feedingDate);

                    //initiate a new entry
                    DateStepsModel newEntry = new DateStepsModel(dateDisplay, mStepCountList.get(position).mStepCount, mStepCountList.get(position).mtrackingDevice);

                    //check if user has a post, set up its link properly
                    newEntry.relevantPostLink = matchUserPostLink((mStepCountList.get(position)).mDate);
                    newEntry.hasRelevantPost = !Objects.equals(newEntry.relevantPostLink, "");

                    mStepFinalList.add(newEntry);

                }catch(ParseException txtEx){
                    Log.d(MainActivity.TAG,txtEx.toString());
                    txtEx.printStackTrace();
                }
            }
            //reverse the list for descending display
            Collections.reverse(mStepFinalList);

            // Create the adapter to convert the array to views
            listingAdapter = new ActivityEntryAdapter(getApplicationContext(), mStepFinalList);


            mStepsListView.setAdapter(listingAdapter);
            progressBarRelLayout.setVisibility(View.GONE);

        }
    }


    /**
     * function handles loading user's historical posts
     */
    void loadPosts()  {

        HiveRequests hive = new HiveRequests(getApplicationContext());

        //Thread thread = new Thread(() -> {

            try {
                JSONObject params = new JSONObject();
                params.put("sort", "posts");
                params.put("account", MainActivity.username);
                params.put("start_author", "");
                params.put("start_permlink", "");
                params.put("observer", "");
                JSONArray result = hive.getAccountPosts(params);
                userPosts = result;

            }
            catch (Exception e){
                //Log.e(MainActivity.TAG, Objects.requireNonNull(e.getMessage()));
                Log.e(MainActivity.TAG, "ERROR");
            }
        //});
        //thread.start();
    }
    String matchUserPostLink(String entryDate){
        for (int i = 0; i < userPosts.length(); i++) {
            try {
                JSONObject post = userPosts.getJSONObject(i);
                SingleHivePostModel postEntry = new SingleHivePostModel(post, getApplicationContext());
                //same date
                if (postEntry.postDateMatches(entryDate)) {
                    if (postEntry.author.equals(MainActivity.username) && postEntry.hasActivityCount()
                    && (postEntry.hasActifitTag() )
                    ) {
                        return postEntry.permlink;
                    }
                }
            } catch (Exception e) {
                //Log.e(MainActivity.TAG, Objects.requireNonNull(e.getMessage()));
                Log.e(MainActivity.TAG, "ERROR");
            }
        }
        return "";
    }
}
