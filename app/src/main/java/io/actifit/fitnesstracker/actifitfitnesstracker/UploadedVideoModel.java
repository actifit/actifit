package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

public class UploadedVideoModel {

    public String _id;
    public String status;
    public String created;
    public String permlink;
    public double duration;
    public double size;
    public String title;
    public String filename;
    public String thumbnail;
    public String thumbUrl;
    public String video_v2;
    public String description;
    public String tags;
    public String beneficiaries;
	public double encodingProgress;
    //public JSONArray tags;

    public UploadedVideoModel(JSONObject jsonObject) {
        try {
            //load data
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
