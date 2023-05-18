package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class ActivityEntryAdapter extends ArrayAdapter<DateStepsModel> {

    public ActivityEntryAdapter(Context context, ArrayList<DateStepsModel> activityEntry) {
        super(context, 0, activityEntry);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        final DateStepsModel activityEntry = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.activity_history_single_entry, parent, false);
        }
        // Lookup view for data population
        TextView entryDate = convertView.findViewById(R.id.activityEntryDate);
        TextView entryCount = convertView.findViewById(R.id.activityEntryCount);
        TextView entryDevice = convertView.findViewById(R.id.activityDevice);
        Button detailsButton = convertView.findViewById(R.id.activityDetailsBtn);
        // Populate the data into the template view using the data object
        entryDate.setText(activityEntry.mDate.toString());

        //decimal format the numbers to add thousands separator
        DecimalFormat decim = new DecimalFormat("#,###");

        entryCount.setText(decim.format(activityEntry.mStepCount));

        //render some visual effects for step count
        if (activityEntry.mStepCount >= 10000 ){
            entryCount.setTextColor(getContext().getResources().getColor(R.color.actifitDarkGreen));
        }else if (activityEntry.mStepCount >= 5000 ){
            entryCount.setTextColor(getContext().getResources().getColor(R.color.actifitRed));
        }else {
            entryCount.setTextColor(getContext().getResources().getColor(android.R.color.tab_indicator_text));
        }

        //if this is a non-device tracked data (for e.g. Fitbit), display accordingly
        if (activityEntry.mtrackingDevice!=null && !activityEntry.mtrackingDevice.equals("")
                && !activityEntry.mtrackingDevice.equals(StepsDBHelper.DEVICE_SENSORS)){
            entryDevice.setText(activityEntry.mtrackingDevice);
        }else{
            entryDevice.setText("");
        }

        //associate proper action with button
        detailsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent intent = new Intent(getContext(), DailyDetailedActivity.class);

                //initializing date conversion components
                String dateDisplay = "";
                //existing date format
                SimpleDateFormat dateFormIn = new SimpleDateFormat("MM/dd/yyyy");
                //output format
                SimpleDateFormat dateFormOut = new SimpleDateFormat("yyyyMMdd");

                //grab date entry according to stored format

                try {
                    Date feedingDate = dateFormIn.parse(activityEntry.mDate);

                    //convert it to new format for display
                    dateDisplay = dateFormOut.format(feedingDate);

                } catch (ParseException e) {
                    e.printStackTrace();
                }


                intent.putExtra(DailyDetailedActivity.detailedActivityParam, dateDisplay);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }
        });
        // Return the completed view to render on screen
        return convertView;
    }

}
