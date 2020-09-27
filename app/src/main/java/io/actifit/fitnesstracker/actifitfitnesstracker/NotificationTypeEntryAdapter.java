package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.browser.customtabs.CustomTabsIntent;

import com.squareup.picasso.Picasso;

import org.w3c.dom.Text;

import java.text.DecimalFormat;
import java.util.ArrayList;


public class NotificationTypeEntryAdapter extends ArrayAdapter<SingleNotificationModel> {

    ArrayList<SingleNotificationModel> activityEntry;

    public NotificationTypeEntryAdapter(Context context, ArrayList<SingleNotificationModel> activityEntry) {
        super(context, 0, activityEntry);
        this.activityEntry = activityEntry;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {

        CheckBox notificationEntry;
        TextView notificationType;

        // Get the data item for this position
        final SingleNotificationModel notfEntry = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.notification_settings_entry, parent, false);
        }

        // Lookup view for data population
        //LinearLayout entryContainer = convertView.findViewById(R.id.single_notif_container);
        notificationEntry = convertView.findViewById(R.id.notification_entry);
        notificationType = convertView.findViewById(R.id.notification_type);

        // Populate the data into the template view using the data object
        notificationEntry.setText(notfEntry.name);
        notificationType.setText(notfEntry.type);

        notificationEntry.setChecked(notfEntry.isChecked);

        notificationEntry.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener(){


            @Override
            public void onCheckedChanged(CompoundButton item, boolean checked) {
                activityEntry.get(position).isChecked = checked;
            }
        });

        //final Context leaderboardContext = this.getContext();

        // Return the completed view to render on screen
        return convertView;
    }

   /* public void isChecked(int position){
        notificationEntry.setChecked(status);
    }*/

}
