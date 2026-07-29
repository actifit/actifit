package io.actifit.fitnesstracker.actifitfitnesstracker;

import static com.google.android.material.internal.ViewUtils.dpToPx;

import static io.actifit.fitnesstracker.actifitfitnesstracker.Utils.setBackgroundFromThemeAttribute;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.AbsListView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.transition.TransitionManager;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import androidx.core.widget.NestedScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WalletActivity extends BaseActivity {

    private ProgressDialog progress;
    public static JSONObject hiveChainInfo;
    private JSONObject steemChainInfo;
    private JSONObject blurtChainInfo;

    private Double blurtPrice = 0.02;
    private AlertDialog.Builder pendingRewardsDialogBuilder;
    private AlertDialog pendingRewardsDialog;
    private JSONObject innerRewards = new JSONObject();
    private String username;
    private String accessToken;

    private JSONArray heTokens;

    // private ProgressBar loader;
    Activity callerActivity;

    TextView hiveActionsExpander;

    RequestQueue queue;

    TextView BtnCheckBalance, loadPendingRewards, claimRewards, sendAFIT,
            sendToken, stakeToken, unstakeToken, sendHEToken, stakeHEToken,
            unstakeHEToken, BtnCheckHEBalance;

    RotateAnimation rotate;
    String afitBal = "";

    private ViewGroup rootView; // Root layout for transitions

    // Headers
    private LinearLayout headerCoreBalance;
    private LinearLayout headerHeBalance;
    private LinearLayout headerClaimableRewards;
    private LinearLayout headerTransactions;
    private LinearLayout headerHiveTransactions;

    // Content Containers
    private LinearLayout contentCoreBalance;
    private NestedScrollView contentHeBalanceScrollView;
    private LinearLayout contentClaimableRewards;
    private LinearLayout contentTransactions;
    private LinearLayout contentHiveTransactions;

    // Indicators
    private TextView indicatorCoreBalance;
    private TextView indicatorHeBalance;
    private TextView indicatorClaimableRewards;
    private TextView indicatorTransactions;
    private TextView indicatorHiveTransactions;

    // Hive transactions views
    private ListView hiveTransactionsView;
    private TextView hiveTransactionsError;
    private TextView btnRefreshHiveTransactions;
    private TextView btnLoadMoreHiveTransactions;
    private ArrayList<TransactionItem> hiveTransactionsList = new ArrayList<>();
    private TransactionAdapter hiveTransactionsAdapter;
    private long hiveHistoryMinSeq = -1;
    private boolean hasMoreHiveHistory = false;

    // List to hold pairs of (contentView, indicatorView) for easy management
    private List<Pair<View, TextView>> sectionPairs;

    // Method to toggle visibility and hide others (Accordion behavior)
    // Now takes both the content view and its indicator
    private void toggleSectionVisibility(View contentToToggle, TextView indicatorToToggle) {
        // Optional: Add animation for smoother transitions
        if (rootView != null) {
            TransitionManager.beginDelayedTransition(rootView);
        }

        if (contentToToggle.getVisibility() == View.VISIBLE) {
            // If the clicked section is already open, close it
            contentToToggle.setVisibility(View.GONE);
            // Rotate indicator down (0 degrees)
            indicatorToToggle.animate().rotation(0).setDuration(200).start();
        } else {
            // Otherwise, close all sections and open the clicked one
            for (Pair<View, TextView> section : sectionPairs) {
                View contentView = section.first;
                TextView indicatorView = section.second;

                if (contentView.getVisibility() == View.VISIBLE) {
                    contentView.setVisibility(View.GONE);
                    // Rotate indicator down (0 degrees) for the section being closed
                    indicatorView.animate().rotation(0).setDuration(200).start();
                }
            }
            // Now open the selected section
            contentToToggle.setVisibility(View.VISIBLE);
            // Rotate indicator up (180 degrees) for the section being opened
            indicatorToToggle.animate().rotation(180).setDuration(200).start();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        // 1. Get references to Views
        rootView = findViewById(R.id.cards_container);

        headerCoreBalance = findViewById(R.id.header_core_balance);
        contentCoreBalance = findViewById(R.id.content_core_balance);
        indicatorCoreBalance = findViewById(R.id.indicator_core_balance);

        headerHeBalance = findViewById(R.id.header_he_balance);
        contentHeBalanceScrollView = findViewById(R.id.content_he_balance_scrollview);
        indicatorHeBalance = findViewById(R.id.indicator_he_balance);

        headerClaimableRewards = findViewById(R.id.header_claimable_rewards);
        contentClaimableRewards = findViewById(R.id.content_claimable_rewards);
        indicatorClaimableRewards = findViewById(R.id.indicator_claimable_rewards);

        headerTransactions = findViewById(R.id.header_transactions);
        contentTransactions = findViewById(R.id.content_transactions);
        indicatorTransactions = findViewById(R.id.indicator_transactions);

        headerHiveTransactions = findViewById(R.id.header_hive_transactions);
        contentHiveTransactions = findViewById(R.id.content_hive_transactions);
        indicatorHiveTransactions = findViewById(R.id.indicator_hive_transactions);
        hiveTransactionsView = findViewById(R.id.hive_transactions_list);
        hiveTransactionsError = findViewById(R.id.hive_transactions_error);
        btnRefreshHiveTransactions = findViewById(R.id.btn_refresh_hive_transactions);
        btnLoadMoreHiveTransactions = findViewById(R.id.btn_load_more_hive_transactions);

        // Allow ListView to scroll inside NestedScrollView
        hiveTransactionsView.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });

        // Show Load More only when scrolled to the bottom of the list
        hiveTransactionsView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int scrollState) {}
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
                boolean atBottom = totalItemCount > 0 &&
                        (firstVisibleItem + visibleItemCount >= totalItemCount);
                btnLoadMoreHiveTransactions.setVisibility(
                        (atBottom && hasMoreHiveHistory) ? View.VISIBLE : View.GONE);
            }
        });

        // 2. Create the list of section pairs
        sectionPairs = new ArrayList<>();
        sectionPairs.add(new Pair<>(contentCoreBalance, indicatorCoreBalance));
        sectionPairs.add(new Pair<>(contentHeBalanceScrollView, indicatorHeBalance));
        sectionPairs.add(new Pair<>(contentClaimableRewards, indicatorClaimableRewards));
        sectionPairs.add(new Pair<>(contentTransactions, indicatorTransactions));
        sectionPairs.add(new Pair<>(contentHiveTransactions, indicatorHiveTransactions));

        // 3. Set up Click Listeners
        // Pass both the content and the indicator to the toggle method
        headerCoreBalance.setOnClickListener(v -> toggleSectionVisibility(contentCoreBalance, indicatorCoreBalance));
        headerHeBalance
                .setOnClickListener(v -> toggleSectionVisibility(contentHeBalanceScrollView, indicatorHeBalance));
        headerClaimableRewards
                .setOnClickListener(v -> toggleSectionVisibility(contentClaimableRewards, indicatorClaimableRewards));
        headerTransactions.setOnClickListener(v -> toggleSectionVisibility(contentTransactions, indicatorTransactions));
        headerHiveTransactions
                .setOnClickListener(v -> toggleSectionVisibility(contentHiveTransactions, indicatorHiveTransactions));

        // define standard rotate animation

        rotate = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(2000);
        rotate.setInterpolator(new LinearInterpolator());
        rotate.setRepeatCount(Animation.INFINITE);

        // loader = findViewById(R.id.loader);

        hiveActionsExpander = findViewById(R.id.expand_view);

        LinearLayout expandedView = findViewById(R.id.hive_actions_container);
        hiveActionsExpander.setOnClickListener(v -> {
            if (expandedView.getVisibility() == View.GONE) {
                // expand
                expandedView.setVisibility(View.VISIBLE);
                // switch button wording
                hiveActionsExpander.setText("\uf0aa");
            } else {
                expandedView.setVisibility(View.GONE);
                // switch button wording
                hiveActionsExpander.setText("\uf0ab");
            }
        });

        // grab links to layout items for later use
        // final TextView steemitUsername = findViewById(R.id.steemit_username);
        // Button BtnCheckBalance = findViewById(R.id.btn_get_balance);

        BtnCheckBalance = findViewById(R.id.btn_refresh_balance);

        BtnCheckHEBalance = findViewById(R.id.btn_refresh_he_balance);

        // try to check first if we had a user defined already and saved to preferences
        // retrieving account data for simple reuse. Data is not stored anywhere outside
        // actifit App.
        SharedPreferences sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);
        // SharedPreferences.Editor editor = sharedPreferences.edit();

        // grab stored value, if any
        username = sharedPreferences.getString("actifitUser", "");
        // steemitUsername.setText(curUser);

        callerActivity = this;
        final Context callerContext = this;

        queue = Volley.newRequestQueue(this);

        loadPendingRewards = findViewById(R.id.btn_get_pending_rewards);

        loadPendingRewards.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                displayPendingRewards();

            }
        });

        stakeToken = findViewById(R.id.btn_stake_token);
        unstakeToken = findViewById(R.id.btn_unstake_token);

        btnRefreshHiveTransactions.setOnClickListener(v -> loadHiveTransactions(username));
        btnLoadMoreHiveTransactions.setOnClickListener(v -> fetchHiveHistoryBatch(username, hiveHistoryMinSeq - 1, 0, false));

        // make sure we have a value, and if so, automatically grab it
        if (!username.equals("")) {
            // if we already have data, emulate a click to grab the info
            loadAccountBalance(username, callerActivity, callerContext);

            loadHEBalance(username);

            loadHiveTransactions(username);

            // fetch user global settings - server based

            String pkey = sharedPreferences.getString("actifitPst", "");

            // authorize user login based on credentials if user is already verified
            if (!pkey.equals("")) {
                String loginAuthUrl = Utils.apiUrl(this) + getString(R.string.login_auth);

                JSONObject loginSettings = new JSONObject();
                try {
                    loginSettings.put(getString(R.string.username_param), username);
                    loginSettings.put(getString(R.string.pkey_param), pkey);
                    loginSettings.put(getString(R.string.bchain_param), "HIVE");// default always HIVE
                    loginSettings.put(getString(R.string.keeploggedin_param), false);// TODO make dynamic
                    loginSettings.put(getString(R.string.login_source),
                            getString(R.string.android) + BuildConfig.VERSION_NAME);
                } catch (JSONException e) {
                    // Log.e(MainActivity.TAG, e.getMessage());
                }

                // grab auth token for logged in user
                JsonObjectRequest loginRequest = new JsonObjectRequest(Request.Method.POST,
                        loginAuthUrl, loginSettings,
                        response -> {
                            // store token for reuse when saving settings
                            try {
                                if (response.has("success")) {
                                    Log.d(MainActivity.TAG, response.toString());
                                    accessToken = response.getString(getString(R.string.login_token));
                                    LoginActivity.accessToken = accessToken;
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
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

            // handles sending AFIT tokens
            sendAFIT = findViewById(R.id.btn_send_afit);

            sendAFIT.setOnClickListener(arg0 -> {

                SendAFITModalDialogFragment dialogFragment = SendAFITModalDialogFragment.newInstance(afitBal);
                FragmentManager fmgr = getSupportFragmentManager();
                dialogFragment.show(fmgr, "send_afit_modal");

            });

            claimRewards = findViewById(R.id.btn_claim_pending_rewards);

            claimRewards.setOnClickListener(arg0 -> {

                progress = new ProgressDialog(callerContext);
                progress.setMessage(getString(R.string.claiming_rewards));
                progress.show();

                claimRewards.startAnimation(rotate);

                RequestQueue queue1 = Volley.newRequestQueue(callerContext);

                // fetch blurt price
                String claimRewardsUrl = Utils.apiUrl(this) + getString(R.string.claim_rewards_url) + username;
                final String success_notification = getString(R.string.rewards_claimed_successfully);
                final String error_notification = getString(R.string.rewards_claim_error);

                // Process claim rewards request
                JsonObjectRequest claimRewardsReq = new JsonObjectRequest(Request.Method.GET, claimRewardsUrl, null,
                        new Response.Listener<JSONObject>() {
                            JSONObject hiveClaim;

                            @Override
                            public void onResponse(JSONObject response) {

                                // Display the result
                                try {
                                    // hive is main claim indicator
                                    hiveClaim = response.getJSONObject("hive");
                                    if (hiveClaim.has("success")) {
                                        displayNotification(success_notification, null, callerContext, callerActivity,
                                                false);

                                        // update all balances after 5 seconds
                                        new android.os.Handler().postDelayed(
                                                new Runnable() {
                                                    public void run() {
                                                        loadAccountBalance(username, callerActivity, callerContext);
                                                    }
                                                }, 5000);
                                    } else if (!hiveClaim.getString("error").equals("")) {
                                        displayNotification(hiveClaim.getString("error"), null, callerContext,
                                                callerActivity, false);
                                    } else {
                                        displayNotification(error_notification, null, callerContext, callerActivity,
                                                false);
                                    }
                                } catch (JSONException e) {
                                    // server responded but not in the expected shape — log the raw body
                                    Log.e(MainActivity.TAG, "error claiming rewards - unexpected response body: " + response, e);
                                    displayNotification(error_notification + " (unexpected response)", null, callerContext, callerActivity, false);
                                }

                                if (progress != null && progress.isShowing()) {
                                    progress.dismiss();
                                }
                                claimRewards.clearAnimation();
                            }
                        }, new Response.ErrorListener() {

                            @Override
                            public void onErrorResponse(VolleyError error) {
                                // diagnostics: capture HTTP status + server body + auth-token presence so
                                // field-reported failures are actionable (401 = auth, 5xx = server, etc.)
                                int httpStatus = (error.networkResponse != null) ? error.networkResponse.statusCode : -1;
                                String body = "";
                                if (error.networkResponse != null && error.networkResponse.data != null) {
                                    try {
                                        body = new String(error.networkResponse.data, java.nio.charset.StandardCharsets.UTF_8);
                                    } catch (Exception ignored) {
                                    }
                                }
                                boolean tokenPresent = (accessToken != null && !accessToken.isEmpty());
                                Log.e(MainActivity.TAG, "error claiming rewards - httpStatus=" + httpStatus
                                        + " tokenPresent=" + tokenPresent + " body=" + body, error);
                                // surface a concise, screenshot-able detail to the user for field reports
                                String detail = (httpStatus != -1) ? " (code " + httpStatus + ")"
                                        : (!tokenPresent ? " (not authenticated)" : " (no server response)");
                                displayNotification(error_notification + detail, null, callerContext, callerActivity, false);
                                if (progress != null && progress.isShowing()) {
                                    progress.dismiss();
                                }
                                claimRewards.clearAnimation();
                            }
                        }) {

                    @Override
                    public Map<String, String> getHeaders() throws AuthFailureError {
                        final Map<String, String> params = new HashMap<>();
                        params.put("Content-Type", "application/json");
                        params.put(getString(R.string.validation_header),
                                getString(R.string.validation_pre_data) + " " + accessToken);
                        return params;
                    }
                };

                queue1.add(claimRewardsReq);

            });

            // prepare pending rewards dialog
            pendingRewardsDialogBuilder = new AlertDialog.Builder(this);
            pendingRewardsDialogBuilder.setMessage(getString(R.string.loading));
        }
        // handle activity to fetch balance
        BtnCheckBalance.setOnClickListener(arg0 -> loadAccountBalance(username, callerActivity, callerContext));

        BtnCheckHEBalance.setOnClickListener(arg0 -> loadHEBalance(username));
    }

    int queriesFetchedPendingRewards = 0;
    int totalQueryCountPendingRewards = 0;

    // handles fetching and displaying pending user rewards
    public void displayPendingRewards() {
        // grab stored value, if any
        final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);
        username = sharedPreferences.getString("actifitUser", "");

        final Context ctx = this;

        if (username != "") {

            queriesFetchedPendingRewards = 0;
            totalQueryCountPendingRewards = 2;

            loadPendingRewards.startAnimation(rotate);

            progress = new ProgressDialog(ctx);
            progress.setMessage(getString(R.string.fetching_pending_rewards));
            progress.show();

            // handles sending out API query requests
            RequestQueue queue = Volley.newRequestQueue(this);

            // fetch blurt price
            String blurtPriceUrl = getString(R.string.coingecko_price).replace("CURRENCY", "BLURT");

            // Request the rank of the user while expecting a JSON response
            JsonObjectRequest blurtPriceReq = new JsonObjectRequest(Request.Method.GET, blurtPriceUrl, null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {

                            // Display the result
                            try {
                                blurtPrice = response.getJSONObject("blurt").getDouble("usd");
                                // update text as dialog should already be showing in case call returns after
                                if (pendingRewardsDialog != null) {// && pendingRewardsDialog.isShowing()){
                                    String hiveRewards = MainActivity.parseRewards(innerRewards, "HIVE", "HBD", 1.0);
                                    String steemRewards = MainActivity.parseRewards(innerRewards, "STEEM", "SBD", 1.0);
                                    String blurtRewards = MainActivity.parseRewards(innerRewards, "BLURT", "BLURT",
                                            blurtPrice);
                                    // update the text message as dialog is already showing
                                    String msg = "";

                                    msg += !hiveRewards.equals("") ? hiveRewards : "";
                                    msg += !steemRewards.equals("") ? steemRewards : "";
                                    msg += !blurtRewards.equals("") ? blurtRewards : "";

                                    pendingRewardsDialogBuilder.setMessage(Html.fromHtml(msg));

                                    if (!msg.equals("")) {
                                        // pending rewards exist
                                        pendingRewardsDialogBuilder.setMessage(Html.fromHtml(msg));

                                    } /*
                                       * else{
                                       * Toast.makeText(ctx,
                                       * getString(R.string.no_pending_rewards),Toast.LENGTH_LONG);
                                       * }
                                       */
                                }
                            } catch (JSONException jsex) {
                                jsex.printStackTrace();
                            }
                            queriesFetchedPendingRewards += 1;
                            if (queriesFetchedPendingRewards >= totalQueryCountPendingRewards) {
                                loadPendingRewards.clearAnimation();
                            }
                        }
                    }, new Response.ErrorListener() {

                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // hide dialog
                            // error.printStackTrace();
                            Log.e(MainActivity.TAG, "error fetching blurt price");
                            queriesFetchedPendingRewards += 1;
                            if (queriesFetchedPendingRewards >= totalQueryCountPendingRewards) {
                                loadPendingRewards.clearAnimation();
                            }
                        }
                    });

            queue.add(blurtPriceReq);

            // fetch user pending rewards and display notification

            // This holds the url to connect to the API and grab the pending rewards.
            // We append to it the username
            String userPendingRewardsUrl = Utils.apiUrl(this) + getString(R.string.user_pending_rewards_url) + username;

            // Request the rank of the user while expecting a JSON response
            JsonObjectRequest pendRewardsRequest = new JsonObjectRequest(Request.Method.GET, userPendingRewardsUrl,
                    null, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {

                            // Display the result
                            try {
                                innerRewards = response.getJSONObject("pendingRewards");

                                String hiveRewards = MainActivity.parseRewards(innerRewards, "HIVE", "HBD", 1.0);
                                String steemRewards = MainActivity.parseRewards(innerRewards, "STEEM", "SBD", 1.0);
                                String blurtRewards = MainActivity.parseRewards(innerRewards, "BLURT", "BLURT",
                                        blurtPrice);
                                // update the text message as dialog is already showing
                                String msg = "";

                                msg += !hiveRewards.equals("") ? hiveRewards : "";
                                msg += !steemRewards.equals("") ? steemRewards : "";
                                msg += !blurtRewards.equals("") ? blurtRewards : "";

                                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        switch (which) {
                                            case DialogInterface.BUTTON_POSITIVE:
                                                // take user to activity list on web

                                                // private void openUserRank(SharedPreferences sharedPreferences){
                                                username = sharedPreferences.getString("actifitUser", "");
                                                if (username != "") {
                                                    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

                                                    builder.setToolbarColor(
                                                            getResources().getColor(R.color.actifitRed));

                                                    // animation for showing and closing fitbit authorization screen
                                                    builder.setStartAnimations(ctx, R.anim.slide_in_right,
                                                            R.anim.slide_out_left);

                                                    // animation for back button clicks
                                                    builder.setExitAnimations(ctx, android.R.anim.slide_in_left,
                                                            android.R.anim.slide_out_right);

                                                    CustomTabsIntent customTabsIntent = builder.build();

                                                    customTabsIntent.launchUrl(ctx,
                                                            Uri.parse(MainActivity.ACTIFIT_CORE_URL + "/"
                                                                    + getString(R.string.activity_url_link) + "/"
                                                                    + username));
                                                }
                                                // }

                                                break;

                                            case DialogInterface.BUTTON_NEGATIVE:
                                                // cancel
                                                break;
                                        }
                                    }
                                };

                                if (progress != null && progress.isShowing()) {
                                    progress.dismiss();
                                }

                                if (!msg.equals("")) {
                                    // pending rewards exist
                                    pendingRewardsDialog = pendingRewardsDialogBuilder.setMessage(Html.fromHtml(msg))
                                            .setTitle(getString(R.string.pending_rewards_title))
                                            .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                                            .setPositiveButton(getString(R.string.my_activity_button),
                                                    dialogClickListener)
                                            .setNegativeButton(getString(R.string.close_button), dialogClickListener)
                                            .create();

                                    pendingRewardsDialogBuilder.show();
                                    /*
                                     * pendingRewardsDialog.getWindow().getAttributes().windowAnimations =
                                     * R.style.DialogAnimation;
                                     * pendingRewardsDialog.getWindow().getDecorView().setBackground(getDrawable(R.
                                     * drawable.dialog_shape));
                                     * //if (pointer.getWindow().isActive()) {
                                     * pendingRewardsDialog.show();
                                     * 
                                     */

                                } else {
                                    if (progress != null && progress.isShowing()) {
                                        progress.dismiss();
                                    }
                                    Toast.makeText(ctx, getString(R.string.no_pending_rewards), Toast.LENGTH_LONG)
                                            .show();
                                }

                            } catch (JSONException e) {
                                // hide dialog
                                e.printStackTrace();
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                            if (progress != null && progress.isShowing()) {
                                progress.dismiss();
                            }
                            queriesFetchedPendingRewards += 1;
                            if (queriesFetchedPendingRewards >= totalQueryCountPendingRewards) {
                                loadPendingRewards.clearAnimation();
                            }
                        }
                    }, error -> {
                        // hide dialog
                        // error.printStackTrace();
                        Log.e(MainActivity.TAG, "error fetching pending rewards");
                        if (progress != null && progress.isShowing()) {
                            progress.dismiss();
                            Toast.makeText(ctx, getString(R.string.error_fetching_data), Toast.LENGTH_LONG).show();
                        }
                        queriesFetchedPendingRewards += 1;
                        if (queriesFetchedPendingRewards >= totalQueryCountPendingRewards) {
                            loadPendingRewards.clearAnimation();
                        }
                    });

            // to enable waiting for longer time with extra retry
            pendRewardsRequest.setRetryPolicy(new DefaultRetryPolicy(
                    10000, // 10 seconds default timeout
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

            queue.add(pendRewardsRequest);

        }
    }

    int queriesFetched = 0;
    int totalQueryCount = 0;
    TextView hiveBalance;
    TextView hbdBalance;
    TextView hpBalance;
    TextView blurtBalance, bpBalance;
    TextView sportsBalance;
    TextView actifitBalance;
    boolean chainInfoFetched = false;
    JSONObject balanceData = null;

    void loadHEBalance(String username) {

        // cleanup existing content
        LinearLayout tokensContainer = findViewById(R.id.he_tokens_container);

        tokensContainer.removeAllViewsInLayout();

        // add header view
        View walletHeader = LayoutInflater.from(this)
                .inflate(R.layout.wallet_header, tokensContainer, false);
        tokensContainer.addView(walletHeader);

        // separator below header
        TableRow separatorRow = new TableRow(this);
        View separatorLine = new View(this);
        TableRow.LayoutParams separatorParams = new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, Utils.dpToPx(this, 1));
        separatorParams.span = 4;
        separatorLine.setLayoutParams(separatorParams);
        setBackgroundFromThemeAttribute(separatorLine, android.R.attr.listDivider);
        separatorRow.addView(separatorLine);
        tokensContainer.addView(separatorRow);

        HiveEngineAPI herpc = new HiveEngineAPI(getApplicationContext());
        BtnCheckHEBalance.startAnimation(rotate);

        herpc.fetchAllTokens(new HiveEngineAPI.VolleyCallback() {
            @Override
            public void onSuccess(JSONArray tokenExtraDetails) {
                herpc.queryHEContract(username, new HiveEngineAPI.VolleyCallback() {
                    @Override
                    public void onSuccess(JSONArray result) {
                        displayHETokens(result, tokenExtraDetails, tokensContainer);
                        BtnCheckHEBalance.clearAnimation();
                    }
                    @Override
                    public void onFailure(String error) {
                        System.out.println(error);
                        BtnCheckHEBalance.clearAnimation();
                    }
                });
            }
            @Override
            public void onFailure(String error) {
                herpc.queryHEContract(username, new HiveEngineAPI.VolleyCallback() {
                    @Override
                    public void onSuccess(JSONArray result) {
                        displayHETokens(result, new JSONArray(), tokensContainer);
                        BtnCheckHEBalance.clearAnimation();
                    }
                    @Override
                    public void onFailure(String error2) {
                        System.out.println(error2);
                        BtnCheckHEBalance.clearAnimation();
                    }
                });
            }
        });
    }

    private void displayHETokens(JSONArray heTokens, JSONArray tokenExtraDetails, LinearLayout tokensContainer) {
        if (heTokens.length() == 0) {
            TextView emptyView = new TextView(this);
            emptyView.setText(getString(R.string.no_he_tokens_lbl));
            emptyView.setGravity(android.view.Gravity.CENTER);
            emptyView.setPadding(0, Utils.dpToPx(this, 24), 0, Utils.dpToPx(this, 24));
            tokensContainer.addView(emptyView);
            return;
        }

        Handler uiHandler = new Handler(Looper.getMainLooper());
        DecimalFormat decimalFormat = new DecimalFormat("#,###,##0.000");

        for (int i = 0; i < heTokens.length(); i++) {
            try {
                JSONObject entry = heTokens.getJSONObject(i);
                View tokenView = LayoutInflater.from(WalletActivity.this)
                        .inflate(R.layout.he_token_entry, null, false);

                ImageView tokenIcon = tokenView.findViewById(R.id.token_icon);
                String symbol = entry.has("symbol") ? entry.getString("symbol") : "";

                TextView balance = tokenView.findViewById(R.id.balance);
                String balval = decimalFormat.format(entry.has("balance") ? entry.getDouble("balance") : 0);
                balance.setText(balval + " " + symbol);

                TextView stake = tokenView.findViewById(R.id.stake);
                String val = decimalFormat.format(entry.has("stake") ? entry.getDouble("stake") : 0);
                stake.setText(val + " " + symbol);

                String icon = "";
                boolean stakable = false;
                String unstakePeriod = "";

                if (!symbol.isEmpty()) {
                    tokenIcon.setImageDrawable(new LetterDrawable(symbol.substring(0, 1)));
                    for (int j = 0; j < tokenExtraDetails.length(); j++) {
                        JSONObject tokenDetail = tokenExtraDetails.getJSONObject(j);
                        if (tokenDetail.getString("symbol").equals(symbol)) {
                            try {
                                JSONObject meta = new JSONObject(tokenDetail.optString("metadata", "{}"));
                                icon = meta.optString("icon", "");
                                stakable = tokenDetail.optBoolean("stakingEnabled", false);
                                unstakePeriod = tokenDetail.has("unstakingCooldown")
                                        ? tokenDetail.getInt("unstakingCooldown") + " days" : "";
                                if (!icon.isEmpty()) {
                                    String finalIcon = icon;
                                    LetterDrawable placeholder = new LetterDrawable(symbol.substring(0, 1));
                                    uiHandler.post(() -> Glide.with(WalletActivity.this)
                                            .load(finalIcon).placeholder(placeholder).error(placeholder)
                                            .into(tokenIcon));
                                }
                            } catch (Exception inn) { inn.printStackTrace(); }
                            break;
                        }
                    }
                }

                TextView expander = tokenView.findViewById(R.id.expand_view);
                View expandedView = LayoutInflater.from(WalletActivity.this)
                        .inflate(R.layout.he_token_actions, null, false);

                expander.setOnClickListener(v -> {
                    if (expandedView.getVisibility() == View.GONE) {
                        expandedView.setVisibility(View.VISIBLE);
                        expander.setText("");
                    } else {
                        expandedView.setVisibility(View.GONE);
                        expander.setText("");
                    }
                });

                String finalIcon = icon;
                String finalUnstakePeriod = unstakePeriod;

                sendHEToken = expandedView.findViewById(R.id.btn_send_token);
                sendHEToken.setOnClickListener(arg0 -> {
                    SendTokenModalDialogFragment dlg = new SendTokenModalDialogFragment(
                            getApplicationContext(), balval, queue);
                    dlg.setHeToken(true, symbol, finalIcon);
                    ((AppCompatActivity) callerActivity).getSupportFragmentManager()
                            .beginTransaction().add(dlg, "send_he_token").commitAllowingStateLoss();
                });

                stakeHEToken = expandedView.findViewById(R.id.btn_stake_token);
                unstakeHEToken = expandedView.findViewById(R.id.btn_unstake_token);

                if (!stakable) {
                    stakeHEToken.setEnabled(false);
                    stakeHEToken.setTextColor(getResources().getColor(R.color.colorBlack));
                    unstakeHEToken.setEnabled(false);
                    unstakeHEToken.setTextColor(getResources().getColor(R.color.colorBlack));
                }
                if (Float.parseFloat(balval.replace(",", "")) == 0) {
                    stakeHEToken.setEnabled(false);
                    stakeHEToken.setTextColor(getResources().getColor(R.color.colorBlack));
                }
                if (Float.parseFloat(val.replace(",", "")) == 0) {
                    unstakeHEToken.setEnabled(false);
                    unstakeHEToken.setTextColor(getResources().getColor(R.color.colorBlack));
                }

                stakeHEToken.setOnClickListener(arg0 -> {
                    StakeTokenModalDialogFragment dlg = new StakeTokenModalDialogFragment(
                            getApplicationContext(), balval, queue, 0, true, symbol, finalIcon, finalUnstakePeriod);
                    ((AppCompatActivity) callerActivity).getSupportFragmentManager()
                            .beginTransaction().add(dlg, "stake_he_token").commitAllowingStateLoss();
                });

                unstakeHEToken.setOnClickListener(arg0 -> {
                    StakeTokenModalDialogFragment dlg = new StakeTokenModalDialogFragment(
                            getApplicationContext(), val, queue, 1, true, symbol, finalIcon, finalUnstakePeriod);
                    ((AppCompatActivity) callerActivity).getSupportFragmentManager()
                            .beginTransaction().add(dlg, "unstake_he_token").commitAllowingStateLoss();
                });

                tokensContainer.addView(tokenView);
                tokensContainer.addView(expandedView);
            } catch (Exception exc) {
                exc.printStackTrace();
            }
        }
    }

    void loadAccountBalance(String username, Activity callerActivity, Context callerContext) {

        if (username != null && !username.isEmpty()) {

            chainInfoFetched = false;

            queriesFetched = 0;
            totalQueryCount = 4;
            balanceData = null;

            // rotate.setRepeatMode(Animation.REVERSE);

            BtnCheckBalance.startAnimation(rotate);

            // skip on spaces, upper case, and @ symbols to properly match steem username
            // patterns
            username = username.trim().toLowerCase().replace("@", "");
            // connect to the interface to display result
            actifitBalance = findViewById(R.id.actifit_balance);
            // final TextView actifitBalanceLbl = findViewById(R.id.actifit_balance_lbl);
            final TextView actifitTransactionsLbl = findViewById(R.id.actifit_transactions_lbl);
            final ListView actifitTransactionsView = findViewById(R.id.actifit_transactions);
            final TextView actifitTransactionsError = findViewById(R.id.actifit_transactions_error);

            // Allow AFIT transactions ListView to scroll inside NestedScrollView
            actifitTransactionsView.setOnTouchListener((v, event) -> {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return false;
            });

            hiveBalance = findViewById(R.id.hive_balance);
            hbdBalance = findViewById(R.id.hbd_balance);
            hpBalance = findViewById(R.id.hp_balance);
            blurtBalance = findViewById(R.id.blurt_balance);
            bpBalance = findViewById(R.id.bp_balance);
            sportsBalance = findViewById(R.id.sports_balance);

            // hide if this is a recurring call
            actifitTransactionsError.setVisibility(View.GONE);

            // initialize progress dialog
            progress = new ProgressDialog(callerContext);

            // Instantiate the RequestQueue.
            RequestQueue queue = Volley.newRequestQueue(callerActivity);

            // This holds the url to connect to the API and grab the balance.
            // We append to it the username
            String balanceUrl = Utils.apiUrl(this) + getString(R.string.user_balance_api_url) + username;

            // display header
            // actifitBalanceLbl.setVisibility(View.VISIBLE);
            // Request the balance of the user while expecting a JSON response
            JsonObjectRequest balanceRequest = new JsonObjectRequest(Request.Method.GET, balanceUrl, null, response -> {
                // hide dialog
                progress.hide();
                // Display the result
                try {
                    afitBal = response.getString("tokens").replace(",", "");
                    // grab current token count

                    DecimalFormat decimalFormat = new DecimalFormat("#,###,##0.000");
                    actifitBalance.setText(decimalFormat.format(Float.parseFloat(afitBal)) + " AFIT");
                } catch (JSONException e) {
                    // hide dialog
                    progress.hide();
                    actifitBalance.setText(getString(R.string.unable_fetch_afit_balance));
                }
                queriesFetched += 1;
                if (queriesFetched >= totalQueryCount) {
                    BtnCheckBalance.clearAnimation();
                }
            }, error -> {
                // hide dialog
                progress.hide();
                actifitBalance.setText(getString(R.string.unable_fetch_afit_balance));
                queriesFetched += 1;
                if (queriesFetched >= totalQueryCount) {
                    BtnCheckBalance.clearAnimation();
                }
            });

            // Add balance request to be processed
            queue.add(balanceRequest);

            // grab chain info to convert vests to power value
            String chainDataUrl = Utils.apiUrl(this) + getString(R.string.get_chain_info);
            JsonObjectRequest chainInfoRequest = new JsonObjectRequest(Request.Method.GET, chainDataUrl, null,
                    response -> {
                        // hide dialog
                        // progress.hide();
                        try {
                            chainInfoFetched = true;
                            hiveChainInfo = response.getJSONObject("HIVE");
                            steemChainInfo = response.optJSONObject("STEEM");
                            blurtChainInfo = response.optJSONObject("BLURT");
                            loadData();
                        } catch (Exception e) {
                            // hide dialog
                            e.printStackTrace();
                        }
                        queriesFetched += 1;
                        if (queriesFetched >= totalQueryCount) {
                            BtnCheckBalance.clearAnimation();
                        }
                    }, error -> {
                        // hide dialog
                        progress.hide();
                        actifitBalance.setText(getString(R.string.unable_fetch_balance));
                        queriesFetched += 1;
                        if (queriesFetched >= totalQueryCount) {
                            BtnCheckBalance.clearAnimation();
                        }
                    });

            // Add balance request to be processed
            queue.add(chainInfoRequest);

            // This holds the url to connect to the API and grab the balance.
            // We append to it the username
            String accountDataUrl = Utils.apiUrl(this) + getString(R.string.get_account_api_url) + username;

            // display header
            // actifitBalanceLbl.setVisibility(View.VISIBLE);
            // Request the balance of the user while expecting a JSON response
            JsonObjectRequest userDataRequest = new JsonObjectRequest(Request.Method.GET, accountDataUrl, null,
                    response -> {
                        // hide dialog
                        // progress.hide();
                        balanceData = response;
                        loadData();
                    }, error -> {
                        // hide dialog
                        progress.hide();
                        actifitBalance.setText(getString(R.string.unable_fetch_balance));
                        queriesFetched += 1;
                        if (queriesFetched >= totalQueryCount) {
                            BtnCheckBalance.clearAnimation();
                        }
                    });

            // Add balance request to be processed
            queue.add(userDataRequest);

            // This holds the url to connect to the API and grab the transactions.
            // We append to it the username
            String transactionUrl = Utils.apiUrl(this) + getString(R.string.user_transactions_api_url) + username;

            // display header
            actifitTransactionsLbl.setVisibility(View.VISIBLE);

            // Request the transactions of the user first via JsonArrayRequest
            // according to our data format
            JsonArrayRequest transactionRequest = new JsonArrayRequest(Request.Method.GET,
                    transactionUrl, null, new Response.Listener<JSONArray>() {

                        @Override
                        public void onResponse(JSONArray transactionListArray) {
                            // hide dialog
                            progress.hide();

                            ArrayList<TransactionItem> transactionList = new ArrayList<>();

                            SimpleDateFormat jsonDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX",
                                    Locale.ROOT);

                            // Handle the result
                            try {

                                for (int i = 0; i < transactionListArray.length(); i++) {
                                    // Retrieve each JSON object within the JSON array
                                    JSONObject jsonObject = transactionListArray.getJSONObject(i);

                                    TransactionItem item = new TransactionItem();

                                    // Use optString and optDouble for safe parsing of potentially missing fields
                                    // provide a default value (like null or 0.0) if the key doesn't exist
                                    item.activityType = jsonObject.optString("reward_activity", null);
                                    item.tokenCount = jsonObject.optDouble("token_count", 0.0); // IMPORTANT: Parse as
                                                                                                // double
                                    item.user = jsonObject.optString("user", null);
                                    item.recipient = jsonObject.optString("recipient", null);

                                    item.date = jsonObject.optString("date", null); // Store original date string
                                    // --- Parse the date string into a Date object ---
                                    item.parsedDate = null; // Initialize to null
                                    if (item.date != null && !item.date.isEmpty()) {
                                        try {
                                            item.parsedDate = jsonDateFormat.parse(item.date);
                                        } catch (Exception e) {
                                            // Log the parsing error but allow the app to continue
                                            Log.e("WalletActivity", "Failed to parse date: " + item.date, e);
                                            // item.parsedDate remains null
                                        }
                                    }
                                    // ------------------------------------------------

                                    item.note = jsonObject.optString("note", null);
                                    item.url = jsonObject.optString("url", null); // Populate URL field

                                    transactionList.add(item);
                                }
                                // Create your custom adapter
                                TransactionAdapter adapter = new TransactionAdapter(
                                        callerContext, // Use 'this' in Activity, or 'getContext()' in Fragment
                                        R.layout.list_item_transaction, // Use your custom list item layout
                                        transactionList);

                                // Set the adapter to the ListView
                                actifitTransactionsView.setAdapter(adapter);

                                // actifitTransactions.setText("Response is: "+ response);
                            } catch (Exception e) {
                                // hide dialog
                                progress.hide();
                                actifitTransactionsError.setVisibility(View.VISIBLE);
                                e.printStackTrace();
                            }
                            queriesFetched += 1;
                            if (queriesFetched >= totalQueryCount) {
                                BtnCheckBalance.clearAnimation();
                            }
                        }
                    }, error -> {
                        // hide dialog
                        progress.hide();
                        // actifitTransactionsView.setText("Unable to fetch balance");
                        actifitTransactionsError.setVisibility(View.VISIBLE);
                        queriesFetched += 1;
                        if (queriesFetched >= totalQueryCount) {
                            BtnCheckBalance.clearAnimation();
                        }
                    });

            // Add transaction request to be processed
            queue.add(transactionRequest);

            // display a progress dialog not to keep the user waiting
            progress.setMessage(getString(R.string.fetching_user_balance));
            progress.show();

        } else {
            displayNotification(getString(R.string.username_missing), null,
                    callerContext, callerActivity, false);

        }
    }

    private void loadData() {
        try {
            if (!chainInfoFetched) {
                return;
            }
            if (balanceData == null) {
                return;
            }
            JSONObject hiveData = balanceData.getJSONObject("HIVE");

            // Display the result

            String hiveBalanceVal = hiveData.getString("balance");

            hiveBalance.setText(Html.fromHtml(hiveBalanceVal));

            String hbdBalanceVal = hiveData.getString("hbd_balance");

            hbdBalance.setText(Html.fromHtml(hbdBalanceVal));

            hiveChainInfo.put("chainName", "hive");
            // add HP balances
            String hpBalanceVal = MainActivity
                    .formatValue(vestsToPower(hiveChainInfo, hiveData.getString("vesting_shares")));
            // grab delegated balance
            String delegatedVal = MainActivity
                    .formatValue(vestsToPower(hiveChainInfo, hiveData.getString("delegated_vesting_shares")));
            // grab powering down balance
            String unstakingVal = MainActivity
                    .formatValue(vestsToPower(hiveChainInfo, hiveData.getString("vesting_withdraw_rate")));
            // owned power
            String incomingVal = MainActivity
                    .formatValue(vestsToPower(hiveChainInfo, hiveData.getString("received_vesting_shares")));
            Float ownedPower = Float.parseFloat(hpBalanceVal.replace(",", "")) -
                    Float.parseFloat(delegatedVal.replace(",", "")) -
                    Float.parseFloat(unstakingVal.replace(",", ""));
            String ownedPowerVal = MainActivity.formatValue(ownedPower);
            String fullPowerVal = MainActivity
                    .formatValue(vestsToPower(hiveChainInfo, hiveData.getString("post_voting_power")));
            hpBalance.setText(Html.fromHtml(ownedPowerVal) + " HP" + " (" + fullPowerVal + " HP)");

            // hiveBalances += " " + hpBalance + " HP";

            // hiveBalances += " \r\n";

            // grab current token count
            // actifitBalance.setText(" " + response.getString("tokens"));

            /*
             * //convert to VESTS
             * HiveRequests hiveReq = new HiveRequests(getApplicationContext());
             * hiveReq.getGlobalProps(
             * 
             * new HiveRequests.APIResponseListener() {
             * 
             * @Override
             * public void onResponse(JSONObject dynamicProps) {
             * // Step 5: Perform another API call
             * //performAnotherAPIRequest(dynamicProps);
             * try {
             * System.out.println(">>>>> dyn props response:" + dynamicProps.toString());
             * //convert HP to VESTS to send power down request
             * JSONObject totalHive = dynamicProps.getJSONObject("total_vesting_fund_hive");
             * String amount = totalHive.getString("amount");
             * JSONObject totalHiveVests =
             * dynamicProps.getJSONObject("total_vesting_shares");
             * String totAmount = totalHiveVests.getString("amount");
             * return parseFloat(hivePower * totalHiveVests / totalHive).toFixed(6);
             * }catch(Exception ex){
             * ex.printStackTrace();
             * }
             * 
             * }
             * 
             * @Override
             * public void onError(String errorMessage) {
             * // Handle the error
             * System.out.println(">>>>> dyn props error:" + errorMessage);
             * }
             * });
             */

            // handles sending HIVE/HBD tokens
            sendToken = findViewById(R.id.btn_send_token);
            String hiveBal = "0";
            try {
                hiveBal = hiveData.getString("balance").split(" ")[0];
            } catch (Exception exp) {
                exp.printStackTrace();
            }
            String finalHiveBal = hiveBal;

            sendToken.setOnClickListener(arg0 -> {

                SendTokenModalDialogFragment dialogFragment = new SendTokenModalDialogFragment(this, finalHiveBal,
                        queue);
                dialogFragment.setHeToken(false, "HIVE", "");
                // remove HBD part and comma formatting
                dialogFragment.setSecToken(Float.parseFloat(hbdBalanceVal.replace(" HBD", "").replace(",", "")), "HBD");
                FragmentManager fmgr = (this).getSupportFragmentManager();
                dialogFragment.show(fmgr, "send_token");

            });

            // handle staking action & params
            stakeToken.setOnClickListener(arg0 -> {
                try {
                    StakeTokenModalDialogFragment dialogFragment = new StakeTokenModalDialogFragment(this, finalHiveBal,
                            queue, 0,
                            false, "HIVE", getString(R.string.hive_logo_url), "13 weeks");
                    FragmentManager fmgr = (this).getSupportFragmentManager();
                    dialogFragment.show(fmgr, "stake_hive");

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // handle unstaking action & params
            unstakeToken.setOnClickListener(arg0 -> {
                try {
                    StakeTokenModalDialogFragment dialogFragment = new StakeTokenModalDialogFragment(this,
                            ownedPowerVal, queue, 1,
                            false, "HIVE", getString(R.string.hive_logo_url), "13 weeks");
                    FragmentManager fmgr = (this).getSupportFragmentManager();
                    dialogFragment.show(fmgr, "unstake_hive");

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // sportsBalance.setText("sports");

            // also display claimable balances
            String hiveRewards = "";
            hiveRewards += hiveData.getString("reward_hbd_balance") + " ";
            hiveRewards += hiveData.getString("reward_hive_balance") + " ";
            hiveRewards += hiveData.getString("reward_vesting_hive").replace("HIVE", "HP");

            TextView hiveRewardsTxt = findViewById(R.id.hive_rewards);
            hiveRewardsTxt.setText(hiveRewards);

            if (balanceData.has("BLURT") && blurtChainInfo != null) {
                JSONObject blurtData = balanceData.getJSONObject("BLURT");
                String blurtBalances = blurtData.getString("balance");
                blurtChainInfo.put("chainName", "blurt");
                blurtBalance.setText(Html.fromHtml(blurtBalances));

                String bpBalances = MainActivity
                        .formatValue(vestsToPower(blurtChainInfo, blurtData.getString("vesting_shares"))) + " BP";

                bpBalance.setText(" " + Html.fromHtml(bpBalances));

                String blurtRewards = "";
                blurtRewards += blurtData.getString("reward_blurt_balance") + " ";
                blurtRewards += blurtData.getString("reward_vesting_blurt").replace("BLURT", "BP");
                TextView blurtRewardsTxt = findViewById(R.id.blurt_rewards);
                blurtRewardsTxt.setText(blurtRewards);
            }

        } catch (Exception e) {
            // hide dialog
            progress.hide();
            actifitBalance.setText(getString(R.string.unable_fetch_balance));
        }
        queriesFetched += 1;
        if (queriesFetched >= totalQueryCount) {
            BtnCheckBalance.clearAnimation();
        }
    }

    public static Double powerToVests(JSONObject chain, Double hivePower) {
        Double totalHiveVests = 1.0;
        Double vests = 0.0;
        Double totalHive = 0.0;
        // DecimalFormat df = new DecimalFormat(".00");

        try {
            String vestingFund = chain.getString("total_vesting_fund_" + chain.getString("chainName"));
            String[] entries = vestingFund.split(" ");
            totalHive = Double.parseDouble(entries[0]);
            // .split(" ")[0];

            String totalVestsStr = chain.getString("total_vesting_shares");
            String[] vals = totalVestsStr.split(" ");
            totalHiveVests = Double.parseDouble(vals[0]);

            return hivePower * totalHiveVests / totalHive;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    private Double vestsToPower(JSONObject chain, String vestsValue) {
        Double powerVal = 0.0;
        Double totalVests = 1.0;
        Double vests = 0.0;
        // DecimalFormat df = new DecimalFormat(".00");

        try {
            String vestingFund = chain.getString("total_vesting_fund_" + chain.getString("chainName"));
            String[] entries = vestingFund.split(" ");
            powerVal = Double.parseDouble(entries[0]);
            // .split(" ")[0];

            String totalVestsStr = chain.getString("total_vesting_shares");
            String[] vals = totalVestsStr.split(" ");
            totalVests = Double.parseDouble(vals[0]);

            vestsValue = vestsValue.split(" ")[0];
            vests = Double.parseDouble(vestsValue);

            return powerVal * vests / totalVests;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (progress != null) {
            progress.dismiss();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (progress != null) {
            progress.dismiss();
        }
    }

    void displayNotification(final String notification, final ProgressDialog progress,
            final Context context, final Activity currentActivity,
            final Boolean closeScreen) {
        // render result
        currentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // hide the progressDialog
                if (progress != null) {
                    progress.dismiss();
                }

                AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
                builder1.setMessage(notification);

                builder1.setCancelable(true);

                builder1.setPositiveButton(
                        getString(R.string.dismiss_button),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                // if we need to close current Activity
                                if (closeScreen) {
                                    // close current screen
                                    Log.d(MainActivity.TAG, ">>>Finish");
                                    currentActivity.finish();
                                }
                            }
                        });
                // create and display alert window
                AlertDialog alert11 = builder1.create();
                builder1.show();
            }
        });

    }

    private void loadHiveTransactions(String username) {
        if (username == null || username.isEmpty()) return;
        // Reset state for a fresh load
        hiveTransactionsList.clear();
        hiveHistoryMinSeq = -1;
        hasMoreHiveHistory = false;
        hiveTransactionsAdapter = null;
        hiveTransactionsView.setAdapter(null);
        btnLoadMoreHiveTransactions.setVisibility(View.GONE);
        hiveTransactionsError.setVisibility(View.GONE);
        fetchHiveHistoryBatch(username, -1, 0, true);
    }

    // start: -1 for most recent, or oldest seq seen minus 1 for pagination
    // autoFetchCount: how many batches have been auto-fetched so far (max 3)
    // isRefresh: true when triggered by refresh button (shows spinner)
    private void fetchHiveHistoryBatch(String username, long start, int autoFetchCount, boolean isRefresh) {
        if (isRefresh) btnRefreshHiveTransactions.startAnimation(rotate);
        btnLoadMoreHiveTransactions.setVisibility(View.GONE);

        HiveRequests hiveRequests = new HiveRequests(this);
        String rpcUrl = hiveRequests.hiveRPCUrl;

        JSONObject requestBody = new JSONObject();
        try {
            JSONArray params = new JSONArray();
            params.put(username);
            params.put(start);
            params.put(1000); // fetch max per batch
            requestBody.put("jsonrpc", "2.0");
            requestBody.put("method", "condenser_api.get_account_history");
            requestBody.put("params", params);
            requestBody.put("id", 1);
        } catch (JSONException e) {
            e.printStackTrace();
            if (isRefresh) btnRefreshHiveTransactions.clearAnimation();
            return;
        }

        SimpleDateFormat hiveDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, rpcUrl, requestBody,
                response -> {
                    if (isRefresh) btnRefreshHiveTransactions.clearAnimation();
                    try {
                        JSONArray history = response.getJSONArray("result");
                        int newlyFound = 0;
                        long batchMinSeq = Long.MAX_VALUE;

                        // Iterate newest-first (reverse order of returned array)
                        for (int i = history.length() - 1; i >= 0; i--) {
                            JSONArray entry = history.getJSONArray(i);
                            long seq = entry.getLong(0);
                            if (seq < batchMinSeq) batchMinSeq = seq;

                            JSONObject txData = entry.getJSONObject(1);
                            JSONArray op = txData.getJSONArray("op");
                            String opType = op.getString(0);
                            JSONObject opData = op.getJSONObject(1);

                            TransactionItem item = new TransactionItem();
                            item.date = txData.optString("timestamp", "");
                            if (!item.date.isEmpty()) {
                                try { item.parsedDate = hiveDateFormat.parse(item.date); } catch (Exception ignored) {}
                            }

                            switch (opType) {
                                case "transfer":
                                    String to = opData.optString("to", "");
                                    String from = opData.optString("from", "");
                                    String amount = opData.optString("amount", "0");
                                    item.note = opData.optString("memo", "");
                                    item.tokenCount = parseHiveAmount(amount);
                                    item.tokenSymbol = extractHiveSymbol(amount);
                                    if (to.equals(username)) {
                                        item.activityType = "Transfer In";
                                        item.user = from;
                                    } else {
                                        item.activityType = "Transfer Out";
                                        item.recipient = to;
                                        item.tokenCount = -item.tokenCount;
                                    }
                                    break;
                                case "transfer_to_vesting":
                                    item.activityType = "Power Up";
                                    item.user = opData.optString("from", "");
                                    item.tokenCount = parseHiveAmount(opData.optString("amount", "0"));
                                    item.tokenSymbol = "HIVE";
                                    break;
                                case "withdraw_vesting":
                                    item.activityType = "Power Down";
                                    item.tokenCount = -parseHiveAmount(opData.optString("vesting_shares", "0"));
                                    item.tokenSymbol = "VESTS";
                                    break;
                                case "claim_reward_balance":
                                    item.activityType = "Claim Rewards";
                                    item.tokenCount = parseHiveAmount(opData.optString("reward_hive", "0"));
                                    item.tokenSymbol = "HIVE";
                                    item.note = "HBD: " + opData.optString("reward_hbd", "0");
                                    break;
                                default:
                                    continue;
                            }
                            hiveTransactionsList.add(item);
                            newlyFound++;
                        }

                        // Update cursor for next page
                        if (batchMinSeq != Long.MAX_VALUE) {
                            hiveHistoryMinSeq = batchMinSeq;
                        }

                        boolean hasMoreHistory = batchMinSeq != Long.MAX_VALUE && batchMinSeq > 0;

                        if (hiveTransactionsList.isEmpty() && !hasMoreHistory) {
                            hiveTransactionsError.setText(R.string.hive_transactions_empty);
                            hiveTransactionsError.setVisibility(View.VISIBLE);
                            return;
                        }

                        // Auto-fetch next batch if we found too few financial txns
                        if (newlyFound < 10 && hasMoreHistory && autoFetchCount < 3) {
                            fetchHiveHistoryBatch(username, hiveHistoryMinSeq - 1, autoFetchCount + 1, false);
                            return;
                        }

                        // Render the accumulated list
                        hiveTransactionsError.setVisibility(View.GONE);
                        if (hiveTransactionsAdapter == null) {
                            hiveTransactionsAdapter = new TransactionAdapter(
                                    WalletActivity.this, R.layout.list_item_transaction, hiveTransactionsList);
                            hiveTransactionsView.setAdapter(hiveTransactionsAdapter);
                        } else {
                            hiveTransactionsAdapter.notifyDataSetChanged();
                        }

                        // Scroll listener will show Load More when user reaches the bottom
                        hasMoreHiveHistory = hasMoreHistory;

                    } catch (Exception e) {
                        e.printStackTrace();
                        hiveTransactionsError.setText(R.string.hive_transactions_error);
                        hiveTransactionsError.setVisibility(View.VISIBLE);
                    }
                },
                error -> {
                    if (isRefresh) btnRefreshHiveTransactions.clearAnimation();
                    hiveTransactionsError.setText(R.string.hive_transactions_error);
                    hiveTransactionsError.setVisibility(View.VISIBLE);
                    hasMoreHiveHistory = !hiveTransactionsList.isEmpty();
                });

        queue.add(request);
    }

    private double parseHiveAmount(String amount) {
        try { return Double.parseDouble(amount.split(" ")[0]); } catch (Exception e) { return 0.0; }
    }

    private String extractHiveSymbol(String amount) {
        try {
            String[] parts = amount.split(" ");
            return parts.length > 1 ? parts[1] : "HIVE";
        } catch (Exception e) { return "HIVE"; }
    }

}
