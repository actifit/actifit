package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

public class StepHistoryActivity extends AppCompatActivity {
    private ListView mStepsListView;
    private StepsDBHelper mStepsDBHelper;
    private ArrayList<DateStepsModel> mStepCountList;
    private ArrayList<String> mStepFinalList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_history);

        mStepsListView = findViewById(R.id.steps_list);
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
        for (int position=0;position<mStepCountList.size();position++){
            try {
                //grab date entry according to stored format
                Date feedingDate = dateFormIn.parse((mStepCountList.get(position)).mDate);
                //convert it to new format for display
                dateDisplay = dateFormOut.format(feedingDate);
                //append to display
                mStepFinalList.add(dateDisplay + " - Total Activity: " + String.valueOf((mStepCountList.get(position)).mStepCount));
            }catch(ParseException txtEx){
                System.out.println(txtEx.toString());
                txtEx.printStackTrace();
            }
        }
        //reverse the list for descending display
        Collections.reverse(mStepFinalList);

        ArrayAdapter<String> arrayAdapter =
                new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, mStepFinalList);

        mStepsListView.setAdapter(arrayAdapter);

    }

    /**
     * function handles preparing the proper data to the mStepCountList ArrayList
     */
    public void getDataForList() {
        mStepsDBHelper = new StepsDBHelper(this);
        mStepCountList = mStepsDBHelper.readStepsEntries();
    }
}
