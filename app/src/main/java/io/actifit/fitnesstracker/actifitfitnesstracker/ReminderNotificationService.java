package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Class handles sending out reminder notifications in case user sets them
 */
public class ReminderNotificationService extends BroadcastReceiver {
    public static final int NOTIFICATION_ID = 2194143;


    @Override
    public void onReceive(Context context, Intent intent) {

        //let's check first if user had posted already so as to avoid sending a useless reminder
        SharedPreferences sharedPreferences = context.getSharedPreferences("actifitSets", context.MODE_PRIVATE);
        String lastPostDate = sharedPreferences.getString("actifitLastPostDate","");
        String currentDate = new SimpleDateFormat("yyyyMMdd").format(
                Calendar.getInstance().getTime());

        if (!lastPostDate.equals("")) {
            if (Integer.parseInt(lastPostDate) >= Integer.parseInt(currentDate)) {
                //do nothing as the user has already posted today
                return;
            }
        }

        //alternate path, let's notify

        //support for Android 8+
        createNotificationChannel(context);

        Intent notifyIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getActivity(context, 0, notifyIntent, PendingIntent.FLAG_IMMUTABLE);
        }else{
            pendingIntent = PendingIntent.getActivity(context, 0, notifyIntent, 0);
        }
        //prepare notification details
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                context.getString(R.string.actifit_channel_remind_ID))
                .setContentTitle(context.getString(R.string.daily_post_reminder_title))
                .setContentText(context.getString(R.string.daily_post_reminder))
                .setSmallIcon(R.drawable.actifit_logo)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        Notification notificationCompat = builder.build();

        //proceed notifying user
        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);
        managerCompat.notify(NOTIFICATION_ID, notificationCompat);

        //also launch Actifit & its tracking service
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }


    private void createNotificationChannel(Context context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.actifit_notif_channel);
            String description = context.getString(R.string.actifit_notif_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(
                    context.getString(R.string.actifit_channel_remind_ID), name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

}
