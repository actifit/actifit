package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.content.Context;

import androidx.appcompat.app.AlertDialog;

/**
 * Google turned off the legacy Fitbit Web API (Oct 30 2026), so the in-app Fitbit login can no
 * longer work. Fitbit data still reaches Health Connect through the Google Health app, so we point
 * Fitbit users there instead of starting the dead login flow.
 */
final class FitbitRetired {

    // show the dashboard reminder once per app launch, not every time the dashboard reloads
    static boolean shownThisLaunch = false;

    private FitbitRetired() {}

    /** @return false when the dialog could not be shown (activity already going away) */
    static boolean show(Context ctx, int positiveLabel, Runnable onPositive) {
        // callers include async callbacks; a dialog on a dead window throws BadTokenException
        if (ctx instanceof Activity
                && (((Activity) ctx).isFinishing() || ((Activity) ctx).isDestroyed())) {
            return false;
        }
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.fitbit_retired_title)
                .setMessage(R.string.fitbit_retired_msg)
                .setPositiveButton(positiveLabel, (d, w) -> onPositive.run())
                .setNegativeButton(R.string.fitbit_retired_later, null)
                .show();
        return true;
    }
}
