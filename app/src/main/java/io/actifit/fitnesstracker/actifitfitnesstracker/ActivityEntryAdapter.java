package io.actifit.fitnesstracker.actifitfitnesstracker;

import static android.view.View.GONE;
import static androidx.core.content.ContextCompat.startActivity;

import static com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread;
import static io.actifit.fitnesstracker.actifitfitnesstracker.MainActivity.TAG;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class ActivityEntryAdapter extends ArrayAdapter<DateStepsModel> {

    private final HiveRequests hiveReq;
    String start_author, start_permlink;

    public ActivityEntryAdapter(Context context, ArrayList<DateStepsModel> activityEntry) {
        super(context, 0, activityEntry);
        hiveReq = new HiveRequests(context);
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
        TextView detailsButton = convertView.findViewById(R.id.activityDetailsBtn);
        TextView postViewButton = convertView.findViewById(R.id.post_link);
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
        detailsButton.setOnClickListener(arg0 -> {
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
        });
        runOnUiThread(() -> {
            if (activityEntry.hasRelevantPost) {
                postViewButton.setText("\uf15c");
                /*ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) postView.getLayoutParams();
                margins.leftMargin = 95;
                postViewButton.setLayoutParams(margins);*/
                postViewButton.setTextColor(getContext().getResources().getColor(R.color.actifitDarkGreen));
                postViewButton.setOnClickListener(view -> openPost(activityEntry.relevantPostLink));


                /* postView.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

                        builder.setToolbarColor(getContext().getResources().getColor(R.color.actifitRed));

                        //animation for showing and closing fitbit authorization screen
                        builder.setStartAnimations(getContext(), R.anim.slide_in_right, R.anim.slide_out_left);

                        //animation for back button clicks
                        builder.setExitAnimations(getContext(), android.R.anim.slide_in_left,
                                android.R.anim.slide_out_right);

                        CustomTabsIntent customTabsIntent = builder.build();

                        customTabsIntent.launchUrl(getContext(), Uri.parse(MainActivity.ACTIFIT_CORE_URL + '/'
                                + MainActivity.username + '/' + activityEntry.relevantPostLink));
                    }
                });*/
            }else{
                postViewButton.setText("\uf410");
                postViewButton.setTextColor(getContext().getResources().getColor(R.color.actifitRed));
                postViewButton.setOnClickListener(view -> noPostFound());
            }
        });
        return convertView;
    }

    private void noPostFound(){
        Toast.makeText(getContext(), getContext().getString(R.string.noPostFound),
                Toast.LENGTH_SHORT).show();
    }

    private void openPost(String permlink) {
        String link = getContext().getString(R.string.actifit_url) + MainActivity.username + "/" + permlink;
        Intent gotoPost = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        gotoPost.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(gotoPost);
    }

}