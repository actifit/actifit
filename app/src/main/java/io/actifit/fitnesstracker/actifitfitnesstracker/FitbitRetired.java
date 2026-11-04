package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

/**
 * Google turned off the legacy Fitbit Web API (Oct 30 2026), so the in-app Fitbit login can no
 * longer work. Fitbit data still reaches Health Connect through the Google Health app, so we point
 * Fitbit users there instead of starting the dead login flow.
 */
final class FitbitRetired {

    // show the dashboard reminder once per app launch, not on every resume
    static boolean shownThisLaunch = false;

    private FitbitRetired() {}

    static void show(Context ctx, Runnable onSwitch) {
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.fitbit_retired_title)
                .setMessage(R.string.fitbit_retired_msg)
                .setPositiveButton(R.string.fitbit_retired_switch, (d, w) -> onSwitch.run())
                .setNegativeButton(R.string.fitbit_retired_later, null)
                .show();
    }
}
