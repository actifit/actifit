package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

public class SingleHivePostModel{

    public int post_id;
    public String title;
    public String author;
    public String permlink;
    public String category;
    public String url;
    public String body;
    public JSONObject json_metadata;
    public String created;
    public int children;
    public Boolean is_paidout = false;

    public String pending_payout_value;
    public String author_payout_value;
    public String curator_payout_value;
    public String total_payout_value;

    public JSONObject stats;

    public JSONArray beneficiaries;
    public JSONArray active_votes;

    public Double afitRewards = 0.0;
    NumberFormat numberFormat;
    public ArrayList<SingleHivePostModel> comments;
    public Boolean commentsExpanded = false;


    public SingleHivePostModel(JSONObject jsonObject) {
        try {
            numberFormat = NumberFormat.getNumberInstance(Locale.getDefault());
            this.fillObjectFromJson(this, jsonObject);
            System.out.println(this.title);
            comments = new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public String getActivityType(){
        if (json_metadata == null) return "";
        if (json_metadata.has("activity_type")){
            try {
                JSONArray activityTypeArray = json_metadata.optJSONArray("activity_type");
                if (activityTypeArray != null && activityTypeArray.length() > 0) {
                    String list = activityTypeArray.optString(0);
                    return list;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    public String getActivityCount(Boolean formatted){
        if (hasActivityCount()){
            try {
                JSONArray stepCountArray = json_metadata.optJSONArray("step_count");
                if (stepCountArray != null && stepCountArray.length() > 0) {
                    int stepCount = stepCountArray.optInt(0);
                    if (formatted) {
                        //format number for display
                        String formattedNumber = numberFormat.format(stepCount);
                        return formattedNumber;
                    }else{
                        return stepCount+"";
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    public Boolean hasActivityCount(){
        if (json_metadata == null) return false;
        return json_metadata.has("step_count");
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
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }


}
