package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;

import com.github.mikephil.charting.components.XAxis;

import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.utils.ViewPortHandler;


import java.util.ArrayList;
import java.util.Collections;

import java.util.List;

public class DailyDetailedActivity extends AppCompatActivity {

    private StepsDBHelper mStepsDBHelper;
    private ArrayList<ActivitySlot> mStepCountList;

    public static final String detailedActivityParam = "targetDate";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detailed_chart);

        //grab target date
        String targetDateString = getIntent().getStringExtra(detailedActivityParam);

        TextView targetDate = findViewById(R.id.targetDetailedDate);
        targetDate.setText(targetDateString);

        //grab the data to be displayed in the list
        getDataForList(targetDateString);

        //connect to the chart and fill it with data
        BarChart chart = findViewById(R.id.activity_chart);

        List<BarEntry> entries = new ArrayList<>();

        int data_id = 0;

        //create a full day chart
        int indHr;
        int indMin;
        int hoursInDay = 24;
        int[] minInt = {0, 15, 30, 45};
        int minSlots = minInt.length;

        final String[] labels = new String[hoursInDay * minSlots];

        //loop through whole day as hours
        for (indHr = 0; indHr < hoursInDay; indHr++){
            //loop through 15 mins breaks in hour
            for (indMin = 0; indMin < minSlots; indMin++){
                String slotLabel = "" + indHr;
                if (indHr < 10){
                    slotLabel = "0" + indHr;
                }
                labels[data_id] = slotLabel + ":";
                if (minInt[indMin]<10){
                    slotLabel += "0" + minInt[indMin];
                    labels[data_id] += "0" + minInt[indMin];
                }else{
                    slotLabel += minInt[indMin];
                    labels[data_id] += minInt[indMin];
                }

                int matchingSlot = mStepCountList.indexOf(new ActivitySlot(slotLabel, 0));
                if (matchingSlot>0){
                    //found match, assign values
                    entries.add(new BarEntry(data_id, Float.parseFloat( "" + mStepCountList.get(matchingSlot).activityCount)));
                }else{
                    //default null value
                    entries.add(new BarEntry(data_id, Float.parseFloat( "0")));
                }

                data_id+=1f;
            }
        }

        BarDataSet dataSet = new BarDataSet(entries, getString(R.string.activity_details_lbl));

        BarData barData = new BarData( dataSet);
        // set custom bar width
        barData.setBarWidth(0.8f);


        //customize X-axis

        IAxisValueFormatter formatter = new IAxisValueFormatter() {

            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                return labels[(int) value];
            }

        };

        XAxis xAxis = chart.getXAxis();
        xAxis.setGranularity(1f); // minimum axis-step (interval)
        xAxis.setValueFormatter(formatter);

        IValueFormatter yFormatter = new IValueFormatter() {

            @Override
            public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {
                if (value < 1){
                    return "";
                }
                return "" + (int)value;
            }

        };

        //add limit lines to show marker of min 5K activity
        //YAxis yAxis = chart.getAxisLeft();
        barData.setValueFormatter(yFormatter);
        //yAxis.setAxisMinimum(0);

        //description field of chart
        Description chartDescription = new Description();
        chartDescription.setText(getString(R.string.activity_details_chart_title));
        chart.setDescription(chartDescription);

        //fill chart with data
        chart.setData(barData);


        // make the x-axis fit exactly all bars
        //chart.setFitBars(true);

        //display data
        chart.invalidate();
    }

    /**
     * function handles preparing the proper data to the mStepCountList ArrayList
     */
    public void getDataForList(String targetDateString) {
        mStepsDBHelper = new StepsDBHelper(this);
        mStepCountList = mStepsDBHelper.fetchDateTimeSlotActivity(targetDateString);
    }
}
