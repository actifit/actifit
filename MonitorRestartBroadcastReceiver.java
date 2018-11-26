package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * class handles the re-initiation of the activity sensor monitor in case it was destroyed
 */
public class MonitorRestartBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        /*System.out.println(MonitorRestartBroadcastReceiver.class.getSimpleName() + " Service destroyed!");
        context.startService(new Intent(context, ActivityMonitorService.class));;*/
    }
}
