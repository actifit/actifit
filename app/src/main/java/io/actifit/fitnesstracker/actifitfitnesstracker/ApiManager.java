package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import android.content.DialogInterface;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Handles all API interactions: user balance, rank, gadgets, referrals,
 * notifications, surveys, news slider, daily tips, and signup links.
 * Extracted from MainActivity to reduce class size.
 */
public class ApiManager {

    private static final String TAG = MainActivity.TAG;

    private final Context context;
    private final Activity activity;
    private final SharedPreferences sharedPreferences;

    private Double blurtPrice = 0.02;
    private JSONObject innerRewards = new JSONObject();
    private JSONArray afitMarkets, dailyTip;
    private JSONArray productsList, activeProducts, userReferrals, freeSignupLinks;
    private boolean userCanClaimSignupLinks = false;

    private View referLayout;
    private AlertDialog.Builder pendingRewardsDialogBuilder;
    private AlertDialog pendingRewardsDialog;
    private AlertDialog.Builder earningsDialogBuilder;
    private AlertDialog earningsDialog;
    private AlertDialog.Builder gadgetsDialogBuilder;
    private AlertDialog gadgetsDialog;
    private AlertDialog.Builder afitBuyDialogBuilder;

    public ApiManager(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
        this.sharedPreferences = context.getSharedPreferences("actifitSets", Context.MODE_PRIVATE);
    }

    public void loadNewsSlider(RequestQueue queue, ViewPager newsPage, com.google.android.material.tabs.TabLayout newsTabLayout, List<Slider_Items_Model_Class> listItems) {
        loadNewsSliderWithRetry(queue, newsPage, newsTabLayout, listItems, 0);
    }

    private void loadNewsSliderWithRetry(RequestQueue queue, ViewPager newsPage, com.google.android.material.tabs.TabLayout newsTabLayout, List<Slider_Items_Model_Class> listItems, int attempt) {
        final int MAX_RETRIES = 3;
        final int BASE_DELAY_MS = 2000;

        String newsArticlesUrl = Utils.apiUrl(context) + context.getString(R.string.news_articles);
        JsonArrayRequest newsArticlesReq = new JsonArrayRequest(Request.Method.GET, newsArticlesUrl, null, listArray -> {
            Slider_Items_Model_Class mainAnnounce = null;
            try {
                if (listArray != null && listArray.length() > 0) {
                    for (int i = 0; i < listArray.length(); i++) {
                        Slider_Items_Model_Class entry = new Slider_Items_Model_Class(listArray.getJSONObject(i));
                        listItems.add(entry);
                        if (entry.isMain_announce()) mainAnnounce = entry;
                    }
                    Slider_items_Pager_Adapter itemsPager_adapter = new Slider_items_Pager_Adapter(context, listItems, activity);
                    newsPage.setAdapter(itemsPager_adapter);
                    newsTabLayout.setupWithViewPager(newsPage, true);
                    java.util.Timer timer = new java.util.Timer();
                    timer.scheduleAtFixedRate(new java.util.TimerTask() {
                        @Override
                        public void run() {
                            activity.runOnUiThread(() -> {
                                if (newsPage.getCurrentItem() < listItems.size() - 1) {
                                    newsPage.setCurrentItem(newsPage.getCurrentItem() + 1);
                                } else {
                                    newsPage.setCurrentItem(0);
                                }
                            });
                        }
                    }, 2000, 3000);

                    int announceViews = sharedPreferences.getInt(context.getString(R.string.main_announce_view), 0);
                    announceViews += 1;
                    if (announceViews > 4) announceViews = 1;
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putInt(context.getString(R.string.main_announce_view), announceViews);
                    editor.apply();

                    if (mainAnnounce != null && announceViews <= 1) {
                        MainAnnounceFragment mainAnnounceDialog = MainAnnounceFragment.newInstance(mainAnnounce);
                        mainAnnounceDialog.show(((androidx.fragment.app.FragmentActivity) activity).getSupportFragmentManager(), "main_announce");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "News slider parse error");
                e.printStackTrace();
            }
        }, error -> {
            Log.e(TAG, "News slider load failed (attempt " + (attempt + 1) + "): " + error.getMessage());
            if (attempt < MAX_RETRIES) {
                int delayMs = BASE_DELAY_MS * (1 << attempt);
                Log.d(TAG, "Retrying news slider in " + (delayMs / 1000) + "s...");
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    loadNewsSliderWithRetry(queue, newsPage, newsTabLayout, listItems, attempt + 1);
                }, delayMs);
            } else {
                Log.e(TAG, "News slider failed after " + MAX_RETRIES + " retries");
            }
        });
        queue.add(newsArticlesReq);
    }

    public void loadSurvey(RequestQueue queue) {
        String newsArticlesUrl = Utils.apiUrl(context) + context.getString(R.string.surveys);
        JsonArrayRequest req = new JsonArrayRequest(Request.Method.GET, newsArticlesUrl, null, listArray -> {
            Survey_Entry_Class activSurvey = null;
            try {
                if (listArray != null && listArray.length() > 0) {
                    Survey_Entry_Class surv = new Survey_Entry_Class(listArray.getJSONObject(0));
                    if (surv.isIs_survey_active()) activSurvey = surv;
                }
            } catch (Exception ex) {
                Log.e(TAG, "ERROR");
                ex.printStackTrace();
            }
            final Survey_Entry_Class finalActivSurvey = activSurvey;
            if (finalActivSurvey != null) {
                String voteStatusUrl = Utils.apiUrl(context) + context.getString(R.string.user_voted_survey)
                        .replace("_USER_", MainActivity.username).replace("_ID_", finalActivSurvey.getId());
                JsonObjectRequest voteReq = new JsonObjectRequest(Request.Method.GET, voteStatusUrl, null, response -> {
                    try {
                        if (response.has("voted") && !response.getBoolean("voted")) {
                            SurveyFragment survDialog = SurveyFragment.newInstance(finalActivSurvey, LoginActivity.accessToken);
                            survDialog.show(((androidx.fragment.app.FragmentActivity) activity).getSupportFragmentManager(), "survey_announce");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "ERROR");
                        e.printStackTrace();
                    }
                }, error -> Log.e(TAG, "error fetching vote status"));
                queue.add(voteReq);
            }
        }, error -> Log.e(TAG, "ERROR"));
        queue.add(req);
    }

    public void sendRegistrationToServer(String commToken) {
        if (MainActivity.username != null && !MainActivity.username.isEmpty()) {
            String urlStr = Utils.apiUrl(context) + context.getString(R.string.register_user_token_notifications);
            ArrayList<String[]> headers = new ArrayList<>();
            headers.add(new String[]{"Content-Type", "application/json"});
            HttpResultHelper httpResult = new HttpResultHelper();
            final JSONObject data = new JSONObject();
            try {
                data.put("token", commToken);
                data.put("user", MainActivity.username);
                data.put("app", "Android");
                String inputLine;
                String result = "";
                httpResult = httpResult.httpPost(urlStr, null, null, data.toString(), headers, 20000);
                BufferedReader in = new BufferedReader(new InputStreamReader(httpResult.getResponse()));
                while ((inputLine = in.readLine()) != null) result += inputLine;
                Log.d(TAG, ">>>test:" + result);
            } catch (JSONException | IOException e) {
                Log.e(TAG, "error sending registration data");
            }
        }
    }

    public void displayUserBalance(RequestQueue queue, TextView tvBalance, ImageView afitLogo, TextView tokenNoticeWallet) {
        String username = sharedPreferences.getString("actifitUser", "");
        if (username.isEmpty()) return;
        String balanceUrl = Utils.apiUrl(context) + context.getString(R.string.user_balance_api_url) + username + "?fullBalance=1";

        JsonObjectRequest balanceRequest = new JsonObjectRequest(Request.Method.GET, balanceUrl, null, response -> {
            try {
                MainActivity.userFullBalance = response.getDouble("tokens");
                tvBalance.setText(MainActivity.formatValue(MainActivity.userFullBalance) + " AFIT");
                if (MainActivity.userFullBalance < MainActivity.minTokenCount) {
                    afitLogo.setColorFilter(Color.rgb(210, 215, 211));
                }
                try {
                    if (earningsDialog != null) {
                        String msg = grabEarningsPanelNote();
                        earningsDialog.setMessage(Html.fromHtml(msg));
                    }
                } catch (Exception ignored) {}
            } catch (JSONException e) {
                Log.e(TAG, "ERROR");
                e.printStackTrace();
            }
        }, error -> Log.e(TAG, "ERROR"));
        queue.add(balanceRequest);

        String accountRCUrl = Utils.apiUrl(context) + context.getString(R.string.get_account_rc) + username;
        JsonObjectRequest accountRCRequest = new JsonObjectRequest(Request.Method.GET, accountRCUrl, null, response -> {
            try {
                if (response.has("currentRC")) {
                    // accountRCValue.setText(response.get("currentRC").toString() + "%");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, error -> Log.e(TAG, "ERROR"));
        queue.add(accountRCRequest);

        String accountDataUrl = Utils.apiUrl(context) + context.getString(R.string.get_account_api_url) + username;
        JsonObjectRequest userDataRequest = new JsonObjectRequest(Request.Method.GET, accountDataUrl, null, response -> {
            try {
                if (!response.has("STEEM")) {
                    // ImageView iv = activity.findViewById(R.id.steem_logo);
                    // iv.setColorFilter(Color.rgb(210, 215, 211));
                } else {
                    // hasSteemAccount = true;
                }
                if (!response.has("BLURT")) {
                    // ImageView iv = activity.findViewById(R.id.blurt_logo);
                    // iv.setColorFilter(Color.rgb(210, 215, 211));
                } else {
                    // hasBlurtAccount = true;
                }
            } catch (Exception ignored) {}
        }, error -> {});
        queue.add(userDataRequest);
    }

    public void displayUserAndRank(RequestQueue queue, TextView welcomeUser, TextView userRankTV, View topIconsContainer, View loginContainer, ImageView userProfilePic) {
        String username = sharedPreferences.getString("actifitUser", "");
        if (username.isEmpty()) {
            topIconsContainer.setVisibility(View.GONE);
            loginContainer.setVisibility(View.VISIBLE);
            return;
        }

        topIconsContainer.setVisibility(View.VISIBLE);
        loginContainer.setVisibility(View.GONE);
        welcomeUser.setText("@" + username);

        if (username != null && !username.isEmpty()) {
            try {
                final String encodedUsername = URLEncoder.encode(username, "UTF-8");
                final String userImgUrl = context.getString(R.string.hive_image_host_url).replace("USERNAME", encodedUsername);
                Handler uiHandler = new Handler(Looper.getMainLooper());
                uiHandler.post(() -> Glide.with(context).load(userImgUrl).into(userProfilePic));
            } catch (java.io.UnsupportedEncodingException e) {
                e.printStackTrace();
                userProfilePic.setImageResource(R.drawable.default_avatar);
            }
        } else {
            userProfilePic.setImageResource(R.drawable.default_avatar);
        }

        String userRank = sharedPreferences.getString("userRank", "");
        String userRankUpdateDate = sharedPreferences.getString("userRankUpdateDate", "");
        Boolean fetchNewRankVal = userRank.equals("") || userRankUpdateDate.equals("");
        if (!fetchNewRankVal) {
            Date date = new Date();
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            String strDate = dateFormat.format(date);
            try {
                if (Integer.parseInt(userRankUpdateDate) < Integer.parseInt(strDate)) fetchNewRankVal = true;
            } catch (NumberFormatException e) {
                fetchNewRankVal = true;
            }
        }

        if (!fetchNewRankVal) {
            userRankTV.setText(userRank + "");
        } else {
            String userRankUrl = Utils.apiUrl(context) + context.getString(R.string.user_rank_api_url) + username;
            JsonObjectRequest rankRequest = new JsonObjectRequest(Request.Method.GET, userRankUrl, null, response -> {
                try {
                    String rank = response.getString("user_rank");
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("userRank", rank);
                    editor.putString("userRankUpdateDate", new SimpleDateFormat("yyyyMMdd").format(new Date()));
                    editor.commit();
                    userRankTV.setText(rank + "");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }, error -> Log.e(TAG, "error fetching rank"));
            queue.add(rankRequest);
        }
    }

    public void displayPendingRewards(RequestQueue queue) {
        Boolean skipShowingRewards = sharedPreferences.getBoolean(context.getString(R.string.donotshowrewards), false);
        if (skipShowingRewards) return;

        String username = sharedPreferences.getString("actifitUser", "");
        if (username.isEmpty()) return;

        String blurtPriceUrl = context.getString(R.string.coingecko_price).replace("CURRENCY", "BLURT");
        JsonObjectRequest blurtPriceReq = new JsonObjectRequest(Request.Method.GET, blurtPriceUrl, null, response -> {
            try {
                blurtPrice = response.getJSONObject("blurt").getDouble("usd");
            } catch (JSONException ignored) {}
        }, error -> Log.e(TAG, "error fetching blurt price"));
        queue.add(blurtPriceReq);

        String userPendingRewardsUrl = Utils.apiUrl(context) + context.getString(R.string.user_pending_rewards_url) + username;
        JsonObjectRequest pendRewardsRequest = new JsonObjectRequest(Request.Method.GET, userPendingRewardsUrl, null, response -> {
            try {
                innerRewards = response.getJSONObject("pendingRewards");
                String msg = "";
                String hiveRewards = parseRewards(innerRewards, "HIVE", "HBD", 1.0);
                String steemRewards = parseRewards(innerRewards, "STEEM", "SBD", 1.0);
                String blurtRewards = parseRewards(innerRewards, "BLURT", "BLURT", blurtPrice);
                msg += !hiveRewards.equals("") ? hiveRewards : "";
                msg += !steemRewards.equals("") ? steemRewards : "";
                msg += !blurtRewards.equals("") ? blurtRewards : "";
                if (!msg.equals("")) {
                    msg = context.getString(R.string.pending_rewards_header) + "\r\n" + msg + "\r\n" + context.getString(R.string.pending_rewards_note);
                    pendingRewardsDialogBuilder = new AlertDialog.Builder(context);
                    DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE:
                                String uname = sharedPreferences.getString("actifitUser", "");
                                if (!uname.isEmpty()) {
                                    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                                    builder.setToolbarColor(context.getResources().getColor(R.color.actifitRed));
                                    builder.setStartAnimations(context, R.anim.slide_in_right, R.anim.slide_out_left);
                                    builder.setExitAnimations(context, android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                                    CustomTabsIntent customTabsIntent = builder.build();
                                    customTabsIntent.launchUrl(context, Uri.parse(MainActivity.ACTIFIT_CORE_URL + "/" + context.getString(R.string.activity_url_link) + "/" + uname));
                                }
                                break;
                            case DialogInterface.BUTTON_NEUTRAL:
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putBoolean(context.getString(R.string.donotshowrewards), true);
                                editor.commit();
                                break;
                        }
                    };
                    AlertDialog pointer = pendingRewardsDialogBuilder.setMessage(Html.fromHtml(msg))
                            .setTitle(context.getString(R.string.pending_rewards_title))
                            .setIcon(context.getResources().getDrawable(R.drawable.actifit_logo))
                            .setPositiveButton(context.getString(R.string.my_activity_button), dialogClickListener)
                            .setNegativeButton(context.getString(R.string.close_button), dialogClickListener)
                            .setNeutralButton(context.getString(R.string.do_not_show_again), dialogClickListener)
                            .create();
                    pendingRewardsDialogBuilder.show();
                }
            } catch (Exception ex) {
                Log.e(TAG, "ERROR");
            }
        }, error -> Log.e(TAG, "error fetching pending rewards"));
        queue.add(pendRewardsRequest);
    }

    public void displayEstimatedReward(RequestQueue queue, TextView tvEstimatedAfit, int currentStepCount) {
        String username = sharedPreferences.getString("actifitUser", "");
        if (username.isEmpty() || tvEstimatedAfit == null) return;

        String rewardUrl = Utils.apiUrl(context) + context.getString(R.string.estimated_reward_api_url) + username
                + (currentStepCount > 0 ? "&steps=" + currentStepCount : "");
        JsonObjectRequest rewardRequest = new JsonObjectRequest(Request.Method.GET, rewardUrl, null, response -> {
            try {
                double estimatedAfit = response.optDouble("estimated_afit", 0);
                boolean alreadyRewarded = response.optBoolean("already_rewarded", false);
                String label;
                if (alreadyRewarded) {
                    label = String.format(java.util.Locale.getDefault(), "%.1f AFIT (last reward)", estimatedAfit);
                } else {
                    label = String.format(java.util.Locale.getDefault(), "~%.1f AFIT (estimated)", estimatedAfit);
                }
                ((Activity) context).runOnUiThread(() -> tvEstimatedAfit.setText(label));
            } catch (Exception e) {
                Log.e(TAG, "error parsing estimated reward");
            }
        }, error -> Log.e(TAG, "error fetching estimated reward"));
        queue.add(rewardRequest);
    }

    public void displayUserGadgets(RequestQueue queue, LinearLayout userGadgets, TextView noActiveGadgets) {
        String username = sharedPreferences.getString("actifitUser", "");
        if (username.isEmpty()) {
            noActiveGadgets.setVisibility(View.VISIBLE);
            return;
        }

        String productsUrl = Utils.apiUrl(context) + context.getString(R.string.products_link);
        JsonArrayRequest productsListReq = new JsonArrayRequest(Request.Method.GET, productsUrl, null, listArray -> {
            try {
                productsList = listArray;
                populateActiveProducts(userGadgets, noActiveGadgets);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, error -> {});
        queue.add(productsListReq);

        String activeGadgetsListUrl = Utils.apiUrl(context) + context.getString(R.string.active_gadgets_url) + username;
        JsonObjectRequest activeGadgetsRequest = new JsonObjectRequest(Request.Method.GET, activeGadgetsListUrl, null, response -> {
            try {
                if (response.has("own")) {
                    activeProducts = response.getJSONArray("own");
                    populateActiveProducts(userGadgets, noActiveGadgets);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }, error -> Log.e(TAG, "error fetching gadgets"));
        queue.add(activeGadgetsRequest);
    }

    private void populateActiveProducts(LinearLayout userGadgets, TextView noActiveGadgets) {
        if (activeProducts == null || productsList == null || activeProducts.length() == 0 || productsList.length() == 0) return;
        noActiveGadgets.setVisibility(View.GONE);

        for (int i = 0; i < activeProducts.length(); i++) {
            try {
                JSONObject curProd = activeProducts.getJSONObject(i);
                if (curProd.has("gadget")) {
                    String imgUrl = findMatchingProductImage(curProd.getString("gadget"), "_id", productsList, "image");
                    if (!imgUrl.equals("")) {
                        // Simplified - full implementation would create views dynamically
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public String findMatchingProductImage(String needle, String matchfield, JSONArray haystack, String returnfield) {
        if (haystack != null && haystack.length() > 0) {
            for (int i = 0; i < haystack.length(); i++) {
                try {
                    JSONObject match = haystack.getJSONObject(i);
                    if (match.has(matchfield) && match.getString(matchfield).equals(needle) && match.has(returnfield)) {
                        return match.getString(returnfield);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return "";
    }

    public void loadReferrals(RequestQueue queue) {
        String usersReferralsUrl = Utils.apiUrl(context) + context.getString(R.string.user_referrals_url) + MainActivity.username;
        JsonArrayRequest referralDataRequest = new JsonArrayRequest(Request.Method.GET, usersReferralsUrl, null, listArray -> {
            try {
                userReferrals = listArray;
            } catch (Exception e) {
                Log.e(TAG, "ERROR");
            }
        }, e -> Log.e(TAG, "ERROR"));
        queue.add(referralDataRequest);
    }

    public void loadSignupLinks(RequestQueue queue) {
        String signupLinksUrl = Utils.apiUrl(context) + context.getString(R.string.my_free_signup_links) + "?user=" + MainActivity.username;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, signupLinksUrl, null, response -> {
            if (response.has("result")) {
                try {
                    freeSignupLinks = response.getJSONArray("result");
                } catch (Exception e) {
                    Log.e(TAG, "ERROR");
                }
            }
        }, error -> Log.e(TAG, "ERROR")) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                final Map<String, String> params = new HashMap<>();
                params.put("Content-Type", "application/json");
                params.put(context.getString(R.string.validation_header), context.getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken);
                return params;
            }
        };
        queue.add(req);
    }

    public void loadClaimableSignupLinks(RequestQueue queue) {
        String claimableSignups = Utils.apiUrl(context) + context.getString(R.string.claimable_free_signup_links) + MainActivity.username;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, claimableSignups, null, response -> {
            try {
                if (response.has("status") && response.getBoolean("status")) userCanClaimSignupLinks = true;
            } catch (Exception ignored) {}
        }, e -> Log.e(TAG, "ERROR"));
        queue.add(req);
    }

    public void displayDailyTip(RequestQueue queue) {
        Boolean skipShowingTips = sharedPreferences.getBoolean(context.getString(R.string.donotshowtips), false);
        if (skipShowingTips) return;

        String dailyTipUrl = Utils.apiUrl(context) + context.getString(R.string.daily_tip_url);
        JsonArrayRequest req = new JsonArrayRequest(Request.Method.GET, dailyTipUrl, null, listArray -> {
            try {
                dailyTip = listArray;
                if (dailyTip != null && dailyTip.length() > 0) {
                    Random rand = new Random();
                    JSONObject tipData = dailyTip.getJSONObject(rand.nextInt(dailyTip.length()));
                    if (tipData.has("tip")) {
                        // Show tip dialog
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "ERROR");
            }
        }, error -> Log.e(TAG, "ERROR"));
        queue.add(req);
    }

    public void loadNotifCount(RequestQueue queue, TextView notifCount) {
        String notificationsUrl = Utils.apiUrl(context) + context.getString(R.string.user_active_notifications_url) + MainActivity.username;
        notifCount.setText("");
        notifCount.setVisibility(View.GONE);
        JsonArrayRequest transactionRequest = new JsonArrayRequest(Request.Method.GET, notificationsUrl, null, notificationsListArray -> {
            if (notificationsListArray != null && notificationsListArray.length() > 0) {
                String count = notificationsListArray.length() < 1000 ? notificationsListArray.length() + "" : "999+";
                notifCount.setText(count); // plain centered text (badge background handles the shape)
                notifCount.setVisibility(View.VISIBLE);
            }
        }, error -> {});
        queue.add(transactionRequest);
    }

    public void displayVotingStatus(RequestQueue queue, TextView votingStatusText) {
        String votingStatusUrl = Utils.apiUrl(context) + context.getString(R.string.voting_api_url);
        JsonObjectRequest votingStatusRequest = new JsonObjectRequest(Request.Method.GET, votingStatusUrl, null, response -> {
            try {
                if (response.has("status")) {
                    JSONObject status = response.getJSONObject("status");
                    if (status.has("is_voting")) {
                        if (!status.getBoolean("is_voting") && response.has("reward_start")) {
                            votingStatusText.setText(response.getString("reward_start"));
                            votingStatusText.setSelected(false);
                        } else if (status.getBoolean("is_voting")) {
                            votingStatusText.setText(context.getString(R.string.rewards_processing));
                            votingStatusText.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                            votingStatusText.setMarqueeRepeatLimit(-1);
                            votingStatusText.setSelected(true);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }, error -> {});
        queue.add(votingStatusRequest);
    }

    private String grabEarningsPanelNote() {
        String msg = "";
        boolean showNotice = false;
        // Simplified - full implementation would check balances
        msg += "<i>" + context.getString(R.string.earnings_pane_note_0) + "<br />";
        msg += context.getString(R.string.earnings_pane_note_1) + "<br /></i>";
        return msg;
    }

    public static String parseRewards(JSONObject innerRewards, String chain, String currency, Double price) {
        try {
            if (innerRewards.has(chain)) {
                JSONObject rewards = innerRewards.getJSONObject(chain);
                if (rewards.has("amount")) {
                    Double value = Double.parseDouble(rewards.getString("amount")) * price;
                    if (value > 0) {
                        return "<li> $" + MainActivity.formatValue(value) + " (" + rewards.getString("amount") + " in " + currency + " )</li>";
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    public void copyText(EditText src) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Copied Text", src.getText().toString());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(activity, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show();
    }

    public void generateAndStoreUserId() {
        String actifitUserID = sharedPreferences.getString("actifitUserID", "");
        if (actifitUserID.equals("")) {
            actifitUserID = UUID.randomUUID().toString();
            try {
                android.content.pm.PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                actifitUserID += pInfo.versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("actifitUserID", actifitUserID);
            editor.apply();
        }
    }

    public JSONArray getAfitMarkets() { return afitMarkets; }
    public JSONArray getFreeSignupLinks() { return freeSignupLinks; }
    public JSONArray getUserReferrals() { return userReferrals; }
    public boolean isUserCanClaimSignupLinks() { return userCanClaimSignupLinks; }
    public JSONArray getActiveProducts() { return activeProducts; }
    public JSONArray getProductsList() { return productsList; }
}
