package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference; // Good practice for AsyncTasks
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;


public class StepHistoryActivity extends BaseActivity { // Assuming BaseActivity is fine
    private ListView mStepsListView;
    private ArrayList<DateStepsModel> mStepFinalList = new ArrayList<>(); // Initialize here
    private ActivityEntryAdapter listingAdapter;
    private RelativeLayout progressBarRelLayout;

    // We'll manage the AsyncTasks to potentially cancel them on destroy
    private LoadStepsAsyncTask loadStepsTask;
    private LoadPostsAsyncTask loadPostsTask;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_history);

        mStepsListView = findViewById(R.id.steps_list);
        progressBarRelLayout = findViewById(R.id.progressBarRelLayout);

        // Hook chart activity button
        Button BtnViewChart = findViewById(R.id.chart_view);
        BtnViewChart.setOnClickListener(arg0 -> {
            Intent intent = new Intent(StepHistoryActivity.this, HistoryChartActivity.class);
            startActivity(intent);
        });

        // Start the first task to load steps and display the initial list
        loadStepsTask = new LoadStepsAsyncTask(this); // Pass Activity context
        loadStepsTask.execute();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancel ongoing tasks to prevent memory leaks or crashes
        if (loadStepsTask != null && loadStepsTask.getStatus() == AsyncTask.Status.RUNNING) {
            loadStepsTask.cancel(true);
        }
        if (loadPostsTask != null && loadPostsTask.getStatus() == AsyncTask.Status.RUNNING) {
            loadPostsTask.cancel(true);
        }
    }


    /**
     * Task 1: Load steps from DB and display the initial list
     */
    private static class LoadStepsAsyncTask extends AsyncTask<Void, Void, ArrayList<DateStepsModel>> {

        // Use a WeakReference to the Activity to prevent memory leaks
        private WeakReference<StepHistoryActivity> activityWeakReference;
        private String errorMessage = null;

        LoadStepsAsyncTask(StepHistoryActivity context) {
            activityWeakReference = new WeakReference<>(context);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            StepHistoryActivity activity = activityWeakReference.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }
            // Show progress bar on UI thread
            if (activity.progressBarRelLayout != null) {
                activity.progressBarRelLayout.setVisibility(View.VISIBLE);
            }
            // Clear previous data if any
            activity.mStepFinalList.clear();
            if (activity.listingAdapter != null) {
                activity.listingAdapter.notifyDataSetChanged();
            }
        }

        @Override
        protected ArrayList<DateStepsModel> doInBackground(Void... voids) {
            // This runs on a background thread
            StepHistoryActivity activity = activityWeakReference.get();
            if (activity == null || activity.isFinishing() || isCancelled()) {
                return null; // Return null if activity is gone or task is cancelled
            }

            ArrayList<DateStepsModel> stepsList = null;
            try {
                // Load steps from Database (ensure StepsDBHelper is thread-safe or create new instance)
                // Using Activity context for DB helper is generally acceptable here.
                StepsDBHelper stepsDBHelper = new StepsDBHelper(activity);
                stepsList = stepsDBHelper.readStepsEntries();
            } catch (Exception e) {
                Log.e(MainActivity.TAG, "Error loading steps from DB", e);
                errorMessage = "Failed to load step history.";
            }

            // Prepare the initial list with just steps
            ArrayList<DateStepsModel> initialStepFinalList = new ArrayList<>();
            if (stepsList != null) {
                // Use Locale-aware SimpleDateFormat
                SimpleDateFormat dateFormIn = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                SimpleDateFormat dateFormOut = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());

                for (DateStepsModel stepEntry : stepsList) {
                    if (isCancelled()) return null; // Check for cancellation
                    try {
                        Date feedingDate = dateFormIn.parse(stepEntry.mDate);
                        String dateDisplay = dateFormOut.format(feedingDate);
                        // Create initial entry without post link data
                        DateStepsModel newEntry = new DateStepsModel(dateDisplay, stepEntry.mStepCount, stepEntry.mtrackingDevice);
                        // relevantPostChecked and hasRelevantPost will be false by default
                        initialStepFinalList.add(newEntry);
                    } catch (ParseException txtEx) {
                        Log.d(MainActivity.TAG, "Error parsing date for step entry: " + stepEntry.mDate, txtEx);
                    }
                }
                // reverse the list for descending display (or do this in fillData)
                Collections.reverse(initialStepFinalList);
            }

            return initialStepFinalList; // Return the initial list
        }

        @Override
        protected void onPostExecute(ArrayList<DateStepsModel> initialResultList) {
            super.onPostExecute(initialResultList);
            StepHistoryActivity activity = activityWeakReference.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }

            // Hide the initial progress bar AFTER showing the list
            if (activity.progressBarRelLayout != null) {
                activity.progressBarRelLayout.setVisibility(View.GONE);
            }

            if (errorMessage != null) {
                // Display error message to the user (e.g., Toast, Dialog)
                Log.e(MainActivity.TAG, "Displaying steps loading error to user: " + errorMessage);
                // Show an empty list or error message View
                activity.mStepFinalList.clear();
                activity.updateListViewAdapter(); // Update the list view (likely to show empty)
                return; // Stop here if steps failed to load
            }


            // Update the instance field with the initial list result
            activity.mStepFinalList = initialResultList != null ? initialResultList : new ArrayList<>();


            // Display the initial list immediately
            activity.updateListViewAdapter(); // Create/update adapter on UI thread

            // Now, start the second task to load posts and update the list items
            activity.loadPostsTask = new LoadPostsAsyncTask(activity, activity.mStepFinalList); // Pass activity and the list
            activity.loadPostsTask.execute();
        }
        @Override
        protected void onCancelled() {
            Log.d(MainActivity.TAG, "LoadStepsAsyncTask cancelled");
            // Handle cancellation if needed, e.g., hide progress bar
            StepHistoryActivity activity = activityWeakReference.get();
            if (activity != null && !activity.isFinishing()) {
                if (activity.progressBarRelLayout != null) {
                    activity.progressBarRelLayout.setVisibility(View.GONE);
                }
            }
        }
    }

    /**
     * Task 2: Load posts from Network and update existing list items
     */
    private static class LoadPostsAsyncTask extends AsyncTask<Void, DateStepsModel, JSONArray> { // Void, ProgressType (item updated), ResultType

        private WeakReference<StepHistoryActivity> activityWeakReference;
        private ArrayList<DateStepsModel> currentStepList; // Reference to the list being displayed
        private String errorMessage = null;

        LoadPostsAsyncTask(StepHistoryActivity context, ArrayList<DateStepsModel> stepList) {
            activityWeakReference = new WeakReference<>(context);
            this.currentStepList = stepList; // Keep a reference to the list
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Optionally show a *secondary* progress indicator if post loading takes a long time
            // but for simplicity, we hide the main one after steps load.
        }

        @Override
        protected JSONArray doInBackground(Void... voids) {
            // This runs on a background thread
            StepHistoryActivity activity = activityWeakReference.get(); // Get activity instance ONCE before the loop
            if (activity == null || activity.isFinishing() || isCancelled()) {
                return null; // Return null if activity is gone or task cancelled before the loop starts
            }

            JSONArray userPostsFromNetwork = new JSONArray(); // Default empty
            try {
                // Load user's historical posts from Network (blocking call)
                HiveRequests hive = new HiveRequests(activity.getApplicationContext()); // Use Application context
                JSONObject params = new JSONObject();
                params.put("sort", "posts");
                params.put("account", MainActivity.username); // Make sure MainActivity.username is accessible
                params.put("start_author", "");
                params.put("start_permlink", "");
                params.put("observer", "");
                userPostsFromNetwork = hive.getAccountPosts(params); // Assumes this is blocking/synchronous
            } catch (Exception e) {
                Log.e(MainActivity.TAG, "Error loading posts in background", e);
                errorMessage = "Failed to load posts.";
                userPostsFromNetwork = new JSONArray(); // Ensure it's not null on error
            }

            if (isCancelled()) return null; // Check for cancellation after network call

            // Now, iterate through the *existing* step list and update items if a matching post is found
            // We iterate in reverse because mStepFinalList is reversed for display
            // Declare SimpleDateFormat instances ONCE before the loop for efficiency
            SimpleDateFormat dateFormIn = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()); // List uses this format
            SimpleDateFormat dateFormMatch = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()); // Posts use this format? Need to confirm

            if (currentStepList != null && userPostsFromNetwork != null && userPostsFromNetwork.length() > 0) {
                for (int i = 0; i < currentStepList.size(); i++) {
                    if (isCancelled()) {
                        Log.d(MainActivity.TAG, "Post matching loop interrupted");
                        return null; // Task cancelled, exit doInBackground
                    }

                    // Check if the activity is still valid *inside* the loop before using it
                    // Use the 'activity' variable declared outside the loop
                    if (activity.isFinishing()) { // No need to re-get from weak ref, just check if finishing
                        Log.w(MainActivity.TAG, "Activity finishing during post matching loop");
                        // Depending on requirements, you might return null or just break the loop
                        return null; // Activity is going away, stop processing
                    }


                    DateStepsModel stepEntry = currentStepList.get(i);

                    try {
                        // Use the date format variables declared outside the loop
                        Date displayedDate = dateFormIn.parse(stepEntry.mDate);
                        String matchDate = dateFormMatch.format(displayedDate);

                        // Call the non-static method on the retrieved activity instance
                        String postLink = activity.matchUserPostLinkInternal(userPostsFromNetwork, matchDate, activity.getApplicationContext());

                        if (!postLink.isEmpty()) {
                            stepEntry.relevantPostChecked = true; // Mark as checked
                            stepEntry.relevantPostLink = postLink;
                            stepEntry.hasRelevantPost = true;
                            // Publish the updated item to trigger a partial UI refresh
                            publishProgress(stepEntry); // Sends the *updated* item
                        } else {
                            // Explicitly mark as checked even if no post found, prevents re-checking
                            stepEntry.relevantPostChecked = true;
                            // No need to publish if no change visually (relevantPostChecked is internal flag)
                        }
                    } catch (ParseException e) {
                        Log.e(MainActivity.TAG, "Error parsing date for post matching: " + stepEntry.mDate, e);
                        stepEntry.relevantPostChecked = true; // Mark as checked to avoid infinite loop
                    } catch (Exception e) {
                        Log.e(MainActivity.TAG, "Error matching post for step entry: " + stepEntry.mDate, e);
                        stepEntry.relevantPostChecked = true; // Mark as checked
                    }
                }
            }
            // ... rest of doInBackground ...
            return userPostsFromNetwork; // Or null, as publishing updates the UI
        }

        @Override
        protected void onProgressUpdate(DateStepsModel... updatedItems) {
            super.onProgressUpdate(updatedItems);
            StepHistoryActivity activity = activityWeakReference.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }
            // This runs on the UI thread whenever publishProgress is called

            // The adapter holds a reference to mStepFinalList.
            // Since we updated items *within* that list in doInBackground,
            // we just need to tell the adapter the data has changed.
            if (activity.listingAdapter != null) {
                activity.listingAdapter.notifyDataSetChanged(); // Refreshes the whole list. For ListView, this is common.
                // A more optimized way for RecyclerView would be notifyItemChanged(index)
                // but that requires finding the index of the updated item. notifyDataSetChanged is simpler here.
            }

        }


        @Override
        protected void onPostExecute(JSONArray resultPosts) {
            super.onPostExecute(resultPosts);
            StepHistoryActivity activity = activityWeakReference.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }

            // Hide any secondary progress indicator if you added one

            if (errorMessage != null) {
                // Display error message for post loading (less critical than step loading failure)
                Log.w(MainActivity.TAG, "Displaying posts loading error to user: " + errorMessage);
                // The list will still show steps, just without post links.
            }

            // Ensure the adapter shows the final state, even if no items were published
            if (activity.listingAdapter != null) {
                activity.listingAdapter.notifyDataSetChanged(); // Final refresh
            }
            Log.d(MainActivity.TAG, "Post loading and list update finished.");

        }

        @Override
        protected void onCancelled(JSONArray resultPosts) {
            Log.d(MainActivity.TAG, "LoadPostsAsyncTask cancelled");
            // Handle cancellation if needed
            StepHistoryActivity activity = activityWeakReference.get();
            if (activity != null && !activity.isFinishing()) {
                // Hide secondary progress if shown
            }
        }
    }

    /**
     * Helper function to update the ListView adapter on the UI thread.
     * Called from onPostExecute of LoadStepsAsyncTask and potentially elsewhere.
     */
    private void updateListViewAdapter() {
        // Create the adapter or update the existing one
        if (listingAdapter == null) {
            // Use Activity context for the adapter
            listingAdapter = new ActivityEntryAdapter(this, mStepFinalList, this);
            mStepsListView.setAdapter(listingAdapter);
        } else {
            // This path might not be needed if mStepFinalList is updated and notifyDataSetChanged is called
            // If ActivityEntryAdapter has a setData method:
            // listingAdapter.setData(mStepFinalList);
            listingAdapter.notifyDataSetChanged(); // Just notify if data reference doesn't change but content does
        }
        // Ensure list view is visible if it was hidden
        mStepsListView.setVisibility(View.VISIBLE);
    }


    /**
     * Helper function to match a single step entry date with a post permlink.
     * Designed to be called from a background thread (specifically doInBackground).
     * Needs the JSONArray of posts passed in, not relying on instance field directly.
     * Added Context parameter for SingleHivePostModel construction.
     */
    private String matchUserPostLinkInternal(JSONArray posts, String entryDate, android.content.Context appContext){
        if (posts == null || posts.length() == 0) {
            return ""; // No posts to check
        }
        for (int i = 0; i < posts.length(); i++) {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    Log.d(MainActivity.TAG, "Post matching interrupted");
                    return ""; // Stop processing if task is cancelled
                }
                JSONObject post = posts.getJSONObject(i);
                // Ensure SingleHivePostModel is safe to construct/use on a background thread
                // Use ApplicationContext here if SingleHivePostModel needs it
                SingleHivePostModel postEntry = new SingleHivePostModel(post, appContext);
                //same date
                if (postEntry.postDateMatches(entryDate)) {
                    // Make sure MainActivity.username is static or safely accessible
                    if (postEntry.author.equals(MainActivity.username) && postEntry.hasActivityCount()
                            && (postEntry.hasActifitTag() )
                    ) {
                        return postEntry.permlink;
                    }
                }
            } catch (Exception e) {
                // Log specific error for matching a post, don't crash the task
                Log.e(MainActivity.TAG, "ERROR matching post for date " + entryDate, e);
            }
        }
        return ""; // Return empty string if no match found
    }

    // The original fillData method is no longer needed as its logic is split
    // void fillData() { ... }

    // The original loadPosts method is no longer needed
    // void loadPosts() { ... }

    // The original getDataForList method is no longer needed
    // public void getDataForList() { ... }
}