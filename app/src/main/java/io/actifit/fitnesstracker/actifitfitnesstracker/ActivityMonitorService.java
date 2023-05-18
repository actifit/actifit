package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Notification;
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
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;


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
    private int tenknotificationID = 446878874;
    private int fiveknotificationID = 574687813;
    private NotificationCompat.Builder mBuilder;
    private NotificationManagerCompat notificationManager;

    private SharedPreferences sharedPreferences;
    private static PowerManager pm;
    private static PowerManager.WakeLock wl;

    private static boolean fivekmilestone = false;
    private static boolean tenkmilestone = false;
    private final int fivekValMilestone = 5000;
    private final int tenkValMilestone = 10000;

    public static PowerManager getPowerManagerInstance(){
        return pm;
    }

    public static PowerManager.WakeLock getWakeLockInstance(){
        return wl;
    }

    public ActivityMonitorService(Context applicationContext) {
        super();
        Log.d(MainActivity.TAG,">>>>[Actifit]here I am!");
    }

    public ActivityMonitorService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel(String channel_id) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.actifit_notif_channel);
            String description = getString(R.string.actifit_notif_description);
            //making a fix for no sound in Android 8
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(
                    channel_id, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }


    @Override
    public void onCreate(){
        CreateAsyncTask createAsyncTask = new CreateAsyncTask();
        createAsyncTask.execute();

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

        boolean doNotify = false;
        //if user crossed 5k, or 10k, also send out another notification
        if (curStepCount >= tenkValMilestone && !tenkmilestone){
            doNotify = true;
            tenkmilestone = true;
        }else if (curStepCount >= fivekValMilestone && !fivekmilestone){
            doNotify = true;
            fivekmilestone = true;
        }else if (curStepCount < fivekValMilestone){
            //reset notification status
            tenkmilestone = false;
            fivekmilestone = false;
        }

        if (doNotify) {
            Context context = getApplicationContext();

            int notID = tenknotificationID;
            String notText = context.getString(R.string.activity_today_tenk_milestone);
            //support for Android 8+
            if (tenkmilestone){
                createNotificationChannel(getString(R.string.actifit_channel_10k_notice));
            }else{
                notID = fiveknotificationID;
                notText = context.getString(R.string.activity_today_fivek_milestone);
                createNotificationChannel(getString(R.string.actifit_channel_5k_notice));
            }

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
                    .setContentText(notText)
                    .setSmallIcon(R.drawable.actifit_logo)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            Notification notificationCompat = builder.build();

            //proceed notifying user
            NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);
            managerCompat.notify(notID, notificationCompat);
        }
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
        MonitorAsyncTask updateTask = new MonitorAsyncTask();
        updateTask.execute(event);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        InitializeAsyncTask initNotif = new InitializeAsyncTask();
        initNotif.execute();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(MainActivity.TAG,">>>>[Actifit]ondestroy service!");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        }

        try {
            sensorManager.unregisterListener(ActivityMonitorService.this);
        }catch(Exception e ){
            Log.d(MainActivity.TAG,"error unregisterig listener");
        }
        sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
        String aggModeEnabled = sharedPreferences.getString("aggressiveBackgroundTracking"
                ,getString(R.string.aggr_back_tracking_off_ntt));

        //release wake lock now
        if (aggModeEnabled.equals(getString(R.string.aggr_back_tracking_on_ntt))) {
            Log.d(MainActivity.TAG,">>>>[Actifit]AGG MODE ON - RELEASING LOCK");
            pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getString(R.string.actifit_wake_lock_tag));
            if (wl.isHeld()) {
                wl.release();
            }
        }
        if (mStepsDBHelper != null) {
            mStepsDBHelper.closeConnection();
        }
        //mStepsDBHelper.closeConnection();
        //service destroyed, let's start it again
        //Intent broadcastIntent = new Intent(".MonitorRestart");
       // sendBroadcast(broadcastIntent);

    }

    private class CreateAsyncTask extends AsyncTask<Void, Void, Void>{

        @Override
        protected Void doInBackground(Void... voids) {

            //initialize power manager and wake locks either way
            pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getString(R.string.actifit_wake_lock_tag));

            //check if aggressive background tracking mode is enabled

            sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
            String aggModeEnabled = sharedPreferences.getString("aggressiveBackgroundTracking"
                    ,getString(R.string.aggr_back_tracking_off_ntt));

            //enable wake lock to ensure tracking functions in the background
            if (aggModeEnabled.equals(getString(R.string.aggr_back_tracking_on_ntt))) {
                Log.d(MainActivity.TAG,">>>>[Actifit]AGG MODE ON");
                if (!wl.isHeld()) {
                    wl.acquire();
                }
            }

            mStepsDBHelper = new StepsDBHelper(getApplicationContext());
            // Get an instance of the SensorManager
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

            accSensor =
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

            //for Android 8, need to initialize notifications first
            createNotificationChannel(getString(R.string.actifit_channel_ID));

            //initiate step detector and start tracking
            simpleStepDetector = new SimpleStepDetector();
            simpleStepDetector.registerListener(ActivityMonitorService.this);

            sensorManager.registerListener(ActivityMonitorService.this, accSensor,
                    SensorManager.SENSOR_DELAY_FASTEST);
                    //SensorManager.SENSOR_DELAY_GAME);

            return null;
        }
    }

    private class InitializeAsyncTask extends AsyncTask<Void, Void, Integer> {


        @Override
        protected Integer doInBackground(Void... voids) {
            //grab activity count so far today
            int curActivityCount = mStepsDBHelper.fetchTodayStepCount();
            return curActivityCount;
        }

        @Override
        protected void onPostExecute(Integer curActivityCount) {
            super.onPostExecute(curActivityCount);


            //create the service that will display as a notification on screen lock
            Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);

            PendingIntent pendingIntent;
            if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.S) {
                pendingIntent =
                        PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

            }else {
                pendingIntent =
                        PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);
            }
            mBuilder = new
                    NotificationCompat.Builder(getApplicationContext(), getString(R.string.actifit_channel_ID))
                    .setContentTitle(getString(R.string.actifit_notif_title))
                    .setContentText(getString(R.string.activity_today_string)+" "+(curActivityCount<0?0:curActivityCount))
                    .setSmallIcon(R.drawable.actifit_logo)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOnlyAlertOnce(true);

            notificationManager = NotificationManagerCompat.from(getApplicationContext());

            startForeground(notificationID,mBuilder.build());
        }
    }


    private class MonitorAsyncTask extends AsyncTask<SensorEvent, Void, Void> {


        @Override
        protected Void doInBackground(SensorEvent... event) {
            simpleStepDetector.updateAccel(
                    event[0].timestamp, event[0].values[0], event[0].values[1], event[0].values[2]);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }
    }



}
