package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/**
 * Home-screen widget: today's steps as a ring towards the daily goal, plus the streak.
 * All rendering happens in {@link WidgetUpdater} off the main thread; the callbacks here only ask it
 * to refresh. Deliberately not WorkManager based: enqueueing work toggles WorkManager's
 * RescheduleReceiver component, which broadcasts PACKAGE_CHANGED and re-triggers APPWIDGET_UPDATE.
 */
public class StepsWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetUpdater.requestUpdate(context, goAsync());
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                          int appWidgetId, Bundle newOptions) {
        // pre-Android 12 the layout is picked from the reported size, so a resize needs a re-render
        WidgetUpdater.requestUpdate(context, goAsync());
    }

    @Override
    public void onEnabled(Context context) {
        WidgetUpdater.setActive(context, true);
    }

    @Override
    public void onDisabled(Context context) {
        WidgetUpdater.setActive(context, false);
    }
}
