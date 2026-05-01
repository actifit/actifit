package io.actifit.fitnesstracker.actifitfitnesstracker;

import static java.lang.Integer.parseInt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private ConsentInformation consentInformation;
    private ConsentForm consentForm;

    private Button dailyRewardButton;
    private Button freeRewardButton, fivekRewardButton, sevenkRewardButton, tenkRewardButton;
    private TextView textViewFreeRewardStatus, textView5kRewardStatus, textView7kRewardStatus, textView10kRewardStatus;
    private TextView textViewCurrentSteps;
    private TextView giftLoader;

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
        MobileAds.initialize(activity);
        loadRewardedAd();
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
                            if (context.getString(R.string.sec_check_signature).equals("off")) {
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

        freeRewardButton.setOnClickListener(innerView -> showRewardedVideo(innerView, 1));
        fivekRewardButton.setOnClickListener(innerView -> showRewardedVideo(innerView, 2));
        sevenkRewardButton.setOnClickListener(innerView -> showRewardedVideo(innerView, 3));
        tenkRewardButton.setOnClickListener(innerView -> showRewardedVideo(innerView, 4));

        resetRewardClaimStatus();

        int curStepCount = stepsDBHelper.fetchTodayStepCount();

        String stepsLabel = context.getString(R.string.activity_count_lbl);
        textViewCurrentSteps.setText(stepsLabel + ": " + curStepCount);

        updateRewardButtonAndStatus(freeRewardButton, textViewFreeRewardStatus, dailyRewardClaimed, 0,
                sharedPreferences.getString("freerewardedValue", ""), scaler, checkMark, curStepCount);
        updateRewardButtonAndStatus(fivekRewardButton, textView5kRewardStatus, fivekRewardClaimed, activityMilestoneOne,
                sharedPreferences.getString("5krewardedValue", ""), scaler, checkMark, curStepCount);
        updateRewardButtonAndStatus(sevenkRewardButton, textView7kRewardStatus, sevenkRewardClaimed, activityMilestoneTwo,
                sharedPreferences.getString("7krewardedValue", ""), scaler, checkMark, curStepCount);
        updateRewardButtonAndStatus(tenkRewardButton, textView10kRewardStatus, tenkRewardClaimed, activityMilestoneThree,
                sharedPreferences.getString("10krewardedValue", ""), scaler, checkMark, curStepCount);

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
                                             String checkMarkIcon, int currentStepCount) {
        button.setText(context.getString(R.string.claim_now));

        if (isClaimed) {
            button.setEnabled(false);
            String claimedStatusText;
            if (claimedValue != null && !claimedValue.isEmpty()) {
                String checkmarkHtml = HtmlCompat.fromHtml(checkMarkIcon, HtmlCompat.FROM_HTML_MODE_COMPACT).toString();
                claimedStatusText = context.getString(R.string.reward_claimed) + claimedValue + "AFIT" + checkmarkHtml;
            } else {
                claimedStatusText = context.getString(R.string.reward_claimed);
            }
            statusTextView.setText(HtmlCompat.fromHtml(claimedStatusText, HtmlCompat.FROM_HTML_MODE_COMPACT));
            statusTextView.setVisibility(View.VISIBLE);
            button.clearAnimation();
        } else {
            if (currentStepCount >= requiredSteps) {
                button.setEnabled(true);
                statusTextView.setText(context.getString(R.string.available_lbl));
                statusTextView.setVisibility(View.VISIBLE);
                if (animation != null) {
                    button.startAnimation(animation);
                }
            } else {
                button.setEnabled(false);
                statusTextView.setText("Not Met");
                statusTextView.setVisibility(View.VISIBLE);
                button.clearAnimation();
            }
        }
    }

    void showRewardedVideo(View view, int tier) {
        int curStepCount = mStepsDBHelper.fetchTodayStepCount();
        if (giftLoader != null) {
            giftLoader.startAnimation(scaler);
        }

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

        double rewardValue = calculateRewardValue(tier);
        rewardValue = Math.floor(rewardValue * 1000) / 1000;

        Button myBtn = (Button) view;
        ServerSideVerificationOptions options = new ServerSideVerificationOptions.Builder()
                .setCustomData(MainActivity.username + "_" + rewardValue + "_" + tier + "_" + android.net.Uri.encode(myBtn.getText().toString()))
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
                if (giftLoader != null) giftLoader.clearAnimation();
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                Log.d(TAG, "onAdDismissedFullScreenContent");
                rewardedAd = null;
                loadRewardedAd();
            }
        });

        double finalRewardValue = rewardValue;
        rewardedAd.show(activity, rewardItem -> {
            Log.d("TAG", "The user earned the reward.");
            Toast.makeText(context,
                    context.getString(R.string.ad_reward_success).replace("_VAL_", finalRewardValue + ""),
                    Toast.LENGTH_SHORT).show();

            if (giftLoader != null) giftLoader.clearAnimation();

            recordRewardClaim(view.getId(), finalRewardValue);
            updateRewardButtonAndStatus((Button) view, getRewardStatusTextView(view.getId()), true,
                    getRequiredSteps(view.getId()), finalRewardValue + "", scaler, checkMark, curStepCount);
            adjustRewardButtonsStatus(mStepsDBHelper.fetchTodayStepCount());
        });
    }

    private double calculateRewardValue(int tier) {
        switch (tier) {
            case 1:
                return generateRandomVal(Float.parseFloat(context.getString(R.string.tier_one_reward_max)),
                        Float.parseFloat(context.getString(R.string.tier_one_reward_min)));
            case 2:
                return generateRandomVal(Float.parseFloat(context.getString(R.string.tier_two_reward_max)),
                        Float.parseFloat(context.getString(R.string.tier_two_reward_min)));
            case 3:
                return generateRandomVal(Float.parseFloat(context.getString(R.string.tier_three_reward_max)),
                        Float.parseFloat(context.getString(R.string.tier_three_reward_min)));
            case 4:
                return generateRandomVal(Float.parseFloat(context.getString(R.string.tier_four_reward_max)),
                        Float.parseFloat(context.getString(R.string.tier_four_reward_min)));
            default:
                return 0;
        }
    }

    private void recordRewardClaim(int viewId, double finalRewardValue) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Date date = new Date();
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String strDate = dateFormat.format(date);

        if (viewId == R.id.daily_free_reward) {
            dailyRewardClaimed = true;
            editor.putString(context.getString(R.string.daily_free_reward), strDate);
            editor.putString("freerewardedValue", finalRewardValue + "");
        } else if (viewId == R.id.daily_5k_reward) {
            fivekRewardClaimed = true;
            editor.putString(context.getString(R.string.daily_5k_reward), strDate);
            editor.putString("5krewardedValue", finalRewardValue + "");
        } else if (viewId == R.id.daily_7k_reward) {
            sevenkRewardClaimed = true;
            editor.putString(context.getString(R.string.daily_7k_reward), strDate);
            editor.putString("7krewardedValue", finalRewardValue + "");
        } else if (viewId == R.id.daily_10k_reward) {
            tenkRewardClaimed = true;
            editor.putString(context.getString(R.string.daily_10k_reward), strDate);
            editor.putString("10krewardedValue", finalRewardValue + "");
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
                freeRewardButton.clearAnimation();
            } else if (freeRewardButton.getAnimation() == null || !freeRewardButton.getAnimation().hasStarted()) {
                freeRewardButton.setAnimation(scaler);
            }
            if (fivekRewardClaimed) {
                fivekRewardButton.clearAnimation();
            } else if (stepCount >= activityMilestoneOne
                    && (fivekRewardButton.getAnimation() == null || !fivekRewardButton.getAnimation().hasStarted())) {
                fivekRewardButton.setAnimation(scaler);
            }
            if (sevenkRewardClaimed) {
                sevenkRewardButton.clearAnimation();
            } else if (stepCount >= activityMilestoneTwo
                    && (sevenkRewardButton.getAnimation() == null || !sevenkRewardButton.getAnimation().hasStarted())) {
                sevenkRewardButton.setAnimation(scaler);
            }
            if (tenkRewardClaimed) {
                tenkRewardButton.clearAnimation();
            } else if (stepCount >= activityMilestoneThree
                    && (tenkRewardButton.getAnimation() == null || !tenkRewardButton.getAnimation().hasStarted())) {
                tenkRewardButton.setAnimation(scaler);
            }
        }
    }

    private float generateRandomVal(float max, float min) {
        Random rand = new Random();
        float finalVal = rand.nextFloat() * (max - min) + min;
        for (int i = 0; i < 5; i++) {
            float randomVal = rand.nextFloat() * (max - min) + min;
            if (randomVal < finalVal) {
                finalVal = randomVal;
            }
        }
        if (finalVal < min) finalVal = min;
        if (finalVal > max) finalVal = max;
        return finalVal;
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
