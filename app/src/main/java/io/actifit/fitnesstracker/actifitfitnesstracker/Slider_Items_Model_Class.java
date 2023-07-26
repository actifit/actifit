package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONException;
import org.json.JSONObject;

public class Slider_Items_Model_Class {
    private String featured_image_url;
    private String news_title;
    private String link_url;
    private boolean main_announce;

    public Slider_Items_Model_Class(JSONObject entry) {
        if (entry!=null){
            try {
                this.featured_image_url = (entry.has("featured_image_url")?entry.getString("featured_image_url"):"") ;
                this.news_title = (entry.has("news_title")?entry.getString("news_title"):"") ;
                this.link_url = (entry.has("link_url")?entry.getString("link_url"):"") ;
                this.main_announce = (entry.has("main_announce")?entry.getBoolean("main_announce"):false) ;
            } catch (JSONException e) {
                e.printStackTrace();
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

    public void setFeatured_image_url(String featured_image_url) {
        this.featured_image_url = featured_image_url;
    }

    public void setNews_title(String news_title) {
        this.news_title = news_title;
    }

    public void setLink_url(String link_url) {
        this.link_url = link_url;
    }
}