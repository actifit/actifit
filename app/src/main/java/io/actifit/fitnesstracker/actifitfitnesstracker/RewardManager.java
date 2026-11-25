package io.actifit.fitnesstracker.actifitfitnesstracker;

import static java.lang.Integer.parseInt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.animation.ObjectAnimator;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import androidx.appcompat.app.AlertDialog;
import androidx.core.text.HtmlCompat;

import androidx.annotation.NonNull;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions;
import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

/**
 * Manages reward tiers, AdMob rewarded ads, claim logic, and consent forms.
 * Extracted from MainActivity to reduce class size.
 */
public class RewardManager {

    private static final String TAG = MainActivity.TAG;

    private final Context context;
    private final Activity activity;
    private final SharedPreferences sharedPreferences;

    private RewardedAd rewardedAd;
    private boolean isAdLoading;
    private final AtomicBoolean isMobileAdsInitializeCalled = new AtomicBoolean(false);
    private final AtomicBoolean rewardInFlight = new AtomicBoolean(false);

    private ConsentInformation consentInformation;
    private ConsentForm consentForm;

    private Button dailyRewardButton;
    private Button freeRewardButton, fivekRewardButton, sevenkRewardButton, tenkRewardButton;
    private TextView textViewFreeRewardStatus, textView5kRewardStatus, textView7kRewardStatus, textView10kRewardStatus;
    private TextView textViewCurrentSteps;
    private TextView giftLoader;
    private LinearProgressIndicator progress5kReward, progress7kReward, progress10kReward;

    private boolean dailyRewardClaimed, fivekRewardClaimed, sevenkRewardClaimed, tenkRewardClaimed;

    private final int activityMilestoneOne;
    private final int activityMilestoneTwo;
    private final int activityMilestoneThree;
    private final String checkMark;
    private final Animation scaler;

    private StepsDBHelper mStepsDBHelper;

    public RewardManager(Context context, Activity activity, int milestoneOne, int milestoneTwo, int milestoneThree,
                         String checkMark, Animation scaler, StepsDBHelper stepsDBHelper) {
        this.context = context;
        this.activity = activity;
        this.sharedPreferences = context.getSharedPreferences("actifitSets", Context.MODE_PRIVATE);
        this.activityMilestoneOne = milestoneOne;
        this.activityMilestoneTwo = milestoneTwo;
        this.activityMilestoneThree = milestoneThree;
        this.checkMark = checkMark;
        this.scaler = scaler;
        this.mStepsDBHelper = stepsDBHelper;
    }

    public void setDailyRewardButton(Button button) {
        this.dailyRewardButton = button;
    }

    public void setGiftLoader(TextView giftLoader) {
        this.giftLoader = giftLoader;
    }

    public void setRewardButtons(Button free, Button fivek, Button sevenk, Button tenk) {
        this.freeRewardButton = free;
        this.fivekRewardButton = fivek;
        this.sevenkRewardButton = sevenk;
        this.tenkRewardButton = tenk;
    }

    public void setRewardStatusTextViews(TextView free, TextView fivek, TextView sevenk, TextView tenk) {
        this.textViewFreeRewardStatus = free;
        this.textView5kRewardStatus = fivek;
        this.textView7kRewardStatus = sevenk;
        this.textView10kRewardStatus = tenk;
    }

    public void setCurrentStepsTextView(TextView textView) {
        this.textViewCurrentSteps = textView;
    }

    public boolean isMobileAdsInitialized() {
        return isMobileAdsInitializeCalled.get();
    }

    public void prepareAds() {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return;
        }
        // the SDK's startup does disk work that froze the dashboard for ~0.7 s on the main thread;
        // Google recommends initializing on a background thread
        Context appContext = activity.getApplicationContext();
        new Thread(() -> MobileAds.initialize(appContext, status -> activity.runOnUiThread(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) loadRewardedAd();
        })), "ads-init").start();
    }

    public void loadRewardedAd() {
        if (rewardedAd == null) {
            isAdLoading = true;
            AdRequest adRequest = new AdRequest.Builder().build();
            RewardedAd.load(activity, context.getString(R.string.admob_ad_unit_1),
                    adRequest, new RewardedAdLoadCallback() {
                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            Log.d(TAG, loadAdError.toString());
                            rewardedAd = null;
                            isAdLoading = false;
                            // non-enforcing (debug) builds surface the raw ad-load error to aid
                            // diagnosis; enforcing (release) builds show a friendly message
                            if (!BuildConfig.ENFORCE_SECURITY) {
                                Toast.makeText(context, loadAdError.getMessage(), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(context, context.getString(R.string.err_load_ad), Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onAdLoaded(@NonNull RewardedAd ad) {
                            rewardedAd = ad;
                            isAdLoading = false;
                        }
                    });
        }
    }

    public RewardedAd getRewardedAd() {
        return rewardedAd;
    }

    public void showRewardPopup(StepsDBHelper stepsDBHelper) {
        if (MainActivity.username == null || MainActivity.username.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.username_missing), Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog.Builder rewardsDialogBuilder = new AlertDialog.Builder(context);
        final View rewardsLayout = LayoutInflater.from(context).inflate(R.layout.reward_popup_v2, null);

        giftLoader = rewardsLayout.findViewById(R.id.daily_reward_icon);
        freeRewardButton = rewardsLayout.findViewById(R.id.daily_free_reward);
        fivekRewardButton = rewardsLayout.findViewById(R.id.daily_5k_reward);
        sevenkRewardButton = rewardsLayout.findViewById(R.id.daily_7k_reward);
        tenkRewardButton = rewardsLayout.findViewById(R.id.daily_10k_reward);

        textViewFreeRewardStatus = rewardsLayout.findViewById(R.id.textViewFreeRewardStatus);
        textView5kRewardStatus = rewardsLayout.findViewById(R.id.textView5kRewardStatus);
        textView7kRewardStatus = rewardsLayout.findViewById(R.id.textView7kRewardStatus);
        textView10kRewardStatus = rewardsLayout.findViewById(R.id.textView10kRewardStatus);
        textViewCurrentSteps = rewardsLayout.findViewById(R.id.textViewCurrentSteps);
        progress5kReward = rewardsLayout.findViewById(R.id.progress_5k_reward);
        progress7kReward = rewardsLayout.findViewById(R.id.progress_7k_reward);
        progress10kReward = rewardsLayout.findViewById(R.id.progress_10k_reward);

        freeRewardButton.setOnClickListener(innerView -> showRewardedVideo(innerView, 1));
        fivekRewardButton.setOnClickListener(innerView -> showRewardedVideo(innerView, 2));
        sevenkRewardButton.setOnClickListener(innerView -> showRewardedVideo(innerView, 3));
        tenkRewardButton.setOnClickListener(innerView -> showRewardedVideo(innerView, 4));

        resetRewardClaimStatus();

        int curStepCount = getCurrentStepCount(stepsDBHelper);

        String stepsLabel = context.getString(R.string.activity_count_lbl);
        textViewCurrentSteps.setText(stepsLabel + ": " + curStepCount);

        updateRewardButtonAndStatus(freeRewardButton, textViewFreeRewardStatus, dailyRewardClaimed, 0,
                sharedPreferences.getString("freerewardedValue", ""), scaler, checkMark, curStepCount, null);
        updateRewardButtonAndStatus(fivekRewardButton, textView5kRewardStatus, fivekRewardClaimed, activityMilestoneOne,
                sharedPreferences.getString("5krewardedValue", ""), scaler, checkMark, curStepCount, progress5kReward);
        updateRewardButtonAndStatus(sevenkRewardButton, textView7kRewardStatus, sevenkRewardClaimed, activityMilestoneTwo,
                sharedPreferences.getString("7krewardedValue", ""), scaler, checkMark, curStepCount, progress7kReward);
        updateRewardButtonAndStatus(tenkRewardButton, textView10kRewardStatus, tenkRewardClaimed, activityMilestoneThree,
                sharedPreferences.getString("10krewardedValue", ""), scaler, checkMark, curStepCount, progress10kReward);
        fillMissingAmounts();

        AlertDialog pointer = rewardsDialogBuilder.setView(rewardsLayout)
                .setIcon(context.getResources().getDrawable(R.drawable.actifit_logo))
                .setPositiveButton(context.getString(R.string.close_button), null)
                .create();

        rewardsDialogBuilder.show();
    }

    void resetRewardClaimStatus() {
        Date date = new Date();
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        int curDate = parseInt(dateFormat.format(date));

        dailyRewardClaimed = false;
        fivekRewardClaimed = false;
        sevenkRewardClaimed = false;
        tenkRewardClaimed = false;

        if (context.getString(R.string.test_mode).equals("on")) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(context.getString(R.string.daily_free_reward));
            editor.remove("freerewardedValue");
            editor.commit();
        }

        String strDate = sharedPreferences.getString(context.getString(R.string.daily_free_reward), "");
        if (!strDate.equals("") && curDate <= parseInt(strDate)) {
            dailyRewardClaimed = true;
        }

        strDate = sharedPreferences.getString(context.getString(R.string.daily_5k_reward), "");
        if (!strDate.equals("") && curDate <= parseInt(strDate)) {
            fivekRewardClaimed = true;
        }

        strDate = sharedPreferences.getString(context.getString(R.string.daily_7k_reward), "");
        if (!strDate.equals("") && curDate <= parseInt(strDate)) {
            sevenkRewardClaimed = true;
        }

        strDate = sharedPreferences.getString(context.getString(R.string.daily_10k_reward), "");
        if (!strDate.equals("") && curDate <= parseInt(strDate)) {
            tenkRewardClaimed = true;
        }
    }

    void updateRewardButtonAndStatus(Button button, TextView statusTextView,
                                             boolean isClaimed, int requiredSteps,
                                             String claimedValue, Animation animation,
                                             String checkMarkIcon, int currentStepCount,
                                             LinearProgressIndicator stepProgress) {
        if (stepProgress != null) stepProgress.setVisibility(View.GONE);

        if (isClaimed) {
            button.setText(context.getString(R.string.watch_and_earn));
            button.setEnabled(false);
            setButtonDisabled(button);
            String claimedStatusText;
            if (claimedValue != null && !claimedValue.isEmpty()) {
                String checkmarkHtml = HtmlCompat.fromHtml(checkMarkIcon, HtmlCompat.FROM_HTML_MODE_COMPACT).toString();
                claimedStatusText = context.getString(R.string.reward_claimed) + claimedValue + " AFIT " + checkmarkHtml;
            } else {
                claimedStatusText = context.getString(R.string.reward_claimed);
            }
            statusTextView.setText(HtmlCompat.fromHtml(claimedStatusText, HtmlCompat.FROM_HTML_MODE_COMPACT));
            statusTextView.setVisibility(View.VISIBLE);
            stopPulseAnimation(button);
        } else if (currentStepCount >= requiredSteps) {
            button.setText(context.getString(R.string.watch_and_earn));
            button.setEnabled(true);
            setButtonEnabled(button);
            statusTextView.setText(context.getString(R.string.available_lbl));
            statusTextView.setVisibility(View.VISIBLE);
            startPulseAnimation(button);
        } else {
            button.setText(context.getString(R.string.watch_and_earn));
            button.setEnabled(false);
            setButtonDisabled(button);
            int stepsNeeded = requiredSteps - currentStepCount;
            statusTextView.setText(String.format(Locale.getDefault(),
                    context.getString(R.string.steps_to_unlock), stepsNeeded));
            statusTextView.setVisibility(View.VISIBLE);
            stopPulseAnimation(button);
            if (stepProgress != null) {
                stepProgress.setProgressCompat((int) ((float) currentStepCount / requiredSteps * 100), false);
                stepProgress.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setButtonEnabled(Button button) {
        button.setBackgroundTintList(ColorStateList.valueOf(
                context.getResources().getColor(R.color.actifitRed)));
        button.setTextColor(context.getResources().getColor(R.color.text_on_actifit_red));
    }

    private void setButtonDisabled(Button button) {
        button.setBackgroundTintList(ColorStateList.valueOf(
                context.getResources().getColor(R.color.md_theme_separator)));
        button.setTextColor(context.getResources().getColor(R.color.md_theme_textSecondary));
    }

    private void startPulseAnimation(Button button) {
        stopPulseAnimation(button);
        ObjectAnimator pulse = ObjectAnimator.ofFloat(button, "alpha", 1f, 0.55f, 1f);
        pulse.setDuration(1500);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setRepeatMode(ObjectAnimator.RESTART);
        pulse.start();
        button.setTag(pulse);
    }

    private void stopPulseAnimation(Button button) {
        Object tag = button.getTag();
        if (tag instanceof ObjectAnimator) {
            ((ObjectAnimator) tag).cancel();
        }
        button.setTag(null);
        button.setAlpha(1f);
        button.clearAnimation();
    }

    void showRewardedVideo(View view, int tier) {
        // one reward request at a time: a second tap while the first is in flight would
        // overwrite the playing ad's verification data and callbacks
        if (rewardInFlight.get()) return;
        int curStepCount = getCurrentStepCount(mStepsDBHelper);

        if (context.getString(R.string.test_mode).equals("off")) {
            int id = view.getId();
            if (id == R.id.daily_free_reward) {
                if (dailyRewardClaimed) {
                    Toast.makeText(context, context.getString(R.string.reward_already_claimed), Toast.LENGTH_LONG).show();
                    return;
                }
            } else if (id == R.id.daily_5k_reward) {
                if (fivekRewardClaimed) {
                    Toast.makeText(context, context.getString(R.string.reward_already_claimed), Toast.LENGTH_LONG).show();
                    return;
                }
                if (curStepCount < activityMilestoneOne) {
                    Toast.makeText(context, context.getString(R.string.not_eligible), Toast.LENGTH_LONG).show();
                    return;
                }
            } else if (id == R.id.daily_7k_reward) {
                if (sevenkRewardClaimed) {
                    Toast.makeText(context, context.getString(R.string.reward_already_claimed), Toast.LENGTH_LONG).show();
                    return;
                }
                if (curStepCount < activityMilestoneTwo) {
                    Toast.makeText(context, context.getString(R.string.not_eligible), Toast.LENGTH_LONG).show();
                    return;
                }
            } else if (id == R.id.daily_10k_reward) {
                if (tenkRewardClaimed) {
                    Toast.makeText(context, context.getString(R.string.reward_already_claimed), Toast.LENGTH_LONG).show();
                    return;
                }
                if (curStepCount < activityMilestoneThree) {
                    Toast.makeText(context, context.getString(R.string.not_eligible), Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }

        if (rewardedAd == null) {
            Log.d("TAG", context.getString(R.string.ad_not_ready));
            try {
                Toast.makeText(context, context.getString(R.string.ad_not_ready), Toast.LENGTH_LONG).show();
            } catch (Exception ex) {
                Log.e(TAG, "ERROR");
            }
            loadConsentData(true);
            return;
        }

        if (!rewardInFlight.compareAndSet(false, true)) return;
        if (giftLoader != null) {
            giftLoader.startAnimation(scaler);
        }
        // The bot issues a one-time id and picks the amount when it pays; AdMob only ever
        // sees "r1_<id>" (no username, no step tier).
        AdRewardApi.request(context, MainActivity.username, tier, new AdRewardApi.Callback() {
            @Override
            public void onReady(String id) {
                showAd(view, AdRewardApi.customData(id), curStepCount);
            }

            @Override
            public void onError(AdRewardApi.Error error) {
                endRewardRequest();
                if (error == AdRewardApi.Error.ALREADY_CLAIMED) {
                    // claimed on another device or before a reinstall: show it as claimed here too
                    recordRewardClaim(view.getId(), "");
                    adjustRewardButtonsStatus(getCurrentStepCount(mStepsDBHelper));
                    fillMissingAmounts();
                }
                int msg = error == AdRewardApi.Error.ALREADY_CLAIMED ? R.string.reward_already_claimed
                        : error == AdRewardApi.Error.DISABLED ? R.string.ad_rewards_paused
                        : error == AdRewardApi.Error.AUTH ? R.string.gadget_session_expired
                        : R.string.ad_reward_start_failed;
                Toast.makeText(context, context.getString(msg), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void endRewardRequest() {
        rewardInFlight.set(false);
        if (giftLoader != null) giftLoader.clearAnimation();
    }

    /** Only show an ad over our own screen: the id can arrive after the user has left it. */
    private boolean inForeground() {
        if (activity.isFinishing() || activity.isDestroyed()) return false;
        return !(activity instanceof LifecycleOwner) || ((LifecycleOwner) activity).getLifecycle()
                .getCurrentState().isAtLeast(Lifecycle.State.RESUMED);
    }

    private void showAd(View view, String customData, int curStepCount) {
        if (rewardedAd == null || !inForeground()) {
            // the unused id simply expires on the server
            endRewardRequest();
            if (rewardedAd == null && inForeground()) {
                Toast.makeText(context, context.getString(R.string.ad_not_ready), Toast.LENGTH_LONG).show();
            }
            return;
        }
        ServerSideVerificationOptions options = new ServerSideVerificationOptions.Builder()
                .setCustomData(customData)
                .build();
        rewardedAd.setServerSideVerificationOptions(options);

        rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                Log.d(TAG, "onAdShowedFullScreenContent");
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                Log.d(TAG, "onAdFailedToShowFullScreenContent");
                rewardedAd = null;
                endRewardRequest();
                loadRewardedAd();
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                Log.d(TAG, "onAdDismissedFullScreenContent");
                rewardedAd = null;
                endRewardRequest();
                loadRewardedAd();
            }
        });

        rewardedAd.show(activity, rewardItem -> {
            Log.d("TAG", "The user earned the reward.");
            // the bot picks the amount when AdMob confirms the view; pollPaidAmount shows it
            Toast.makeText(context, context.getString(R.string.ad_reward_pending_short), Toast.LENGTH_SHORT).show();

            if (giftLoader != null) giftLoader.clearAnimation();

            recordRewardClaim(view.getId(), "");
            updateRewardButtonAndStatus((Button) view, getRewardStatusTextView(view.getId()), true,
                    getRequiredSteps(view.getId()), "", scaler, checkMark, curStepCount, null);
            adjustRewardButtonsStatus(mStepsDBHelper.fetchTodayStepCount());
            pollPaidAmount(view.getId(), MainActivity.username, 0);
        });
    }

    // AdMob usually confirms a view within seconds; the bot pays then
    private static final int AMOUNT_POLL_MS = 3000;
    private static final int AMOUNT_POLL_TRIES = 10;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * After the ad: read the amount the bot paid to `user`, then show it and keep it on the
     * button. Stops when the account changes or its session can't be renewed.
     */
    private void pollPaidAmount(int viewId, String user, int attempt) {
        if (attempt >= AMOUNT_POLL_TRIES) return; // the notification still tells the user
        mainHandler.postDelayed(() -> {
            if (!user.equals(MainActivity.username)) return;
            AdRewardApi.today(context, user, amounts -> {
                if (amounts == null || !user.equals(MainActivity.username)) return;
                String amount = amounts.get(tierFor(viewId));
                if (amount == null) {
                    pollPaidAmount(viewId, user, attempt + 1);
                    return;
                }
                showPaidAmount(viewId, amount);
                if (inForeground()) {
                    // short on purpose: toasts are cut at two lines on many phones
                    Toast.makeText(context, context.getString(R.string.ad_reward_earned, amount),
                            Toast.LENGTH_LONG).show();
                }
            });
        }, AMOUNT_POLL_MS);
    }

    /** Claimed today but the amount never arrived (app closed, other device): ask once. */
    void fillMissingAmounts() {
        final int[] ids = {R.id.daily_free_reward, R.id.daily_5k_reward, R.id.daily_7k_reward, R.id.daily_10k_reward};
        boolean missing = false;
        for (int id : ids) {
            if (isClaimed(id) && sharedPreferences.getString(valueKey(id), "").isEmpty()) missing = true;
        }
        if (!missing) return;
        final String user = MainActivity.username;
        AdRewardApi.today(context, user, amounts -> {
            if (amounts == null || !user.equals(MainActivity.username)) return;
            for (int id : ids) {
                String amount = amounts.get(tierFor(id));
                if (amount != null && isClaimed(id) && sharedPreferences.getString(valueKey(id), "").isEmpty()) {
                    showPaidAmount(id, amount);
                }
            }
        });
    }

    private void showPaidAmount(int viewId, String amount) {
        sharedPreferences.edit().putString(valueKey(viewId), amount).apply();
        Button button = buttonFor(viewId);
        if (button == null) return;
        updateRewardButtonAndStatus(button, getRewardStatusTextView(viewId), true, getRequiredSteps(viewId),
                amount, scaler, checkMark, getCurrentStepCount(mStepsDBHelper), null);
    }

    private static int tierFor(int viewId) {
        if (viewId == R.id.daily_5k_reward) return 2;
        if (viewId == R.id.daily_7k_reward) return 3;
        if (viewId == R.id.daily_10k_reward) return 4;
        return 1;
    }

    private static String valueKey(int viewId) {
        if (viewId == R.id.daily_5k_reward) return "5krewardedValue";
        if (viewId == R.id.daily_7k_reward) return "7krewardedValue";
        if (viewId == R.id.daily_10k_reward) return "10krewardedValue";
        return "freerewardedValue";
    }

    private boolean isClaimed(int viewId) {
        if (viewId == R.id.daily_5k_reward) return fivekRewardClaimed;
        if (viewId == R.id.daily_7k_reward) return sevenkRewardClaimed;
        if (viewId == R.id.daily_10k_reward) return tenkRewardClaimed;
        return dailyRewardClaimed;
    }

    private Button buttonFor(int viewId) {
        if (viewId == R.id.daily_5k_reward) return fivekRewardButton;
        if (viewId == R.id.daily_7k_reward) return sevenkRewardButton;
        if (viewId == R.id.daily_10k_reward) return tenkRewardButton;
        return freeRewardButton;
    }

    private void recordRewardClaim(int viewId, String finalRewardValue) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Date date = new Date();
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String strDate = dateFormat.format(date);

        if (viewId == R.id.daily_free_reward) {
            dailyRewardClaimed = true;
            editor.putString(context.getString(R.string.daily_free_reward), strDate);
            editor.putString("freerewardedValue", finalRewardValue);
        } else if (viewId == R.id.daily_5k_reward) {
            fivekRewardClaimed = true;
            editor.putString(context.getString(R.string.daily_5k_reward), strDate);
            editor.putString("5krewardedValue", finalRewardValue);
        } else if (viewId == R.id.daily_7k_reward) {
            sevenkRewardClaimed = true;
            editor.putString(context.getString(R.string.daily_7k_reward), strDate);
            editor.putString("7krewardedValue", finalRewardValue);
        } else if (viewId == R.id.daily_10k_reward) {
            tenkRewardClaimed = true;
            editor.putString(context.getString(R.string.daily_10k_reward), strDate);
            editor.putString("10krewardedValue", finalRewardValue);
        }
        editor.commit();
    }

    private TextView getRewardStatusTextView(int viewId) {
        if (viewId == R.id.daily_5k_reward) return textView5kRewardStatus;
        if (viewId == R.id.daily_7k_reward) return textView7kRewardStatus;
        if (viewId == R.id.daily_10k_reward) return textView10kRewardStatus;
        return textViewFreeRewardStatus;
    }

    private int getRequiredSteps(int viewId) {
        if (viewId == R.id.daily_5k_reward) return activityMilestoneOne;
        if (viewId == R.id.daily_7k_reward) return activityMilestoneTwo;
        if (viewId == R.id.daily_10k_reward) return activityMilestoneThree;
        return 0;
    }

    public void adjustRewardButtonsStatus(int stepCount) {
        if (freeRewardButton != null && fivekRewardButton != null && tenkRewardButton != null) {
            if (dailyRewardClaimed) {
                stopPulseAnimation(freeRewardButton);
            } else if (!(freeRewardButton.getTag() instanceof ObjectAnimator)) {
                startPulseAnimation(freeRewardButton);
            }
            if (fivekRewardClaimed) {
                stopPulseAnimation(fivekRewardButton);
            } else if (stepCount >= activityMilestoneOne
                    && !(fivekRewardButton.getTag() instanceof ObjectAnimator)) {
                startPulseAnimation(fivekRewardButton);
            }
            if (sevenkRewardClaimed) {
                stopPulseAnimation(sevenkRewardButton);
            } else if (stepCount >= activityMilestoneTwo
                    && !(sevenkRewardButton.getTag() instanceof ObjectAnimator)) {
                startPulseAnimation(sevenkRewardButton);
            }
            if (tenkRewardClaimed) {
                stopPulseAnimation(tenkRewardButton);
            } else if (stepCount >= activityMilestoneThree
                    && !(tenkRewardButton.getTag() instanceof ObjectAnimator)) {
                startPulseAnimation(tenkRewardButton);
            }
        }
    }

    private int getCurrentStepCount(StepsDBHelper stepsDBHelper) {
        String trackingSystem = sharedPreferences.getString("dataTrackingSystem",
                context.getString(R.string.device_tracking_ntt));
        if (trackingSystem.equals(context.getString(R.string.health_connect_tracking_ntt))) {
            return sharedPreferences.getInt("healthConnectSyncCount", 0);
        } else if (trackingSystem.equals(context.getString(R.string.fitbit_tracking_ntt))) {
            return sharedPreferences.getInt("fitbitSyncCount", 0);
        } else {
            return stepsDBHelper != null ? stepsDBHelper.fetchTodayStepCount() : 0;
        }
    }

    public void loadConsentData(Boolean goForAds) {
        ConsentDebugSettings debugSettings = new ConsentDebugSettings.Builder(context)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .addTestDeviceHashedId(context.getString(R.string.test_app_id))
                .build();

        ConsentRequestParameters params = new ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build();

        consentInformation = UserMessagingPlatform.getConsentInformation(activity);

        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () -> {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                            activity,
                            (ConsentForm.OnConsentFormDismissedListener) loadAndShowError -> {
                                if (loadAndShowError != null) {
                                    Log.w(TAG, String.format("%s: %s",
                                            loadAndShowError.getErrorCode(),
                                            loadAndShowError.getMessage()));
                                }
                                if (consentInformation.canRequestAds()) {
                                    prepareAds();
                                }
                            });

                    if (consentInformation.canRequestAds()) {
                        prepareAds();
                    }
                },
                formError -> {
                    if (goForAds) {
                        loadRewardedAd();
                    }
                });
    }

    public void loadForm(Boolean goForAds) {
        UserMessagingPlatform.loadConsentForm(
                activity,
                consentForm -> {
                    this.consentForm = consentForm;
                    if (consentInformation.getConsentStatus() == ConsentInformation.ConsentStatus.REQUIRED) {
                        consentForm.show(activity, formError -> {
                            if (consentInformation.getConsentStatus() == ConsentInformation.ConsentStatus.OBTAINED) {
                                if (goForAds) {
                                    loadRewardedAd();
                                }
                            }
                            loadForm(goForAds);
                        });
                    }
                },
                formError -> {
                    if (goForAds) {
                        loadRewardedAd();
                    }
                });
    }

    public void showConsentForm() {
        if (consentForm != null) {
            consentForm.show(activity, formError -> loadForm(false));
        }
    }
}
