package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.browser.customtabs.CustomTabsIntent;

import android.net.Uri;

/**
 * Handles UI utilities: animations, slide effects, dialogs, battery optimization notices,
 * and common UI patterns used across MainActivity.
 * Extracted from MainActivity to reduce class size.
 */
public class UiHelper {

    private static final String TAG = MainActivity.TAG;

    private final Context context;
    private final Activity activity;
    private final SharedPreferences sharedPreferences;

    private RotateAnimation rotate;
    private ScaleAnimation scaler;

    public UiHelper(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
        this.sharedPreferences = context.getSharedPreferences("actifitSets", Context.MODE_PRIVATE);
    }

    public void initializeAnimations() {
        rotate = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(2000);
        rotate.setInterpolator(new LinearInterpolator());

        scaler = new ScaleAnimation(1f, 0.95f, 1f, 0.95f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaler.setDuration(400);
        scaler.setRepeatMode(Animation.REVERSE);
        scaler.setRepeatCount(Animation.INFINITE);
    }

    public RotateAnimation getRotateAnimation() { return rotate; }
    public ScaleAnimation getScalerAnimation() { return scaler; }

    public void slideRight(View view) {
        view.setVisibility(View.VISIBLE);
        TranslateAnimation animate = new TranslateAnimation(view.getWidth(), 0, 0, 0);
        animate.setDuration(500);
        animate.setFillAfter(true);
        view.startAnimation(animate);
    }

    public void slideLeft(View view) {
        view.setVisibility(View.GONE);
        TranslateAnimation animate = new TranslateAnimation(0, view.getWidth(), 0, 0);
        animate.setDuration(500);
        animate.setFillAfter(true);
        view.startAnimation(animate);
    }

    public void showBatteryNotice() {
        String msg = context.getString(R.string.device_ignore_battery_optimization);
        msg += context.getString(R.string.device_app_launch);

        AlertDialog.Builder batteryDialogBuilder = new AlertDialog.Builder(context);
        AlertDialog.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case AlertDialog.BUTTON_POSITIVE:
                    break;
                case AlertDialog.BUTTON_NEUTRAL:
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean(context.getString(R.string.donotshowbatteryoptimization), true);
                    editor.commit();
                    break;
            }
        };

        AlertDialog pointer = batteryDialogBuilder.setMessage(Html.fromHtml(msg))
                .setTitle(context.getString(R.string.battery_optimization_setting))
                .setIcon(context.getResources().getDrawable(R.drawable.actifit_logo))
                .setPositiveButton(context.getString(R.string.close_button), dialogClickListener)
                .setNeutralButton(context.getString(R.string.do_not_show_again), dialogClickListener)
                .create();
        batteryDialogBuilder.show();
    }

    public void checkBatteryOptimization(Boolean forceShow, TextView batteryNotif) {
        Boolean skipShowingRewards = sharedPreferences.getBoolean(context.getString(R.string.donotshowbatteryoptimization), false);
        String dataTrackingSystem = sharedPreferences.getString("dataTrackingSystem", context.getString(R.string.device_tracking_ntt));
        if (dataTrackingSystem.equals(context.getString(R.string.fitbit_tracking_ntt))) {
            skipShowingRewards = true;
        }
        if (forceShow) skipShowingRewards = false;

        android.os.PowerManager pm = (android.os.PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations("io.actifit.fitnesstracker.actifitfitnesstracker")) {
            batteryNotif.setVisibility(View.VISIBLE);
            batteryNotif.setOnClickListener(view -> showBatteryNotice());
            if (skipShowingRewards) return;
            showBatteryNotice();
        } else {
            batteryNotif.setVisibility(View.GONE);
        }
    }

    public void openUserAccount() {
        String username = sharedPreferences.getString("actifitUser", "");
        if (!username.isEmpty()) {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setToolbarColor(context.getResources().getColor(R.color.actifitRed));
            builder.setStartAnimations(context, R.anim.slide_in_right, R.anim.slide_out_left);
            builder.setExitAnimations(context, android.R.anim.slide_in_left, android.R.anim.slide_out_right);
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.launchUrl(context, Uri.parse(MainActivity.ACTIFIT_CORE_URL + '/' + username));
        }
    }

    public void openUserRank() {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setToolbarColor(context.getResources().getColor(R.color.actifitRed));
        builder.setStartAnimations(context, R.anim.slide_in_right, R.anim.slide_out_left);
        builder.setExitAnimations(context, android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        CustomTabsIntent customTabsIntent = builder.build();
        customTabsIntent.launchUrl(context, Uri.parse(MainActivity.ACTIFIT_RANK_URL));
    }

    public void showEarningsPanel(String msg) {
        AlertDialog.Builder earningsDialogBuilder = new AlertDialog.Builder(context);
        AlertDialog.OnClickListener dialogClickListener = (dialog, which) -> {};

        AlertDialog earningsDialog = earningsDialogBuilder.setMessage(Html.fromHtml(msg))
                .setTitle(context.getString(R.string.earnings_pane_title))
                .setIcon(context.getResources().getDrawable(R.drawable.actifit_logo))
                .setPositiveButton(context.getString(R.string.close_button), dialogClickListener)
                .create();
        earningsDialogBuilder.show();
    }

    public void showGadgetsDialog() {
        AlertDialog.Builder gadgetsDialogBuilder = new AlertDialog.Builder(context);
        AlertDialog.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case AlertDialog.BUTTON_NEUTRAL:
                    // Navigate to market
                    break;
            }
        };

        String msg = "";
        msg += context.getString(R.string.active_gadgets_note_2) + "<br />";
        msg += context.getString(R.string.active_gadgets_note_3) + "<br />";

        AlertDialog gadgetsDialog = gadgetsDialogBuilder.setMessage(Html.fromHtml(msg))
                .setTitle(context.getString(R.string.gadgets_earning_title))
                .setIcon(context.getResources().getDrawable(R.drawable.actifit_logo))
                .setNeutralButton(context.getString(R.string.head_market), dialogClickListener)
                .setPositiveButton(context.getString(R.string.close_button), dialogClickListener)
                .create();
        gadgetsDialogBuilder.show();
    }

    public void showAfitBuyDialog(java.util.List<String> marketItems, java.util.List<String> marketLinks) {
        AlertDialog.Builder afitBuyDialogBuilder = new AlertDialog.Builder(context);
        AlertDialog.OnClickListener dialogClickListener = (dialog, which) -> {};

        CharSequence[] marketBtns = marketItems.toArray(new CharSequence[0]);

        AlertDialog afitBuyDialog = afitBuyDialogBuilder
                .setTitle(context.getString(R.string.afit_buy_title))
                .setIcon(context.getResources().getDrawable(R.drawable.actifit_logo))
                .setPositiveButton(context.getString(R.string.close_button), dialogClickListener)
                .setItems(marketBtns, (dialog, which) -> {
                    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                    builder.setToolbarColor(context.getResources().getColor(R.color.actifitRed));
                    builder.setStartAnimations(context, R.anim.slide_in_right, R.anim.slide_out_left);
                    builder.setExitAnimations(context, android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                    CustomTabsIntent customTabsIntent = builder.build();
                    customTabsIntent.launchUrl(context, Uri.parse(marketLinks.get(which)));
                })
                .create();
        afitBuyDialogBuilder.show();
    }

    public void showReferFriendDialog(String username, View referLayout, EditText refLink, TextView successfulReferral, int referralCount) {
        AlertDialog.Builder referDialogBuilder = new AlertDialog.Builder(context);
        refLink.setText(context.getString(R.string.referrals_format) + username);

        TextView referralDescription = referLayout.findViewById(R.id.referral_description);
        referralDescription.setText(Html.fromHtml(context.getString(R.string.referrals_details)));

        if (referralCount > 0) {
            successfulReferral.setTextColor(context.getResources().getColor(R.color.actifitDarkGreen));
            successfulReferral.setText(Html.fromHtml("&#10003;" + referralCount));
        }

        TextView copyButton = referLayout.findViewById(R.id.copyButton);
        TextView shareButton = referLayout.findViewById(R.id.shareButton);

        AlertDialog pointer = referDialogBuilder.setView(referLayout)
                .setTitle(context.getString(R.string.referrals_note))
                .setIcon(context.getResources().getDrawable(R.drawable.actifit_logo))
                .setPositiveButton(context.getString(R.string.close_button), null)
                .create();
        referDialogBuilder.show();
    }

    public void displayDate(TextView dateTextView) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE, MMM dd, yyyy", java.util.Locale.getDefault());
        dateTextView.setText(sdf.format(new java.util.Date()));
    }
}
