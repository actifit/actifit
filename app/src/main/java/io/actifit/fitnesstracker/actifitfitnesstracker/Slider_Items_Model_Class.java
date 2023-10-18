package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class Slider_Items_Model_Class {
    private String featured_image_url;
    private String news_title;
    private String link_url;
    private boolean main_announce;
    private boolean is_survey = false;
    private JSONArray survey_options;
    private int survey_duration;
    private boolean is_survey_active = false;
    private int survey_reward;
    private Date date;
    private Date endDate;

    public Slider_Items_Model_Class(JSONObject entry) {
        if (entry!=null){
            try {
                this.featured_image_url = (entry.has("featured_image_url")?entry.getString("featured_image_url"):"") ;
                this.news_title = (entry.has("news_title")?entry.getString("news_title"):"") ;
                this.link_url = (entry.has("link_url")?entry.getString("link_url"):"") ;
                this.main_announce = (entry.has("main_announce")?entry.getBoolean("main_announce"):false) ;
                this.is_survey = (entry.has("is_survey")?entry.getBoolean("is_survey"):false) ;
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

    public Slider_Items_Model_Class(String featured_image_url, String news_title, String link_url) {
        this.featured_image_url = featured_image_url;
        this.news_title = news_title;
        this.link_url = link_url;
    }

    public String getFeatured_image_url() {
        return featured_image_url;
    }

    public String getNews_title() {
        return news_title;
    }

    public String getLink_url(){
        return this.link_url;
    }

    public boolean isMain_announce(){ return this.main_announce; }

    public boolean isSurvey(){ return this.is_survey; }

    public void setFeatured_image_url(String featured_image_url) {
        this.featured_image_url = featured_image_url;
    }

    public void setNews_title(String news_title) {
        this.news_title = news_title;
    }

    public void setLink_url(String link_url) {
        this.link_url = link_url;
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
}