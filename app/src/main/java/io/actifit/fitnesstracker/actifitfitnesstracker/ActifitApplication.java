package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/*
 * Class handles providing an instance of our running Actifit app
 *
 * */
public class ActifitApplication extends Application {

    public static ActifitApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        // open the shared step database early, off the main thread, so the dashboard finds it open
        Context appContext = this;
        new Thread(() -> new StepsDBHelper(appContext), "db-warmup").start();
        instance = this;
        SharedPreferences sharedPreferences = getSharedPreferences("actifitSets", Context.MODE_PRIVATE);
        boolean isDarkModeEnabled = sharedPreferences.getBoolean("theme_mode", false);
        AppCompatDelegate.setDefaultNightMode(
                isDarkModeEnabled ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }
    @Override
    public Context getApplicationContext() {
        return super.getApplicationContext();
    }
    public static ActifitApplication getInstance() {
        return instance;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.setLocale(base));
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleManager.setLocale(this);
    }
}
