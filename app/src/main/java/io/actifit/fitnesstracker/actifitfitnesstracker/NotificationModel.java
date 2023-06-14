package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;

public class NotificationModel {

    public String _id;
    public String action_taker;
    public String type;
    public String url;
    public String date;
    public String details;
    public String status;

    public NotificationModel(JSONObject jsonObject) {
        try {
            this.fillObjectFromJson(this, jsonObject);
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
