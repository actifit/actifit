package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONException;
import org.json.JSONObject;

public class SingleNotificationModel {


    public String name;
    public String type;
    public boolean isChecked;

    public SingleNotificationModel(){

    }

    public SingleNotificationModel(String name, String type, boolean isChecked){
        this.name = name;
        this.type = type;
        this.isChecked = isChecked;
    }

    public SingleNotificationModel(JSONObject jsonObject, boolean isChecked){
        try {
            this.name = jsonObject.has("name") ? jsonObject.getString("name"):"";
            this.type = jsonObject.has("category") ? jsonObject.getString("category"):"";
            this.isChecked = isChecked;
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
