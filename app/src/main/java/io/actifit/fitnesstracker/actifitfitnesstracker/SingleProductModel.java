package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SingleProductModel implements Comparable<SingleProductModel>{

    public String id;
    public String name;
    public boolean active;
    public String type;
    public JSONArray price;
    public int level;
    public String image;
    public String provider;
    public String description;
    public JSONObject benefits;
    public JSONArray boosts;
    public JSONArray requirements;
    public boolean allReqtsMet;
    public int count;
    public String validity;
    public int validityVal;
    public Double priceAFIT;
    public Double priceHIVE;
    public boolean isFriendRewarding = false;
    public int nonConsumedCopy;
    public int totalBought;
    public int totalConsumed;
    public int remainingBoosts;
    public boolean specialevent;
    public String event;

    public final static int NOCOPY = 0;
    public final static int BOUGHTCOPY = 1;
    public final static int ACTIVECOPY = 2;


    public SingleProductModel(JSONObject jsonObject, JSONObject afitPrice){
        try {
            this.id = jsonObject.has("_id") ? jsonObject.getString("_id"):"";
            this.name = jsonObject.has("name") ? jsonObject.getString("name"):"";
            this.type = jsonObject.has("type") ? jsonObject.getString("type"):"";
            this.image = jsonObject.has("image") ? jsonObject.getString("image"):"";
            this.provider = jsonObject.has("provider") ? jsonObject.getString("provider"):"";
            this.description = jsonObject.has("description") ? jsonObject.getString("description"):"";
            this.benefits = jsonObject.has("benefits") ? jsonObject.getJSONObject("benefits"): new JSONObject();

            this.price = jsonObject.has("price") ? jsonObject.getJSONArray("price"):new JSONArray();
            this.boosts = (this.benefits!=null && this.benefits.has("boosts")) ? this.benefits.getJSONArray("boosts"):new JSONArray();

            if (jsonObject.has("requirements")) {
                this.requirements = !jsonObject.getString("requirements").equals("") ?jsonObject.getJSONArray("requirements"):new JSONArray();
            }
            this.active = jsonObject.has("active") ? jsonObject.getBoolean("active"):false;


            this.count = jsonObject.has("count") ? jsonObject.getInt("count"):0;
            this.level = jsonObject.has("level") ? jsonObject.getInt("level"):0;
            this.validity = (this.benefits!=null && this.benefits.has("time_span")) ? this.benefits.getString("time_span") + " " +this.benefits.getString("time_unit"):"";
            this.validityVal = (this.benefits!=null && this.benefits.has("time_span")) ? Integer.parseInt(this.benefits.getString("time_span")):0;

            this.priceAFIT = Double.parseDouble(this.grabPrice("AFIT", true));
            if (afitPrice != null && afitPrice.has("afitHiveLastPrice")) {
                this.priceHIVE = Double.parseDouble(String.format("%.3f", this.priceAFIT * afitPrice.getDouble("afitHiveLastPrice")));
            }

            this.specialevent = jsonObject.has("specialevent") ? jsonObject.getBoolean("specialevent"):false;
            this.event = jsonObject.has("event") ? jsonObject.getString("event"):"";

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    //sort by level
    @Override
    public int compareTo(SingleProductModel singleProductModel) {
        try {
            return this.level - singleProductModel.level;
        }catch(Exception excp){
            return 0;
        }
    }


    public String grabPrice(String currency, boolean priceOnly){
        String price = "0.0";
        for (int i=0;i < this.price.length();i++) {
            JSONObject priceEntry = null;
            try {
                priceEntry = this.price.getJSONObject(i);

                if (priceEntry.has("currency")){
                    currency = priceEntry.getString("currency");
                    if (currency.equals("AFIT")) {
                        price = priceEntry.getString("price");
                        break;
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (priceOnly){
            return price;
        }
        return price + " " + currency;

    }
}
