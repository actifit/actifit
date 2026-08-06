package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.utils.ViewPortHandler;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Manages all chart-related functionality: pie charts for step progress,
 * bar charts for hourly/daily activity, and animations.
 * Extracted from MainActivity to reduce class size.
 */
public class ChartManager {

    private static final String TAG = MainActivity.TAG;

    private final Context context;

    private CircularProgressIndicator stepRing;
    private CircularProgressIndicator stepRingFitbit;
    private CircularProgressIndicator stepRingHc;

    private static final int DAILY_GOAL = 10000;

    private BarChart dayChart, fullChart;
    private BarData chartBarData, dayBarData;

    private final int activityMilestoneOne;
    private final int activityMilestoneTwo;
    private final int activityMilestoneThree;

    private View BtnWaves;
    private View BtnPostSteemit;
    private ScaleAnimation scaler;

    private View thirdPartyTracking;
    private View healthConnectTracking;
    private View chartSwitcher;

    public ChartManager(Context context, int milestoneOne, int milestoneTwo, int milestoneThree) {
        this.context = context;
        this.activityMilestoneOne = milestoneOne;
        this.activityMilestoneTwo = milestoneTwo;
        this.activityMilestoneThree = milestoneThree;
    }

    public void setScaler(ScaleAnimation scaler) {
        this.scaler = scaler;
    }

    public void setBtnWaves(View btnWaves) {
        BtnWaves = btnWaves;
    }

    public void setBtnPostSteemit(View btnPostSteemit) {
        BtnPostSteemit = btnPostSteemit;
    }

    public void setThirdPartyTracking(View thirdPartyTracking) {
        this.thirdPartyTracking = thirdPartyTracking;
    }

    public void setHealthConnectTracking(View healthConnectTracking) {
        this.healthConnectTracking = healthConnectTracking;
    }

    public void setChartSwitcher(View chartSwitcher) {
        this.chartSwitcher = chartSwitcher;
    }

    public BarChart getDayChart() {
        return dayChart;
    }

    public BarChart getFullChart() {
        return fullChart;
    }

    public BarData getChartBarData() {
        return chartBarData;
    }

    public BarData getDayBarData() {
        return dayBarData;
    }

    private void updateRing(CircularProgressIndicator ring, android.widget.TextView tvCount,
                            android.widget.TextView tvGoal, android.widget.TextView tvPct,
                            int stepCount, boolean animate) {
        int steps = Math.max(stepCount, 0);
        int progress = Math.min((steps * 100) / DAILY_GOAL, 100);
        int color = steps >= activityMilestoneOne
                ? ContextCompat.getColor(context, R.color.actifitDarkGreen)
                : ContextCompat.getColor(context, R.color.actifitRed);

        ring.setIndicatorColor(color);
        if (animate) {
            ring.setProgressCompat(progress, true);
        } else {
            ring.setProgress(progress, false);
        }

        if (tvCount != null) {
            tvCount.setText(java.text.NumberFormat.getInstance().format(steps));
            tvCount.setTextColor(color);
        }
        if (tvGoal != null) tvGoal.setText(context.getString(R.string.step_goal_format, java.text.NumberFormat.getInstance().format(DAILY_GOAL)));
        if (tvPct != null) tvPct.setText(context.getString(R.string.step_pct_to_goal_format, progress));
    }

    public void displayActivityChart(final int stepCount, final boolean animate) {
        ((android.app.Activity) context).runOnUiThread(() -> {
            stepRing = ((android.app.Activity) context).findViewById(R.id.step_ring);
            android.widget.TextView tvCount = ((android.app.Activity) context).findViewById(R.id.tv_step_count);
            android.widget.TextView tvGoal = ((android.app.Activity) context).findViewById(R.id.tv_step_goal);
            if (stepRing == null) return;

            // tv_step_pct owned by MainActivity.updateDeviceDashboardRings (distance/calorie metrics)
            updateRing(stepRing, tvCount, tvGoal, null, stepCount, animate);
            // render the multi-ring dashboard + metrics here (not in MainActivity's wrapper) so every
            // caller is covered — including TrackingManager.useDefaultTrackingMethod's direct calls
            if (context instanceof MainActivity) ((MainActivity) context).updateDeviceDashboardRings(stepCount);

            if (stepCount > 2000) {
                if (BtnWaves != null && (BtnWaves.getAnimation() == null || !BtnWaves.getAnimation().hasStarted())) {
                    if (scaler != null) BtnWaves.startAnimation(scaler);
                }
            } else {
                if (BtnWaves != null) BtnWaves.clearAnimation();
            }
            if (stepCount >= activityMilestoneOne && stepCount < activityMilestoneThree) {
                if (BtnPostSteemit != null && scaler != null) {
                    if (BtnPostSteemit.getAnimation() == null || BtnPostSteemit.getAnimation().hasStarted()) {
                        BtnPostSteemit.startAnimation(scaler);
                    }
                }
            } else if (stepCount >= activityMilestoneThree) {
                if (BtnPostSteemit != null) BtnPostSteemit.clearAnimation();
            }
        });
    }

    public void displayActivityChartFitbit(final int stepCount, final boolean animate) {
        ((android.app.Activity) context).runOnUiThread(() -> {
            stepRingFitbit = ((android.app.Activity) context).findViewById(R.id.step_ring_fitbit);
            android.widget.TextView tvCount = ((android.app.Activity) context).findViewById(R.id.tv_step_count_fitbit);
            android.widget.TextView tvGoal = ((android.app.Activity) context).findViewById(R.id.tv_step_goal_fitbit);
            if (stepRingFitbit == null) return;

            // tv_step_pct_fitbit owned by MainActivity.updateFitbitDashboardRings (distance/calorie metrics)
            updateRing(stepRingFitbit, tvCount, tvGoal, null, stepCount, animate);
            // render rings + metrics here so TrackingManager's direct calls are covered too
            if (context instanceof MainActivity) ((MainActivity) context).updateFitbitDashboardRings(stepCount);

            if (stepCount > 2000) {
                if (BtnWaves != null && (BtnWaves.getAnimation() == null || !BtnWaves.getAnimation().hasStarted())) {
                    if (scaler != null) BtnWaves.startAnimation(scaler);
                }
            } else {
                if (BtnWaves != null) BtnWaves.clearAnimation();
            }
            if (stepCount >= activityMilestoneOne && stepCount < activityMilestoneThree) {
                if (BtnPostSteemit != null && scaler != null) {
                    if (BtnPostSteemit.getAnimation() == null || BtnPostSteemit.getAnimation().hasStarted()) {
                        BtnPostSteemit.startAnimation(scaler);
                    }
                }
            } else if (stepCount >= activityMilestoneThree) {
                if (BtnPostSteemit != null) BtnPostSteemit.clearAnimation();
            }
        });
    }

    public void displayActivityChartHealthConnect(final int stepCount, final boolean animate) {
        ((android.app.Activity) context).runOnUiThread(() -> {
            stepRingHc = ((android.app.Activity) context).findViewById(R.id.step_ring_hc);
            android.widget.TextView tvCount = ((android.app.Activity) context).findViewById(R.id.tv_step_count_hc);
            android.widget.TextView tvGoal = ((android.app.Activity) context).findViewById(R.id.tv_step_goal_hc);
            if (stepRingHc == null) return;

            // tv_step_pct_hc is owned by MainActivity.updateHcDashboardRings (the distance/calorie
            // metrics line) in the multi-ring HC view, so don't overwrite it with "% to goal" here
            updateRing(stepRingHc, tvCount, tvGoal, null, stepCount, animate);

            if (stepCount > 2000) {
                if (BtnWaves != null && (BtnWaves.getAnimation() == null || !BtnWaves.getAnimation().hasStarted())) {
                    if (scaler != null) BtnWaves.startAnimation(scaler);
                }
            } else {
                if (BtnWaves != null) BtnWaves.clearAnimation();
            }
            if (stepCount >= activityMilestoneOne && stepCount < activityMilestoneThree) {
                if (BtnPostSteemit != null && scaler != null) {
                    if (BtnPostSteemit.getAnimation() == null || !BtnPostSteemit.getAnimation().hasStarted()) {
                        BtnPostSteemit.startAnimation(scaler);
                    }
                }
            } else if (stepCount >= activityMilestoneThree) {
                if (BtnPostSteemit != null) BtnPostSteemit.clearAnimation();
            }
        });
    }

    public void displayDayChartData(final boolean animate) {
        ((android.app.Activity) context).runOnUiThread(() -> {
            Date date = new Date();
            java.text.DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault());
            String strDate = dateFormat.format(date);

            StepsDBHelper mStepsDBHelper = new StepsDBHelper(context);
            ArrayList<ActivitySlot> mStepCountList = mStepsDBHelper.fetchDateTimeSlotActivity(strDate);

            dayChart = ((android.app.Activity) context).findViewById(R.id.main_today_activity_chart);
            List<BarEntry> entries = new ArrayList<>();
            int data_id = 0;
            int hoursInDay = 24;
            int[] minInt = {0, 15, 30, 45};
            int minSlots = minInt.length;
            final String[] labels = new String[hoursInDay * minSlots];

            for (int indHr = 0; indHr < hoursInDay; indHr++) {
                for (int indMin = 0; indMin < minSlots; indMin++) {
                    String slotLabel = "" + indHr;
                    if (indHr < 10) slotLabel = "0" + indHr;
                    labels[data_id] = slotLabel + ":";
                    if (minInt[indMin] < 10) {
                        slotLabel += "0" + minInt[indMin];
                        labels[data_id] += "0" + minInt[indMin];
                    } else {
                        slotLabel += minInt[indMin];
                        labels[data_id] += minInt[indMin];
                    }
                    int matchingSlot = mStepCountList.indexOf(new ActivitySlot(slotLabel, 0));
                    if (matchingSlot > -1) {
                        entries.add(new BarEntry(data_id, Float.parseFloat("" + mStepCountList.get(matchingSlot).activityCount)));
                    } else {
                        entries.add(new BarEntry(data_id, Float.parseFloat("0")));
                    }
                    data_id += 1f;
                }
            }

            BarDataSet dataSet = new BarDataSet(entries, context.getString(R.string.activity_count_lbl));
            dayBarData = new BarData(dataSet);
            dayBarData.setBarWidth(0.8f);

            IAxisValueFormatter formatter = (value, axis) -> { int i = (int) value; return (i >= 0 && i < labels.length) ? labels[i] : ""; };
            XAxis xAxis = dayChart.getXAxis();
            xAxis.setGranularity(1f);
            xAxis.setValueFormatter(formatter);

            IValueFormatter yFormatter = (value, entry, dataSetIndex, viewPortHandler) -> {
                if (value < 1) return "";
                return "" + (int) value;
            };

            YAxis yAxisLeft = dayChart.getAxisLeft();
            YAxis yAxisRight = dayChart.getAxisRight();
            dayBarData.setValueFormatter(yFormatter);

            Description chartDescription = new Description();
            chartDescription.setText(context.getString(R.string.activity_details_chart_title));
            dayChart.setDescription(chartDescription);
            dayChart.getLegend().setEnabled(false);
            dayChart.setData(dayBarData);

            int textColor = ContextCompat.getColor(context, R.color.colorBlack);
            Legend legend = dayChart.getLegend();
            legend.setTextColor(textColor);
            xAxis.setTextColor(textColor);
            yAxisLeft.setTextColor(textColor);
            yAxisRight.setTextColor(textColor);
            dataSet.setValueTextColor(textColor);
            chartDescription.setTextColor(textColor);

            if (animate) {
                dayChart.animateXY(1500, 1500);
            } else {
                dayChart.invalidate();
            }
        });
    }

    public void displayChartData(final boolean animate) {
        ((android.app.Activity) context).runOnUiThread(() -> {
            StepsDBHelper mStepsDBHelper = new StepsDBHelper(context);
            ArrayList<DateStepsModel> mStepCountList = mStepsDBHelper.readStepsEntries();

            SimpleDateFormat dateFormIn = new SimpleDateFormat("yyyyMMdd");
            SimpleDateFormat dateFormOut = new SimpleDateFormat("MM/dd");
            SimpleDateFormat dateFormOutFull = new SimpleDateFormat("MM/dd/yy");

            fullChart = ((android.app.Activity) context).findViewById(R.id.main_history_activity_chart);
            List<BarEntry> entries = new ArrayList<>();
            final String[] labels = new String[mStepCountList.size()];
            int data_id = 0;

            try {
                for (DateStepsModel data : mStepCountList) {
                    Date feedingDate = dateFormIn.parse(data.mDate);
                    String dateDisplay = dateFormOut.format(feedingDate);
                    if (dateDisplay.substring(0, 2).equals("01") || dateDisplay.substring(0, 2).equals("12")) {
                        dateDisplay = dateFormOutFull.format(feedingDate);
                    }
                    labels[data_id] = dateDisplay;
                    entries.add(new BarEntry(data_id, Float.parseFloat("" + data.mStepCount)));
                    data_id += 1f;
                }
            } catch (ParseException e) {
                Log.e(TAG, "ERROR");
            }

            BarDataSet dataSet = new BarDataSet(entries, context.getString(R.string.activity_count_lbl));
            chartBarData = new BarData(dataSet);
            chartBarData.setBarWidth(0.5f);

            IAxisValueFormatter formatter = (value, axis) -> { int i = (int) value; return (i >= 0 && i < labels.length) ? labels[i] : ""; };
            XAxis xAxis = fullChart.getXAxis();
            xAxis.setGranularity(1f);
            xAxis.setValueFormatter(formatter);

            YAxis yAxisLeft = fullChart.getAxisLeft();
            YAxis yAxisRight = fullChart.getAxisRight();
            int textColor = ContextCompat.getColor(context, R.color.colorBlack);

            if (yAxisLeft.getLimitLines().isEmpty()) {
                LimitLine line = new LimitLine(activityMilestoneOne, context.getString(R.string.min_reward_level_chart));
                line.enableDashedLine(10f, 10f, 10f);
                line.setLineColor(ContextCompat.getColor(context, R.color.actifitRed));
                line.setLineWidth(2f);
                line.setTextStyle(Paint.Style.FILL_AND_STROKE);
                line.setTextColor(Color.BLACK);
                line.setTextSize(12f);
                yAxisLeft.addLimitLine(line);

                line = new LimitLine(activityMilestoneThree, context.getString(R.string.max_reward_level_chart));
                line.setLineColor(ContextCompat.getColor(context, R.color.actifitDarkGreen));
                line.setLineWidth(2f);
                line.setTextStyle(Paint.Style.FILL_AND_STROKE);
                line.setTextColor(textColor);
                line.setTextSize(12f);
                yAxisLeft.addLimitLine(line);
            }

            Description chartDescription = new Description();
            chartDescription.setText(context.getString(R.string.activity_history_chart_title));
            fullChart.setDescription(chartDescription);
            fullChart.getLegend().setEnabled(false);
            fullChart.setData(chartBarData);

            Legend legend = fullChart.getLegend();
            legend.setTextColor(textColor);
            xAxis.setTextColor(textColor);
            yAxisLeft.setTextColor(textColor);
            yAxisRight.setTextColor(textColor);
            dataSet.setValueTextColor(textColor);
            chartDescription.setTextColor(textColor);

            if (animate) {
                fullChart.animateXY(1500, 1500);
            } else {
                fullChart.invalidate();
            }
        });
    }

    public void hideCharts() {
        View defaultContainer = ((android.app.Activity) context).findViewById(R.id.default_chart_container);
        if (thirdPartyTracking == null) thirdPartyTracking = ((android.app.Activity) context).findViewById(R.id.third_party_active);
        if (healthConnectTracking == null) healthConnectTracking = ((android.app.Activity) context).findViewById(R.id.health_connect_active);

        if (defaultContainer != null) defaultContainer.setVisibility(View.GONE);
        if (thirdPartyTracking != null) thirdPartyTracking.setVisibility(View.GONE);
        if (healthConnectTracking != null) healthConnectTracking.setVisibility(View.GONE);

        if (chartSwitcher != null) chartSwitcher.setVisibility(View.GONE);
        View barCharts = ((android.app.Activity) context).findViewById(R.id.bar_chart_container);
        if (barCharts != null) barCharts.setVisibility(View.GONE);
    }

    // ── HC / Fitbit chart display methods ───────────────────────────────────

    public void displayChartDataHC(final boolean animate) {
        ((android.app.Activity) context).runOnUiThread(() -> {
            StepsDBHelper db = new StepsDBHelper(context);
            ArrayList<DateStepsModel> data = db.readHCStepsEntries();
            fullChart = ((android.app.Activity) context).findViewById(R.id.main_history_activity_chart);
            renderDailyBars(fullChart, data, animate);
        });
    }

    public void displayDayChartDataHC(final boolean animate) {
        ((android.app.Activity) context).runOnUiThread(() -> {
            String strDate = new SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(new Date());
            StepsDBHelper db = new StepsDBHelper(context);
            ArrayList<ActivitySlot> data = db.fetchHCDateTimeSlotActivity(strDate);
            dayChart = ((android.app.Activity) context).findViewById(R.id.main_today_activity_chart);
            renderHourlyBars(dayChart, data, animate);
        });
    }

    public void displayChartDataFitbit(final boolean animate) {
        ((android.app.Activity) context).runOnUiThread(() -> {
            StepsDBHelper db = new StepsDBHelper(context);
            ArrayList<DateStepsModel> data = db.readFitbitStepsEntries();
            fullChart = ((android.app.Activity) context).findViewById(R.id.main_history_activity_chart);
            renderDailyBars(fullChart, data, animate);
        });
    }

    private void renderDailyBars(BarChart chart, ArrayList<DateStepsModel> data, boolean animate) {
        SimpleDateFormat dateFormIn = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat dateFormOut = new SimpleDateFormat("MM/dd");
        SimpleDateFormat dateFormOutFull = new SimpleDateFormat("MM/dd/yy");

        List<BarEntry> entries = new ArrayList<>();
        final String[] labels = new String[data.size()];
        int dataId = 0;

        try {
            for (DateStepsModel item : data) {
                Date feedingDate = dateFormIn.parse(item.mDate);
                String dateDisplay = dateFormOut.format(feedingDate);
                if (dateDisplay.substring(0, 2).equals("01") || dateDisplay.substring(0, 2).equals("12")) {
                    dateDisplay = dateFormOutFull.format(feedingDate);
                }
                labels[dataId] = dateDisplay;
                entries.add(new BarEntry(dataId, (float) item.mStepCount));
                dataId++;
            }
        } catch (ParseException e) {
            Log.e(TAG, "ERROR");
        }

        BarDataSet dataSet = new BarDataSet(entries, context.getString(R.string.activity_count_lbl));
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.5f);

        IAxisValueFormatter formatter = (value, axis) -> {
            int i = (int) value;
            return (i >= 0 && i < labels.length) ? labels[i] : "";
        };
        XAxis xAxis = chart.getXAxis();
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(formatter);

        YAxis yAxisLeft = chart.getAxisLeft();
        YAxis yAxisRight = chart.getAxisRight();
        int textColor = ContextCompat.getColor(context, R.color.colorBlack);

        if (yAxisLeft.getLimitLines().isEmpty()) {
            LimitLine line = new LimitLine(activityMilestoneOne, context.getString(R.string.min_reward_level_chart));
            line.enableDashedLine(10f, 10f, 10f);
            line.setLineColor(ContextCompat.getColor(context, R.color.actifitRed));
            line.setLineWidth(2f);
            line.setTextStyle(Paint.Style.FILL_AND_STROKE);
            line.setTextColor(Color.BLACK);
            line.setTextSize(12f);
            yAxisLeft.addLimitLine(line);
            line = new LimitLine(activityMilestoneThree, context.getString(R.string.max_reward_level_chart));
            line.setLineColor(ContextCompat.getColor(context, R.color.actifitDarkGreen));
            line.setLineWidth(2f);
            line.setTextStyle(Paint.Style.FILL_AND_STROKE);
            line.setTextColor(textColor);
            line.setTextSize(12f);
            yAxisLeft.addLimitLine(line);
        }

        Description chartDescription = new Description();
        chartDescription.setText(context.getString(R.string.activity_history_chart_title));
        chart.setDescription(chartDescription);
        chart.getLegend().setEnabled(false);
        chart.setData(barData);

        Legend legend = chart.getLegend();
        legend.setTextColor(textColor);
        xAxis.setTextColor(textColor);
        yAxisLeft.setTextColor(textColor);
        yAxisRight.setTextColor(textColor);
        dataSet.setValueTextColor(textColor);
        chartDescription.setTextColor(textColor);

        if (animate) {
            chart.animateXY(1500, 1500);
        } else {
            chart.invalidate();
        }
    }

    private void renderHourlyBars(BarChart chart, ArrayList<ActivitySlot> data, boolean animate) {
        List<BarEntry> entries = new ArrayList<>();
        int dataId = 0;
        int[] minInt = {0, 15, 30, 45};
        final String[] labels = new String[24 * minInt.length];

        for (int indHr = 0; indHr < 24; indHr++) {
            for (int indMin = 0; indMin < minInt.length; indMin++) {
                String slotLabel = indHr < 10 ? "0" + indHr : "" + indHr;
                labels[dataId] = slotLabel + ":";
                if (minInt[indMin] < 10) {
                    slotLabel += "0" + minInt[indMin];
                    labels[dataId] += "0" + minInt[indMin];
                } else {
                    slotLabel += minInt[indMin];
                    labels[dataId] += minInt[indMin];
                }
                int matchingSlot = data.indexOf(new ActivitySlot(slotLabel, 0));
                entries.add(new BarEntry(dataId,
                        matchingSlot > -1 ? (float) data.get(matchingSlot).activityCount : 0f));
                dataId++;
            }
        }

        BarDataSet dataSet = new BarDataSet(entries, context.getString(R.string.activity_count_lbl));
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.8f);

        IAxisValueFormatter formatter = (value, axis) -> labels[(int) value];
        XAxis xAxis = chart.getXAxis();
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(formatter);

        IValueFormatter yFormatter = (value, entry, dataSetIndex, viewPortHandler) -> value < 1 ? "" : "" + (int) value;
        barData.setValueFormatter(yFormatter);

        YAxis yAxisLeft = chart.getAxisLeft();
        YAxis yAxisRight = chart.getAxisRight();
        int textColor = ContextCompat.getColor(context, R.color.colorBlack);

        Description chartDescription = new Description();
        chartDescription.setText(context.getString(R.string.activity_details_chart_title));
        chart.setDescription(chartDescription);
        chart.getLegend().setEnabled(false);
        chart.setData(barData);

        Legend legend = chart.getLegend();
        legend.setTextColor(textColor);
        xAxis.setTextColor(textColor);
        yAxisLeft.setTextColor(textColor);
        yAxisRight.setTextColor(textColor);
        dataSet.setValueTextColor(textColor);
        chartDescription.setTextColor(textColor);

        if (animate) {
            chart.animateXY(1500, 1500);
        } else {
            chart.invalidate();
        }
    }

    public void slideRight(View view) {
        view.setVisibility(View.VISIBLE);
        TranslateAnimation animate = new TranslateAnimation(view.getWidth(), 0, 0, 0);
        animate.setDuration(500);
        animate.setFillAfter(true);
        view.startAnimation(animate);
    }

    public void slideLeft(View view) {
        view.setVisibility(View.GONE);
        TranslateAnimation animate = new TranslateAnimation(0, view.getWidth(), 0, 0);
        animate.setDuration(500);
        animate.setFillAfter(true);
        view.startAnimation(animate);
    }
}
