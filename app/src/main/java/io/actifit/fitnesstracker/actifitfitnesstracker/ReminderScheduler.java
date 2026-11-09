package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

/**
 * Schedules the daily post reminder as a one-shot alarm that re-arms itself each time it fires
 * (and after a reboot). setRepeating was inexact and deferred in Doze, so reminders came late or
 * not at all; setAndAllowWhileIdle still fires in Doze and needs no exact-alarm permission.
 */
final class ReminderScheduler {

    static final String ACTION_REMIND = "io.actifit.fitnesstracker.DAILY_POST_REMINDER";

    private ReminderScheduler() {}

    /** Arms the next reminder from the saved hour/minute, or cancels it if none is set. */
    static void scheduleFromPrefs(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("actifitSets", Context.MODE_PRIVATE);
        try {
            int hour = Integer.parseInt(prefs.getString("selectedReminderHour", ""));
            int minute = Integer.parseInt(prefs.getString("selectedReminderMin", ""));
            schedule(ctx, hour, minute);
        } catch (NumberFormatException noReminderSet) {
            cancel(ctx);
        }
    }

    static void schedule(Context ctx, int hour, int minute) {
        cancelLegacyAlarm(ctx);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        // a time that has already passed today means tomorrow, not an immediate reminder
        if (!next.after(Calendar.getInstance())) {
            next.add(Calendar.DATE, 1);
        }
        alarmManager(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(),
                reminderIntent(ctx));
    }

    static void cancel(Context ctx) {
        cancelLegacyAlarm(ctx);
        Intent intent = new Intent(ctx, ReminderNotificationService.class).setAction(ACTION_REMIND);
        PendingIntent existing = PendingIntent.getBroadcast(ctx, ReminderNotificationService.NOTIFICATION_ID,
                intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        if (existing != null) {
            alarmManager(ctx).cancel(existing);
            existing.cancel();
        }
    }

    static boolean isEnabled(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("actifitSets", Context.MODE_PRIVATE);
        return !prefs.getString("selectedReminderHour", "").isEmpty();
    }

    private static PendingIntent reminderIntent(Context ctx) {
        Intent intent = new Intent(ctx, ReminderNotificationService.class).setAction(ACTION_REMIND);
        return PendingIntent.getBroadcast(ctx, ReminderNotificationService.NOTIFICATION_ID, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // Versions up to 0.15.2 set a daily repeating alarm with request code 0 and no action.
    // A PendingIntent lookup also matches on the mutability flag it was created with:
    // FLAG_MUTABLE on Android 12+, none before that.
    @android.annotation.SuppressLint("UnspecifiedImmutableFlag")
    private static void cancelLegacyAlarm(Context ctx) {
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_NO_CREATE
                : PendingIntent.FLAG_NO_CREATE;
        PendingIntent legacy = PendingIntent.getBroadcast(ctx, 0,
                new Intent(ctx, ReminderNotificationService.class), flags);
        if (legacy != null) {
            alarmManager(ctx).cancel(legacy);
            legacy.cancel();
        }
    }

    private static AlarmManager alarmManager(Context ctx) {
        return (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    }
}
