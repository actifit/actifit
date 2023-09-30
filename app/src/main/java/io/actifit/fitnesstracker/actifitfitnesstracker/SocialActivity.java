package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.opengl.Visibility;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

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
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import info.androidhive.fontawesome.FontTextView;

import static com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread;
import static io.actifit.fitnesstracker.actifitfitnesstracker.MainActivity.TAG;

public class SocialActivity extends BaseActivity {

    private ListView socialView;
    private ArrayList<SingleHivePostModel> posts;
    private ProgressBar progress, progressMore;
    //private ProgressDialog progress;
    private PostAdapter postAdapter;

    public static Double afitBalance = 0.0;

    JSONObject afitPrice;
    RequestQueue queue = null;
    Button loadMoreBtn;

    //fetch posts directly from chain
    String hiveRPCUrl;
    String productsCall;

    String sort = "created";
    String tag;

    String start_author = "";
    String start_permlink = "";

    @Override
    protected void onResume() {
        super.onResume();
        if (queue==null){
            queue = Volley.newRequestQueue(this);
        }
        progress.setVisibility(View.VISIBLE);


        //progress.show();
        //loadBalance(queue);
    }

    @Override
    protected void onPostResume(){
        super.onPostResume();
        if (posts.size() > 0){
            //already loaded, hide
            progress.setVisibility(View.GONE);
        }
    }

    private void loadBalance(RequestQueue queue){
        //fetch user balance on actifit.io
        if (MainActivity.username.equals("") || MainActivity.username.length() <1){
            return;
        }
        String balanceUrl = getString(R.string.user_balance_api_url)+MainActivity.username;

        //display header
        //actifitBalanceLbl.setVisibility(View.VISIBLE);
        // Request the balance of the user while expecting a JSON response
        JsonObjectRequest balanceRequest = new JsonObjectRequest
                (Request.Method.GET, balanceUrl, null, response -> {
                    //hide dialog
                    //progress.setVisibility(View.GONE);
                    //progress.hide();
                    // Display the result
                    try {
                        //grab current token count
                        afitBalance = Double.parseDouble(response.getString("tokens"));
                    }catch(JSONException e){
                        //hide dialog
                        Log.e(TAG, "AFIT balance load error");
                    }
                }, error -> {
                    //hide dialog
                    Log.e(TAG, "AFIT balance load error");
                });

        // Add balance request to be processed
        queue.add(balanceRequest);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.social_page);

        socialView = findViewById(R.id.postList);

        progress = findViewById(R.id.loader);
        progressMore = findViewById(R.id.loaderMore);

        loadMoreBtn = findViewById(R.id.load_more);

        posts = new ArrayList<>();

        //initialize needed query
        tag = getString(R.string.actifit_community);

        // Instantiate the RequestQueue.
        queue = Volley.newRequestQueue(this);

        final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

        //set load more functionality
        loadMoreBtn.setOnClickListener(view -> {
            loadMoreBtn.setVisibility(View.GONE);
            //progressMore.setVisibility(View.VISIBLE);
            loadPosts(false);
        });


        //set modal for social info
        TextView modalBtn = findViewById(R.id.social_info);
        modalBtn.setOnClickListener(v -> {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

            AlertDialog pointer = dialogBuilder.setMessage(Html.fromHtml(getString(R.string.social_description)))
                    .setTitle(getString(R.string.social_title))
                    .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                    .setPositiveButton(getString(R.string.close_button), null)
                    .create();

            dialogBuilder.show();
        });

        //capture scroll event to bottom

        socialView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                // Not needed for this implementation
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (postAdapter != null  && postAdapter.Size() > 0) {
                    if (socialView.getLastVisiblePosition() == totalItemCount - 1
                            && socialView.getChildAt(socialView.getChildCount() - 1).getBottom() <= socialView.getHeight()) {
                        // Scrolled to the bottom
                        // Perform your action here
                        loadMoreBtn.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        /*socialView.post(() -> {
            // Delayed task to be executed after rendering is complete
            progress.setVisibility(View.GONE); // Hide the ProgressBar
        });*/

        //refresh user login
        if (!MainActivity.username.equals("")) {
            String pkey = sharedPreferences.getString("actifitPst", "");

            //authorize user login based on credentials if user is already verified
            if (!pkey.equals("")) {
                String loginAuthUrl = getString(R.string.live_server)
                        + getString(R.string.login_auth);


                JSONObject loginSettings = new JSONObject();
                try {
                    loginSettings.put(getString(R.string.username_param), MainActivity.username);
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
                                        Log.d(TAG, response.toString());
                                        LoginActivity.accessToken = response.getString(getString(R.string.login_token));
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
                                Log.e(TAG, "Login error");
                            }
                        });

                queue.add(loginRequest);
            }


            loadBalance(queue);


        }
        //load posts for first time
        loadPosts(true);

    }



    public void loadPosts(Boolean showFullProgress){


        HiveRequests hiveReq = new HiveRequests(this);

        Thread thread = new Thread(() -> {

            try {
                JSONObject params = new JSONObject();
                params.put("sort", sort);
                params.put("tag", tag);
                params.put("start_author", start_author);
                params.put("start_permlink", start_permlink);

                //progress = new ProgressBar(this);
                // progress = new ProgressDialog(this);
                runOnUiThread(() -> {
                            if (showFullProgress) {
                                progress.setVisibility(View.VISIBLE);
                            } else {
                                progressMore.setVisibility(View.VISIBLE);
                            }
                        });


                //grab array from result
                JSONArray result = hiveReq.getRankedPosts(params);
                SingleHivePostModel lastPost = null;

                for (int i = 0; i < result.length(); i++) {
                    // Retrieve each JSON object within the JSON array
                    //JSONObject jsonObject = new JSONObject()

                    SingleHivePostModel postEntry = new SingleHivePostModel((result.getJSONObject(i)));
                    lastPost = postEntry;
                    posts.add(postEntry);
                    //grab post AFIT rewards

                    //Thread thread = new Thread(() -> {
                        try {
                            //send out server notification registration with username and token
                            String reqUrl = getString(R.string.post_rewards_api_url).replace("_USER_", postEntry.author).replace("_URL_", postEntry.url);

                            JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, reqUrl, null,
                                    response -> {
                                        try {
                                            //Double afitRewards = Double.parseDouble();
                                            postEntry.afitRewards = Double.parseDouble(response.getDouble("token_count") + "");
                                            //callback.onSuccess(response.getString(soughtVal));
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    },
                                    error -> Log.e(TAG, error.getMessage()));

                            // Add the request to the request queue
                            // (Assuming you have a RequestQueue instance named "requestQueue")
                            queue.add(request);

                            //postEntry.afitRewards = Double.parseDouble(afitRewards);
                        } catch (Exception e) {
                            Log.e(TAG, e.getMessage());
                        }
                    //});
                    //thread.start();

                }

                //update last loaded post author and permlink for pagination purposes
                if (lastPost != null) {
                    start_author = lastPost.author;
                    start_permlink = lastPost.permlink;
                }
                //Collections.sort(posts);
                // Create the adapter to convert the array to views
                //String pkey = sharedPreferences.getString("actifitPst", "");

                postAdapter = new PostAdapter(getBaseContext(), posts, socialView, SocialActivity.this, false);
                //postAdapter = new PostAdapter(getApplicationContext(), posts, socialView, SocialActivity.this, false);

                // Execute UI-related code on the main thread
                runOnUiThread(() -> {

                    if (!showFullProgress) {

                        //case for maintaining scroll position upon append
                        int currentPosition = socialView.getFirstVisiblePosition();
                        View v = socialView.getChildAt(0);
                        int topOffset = (v == null) ? 0 : v.getTop();

                        // Set the new adapter
                        socialView.setAdapter(postAdapter);

                        // Restore the scroll position
                        socialView.setSelectionFromTop(currentPosition, topOffset);

                    } else {
                        socialView.setAdapter(postAdapter);
                    }

                    //only hide after ready to render
                    socialView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            // Remove the listener to avoid multiple callbacks
                            socialView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                            // Delayed task to be executed after rendering is complete
                            progress.setVisibility(View.GONE); // Hide the ProgressBar
                            progressMore.setVisibility(View.GONE);
                        }
                    });

                    //hide load more button
                    loadMoreBtn.setVisibility(View.INVISIBLE);

                });


            } catch (Exception e) {
                //hide dialog
                progress.setVisibility(View.GONE);
                progressMore.setVisibility(View.GONE);
                //progress.hide();
                //actifitTransactionsError.setVisibility(View.VISIBLE);
                e.printStackTrace();
            }



        });
        thread.start();

    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if ( progress!=null){
            progress.setVisibility(View.GONE);
            //progress.hide();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        progress.setVisibility(View.VISIBLE);
        //progress.show();
        // The activity is about to become visible to the user
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if ( progress!=null){
            progress.setVisibility(View.GONE);
            //progress.hide();
        }
    }

}
