package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;

import java.sql.Array;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class HistoryChartActivity extends BaseActivity {

    private StepsDBHelper mStepsDBHelper;
    private ArrayList<DateStepsModel> mStepCountList;
    private ArrayList<String> mStepFinalList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_chart);

        mStepFinalList = new ArrayList<String>();

        //grab the data to be displayed in the list
        getDataForList();

        //initializing date conversion components
        String dateDisplay;
        //existing date format
        SimpleDateFormat dateFormIn = new SimpleDateFormat("yyyyMMdd");
        //output format
        SimpleDateFormat dateFormOut = new SimpleDateFormat("MM/dd");
        SimpleDateFormat dateFormOutFull = new SimpleDateFormat("MM/dd/yy");

        //connect to the chart and fill it with data
        BarChart chart = findViewById(R.id.activity_chart);

        List<BarEntry> entries = new ArrayList<BarEntry>();

        final String[] labels = new String[mStepCountList.size()];

        int data_id = 0;
        //int data_id_int = 0;
        try {
            for (DateStepsModel data : mStepCountList) {

                //grab date entry according to stored format
                Date feedingDate = dateFormIn.parse(data.mDate);

                //convert it to new format for display

                dateDisplay = dateFormOut.format(feedingDate);

                //if this is month 12, display year along with it
                if (dateDisplay.substring(0,2).equals("01") || dateDisplay.substring(0,2).equals("12")){
                    dateDisplay = dateFormOutFull.format(feedingDate);
                }

                labels[data_id] = dateDisplay;
                entries.add(new BarEntry(data_id, Float.parseFloat(""+data.mStepCount)));
                data_id+=1f;
                //data_id_int++;
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }

        BarDataSet dataSet = new BarDataSet(entries, getString(R.string.activity_count_lbl));

        BarData barData = new BarData( dataSet);
        // set custom bar width
        barData.setBarWidth(0.5f);


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

        //add limit lines to show marker of min 5K activity
        YAxis yAxis = chart.getAxisLeft();

        LimitLine line = new LimitLine(5000, getString(R.string.min_reward_level_chart));
        line.enableDashedLine(10f, 10f, 10f);
        line.setLineColor(Color.RED);
        line.setLineWidth(2f);
        line.setTextStyle(Paint.Style.FILL_AND_STROKE);
        line.setTextColor(Color.BLACK);
        line.setTextSize(12f);

        yAxis.addLimitLine(line);

        //add Limit line for max rewarded activity
        line = new LimitLine(10000, getString(R.string.max_reward_level_chart));
        line.setLineColor(Color.GREEN);
        line.setLineWidth(2f);
        line.setTextStyle(Paint.Style.FILL_AND_STROKE);
        line.setTextColor(Color.BLACK);
        line.setTextSize(12f);


        yAxis.addLimitLine(line);

        //description field of chart
        Description chartDescription = new Description();
        chartDescription.setText(getString(R.string.activity_history_chart_title));
        chart.setDescription(chartDescription);

        //fill chart with data
        chart.setData(barData);


        // make the x-axis fit exactly all bars
        //chart.setFitBars(true);

        //display data
        //chart.invalidate();

        //display data with cool animation
        chart.animateXY(1500, 1500);
    }

    /**
     * function handles preparing the proper data to the mStepCountList ArrayList
     */
    public void getDataForList() {
        mStepsDBHelper = new StepsDBHelper(this);
        mStepCountList = mStepsDBHelper.readStepsEntries();
    }
}
