/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.actifit.fitnesstracker.actifitfitnesstracker;

import static android.view.View.GONE;
import static java.lang.Integer.parseInt;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.Manifest;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.health.connect.client.permission.HealthPermission;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;
import androidx.annotation.Nullable;


import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.load.DataSource;
import android.graphics.drawable.Drawable;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.github.mikephil.charting.utils.ViewPortHandler;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.tabs.TabLayout;
import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessaging;
import com.scottyab.rootbeer.RootBeer;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.URLEncoder;
import java.net.CookieManager;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import de.hdodenhof.circleimageview.CircleImageView;
import kotlin.Unit;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.jvm.internal.ClassReference;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;

import androidx.health.connect.client.records.HeartRateRecord;
import androidx.health.connect.client.records.StepsRecord;

/**
 * Implementation of this project was made possible via re-use, adoption and improvement of
 * following tutorials and resources:
 * - http://file.allitebooks.com/20170511/Android%20Sensor%20Programming%20By%20Example.pdf.
 * - google's simple-pedometer github work licensed under Apache License
 * https://github.com/google/simple-pedometer (I initially found it under
 * http://gadgetsaint.com/android/create-pedometer-step-counter-android/ who seems to have
 * copied it without any reference to original source/work by google)
 * - https://notes.iopush.net/android-send-a-https-post-request/
 * - additional help and code has been utilized from
 * https://fabcirablog.weebly.com/blog/creating-a-never-ending-background-service-in-android
 * to help with services, but also relying on official Android documentation
 */

/**
 * attributions:
 * success alert image: <a href="https://www.freeiconspng.com/img/23186">Success
 * Hd Icon</a>
 * error alert image: <a href="https://www.freeiconspng.com/img/25248">sign
 * error icon</a>
 */
public class MainActivity extends BaseActivity {
    private CircleImageView userProfilePic;
    public static SensorManager sensorManager;
    public static String username = "";
    public static String commToken;
    // private TextView stepDisplay;
    private RelativeLayout thirdPartyTracking;
    private LinearLayout gadgetsll;
    private RelativeLayout healthConnectTracking;

    // tracks a reference to an instance of this class
    public static SensorEventListener mainActivitySensorList;

    public static final String TAG = "Actifit";

    private Double blurtPrice = 0.02;

    public static int connectTimeout = 10000;
    public static int connectMaxRetries = 3;

    public static int connectSubsequentRetryDelay = 2; // backoffmultiplier

    private final AtomicBoolean isMobileAdsInitializeCalled = new AtomicBoolean(false);

    private static final String PREF_KEY_DARK_MODE = "theme_mode";

    private AlertDialog.Builder pendingRewardsDialogBuilder;
    private AlertDialog pendingRewardsDialog;
    private JSONObject innerRewards = new JSONObject();

    private AlertDialog.Builder earningsDialogBuilder;
    private AlertDialog earningsDialog;

    private AlertDialog.Builder gadgetsDialogBuilder;
    private AlertDialog gadgetsDialog;

    private AlertDialog.Builder afitBuyDialogBuilder;
    private AlertDialog afitBuyDialog;
    JSONArray afitMarkets, dailyTip;

    TextView giftLoader;

    View referLayout;

    // tracks if listener is active
    public static boolean isListenerActive = false;

    private StepsDBHelper mStepsDBHelper;

    private RewardManager rewardManager;
    private ChartManager chartManager;
    private TrackingManager trackingManager;
    private ApiManager apiManager;
    private SecurityManager securityManager;
    private UiHelper uiHelper;

    // to utilize built-in step sensors
    private Sensor stepSensor;

    public static boolean isStepSensorPresent = false;
    public static String ACCEL_SENSOR = "ACCEL_SENSOR";
    public static String STEP_SENSOR = "STEP_SENSOR";
    public static String ACTIFIT_CORE_URL = "https://actifit.io";
    public static String ACTIFIT_RANK_URL = "https://actifit.io/userrank";

    public static final String[] tutVidUrl = { "" };

    // enforcing active sensor by default as ACC
    public static String activeSensor = MainActivity.ACCEL_SENSOR;

    /* items related to batch data capturing */

    private int currentDisplayedStepCount = 0;
    private static final String BUNDLE_LISTENER = "listener";

    private static Intent mServiceIntent;
    private static ActivityMonitorService mSensorService;
    private Context ctx;

    private BroadcastReceiver receiver;

    // flag if service is bound now
    boolean mBound = false;

    public Context getCtx() {
        return ctx;
    }

    static final int REQUEST_TAKE_PHOTO = 1;

    String checkMark = "&#10003;";

    View defaultChartContainer;

    static boolean isActivityVisible = true;

    private BarData chartBarData, dayBarData;

    private BarChart dayChart, fullChart;

    public static Double userFullBalance = 0.0;
    public static String userRank = "0.0";// default 0
    public boolean hasSteemAccount = false;
    public boolean hasBlurtAccount = false;
    public Double blurtBalance = 0.0;

    public static Double minTokenCount;
    TextView loginLink, logoutLink, signupLink, accountRCValue, votingStatusText, newbieLink,
            notifCount;
    LinearLayout loginContainer, userGadgets, accountRCContainer, votingStatusContainer;
    GridLayout topIconsContainer;
    TextView BtnSettings;

    public static JSONObject userSettings;
    JSONArray freeSignupLinks;

    JSONArray productsList;
    JSONArray activeProducts;
    JSONArray userReferrals;
    boolean userCanClaimSignupLinks = false;

    final int activityMilestoneOne = 5000;
    final int activityMilestoneTwo = 7000;
    final int activityMilestoneThree = 10000;
    private int lastCelebrationMilestone = 0;

    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    private RewardedAd rewardedAd;
    private Button dailyRewardButton;
    private Button freeRewardButton, fivekRewardButton, sevenkRewardButton, tenkRewardButton;
    // ,moveTotweets;
    private Button BtnWaves;
    private boolean dailyRewardClaimed = false, fivekRewardClaimed = false, sevenkRewardClaimed = false,
            tenkRewardClaimed = false;

    // Reward Status TextViews (New references)
    private TextView textViewFreeRewardStatus;
    private TextView textView5kRewardStatus;
    private TextView textView7kRewardStatus;
    private TextView textView10kRewardStatus;

    // Current Steps TextView (New reference)
    private TextView textViewCurrentSteps;

    private boolean isAdLoading;

    Button fullChartButton, dayChartButton;
    LinearLayout chartSwitcher;
    RotateAnimation rotate;

    ScaleAnimation scaler;
    ValueAnimator valueAnimator;

    ExtendedFloatingActionButton BtnPostSteemit;

    private ConsentInformation consentInformation;
    private ConsentForm consentForm;

    /* news tab related variables */

    private List<Slider_Items_Model_Class> listItems;
    private ViewPager newsPage;
    private TabLayout newsTabLayout;

    // required function to ask for proper read/write permissions on later Android
    // versions
    protected boolean shouldAskPermissions() {
        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1);
    }

    public static ActivityMonitorService getmSensorService() {
        return mSensorService;
    }

    public static void setmSensorService(ActivityMonitorService sensorService) {
        mSensorService = sensorService;
    }

    public static Intent getmServiceIntent() {
        return mServiceIntent;
    }

    public static void setmServiceIntent(Intent serviceIntent) {
        mServiceIntent = serviceIntent;
    }

    /**
     * function checks if the sensor service is running or not
     *
     * @param serviceClass
     * @return
     */
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        return securityManager.isServiceRunning(serviceClass);
    }

    String mCurrentPhotoPath;

    // handles creating the snapped image file
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        // File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        // File storageDir =
        // getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName, /* prefix */
                ".jpg", /* suffix */
                getApplicationContext().getFilesDir()
                // storageDir /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    // security function to detect emulators
    public static boolean isEmulator() {
        return Build.FINGERPRINT.contains("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic")
                && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT)
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.HARDWARE.contains("andy");
    }

    private static final int VALID = 0;

    private static final int INVALID = 1;

    public int checkAppSignature(Context context) {
        return securityManager.checkAppSignature() ? 0 : 1;
    }

    // function handles killing the app
    private void killActifit(final String reason) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // display notification to user
                Toast toast = Toast.makeText(getCtx(), reason,
                        Toast.LENGTH_LONG);

                /*
                 * View view = toast.getView();
                 *
                 * TextView text = view.findViewById(android.R.id.message);
                 *
                 * try {
                 * //Gets the actual oval background of the Toast then sets the colour filter
                 * view.getBackground().setColorFilter(getResources().getColor(R.color.
                 * actifitRed), PorterDuff.Mode.SRC_IN);
                 * }catch(Exception e){
                 * e.printStackTrace();
                 * }
                 *
                 * text.setTextColor(Color.WHITE);
                 */

                toast.show();
                finish();
                /*
                 * System.exit(0);
                 * //kill gracefully after waiting for toast
                 * new Handler().postDelayed(new Runnable() {
                 *
                 * @Override
                 * public void run() {
                 *
                 *
                 *
                 * }
                 * }, 3000);
                 */

                // ((MainActivity)getCtx()).finish();
            }
        });
    }

    @TargetApi(23)
    protected void askPermissions(String[] permissions) {
        int requestCode = 200;
        requestPermissions(permissions, requestCode);
    }

    // function handles checking if the SIM card is available
    public boolean isSimAvailable() {
        return securityManager.isSimAvailable();
    }

    /*
     * public void crashMe(View v) {
     * //throw new NullPointerException();
     * //killActifit(getString(R.string.no_valid_sim));
     * Crashlytics.getInstance().crash();
     *
     * //new syntax:
     * FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
     * crashlytics.someAction();
     * }
     */

    // slide the view from below itself to the current position
    public void slideRight(View view) {
        uiHelper.slideRight(view);
    }

    public void slideLeft(View view) {
        uiHelper.slideLeft(view);
    }

    /* handles auto-revolving news tab at the top */
    public class Slide_timer extends TimerTask {
        @Override
        public void run() {

            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (newsPage.getCurrentItem() < listItems.size() - 1) {
                        newsPage.setCurrentItem(newsPage.getCurrentItem() + 1);
                    } else
                        newsPage.setCurrentItem(0);
                }
            });
        }
    }

    // handles displaying any available surveys
    private void loadSurvey(RequestQueue queue) {
        String newsArticlesUrl = Utils.apiUrl(this) + getString(R.string.surveys);

        // also set and popup any mainAnnounce news

        JsonArrayRequest req = new JsonArrayRequest(Request.Method.GET,
                newsArticlesUrl, null, listArray -> {
            Survey_Entry_Class activSurvey = null;
            try {
                if (listArray != null && listArray.length() > 0) {
                    // grab first
                    Survey_Entry_Class surv = new Survey_Entry_Class(listArray.getJSONObject(0));
                    if (surv.isIs_survey_active()) {
                        activSurvey = surv;
                    }
                }
            } catch (Exception ex) {
                // Log.e(TAG, Objects.requireNonNull(ex.getMessage()));
                Log.e(TAG, "ERROR");
                ex.printStackTrace();
            }

            if (activSurvey != null) {

                // check if user voted on survey already, if not show it
                Survey_Entry_Class finalActivSurvey = activSurvey;
                String voteStatusUrl = Utils.apiUrl(this)
                        + getString(R.string.user_voted_survey).replace("_USER_", MainActivity.username)
                        .replace("_ID_", activSurvey.getId());
                JsonObjectRequest voteReq = new JsonObjectRequest(Request.Method.GET, voteStatusUrl, null,
                        new Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {
                                try {
                                    if (response.has("voted") && !response.getBoolean("voted")) {

                                        // show mainAnnounce if there exists one
                                        SurveyFragment survDialog = SurveyFragment.newInstance(finalActivSurvey,
                                                LoginActivity.accessToken);
                                        survDialog.show(getSupportFragmentManager(), "survey_announce");
                                    }
                                } catch (Exception e) {
                                    // Log.e(TAG, Objects.requireNonNull(e.getMessage()));
                                    Log.e(TAG, "ERROR");
                                    e.printStackTrace();
                                }
                            }
                        }, error -> {
                    // hide dialog
                    // error.printStackTrace();
                    Log.e(MainActivity.TAG, "error fetching vote status");
                    error.printStackTrace();
                });

                // Add balance request to be processed
                queue.add(voteReq);

            }

        }, error -> {
            Log.e(MainActivity.TAG, "ERROR");
            error.printStackTrace();
        });

        queue.add(req);
    }

    // handles initiating and filling newsslider data
    // SLIDER SETUP INFO:
    // //https://www.section.io/engineering-education/how-to-create-an-automatic-slider-in-android-studio/
    private void loadNewsSlider(RequestQueue queue) {
        loadNewsSliderWithRetry(queue, 0);
    }

    private void loadNewsSliderWithRetry(RequestQueue queue, int attempt) {
        final int MAX_RETRIES = 3;
        final int BASE_DELAY_MS = 2000;

        newsPage = findViewById(R.id.news_pager);
        newsTabLayout = findViewById(R.id.news_tablayout);
        listItems = new ArrayList<>();

        String newsArticlesUrl = Utils.apiUrl(this) + getString(R.string.news_articles);

        JsonArrayRequest newsArticlesReq = new JsonArrayRequest(Request.Method.GET,
                newsArticlesUrl, null, listArray -> {
            try {
                if (listArray != null && listArray.length() > 0) {
                    Slider_Items_Model_Class mainAnnounce = null;
                    int newsLimit = Math.min(8, listArray.length());
                    for (int i = 0; i < newsLimit; i++) {
                        Slider_Items_Model_Class entry = new Slider_Items_Model_Class(
                                listArray.getJSONObject(i));
                        listItems.add(entry);
                        if (entry.isMain_announce()) {
                            mainAnnounce = entry;
                        }
                    }

                    Slider_items_Pager_Adapter itemsPager_adapter = new Slider_items_Pager_Adapter(
                            this, listItems, MainActivity.this);
                    newsPage.setAdapter(itemsPager_adapter);
                    newsTabLayout.setupWithViewPager(newsPage, true);

                    java.util.Timer timer = new java.util.Timer();
                    timer.scheduleAtFixedRate(new Slide_timer(), 2000, 5000);
                    newsTabLayout.setupWithViewPager(newsPage, true);

                    SharedPreferences sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);
                    int announceViews = (sharedPreferences.getInt(getString(R.string.main_announce_view), 0));
                    announceViews += 1;
                    if (announceViews > 4)
                        announceViews = 1;

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putInt(getString(R.string.main_announce_view), announceViews);
                    editor.apply();

                    if (mainAnnounce != null && announceViews <= 1) {
                        MainAnnounceFragment mainAnnounceDialog = MainAnnounceFragment.newInstance(mainAnnounce);
                        mainAnnounceDialog.show(getSupportFragmentManager(), "main_announce");
                    }

                    loadLatestTweetIntoCarousel(queue);
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
                    loadNewsSliderWithRetry(queue, attempt + 1);
                }, delayMs);
            } else {
                Log.e(TAG, "News slider failed after " + MAX_RETRIES + " retries");
            }
        });

        queue.add(newsArticlesReq);
    }

    private void loadLatestTweetIntoCarousel(RequestQueue queue) {
        String url = Utils.apiUrl(this) + getString(R.string.x_latest_post_url);
        com.android.volley.toolbox.JsonObjectRequest req = new com.android.volley.toolbox.JsonObjectRequest(
                com.android.volley.Request.Method.GET, url, null,
                response -> {
                    org.json.JSONArray tweetsArray = response.optJSONArray("tweets");
                    if (tweetsArray == null || tweetsArray.length() == 0) return;

                    java.util.List<Slider_Items_Model_Class> tweetSlides = new java.util.ArrayList<>();
                    int count = Math.min(2, tweetsArray.length());
                    for (int i = 0; i < count; i++) {
                        org.json.JSONObject t = tweetsArray.optJSONObject(i);
                        if (t == null) continue;
                        String tweetText = t.optString("tweetText", "");
                        String tweetUrl = t.optString("tweetUrl", "");
                        if (tweetText.isEmpty() || tweetUrl.isEmpty()) continue;
                        String timestamp = t.optString("tweetTimestamp", "");
                        String tweetImageUrl = t.optString("tweetImageUrl", "");
                        tweetSlides.add(Slider_Items_Model_Class.fromTweet(tweetText, tweetUrl, timestamp, tweetImageUrl));
                    }
                    if (tweetSlides.isEmpty()) return;

                    runOnUiThread(() -> {
                        if (listItems != null && newsPage != null && newsPage.getAdapter() != null) {
                            listItems.addAll(0, tweetSlides);
                            newsPage.getAdapter().notifyDataSetChanged();
                            newsTabLayout.setupWithViewPager(newsPage, true);
                        }
                    });
                },
                error -> Log.e(TAG, "loadLatestTweetIntoCarousel failed: " + error.getMessage()));
        queue.add(req);
    }

    private void loadCommunityFeed() {
        androidx.recyclerview.widget.RecyclerView rv = findViewById(R.id.community_feed_rv);
        if (rv == null) return;
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(
                this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));

        HiveRequests hiveReq = new HiveRequests(this);
        new Thread(() -> {
            try {
                org.json.JSONObject params = new org.json.JSONObject();
                params.put("sort", "created");
                params.put("tag", getString(R.string.actifit_community));
                params.put("start_author", "");
                params.put("start_permlink", "");
                params.put("limit", 10);
                org.json.JSONArray result = hiveReq.getRankedPosts(params);
                java.util.List<SingleHivePostModel> posts = new java.util.ArrayList<>();
                for (int i = 0; i < result.length(); i++) {
                    posts.add(new SingleHivePostModel(result.getJSONObject(i), this));
                }
                runOnUiThread(() -> {
                    rv.setAdapter(new CommunityFeedAdapter(this, posts));
                    android.widget.TextView seeAll = findViewById(R.id.btn_see_all_community);
                    if (seeAll != null) {
                        seeAll.setOnClickListener(v ->
                                startActivity(new android.content.Intent(this, SocialActivity.class)));
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "loadCommunityFeed error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Persist token to third-party servers.
     *
     * Modify this method to associate the user's FCM InstanceID token with any
     * server-side account
     * maintained by your application.
     *
     */
    private void sendRegistrationToServer() {
        if (username != null && !username.isEmpty()) {
            String urlStr = Utils.apiUrl(this) + getString(R.string.register_user_token_notifications);
            Log.d(MainActivity.TAG, "sendRegistrationToServer - urlStr:" + urlStr);
            ArrayList<String[]> headers = new ArrayList<>();
            headers.add(new String[] { "Content-Type", "application/json" });
            HttpResultHelper httpResult = new HttpResultHelper();

            final JSONObject data = new JSONObject();
            try {
                data.put("token", commToken);
                data.put("user", username);
                data.put("app", "Android");

                String inputLine;
                String result = "";
                httpResult = httpResult.httpPost(urlStr, null, null, data.toString(), headers, 20000);
                BufferedReader in = new BufferedReader(new InputStreamReader(httpResult.getResponse()));
                while ((inputLine = in.readLine()) != null) {
                    result += inputLine;
                }

                Log.d(MainActivity.TAG, ">>>test:" + result);
            } catch (JSONException | IOException e) {
                // e.printStackTrace();
                Log.e(MainActivity.TAG, "error sending registration data");
            }
        }

    }

    /**
     * WARNING: This is a synchronous, blocking call and should NOT be used on the
     * main UI thread
     * as it can cause the application to hang or trigger an "Application Not
     * Responding" (ANR) error.
     * It synchronously checks if the Health Connect SDK is available and if all
     * required permissions
     * have been granted.
     *
     * @return {@code true} if Health Connect is available and all permissions are
     *         granted, {@code false} otherwise.
     */
    private boolean isHealthConnectPermActivated() {
        return trackingManager.isHealthConnectPermActivated();
    }

    private void checkHealthConnectStatusAndPermissions() {
        trackingManager.checkHealthConnectStatusAndPermissions();
    }

    private void requestHealthConnectPermissionsUI() {
        trackingManager.requestHealthConnectPermissionsUI();
    }

    private void showInstallOrUpdateHealthConnectRationale(boolean needsUpdate) {
        trackingManager.showInstallOrUpdateHealthConnectRationale(needsUpdate);
    }

    private void checkPermissionsAndReadData() {
        trackingManager.checkPermissionsAndReadData();
    }

    /*******************************************/

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // if (DEVELOPER_MODE) {
        /*
         * StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
         * .detectDiskReads()
         * .detectDiskWrites()
         * .detectAll()//.detectNetwork() // or .detectAll() for all detectable problems
         * .penaltyLog()
         * .build());
         * StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
         * .detectLeakedSqlLiteObjects()
         * .detectLeakedClosableObjects()
         * .penaltyLog()
         * // .penaltyDeath()
         * .build());
         */
        // }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        userProfilePic = findViewById(R.id.user_profile_pic);
        Log.d(MainActivity.TAG, "[Actifit] oncreate MainActivity");

        CookieManager manager = new CookieManager();
        CookieHandler.setDefault(manager);

        // Initialize managers
        chartManager = new ChartManager(this, activityMilestoneOne, activityMilestoneTwo, activityMilestoneThree);
        chartManager.setScaler(scaler);
        chartManager.setBtnWaves(BtnWaves);
        chartManager.setBtnPostSteemit(BtnPostSteemit);

        // Initialize bar chart references for chart switching
        dayChart = findViewById(R.id.main_today_activity_chart);
        fullChart = findViewById(R.id.main_history_activity_chart);

        securityManager = new SecurityManager(this);
        uiHelper = new UiHelper(this, this);
        uiHelper.initializeAnimations();
        chartManager.setScaler(uiHelper.getScalerAnimation());

        if (mStepsDBHelper == null) {
            mStepsDBHelper = new StepsDBHelper(this);
        }
        rewardManager = new RewardManager(this, this, activityMilestoneOne, activityMilestoneTwo, activityMilestoneThree,
                checkMark, uiHelper.getScalerAnimation(), mStepsDBHelper);

        apiManager = new ApiManager(this, this);

        trackingManager = new TrackingManager(this, this);
        trackingManager.setSensorService(mSensorService);
        trackingManager.setServiceIntent(mServiceIntent);
                trackingManager.initialize(chartManager, mStepsDBHelper, findViewById(R.id.health_connect_status));
        trackingManager.startHealthConnectCheck();


        // short rotate animation
        rotate = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(2000);
        rotate.setInterpolator(new LinearInterpolator());

        logoutLink = findViewById(R.id.logout_action);
        loginLink = findViewById(R.id.login_action);
        signupLink = findViewById(R.id.signup_action);
        loginContainer = findViewById(R.id.login_container);
        accountRCValue = findViewById(R.id.account_rc);
        topIconsContainer = findViewById(R.id.top_icons_container);
        newbieLink = findViewById(R.id.verify_newbie);
        notifCount = findViewById(R.id.notif_count);

        votingStatusText = findViewById(R.id.voting_status_text);
        votingStatusContainer = findViewById(R.id.voting_status_container);

        fullChartButton = findViewById(R.id.daily_chart_btn);
        dayChartButton = findViewById(R.id.hourly_chart_btn);
        chartSwitcher = findViewById(R.id.chart_switcher);
        defaultChartContainer = findViewById(R.id.default_chart_container);

        // allow opening signup link
        signupLink.setMovementMethod(LinkMovementMethod.getInstance());
        signupLink.setPaintFlags(signupLink.getPaintFlags() & (~Paint.UNDERLINE_TEXT_FLAG));

        isActivityVisible = true;

        /*
         * if (getIntent().getExtras() != null) {
         * for (String key : getIntent().getExtras().keySet()) {
         * Object value = getIntent().getExtras().get(key);
         * Log.d("MainActivity: ", "Key: " + key + " Value: " + value);
         * }
         * }
         */

        // check if user had unsubscribed from notifications
        SharedPreferences sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);
        boolean currentNotifStatus = (sharedPreferences.getBoolean(getString(R.string.notification_status), true));

        // clear all preferences for testing purposes
        /*
         * SharedPreferences.Editor editor2 = sharedPreferences.edit();
         * editor2.clear();
         * editor2.apply();
         */

        // if not set as subscribed by default
        // FirebaseApp.initializeApp(this);
        if (currentNotifStatus) {

            FirebaseMessaging.getInstance().subscribeToTopic(getString(R.string.actif_def_not_topic));
        }
        /***** Script below for fetching new app communication token *******/
        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(@NonNull Task<InstanceIdResult> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "getInstanceId failed", task.getException());
                            return;
                        }

                        // Get new Instance ID token
                        commToken = task.getResult().getToken();

                        // Log and toast
                        // String msg = getString(R.string.msg_, token);
                        Log.d(TAG, commToken);

                        Thread thread = new Thread(() -> {
                            try {
                                // send out server notification registration with username and token
                                sendRegistrationToServer();
                            } catch (Exception e) {
                                // Log.e(TAG, Objects.requireNonNull(e.getMessage()));
                                Log.e(TAG, "ERROR");
                                e.printStackTrace();
                            }
                        });
                        thread.start();

                        // Log.d(TAG, msg);
                        // Toast.makeText(MainActivity.this, token, Toast.LENGTH_SHORT).show();
                    }
                });

        // notify user of app restart with a Toast
        // TODO: might need to remove again
        if (getIntent().getBooleanExtra("crash", false)) {
            try {
                Toast toast = Toast.makeText(this, getString(R.string.actifit_crash_restarted), Toast.LENGTH_SHORT);
                View view = toast.getView();
                if (view != null) {
                    TextView text = view.findViewById(android.R.id.message);
                    /*
                     * try {
                     * //Gets the actual oval background of the Toast then sets the colour filter
                     * view.getBackground().setColorFilter(getResources().getColor(R.color.
                     * actifitRed), );//, PorterDuff.Mode.SRC_IN);
                     * }catch(Exception e){
                     * e.printStackTrace();
                     * }
                     */
                    text.setTextColor(Color.WHITE);
                    toast.show();
                }
            } catch (Exception ex) {
                Log.e(TAG, "error displaying toast");
            }

        }

        // for language/locale management
        resetTitles();

        // enforce test crash
        // Crashlytics.getInstance().crash();

        ctx = this;

        RequestQueue queue = Volley.newRequestQueue(this);

        loadNewsSlider(queue);

        loadSurvey(queue);

        loadCommunityFeed();

        // grab pointers to specific elements/buttons to be able to capture events and
        // take action
        // stepDisplay = findViewById(R.id.step_display);
        thirdPartyTracking = findViewById(R.id.third_party_active);
        healthConnectTracking = findViewById(R.id.health_connect_active);

        View BtnLeaderboard = findViewById(R.id.btn_view_leaderboard);
        TextView BtnWallet = findViewById(R.id.btn_view_wallet);
        TextView BtnViewNotifications = findViewById(R.id.btn_view_notifications);
        LinearLayout BtnWalletAltContainer = findViewById(R.id.wallet_alt_container);

        // FontTextView BtnSnapActiPic = findViewById(R.id.btn_snap_picture);
        TextView BtnVideo = findViewById(R.id.btn_video);

        BtnSettings = findViewById(R.id.btn_settings);
        TextView BtnShareAchievement = findViewById(R.id.btn_share_achievement);
        TextView BtnShareAchievementFitbit = findViewById(R.id.btn_share_achievement_fitbit);
        TextView BtnShareAchievementHC = findViewById(R.id.btn_share_achievement_hc);
        View BtnMarket = findViewById(R.id.btn_view_market);
        View BtnPosts = findViewById(R.id.btn_view_social);
        BtnWaves = findViewById(R.id.btn_waves);
        TextView BtnSwitchSettings = findViewById(R.id.switchSettings);

        BtnPostSteemit = findViewById(R.id.btn_post_steemit);

        ScrollView mainScrollView = findViewById(R.id.main_scroll_view);
        if (mainScrollView != null) {
            mainScrollView.setOnScrollChangeListener((View.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (scrollY > oldScrollY + 8) {
                    BtnPostSteemit.shrink();
                } else if (scrollY < oldScrollY - 8 || scrollY == 0) {
                    BtnPostSteemit.extend();
                }
            });
        }

        Button BtnBuyAFIT = findViewById(R.id.btn_buy_afit);
        Button BtnReferFriend = findViewById(R.id.refer_friend_button);

        scaler = new ScaleAnimation(1f, 0.95f, 1f, 0.95f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                0.5f);
        scaler.setDuration(400);
        scaler.setRepeatMode(Animation.REVERSE);
        scaler.setRepeatCount(Animation.INFINITE);

        int fitbitStepCount = 0;
        if (!sharedPreferences.getString("dataTrackingSystem",
                        ctx.getString(R.string.device_tracking_ntt))
                .equals(ctx.getString(R.string.device_tracking_ntt))) {
            if (mStepsDBHelper == null) {
                mStepsDBHelper = new StepsDBHelper(ctx);
            }
            fitbitStepCount = mStepsDBHelper.fetchTodayStepCount();
        }
        displayActivityChartFitbit(fitbitStepCount, true);

        TextView fitbitSync = findViewById(R.id.sync);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            fitbitSync.setTooltipText(getString(R.string.sync_steps));
        }
        fitbitSync.setOnClickListener(view -> {
            Thread thread = new Thread(() -> {
                NxFitbitHelper.sendUserToAuthorisation(ctx, false);
            });
            thread.start();
        });

        TextView healthConnectSync = findViewById(R.id.sync_health_connect);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            healthConnectSync.setTooltipText(getString(R.string.sync_health_connect_btn));
        }
        healthConnectSync.setOnClickListener(view -> {
            checkPermissionsAndReadData();
        });

        View launchWorkoutWizardButton = findViewById(R.id.btn_start_workout_section); // Assuming you have this
        // button in MainActivity
        // layout
        launchWorkoutWizardButton.setOnClickListener(v -> {
            Intent intent = new Intent(ctx, WorkoutWizardActivity.class);
            startActivity(intent);
        });

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                    Boolean fine = results.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarse = results.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                        showActivityTypePicker();
                    } else {
                        Toast.makeText(this, "Location permission is required to record routes.",
                                Toast.LENGTH_LONG).show();
                    }
                });

        setupRouteCard();

        Uri returnUrl = getIntent().getData();
        if (returnUrl != null) {
            try {
                NxFitbitHelper fitbit = new NxFitbitHelper(ctx, false);
                fitbit.requestAccessTokenFromIntent(returnUrl);
                try {
                    JSONObject responseProfile = fitbit.getUserProfile();
                    // essential for capability to fetch measurements
                    responseProfile.getJSONObject("user");

                    SharedPreferences.Editor editor = sharedPreferences.edit();

                    // grab userId
                    String fitbitUserId = fitbit.getUserId();
                    editor.putString("fitbitUserId", fitbitUserId);// trackedActivityCount);
                    editor.apply();

                    String soughtInfo = "steps";
                    String targetDate = "today";
                    int trackedActivityCount = 0;
                    try {
                        JSONObject stepActivityList;
                        stepActivityList = fitbit.getActivityByDate(soughtInfo, targetDate);
                        JSONArray stepActivityArray;
                        stepActivityArray = stepActivityList.getJSONArray("activities-tracker-" + soughtInfo);
                        Log.d(MainActivity.TAG, "From JSON distance:" + stepActivityArray.length());
                        if (stepActivityArray.length() > 0) {
                            Log.d(MainActivity.TAG, "we found matching records");
                            // loop through records adding up recorded steps
                            for (int i = 0; i < stepActivityArray.length(); i++) {
                                trackedActivityCount += parseInt(stepActivityArray.getJSONObject(i).getString("value"));
                            }

                            displayActivityChartFitbit(trackedActivityCount, true);
                            Calendar mCalendar = Calendar.getInstance();

                            editor.putString("fitbitLastSyncDate",
                                    new SimpleDateFormat("yyyyMMdd").format(
                                            mCalendar.getTime()));
                            editor.putLong("fitbitLastSyncTime", System.currentTimeMillis());
                            // TODO: demo data, replace when go live
                            editor.putInt("fitbitSyncCount", trackedActivityCount);// 6543);//
                            editor.apply();
                        } else {
                            Log.d(MainActivity.TAG, "No auto-tracked activity found for today");
                        }

                    } catch (Exception e) {
                        // Log.e(TAG, Objects.requireNonNull(e.getMessage()));
                        Log.e(TAG, "ERROR");
                        e.printStackTrace();
                    }
                } catch (Exception myExc) {
                    // Log.e(TAG, Objects.requireNonNull(myExc.getMessage()));
                    Log.e(TAG, "ERROR");
                    Toast.makeText(getApplicationContext(), getString(R.string.error_fitbit_fecth), Toast.LENGTH_SHORT)
                            .show();
                }
            } catch (Exception ex) {
                // Log.e(TAG, Objects.requireNonNull(ex.getMessage()));
                Log.e(TAG, "ERROR");
                ex.printStackTrace();
            }

            fitbitSync.startAnimation(scaler);
        }

        ImageView fitbitLogo = findViewById(R.id.fitbit_logo);
        fitbitLogo.setOnClickListener(view -> {
            String lastMainSyncDate = sharedPreferences.getString("fitbitLastSyncDate", "");
            if (!lastMainSyncDate.isEmpty())
                Toast.makeText(ctx, "Fitbit last synced on : " + lastMainSyncDate, Toast.LENGTH_LONG).show();
            else {
                Toast.makeText(ctx, "Fitbit not synced yet. Click the cloud button to sync Now.", Toast.LENGTH_LONG)
                        .show();
            }
        });

                ImageView healthConnectLogoInChart = findViewById(R.id.health_connect_logo);
        healthConnectLogoInChart.setOnClickListener(view -> {
            SharedPreferences prefs = getSharedPreferences("actifitSets", MODE_PRIVATE);
            String lastMainSyncDate = prefs.getString("healthConnectLastSyncDate", "");
            if (!lastMainSyncDate.isEmpty())
                Toast.makeText(ctx, "Health Connect last synced on : " + lastMainSyncDate, Toast.LENGTH_LONG).show();
            else {
                Toast.makeText(ctx, "Health Connect not synced yet. Click the cloud button to sync Now.",
                        Toast.LENGTH_LONG).show();
            }
        });

        // TODO: revisit this Fitbit chart reset functionality, not sure why it is there
        /*
         * getFitbitPieChartReset();
         * boolean resetFitbit = sharedPreferences.getBoolean("resetPieChart",false);
         * if(resetFitbit){
         * displayActivityChartFitbit(0,true);
         * SharedPreferences.Editor editor = sharedPreferences.edit();
         * editor.putBoolean("resetPieChart",false);
         * editor.apply();
         * }
         */

        dayChartButton.setOnClickListener(view -> {

            slideRight(dayChart);
            slideLeft(fullChart);
            dayChartButton.setVisibility(GONE);
            fullChartButton.setVisibility(View.VISIBLE);
            /*
             * fullChart.animate()
             * .translationXBy(fullChart.getWidth())
             * .alpha(0.0f);
             *
             * dayChart.animate()
             * .translationXBy(dayChart.getWidth())
             * .alpha(1.0f)
             * .setListener(null);
             */
            /*
             * fullChart.animate()
             * .translationX(0)
             * .alpha(1.0f)
             * .setListener(null);
             */

        });

        fullChartButton.setOnClickListener(view -> {
            slideLeft(dayChart);
            slideRight(fullChart);
            dayChartButton.setVisibility(View.VISIBLE);
            fullChartButton.setVisibility(GONE);
            /*
             * dayChart.animate()
             * .translationX(dayChart.getWidth() * -1)
             * .alpha(0.0f);
             *
             * fullChart.animate()
             * .translationXBy(fullChart.getWidth() * -1)
             * .alpha(1.0f)
             * .setListener(null);
             * /*fullChart.animate()
             * .translationX(0)
             * .alpha(1.0f)
             * .setListener(null);
             */

        });

        // preload tutorial vid url
        Handler uiAltHandler = new Handler(Looper.getMainLooper());
        String vidFetchUrl = Utils.apiUrl(this) + getString(R.string.tut_vid_url);
        // final String[] tutVidUrl = {""};

        // Request the rank of the user while expecting a JSON response
        JsonObjectRequest vidUrlRequest = new JsonObjectRequest(Request.Method.GET, vidFetchUrl, null,
                response -> uiAltHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (response.has("vidUrl")) {
                            try {
                                tutVidUrl[0] = response.getString("vidUrl");
                            } catch (JSONException e) {
                                // Log.e(TAG, Objects.requireNonNull(e.getMessage()));
                                Log.e(TAG, "ERROR");
                                e.printStackTrace();
                            }
                        }
                    }
                }),
                error -> {
                    // error
                    Log.e(MainActivity.TAG, "Load image error");
                    error.printStackTrace();
                });

        // queue = Volley.newRequestQueue(this);

        queue.add(vidUrlRequest);

        BtnWaves.setOnClickListener(view -> {
            WavesDialogFragment wavesDialog = new WavesDialogFragment(getCtx());
            wavesDialog.show(getSupportFragmentManager(), "waves_dialog");

        });

        BtnReferFriend.setOnClickListener(view -> {

            AlertDialog.Builder referDialogBuilder = new AlertDialog.Builder(ctx);
            referLayout = getLayoutInflater().inflate(R.layout.refer_friend, null);
            EditText refLink = referLayout.findViewById(R.id.referralLink);
            refLink.setText(getString(R.string.referrals_format) + username);

            TextView referralDescription = referLayout.findViewById(R.id.referral_description);
            referralDescription.setText(Html.fromHtml(getString(R.string.referrals_details)));

            TextView successfulReferral = referLayout.findViewById(R.id.success_referrals);

            if (userReferrals != null && userReferrals.length() > 0) {

                successfulReferral.setTextColor(ctx.getResources().getColor(R.color.actifitDarkGreen));
                successfulReferral.setText(Html.fromHtml(checkMark + userReferrals.length()));
            }

            TextView copyButton = referLayout.findViewById(R.id.copyButton);
            copyButton.setOnClickListener(view12 -> {
                copyButton.startAnimation(rotate);
                copyText(refLink);
            });

            TextView shareButton = referLayout.findViewById(R.id.shareButton);
            shareButton.setOnClickListener(view1 -> {
                // copyText(refLink);
                shareButton.startAnimation(rotate);
                Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");
                String shareSubject = getString(R.string.referral_title);
                String shareBody = getString(R.string.referral_description);
                shareBody += " "
                        + getString(R.string.referral_join_link).replace("_URL_", refLink.getText().toString());

                sharingIntent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);
                sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody);

                MainActivity.this.startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)));

            });

            // also load data relating to free signups
            loadAndUpdateSignupData();

            // display referrals count
            // Html.fromHtml(checkMark +userReferrals.length());

            AlertDialog pointer = referDialogBuilder.setView(referLayout)
                    .setTitle(getString(R.string.referrals_note))
                    .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                    .setPositiveButton(getString(R.string.close_button), null).create();

            referDialogBuilder.show();
            /*
             * pointer.getWindow().getAttributes().windowAnimations =
             * R.style.DialogAnimation;
             * pointer.getWindow().getDecorView().setBackground(getDrawable(R.drawable.
             * dialog_shape));
             * pointer.show();
             *
             */

        });

        LinearLayout EarningsPanel = findViewById(R.id.earnings_panel);

        minTokenCount = Double.parseDouble(getString(R.string.min_afit_reward_balance));

        Log.d(TAG, ">>>>[Actifit] Getting jiggy with it");

        EarningsPanel.setOnClickListener(view -> {

            // display alert dialog about pending rewards
            earningsDialogBuilder = new AlertDialog.Builder(ctx);

            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case DialogInterface.BUTTON_POSITIVE:
                            // cancel
                            break;
                    }
                }
            };

            String msg = grabEarningsPanelNote();

            earningsDialog = earningsDialogBuilder.setMessage(Html.fromHtml(msg))
                    .setTitle(getString(R.string.earnings_pane_title))
                    .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                    .setPositiveButton(getString(R.string.close_button), dialogClickListener).create();

            earningsDialogBuilder.show();
            /*
             * earningsDialog.getWindow().getAttributes().windowAnimations =
             * R.style.DialogAnimation;
             * earningsDialog.getWindow().getDecorView().setBackground(getDrawable(R.
             * drawable.dialog_shape));
             * earningsDialog.show();
             *
             */

        });

        // introduce the functionality under a separate thread to avoid ANRs

        PrepareGround prepareApp = new PrepareGround();
        prepareApp.execute();

        // Check if notifications are enabled
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(this);
        boolean notificationsEnabled = notificationManagerCompat.areNotificationsEnabled();

        // If notifications are disabled, request the permission
        if (!notificationsEnabled) {
            // requestNotificationPermissionLauncher.launch(POST_NOTIFICATIONS_PERMISSION);
            // Initialize the permission result launcher

            final String POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS";
            final int REQUEST_CODE_NOTIFICATION_PERMISSION = 1;

            // Request the notification permission
            ActivityCompat.requestPermissions(
                    this,
                    new String[] { POST_NOTIFICATIONS_PERMISSION },
                    REQUEST_CODE_NOTIFICATION_PERMISSION);

            /*
             * PermissionResultLauncher<String> requestNotificationPermissionLauncher =
             * registerForActivityResult(
             * new ActivityResultContracts.RequestPermission(),
             * result -> {
             * // Handle the permission grant or denial
             * if (result.isGranted()) {
             * // The user granted the permission, so you can now post notifications
             * } else {
             * // The user denied the permission, so you cannot post notifications
             * }
             * }
             * );
             */
        }

        // prepare ads
        // prepareAds();

        // Create the "show" button, which shows a rewarded video if one is loaded.
        dailyRewardButton = findViewById(R.id.daily_reward);

        // showVideoButton.setVisibility(View.INVISIBLE);
        // dailyRewardButton.setText(Html.fromHtml(getString(R.string.daily_reward)));
        dailyRewardButton.setOnClickListener(view -> {

            if (username == null || username.isEmpty()) {
                Toast.makeText(ctx, getString(R.string.username_missing), Toast.LENGTH_LONG).show();
                return;
            }

            // show popup for rewards
            AlertDialog.Builder rewardsDialogBuilder = new AlertDialog.Builder(ctx);
            final View rewardsLayout = getLayoutInflater().inflate(R.layout.reward_popup_v2, null);

            giftLoader = rewardsLayout.findViewById(R.id.daily_reward_icon);

            freeRewardButton = rewardsLayout.findViewById(R.id.daily_free_reward);
            fivekRewardButton = rewardsLayout.findViewById(R.id.daily_5k_reward);
            sevenkRewardButton = rewardsLayout.findViewById(R.id.daily_7k_reward);
            tenkRewardButton = rewardsLayout.findViewById(R.id.daily_10k_reward);

            // Get references to the new Status TextViews
            textViewFreeRewardStatus = rewardsLayout.findViewById(R.id.textViewFreeRewardStatus);
            textView5kRewardStatus = rewardsLayout.findViewById(R.id.textView5kRewardStatus);
            textView7kRewardStatus = rewardsLayout.findViewById(R.id.textView7kRewardStatus);
            textView10kRewardStatus = rewardsLayout.findViewById(R.id.textView10kRewardStatus);

            // Get reference to the Current Steps TextView
            textViewCurrentSteps = rewardsLayout.findViewById(R.id.textViewCurrentSteps);

            // Sync dialog views into RewardManager so its reward callback operates on the correct views
            rewardManager.setGiftLoader(giftLoader);
            rewardManager.setRewardButtons(freeRewardButton, fivekRewardButton, sevenkRewardButton, tenkRewardButton);
            rewardManager.setRewardStatusTextViews(textViewFreeRewardStatus, textView5kRewardStatus, textView7kRewardStatus, textView10kRewardStatus);
            rewardManager.resetRewardClaimStatus();

            /*
             * moveTotweets = rewardsLayout.findViewById(R.id.displayTweets);
             *
             * moveTotweets.setOnClickListener(v -> {
             * AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
             * LayoutInflater inflater = getLayoutInflater();
             * View dialogLayout = inflater.inflate(R.layout.activity_tweet_actions, null);
             * builder.setView(dialogLayout);
             *
             * // Optionally, set up the ImageView if you want to manipulate it
             * programmatically
             * // You can set an image programmatically if needed
             * // imageView.setImageResource(R.drawable.your_image);
             *
             * ImageView like = dialogLayout.findViewById(R.id.like);
             * like.setOnClickListener(v1 -> {
             * String url = "https://x.com/intent/like?tweet_id=1797370316022272474";
             * Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
             * startActivity(intent);
             * });
             *
             * ImageView retweet = dialogLayout.findViewById(R.id.retweet);
             * retweet.setOnClickListener(v12 -> {
             * String url = "https://x.com/intent/retweet?tweet_id=1797370316022272474";
             * Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
             * startActivity(intent);
             * });
             *
             * ImageView follow = dialogLayout.findViewById(R.id.follow);
             * follow.setOnClickListener(v13 -> {
             * String url = "https://x.com/intent/follow?screen_name=Actifit_fitness";
             * Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
             * startActivity(intent);
             * });
             *
             * ImageView reply = dialogLayout.findViewById(R.id.reply);
             * reply.setOnClickListener(v14 -> {
             * String url = "https://x.com/intent/tweet?in_reply_to=1797370316022272474";
             * Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
             * startActivity(intent);
             * });
             *
             *
             * builder.setPositiveButton("Close", null);
             * AlertDialog dialog = builder.create();
             * dialog.show();
             * });
             */
            freeRewardButton.setOnClickListener(innerView -> showRewardedVideo(innerView, 1));
            fivekRewardButton.setOnClickListener(innerView -> showRewardedVideo(innerView, 2));
            sevenkRewardButton.setOnClickListener(innerView -> showRewardedVideo(innerView, 3));
            tenkRewardButton.setOnClickListener(innerView -> showRewardedVideo(innerView, 4));

            // fetch existing reward status

            Date date = new Date();
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            int curDate = parseInt(dateFormat.format(date));

            // Toast.makeText(getApplicationContext(),"date"+curDate,
            // Toast.LENGTH_LONG).show();

            String strDate = sharedPreferences.getString(getString(R.string.daily_free_reward), "");
            // reinitialize rewards claimed status
            dailyRewardClaimed = false;
            fivekRewardClaimed = false;
            sevenkRewardClaimed = false;
            tenkRewardClaimed = false;

            // used to temporarly remove rewards
            if (getString(R.string.test_mode).equals("on")) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.remove(getString(R.string.daily_free_reward));
                editor.remove("freerewardedValue");
                editor.commit();
            }

            if (!strDate.equals("")) {
                if (curDate <= parseInt(strDate)) {
                    // user has already received reward
                    dailyRewardClaimed = true;
                }
            }

            strDate = sharedPreferences.getString(getString(R.string.daily_5k_reward), "");
            if (!strDate.equals("")) {
                if (curDate <= parseInt(strDate)) {
                    // user has already received reward
                    fivekRewardClaimed = true;
                }
            }

            strDate = sharedPreferences.getString(getString(R.string.daily_7k_reward), "");
            if (!strDate.equals("")) {
                if (curDate <= parseInt(strDate)) {
                    // user has already received reward
                    sevenkRewardClaimed = true;
                }
            }

            strDate = sharedPreferences.getString(getString(R.string.daily_10k_reward), "");
            if (!strDate.equals("")) {
                if (curDate <= parseInt(strDate)) {
                    // user has already received reward
                    tenkRewardClaimed = true;
                }
            }

            /*
             * dailyRewardClaimed = false;
             * fivekRewardClaimed = false;
             * sevenkRewardClaimed = false;
             * tenkRewardClaimed = false;
             *
             */

            /*
             * if (!dailyRewardClaimed){
             * freeRewardButton.setAnimation(scaler);
             * }else{
             * Spanned text = Html.fromHtml(getString(R.string.reward_claimed)+
             * sharedPreferences.getString("freerewardedValue","")+" AFIT "+checkMark);
             * freeRewardButton.setText(text);
             * }
             */

            int curStepCount = mStepsDBHelper.fetchTodayStepCount();

            // Display Current Steps
            // Get the static label string from resources
            String stepsLabel = getString(R.string.activity_count_lbl);

            // Concatenate the label with the current step count
            // You might want to add a separator like ": " or a space
            String stepsDisplayText = stepsLabel + ": " + curStepCount;

            // Set the combined text to the TextView
            textViewCurrentSteps.setText(stepsDisplayText);

            // Function to update the UI for a specific reward
            // This makes the logic cleaner and reusable
            updateRewardButtonAndStatus(
                    freeRewardButton,
                    textViewFreeRewardStatus,
                    dailyRewardClaimed,
                    0, // Steps needed for Free reward
                    sharedPreferences.getString("freerewardedValue", ""), // Replace with how you get the claimed value
                    // for Free
                    scaler, // Animation object
                    checkMark,
                    curStepCount);

            updateRewardButtonAndStatus(
                    fivekRewardButton,
                    textView5kRewardStatus,
                    fivekRewardClaimed,
                    activityMilestoneOne, // Steps needed for 5k reward
                    sharedPreferences.getString("5krewardedValue", ""), // Get claimed value from preferences
                    scaler,
                    checkMark,
                    curStepCount);

            updateRewardButtonAndStatus(
                    sevenkRewardButton,
                    textView7kRewardStatus,
                    sevenkRewardClaimed,
                    activityMilestoneTwo, // Steps needed for 7k reward
                    sharedPreferences.getString("7krewardedValue", ""), // Get claimed value
                    scaler,
                    checkMark,
                    curStepCount);

            updateRewardButtonAndStatus(
                    tenkRewardButton,
                    textView10kRewardStatus,
                    tenkRewardClaimed,
                    activityMilestoneThree, // Steps needed for 10k reward
                    sharedPreferences.getString("10krewardedValue", ""), // Get claimed value
                    scaler,
                    checkMark,
                    curStepCount);

            // animate reward button
            /*
             * if (!fivekRewardClaimed && curStepCount >= 5000){
             * fivekRewardButton.setAnimation(scaler);
             * }else if (fivekRewardClaimed){
             * Spanned text =
             * Html.fromHtml(getString(R.string.reward_claimed)+sharedPreferences.getString(
             * "5krewardedValue","")+" AFIT "+checkMark);
             * fivekRewardButton.setText(text);
             * }
             *
             * if (!sevenkRewardClaimed && curStepCount >= 7000){
             * sevenkRewardButton.setAnimation(scaler);
             * }else if (sevenkRewardClaimed){
             * Spanned text =
             * Html.fromHtml(getString(R.string.reward_claimed)+sharedPreferences.getString(
             * "7krewardedValue","")+" AFIT "+checkMark);
             * sevenkRewardButton.setText(text);
             * }
             *
             * if (!tenkRewardClaimed && curStepCount >= 10000){
             * tenkRewardButton.setAnimation(scaler);
             * }else if (tenkRewardClaimed){
             * Spanned text =
             * Html.fromHtml(getString(R.string.reward_claimed)+sharedPreferences.getString(
             * "10krewardedValue","")+" AFIT "+checkMark);
             * tenkRewardButton.setText(text);
             * }
             */

            AlertDialog pointer = rewardsDialogBuilder.setView(rewardsLayout)
                    // .setTitle(getString(R.string.rewards_note))
                    .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                    .setPositiveButton(getString(R.string.close_button), null)
                    .create();

            AlertDialog rewardsDialog = rewardsDialogBuilder.show();
            rewardsDialog.setOnDismissListener(d -> refreshSecondaryCards());
            /*
             * pointer.getWindow().getAttributes().windowAnimations =
             * R.style.DialogAnimation;
             * pointer.getWindow().getDecorView().setBackground(getDrawable(R.drawable.
             * dialog_shape));
             * //pointer.getWindow().getDecorView().setBackgroundResource(android.R.color.
             * transparent);
             *
             * pointer.show();
             */
        });

        dailyRewardButton.startAnimation(scaler);

        // final FrameLayout picFrame = findViewById(R.id.pic_frame);
        final CircleImageView picFrame = findViewById(R.id.user_profile_pic);
        final TextView welcomeUser = findViewById(R.id.welcome_user);
        final TextView userRankTV = findViewById(R.id.user_rank);

        // handle click on user profile
        picFrame.setOnClickListener(view -> {
            final SharedPreferences sharedPreferences1 = getSharedPreferences("actifitSets", MODE_PRIVATE);
            openUserAccount(sharedPreferences1);
        });

        // also handle click on username
        welcomeUser.setOnClickListener(view -> {
            final SharedPreferences sharedPreferences12 = getSharedPreferences("actifitSets", MODE_PRIVATE);
            openUserAccount(sharedPreferences12);
        });

        // also handle click on rank
        userRankTV.setOnClickListener(view -> {
            // final SharedPreferences sharedPreferences13 =
            // getSharedPreferences("actifitSets",MODE_PRIVATE);
            // openUserRank(sharedPreferences13);
            String msg = getString(R.string.user_rank_description) + getString(R.string.user_rank_description_2);

            DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
                switch (which) {
                    case DialogInterface.BUTTON_NEUTRAL:
                        // cancel
                        openUserRank();
                        break;
                }
            };

            // display alert dialog about pending rewards
            AlertDialog.Builder userRankDialogBuilder = new AlertDialog.Builder(ctx);

            AlertDialog pointer = userRankDialogBuilder.setMessage(Html.fromHtml(msg))
                    .setTitle(getString(R.string.user_rank_title))
                    .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                    .setPositiveButton(getString(R.string.close_button), null)
                    .setNeutralButton(getString(R.string.user_rank_web), dialogClickListener)
                    .create();
            userRankDialogBuilder.show();
            /*
             * pointer.getWindow().getAttributes().windowAnimations =
             * R.style.DialogAnimation;
             * pointer.getWindow().getDecorView().setBackground(getDrawable(R.drawable.
             * dialog_shape));
             * pointer.show();
             */
        });

        // hook up our standard thread catcher to allow auto-restart after crash
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandlerRestartApp(this));

        // this is now needed for proper image upload to AWS
        // TODO: RECHECK THIS CRASHING
        /*
         * try {
         * getApplicationContext().startService(new Intent(getApplicationContext(),
         * TransferService.class));
         * }catch(Exception e){
         * e.printStackTrace();
         * try {
         * if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
         * getApplicationContext().startForegroundService(new
         * Intent(getApplicationContext(), TransferService.class));
         * }
         * }catch(Exception ex){
         * ex.printStackTrace();
         * }
         * }
         */

        // connecting the activity to the service to receive proper updates on move
        // count
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final int stepCount = intent.getIntExtra("move_count", 0);
                // stepDisplay.setText(getString(R.string.activity_today_string) + (stepCount <
                // 0 ? 0 : stepCount));
                // stepDisplay.setVisibility(View.GONE);

                // if (!mStepsDBHelper.isConnected()){
                // mStepsDBHelper.reConnect();
                // }

                // Only update ring/charts from device sensor in device mode;
                // HC and Fitbit modes have their own update paths.
                String activeMode = sharedPreferences.getString("dataTrackingSystem",
                        getString(R.string.device_tracking_ntt));
                if (activeMode.equals(getString(R.string.device_tracking_ntt))) {
                    displayActivityChart(stepCount, false);
                    if (MainActivity.isActivityVisible) {
                        DisplayDayChartDataAsyncTask dispChartData = new DisplayDayChartDataAsyncTask(false);
                        dispChartData.execute(false);
                        DisplayChartDataAsyncTask dispCData = new DisplayChartDataAsyncTask(false);
                        dispCData.execute(false);
                    }
                }
            }
        };

        // handle taking photos
        /*
         * BtnSnapActiPic.setOnClickListener(view -> {
         *
         * //make sure we have a cam on device
         * PackageManager pm = ctx.getPackageManager();
         *
         * //if no cam, notify and leave
         * if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
         * Toast.makeText(getApplicationContext(),getString(R.string.device_has_no_cam),
         * Toast.LENGTH_SHORT).show();
         * return;
         * }
         *
         * //ensure we have proper permissions for image upload
         * if (shouldAskPermissions()) {
         * String[] permissions = {
         * "android.permission.READ_EXTERNAL_STORAGE",
         * "android.permission.WRITE_EXTERNAL_STORAGE"
         * };
         * askPermissions(permissions);
         * }
         *
         * Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
         * // Ensure that there's a camera activity to handle the intent
         * if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
         * // Create the File where the photo should go
         * File photoFile = null;
         * try {
         * photoFile = createImageFile();
         * } catch (IOException ex) {
         * // Error occurred while creating the File
         * ex.printStackTrace();
         * }
         * // Continue only if the File was successfully created
         * if (photoFile != null) {
         * try {
         * Uri photoURI = FileProvider.getUriForFile(ctx,
         * "io.actifit.fileprovider",
         * photoFile);
         * takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
         * startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
         * }catch (Exception myExc){
         * myExc.printStackTrace();
         * }
         * }
         * }
         * }
         * );
         */

        // handle video activity
        BtnVideo.setOnClickListener(v -> {
            // show video modal
            VideoUploadFragment dialog = new VideoUploadFragment(getApplicationContext(), LoginActivity.accessToken,
                    this, false);
            dialog.show(getSupportFragmentManager(), "video_upload_fragment");
        });

        // More footer button — handled by BaseActivity.showMoreMenu()
        View BtnMoreFooter = findViewById(R.id.btn_more_footer);
        if (BtnMoreFooter != null) {
            BtnMoreFooter.setOnClickListener(v -> showMoreMenu());
        }

        // handle activity to move to post to steemit screen
        BtnPostSteemit.setOnClickListener(arg0 -> {

            if (username == null || username.isEmpty()) {
                Toast.makeText(ctx, getString(R.string.username_missing), Toast.LENGTH_LONG).show();
            } else {
                Intent intent = new Intent(MainActivity.this, PostSteemitActivity.class);
                MainActivity.this.startActivity(intent);
            }

        });

        // load AFIT markets
        // handles sending out API query requests
        // RequestQueue queue = Volley.newRequestQueue(this);

        String afitMarketsUrl = Utils.apiUrl(this) + getString(R.string.afit_markets);

        JsonArrayRequest afitMarketsReq = new JsonArrayRequest(Request.Method.GET,
                afitMarketsUrl, null, listArray -> {
            // hide dialog
            // progress.hide();

            // Handle the result
            try {
                afitMarkets = listArray;
                // actifitTransactions.setText("Response is: "+ response);
            } catch (Exception e) {
                // hide dialog
                // progress.hide();
                // actifitTransactionsError.setVisibility(View.VISIBLE);
                // Log.e(TAG, Objects.requireNonNull(e.getMessage()));
                Log.e(TAG, "ERROR");
                e.printStackTrace();
            }

        }, error -> {
            // hide dialog
            // progress.hide();
            // actifitTransactionsView.setText("Unable to fetch balance");
            // actifitTransactionsError.setVisibility(View.VISIBLE);
        });

        queue.add(afitMarketsReq);

        // load daily tip
        displayDailyTip();

        // load user gadgets
        displayUserGadgets();

        // load referral count
        loadReferrals(queue);

        // load claimable signups
        loadClaimableSignupLinks(queue);

        // load signup links
        loadSignupLinks(queue);

        BtnBuyAFIT.setOnClickListener(arg0 -> {

            afitBuyDialogBuilder = new AlertDialog.Builder(ctx);

            String msg = "";
            msg += getString(R.string.afit_buy_note) + "<br />";

            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case DialogInterface.BUTTON_NEGATIVE:
                            // cancel
                            break;
                    }
                }
            };

            List<String> listItems = new ArrayList<String>();
            for (int i = 0; i < afitMarkets.length(); i++) {
                try {
                    listItems.add(afitMarkets.getJSONObject(i).getString("exchange"));
                } catch (JSONException e) {
                    // Log.e(TAG, Objects.requireNonNull(e.getMessage()));
                    Log.e(TAG, "ERROR");
                }
            }
            CharSequence[] marketBtns = listItems.toArray(new CharSequence[listItems.size()]);

            afitBuyDialog = afitBuyDialogBuilder
                    // .setMessage(Html.fromHtml(msg))
                    .setTitle(getString(R.string.afit_buy_title))
                    .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                    .setPositiveButton(getString(R.string.close_button), dialogClickListener)
                    .setItems(marketBtns,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {

                                    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

                                    builder.setToolbarColor(getResources().getColor(R.color.actifitRed));

                                    // animation for showing and closing screen
                                    builder.setStartAnimations(ctx, R.anim.slide_in_right, R.anim.slide_out_left);

                                    // animation for back button clicks
                                    builder.setExitAnimations(ctx, android.R.anim.slide_in_left,
                                            android.R.anim.slide_out_right);

                                    CustomTabsIntent customTabsIntent = builder.build();

                                    try {
                                        customTabsIntent.launchUrl(ctx,
                                                Uri.parse(afitMarkets.getJSONObject(which).getString("link")));
                                    } catch (JSONException e) {
                                        // Log.e(TAG, Objects.requireNonNull(e.getMessage()));
                                        Log.e(TAG, "ERROR");
                                    }

                                }
                            })
                    .create();
            afitBuyDialogBuilder.show();
            /*
             * afitBuyDialog.getWindow().getAttributes().windowAnimations =
             * R.style.DialogAnimation;
             * afitBuyDialog.getWindow().getDecorView().setBackground(getDrawable(R.drawable
             * .dialog_shape));
             * afitBuyDialog.show();
             */

        });

        // handle activity to move over to the Leaderboard screen
        BtnLeaderboard.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Intent intent = new Intent(MainActivity.this, LeaderboardActivity.class);
                MainActivity.this.startActivity(intent);

            }
        });

        BtnWalletAltContainer.setOnClickListener(arg0 -> BtnWallet.performClick());

        // handle activity to move over to the Wallet screen
        BtnWallet.setOnClickListener(arg0 -> {
            if (username == null || username.isEmpty()) {
                Toast.makeText(ctx, getString(R.string.username_missing), Toast.LENGTH_LONG).show();
            } else {

                Intent intent = new Intent(MainActivity.this, WalletActivity.class);
                MainActivity.this.startActivity(intent);
            }
        });

        // BtnViewNotifications.setOnClickListener(arg0 -> BtnWallet.performClick());
        BtnViewNotifications.setOnClickListener(arg0 -> {
            if (username == null || username.isEmpty()) {
                Toast.makeText(ctx, getString(R.string.username_missing), Toast.LENGTH_LONG).show();
            } else {

                Intent intent = new Intent(MainActivity.this, NotificationsActivity.class);
                MainActivity.this.startActivity(intent);
            }
        });

        // handle activity to move over to the Settings screen
        View.OnClickListener shareAchievementListener = v -> {
            Intent shareIntent = new Intent(MainActivity.this, ShareAchievementActivity.class);
            // Pass current stats
            int steps = mStepsDBHelper.fetchTodayStepCount();
            shareIntent.putExtra("steps", String.valueOf(steps));
            shareIntent.putExtra("username", MainActivity.username);
            shareIntent.putExtra("afit", String.format(Locale.getDefault(), "%.2f", userFullBalance));
            shareIntent.putExtra("rank", userRank);
            startActivity(shareIntent);
        };

        BtnShareAchievement.setOnClickListener(shareAchievementListener);
        BtnShareAchievementFitbit.setOnClickListener(shareAchievementListener);
        BtnShareAchievementHC.setOnClickListener(shareAchievementListener);

        BtnSettings.setOnClickListener(arg0 -> {

            if (username == null || username.isEmpty()) {
                Toast.makeText(ctx, getString(R.string.username_missing), Toast.LENGTH_LONG).show();
            } else {
                // sensorManager.unregisterListener(MainActivity.this);

                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                // overridePendingTransition(0,0);
                MainActivity.this.startActivity(intent);
                // overridePendingTransition(0,0);
            }
        });

        BtnMarket.setOnClickListener(arg0 -> {

            /*
             * if (username == null || username.length() <1){
             * Toast.makeText(ctx, getString(R.string.username_missing),
             * Toast.LENGTH_LONG).show();
             * }else {
             */

            // sensorManager.unregisterListener(MainActivity.this);
            Intent intent = new Intent(MainActivity.this, MarketActivity.class);
            MainActivity.this.startActivity(intent);
            // }
        });

        BtnPosts.setOnClickListener(arg0 -> {

            /*
             * if (username == null || username.length() <1){
             * Toast.makeText(ctx, getString(R.string.username_missing),
             * Toast.LENGTH_LONG).show();
             * }else {
             */

            // sensorManager.unregisterListener(MainActivity.this);
            Intent intent = new Intent(MainActivity.this, SocialActivity.class);
            MainActivity.this.startActivity(intent);
            // }
        });

        userGadgets = findViewById(R.id.user_gadgets);

        userGadgets.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                // display alert dialog about pending rewards
                gadgetsDialogBuilder = new AlertDialog.Builder(ctx);

                DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
                    switch (which) {
                        case DialogInterface.BUTTON_NEUTRAL:
                            BtnMarket.performClick();
                            break;
                        case DialogInterface.BUTTON_POSITIVE:
                            // cancel
                            break;
                    }
                };

                String msg = "";
                if (activeProducts == null || activeProducts.length() < 1) {
                    msg += "<b>" + getString(R.string.active_gadgets_note_1) + " <br />";
                }
                msg += getString(R.string.active_gadgets_note_2) + "<br />";
                msg += getString(R.string.active_gadgets_note_3) + "<br />";

                gadgetsDialog = gadgetsDialogBuilder.setMessage(Html.fromHtml(msg))
                        .setTitle(getString(R.string.gadgets_earning_title))
                        .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                        .setNeutralButton(getString(R.string.head_market), dialogClickListener)
                        .setPositiveButton(getString(R.string.close_button), dialogClickListener).create();
                gadgetsDialogBuilder.show();

            }
        });

        TextView BtnSwitchSettingsFitbit = findViewById(R.id.switchSettingsFitbit);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            BtnSwitchSettingsFitbit.setTooltipText("Switch to Health Connect mode");
        }

        BtnSwitchSettingsFitbit.setOnClickListener(arg0 -> {
            if (username == null || username.isEmpty()) {
                Toast.makeText(ctx, getString(R.string.username_missing), Toast.LENGTH_LONG).show();
            } else {
                // Switch to Health Connect
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("dataTrackingSystem", getString(R.string.health_connect_tracking_ntt));
                editor.commit();
                hideCharts();
                healthConnectTracking.setVisibility(View.VISIBLE);
                chartSwitcher.setVisibility(View.VISIBLE);
                findViewById(R.id.bar_chart_container).setVisibility(View.VISIBLE);
                dayChart.setVisibility(View.GONE);
                fullChart.setVisibility(View.VISIBLE);
                fullChartButton.setVisibility(View.GONE);
                dayChartButton.setVisibility(View.VISIBLE);
                checkPermissionsAndReadData();
            }
        });

        TextView BtnSwitchSettingsHealthConnect = findViewById(R.id.switchSettingsHealthConnect);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            BtnSwitchSettingsHealthConnect.setTooltipText("Switch to Phone sensors mode");
        }
        BtnSwitchSettingsHealthConnect.setOnClickListener(arg0 -> {
            if (username == null || username.isEmpty()) {
                Toast.makeText(ctx, getString(R.string.username_missing), Toast.LENGTH_LONG).show();
            } else {
                SharedPreferences.Editor editor = sharedPreferences.edit();

                // Switch back to device sensor tracking
                hideCharts();
                if (defaultChartContainer != null) defaultChartContainer.setVisibility(View.VISIBLE);
                chartSwitcher.setVisibility(View.VISIBLE);
                findViewById(R.id.bar_chart_container).setVisibility(View.VISIBLE);
                dayChart.setVisibility(View.GONE);
                fullChart.setVisibility(View.VISIBLE);
                fullChartButton.setVisibility(View.GONE);
                dayChartButton.setVisibility(View.VISIBLE);

                editor.putString("dataTrackingSystem", getString(R.string.device_tracking_ntt));
                editor.commit();
                if (mStepsDBHelper == null) {
                    mStepsDBHelper = new StepsDBHelper(ctx);
                }
                int steps = mStepsDBHelper.fetchTodayStepCount();
                ResumeAsyncTask resumeAsyncTask = new ResumeAsyncTask();
                resumeAsyncTask.execute();

                displayActivityChart(steps, true);
                new DisplayChartDataAsyncTask(true).execute(true);
                new DisplayDayChartDataAsyncTask(true).execute(true);
                refreshSecondaryCards();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            BtnSwitchSettings.setTooltipText("Switch to Fitbit mode");
        }
        BtnSwitchSettings.setOnClickListener(arg0 -> {
            if (username == null || username.isEmpty()) {
                Toast.makeText(ctx, getString(R.string.username_missing), Toast.LENGTH_LONG).show();
            } else {
                SharedPreferences.Editor editor = sharedPreferences.edit();

                // Switch to Fitbit
                hideCharts();
                chartSwitcher.setVisibility(View.GONE);
                thirdPartyTracking.setVisibility(View.VISIBLE);
                editor.putString("dataTrackingSystem", getString(R.string.fitbit_tracking_ntt));
                editor.commit();
                int steps = mStepsDBHelper.fetchTodayStepCount();
                displayActivityChartFitbit(steps, true);
                findViewById(R.id.bar_chart_container).setVisibility(View.VISIBLE);
                dayChart.setVisibility(View.GONE);
                fullChart.setVisibility(View.VISIBLE);
                chartManager.displayChartDataFitbit(true);
                refreshSecondaryCards();
            }
        });

        checkBatteryOptimization(false);

        Log.d(TAG, "[Actifit] - post check battery optimization");

        // redirect user to url of notification
        // script to receive notifications in background mode
        if (getIntent().getExtras() != null) {
            // Call your NotificationActivity here..
            if (getIntent().getExtras().containsKey("url")) {
                String targetUrl = getIntent().getExtras().get("url").toString();
                /*
                 * Intent intent = new Intent(this, MainActivity.class);
                 * intent.setData(Uri.parse(targetUrl));
                 * startActivity(intent);
                 */
                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

                builder.setToolbarColor(getResources().getColor(R.color.actifitRed));

                // animation for showing and closing fitbit authorization screen
                builder.setStartAnimations(this, R.anim.slide_in_right, R.anim.slide_out_left);

                // animation for back button clicks
                builder.setExitAnimations(this, android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right);

                CustomTabsIntent customTabsIntent = builder.build();

                customTabsIntent.launchUrl(this, Uri.parse(targetUrl));

                // return;
            }
        }

        Log.d(TAG, "[Actifit] - end of onCreate");

    }

    private void loadAndUpdateSignupData() {
        Button claimSignups = referLayout.findViewById(R.id.claimFreeSignups);
        if (userCanClaimSignupLinks) {
            claimSignups.setVisibility(View.VISIBLE);
        } else {
            claimSignups.setVisibility(GONE);
        }

        claimSignups.setOnClickListener(v -> {
            RequestQueue queue = Volley.newRequestQueue(this);
            claimFreeSignupLinks(queue);
        });

        LinearLayout linksView = referLayout.findViewById(R.id.signupLinksContainer);
        TextView linksHeader = referLayout.findViewById(R.id.available_free_signups_notice);

        // loop through signup links and display them
        if (freeSignupLinks != null && freeSignupLinks.length() > 0) {
            linksHeader.setVisibility(View.VISIBLE);
            // append all links
            for (int i = 0; i < freeSignupLinks.length(); i++) {
                try {
                    JSONObject entry = freeSignupLinks.getJSONObject(i);
                    View convertView = LayoutInflater.from(ctx).inflate(R.layout.signup_link, (ViewGroup) referLayout,
                            false);
                    // set link content
                    TextView linkTxt = convertView.findViewById(R.id.signupLink);
                    String fullLink = getString(R.string.signup_link_format)
                            .replace("PROMO", entry.getString("code"))
                            .replace("REFERRER", username);
                    linkTxt.setText(fullLink);
                    linksView.addView(convertView);

                    // add copy link functionality
                    TextView copyBtn = convertView.findViewById(R.id.copyBtn);
                    copyBtn.setOnClickListener(view12 -> {
                        copyBtn.startAnimation(rotate);

                        // copy code
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ctx
                                .getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Text", fullLink);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(MainActivity.this, getString(R.string.copy_success), Toast.LENGTH_SHORT)
                                .show();

                    });

                    // add share button functionality
                    TextView shareBtn = convertView.findViewById(R.id.shareBtn);
                    shareBtn.setOnClickListener(view1 -> {
                        // copyText(refLink);
                        shareBtn.startAnimation(rotate);
                        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                        sharingIntent.setType("text/plain");
                        String shareSubject = getString(R.string.referral_title);
                        String shareBody = getString(R.string.referral_description);
                        shareBody += " " + getString(R.string.referral_join_link).replace("_URL_", fullLink);

                        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);
                        sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody);

                        MainActivity.this
                                .startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)));

                    });
                } catch (JSONException exc) {
                    // Log.e(TAG, Objects.requireNonNull(exc.getMessage()));
                    Log.e(TAG, "ERROR");
                    exc.printStackTrace();
                }
            }
        } else {
            linksHeader.setVisibility(GONE);
        }
    }

    private void copyText(EditText src) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ctx
                .getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Text", src.getText().toString());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(MainActivity.this, getString(R.string.copy_success), Toast.LENGTH_SHORT)
                .show();
    }

    // for extra ad related documentation :
    // https://developers.google.com/admob/android/rewarded
    private void prepareAds() {
        rewardManager.prepareAds();
    }

    private void loadRewardedAd() {
        rewardManager.loadRewardedAd();
    }

    // Helper method to update a single reward's UI state
    private void updateRewardButtonAndStatus(Button button, TextView statusTextView,
                                             boolean isClaimed, int requiredSteps,
                                             String claimedValue, Animation animation,
                                             String checkMarkIcon, int currentStepCount) {
        rewardManager.updateRewardButtonAndStatus(button, statusTextView, isClaimed, requiredSteps,
                claimedValue, animation, checkMarkIcon, currentStepCount, null);
    }
            // Keep the default "Claim Reward" text, just disable it

    private void showRewardedVideo(View view, int tier) {
        rewardManager.showRewardedVideo(view, tier);
    }

    private void adjustRewardButtonsStatus(int stepCount) {
        rewardManager.adjustRewardButtonsStatus(stepCount);
    }

    private void loadConsentData(Boolean goForAds) {
        rewardManager.loadConsentData(goForAds);
    }

    public void loadForm(Boolean goForAds) {
        rewardManager.loadForm(goForAds);
    }

    public void showConsentForm() {
        rewardManager.showConsentForm();
    }

    private String grabEarningsPanelNote() {
        String msg = "";
        boolean showNotice = false;
        if (userFullBalance < minTokenCount) {
            msg += "<b>" + getString(R.string.not_earning_afit) + " " + getString(R.string.min_afit_reward_balance)
                    + " AFIT. <br /></b>";
            showNotice = true;
        }
        if (!hasSteemAccount) {
            msg += "<b>" + getString(R.string.not_earning_steem) + "<br /></b>";
            showNotice = true;
        }
        if (!hasBlurtAccount) {
            msg += "<b>" + getString(R.string.not_earning_blurt) + "<br /></b>";
            showNotice = true;
        } else if (blurtBalance < Double.parseDouble(getString(R.string.min_blurt_reward_balance))) {
            msg += "<b>" + getString(R.string.not_earning_blurt_balance) + "<br /></b>";
            showNotice = true;
        }
        msg += "<i>" + getString(R.string.earnings_pane_note_0) + "<br />";
        msg += getString(R.string.earnings_pane_note_1) + "<br /></i>";
        msg += getString(R.string.earnings_pane_note_2) + "<br /></i>";

        TextView ftv = findViewById(R.id.token_notice);
        if (showNotice) {
            ftv.setVisibility(View.VISIBLE);
        } else {
            ftv.setVisibility(GONE);
        }

        return msg;
    }

    // check and notify user about battery optimization
    @TargetApi(23)
    private void checkBatteryOptimization(Boolean forceShow) {
        TextView batteryNotif = findViewById(R.id.battery_notice);
        uiHelper.checkBatteryOptimization(forceShow, batteryNotif);
    }

    private void showBatteryNotice() {
        uiHelper.showBatteryNotice();
    }

    private void openUserAccount(SharedPreferences sharedPreferences) {
        uiHelper.openUserAccount();
    }

    private void openUserRank() {
        uiHelper.openUserRank();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO double check
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            galleryAddPic();
        }
    }

    // handle appending created pic to the gallery
    private void galleryAddPic() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(mCurrentPhotoPath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }

    // handles display of local date on front end
    private void displayDate() {
        TextView date = findViewById(R.id.current_date);
        uiHelper.displayDate(date);
    }

    private void displayUserBalance() {
        RequestQueue queue = Volley.newRequestQueue(this);
        TextView tv = findViewById(R.id.bal_display);
        ImageView afitLogo = findViewById(R.id.afit_logo);
        TextView tokenNoticeWallet = findViewById(R.id.token_notice_wallet);
        apiManager.displayUserBalance(queue, tv, afitLogo, tokenNoticeWallet);
        accountRCValue.setOnClickListener(view -> {
            AlertDialog.Builder rcDialogBuilder = new AlertDialog.Builder(ctx);
            String msg = getString(R.string.rc_note);
            AlertDialog pointer = rcDialogBuilder.setMessage(Html.fromHtml(msg))
                    .setTitle(getString(R.string.rc_note_title))
                    .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                    .setCancelable(true)
                    .setNegativeButton(getString(R.string.close_button), (dialog, id) -> dialog.dismiss()).create();
            rcDialogBuilder.show();
        });
        newbieLink.setOnClickListener(iew -> {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(ctx);
            String msg = getString(R.string.verify_newbie_note);
            dialogBuilder.setMessage(msg);
            dialogBuilder.setTitle(getString(R.string.verify_newbie_title));
            dialogBuilder.setNegativeButton(getString(R.string.discord), (dialog, id) -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.discord_actifit))));
                } catch (Exception e) {
                    Log.e(MainActivity.TAG, "error opening social media");
                }
            });
            dialogBuilder.setPositiveButton(getString(R.string.share_post_button), (dialog, id) -> {
                Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");
                String shareSubject = getString(R.string.newbie_share_socials);
                String shareBody = getString(R.string.newbie_share_socials);
                shareBody += " " + getString(R.string.actifit_url);
                sharingIntent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);
                sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
                MainActivity.this.startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)));
            });
            dialogBuilder.setCancelable(true);
            dialogBuilder.setNeutralButton(getString(R.string.dismiss_button), (dialog, id) -> dialog.cancel());
            try {
                dialogBuilder.show();
            } catch (Exception e) {
                // Log.e(MainActivity.TAG, e.getMessage());
            }
        });
    }

    public static String formatValue(double value) {
        DecimalFormat df = new DecimalFormat("###,###,###.###");
        return df.format(value);
    }

    private void displayActivityChartFitbit(final int stepCount, final boolean animate) {
        currentDisplayedStepCount = stepCount;
        chartManager.displayActivityChartFitbit(stepCount, animate);
        updateNudgeCard(stepCount);
        if (animate) checkMilestoneCelebration(stepCount);
    }

    private void displayActivityChartHealthConnect(final int stepCount, final boolean animate) {
        currentDisplayedStepCount = stepCount;
        chartManager.displayActivityChartHealthConnect(stepCount, animate);
        updateNudgeCard(stepCount);
        if (animate) checkMilestoneCelebration(stepCount);
    }

    private void displayActivityChart(final int stepCount, final boolean animate) {
        currentDisplayedStepCount = stepCount;
        chartManager.displayActivityChart(stepCount, animate);
        updateNudgeCard(stepCount);
        if (animate) checkMilestoneCelebration(stepCount);
    }

    /**
     * Refreshes all secondary dashboard cards (streak, heatmap, AI insight, estimated reward)
     * using the currently active tracking mode's data source.
     * fetchStepCountByDate/fetchTodayStepCount are already mode-aware so no extra branching needed.
     */
    public void refreshSecondaryCards() {
        int todaySteps = Math.max(0, mStepsDBHelper != null
                ? mStepsDBHelper.fetchTodayStepCount()
                : currentDisplayedStepCount);
        updateNudgeCard(todaySteps);
        RequestQueue q = Volley.newRequestQueue(this);
        TextView tvAfit = findViewById(R.id.tv_estimated_afit);
        apiManager.displayEstimatedReward(q, tvAfit, todaySteps);
        updateStreakStrip();
        buildMonthHeatmap();
        loadAiInsight();
    }

    private class DisplayDayChartDataAsyncTask extends AsyncTask<Boolean, Void, ArrayList<ActivitySlot>> {
        Boolean animate = false;
        public DisplayDayChartDataAsyncTask(Boolean _animate) { animate = _animate; }
        @Override
        protected ArrayList<ActivitySlot> doInBackground(Boolean... animate) {
            Date date = new Date();
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            String strDate = dateFormat.format(date);
            return mStepsDBHelper.fetchDateTimeSlotActivity(strDate);
        }
        @Override
        protected void onPostExecute(ArrayList<ActivitySlot> mStepCountList) {
            super.onPostExecute(mStepCountList);
            chartManager.displayDayChartData(animate);
        }
    }

    private void displayDayChartData(final boolean animate) {
        chartManager.displayDayChartData(animate);
    }

    private class DisplayChartDataAsyncTask extends AsyncTask<Boolean, Void, ArrayList<DateStepsModel>> {
        Boolean animate = false;
        public DisplayChartDataAsyncTask(Boolean _animate) { animate = _animate; }
        @Override
        protected ArrayList<DateStepsModel> doInBackground(Boolean... animate) {
            return mStepsDBHelper.readStepsEntries();
        }
        @Override
        protected void onPostExecute(ArrayList<DateStepsModel> mStepCountList) {
            super.onPostExecute(mStepCountList);
            chartManager.displayChartData(animate);
        }
    }

    private class DisplayHCHistoryChartAsyncTask extends AsyncTask<Boolean, Void, Void> {
        Boolean animate = false;
        public DisplayHCHistoryChartAsyncTask(Boolean _animate) { animate = _animate; }
        @Override
        protected Void doInBackground(Boolean... params) { return null; }
        @Override
        protected void onPostExecute(Void v) {
            super.onPostExecute(v);
            chartManager.displayChartDataHC(animate);
        }
    }

    private class DisplayHCDayChartAsyncTask extends AsyncTask<Boolean, Void, Void> {
        Boolean animate = false;
        public DisplayHCDayChartAsyncTask(Boolean _animate) { animate = _animate; }
        @Override
        protected Void doInBackground(Boolean... params) { return null; }
        @Override
        protected void onPostExecute(Void v) {
            super.onPostExecute(v);
            chartManager.displayDayChartDataHC(animate);
        }
    }

    private class DisplayFitbitHistoryChartAsyncTask extends AsyncTask<Boolean, Void, Void> {
        Boolean animate = false;
        public DisplayFitbitHistoryChartAsyncTask(Boolean _animate) { animate = _animate; }
        @Override
        protected Void doInBackground(Boolean... params) { return null; }
        @Override
        protected void onPostExecute(Void v) {
            super.onPostExecute(v);
            chartManager.displayChartDataFitbit(animate);
        }
    }

    private void displayChartData(final boolean animate) {
        chartManager.displayChartData(animate);
    }

    private void hideCharts() {
        chartManager.hideCharts();
    }

    // handles fetching and displaying pending user rewards
    public void displayPendingRewards() {
        RequestQueue queue = Volley.newRequestQueue(this);
        apiManager.displayPendingRewards(queue);
    }

    private void displayEstimatedReward() {
        RequestQueue queue = Volley.newRequestQueue(this);
        TextView tvEstimatedAfit = findViewById(R.id.tv_estimated_afit);
        apiManager.displayEstimatedReward(queue, tvEstimatedAfit, currentDisplayedStepCount);
    }

    private void loadSignupLinks(RequestQueue queue) {
        apiManager.loadSignupLinks(queue);
    }

    private boolean isRewardClaimedToday(String prefKey) {
        SharedPreferences prefs = getSharedPreferences("actifitSets", MODE_PRIVATE);
        String strDate = prefs.getString(prefKey, "");
        if (strDate.isEmpty()) return false;
        try {
            int curDate = Integer.parseInt(
                new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(new Date()));
            return curDate <= Integer.parseInt(strDate);
        } catch (NumberFormatException e) { return false; }
    }

    private void updateNudgeCard(int stepCount) {
        View nudgeCard = findViewById(R.id.nudge_card);
        TextView tvMsg = findViewById(R.id.tv_nudge_message);
        TextView tvIcon = findViewById(R.id.tv_nudge_icon);
        TextView tvDismiss = findViewById(R.id.tv_nudge_dismiss);
        if (nudgeCard == null || tvMsg == null) return;

        // DB returns -1 when no entry exists yet for today; treat as 0 steps
        stepCount = Math.max(0, stepCount);

        boolean fiveKClaimed  = isRewardClaimedToday(getString(R.string.daily_5k_reward));
        boolean sevenKClaimed = isRewardClaimedToday(getString(R.string.daily_7k_reward));
        boolean tenKClaimed   = isRewardClaimedToday(getString(R.string.daily_10k_reward));

        String msg;
        String icon = "";
        boolean claimable = false;
        boolean postable  = false;

        if (stepCount < activityMilestoneOne) {
            msg = getString(R.string.nudge_keep_going, activityMilestoneOne - stepCount);
        } else if (!fiveKClaimed) {
            msg = getString(R.string.nudge_claim_reward, activityMilestoneOne);
            claimable = true;
        } else if (stepCount < activityMilestoneTwo) {
            msg = getString(R.string.nudge_next_reward_steps, activityMilestoneTwo - stepCount);
        } else if (!sevenKClaimed) {
            msg = getString(R.string.nudge_claim_reward, activityMilestoneTwo);
            claimable = true;
        } else if (stepCount < activityMilestoneThree) {
            msg = getString(R.string.nudge_next_reward_steps, activityMilestoneThree - stepCount);
        } else if (!tenKClaimed) {
            msg = getString(R.string.nudge_claim_reward, activityMilestoneThree);
            claimable = true;
        } else {
            msg = getString(R.string.nudge_all_done_post);
            postable = true;
        }

        tvMsg.setText(msg);
        if (tvIcon != null) tvIcon.setText(icon);

        nudgeCard.setOnClickListener(null);
        if (claimable) {
            nudgeCard.setOnClickListener(v -> {
                Button rewardBtn = findViewById(R.id.daily_reward);
                if (rewardBtn != null) rewardBtn.performClick();
            });
        } else if (postable) {
            nudgeCard.setOnClickListener(v -> {
                if (BtnPostSteemit != null) BtnPostSteemit.performClick();
            });
        }

        View accentBar = nudgeCard.findViewById(R.id.nudge_accent_bar);
        if (accentBar != null) {
            int barColor;
            if (claimable && stepCount >= activityMilestoneThree) {
                barColor = android.graphics.Color.parseColor("#1976D2"); // blue -- 10K claimable
            } else if (claimable) {
                barColor = ContextCompat.getColor(this, R.color.md_theme_secondary); // green -- 5K/7K claimable
            } else if (postable) {
                barColor = android.graphics.Color.parseColor("#1976D2"); // blue -- all done, go post
            } else {
                barColor = android.graphics.Color.parseColor("#FFA000"); // amber -- in progress
            }
            accentBar.setBackgroundColor(barColor);
        }

        nudgeCard.setVisibility(View.VISIBLE);
        if (tvDismiss != null) tvDismiss.setOnClickListener(v -> nudgeCard.setVisibility(View.GONE));
    }
    private void updateStreakStrip() {
        int[] dayViewIds = {
            R.id.streak_day_0, R.id.streak_day_1, R.id.streak_day_2,
            R.id.streak_day_3, R.id.streak_day_4, R.id.streak_day_5, R.id.streak_day_6
        };
        int[] labelViewIds = {
            R.id.streak_label_0, R.id.streak_label_1, R.id.streak_label_2,
            R.id.streak_label_3, R.id.streak_label_4, R.id.streak_label_5, R.id.streak_label_6
        };
        String[] dayAbbr = {
            getString(R.string.day_abbr_sun), getString(R.string.day_abbr_mon),
            getString(R.string.day_abbr_tue), getString(R.string.day_abbr_wed),
            getString(R.string.day_abbr_thu), getString(R.string.day_abbr_fri),
            getString(R.string.day_abbr_sat)
        };
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);

        // Fill 7-day window: index 0 = 6 days ago, index 6 = today
        for (int i = 0; i < 7; i++) {
            Calendar dayCal = Calendar.getInstance();
            dayCal.add(Calendar.DATE, -(6 - i));
            String dateStr = sdf.format(dayCal.getTime());
            int steps = mStepsDBHelper != null ? mStepsDBHelper.fetchStepCountByDate(dateStr) : -1;
            boolean active = steps >= 5000;

            View dayView = findViewById(dayViewIds[i]);
            if (dayView != null) {
                dayView.setBackground(ContextCompat.getDrawable(this,
                    active ? R.drawable.streak_day_active : R.drawable.streak_day_inactive));
            }
            TextView labelView = findViewById(labelViewIds[i]);
            if (labelView != null) {
                labelView.setText(dayAbbr[dayCal.get(Calendar.DAY_OF_WEEK) - 1]);
            }
        }

        // Compute streak: consecutive days ending at today (skip today if inactive — day in progress)
        Calendar todayCal = Calendar.getInstance();
        int todaySteps = mStepsDBHelper != null ? mStepsDBHelper.fetchStepCountByDate(sdf.format(todayCal.getTime())) : -1;
        int startDaysBack = (todaySteps >= 5000) ? 0 : 1;
        int streak = 0;
        for (int daysBack = startDaysBack; daysBack <= 30; daysBack++) {
            Calendar dayCal = Calendar.getInstance();
            dayCal.add(Calendar.DATE, -daysBack);
            int steps = mStepsDBHelper != null ? mStepsDBHelper.fetchStepCountByDate(sdf.format(dayCal.getTime())) : -1;
            if (steps >= 5000) {
                streak++;
            } else {
                break;
            }
        }

        TextView tvStreakCount = findViewById(R.id.tv_streak_count);
        if (tvStreakCount != null) {
            if (streak == 0) {
                tvStreakCount.setText(getString(R.string.no_streak_yet));
            } else {
                tvStreakCount.setText(getString(R.string.day_streak_label, streak));
            }
        }
    }

    private void checkMilestoneCelebration(int stepCount) {
        int milestone = 0;
        String message = null;
        if (stepCount >= activityMilestoneThree && lastCelebrationMilestone < activityMilestoneThree) {
            milestone = activityMilestoneThree;
            message = getString(R.string.milestone_10k_msg);
        } else if (stepCount >= activityMilestoneTwo && lastCelebrationMilestone < activityMilestoneTwo) {
            milestone = activityMilestoneTwo;
            message = getString(R.string.milestone_7k_msg);
        } else if (stepCount >= activityMilestoneOne && lastCelebrationMilestone < activityMilestoneOne) {
            milestone = activityMilestoneOne;
            message = getString(R.string.milestone_5k_msg);
        }
        if (milestone == 0) return;
        lastCelebrationMilestone = milestone;
        final String finalMessage = message;
        // Pulse the step count text
        TextView tvCount = findViewById(R.id.tv_step_count);
        if (tvCount != null) {
            tvCount.animate().scaleX(1.4f).scaleY(1.4f).setDuration(150)
                .withEndAction(() -> tvCount.animate().scaleX(1f).scaleY(1f).setDuration(250).start())
                .start();
        }
        Toast.makeText(this, finalMessage, Toast.LENGTH_SHORT).show();
    }

    private void loadAiInsight() {
        android.content.SharedPreferences prefs = getSharedPreferences("actifitSets", MODE_PRIVATE);
        String todayStr = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(new java.util.Date());
        String cachedDate = prefs.getString("ai_insight_date", "");
        String cachedText = prefs.getString("ai_insight_text", "");
        View aiCard = findViewById(R.id.ai_insight_card);
        TextView tvInsight = findViewById(R.id.tv_ai_insight);
        if (aiCard == null || tvInsight == null) return;

        if (todayStr.equals(cachedDate) && !cachedText.isEmpty()) {
            tvInsight.setText(cachedText);
            aiCard.setVisibility(View.VISIBLE);
            return;
        }

        // Compute inputs for the prompt
        int todaySteps = mStepsDBHelper != null ? mStepsDBHelper.fetchTodayStepCount() : 0;
        int streakDays = 0;
        int totalSteps7d = 0;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
        Calendar todayStepsCal = Calendar.getInstance();
        int todayStepsVal = mStepsDBHelper != null ? mStepsDBHelper.fetchStepCountByDate(sdf.format(todayStepsCal.getTime())) : -1;
        int startDaysBack = (todayStepsVal >= 5000) ? 0 : 1;
        for (int i = startDaysBack; i <= 30; i++) {
            Calendar c = Calendar.getInstance(); c.add(Calendar.DATE, -i);
            int s = mStepsDBHelper != null ? mStepsDBHelper.fetchStepCountByDate(sdf.format(c.getTime())) : -1;
            if (s >= 5000) streakDays++; else break;
        }
        for (int i = 0; i < 7; i++) {
            Calendar c = Calendar.getInstance(); c.add(Calendar.DATE, -i);
            int s = mStepsDBHelper != null ? mStepsDBHelper.fetchStepCountByDate(sdf.format(c.getTime())) : 0;
            totalSteps7d += Math.max(s, 0);
        }
        int avgSteps7d = totalSteps7d / 7;

        AiService aiService = new AiService();
        final int finalStreak = streakDays;
        aiService.generateDashboardInsight(todaySteps, finalStreak, avgSteps7d, new AiService.TextResponseCallback() {
            @Override
            public void onSuccess(String insight) {
                runOnUiThread(() -> {
                    tvInsight.setText(insight);
                    aiCard.setVisibility(View.VISIBLE);
                    prefs.edit()
                        .putString("ai_insight_date", todayStr)
                        .putString("ai_insight_text", insight)
                        .apply();
                });
            }
            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "AI insight error: " + errorMessage);
            }
        });
    }

    private void setupRouteCard() {
        View compactRow = findViewById(R.id.route_compact_row);
        View fullContent = findViewById(R.id.route_full_content);
        if (compactRow != null && fullContent != null) {
            compactRow.setOnClickListener(v -> {
                boolean expanded = fullContent.getVisibility() == View.VISIBLE;
                fullContent.setVisibility(expanded ? View.GONE : View.VISIBLE);
            });
        }

        View chipRecord = findViewById(R.id.chip_record_compact);
        if (chipRecord != null) {
            chipRecord.setOnClickListener(v -> {
                if (fullContent != null) fullContent.setVisibility(View.VISIBLE);
                View btnStart2 = findViewById(R.id.btn_start_route_recording);
                if (btnStart2 != null) btnStart2.performClick();
            });
        }

        View btnStart = findViewById(R.id.btn_start_route_recording);
        if (btnStart == null) return;
        btnStart.setOnClickListener(v -> {
            if (RouteRecordingService.isRunning) {
                Intent intent = new Intent(this, RouteMapActivity.class);
                intent.putExtra(RouteMapActivity.EXTRA_MODE, RouteMapActivity.MODE_LIVE);
                startActivity(intent);
                return;
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                showActivityTypePicker();
            } else {
                locationPermissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                });
            }
        });

        TextView tvLastRouteView = findViewById(R.id.tv_last_route_view);
        if (tvLastRouteView != null) {
            tvLastRouteView.setOnClickListener(v -> {
                if (mStepsDBHelper == null) return;
                RouteModel route = mStepsDBHelper.getMostRecentRoute();
                if (route != null) {
                    Intent intent = new Intent(this, RouteMapActivity.class);
                    intent.putExtra(RouteMapActivity.EXTRA_MODE, RouteMapActivity.MODE_VIEW);
                    intent.putExtra(RouteMapActivity.EXTRA_DATE, route.date);
                    startActivity(intent);
                }
            });
        }
    }

    private void loadLastRoute() {
        if (mStepsDBHelper == null) return;
        RouteModel route = mStepsDBHelper.getMostRecentRoute();
        LinearLayout summary = findViewById(R.id.last_route_summary);
        TextView tvInfo = findViewById(R.id.tv_last_route_info);
        View fullContent = findViewById(R.id.route_full_content);
        if (summary == null || tvInfo == null) return;
        if (route != null) {
            tvInfo.setText(route.getFormattedDistance() + "  •  " + route.getFormattedDuration()
                    + "  •  " + (route.activityType != null ? route.activityType : ""));
            summary.setVisibility(View.VISIBLE);
            if (fullContent != null) fullContent.setVisibility(View.VISIBLE);
        } else {
            summary.setVisibility(View.GONE);
        }
    }

    private void showActivityTypePicker() {
        String[] outdoorTypes = {"Walking", "Running", "Cycling", "Hiking", "Jogging",
                "Skating", "Skiing", "Geocaching", "Photowalking", "Plogging",
                "Sailing", "Scootering", "Kayaking"};

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.view.View sheetView = getLayoutInflater().inflate(
                R.layout.bottom_sheet_activity_picker, null);
        com.google.android.material.chip.ChipGroup chipGroup =
                sheetView.findViewById(R.id.activity_type_chip_group);

        for (String type : outdoorTypes) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
            chip.setText(type);
            chip.setCheckable(true);
            chipGroup.addView(chip);
        }

        sheetView.findViewById(R.id.activity_type_chip_group);
        sheet.setContentView(sheetView);

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                com.google.android.material.chip.Chip selected =
                        group.findViewById(checkedIds.get(0));
                if (selected != null) {
                    String actType = selected.getText().toString();
                    sheet.dismiss();
                    startRouteRecording(actType);
                }
            }
        });

        sheet.show();
    }

    private void startRouteRecording(String activityType) {
        Intent serviceIntent = new Intent(this, RouteRecordingService.class);
        serviceIntent.putExtra(RouteRecordingService.EXTRA_ACTIVITY_TYPE, activityType);
        startForegroundService(serviceIntent);

        Intent mapIntent = new Intent(this, RouteMapActivity.class);
        mapIntent.putExtra(RouteMapActivity.EXTRA_MODE, RouteMapActivity.MODE_LIVE);
        mapIntent.putExtra(RouteMapActivity.EXTRA_ACTIVITY_TYPE, activityType);
        startActivity(mapIntent);
    }

    public void buildMonthHeatmap() {
        LinearLayout heatmapGrid = findViewById(R.id.heatmap_grid);
        if (heatmapGrid == null || mStepsDBHelper == null) return;
        heatmapGrid.removeAllViews();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
        Calendar today = Calendar.getInstance();
        int year = today.get(Calendar.YEAR);
        int month = today.get(Calendar.MONTH);
        int todayDay = today.get(Calendar.DAY_OF_MONTH);

        Calendar monthStart = Calendar.getInstance();
        monthStart.set(year, month, 1);
        int daysInMonth = monthStart.getActualMaximum(Calendar.DAY_OF_MONTH);
        int firstDow = monthStart.get(Calendar.DAY_OF_WEEK);
        int leadingBlanks = (firstDow == Calendar.SUNDAY) ? 6 : firstDow - Calendar.MONDAY;

        // Load step counts for all past days this month
        int[] stepsByDay = new int[daysInMonth + 1];
        for (int d = 1; d <= todayDay; d++) {
            Calendar c = Calendar.getInstance();
            c.set(year, month, d);
            stepsByDay[d] = mStepsDBHelper.fetchStepCountByDate(sdf.format(c.getTime()));
        }

        // Calculate streak matching streak-strip logic:
        // skip today if not yet at 5K, require 5K per day (same threshold as streak strip)
        int streak = 0;
        int startDay = (stepsByDay[todayDay] >= 5000) ? todayDay : todayDay - 1;
        for (int d = startDay; d >= 1; d--) {
            if (stepsByDay[d] >= 5000) streak++;
            else break;
        }

        // Update title with month name
        TextView tvTitle = findViewById(R.id.tv_heatmap_title);
        if (tvTitle != null) {
            tvTitle.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(today.getTime()));
        }

        // Show streak badge (hidden when streak is 0)
        TextView tvStreak = findViewById(R.id.tv_heatmap_streak);
        if (tvStreak != null) {
            if (streak > 0) {
                tvStreak.setText(streak == 1 ? getString(R.string.heatmap_streak_badge, streak) : getString(R.string.heatmap_streak_badge_plural, streak));
                tvStreak.setVisibility(View.VISIBLE);
            } else {
                tvStreak.setVisibility(View.GONE);
            }
        }

        float density = getResources().getDisplayMetrics().density;
        int cellH = (int) (20 * density);
        int gapPx = (int) (4 * density);

        // Day-of-week header row
        String[] headers = {"M", "T", "W", "T", "F", "S", "S"};
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerLp.setMargins(0, 0, 0, gapPx);
        headerRow.setLayoutParams(headerLp);
        for (String h : headers) {
            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(0, 0, gapPx, 0);
            tv.setLayoutParams(lp);
            tv.setText(h);
            tv.setTextSize(10);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(ContextCompat.getColor(this, R.color.md_theme_textSecondary));
            headerRow.addView(tv);
        }
        heatmapGrid.addView(headerRow);

        // Calendar grid
        int totalCells = leadingBlanks + daysInMonth;
        int numRows = (int) Math.ceil(totalCells / 7.0);
        for (int row = 0; row < numRows; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, cellH);
            rowLp.setMargins(0, 0, 0, gapPx);
            rowLayout.setLayoutParams(rowLp);

            for (int col = 0; col < 7; col++) {
                int cellIdx = row * 7 + col;
                int dayNum = cellIdx - leadingBlanks + 1;
                View cell = new View(this);
                LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, cellH, 1f);
                cellLp.setMargins(0, 0, gapPx, 0);
                cell.setLayoutParams(cellLp);

                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);

                if (dayNum < 1 || dayNum > daysInMonth) {
                    cell.setVisibility(View.INVISIBLE);
                } else if (dayNum > todayDay) {
                    // Future day — solid light grey so the past/future split is obvious
                    gd.setColor(0xFFEEEEEE);
                    cell.setBackground(gd);
                } else {
                    int steps = stepsByDay[dayNum];
                    if (steps <= 0) gd.setColor(0xFFD0D0D0);
                    else if (steps < 5000) gd.setColor(0xFFFFCDD2);
                    else if (steps < 7000) gd.setColor(0xFFEF9A9A);
                    else gd.setColor(0xFFFF112D);
                    cell.setBackground(gd);

                    // Tap to show step count for that day
                    int finalDay = dayNum;
                    int finalSteps = steps;
                    cell.setClickable(true);
                    cell.setFocusable(true);
                    cell.setOnClickListener(v -> {
                        String label = new SimpleDateFormat("MMM d", Locale.ENGLISH)
                                .format(new java.util.Date(
                                        new java.util.GregorianCalendar(year, month, finalDay)
                                                .getTimeInMillis()));
                        String msg = finalSteps > 0
                                ? label + ": " + String.format(Locale.ENGLISH, "%,d", finalSteps) + " steps"
                                : label + ": No activity";
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    });
                }
                rowLayout.addView(cell);
            }
            heatmapGrid.addView(rowLayout);
        }

        // Colour legend
        int legendTopMargin = (int) (8 * density);
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams legendLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        legendLp.setMargins(0, legendTopMargin, 0, 0);
        legend.setLayoutParams(legendLp);

        int[][] legendItems = {
                {0xFFD0D0D0, 0},       // no activity
                {0xFFFFCDD2, 1},       // < 5K
                {0xFFEF9A9A, 5000},    // 5K–7K
                {0xFFFF112D, 7000}     // 7K+
        };
        String[] legendLabels = {"0", "< 5K", "5–7K", "7K+"};

        for (int i = 0; i < legendItems.length; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            itemLp.setMargins(0, 0, (int) (12 * density), 0);
            item.setLayoutParams(itemLp);

            // Colour dot
            View dot = new View(this);
            int dotSize = (int) (10 * density);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotLp.setMargins(0, 0, (int) (4 * density), 0);
            dot.setLayoutParams(dotLp);
            android.graphics.drawable.GradientDrawable dotGd = new android.graphics.drawable.GradientDrawable();
            dotGd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dotGd.setColor(legendItems[i][0]);
            dot.setBackground(dotGd);
            item.addView(dot);

            // Label
            TextView label = new TextView(this);
            label.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            label.setText(legendLabels[i]);
            label.setTextSize(10);
            label.setTextColor(ContextCompat.getColor(this, R.color.md_theme_textSecondary));
            item.addView(label);

            legend.addView(item);
        }
        heatmapGrid.addView(legend);
    }

    private void claimFreeSignupLinks(RequestQueue queue) {
        String claimLink = Utils.apiUrl(this) + getString(R.string.claim_free_signup_links) + username;
        // Request the user's active gadgets list
        JsonObjectRequest claimableSignupsRequest = new JsonObjectRequest(Request.Method.GET, claimLink, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {

                        // grab result
                        try {

                            if (response.has("status") && response.getString("status").equals("success")) {
                                // claimed already
                                userCanClaimSignupLinks = false;
                                loadSignupLinks(queue);
                            }
                        } catch (Exception exc) {

                        }
                    }
                }, e -> {
            // Log.e(TAG, Objects.requireNonNull(e.getMessage()));
            Log.e(TAG, "ERROR");
        });

        queue.add(claimableSignupsRequest);

    }

    private void displayDailyTip() {
        RequestQueue queue = Volley.newRequestQueue(this);
        apiManager.displayDailyTip(queue);
    }

    private void loadReferrals(RequestQueue queue) {
        apiManager.loadReferrals(queue);
    }

    private void loadClaimableSignupLinks(RequestQueue queue) {
        apiManager.loadClaimableSignupLinks(queue);
    }

    // handles fetching and displaying current user and rank
    private void displayUserGadgets() {
        RequestQueue queue = Volley.newRequestQueue(this);
        LinearLayout userGadgets = findViewById(R.id.user_gadgets);
        LinearLayout ctaContainer = findViewById(R.id.missing_active_gadgets_container);
        if (ctaContainer != null) ctaContainer.setVisibility(View.VISIBLE);
        TextView noActiveGadgets = findViewById(R.id.missing_active_gadgets);
        View btnBrowseMarket = findViewById(R.id.btn_browse_market_gadgets);
        if (btnBrowseMarket != null) {
            btnBrowseMarket.setOnClickListener(v -> {
                View btnMarket = findViewById(R.id.btn_view_market);
                if (btnMarket != null) btnMarket.performClick();
            });
        }
        apiManager.displayUserGadgets(queue, userGadgets, noActiveGadgets);
    }

    // here in this method i want to add the circle 9task number1 , nur el huda)

    private void populateActiveProducts() {
        if (activeProducts != null && productsList != null &&
                activeProducts.length() > 0 && productsList.length() > 0) {

            if (gadgetsll != null) {
                userGadgets.removeView(gadgetsll);
            }

            HorizontalScrollView horizontalScrollView = new HorizontalScrollView(getApplicationContext());
            horizontalScrollView.setLayoutParams(new HorizontalScrollView.LayoutParams(
                    HorizontalScrollView.LayoutParams.MATCH_PARENT,
                    HorizontalScrollView.LayoutParams.MATCH_PARENT));
            horizontalScrollView.setScrollContainer(true);

            gadgetsll = new LinearLayout(getApplicationContext());
            // android:isScrollContainer="true"
            // gadgetsll.setScrollContainer(true);

            horizontalScrollView.addView(gadgetsll);

            userGadgets.addView(horizontalScrollView);

            // hide no-gadgets CTA as we do have active gadgets
            LinearLayout ctaContainer2 = findViewById(R.id.missing_active_gadgets_container);
            if (ctaContainer2 != null) ctaContainer2.setVisibility(GONE);
            for (int i = 0; i < activeProducts.length(); i++) {
                try {
                    // find matching image
                    JSONObject curProd = activeProducts.getJSONObject(i);
                    if (curProd.has("gadget")) {

                        String imgUrl = findMatchingProductImage(curProd.getString("gadget"), "_id", productsList,
                                "image");

                        if (!imgUrl.equals("")) {

                            // LinearLayout pll = new LinearLayout(getApplicationContext());

                            FrameLayout fl = new FrameLayout(getApplicationContext());
                            // fl.setScrollContainer(true);

                            // add image
                            ImageView iv = new ImageView(getApplicationContext());
                            iv.setScaleType(ImageView.ScaleType.CENTER);
                            // fl.setOrientation(LinearLayout.VERTICAL);
                            fl.addView(iv);

                            // the part that i added for the task ( nur)
                            TextView tv = new TextView(getApplicationContext());
                            tv.setText(curProd.getString("gadget_level"));
                            tv.setTextSize(10);
                            tv.setTextColor(Color.WHITE); // white text inside red circle

                            tv.setGravity(Gravity.CENTER);
                            tv.setBackground(ContextCompat.getDrawable(ctx, R.drawable.circle_badge));

                            // Set fixed size to keep it circular
                            int sizeInDp = (int) TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics());
                            tv.setWidth(sizeInDp);
                            tv.setHeight(sizeInDp);

                            // Position it at the bottom right of the gadget image
                            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    Gravity.BOTTOM | Gravity.END);
                            params.setMargins(0, 0, 2, 2); // adjust margins as needed
                            tv.setLayoutParams(params);

                            fl.addView(tv);

                            // add level
                            // TextView tv = new TextView(getApplicationContext());
                            // tv.setGravity(Gravity.BOTTOM | Gravity.RIGHT);
                            //
                            // tv.setText(curProd.getString("gadget_level"));
                            // tv.setHeight(10);
                            // tv.setWidth(10);
                            // tv.setTextSize(10);
                            //
                            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            // //tv.setBackgroundColor(getColor(R.color.actifitRed));
                            // tv.setTextColor(getColor(R.color.actifitRed));
                            // }
                            // fl.addView(tv);

                            // pll.addView(fl);

                            // add layout to container
                            // userGadgets.addView(fl);
                            gadgetsll.addView(fl);
                            /*
                             * ImageView iv = new ImageView(ctx);
                             * //iv.setImage
                             *
                             * //append extra image url on actifit
                             * //imgUrl = getString(R.string.actifit_gadget_image)+imgUrl;
                             *
                             * userGadgets.addView(iv);
                             */

                            Handler uiHandler = new Handler(Looper.getMainLooper());
                            uiHandler.post(() -> {
                                // Picasso.with(ctx)
                                if (isFinishing() || isDestroyed()) {
                                    return;
                                }
                                // Calculate 40dp to pixels for gadget images
                                int gadgetSizeDp = 30;
                                int gadgetSizePx = (int) (gadgetSizeDp * getResources().getDisplayMetrics().density);

                                Glide.with(MainActivity.this)
                                        .load(getString(R.string.actifit_gadget_image) + imgUrl)
                                        .override(gadgetSizePx, gadgetSizePx) // Explicitly override with calculated DP size
                                        .addListener(new RequestListener<Drawable>() {
                                            @Override
                                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                                Log.e(MainActivity.TAG, "Glide gadget image load failed for URL: " + model + ", Exception: " + e);
                                                // Optional: Set a placeholder image or hide the ImageView if loading fails
                                                // iv.setImageResource(R.drawable.placeholder_error);
                                                return false; // Let Glide handle the target (e.g., set error drawable if configured)
                                            }

                                            @Override
                                            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                                return false; // Let Glide handle the target
                                            }
                                        })
                                        .into(iv);
                            });
                        }

                        // listItems.add(afitMarkets.getJSONObject(i).getString("exchange"));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
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

    // handles fetching and displaying current user and rank
    private void displayUserAndRank() {
        // grab stored value, if any
        final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);
        username = sharedPreferences.getString("actifitUser", "");
        if (username != "") {
            // greet user if user identified
            final TextView welcomeUser = findViewById(R.id.welcome_user);
            final TextView userRankTV = findViewById(R.id.user_rank);

            LinearLayout userRankContainer = findViewById(R.id.rank_container);
            userRankContainer.setVisibility(View.VISIBLE);

            // hide login, show logout
            // logoutLink.setVisibility(View.VISIBLE);
            topIconsContainer.setVisibility(View.VISIBLE);

            // loginLink.setVisibility(View.GONE);
            loginContainer.setVisibility(GONE);

            // load unread notification count
            RequestQueue queue = Volley.newRequestQueue(ctx);
            loadNotifCount(queue);

            // load user settings
            Utils.loadUserSettings(queue, ctx);

            // display profile pic too
            if (username != null && !username.isEmpty()) {
                try {
                    final String encodedUsername = URLEncoder.encode(username, "UTF-8");
                    final String userImgUrl = getString(R.string.hive_image_host_url).replace("USERNAME", encodedUsername);
                    Handler uiHandler = new Handler(Looper.getMainLooper());
                    uiHandler.post(() -> {
                        Glide.with(ctx)
                                .load(userImgUrl)
                                .into(userProfilePic);
                    });
                } catch (java.io.UnsupportedEncodingException e) {
                    e.printStackTrace();
                    userProfilePic.setImageResource(R.drawable.default_avatar);
                }
            } else {
                userProfilePic.setImageResource(R.drawable.default_avatar);
            }

            // TODO: check on implementation of background for actifit. This is ready to go,
            // just need proper images
            /*
             * Handler uiAltHandler = new Handler(Looper.getMainLooper());
             * uiAltHandler.post(new Runnable(){
             *
             * @Override
             * public void run() {
             * //Picasso.with(ctx)
             * //load custom background image
             * String url = "https://actifit.io/img/header-4.png";
             * LinearLayout mainLayout =
             * MainActivity.this.findViewById(R.id.main_layout_container);
             * Picasso.get()
             * .load(url)
             * //.placeholder()
             * .into(new Target() {
             *
             * @Override
             * public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
             * mainLayout.setBackground(new BitmapDrawable(bitmap));
             * mainLayout.refreshDrawableState();
             * }
             *
             * @Override
             * public void onBitmapFailed(Exception e, Drawable errorDrawable) {
             * Toast.makeText(MainActivity.this, "Error : loading wallpaper",
             * Toast.LENGTH_SHORT).show();
             * }
             *
             * @Override
             * public void onPrepareLoad(Drawable placeHolderDrawable) {
             *
             * }
             * });
             *
             * }
             * });
             */

            // Picasso.with(ctx).load(userImgUrl).into(userProfilePic);

            // grab user rank if it is already stored today
            userRank = sharedPreferences.getString("userRank", "");
            String userRankUpdateDate = sharedPreferences.getString("userRankUpdateDate", "");
            Boolean fetchNewRankVal = false;
            if (userRank.equals("") || userRankUpdateDate.equals("")) {
                fetchNewRankVal = true;
            } else {
                // make sure last value is at least within same day, otherwise grab new val
                Date date = new Date();
                DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                String strDate = dateFormat.format(date);
                try {
                    if (Integer.parseInt(userRankUpdateDate) < Integer.parseInt(strDate)) {
                        fetchNewRankVal = true;
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Error parsing userRankUpdateDate or strDate: " + e.getMessage());
                    fetchNewRankVal = true; // Force refetch if parsing fails
                }

            }

            // set username
            welcomeUser.setText("@" + username);

            if (!fetchNewRankVal) {
                // we already have the rank, display the message and the rank
                // welcomeUser.setText(getString(R.string.welcome_user).replace("USER_NAME",
                // username).replace("USER_RANK","("+userRank+")"));
                userRankTV.setText(userRank + "");

            } else {
                // need to fetch user rank data from API

                // handles sending out API query requests
                // RequestQueue queue = Volley.newRequestQueue(this);

                // This holds the url to connect to the API and grab the user rank.
                // We append to it the username
                String userRankUrl = Utils.apiUrl(this) + getString(R.string.user_rank_api_url) + username;

                // Request the rank of the user while expecting a JSON response
                JsonObjectRequest rankRequest = new JsonObjectRequest(Request.Method.GET, userRankUrl, null,
                        new Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {

                                // Display the result
                                try {
                                    // grab current user rank
                                    String userRank = response.getString("user_rank");

                                    // store user rank along with date updated
                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.putString("userRank", userRank);

                                    Date date = new Date();
                                    DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                                    String strDate = dateFormat.format(date);

                                    editor.putString("userRankUpdateDate", strDate);
                                    editor.commit();

                                    // welcomeUser.setText(getString(R.string.welcome_user).replace("USER_NAME",
                                    // username).replace("USER_RANK", "(" + userRank + ")"));
                                    userRankTV.setText(userRank + "");
                                } catch (JSONException e) {
                                    // hide dialog
                                    e.printStackTrace();
                                }
                            }
                        }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // hide dialog
                        // error.printStackTrace();
                        Log.e(MainActivity.TAG, "error fetching rank");
                    }
                });

                // Add balance request to be processed
                queue.add(rankRequest);

            }
        } else {
            // hide logout, show login
            // logoutLink.setVisibility(View.GONE);
            topIconsContainer.setVisibility(GONE);
            loginContainer.setVisibility(View.VISIBLE);
        }
        loginLink.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                // validate input values
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                MainActivity.this.startActivity(intent);

            }

        });
        /*
         * logoutLink.setOnClickListener(new View.OnClickListener() {
         *
         * @Override
         * public void onClick(View view) {
         * //remove logged in credentials
         * final SharedPreferences sharedPreferences =
         * getSharedPreferences("actifitSets",MODE_PRIVATE);
         * SharedPreferences.Editor editor = sharedPreferences.edit();
         * editor.remove("actifitUser");
         * editor.remove("actifitPst");
         *
         * editor.remove("userRank");
         * editor.remove("userRankUpdateDate");
         * editor.remove("actvKey");
         *
         * editor.apply();
         * LoginActivity.accessToken = "";
         * MainActivity.username = "";
         * MainActivity.userRank = "";
         * MainActivity.userFullBalance = 0.0;
         * LoginActivity.accessToken = "";
         * finish();
         * overridePendingTransition( 0, 0);
         * //startActivity(getIntent());
         * Intent intent = new Intent(MainActivity.this, LoginActivity.class);
         * MainActivity.this.startActivity(intent);
         * overridePendingTransition( 0, 0);
         *
         * }
         *
         * });
         */

        signupLink.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

                builder.setToolbarColor(getResources().getColor(R.color.actifitRed));

                // animation for showing and closing fitbit authorization screen
                builder.setStartAnimations(ctx, R.anim.slide_in_right, R.anim.slide_out_left);

                // animation for back button clicks
                builder.setExitAnimations(ctx, android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right);

                CustomTabsIntent customTabsIntent = builder.build();

                customTabsIntent.launchUrl(ctx, Uri.parse(Utils.apiUrl(ctx) + getString(R.string.signup_link)));
            }

        });
    }

    public static String parseRewards(JSONObject innerRewards, String chain, String currency, Double price) {
        try {
            if (innerRewards.has(chain)) {
                JSONObject rewards = innerRewards.getJSONObject(chain);
                if (rewards.has("amount")) {
                    Double value = Double.parseDouble(rewards.getString("amount")) * price;
                    // String imgUrl = getString(R.string.actifit_image) + chain.toUpperCase()
                    // +".png";
                    // return "<li> $"+value.toString()+" ("+rewards.getString("amount") + "
                    // "+currency+" <img src="+imgUrl + " width='20px' height='20px' style='width:
                    // 20px; height: 20px;' />)</li>";
                    if (value > 0) {
                        return "<li> $" + formatValue(value) + " (" + rewards.getString("amount") + " in " + currency
                                + " )</li>";
                    } else {
                        return "";
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver((receiver),
                new IntentFilter("ACTIFIT_SERVICE"));

    }

    private void updateLang(int selectedLang) {
        LocaleManager.updateLangChoice(this, selectedLang);
        recreate();
    }

    private void displayVotingStatus() {
        RequestQueue queue = Volley.newRequestQueue(this);
        apiManager.displayVotingStatus(queue, votingStatusText);
        votingStatusContainer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder rcDialogBuilder = new AlertDialog.Builder(ctx);
                String msg = getString(R.string.reward_cycle_description);
                AlertDialog pointer = rcDialogBuilder.setMessage(Html.fromHtml(msg))
                        .setTitle(getString(R.string.reward_cycle_title))
                        .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                        .setNegativeButton(getString(R.string.close_button), null).create();
                rcDialogBuilder.show();
            }
        });
    }

    private void useDefaultTrackingMethod() {
        trackingManager.useDefaultTrackingMethod();
    }

    private boolean isHealthConnectEnabledInSettings() {
        return trackingManager.isHealthConnectEnabledInSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "[Actifit] - onResume Main");

        new Thread(() -> {

            final boolean hcActivated = isHealthConnectPermActivated(); // isHealthConnectEnabledInSettings() &&
            runOnUiThread(() -> {

                                View hcs = findViewById(R.id.health_connect_status);
                ImageView hcsHero = findViewById(R.id.health_connect_status_hero);
                hcsHero.setVisibility(GONE);
                if (hcActivated) {
                    hcs.setVisibility(GONE);
                } else {
                    hcs.setVisibility(View.VISIBLE);
                }

                displayDate();

                displayUserAndRank();

                displayUserBalance();

                displayEstimatedReward();

                updateStreakStrip();

                loadAiInsight();

                buildMonthHeatmap();

                loadLastRoute();

                displayVotingStatus();

                displayUserGadgets();

                MainActivity.isActivityVisible = true;

                // if (!mStepsDBHelper.isConnected()){
                // mStepsDBHelper.reConnect();
                // }

                if (getSharedPreferences("actifitSets", MODE_PRIVATE)
                        .getString("dataTrackingSystem", getString(R.string.device_tracking_ntt))
                        .equals(getString(R.string.device_tracking_ntt))) {
                    DisplayDayChartDataAsyncTask dispChartData = new DisplayDayChartDataAsyncTask(true);
                    dispChartData.execute(true);
                    DisplayChartDataAsyncTask dispCData = new DisplayChartDataAsyncTask(true);
                    dispCData.execute(true);
                }

                ResumeAsyncTask resumeAsyncTask = new ResumeAsyncTask();
                resumeAsyncTask.execute();

                checkBatteryOptimization(false);

                Log.d(TAG, "[Actifit] MainActivity Resume");
            });

            // LocalBroadcastManager.getInstance(this).registerReceiver((receiver),
            // new IntentFilter("ACTIFIT_SERVICE")
            // );
        }).start();
    }

    @Override
    protected void onStop() {
        // LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onStop();
        this.isActivityVisible = false;
        Log.d(TAG, "[Actifit] MainActivity onStop");
    }

    @Override
    protected void onPause() {
        // LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onPause();
        this.isActivityVisible = false;
        Log.d(TAG, "[Actifit] MainActivity onPause");
    }

    /*
     * preventing accidental single back button click leading to exiting the app and
     * losing counter tracking
     */
    boolean doubleBackToExitPressedOnce = false;

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, getString(R.string.back_exit_confirmation), Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }

    @Override
    protected void onDestroy() {
        // sensorManager.unregisterListener(this);
        /*
         * isListenerActive = false;
         * try{
         * if (mServiceIntent!=null) {
         * stopService(mServiceIntent);
         * }
         * }catch(Exception e){
         * e.printStackTrace();
         * }
         *
         * this.isActivityVisible = false;
         *
         * mStepsDBHelper.closeConnection();
         *
         * PowerManager.WakeLock wl = ActivityMonitorService.getWakeLockInstance();
         * if (wl!=null && wl.isHeld()) {
         * Log.d(MainActivity.TAG,">>>>[Actifit]Settings AGG MODE OFF");
         * wl.release();
         * }
         */

        // mStepsDBHelper.closeConnection();

        super.onDestroy();

        Log.d(TAG, "[Actifit] destroy state");
    }

    private class ResumeAsyncTask extends AsyncTask<Void, Void, String[]> {
        @Override
        protected String[] doInBackground(Void... voids) {

            // ensure our tracking is active particularly after leaving settings
            final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);

            // only start the tracking service if the device sensors is picked as tracking
            // medium
            String dataTrackingSystem = sharedPreferences.getString("dataTrackingSystem",
                    getString(R.string.device_tracking_ntt));
            String aggModeEnabled = sharedPreferences.getString("aggressiveBackgroundTracking",
                    getString(R.string.aggr_back_tracking_off_ntt));
            String[] result = { dataTrackingSystem, aggModeEnabled };
            return result;
        }

        @Override
        protected void onPostExecute(String[] result) {
            super.onPostExecute(result);

            if (result[0].equals(getString(R.string.device_tracking_ntt))) {

                if (!isMyServiceRunning(mSensorService.getClass())) {
                    // initiate the monitoring service
                    if (mSensorService == null) {
                        mSensorService = new ActivityMonitorService(getCtx());
                    }
                    if (mServiceIntent == null) {
                        mServiceIntent = new Intent(getCtx(), mSensorService.getClass());
                    }
                    // startService(mServiceIntent);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(mServiceIntent);
                    } else {
                        startService(mServiceIntent);
                    }

                    // enable aggressive mode if set
                    String aggModeEnabled = result[1];
                    if (aggModeEnabled.equals(getString(R.string.aggr_back_tracking_on_ntt))) {
                        // enable wake lock to ensure tracking functions in the background
                        PowerManager.WakeLock wl = ActivityMonitorService.getWakeLockInstance();
                        if (wl == null) {
                            // initialize power manager and wake locks either way
                            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                    getString(R.string.actifit_wake_lock_tag));
                        }
                        if (!wl.isHeld()) {
                            Log.d(MainActivity.TAG, ">>>>[Actifit]Settings AGG MODE ON");
                            wl.acquire();
                        }
                    }
                }
                /*
                 * thirdPartyTracking.setVisibility(View.GONE);
                 * dayChartButton.setVisibility(View.GONE);
                 * fullChartButton.setVisibility(View.VISIBLE);
                 */
            } else {
                // stepDisplay = findViewById(R.id.step_display);
                // inform user that fitbit mode is on
                // stepDisplay.setText(getString(R.string.fitbit_tracking_mode_active));
                // thirdPartyTracking.setVisibility(View.VISIBLE);
                // hideCharts();
            }

            // update language in case it was adjusted
            if (SettingsActivity.languageModified) {
                updateLang(SettingsActivity.langChoice);
            }

        }

    }

    private class PrepareGround extends AsyncTask<Void, Void, Void> {
        String dataTrackingSystem;
        int stepCount = 0;

        @Override
        protected Void doInBackground(Void... voids) {
            Log.d(TAG, "[Actifit] PrepareGround start");

            // Looper.prepare();
            if (mStepsDBHelper == null) {
                mStepsDBHelper = new StepsDBHelper(ctx);
            }
            // initiate the monitoring service
            mSensorService = new ActivityMonitorService(getCtx());
            mServiceIntent = new Intent(getCtx(), mSensorService.getClass());

            // retrieving account data for simple reuse. Data is not stored anywhere outside
            // actifit App.
            final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);

            /*************** security features ********************/

            // check if signature has been tampered with

            if (getString(R.string.sec_check_signature).equals("on")) {
                if ((getString(R.string.test_mode).equals("off"))
                        && checkAppSignature(ctx) == MainActivity.INVALID) {
                    // package signature has been manipulated
                    Log.d(TAG, ">>>>[Actifit] Package signature has been manipulated");
                    killActifit(getString(R.string.security_concerns));
                }

                // make sure package name has not been manipulated
                if (!ctx.getPackageName().equals("io.actifit.fitnesstracker.actifitfitnesstracker")) {
                    // package name has been manipulated
                    Log.d(TAG, ">>>>[Actifit] Package name has been manipulated");
                    killActifit(getString(R.string.security_concerns));
                }

                // let's make sure this is a smart phone device by checking SIM Card

                // Crashlytics.getInstance().crash();

                if (!isSimAvailable()) {
                    // no valid active sim card detected
                    Log.d(TAG, ">>>>[Actifit] No valid SIM card detected");
                    killActifit(getString(R.string.no_valid_sim));
                }

                // also let's try to detect if this is a known emulator
                if (isEmulator()) {
                    Log.d(TAG, ">>>>[Actifit] Emulator detected");
                    killActifit(getString(R.string.emulator_device));
                }

                // check if device is rooted
                RootBeer rootBeer = new RootBeer(ctx);
                if (getString(R.string.test_mode).equals("off") && rootBeer.isRootedWithoutBusyBoxCheck()) {
                    Log.d(TAG, ">>>>[Actifit] Device is rooted");
                    killActifit(getString(R.string.device_rooted));
                }

            }
            // require now for GDPR and ad display
            loadConsentData(false);

            // check if user has a proper unique ID already, if not generate one
            String actifitUserID = sharedPreferences.getString("actifitUserID", "");
            if (actifitUserID.equals("")) {
                actifitUserID = UUID.randomUUID().toString();
                try {
                    PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                    String version = pInfo.versionName;
                    actifitUserID += version;
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("actifitUserID", actifitUserID);
                editor.apply();
            }

            // Log.d(TAG,"actifitUserID:"+actifitUserID);

            // Initial check for Health Connect availability and permissions
            /*
             * BuildersKt.launch(lifecycleCoroutineScope, Dispatchers.getDefault(),
             * CoroutineStart.DEFAULT, (scope, continuation) -> {
             * checkHealthConnectAvailabilityAndPermissions();
             * return Unit.INSTANCE;
             * });
             */

            // only start the tracking service if the device sensors is picked as tracking
            // medium
            dataTrackingSystem = sharedPreferences.getString("dataTrackingSystem",
                    getString(R.string.device_tracking_ntt));
            if (dataTrackingSystem.equals(getString(R.string.device_tracking_ntt))) {

                if (!isMyServiceRunning(mSensorService.getClass())) {
                    // startService(mServiceIntent);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(mServiceIntent);
                    } else {
                        startService(mServiceIntent);
                    }
                }

                stepCount = mStepsDBHelper.fetchTodayStepCount();
                if (stepCount < 0) stepCount = 0;
            }
            Log.d(TAG, "[Actifit] PrepareGround end");
            return null;

        }

        @Override
        protected void onPostExecute(Void param) {
            super.onPostExecute(param);
            final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",
                    MODE_PRIVATE);
            // display current date
            displayDate();

            // display user info
            displayUserAndRank();

            displayUserBalance();

            displayEstimatedReward();

            updateStreakStrip();

            loadAiInsight();

            buildMonthHeatmap();

            displayVotingStatus();

            displayPendingRewards();

            if (dataTrackingSystem.equals(getString(R.string.device_tracking_ntt))) {
                hideCharts();
                if (defaultChartContainer != null) defaultChartContainer.setVisibility(View.VISIBLE);
                chartSwitcher.setVisibility(View.VISIBLE);
                findViewById(R.id.bar_chart_container).setVisibility(View.VISIBLE);
                dayChart.setVisibility(View.GONE);
                fullChart.setVisibility(View.VISIBLE);
                fullChartButton.setVisibility(View.GONE);
                dayChartButton.setVisibility(View.VISIBLE);
                displayActivityChart(stepCount, true);
                // Device-mode charts
                new DisplayDayChartDataAsyncTask(true).execute(true);
                new DisplayChartDataAsyncTask(true).execute(true);
            } else if (dataTrackingSystem.equals(getString(R.string.fitbit_tracking_ntt))) {
                hideCharts();
                chartSwitcher.setVisibility(View.GONE);
                thirdPartyTracking.setVisibility(View.VISIBLE);
                int fitbitStepCount = sharedPreferences.getInt("fitbitSyncCount", 0);
                displayActivityChartFitbit(fitbitStepCount, true);
                // Fitbit-mode charts
                findViewById(R.id.bar_chart_container).setVisibility(View.VISIBLE);
                dayChart.setVisibility(View.GONE);
                fullChart.setVisibility(View.VISIBLE);
                new DisplayFitbitHistoryChartAsyncTask(true).execute(true);
            } else if (dataTrackingSystem.equals(getString(R.string.health_connect_tracking_ntt))) {
                hideCharts();
                healthConnectTracking.setVisibility(View.VISIBLE);
                chartSwitcher.setVisibility(View.VISIBLE);
                findViewById(R.id.bar_chart_container).setVisibility(View.VISIBLE);
                dayChart.setVisibility(View.GONE);
                fullChart.setVisibility(View.VISIBLE);
                fullChartButton.setVisibility(View.GONE);
                dayChartButton.setVisibility(View.VISIBLE);
                // Show cached HC chart data immediately; fresh data arrives via checkPermissionsAndReadData
                new DisplayHCHistoryChartAsyncTask(true).execute(true);
                new DisplayHCDayChartAsyncTask(true).execute(true);
                checkPermissionsAndReadData();
            } else {
                // Default fallback
                hideCharts();
                if (defaultChartContainer != null) defaultChartContainer.setVisibility(View.VISIBLE);
                displayActivityChart(stepCount, true);
            }

            Log.d(TAG, "[Actifit] onPostExecute");

        }
    }

    private void loadNotifCount(RequestQueue queue) {
        String notificationsUrl = Utils.apiUrl(this) + getString(R.string.user_active_notifications_url)
                + MainActivity.username;
        notifCount.setText("");
        notifCount.setVisibility(GONE);
        // Request the transactions of the user first via JsonArrayRequest
        // according to our data format
        JsonArrayRequest transactionRequest = new JsonArrayRequest(Request.Method.GET,
                notificationsUrl, null, notificationsListArray -> {
            // set proper notif count
            if (notificationsListArray != null && notificationsListArray.length() > 0) {
                String count = notificationsListArray.length() < 1000 ? notificationsListArray.length() + ""
                        : "999+";
                notifCount.setText(Html.fromHtml("<sup><small>" + count + "</small></sup>"));
                notifCount.setVisibility(View.VISIBLE);
            }
        }, error -> {

        });

        // Add transaction request to be processed
        queue.add(transactionRequest);
    }

    public void getFitbitPieChartReset() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(MainActivity.this, ResetPieChart.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pendingIntent);
        Log.d(MainActivity.TAG, "Alarm set for" + calendar.getTime() + "daily");
    }

}
