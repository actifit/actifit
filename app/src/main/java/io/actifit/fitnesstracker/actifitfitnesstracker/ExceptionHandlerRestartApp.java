package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;


/*
 * Class handles enabling capture of all Actifit sudden crashes, and then restarting it from main activity
 *
 * */
public class ExceptionHandlerRestartApp implements Thread.UncaughtExceptionHandler {
    private Activity activity;

    public ExceptionHandlerRestartApp(Activity a) {
        activity = a;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {

        Intent intent = new Intent(activity, MainActivity.class);
        intent.putExtra("crash", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getActivity(ActifitApplication.getInstance().getBaseContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        }else {
            pendingIntent = PendingIntent.getActivity(ActifitApplication.getInstance().getBaseContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);
        }
        AlarmManager mgr = (AlarmManager) ActifitApplication.getInstance().getBaseContext().getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent);


        activity.finish();
        System.exit(2);
    }
}
