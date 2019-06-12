package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONException;
import org.json.JSONObject;

public class SinglePostModel {

    public int leaderRank;
    public String username;
    public String userProfilePic;
    public int activityCount;
    public String postUrl;

    public SinglePostModel(){

    }

    public SinglePostModel(int leaderRank, String username, String userProfilePic, int activityCount, String postUrl){
        this.leaderRank = leaderRank;
        this.username = username;
        this.userProfilePic = userProfilePic;
        this.activityCount = activityCount;
        this.postUrl = postUrl;
    }

    public SinglePostModel(JSONObject jsonObject){
        try {
            this.leaderRank = jsonObject.has("leaderRank") ? jsonObject.getInt("leaderRank"):-1;
            this.username = jsonObject.has("author") ? jsonObject.getString("author"):"";
            this.userProfilePic = jsonObject.has("userProfilePic") ? jsonObject.getString("userProfilePic"):"";

            this.activityCount = jsonObject.has("activityCount") ? jsonObject.getJSONArray("activityCount").getInt(0):-1;

            this.postUrl = jsonObject.has("url") ? jsonObject.getString("url"):"";
        } catch (JSONException e) {
            e.printStackTrace();
        }


    }
}
