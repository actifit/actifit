package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Health Connect on Android 13 and below refuses to show its permission screen unless the app
 * declares an activity for ACTION_SHOW_PERMISSIONS_RATIONALE (the privacy policy link). Without it
 * the permission request closes instantly and nothing is granted.
 *
 * Data read from Health Connect: steps, distance and active calories, and, only when the user
 * turns on "Show my workouts", exercise sessions (workout type, duration and distance). Workouts
 * the user includes in a report are published with that public report. Keep the privacy policy
 * page in step with this list.
 */
public class HealthPrivacyPolicyActivity extends Activity {

    private static final String PRIVACY_URL = "https://actifit.io/privacy-policy";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)));
        } catch (ActivityNotFoundException ignored) {
            // no browser installed; nothing else to show
        }
        finish();
    }
}
