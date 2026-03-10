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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ShareAchievementActivity extends BaseActivity {

    private View cardContainer;
    private TextView stepsValue, rankValue, dateValue, userHandle;
    private ImageView userProfilePic;
    private Button shareButton;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_achievement);

        // Bind views
        cardContainer = findViewById(R.id.achievement_card_container);
        stepsValue = findViewById(R.id.step_count_value);
        rankValue = findViewById(R.id.user_rank_value);
        dateValue = findViewById(R.id.achievement_date);
        userHandle = findViewById(R.id.user_handle_card);
        userProfilePic = findViewById(R.id.user_profile_pic_card);
        shareButton = findViewById(R.id.share_button);
        progressBar = findViewById(R.id.share_progress);

        // Populate data from Intent
        Intent intent = getIntent();
        String stepsStr = intent.getStringExtra("steps");
        String rank = intent.getStringExtra("rank");
        String username = intent.getStringExtra("username");

        int steps = 0;
        try {
            steps = Integer.parseInt(stepsStr != null ? stepsStr : "0");
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        // Add motivational emojis
        String stepsDisplay = stepsStr != null ? stepsStr : "0";
        if (steps >= 10000) {
            stepsDisplay += " 🏆";
        } else if (steps >= 5000) {
            stepsDisplay += " 🔥";
        }
        
        stepsValue.setText(stepsDisplay);
        rankValue.setText("Rank: " + (rank != null ? rank : "0.0") + "/100");
        userHandle.setText("@" + (username != null ? username : "username"));

        // Load profile pic
        if (username != null && !username.isEmpty()) {
            String userImgUrl = getString(R.string.hive_image_host_url).replace("USERNAME", username);
            Glide.with(this)
                    .load(userImgUrl)
                    .placeholder(R.drawable.actifit_logo)
                    .error(R.drawable.actifit_logo)
                    .into(userProfilePic);
        }
        
        String currentDate = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(new Date());
        dateValue.setText(currentDate);

        shareButton.setOnClickListener(v -> shareAsImage());
    }

    private void shareAsImage() {
        progressBar.setVisibility(View.VISIBLE);
        shareButton.setEnabled(false);

        // Capture the layout as a bitmap
        Bitmap bitmap = Bitmap.createBitmap(cardContainer.getWidth(), cardContainer.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        cardContainer.draw(canvas);

        try {
            // Save bitmap to temporary file
            File cachePath = new File(getCacheDir(), "images");
            cachePath.mkdirs();
            File imageFile = new File(cachePath, "actifit_achievement.png");
            FileOutputStream stream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            // Get URI using FileProvider
            Uri contentUri = FileProvider.getUriForFile(this, "io.actifit.fileprovider", imageFile);

            if (contentUri != null) {
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.setDataAndType(contentUri, getContentResolver().getType(contentUri));
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.putExtra(Intent.EXTRA_TEXT, "I hit " + stepsValue.getText() + " steps today with @actifit! #fitness #crypto");
                startActivity(Intent.createChooser(shareIntent, "Share Achievement via"));
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
