package io.actifit.fitnesstracker.actifitfitnesstracker;

import static android.view.View.GONE;
import static androidx.core.content.ContextCompat.startActivity;

import static com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread;
import static io.actifit.fitnesstracker.actifitfitnesstracker.MainActivity.TAG;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;

import info.androidhive.fontawesome.FontTextView;

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
        FontTextView detailsButton = convertView.findViewById(R.id.activityDetailsBtn);
        FontTextView postView = convertView.findViewById(R.id.post_link);
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

        try {
            loadPosts(postView,activityEntry.mDate);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return convertView;
    }

    private void openPost(String permlink) {
        String link = getContext().getString(R.string.actifit_url) + MainActivity.username + "/" + permlink;
        Intent gotoPost = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        gotoPost.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(gotoPost);
    }
    void loadPosts(FontTextView postView, String mDate) throws JSONException {

        HiveRequests hive = new HiveRequests(getContext());

        Thread thread = new Thread(() -> {

            try {
                JSONObject params = new JSONObject();
                params.put("sort", "posts");
                params.put("account", MainActivity.username);
                params.put("start_author", start_author);
                params.put("start_permlink", start_permlink);
                params.put("observer", "");
                JSONArray result = hive.getAccountPosts(params);
                boolean hasPost = false;
                for (int i = 0; i < result.length(); i++) {
                    JSONObject post = result.getJSONObject(i);
                    String postDate = post.getString("created");
                    SimpleDateFormat formatter1 = new SimpleDateFormat("MM/dd/yyyy");
                    SimpleDateFormat formatter2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                    Date date1 = formatter1.parse(mDate);

                    // Parsing postDate
                    Date date2 = formatter2.parse(postDate);

                    // To get only the date part from date2, you can use Calendar
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(date2);
                    // Extracting only the date part
                    calendar.set(Calendar.HOUR_OF_DAY, 0);
                    calendar.set(Calendar.MINUTE, 0);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    Date dateOnly = calendar.getTime();
                    if (formatter1.format(date1).equals(formatter1.format(dateOnly))) {
                        hasPost = true;
                        final String permlink = post.getString("permlink");
                        runOnUiThread(() -> {
                            postView.setText("\uf15c");
                            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) postView.getLayoutParams();
                            margins.leftMargin = 95;
                            postView.setLayoutParams(margins);
                            postView.setTextColor(getContext().getResources().getColor(R.color.actifitDarkGreen));
                            postView.setOnClickListener(view -> openPost(permlink));
                        });
                        break;
                    }
                }
                if (!hasPost) {
                    runOnUiThread(() -> postView.setText("\uf410"));
                }

            }
            catch (Exception e){
                e.printStackTrace();
            }
        });
        thread.start();
    }
}