package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collections;

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

        //loop through the data to prepare it for proper display
        for (int position=0;position<mStepCountList.size();position++){
            mStepFinalList.add((mStepCountList.get(position)).mDate + " - Total Steps: " + String.valueOf((mStepCountList.get(position)).mStepCount));
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
