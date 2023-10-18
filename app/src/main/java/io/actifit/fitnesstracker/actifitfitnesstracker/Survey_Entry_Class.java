package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class Survey_Entry_Class {
    private String id;
    private String image;
    private String title;
    private String url;
    private JSONArray survey_options;
    private int survey_duration;
    private boolean is_survey_active;
    private int survey_reward;
    private Date date;
    private Date endDate;

    public Survey_Entry_Class(JSONObject entry) {
        if (entry!=null){
            try {
                this.id = (entry.has("_id")?entry.getString("_id"):"") ;
                this.image = (entry.has("image")?entry.getString("image"):"") ;
                this.title = (entry.has("title")?entry.getString("title"):"") ;
                this.url = (entry.has("url")?entry.getString("url"):"") ;
                this.survey_options = (entry.has("survey_options")?entry.getJSONArray("survey_options"):new JSONArray() ) ;
                this.survey_duration = (entry.has("survey_duration")?entry.getInt("survey_duration"):0) ;
                this.survey_reward = (entry.has("survey_reward")?entry.getInt("survey_reward"):0) ;
                this.date = (entry.has("date")? Utils.getFormattedDate(entry.getString("date")):new Date()) ;
                this.endDate = (entry.has("endDate")? Utils.getFormattedDate(entry.getString("endDate")):new Date()) ;
                this.is_survey_active = isSurveyActive(entry);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    private boolean isSurveyActive(JSONObject entry){
        if (!entry.has("endDate")) return false;
        else{
            try{
                return !Utils.isPastTime(entry.getString("endDate"));
            } catch (JSONException e) {
                e.printStackTrace();
                return false;
            }
        }
    }


    public int getSurvey_duration() {
        return survey_duration;
    }

    public void setSurvey_duration(int survey_duration) {
        this.survey_duration = survey_duration;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public JSONArray getSurvey_options() {
        return survey_options;
    }

    public void setSurvey_options(JSONArray survey_options) {
        this.survey_options = survey_options;
    }

    public boolean isIs_survey_active() {
        return is_survey_active;
    }

    public void setIs_survey_active(boolean is_survey_active) {
        this.is_survey_active = is_survey_active;
    }

    public int getSurvey_reward() {
        return survey_reward;
    }

    public void setSurvey_reward(int survey_reward) {
        this.survey_reward = survey_reward;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}