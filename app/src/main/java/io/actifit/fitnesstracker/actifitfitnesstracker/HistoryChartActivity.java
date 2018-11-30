package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.graphics.Color;
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

public class HistoryChartActivity extends AppCompatActivity {

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
        SimpleDateFormat dateFormOut = new SimpleDateFormat("MM/dd/yyyy");

        //loop through the data to prepare it for proper display
        for (int position = 0; position < mStepCountList.size(); position++) {
            try {
                //grab date entry according to stored format
                Date feedingDate = dateFormIn.parse((mStepCountList.get(position)).mDate);
                //convert it to new format for display
                dateDisplay = dateFormOut.format(feedingDate);
                //append to display
                String displayEntryTxt = dateDisplay + " - Total Activity: " + String.valueOf((mStepCountList.get(position)).mStepCount);
                //append to display
                if (mStepCountList.get(position).mtrackingDevice!=null && !mStepCountList.get(position).mtrackingDevice.equals("")
                        && !mStepCountList.get(position).mtrackingDevice.equals(StepsDBHelper.DEVICE_SENSORS)){
                    displayEntryTxt += " ( "+mStepCountList.get(position).mtrackingDevice+" )";
                }
                mStepFinalList.add(displayEntryTxt);
            } catch (ParseException txtEx) {
                Log.d(MainActivity.TAG,txtEx.toString());
                txtEx.printStackTrace();
            }
        }
        //reverse the list for descending display
        Collections.reverse(mStepFinalList);

        //connect to the chart and fill it with data
        BarChart chart = findViewById(R.id.activity_chart);

        List<BarEntry> entries = new ArrayList<BarEntry>();

        final String[] labels = new String[mStepCountList.size()];

        int data_id = 0;
        //int data_id_int = 0;
        for (DateStepsModel data : mStepCountList) {
            labels[data_id] = ""+data.mDate;
            entries.add(new BarEntry(data_id, Float.parseFloat(""+data.mStepCount)));
            data_id+=1f;
            //data_id_int++;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Activity Count");

        BarData barData = new BarData( dataSet);
        // set custom bar width
        barData.setBarWidth(0.3f);


        //customize X-axis

        IAxisValueFormatter formatter = new IAxisValueFormatter() {

            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                return labels[(int) value];
            }

        };

        XAxis xAxis = chart.getXAxis();
        xAxis.setGranularity(0.5f); // minimum axis-step (interval)
        xAxis.setValueFormatter(formatter);

        //add limit lines to show marker of min 5K activity
        YAxis yAxis = chart.getAxisLeft();

        LimitLine line = new LimitLine(5000, "Min Reward - 5K Activity");
        line.setLineColor(Color.RED);
        line.setLineWidth(4f);
        line.setTextColor(Color.BLACK);
        line.setTextSize(12f);

        yAxis.addLimitLine(line);

        //description field of chart
        Description chartDescription = new Description();
        chartDescription.setText("Activity History Chart");
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
    public void getDataForList() {
        mStepsDBHelper = new StepsDBHelper(this);
        mStepCountList = mStepsDBHelper.readStepsEntries();
    }
}
