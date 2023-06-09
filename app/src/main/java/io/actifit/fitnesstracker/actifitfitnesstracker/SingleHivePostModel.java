package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;

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
    public Boolean is_paidout;

    public String pending_payout_value;
    public String author_payout_value;
    public String curator_payout_value;

    public JSONObject stats;

    public JSONArray beneficiaries;
    public JSONArray active_votes;


    public SingleHivePostModel(JSONObject jsonObject) {
        try {
            this.fillObjectFromJson(this, jsonObject);
            System.out.println(this.title);
        } catch (Exception e) {
            e.printStackTrace();
        }

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

                    // Set the value to the field
                    field.set(object, value);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }


}
