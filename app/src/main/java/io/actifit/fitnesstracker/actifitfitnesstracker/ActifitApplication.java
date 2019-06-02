package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Application;
import android.content.Context;

/*
 * Class handles providing an instance of our running Actifit app
 *
 * */
public class ActifitApplication extends Application {

    public static ActifitApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
    @Override
    public Context getApplicationContext() {
        return super.getApplicationContext();
    }
    public static ActifitApplication getInstance() {
        return instance;
    }
}
