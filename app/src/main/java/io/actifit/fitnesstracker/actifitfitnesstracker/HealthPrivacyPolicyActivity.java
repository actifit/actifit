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
