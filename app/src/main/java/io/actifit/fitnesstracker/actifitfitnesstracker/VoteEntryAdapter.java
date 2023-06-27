package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class VoteEntryAdapter extends ArrayAdapter<VoteEntryAdapter.VoteEntry> {

    public VoteEntryAdapter(Context context, ArrayList<VoteEntry> voteEntry) {
        super(context, 0, voteEntry);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        final VoteEntry voteEntry = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.vote_single_entry, parent, false);
        }
        // Lookup view for data population
        TextView voter = convertView.findViewById(R.id.voter);
        TextView votePercent = convertView.findViewById(R.id.voterPercent);
        ImageView voterProfilePic = convertView.findViewById(R.id.voter_pic);

        voter.setText(voteEntry.voter);
        votePercent.setText(voteEntry.voteValue());

        final String userImgUrl = getContext().getString(R.string.hive_image_host_url).replace("USERNAME", voteEntry.voter);

        Handler uiHandler = new Handler(Looper.getMainLooper());
        uiHandler.post(() -> {
            //load user image
            Picasso.get()
                    .load(userImgUrl)
                    .into(voterProfilePic);
        });

        // Return the completed view to render on screen
        return convertView;
    }

    // VoteEntry class
    public static class VoteEntry {
        public String voter;
        private int voteId;
        public long rshares;
        public float postRatio = 0;
        private boolean upvote;
        private double percent;
        private double value;

        public String voteValue(){
            return String.format("%.4f",this.postRatio * this.rshares);
        }

        public VoteEntry(JSONObject jsonObject, float postRatio){
            this.fillObjectFromJson(this, jsonObject);
            this.postRatio = postRatio;
        }

        public VoteEntry(String username, int voteId, boolean upvote, double percent) {
            this.voter = username;
            this.voteId = voteId;
            this.upvote = upvote;
            this.percent = percent;
            this.value = calculateValue();
        }

        private double calculateValue() {
            // Calculate the value based on vote data
            return 0.0; // Replace with your calculation logic
        }

        // Getters and setters

        public int getVoteId() {
            return voteId;
        }

        public void setVoteId(int voteId) {
            this.voteId = voteId;
        }

        public boolean isUpvote() {
            return upvote;
        }

        public void setUpvote(boolean upvote) {
            this.upvote = upvote;
        }

        public double getPercent() {
            return percent;
        }

        public void setPercent(double percent) {
            this.percent = percent;
        }

        public double getValue() {
            return value;
        }

        public void setValue(double value) {
            this.value = value;
        }

        public static void fillObjectFromJson(Object object, JSONObject jsonObject) {
            try {
                // Get all the fields of the class
                Field[] fields = object.getClass().getDeclaredFields();

                for (Field field : fields) {
                    // Make the field accessible (in case it's private)
                    field.setAccessible(true);

                    // Get the field name
                    String fieldName = field.getName();

                    // Check if the JSON object contains the field
                    if (jsonObject.has(fieldName)) {
                        // Get the value from the JSON object
                        Object value = jsonObject.get(fieldName);
                        try{
                            // Set the value to the field
                            field.set(object, value);
                        } catch (IllegalArgumentException e) {
                            try {
                                //attempt to parse JSONObject
                                JSONObject tmpObj = new JSONObject((String) jsonObject.get(fieldName));
                                field.set(object, tmpObj);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                }
            } catch (JSONException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

}
