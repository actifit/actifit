package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.browser.customtabs.CustomTabsIntent;

import com.squareup.picasso.Picasso;

import java.text.DecimalFormat;
import java.util.ArrayList;


public class NotificationEntryAdapter extends ArrayAdapter<NotificationModel> {

    private String currentUser = "";
    private Context ctx;

    public NotificationEntryAdapter(Context context, ArrayList<NotificationModel> activityEntry) {
        super(context, 0, activityEntry);
        this.ctx = context;
        SharedPreferences sharedPreferences = context.getSharedPreferences("actifitSets", context.MODE_PRIVATE);
        currentUser = sharedPreferences.getString("actifitUser","");
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        final NotificationModel notifEntry = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.notification_single_entry, parent, false);
        }

        // Lookup view for data population
        LinearLayout entryContainer = convertView.findViewById(R.id.entryContainer);
        TextView entryDate = convertView.findViewById(R.id.entryDate);
        TextView actionTaker = convertView.findViewById(R.id.actionTaker);
        //TextView notifType = convertView.findViewById(R.id.notificationType);
        TextView entryDetails = convertView.findViewById(R.id.entryDetails);
        ImageView actionTakerPic = convertView.findViewById(R.id.actionTakerPic);
        FrameLayout picFrame = convertView.findViewById(R.id.picFrame);
        Button detailsButton = convertView.findViewById(R.id.entryDetailsBtn);

        // Populate the data into the template view using the data object

        entryDate.setText(Utils.getTimeDifference(notifEntry.date));
        actionTaker.setText(notifEntry.action_taker);
        entryDetails.setText(notifEntry.details);

        //set proper image
        final String userImgUrl = getContext().getString(R.string.hive_image_host_url).replace("USERNAME", notifEntry.action_taker);
        //Picasso.get().load(postEntry.userProfilePic).into(userProfilePic);
        Handler uiHandler = new Handler(Looper.getMainLooper());
        uiHandler.post(() -> {
            //Picasso.with(ctx)
            //load user image
            Picasso.get()
                    .load(userImgUrl)
                    .into(actionTakerPic);
        });

        //Picasso.with(leaderboardContext).load(postEntry.userProfilePic).into(userProfilePic);

        //handle click on user profile
        picFrame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openUserAccount(notifEntry.action_taker);
            }

        });

        //handle click on username
        actionTaker.setOnClickListener(view -> openUserAccount(notifEntry.action_taker));


        //associate proper action with button
        detailsButton.setOnClickListener(arg0 -> {

            //mark as read
            Utils.markNotifRead(ctx, MainActivity.username, notifEntry._id);


            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

            builder.setToolbarColor( ctx.getResources().getColor(R.color.actifitRed));

            //animation for showing and closing fitbit authorization screen
            builder.setStartAnimations(ctx, R.anim.slide_in_right, R.anim.slide_out_left);

            //animation for back button clicks
            builder.setExitAnimations(ctx, android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right);

            CustomTabsIntent customTabsIntent = builder.build();

            customTabsIntent.launchUrl(ctx, Uri.parse(notifEntry.url));


        });
        // Return the completed view to render on screen
        return convertView;
    }

    private void openUserAccount(String username){
        if (username != "") {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

            builder.setToolbarColor(getContext().getResources().getColor(R.color.actifitRed));

            //animation for showing and closing fitbit authorization screen
            builder.setStartAnimations(getContext(), R.anim.slide_in_right, R.anim.slide_out_left);

            //animation for back button clicks
            builder.setExitAnimations(getContext(), android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right);

            CustomTabsIntent customTabsIntent = builder.build();

            customTabsIntent.launchUrl(getContext(), Uri.parse(MainActivity.ACTIFIT_CORE_URL + '/' + username));
        }
    }
}
