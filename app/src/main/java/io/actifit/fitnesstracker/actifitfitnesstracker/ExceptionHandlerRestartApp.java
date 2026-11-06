package io.actifit.fitnesstracker.actifitfitnesstracker;

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
    // the handler installed before ours (Crashlytics, then the system one): it reports the
    // crash and ends the process
    private final Thread.UncaughtExceptionHandler previous;

    public ExceptionHandlerRestartApp() {
        // MainActivity installs this on every onCreate; don't chain onto our own earlier copy
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        previous = current instanceof ExceptionHandlerRestartApp
                ? ((ExceptionHandlerRestartApp) current).previous : current;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        try {
            scheduleRestart();
        } catch (Throwable ignored) {
            // never let the restart attempt stop the crash from being reported
        }
        // finishing the activity from a background thread could hang (ANR); let the previous
        // handler report the crash and kill the process. The restart alarm may be blocked by
        // background-activity-start limits on Android 10+.
        if (previous != null) {
            previous.uncaughtException(thread, ex);
        } else {
            System.exit(2);
        }
    }

    private void scheduleRestart() {

        Intent intent = new Intent(ActifitApplication.getInstance().getBaseContext(), MainActivity.class);
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

    }
}
