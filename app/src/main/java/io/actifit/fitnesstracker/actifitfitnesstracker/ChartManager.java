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
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.github.mikephil.charting.utils.ViewPortHandler;

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

    private PieChart btnPieChart;
    private PieChart fitbitPieChart;
    private PieChart healthConnectPieChart;

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

    public PieChart getBtnPieChart() {
        return btnPieChart;
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

    public void displayActivityChart(final int stepCount, final boolean animate) {
        ((android.app.Activity) context).runOnUiThread(() -> {
            btnPieChart = ((android.app.Activity) context).findViewById(R.id.step_pie_chart);
            ArrayList<PieEntry> activityArray = new ArrayList();
            activityArray.add(new PieEntry(stepCount, ""));

            if (stepCount > 2000) {
                if (BtnWaves != null && (BtnWaves.getAnimation() == null || !BtnWaves.getAnimation().hasStarted())) {
                    if (scaler != null) BtnWaves.startAnimation(scaler);
                }
            } else {
                if (BtnWaves != null) BtnWaves.clearAnimation();
            }

            if (stepCount < activityMilestoneOne) {
                activityArray.add(new PieEntry(activityMilestoneOne - stepCount, ""));
                activityArray.add(new PieEntry(activityMilestoneOne, ""));
            } else if (stepCount < activityMilestoneThree) {
                if (BtnPostSteemit != null && scaler != null) {
                    if (BtnPostSteemit.getAnimation() == null || BtnPostSteemit.getAnimation().hasStarted()) {
                        BtnPostSteemit.startAnimation(scaler);
                    }
                }
                activityArray.add(new PieEntry(activityMilestoneThree - stepCount, ""));
            } else {
                if (BtnPostSteemit != null) BtnPostSteemit.clearAnimation();
            }

            PieDataSet dataSet = new PieDataSet(activityArray, "");
            PieData data = new PieData(dataSet);
            btnPieChart.setData(data);
            btnPieChart.getDescription().setEnabled(false);
            btnPieChart.setCenterText("" + (Math.max(stepCount, 0)));
            btnPieChart.setCenterTextColor(context.getResources().getColor(R.color.actifitRed));
            btnPieChart.setCenterTextSize(20f);
            btnPieChart.setEntryLabelColor(ColorTemplate.COLOR_NONE);
            btnPieChart.setDrawEntryLabels(false);
            btnPieChart.getLegend().setEnabled(false);

            if (stepCount < activityMilestoneOne) {
                dataSet.setColors(context.getResources().getColor(R.color.actifitRed),
                        context.getResources().getColor(android.R.color.tab_indicator_text),
                        context.getResources().getColor(android.R.color.tab_indicator_text));
            } else if (stepCount < activityMilestoneThree) {
                dataSet.setColors(context.getResources().getColor(R.color.actifitDarkGreen),
                        context.getResources().getColor(android.R.color.tab_indicator_text),
                        context.getResources().getColor(android.R.color.tab_indicator_text));
            } else {
                dataSet.setColors(context.getResources().getColor(R.color.actifitDarkGreen));
            }

            dataSet.setSliceSpace(1f);
            dataSet.setHighlightEnabled(true);
            dataSet.setValueTextSize(0f);
            dataSet.setValueTextColor(ColorTemplate.COLOR_NONE);
            dataSet.setValueTextColor(R.color.actifitRed);

            if (animate) {
                btnPieChart.animateXY(2000, 2000);
            } else {
                btnPieChart.invalidate();
            }
        });
    }

    public void displayActivityChartFitbit(final int stepCount, final boolean animate) {
        ((android.app.Activity) context).runOnUiThread(() -> {
            fitbitPieChart = ((android.app.Activity) context).findViewById(R.id.step_pie_chart_fitbit);
            ArrayList<PieEntry> activityArray = new ArrayList();
            activityArray.add(new PieEntry(stepCount, ""));

            if (stepCount > 2000) {
                if (BtnWaves != null && (BtnWaves.getAnimation() == null || BtnWaves.getAnimation().hasStarted())) {
                    if (scaler != null) BtnWaves.setAnimation(scaler);
                }
            }

            if (stepCount < activityMilestoneOne) {
                activityArray.add(new PieEntry(activityMilestoneOne - stepCount, ""));
                activityArray.add(new PieEntry(activityMilestoneOne, ""));
            } else if (stepCount < activityMilestoneThree) {
                if (BtnPostSteemit != null && scaler != null) {
                    if (BtnPostSteemit.getAnimation() == null || BtnPostSteemit.getAnimation().hasStarted()) {
                        BtnPostSteemit.startAnimation(scaler);
                    }
                }
                activityArray.add(new PieEntry(activityMilestoneThree - stepCount, ""));
            }

            PieDataSet dataSet = new PieDataSet(activityArray, "");
            PieData data = new PieData(dataSet);
            fitbitPieChart.setData(data);
            fitbitPieChart.getDescription().setEnabled(false);
            fitbitPieChart.setCenterText("" + (Math.max(stepCount, 0)));
            fitbitPieChart.setCenterTextColor(context.getResources().getColor(R.color.actifitRed));
            fitbitPieChart.setCenterTextSize(20f);
            fitbitPieChart.setEntryLabelColor(ColorTemplate.COLOR_NONE);
            fitbitPieChart.setDrawEntryLabels(false);
            fitbitPieChart.getLegend().setEnabled(false);

            if (stepCount < activityMilestoneOne) {
                dataSet.setColors(context.getResources().getColor(R.color.actifitRed),
                        context.getResources().getColor(android.R.color.tab_indicator_text),
                        context.getResources().getColor(android.R.color.tab_indicator_text));
            } else if (stepCount < activityMilestoneThree) {
                dataSet.setColors(context.getResources().getColor(R.color.actifitDarkGreen),
                        context.getResources().getColor(android.R.color.tab_indicator_text),
                        context.getResources().getColor(android.R.color.tab_indicator_text));
            } else {
                dataSet.setColors(context.getResources().getColor(R.color.actifitDarkGreen));
            }

            dataSet.setSliceSpace(1f);
            dataSet.setHighlightEnabled(true);
            dataSet.setValueTextSize(0f);
            dataSet.setValueTextColor(ColorTemplate.COLOR_NONE);
            dataSet.setValueTextColor(R.color.actifitRed);

            if (animate) {
                fitbitPieChart.animateXY(2000, 2000);
            } else {
                fitbitPieChart.invalidate();
            }
        });
    }

    public void displayActivityChartHealthConnect(final int stepCount, final boolean animate) {
        ((android.app.Activity) context).runOnUiThread(() -> {
            healthConnectPieChart = ((android.app.Activity) context).findViewById(R.id.step_pie_chart_health_connect);
            ArrayList<PieEntry> activityArray = new ArrayList();
            activityArray.add(new PieEntry(stepCount, ""));

            if (stepCount > 2000) {
                if (BtnWaves != null && (BtnWaves.getAnimation() == null || !BtnWaves.getAnimation().hasStarted())) {
                    if (scaler != null) BtnWaves.startAnimation(scaler);
                }
            }

            if (stepCount < activityMilestoneOne) {
                activityArray.add(new PieEntry(activityMilestoneOne - stepCount, ""));
                activityArray.add(new PieEntry(activityMilestoneOne, ""));
            } else if (stepCount < activityMilestoneThree) {
                if (BtnPostSteemit != null && scaler != null) {
                    if (BtnPostSteemit.getAnimation() == null || !BtnPostSteemit.getAnimation().hasStarted()) {
                        BtnPostSteemit.startAnimation(scaler);
                    }
                }
                activityArray.add(new PieEntry(activityMilestoneThree - stepCount, ""));
            }

            PieDataSet dataSet = new PieDataSet(activityArray, "");
            PieData data = new PieData(dataSet);
            healthConnectPieChart.setData(data);
            healthConnectPieChart.getDescription().setEnabled(false);
            healthConnectPieChart.setCenterText("" + (Math.max(stepCount, 0)));
            healthConnectPieChart.setCenterTextColor(context.getResources().getColor(R.color.actifitRed));
            healthConnectPieChart.setCenterTextSize(20f);
            healthConnectPieChart.setEntryLabelColor(ColorTemplate.COLOR_NONE);
            healthConnectPieChart.setDrawEntryLabels(false);
            healthConnectPieChart.getLegend().setEnabled(false);

            if (stepCount < activityMilestoneOne) {
                dataSet.setColors(context.getResources().getColor(R.color.actifitRed),
                        context.getResources().getColor(android.R.color.tab_indicator_text),
                        context.getResources().getColor(android.R.color.tab_indicator_text));
            } else if (stepCount < activityMilestoneThree) {
                dataSet.setColors(context.getResources().getColor(R.color.actifitDarkGreen),
                        context.getResources().getColor(android.R.color.tab_indicator_text),
                        context.getResources().getColor(android.R.color.tab_indicator_text));
            } else {
                dataSet.setColors(context.getResources().getColor(R.color.actifitDarkGreen));
            }

            dataSet.setSliceSpace(1f);
            dataSet.setHighlightEnabled(true);
            dataSet.setValueTextSize(0f);
            dataSet.setValueTextColor(ColorTemplate.COLOR_NONE);

            if (animate) {
                healthConnectPieChart.animateXY(2000, 2000);
            } else {
                healthConnectPieChart.invalidate();
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

            IAxisValueFormatter formatter = (value, axis) -> labels[(int) value];
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

            IAxisValueFormatter formatter = (value, axis) -> labels[(int) value];
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
        if (btnPieChart == null) btnPieChart = ((android.app.Activity) context).findViewById(R.id.step_pie_chart);
        if (thirdPartyTracking == null) thirdPartyTracking = ((android.app.Activity) context).findViewById(R.id.third_party_active);
        if (healthConnectTracking == null) healthConnectTracking = ((android.app.Activity) context).findViewById(R.id.health_connect_active);

        View pieChartView = btnPieChart;
        if (pieChartView != null && pieChartView.getParent() instanceof View) {
            ((View) pieChartView.getParent()).setVisibility(View.GONE);
        }
        thirdPartyTracking.setVisibility(View.GONE);
        healthConnectTracking.setVisibility(View.GONE);

        if (chartSwitcher != null) chartSwitcher.setVisibility(View.INVISIBLE);
        View barCharts = ((android.app.Activity) context).findViewById(R.id.bar_chart_container);
        barCharts.setVisibility(View.INVISIBLE);
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
