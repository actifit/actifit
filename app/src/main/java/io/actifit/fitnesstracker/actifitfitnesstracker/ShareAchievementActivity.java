package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ShareAchievementActivity extends BaseActivity {

    // Render the card at (at least) this width in px so shared images stay crisp on social,
    // independent of the on-screen dp size.
    private static final int SHARE_RENDER_WIDTH = 1080;

    private View cardContainer, rewardsPill;
    private TextView stepsValue, stepCountLabel, achievementTitle, rankValue, afitValue, dateValue, userHandle;
    private ImageView userProfilePic;
    private Button shareButton;
    private ProgressBar progressBar;
    private View periodToggle;
    private TextView periodToday, periodWeek;

    private int dailySteps = 0;
    private int weeklySteps = 0;
    private boolean hasWeekly = false;
    private boolean showingWeekly = false;
    private String afitStr = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_achievement);

        // Bind views
        cardContainer = findViewById(R.id.achievement_card_container);
        stepsValue = findViewById(R.id.step_count_value);
        stepCountLabel = findViewById(R.id.step_count_label);
        achievementTitle = findViewById(R.id.achievement_title);
        rankValue = findViewById(R.id.user_rank_value);
        afitValue = findViewById(R.id.afit_value_card);
        rewardsPill = findViewById(R.id.rewards_pill);
        dateValue = findViewById(R.id.achievement_date);
        userHandle = findViewById(R.id.user_handle_card);
        userProfilePic = findViewById(R.id.user_profile_pic_card);
        shareButton = findViewById(R.id.share_button);
        progressBar = findViewById(R.id.share_progress);
        periodToggle = findViewById(R.id.period_toggle);
        periodToday = findViewById(R.id.period_today);
        periodWeek = findViewById(R.id.period_week);

        // Populate data from Intent
        Intent intent = getIntent();
        dailySteps = parseIntSafe(intent.getStringExtra("steps"));
        weeklySteps = parseIntSafe(intent.getStringExtra("weekly_steps"));
        hasWeekly = intent.hasExtra("weekly_steps") && weeklySteps > 0;
        afitStr = intent.getStringExtra("afit");
        String rank = intent.getStringExtra("rank");
        String username = intent.getStringExtra("username");

        rankValue.setText("Rank: " + (rank != null ? rank : "0.0") + "/100");
        userHandle.setText("@" + (username != null ? username : "username"));

        // AFIT balance pill — show only when a real balance was supplied. This is the user's
        // TOTAL AFIT holdings (a lifetime flex), not per-period earnings.
        double afitBal = parseDoubleSafe(afitStr);
        if (afitStr != null && !afitStr.isEmpty() && afitBal > 0) {
            // Labelled clearly as a total balance (it's lifetime holdings, not period earnings)
            afitValue.setText("Total: " + formatAfit(afitBal) + " AFIT");
            rewardsPill.setVisibility(View.VISIBLE);
        } else {
            rewardsPill.setVisibility(View.GONE);
        }

        // Load profile pic
        if (username != null && !username.isEmpty()) {
            String userImgUrl = getString(R.string.hive_image_host_url).replace("USERNAME", username);
            Glide.with(this)
                    .load(userImgUrl)
                    .placeholder(R.drawable.actifit_logo)
                    .error(R.drawable.actifit_logo)
                    .into(userProfilePic);
        }

        dateValue.setText(new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(new Date()));

        // Daily / weekly toggle — only when a weekly figure is available
        if (hasWeekly) {
            periodToggle.setVisibility(View.VISIBLE);
            periodToday.setOnClickListener(v -> selectPeriod(false));
            periodWeek.setOnClickListener(v -> selectPeriod(true));
            selectPeriod(false);
        } else {
            periodToggle.setVisibility(View.GONE);
            renderPeriod(false);
        }

        shareButton.setOnClickListener(v -> shareAsImage());
    }

    /** Style the segmented toggle for the selected period and refresh the card. */
    private void selectPeriod(boolean weekly) {
        int red = getResources().getColor(R.color.actifitRed);
        int white = getResources().getColor(R.color.colorWhite);
        int idleBg = 0xFFEEEEEE;
        int idleText = 0xFF444444;
        periodToday.setBackgroundColor(weekly ? idleBg : red);
        periodToday.setTextColor(weekly ? idleText : white);
        periodWeek.setBackgroundColor(weekly ? red : idleBg);
        periodWeek.setTextColor(weekly ? white : idleText);
        renderPeriod(weekly);
    }

    /** Update the card text for the selected period (daily vs weekly). */
    private void renderPeriod(boolean weekly) {
        showingWeekly = weekly;
        int steps = weekly ? weeklySteps : dailySteps;
        stepsValue.setText(NumberFormat.getInstance(Locale.getDefault()).format(steps));
        // Keep the badge OFF the giant number (it wraps it to a second line); put it on the label.
        String badge = steps >= 10000 ? " 🏆" : steps >= 5000 ? " 🔥" : "";
        String label = weekly ? getString(R.string.share_steps_week) : getString(R.string.share_steps_today);
        stepCountLabel.setText(label + badge);
        achievementTitle.setText(weekly ? getString(R.string.share_title_weekly) : getString(R.string.share_title_daily));
    }

    /** Compact AFIT formatting: whole thousands get grouping and no decimals; small balances keep 2. */
    private String formatAfit(double v) {
        NumberFormat nf = NumberFormat.getInstance(Locale.getDefault());
        if (v >= 1000) {
            nf.setMaximumFractionDigits(0);
        } else {
            nf.setMaximumFractionDigits(2);
        }
        return nf.format(v);
    }

    private int parseIntSafe(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseDoubleSafe(String s) {
        try {
            return s == null ? 0 : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void shareAsImage() {
        progressBar.setVisibility(View.VISIBLE);
        shareButton.setEnabled(false);

        try {
            int w = cardContainer.getWidth();
            int h = cardContainer.getHeight();
            if (w <= 0 || h <= 0) {
                Toast.makeText(this, "Card not ready yet, try again", Toast.LENGTH_SHORT).show();
                return;
            }

            // Render at higher resolution: text and shapes re-rasterize crisply at the scaled
            // matrix, so social shares look sharp rather than upscaled screen captures.
            float scale = Math.max(1f, (float) SHARE_RENDER_WIDTH / w);
            Bitmap bitmap = Bitmap.createBitmap(Math.round(w * scale), Math.round(h * scale), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            canvas.scale(scale, scale);
            cardContainer.draw(canvas);

            // Save bitmap to the FileProvider-shared cache/images dir
            File cachePath = new File(getCacheDir(), "images");
            cachePath.mkdirs();
            File imageFile = new File(cachePath, "actifit_progress.png");
            FileOutputStream stream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();
            bitmap.recycle();

            Uri contentUri = FileProvider.getUriForFile(this, "io.actifit.fileprovider", imageFile);
            if (contentUri != null) {
                int steps = showingWeekly ? weeklySteps : dailySteps;
                String period = showingWeekly ? "this week" : "today";
                boolean hasAfit = afitStr != null && !afitStr.isEmpty() && parseDoubleSafe(afitStr) > 0;
                String text = "I racked up " + NumberFormat.getInstance(Locale.getDefault()).format(steps)
                        + " steps " + period + " with @actifit"
                        + (hasAfit ? " and I'm earning AFIT rewards" : "")
                        + "! 💪 Move-to-earn on Hive: https://actifit.io #move2earn #fitness #crypto";

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.setDataAndType(contentUri, getContentResolver().getType(contentUri));
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(shareIntent, "Share progress via"));
            }

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to generate share image", Toast.LENGTH_SHORT).show();
        } finally {
            progressBar.setVisibility(View.GONE);
            shareButton.setEnabled(true);
        }
    }
}
