package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;

import java.util.Calendar;


/**
 * This class is a service that runs in the background and handles tracking and storing of
 * movement to avoid losing track if Actifit is running instead
 */

public class ActivityMonitorService extends Service implements SensorEventListener, StepListener {


    private StepsDBHelper mStepsDBHelper;
    private Sensor accSensor;
    public static SensorManager sensorManager;
    private SimpleStepDetector simpleStepDetector;
    private int notificationID = 1;
    private NotificationCompat.Builder mBuilder;
    private NotificationManagerCompat notificationManager;

    private SharedPreferences sharedPreferences;
    private static PowerManager pm;
    private static PowerManager.WakeLock wl;

    public static PowerManager getPowerManagerInstance(){
        return pm;
    }

    public static PowerManager.WakeLock getWakeLockInstance(){
        return wl;
    }

    public ActivityMonitorService(Context applicationContext) {
        super();
        System.out.println(">>>>[Actifit]here I am!");
    }

    public ActivityMonitorService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.actifit_notif_channel);
            String description = getString(R.string.actifit_notif_description);
            //making a fix for no sound in Android 8
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(
                    getString(R.string.actifit_channel_ID), name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }


    @Override
    public void onCreate(){


        //initialize power manager and wake locks either way
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ACTIFIT_SPECIAL_LOCK");

        //check if aggressive background tracking mode is enabled

        sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
        String aggModeEnabled = sharedPreferences.getString("aggressiveBackgroundTracking"
                ,getString(R.string.aggr_back_tracking_off));

        //enable wake lock to ensure tracking functions in the background
        if (aggModeEnabled.equals(getString(R.string.aggr_back_tracking_on))) {
            System.out.println(">>>>[Actifit]AGG MODE ON");
            wl.acquire();
        }

        mStepsDBHelper = new StepsDBHelper(this);
        // Get an instance of the SensorManager
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        accSensor =
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        //for Android 8, need to initialize notifications first
        createNotificationChannel();

        //initiate step detector and start tracking
        simpleStepDetector = new SimpleStepDetector();
        simpleStepDetector.registerListener(this);

        sensorManager.registerListener(ActivityMonitorService.this, accSensor,
                SensorManager.SENSOR_DELAY_FASTEST);

    }

    /**
     * this is overriding the step function which only works in case of using accelerometer sensor
     *
     * @param timeNs
     */
    @Override
    public void step(long timeNs) {
        int curStepCount = mStepsDBHelper.createStepsEntry();

        //adjust step count display and print to notification activity
        //making sure we have an instance of mBuilder
        if (mBuilder!=null) {
            mBuilder.setContentText(getString(R.string.activity_today_string) + " " + curStepCount);
            notificationManager.notify(notificationID, mBuilder.build());
        }

        //also update main activity
        Intent in = new Intent();
        in.putExtra("move_count",curStepCount);
        in.setAction("ACTIFIT_SERVICE");
        //sendBroadcast(in);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(in);

        //stepDisplay.setText(TEXT_NUM_STEPS + (curStepCount < 0 ? 0 : curStepCount));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * handles the actual counting of steps relying on one of the two sensors,
     * whichever is active
     *
     * @param event
     */
    @Override
    public void onSensorChanged(SensorEvent event) {

            simpleStepDetector.updateAccel(
                    event.timestamp, event.values[0], event.values[1], event.values[2]);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);


        //grab activity count so far today
        int curActivityCount = mStepsDBHelper.fetchTodayStepCount();

        //create the service that will display as a notification on screen lock
        Intent notificationIntent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        mBuilder = new
                NotificationCompat.Builder(this, getString(R.string.actifit_channel_ID))
                .setContentTitle("Actifit Report")
                .setContentText(getString(R.string.activity_today_string)+" "+(curActivityCount<0?0:curActivityCount))
                .setSmallIcon(R.drawable.actifit_logo)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true);

        notificationManager = NotificationManagerCompat.from(this);

        startForeground(notificationID,mBuilder.build());

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        System.out.println(">>>>[Actifit]ondestroy service!");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        }
        sensorManager.unregisterListener(ActivityMonitorService.this);

        String aggModeEnabled = sharedPreferences.getString("aggressiveBackgroundTracking"
                ,getString(R.string.aggr_back_tracking_off));

        //release wake lock now
        if (aggModeEnabled.equals(getString(R.string.aggr_back_tracking_on))) {
            System.out.println(">>>>[Actifit]AGG MODE ON - RELEASING LOCK");
            pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ACTIFIT_SPECIAL_LOCK");
            if (wl.isHeld()) {
                wl.release();
            }
        }
        //service destroyed, let's start it again
        //Intent broadcastIntent = new Intent(".MonitorRestart");
       // sendBroadcast(broadcastIntent);

    }

}
