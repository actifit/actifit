package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Date;

public class ResetPieChart extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(MainActivity.TAG, "Broadcast received at " + new Date());
        SharedPreferences sharedPreferences = context.getSharedPreferences("actifitSets",Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("resetPieChart", true);
        Log.d(MainActivity.TAG, "resetting pie chart"+sharedPreferences.getBoolean("resetPieChart", false));
        editor.apply();
    }
}