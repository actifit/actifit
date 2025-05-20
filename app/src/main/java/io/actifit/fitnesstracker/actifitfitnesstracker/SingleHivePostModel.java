package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class SingleHivePostModel implements Comparable<SingleHivePostModel>{

    public int post_id;
    public String title;
    public String author;
    public String permlink;
    public String category;
    public String url;
    public String body;
    public String shortBody;

    public String translatedText = "";
    public JSONObject json_metadata;
    public String created;
    public int children;
    public Boolean is_paidout = false;

    public String pending_payout_value = "0";
    public String author_payout_value = "0";
    public String curator_payout_value = "0";
    public String total_payout_value = "0";

    public JSONObject stats;

    public JSONArray beneficiaries;
    public JSONArray active_votes;
    public float voteRshares = 0;
    public float sumPayout = 0;
    public float ratio = 0;

    public Double afitRewards = 0.0;
    NumberFormat numberFormat;
    public ArrayList<SingleHivePostModel> comments;
    public Boolean commentsExpanded = false;
    public Boolean isTranslated = false;
    public Boolean isExpanded = false; // default view not expanded, so display shortBody content i/o body
    public Context ctx;

    public boolean isThread = false;
    public String threadType = "";


    public SingleHivePostModel(JSONObject jsonObject, Context ctx) {
        try {
            numberFormat = NumberFormat.getNumberInstance(Locale.getDefault());
            //load data
            this.fillObjectFromJson(this, jsonObject);

            //fill shortened content
            this.setShortenedContent();

            //initialize comments
            comments = new ArrayList<>();

            //calculate rshares
            this.calculateVoteRshares();
            //calculate total payout
            this.calculateSumPayout();
            //calculate ratio
            this.calculateRatio();

            this.ctx = ctx;
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public String getTrimmedTranslatedContent(){
        String transStr = Utils.trimText(this.translatedText, Constants.TRIMMED_TEXT_SIZE);
        transStr += (this.translatedText.length() > Constants.TRIMMED_TEXT_SIZE)? "": "...";
        return transStr;
    }

    void setShortenedContent(){
        this.shortBody = Utils.trimText(this.body, Constants.TRIMMED_TEXT_SIZE);
        //sanitize content as well for shortbody
        this.shortBody = Utils.sanitizeContent(this.shortBody,false);
        //append 3 dots if original text is long
        this.shortBody += (this.shortBody.length() > Constants.TRIMMED_TEXT_SIZE)? "": "...";
        //also sanitize content
        this.body = Utils.sanitizeContent(this.body, true);
    }


    public float calculateRatio(){
        if (this.voteRshares == 0) {
            this.ratio = this.voteRshares;
        }else {
            this.ratio = this.sumPayout / this.voteRshares;
        }
        return this.ratio;
    }

    public Float calculateVoteRshares(){
        for (int i = 0; i < this.active_votes.length(); i++) {
            try {
                VoteEntryAdapter.VoteEntry vEntry = new VoteEntryAdapter.VoteEntry((this.active_votes.getJSONObject(i)), 0);
                this.voteRshares += vEntry.rshares;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return this.voteRshares;
    }

    public Float calculateSumPayout(){
        this.sumPayout = 0;
        try{
            sumPayout += Float.parseFloat(this.total_payout_value.replaceAll("[^\\d.]", ""));
        }catch(Exception ex){
            ex.printStackTrace();
        }
        try{
            sumPayout += Float.parseFloat(this.pending_payout_value.replaceAll("[^\\d.]", ""));
        }catch(Exception ex){
            ex.printStackTrace();
        }
        try{
            sumPayout += Float.parseFloat(this.curator_payout_value.replaceAll("[^\\d.]", ""));
        }catch(Exception ex){
            ex.printStackTrace();
        }
        try{
            sumPayout += Float.parseFloat(this.author_payout_value.replaceAll("[^\\d.]", ""));
        }catch(Exception ex){
            ex.printStackTrace();
        }
        return sumPayout;
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
                }else{
                    //alternatively try step count as int
                    try {
                        int stepCount = json_metadata.optInt("step_count");
                        return stepCount+"";
                    }catch(Exception ee){
                        ee.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    public Boolean hasJsonMetaData(){
        return json_metadata != null;
    }

    public Boolean hasActivityCount(){
        if (!hasJsonMetaData()) return false;
        return json_metadata.has("step_count");
    }

    public Boolean hasActivityDate(){
        if (!hasJsonMetaData()) return false;
        return json_metadata.has("activityDate");
    }

    public String getActivityDate(){
        if (!hasActivityDate()) return "";
        try {
            return (String) json_metadata.getJSONArray("activityDate").get(0);
        } catch (JSONException e) {
            //Log.e(MainActivity.TAG, Objects.requireNonNull(e.getMessage()));
            Log.e(MainActivity.TAG, "ERROR");
        }
        return "";
    }

    public Boolean postDateMatches(String paramDate){
        try {
            SimpleDateFormat entryDateformatter = new SimpleDateFormat("yyyyMMdd");
            SimpleDateFormat postDateformatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

            // Parsing postDate
            Date date2 = postDateformatter.parse(this.created);

            // To get only the date part, you can use Calendar
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date2);
            // Extracting only the date part
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date dateOnly = calendar.getTime();
            boolean mainDateMatch = entryDateformatter.format(
                    entryDateformatter.parse(paramDate)).equals(
                    entryDateformatter.format(dateOnly));
            String activityDateString = this.getActivityDate();
            if (!activityDateString.equals("")){
                mainDateMatch = entryDateformatter.format(
                        entryDateformatter.parse(paramDate)).equals(
                        entryDateformatter.format(dateOnly));
            }
            return mainDateMatch;
        }catch(Exception e){
            //Log.e(MainActivity.TAG, Objects.requireNonNull(e.getMessage()));
            Log.e(MainActivity.TAG, "ERROR");
        }
        return false;
    }

    public Boolean hasActifitTag(){
        if (!hasJsonMetaData()) return false;
        boolean hasTags = json_metadata.has("tags");
        if (hasTags){
            //json_metadata.getJSONArray("tags");
            try {
                JSONArray tagArray = json_metadata.getJSONArray("tags");
                return tagArray.toString().contains(ctx.getString(R.string.actifit_community)) ||
                        tagArray.toString().contains(ctx.getString(R.string.actifit_name)) ;
            } catch (JSONException e) {
                //Log.e(MainActivity.TAG, Objects.requireNonNull(e.getMessage()));
                Log.e(MainActivity.TAG, "ERROR");
            }
        }
        return false;
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

    @Override
    public int compareTo(SingleHivePostModel compPost) {
        if (this.created == null || compPost.created == null) {
            return 0;
        }
        return this.created.compareTo(compPost.created);
    }


}
