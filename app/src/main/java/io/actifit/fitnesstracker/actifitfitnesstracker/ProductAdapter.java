package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import androidx.cardview.widget.CardView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class ProductAdapter extends ArrayAdapter<SingleProductModel> {

    String username, pkey;
    //JSONArray consumedProducts;
    ArrayList reqtsMet;
    ProgressDialog progress;
    Context ctx;

    public ProductAdapter(Context context, ArrayList<SingleProductModel> activityEntry,
                          String username, String pkey){//, JSONArray consumedProducts) {
        super(context, 0, activityEntry);

        reqtsMet = new ArrayList();

        this.username = username;
        this.pkey = pkey;
        this.ctx = context;
        //this.consumedProducts = consumedProducts;

    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        try {

            // Get the data item for this position
            final SingleProductModel postEntry = getItem(position);
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.product_single_entry, parent, false);
            }

            // Lookup view for data population

            CardView entryContainer = convertView.findViewById(R.id.entryContainer);
            TextView name = convertView.findViewById(R.id.name);
            TextView level = convertView.findViewById(R.id.level);
            TextView count = convertView.findViewById(R.id.count);
            TextView type = convertView.findViewById(R.id.type);
            TextView validity = convertView.findViewById(R.id.validity);
            LinearLayout requirements = convertView.findViewById(R.id.requirements);
            TextView boosts = convertView.findViewById(R.id.boosts);
            ImageView image = convertView.findViewById(R.id.image);
            Button buyAFIT = convertView.findViewById(R.id.buyAFIT);
            Button buyHIVE = convertView.findViewById(R.id.buyHIVE);
            Button activateGadget = convertView.findViewById(R.id.activateGadget);
            Button deactivateGadget = convertView.findViewById(R.id.deactivateGadget);
            EditText friendBeneficiary = convertView.findViewById(R.id.friendBeneficiary);
            LinearLayout boughtInfo = convertView.findViewById(R.id.boughtInfo);

            TextView totalBought = convertView.findViewById(R.id.totalBoughtCount);
            TextView totalConsumed = convertView.findViewById(R.id.totalConsumedCount);
            TextView remainingBoosts = convertView.findViewById(R.id.remainingBoosts);

            // Populate the data into the template view using the data object


            //populate requirements
            String reqsText = getContext().getString(R.string.none);

            //checkmark
            String checkMark = "&#10003;";//"&#9989;";//"✅";
            String xMark = "&#10006;";//"&#10060;";//"❌";
            int colorSuccess = getContext().getResources().getColor(R.color.actifitDarkGreen);
            int colorFail = getContext().getResources().getColor(R.color.actifitRed);


            //boolean reqtMet = true;

            if (postEntry.requirements!=null && postEntry.requirements.length()>0){

                //cleanup first
                requirements.removeAllViews();

                if (postEntry.requirements.length()<1){
                    TextView reqLine = new TextView(getContext());
                    reqLine.setText(Html.fromHtml(reqsText));
                    //reqLine.setTextColor(colorChoice);
                    reqLine.setPadding(0,0,0,5);

                    requirements.addView(reqLine);
                }else {
                    for (int j = 0; j < postEntry.requirements.length(); j++) {
                        JSONObject reqEntry = postEntry.requirements.getJSONObject(j);

                        reqsText = "";

                        int colorChoice = colorSuccess;

                        //process requirement
                        if (reqEntry.has("item")) {
                            if (reqEntry.getBoolean("reqtMet")){
                                reqsText += checkMark;
                            }else{
                                reqsText += xMark;
                                colorChoice = colorFail;
                                //reqtMet = false;
                            }
                            //reqsText += getContext().getString(R.string.user_rank_reqt).replace("_VAL_", postEntry.level+"");
                            if (reqEntry.getString("item").equals("User Rank")) {
                                /*if (Double.parseDouble(MainActivity.userRank) >= Integer.parseInt(reqEntry.getString("level"))) {
                                    reqsText += checkMark;
                                } else {
                                    reqsText += xMark;
                                    colorChoice = colorFail;
                                    reqtMet = false;
                                }*/
                                reqsText += getContext().getString(R.string.user_rank_reqt_short).replace("_VAL_", reqEntry.getString("level")) + "<br/>";
                            } else if (reqEntry.has("AFIT")) {
                                /*if (MainActivity.userFullBalance >= Integer.parseInt(reqEntry.getString("count"))) {
                                    reqsText += checkMark;
                                } else {
                                    reqsText += xMark;
                                    colorChoice = colorFail;
                                    reqtMet = false;
                                }*/
                                reqsText += getContext().getString(R.string.user_afit_balance).replace("_VAL_", reqEntry.getString("count")) + "<br/>";
                            } else {
                                //check if requirement is met
                                /*int consumed_count = 0;
                                int consumed_target = Integer.parseInt(reqEntry.getString("count"));
                                for (int k = 0; k < consumedProducts.length(); k++) {
                                    // Retrieve each JSON object within the JSON array
                                    JSONObject jsonObject = consumedProducts.getJSONObject(k);
                                    //validate this is a matching consumed gadget
                                    if (reqEntry.getString("item").equals(jsonObject.getString("gadget_name"))
                                            && reqEntry.getString("level").equals(jsonObject.getString("gadget_level"))) {
                                        consumed_count += 1;
                                    }
                                }
                                if (consumed_count >= consumed_target) {
                                    reqsText += checkMark;
                                } else {
                                    reqsText += xMark;
                                    colorChoice = colorFail;
                                    reqtMet = false;
                                }*/

                                reqsText += getContext().getString(R.string.at_Least_consumed_reqt)
                                        .replace("_AMOUNT_", reqEntry.getString("count"))
                                        .replace("_ITEM_", reqEntry.getString("item") + " - L" + reqEntry.getString("level")) + "<br/>";

                            }
                            if (!reqsText.equals("")) {
                                TextView reqLine = new TextView(getContext());
                                reqLine.setText(Html.fromHtml(reqsText));
                                reqLine.setTextColor(colorChoice);
                                reqLine.setPadding(0, 0, 0, 5);
                                requirements.addView(reqLine);
                            }
                            //reqLine.setHeight(10);
                            //reqLine.setWidth();

                        }
                    }
                }
            }else{
                TextView reqLine = new TextView(getContext());
                reqLine.setText(Html.fromHtml(reqsText));
                //reqLine.setTextColor(colorChoice);
                reqLine.setPadding(0,0,0,5);

                requirements.addView(reqLine);
            }


            String boostsText = "";

            if (postEntry.boosts!=null && postEntry.boosts.length()>0){
                boostsText = "";
                for (int j=0;j< postEntry.boosts.length();j++){
                    JSONObject reqEntry = postEntry.boosts.getJSONObject(j);

                    /*if (reqEntry.getString("boost_beneficiary").equals("friend")){
                        //flag it as friend rewarding
                        postEntry.isFriendRewarding = true;
                    }*/

                    //process requirement
                    if (reqEntry.has("boost_amount")) {
                        //reqsText += getContext().getString(R.string.user_rank_reqt).replace("_VAL_", postEntry.level+"");
                        boostsText += getContext().getString(R.string.boost_match_note)
                                    .replace("_AMOUNT_", reqEntry.getString("boost_amount"))
                                    .replace("_TYPE_", reqEntry.getString("boost_type")
                                            .replace("percent_reward", "%")
                                            .replace("percent", "%")
                                            .replace("unit", " "))
                                    .replace("_UNIT_",reqEntry.getString("boost_unit"))
                                    .replace("_RECIPIENT_", (reqEntry.getString("boost_beneficiary").equals("friend")?getContext().getString(R.string.to_friend):getContext().getString(R.string.to_you)))
                                    + "<br/>";
                    } else if (reqEntry.has("boost_min_amount")) {
                        boostsText += getContext().getString(R.string.boost_match_note)
                                .replace("_AMOUNT_", reqEntry.getString("boost_amount"))
                                .replace("percent_reward", "%")
                                .replace("percent", "%")
                                .replace("unit", " ")
                                .replace("_TYPE_", reqEntry.getString("boost_unit"))
                                .replace("_RECIPIENT_", (reqEntry.getString("boost_beneficiary").equals("friend")?getContext().getString(R.string.to_friend):getContext().getString(R.string.to_you)))
                                + "<br/>";
                    }
                }
            }


            //if (!reqtMet){
            if (!postEntry.allReqtsMet){
                //disable buy buttons
                buyAFIT.setEnabled(false);
                buyHIVE.setEnabled(false);
            }else{
                buyAFIT.setEnabled(true);
                buyHIVE.setEnabled(true);
            }

            if (postEntry.nonConsumedCopy == SingleProductModel.BOUGHTCOPY){
                buyAFIT.setVisibility(View.GONE);
                buyHIVE.setVisibility(View.GONE);
                if (postEntry.isFriendRewarding){
                    //also show friend beneficiary to direct rewards over
                    friendBeneficiary.setVisibility(View.VISIBLE);
                }else{
                    friendBeneficiary.setVisibility(View.GONE);
                }
                activateGadget.setVisibility(View.VISIBLE);
                //activateGadget.setText(postEntry.name);
                deactivateGadget.setVisibility(View.GONE);
                boughtInfo.setVisibility(View.VISIBLE);
            }else if (postEntry.nonConsumedCopy == SingleProductModel.ACTIVECOPY){
                buyAFIT.setVisibility(View.GONE);
                buyHIVE.setVisibility(View.GONE);
                activateGadget.setVisibility(View.GONE);
                friendBeneficiary.setVisibility(View.GONE);
                deactivateGadget.setVisibility(View.VISIBLE);
                boughtInfo.setVisibility(View.VISIBLE);
                //deactivateGadget.setText(postEntry.name);
            }else{
                buyAFIT.setVisibility(View.VISIBLE);
                //buyAFIT.setText(postEntry.name);
                buyHIVE.setVisibility(View.VISIBLE);
                activateGadget.setVisibility(View.GONE);
                friendBeneficiary.setVisibility(View.GONE);
                deactivateGadget.setVisibility(View.GONE);
                boughtInfo.setVisibility(View.GONE);
            }


            boosts.setText(Html.fromHtml(boostsText));
            //populate boosts


            validity.setText(postEntry.validity);

            name.setText(postEntry.name);


            //level.setText("" + postEntry.level);
            //display stars for every level entry
            String stars = "";
            for (int i=0;i < postEntry.level;i++) {
                stars += '\uf005';
                stars += ' ';
            }
            level.setText(stars);
            count.setText("" + postEntry.count);
            type.setText(postEntry.type);

            totalBought.setText(""+postEntry.totalBought);
            totalConsumed.setText(""+postEntry.totalConsumed);
            remainingBoosts.setText(""+postEntry.remainingBoosts);


            final Context leaderboardContext = this.getContext();

            //set proper image
            String imgPath = getContext().getString(R.string.actifit_gadget_image);

            Handler uiHandler = new Handler(Looper.getMainLooper());
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    //Picasso.with(ctx)
                    Picasso.get().load(imgPath + postEntry.image).into(image);
                }
            });

            //display proper price in HIVE
            String btnTitle = getContext().getString(R.string.buy_now);

            btnTitle += "<br/>"+ postEntry.priceHIVE + " HIVE";

            buyHIVE.setText(Html.fromHtml(btnTitle));


            //display proper price in AFIT
            btnTitle = getContext().getString(R.string.buy_now);
            btnTitle += "<br/>"+  postEntry.priceAFIT + " AFIT";

            buyAFIT.setText(Html.fromHtml(btnTitle));

            ScaleAnimation scaler;
            scaler = new ScaleAnimation(1f, 0.8f, 1f,0.8f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            scaler.setDuration(500);
            scaler.setRepeatMode(Animation.REVERSE);
            scaler.setRepeatCount(Animation.INFINITE);

            //handle click on username

            //View finalConvertView = convertView;
            buyAFIT.setOnClickListener(view -> {


                if (username == null || username.length() <1){
                    Toast.makeText(getContext(), getContext().getString(R.string.username_missing), Toast.LENGTH_LONG).show();
                    return;
                }

                if (postEntry.priceAFIT > MarketActivity.afitBalance){
                    Toast.makeText(getContext(), getContext().getString(R.string.not_enough_afit_balance) + " " + postEntry.name, Toast.LENGTH_LONG).show();
                    return;
                }


                buyAFIT.startAnimation(scaler);
                //buyAFIT.animate().scaleX(0.5f).scaleY(0.5f).setDuration(3000).;
                //buyAFIT.animate().scaleXBy(1).setDuration(3000); //.startAnimation();

                //progress.setMessage(getContext().getString(R.string.processingBuyGadget));
                //progress.show();

                RequestQueue queue = Volley.newRequestQueue(getContext());

                //first make sure if user is properly logged in as we need to connect to server
                if (LoginActivity.accessToken.equals("")){

                    //authorize user login based on credentials if user is already verified
                    if (!pkey.equals("")) {
                        String loginAuthUrl = Utils.apiUrl(getContext())+ getContext().getString(R.string.login_auth);


                        JSONObject loginSettings = new JSONObject();
                        try {
                            loginSettings.put(getContext().getString(R.string.username_param), MainActivity.username);
                            loginSettings.put(getContext().getString(R.string.pkey_param), pkey);
                            loginSettings.put(getContext().getString(R.string.bchain_param), "HIVE");//default always HIVE
                            loginSettings.put(getContext().getString(R.string.keeploggedin_param), false);//TODO make dynamic
                            loginSettings.put(getContext().getString(R.string.login_source), getContext().getString(R.string.android) + BuildConfig.VERSION_NAME);
                        } catch (JSONException e) {
                            //Log.e(MainActivity.TAG, e.getMessage());
                        }

                        //grab auth token for logged in user
                        JsonObjectRequest loginRequest = new JsonObjectRequest(Request.Method.POST,
                                loginAuthUrl, loginSettings,
                                new Response.Listener<JSONObject>() {

                                    @Override
                                    public void onResponse(JSONObject response) {
                                        //store token for reuse when saving settings
                                        try {
                                            if (response.has("success")) {
                                                Log.d(MainActivity.TAG, response.toString());
                                                LoginActivity.accessToken = response.getString(getContext().getString(R.string.login_token));
                                            }
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }
                                    }

                                },
                                new Response.ErrorListener() {
                                    @Override
                                    public void onErrorResponse(VolleyError error) {
                                        // error
                                        Log.e(MainActivity.TAG, "Login error");
                                    }
                                });

                        queue.add(loginRequest);

                    }


                }

                //prepare query and broadcast to bchain

                //param 1
                String op_name = "custom_json";

                //param 2
                JSONObject cstm_params = new JSONObject();
                try {

                    JSONArray required_auths= new JSONArray();

                    JSONArray required_posting_auths = new JSONArray();
                    required_posting_auths.put(MainActivity.username);

                    //cstm_params.put("required_auths", "[]");
                    cstm_params.put("required_auths", required_auths);
                    cstm_params.put("required_posting_auths", required_posting_auths);
                    cstm_params.put("id", "actifit");
                    //cstm_params.put("json", json_op_details);
                    cstm_params.put("json", "{\"transaction\": \"buy-gadget\" , \"gadget\": \"" + postEntry.id + "\"}");

                    JSONArray operation = new JSONArray();
                    operation.put(0, op_name);
                    operation.put(1, cstm_params);

                    String bcastUrl = Utils.apiUrl(getContext())+getContext().getString(R.string.perform_trx_link) +
                            MainActivity.username +
                            "&operation=[" + operation + "]" +
                            "&bchain=HIVE";//hardcoded for now
                    ;


                    //send out transaction
                    JsonObjectRequest transRequest = new JsonObjectRequest(Request.Method.GET,
                            bcastUrl, null,
                            response -> {

                                Log.d(MainActivity.TAG, response.toString());
                                //
                                if (response.has("success")){
                                    //successfully wrote to chain gadget purchase
                                    try {
                                        JSONObject bcastRes = response.getJSONObject("trx").getJSONObject("tx");



                                        String buyUrl = Utils.apiUrl(getContext())+getContext().getString(R.string.buy_gadget_link)+
                                                MainActivity.username+"/"+
                                                postEntry.id+"/"+
                                                bcastRes.get("ref_block_num")+"/"+
                                                bcastRes.get("id")+"/"+
                                                "HIVE";


                                        //send out transaction
                                        JsonObjectRequest buyRequest = new JsonObjectRequest(Request.Method.GET,
                                                buyUrl, null,
                                                response1 -> {
                                                    //progress.dismiss();
                                                    buyAFIT.clearAnimation();
                                                    Log.d(MainActivity.TAG, response1.toString());
                                                    //
                                                    if (!response1.has("error")) {
                                                        //showActivateButton(postEntry, finalConvertView);

                                                        if (postEntry.isFriendRewarding){
                                                            //also show friend beneficiary to direct rewards over
                                                            friendBeneficiary.setVisibility(View.VISIBLE);
                                                        }
                                                        buyAFIT.setVisibility(View.GONE);
                                                        buyHIVE.setVisibility(View.GONE);
                                                        deactivateGadget.setVisibility(View.GONE);
                                                        activateGadget.setVisibility(View.VISIBLE);
                                                        //successfully bought product
                                                        Toast.makeText(getContext(), getContext().getString(R.string.success_buy_product)+ " " +postEntry.name, Toast.LENGTH_LONG).show();
                                                    } else {
                                                        Toast.makeText(getContext(), getContext().getString(R.string.error_buy_product)+ " " +postEntry.name, Toast.LENGTH_LONG).show();
                                                    }
                                                },
                                                error -> {
                                                    // error
                                                    Log.d(MainActivity.TAG, "Error querying blockchain");
                                                    //progress.dismiss();
                                                    buyAFIT.clearAnimation();
                                                    Toast.makeText(getContext(), getContext().getString(R.string.error_buy_product)+ " " +postEntry.name, Toast.LENGTH_LONG).show();
                                                });

                                        queue.add(buyRequest);


                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }else{
                                    //progress.dismiss();
                                    buyAFIT.clearAnimation();
                                    Toast.makeText(getContext(), getContext().getString(R.string.error_buy_product)+ " " +postEntry.name, Toast.LENGTH_LONG).show();
                                }

                            },
                            error -> {
                                // error
                                Log.d(MainActivity.TAG, "Error querying blockchain");
                                //progress.dismiss();
                                buyAFIT.clearAnimation();
                                Toast.makeText(getContext(), getContext().getString(R.string.error_buy_product)+ " " +postEntry.name, Toast.LENGTH_LONG).show();
                            }) {

                                @Override
                                public Map<String, String> getHeaders() throws AuthFailureError {
                                    final Map<String, String> params = new HashMap<>();
                                    params.put("Content-Type", "application/json");
                                    params.put(getContext().getString(R.string.validation_header), getContext().getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken);
                                    return params;
                                }
                            };

                        queue.add(transRequest);
                }  catch (Exception excep) {
                    excep.printStackTrace();
                }

            });


            //activate gadget functionality
            activateGadget.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {


                    if (username == null || username.length() <1){
                        Toast.makeText(getContext(), getContext().getString(R.string.username_missing), Toast.LENGTH_LONG).show();
                        return;
                    }

                    activateGadget.startAnimation(scaler);
                    //buyAFIT.animate().scaleX(0.5f).scaleY(0.5f).setDuration(3000).;
                    //buyAFIT.animate().scaleXBy(1).setDuration(3000); //.startAnimation();

                    //progress.setMessage(getContext().getString(R.string.processingBuyGadget));
                    //progress.show();

                    RequestQueue queue = Volley.newRequestQueue(getContext());

                    //first make sure if user is properly logged in as we need to connect to server
                    if (LoginActivity.accessToken.equals("")){

                        //authorize user login based on credentials if user is already verified
                        if (!pkey.equals("")) {
                            String loginAuthUrl = Utils.apiUrl(getContext())+ getContext().getString(R.string.login_auth);


                            JSONObject loginSettings = new JSONObject();
                            try {
                                loginSettings.put(getContext().getString(R.string.username_param), MainActivity.username);
                                loginSettings.put(getContext().getString(R.string.pkey_param), pkey);
                                loginSettings.put(getContext().getString(R.string.bchain_param), "HIVE");//default always HIVE
                                loginSettings.put(getContext().getString(R.string.keeploggedin_param), false);//TODO make dynamic
                                loginSettings.put(getContext().getString(R.string.login_source), getContext().getString(R.string.android) + BuildConfig.VERSION_NAME);
                            } catch (JSONException e) {
                                //Log.e(MainActivity.TAG, e.getMessage());
                            }

                            //grab auth token for logged in user
                            JsonObjectRequest loginRequest = new JsonObjectRequest(Request.Method.POST,
                                    loginAuthUrl, loginSettings,
                                    new Response.Listener<JSONObject>() {

                                        @Override
                                        public void onResponse(JSONObject response) {
                                            //store token for reuse when saving settings
                                            try {
                                                if (response.has("success")) {
                                                    Log.d(MainActivity.TAG, response.toString());
                                                    LoginActivity.accessToken = response.getString(getContext().getString(R.string.login_token));
                                                }
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                        }

                                    },
                                    new Response.ErrorListener() {
                                        @Override
                                        public void onErrorResponse(VolleyError error) {
                                            // error
                                            Log.e(MainActivity.TAG, "Login error");
                                        }
                                    });

                            queue.add(loginRequest);

                        }


                    }

                    //prepare query and broadcast to bchain

                    //param 1
                    String op_name = "custom_json";

                    //param 2
                    JSONObject cstm_params = new JSONObject();
                    try {

                        JSONArray required_auths= new JSONArray();

                        JSONArray required_posting_auths = new JSONArray();
                        required_posting_auths.put(MainActivity.username);

                        //cstm_params.put("required_auths", "[]");
                        cstm_params.put("required_auths", required_auths);
                        cstm_params.put("required_posting_auths", required_posting_auths);
                        cstm_params.put("id", "actifit");
                        //cstm_params.put("json", json_op_details);
                        if (!postEntry.isFriendRewarding){
                            cstm_params.put("json", "{\"transaction\": \"activate-gadget\" , \"gadget\": \"" + postEntry.id + "\"}");
                        }else{
                            String friendBenefic = friendBeneficiary.getText().toString();
                            if (friendBenefic.equals("")){
                                //send out error
                                Toast.makeText(getContext(), getContext().getString(R.string.error_activate_product_benefic),  Toast.LENGTH_LONG).show();
                                activateGadget.clearAnimation();
                                return;
                            }
                            cstm_params.put("json", "{\"transaction\": \"activate-gadget\" , \"gadget\": \"" + postEntry.id + "\" , \"benefic\": \"" + friendBenefic + "\"}");
                        }

                        JSONArray operation = new JSONArray();
                        operation.put(0, op_name);
                        operation.put(1, cstm_params);

                        String bcastUrl = Utils.apiUrl(getContext())getContext().getString(R.string.perform_trx_link) +
                                MainActivity.username +
                                "&operation=[" + operation + "]" +
                                "&bchain=HIVE";//hardcoded for now
                        ;


                        //send out transaction
                        JsonObjectRequest transRequest = new JsonObjectRequest(Request.Method.GET,
                                bcastUrl, null,
                                new Response.Listener<JSONObject>() {

                                    @Override
                                    public void onResponse(JSONObject response) {

                                        Log.d(MainActivity.TAG, response.toString());
                                        //
                                        if (response.has("success")){
                                            //successfully wrote to chain gadget purchase
                                            try {
                                                JSONObject bcastRes = response.getJSONObject("trx").getJSONObject("tx");



                                                String buyUrl = Utils.apiUrl(getContext())+getContext().getString(R.string.activate_gadget_link)+
                                                        MainActivity.username+"/"+
                                                        postEntry.id+"/"+
                                                        bcastRes.get("ref_block_num")+"/"+
                                                        bcastRes.get("id")+"/"+
                                                        "HIVE";

                                                if (postEntry.isFriendRewarding){
                                                    //append friend beneficiary
                                                    buyUrl += "/"+ friendBeneficiary.getText().toString();
                                                }


                                                //send out transaction
                                                JsonObjectRequest buyRequest = new JsonObjectRequest(Request.Method.GET,
                                                        buyUrl, null,
                                                        new Response.Listener<JSONObject>() {

                                                            @Override
                                                            public void onResponse(JSONObject response) {
                                                                //progress.dismiss();
                                                                activateGadget.clearAnimation();
                                                                Log.d(MainActivity.TAG, response.toString());
                                                                //
                                                                if (!response.has("error")) {
                                                                    //showActivateButton(postEntry, finalConvertView);
                                                                    postEntry.nonConsumedCopy = SingleProductModel.ACTIVECOPY;
                                                                    friendBeneficiary.setVisibility(View.GONE);
                                                                    activateGadget.setVisibility(View.GONE);
                                                                    deactivateGadget.setVisibility(View.VISIBLE);
                                                                    //successfully bought product
                                                                    Toast.makeText(getContext(), getContext().getString(R.string.success_activate_product)+ " " +postEntry.name, Toast.LENGTH_LONG).show();
                                                                } else {
                                                                    Toast.makeText(getContext(), getContext().getString(R.string.error_activate_product), Toast.LENGTH_LONG).show();
                                                                }
                                                            }
                                                        },
                                                        new Response.ErrorListener() {
                                                            @Override
                                                            public void onErrorResponse(VolleyError error) {
                                                                // error
                                                                Log.d(MainActivity.TAG, "Error querying blockchain");
                                                                //progress.dismiss();
                                                                activateGadget.clearAnimation();
                                                                Toast.makeText(getContext(), getContext().getString(R.string.error_activate_product), Toast.LENGTH_LONG).show();
                                                            }
                                                        });

                                                queue.add(buyRequest);


                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                        }else{
                                            //progress.dismiss();
                                            activateGadget.clearAnimation();
                                            Toast.makeText(getContext(), getContext().getString(R.string.error_activate_product), Toast.LENGTH_LONG).show();
                                        }

                                    }

                                },
                                new Response.ErrorListener() {
                                    @Override
                                    public void onErrorResponse(VolleyError error) {
                                        // error
                                        Log.d(MainActivity.TAG, "Error querying blockchain");
                                        //progress.dismiss();
                                        activateGadget.clearAnimation();
                                        Toast.makeText(getContext(), getContext().getString(R.string.error_activate_product), Toast.LENGTH_LONG).show();
                                    }
                                }) {

                            @Override
                            public Map<String, String> getHeaders() throws AuthFailureError {
                                final Map<String, String> params = new HashMap<>();
                                params.put("Content-Type", "application/json");
                                params.put(getContext().getString(R.string.validation_header), getContext().getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken);
                                return params;
                            }
                        };

                        queue.add(transRequest);
                    }  catch (Exception excep) {
                        excep.printStackTrace();
                    }

                }
            });

            //deactivate gadget functionality
            deactivateGadget.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {


                    if (username == null || username.length() <1){
                        Toast.makeText(getContext(), getContext().getString(R.string.username_missing), Toast.LENGTH_LONG).show();
                        return;
                    }

                    deactivateGadget.startAnimation(scaler);
                    //buyAFIT.animate().scaleX(0.5f).scaleY(0.5f).setDuration(3000).;
                    //buyAFIT.animate().scaleXBy(1).setDuration(3000); //.startAnimation();

                    //progress.setMessage(getContext().getString(R.string.processingBuyGadget));
                    //progress.show();

                    RequestQueue queue = Volley.newRequestQueue(getContext());

                    //first make sure if user is properly logged in as we need to connect to server
                    if (LoginActivity.accessToken.equals("")){

                        //authorize user login based on credentials if user is already verified
                        if (!pkey.equals("")) {
                            String loginAuthUrl = Utils.apiUrl(getContext())+ getContext().getString(R.string.login_auth);


                            JSONObject loginSettings = new JSONObject();
                            try {
                                loginSettings.put(getContext().getString(R.string.username_param), MainActivity.username);
                                loginSettings.put(getContext().getString(R.string.pkey_param), pkey);
                                loginSettings.put(getContext().getString(R.string.bchain_param), "HIVE");//default always HIVE
                                loginSettings.put(getContext().getString(R.string.keeploggedin_param), false);//TODO make dynamic
                                loginSettings.put(getContext().getString(R.string.login_source), getContext().getString(R.string.android) + BuildConfig.VERSION_NAME);
                            } catch (JSONException e) {
                                //Log.e(MainActivity.TAG, e.getMessage());
                            }

                            //grab auth token for logged in user
                            JsonObjectRequest loginRequest = new JsonObjectRequest(Request.Method.POST,
                                    loginAuthUrl, loginSettings,
                                    new Response.Listener<JSONObject>() {

                                        @Override
                                        public void onResponse(JSONObject response) {
                                            //store token for reuse when saving settings
                                            try {
                                                if (response.has("success")) {
                                                    Log.d(MainActivity.TAG, response.toString());
                                                    LoginActivity.accessToken = response.getString(getContext().getString(R.string.login_token));
                                                }
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                        }

                                    },
                                    new Response.ErrorListener() {
                                        @Override
                                        public void onErrorResponse(VolleyError error) {
                                            // error
                                            Log.e(MainActivity.TAG, "Login error");
                                        }
                                    });

                            queue.add(loginRequest);

                        }


                    }

                    //prepare query and broadcast to bchain

                    //param 1
                    String op_name = "custom_json";

                    //param 2
                    JSONObject cstm_params = new JSONObject();
                    try {

                        JSONArray required_auths= new JSONArray();

                        JSONArray required_posting_auths = new JSONArray();
                        required_posting_auths.put(MainActivity.username);

                        //cstm_params.put("required_auths", "[]");
                        cstm_params.put("required_auths", required_auths);
                        cstm_params.put("required_posting_auths", required_posting_auths);
                        cstm_params.put("id", "actifit");
                        //cstm_params.put("json", json_op_details);
                        cstm_params.put("json", "{\"transaction\": \"deactivate-gadget\" , \"gadget\": \"" + postEntry.id + "\"}");

                        JSONArray operation = new JSONArray();
                        operation.put(0, op_name);
                        operation.put(1, cstm_params);

                        String bcastUrl = Utils.apiUrl(getContext())+getContext().getString(R.string.perform_trx_link) +
                                MainActivity.username +
                                "&operation=[" + operation + "]" +
                                "&bchain=HIVE";//hardcoded for now
                        ;


                        //send out transaction
                        JsonObjectRequest transRequest = new JsonObjectRequest(Request.Method.GET,
                                bcastUrl, null,
                                new Response.Listener<JSONObject>() {

                                    @Override
                                    public void onResponse(JSONObject response) {

                                        Log.d(MainActivity.TAG, response.toString());
                                        //
                                        if (response.has("success")){
                                            //successfully wrote to chain gadget purchase
                                            try {
                                                JSONObject bcastRes = response.getJSONObject("trx").getJSONObject("tx");


                                                String buyUrl = Utils.apiUrl(getContext())+getContext().getString(R.string.deactivate_gadget_link)+
                                                        MainActivity.username+"/"+
                                                        postEntry.id+"/"+
                                                        bcastRes.get("ref_block_num")+"/"+
                                                        bcastRes.get("id")+"/"+
                                                        "HIVE";


                                                //send out transaction
                                                JsonObjectRequest buyRequest = new JsonObjectRequest(Request.Method.GET,
                                                        buyUrl, null,
                                                        new Response.Listener<JSONObject>() {

                                                            @Override
                                                            public void onResponse(JSONObject response) {
                                                                //progress.dismiss();
                                                                deactivateGadget.clearAnimation();
                                                                Log.d(MainActivity.TAG, response.toString());
                                                                //
                                                                if (!response.has("error")) {
                                                                    //showActivateButton(postEntry, finalConvertView);
                                                                    postEntry.nonConsumedCopy = SingleProductModel.BOUGHTCOPY;

                                                                    if (postEntry.isFriendRewarding) {
                                                                        friendBeneficiary.setVisibility(View.VISIBLE);
                                                                    }else{
                                                                        friendBeneficiary.setVisibility(View.GONE);
                                                                    }
                                                                    activateGadget.setVisibility(View.VISIBLE);
                                                                    deactivateGadget.setVisibility(View.GONE);
                                                                    //successfully bought product
                                                                    Toast.makeText(getContext(), getContext().getString(R.string.success_deactivate_product)+ " " +postEntry.name, Toast.LENGTH_LONG).show();
                                                                } else {
                                                                    Toast.makeText(getContext(), getContext().getString(R.string.error_deactivate_product), Toast.LENGTH_LONG).show();
                                                                }
                                                            }
                                                        },
                                                        new Response.ErrorListener() {
                                                            @Override
                                                            public void onErrorResponse(VolleyError error) {
                                                                // error
                                                                Log.d(MainActivity.TAG, "Error querying blockchain");
                                                                //progress.dismiss();
                                                                deactivateGadget.clearAnimation();
                                                                Toast.makeText(getContext(), getContext().getString(R.string.error_deactivate_product), Toast.LENGTH_LONG).show();
                                                            }
                                                        });

                                                queue.add(buyRequest);


                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                        }else{
                                            //progress.dismiss();
                                            deactivateGadget.clearAnimation();
                                            Toast.makeText(getContext(), getContext().getString(R.string.error_deactivate_product), Toast.LENGTH_LONG).show();
                                        }

                                    }

                                },
                                new Response.ErrorListener() {
                                    @Override
                                    public void onErrorResponse(VolleyError error) {
                                        // error
                                        Log.d(MainActivity.TAG, "Error querying blockchain");
                                        //progress.dismiss();
                                        deactivateGadget.clearAnimation();
                                        Toast.makeText(getContext(), getContext().getString(R.string.error_deactivate_product), Toast.LENGTH_LONG).show();
                                    }
                                }) {

                            @Override
                            public Map<String, String> getHeaders() throws AuthFailureError {
                                final Map<String, String> params = new HashMap<>();
                                params.put("Content-Type", "application/json");
                                params.put(getContext().getString(R.string.validation_header), getContext().getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken);
                                return params;
                            }
                        };

                        queue.add(transRequest);
                    }  catch (Exception excep) {
                        excep.printStackTrace();
                    }

                }
            });

            //Buy with HIVE
            //String finalHivePrice = hivePrice;
            buyHIVE.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    if (username == null || username.length() <1){
                        Toast.makeText(getContext(), getContext().getString(R.string.username_missing), Toast.LENGTH_LONG).show();
                        return;
                    }

                    //make sure user has his active key set prior to moving ahead
                    SharedPreferences sharedPreferences = getContext().getSharedPreferences("actifitSets",Context.MODE_PRIVATE);
                    final String actvKey = sharedPreferences.getString("actvKey","");

                    if (actvKey.equals("")){
                        //cannot proceed, prompt user to set his active key under settings
                        Toast.makeText(getContext(), getContext().getString(R.string.unableBuyGadgetHive) , Toast.LENGTH_LONG).show();
                        return;
                    }

                    buyHIVE.startAnimation(scaler);

                    RequestQueue queue = Volley.newRequestQueue(getContext());

                    //first make sure if user is properly logged in as we need to connect to server
                    if (LoginActivity.accessToken.equals("")){

                        //authorize user login based on credentials if user is already verified
                        if (!pkey.equals("")) {
                            String loginAuthUrl = Utils.apiUrl(getContext())+ getContext().getString(R.string.login_auth);


                            JSONObject loginSettings = new JSONObject();
                            try {
                                loginSettings.put(getContext().getString(R.string.username_param), MainActivity.username);
                                loginSettings.put(getContext().getString(R.string.pkey_param), pkey);
                                loginSettings.put(getContext().getString(R.string.bchain_param), "HIVE");//default always HIVE
                                loginSettings.put(getContext().getString(R.string.keeploggedin_param), false);//TODO make dynamic
                                loginSettings.put(getContext().getString(R.string.login_source), getContext().getString(R.string.android) + BuildConfig.VERSION_NAME);
                            } catch (JSONException e) {
                                //Log.e(MainActivity.TAG, e.getMessage());
                            }

                            //grab auth token for logged in user
                            JsonObjectRequest loginRequest = new JsonObjectRequest(Request.Method.POST,
                                    loginAuthUrl, loginSettings,
                                    new Response.Listener<JSONObject>() {

                                        @Override
                                        public void onResponse(JSONObject response) {
                                            //store token for reuse when saving settings
                                            try {
                                                if (response.has("success")) {
                                                    Log.d(MainActivity.TAG, response.toString());
                                                    LoginActivity.accessToken = response.getString(getContext().getString(R.string.login_token));
                                                }
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                        }

                                    },
                                    new Response.ErrorListener() {
                                        @Override
                                        public void onErrorResponse(VolleyError error) {
                                            // error
                                            Log.e(MainActivity.TAG, "Login error");
                                        }
                                    });

                            queue.add(loginRequest);

                        }


                    }

                    //prepare query and broadcast to bchain

                    //param 1
                    String op_name = "transfer";

                    //param 2
                    JSONObject cstm_params = new JSONObject();
                    try {

                        //cstm_params.put("required_auths", "[]");
                        cstm_params.put("from", MainActivity.username);
                        cstm_params.put("to", getContext().getString(R.string.actifit_market));
                        cstm_params.put("amount", postEntry.priceHIVE+ " HIVE");
                        //cstm_params.put("json", json_op_details);
                        cstm_params.put("memo", "buy-gadget:"+postEntry.id);

                        JSONArray operation = new JSONArray();
                        operation.put(0, op_name);
                        operation.put(1, cstm_params);

                        JSONArray op_array = new JSONArray();
                        op_array.put(operation);

                        String bcastUrl = Utils.apiUrl(getContext())+getContext().getString(R.string.perform_trx_post_link) +
                                MainActivity.username +
                                "&bchain=HIVE";//hardcoded for now
                        ;

                        //sending post data
                        JSONObject body = new JSONObject();

                        body.put("operation", op_array.toString());
                        body.put("active", actvKey);

                        //send out transaction
                        JsonObjectRequest transRequest = new JsonObjectRequest(Request.Method.POST,
                                bcastUrl, body,
                                new Response.Listener<JSONObject>() {

                                    @Override
                                    public void onResponse(JSONObject response) {

                                        Log.d(MainActivity.TAG, response.toString());
                                        //
                                        if (response.has("success")){
                                            //successfully wrote to chain gadget purchase
                                            try {
                                                JSONObject bcastRes = response.getJSONObject("trx").getJSONObject("tx");



                                                String buyUrl = Utils.apiUrl(getContext())+getContext().getString(R.string.buy_gadget_hive_link)+
                                                        MainActivity.username+"/"+
                                                        postEntry.id+"/"+
                                                        bcastRes.get("ref_block_num")+"/"+
                                                        bcastRes.get("id")+"/"+
                                                        "HIVE";


                                                //send out transaction
                                                JsonObjectRequest buyRequest = new JsonObjectRequest(Request.Method.GET,
                                                        buyUrl, null,
                                                        new Response.Listener<JSONObject>() {

                                                            @Override
                                                            public void onResponse(JSONObject response) {
                                                                //progress.dismiss();
                                                                buyHIVE.clearAnimation();
                                                                Log.d(MainActivity.TAG, response.toString());
                                                                //
                                                                if (!response.has("error")) {
                                                                    //successfully bought product
                                                                    //showActivateButton(postEntry, finalConvertView);
                                                                    if (postEntry.isFriendRewarding){
                                                                        //also show friend beneficiary to direct rewards over
                                                                        friendBeneficiary.setVisibility(View.VISIBLE);
                                                                    }
                                                                    buyAFIT.setVisibility(View.GONE);
                                                                    buyHIVE.setVisibility(View.GONE);
                                                                    deactivateGadget.setVisibility(View.GONE);
                                                                    activateGadget.setVisibility(View.VISIBLE);

                                                                    Toast.makeText(getContext(), getContext().getString(R.string.success_buy_product)+ " " +postEntry.name, Toast.LENGTH_LONG).show();
                                                                } else {
                                                                    Toast.makeText(getContext(), getContext().getString(R.string.error_buy_product)+ " " +postEntry.name, Toast.LENGTH_LONG).show();
                                                                }
                                                            }
                                                        },
                                                        new Response.ErrorListener() {
                                                            @Override
                                                            public void onErrorResponse(VolleyError error) {
                                                                // error
                                                                //progress.dismiss();
                                                                buyHIVE.clearAnimation();
                                                                Log.d(MainActivity.TAG, "Error querying blockchain");
                                                                Toast.makeText(getContext(), getContext().getString(R.string.error_buy_product)+ " " +postEntry.name, Toast.LENGTH_LONG).show();
                                                            }
                                                        });

                                                queue.add(buyRequest);


                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                                //progress.dismiss();
                                                buyHIVE.clearAnimation();
                                                Toast.makeText(getContext(), getContext().getString(R.string.error_buy_product)+ " " +postEntry.name, Toast.LENGTH_LONG).show();
                                            }
                                        }else{
                                            //progress.dismiss();
                                            buyHIVE.clearAnimation();
                                            Toast.makeText(getContext(), getContext().getString(R.string.error_buy_product)+ " " +postEntry.name, Toast.LENGTH_LONG).show();
                                        }

                                    }

                                },
                                new Response.ErrorListener() {
                                    @Override
                                    public void onErrorResponse(VolleyError error) {
                                        // error
                                        Log.d(MainActivity.TAG, "Error querying blockchain");
                                        //progress.dismiss();
                                        buyHIVE.clearAnimation();
                                        Toast.makeText(getContext(), getContext().getString(R.string.error_buy_product)+ " " +postEntry.name, Toast.LENGTH_LONG).show();
                                    }
                                }) {

                            @Override
                            public Map<String, String> getHeaders() throws AuthFailureError {
                                final Map<String, String> params = new HashMap<>();
                                params.put("Content-Type", "application/json");
                                params.put(getContext().getString(R.string.validation_header), getContext().getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken);
                                return params;
                            }


                            /*@Override
                            public Map getParams() {
                                Map params = new HashMap();

                                params.put("operation", operation.toString());
                                params.put("active", actvKey);

                                return params;
                            }*/
/*
                            @Override
                            public byte[] getBody() {
                            //public byte[] getBody() throws AuthFailureError {
                                //        Map<String, String> params = getParams();
                                Map<String, String> params = new HashMap<String, String>();
                                params.put("operation", operation.toString());
                                params.put("active", actvKey);
                                if (params != null && params.size() > 0) {

                                    return encodeParameters(params, getParamsEncoding());

                                }
                                return null;

                            }*/

                            private byte[] encodeParameters(Map<String, String> params, String paramsEncoding) {
                                StringBuilder encodedParams = new StringBuilder();
                                try {
                                    for (Map.Entry<String, String> entry : params.entrySet()) {
                                        encodedParams.append(URLEncoder.encode(entry.getKey(), paramsEncoding));
                                        encodedParams.append('=');
                                        encodedParams.append(URLEncoder.encode(entry.getValue(), paramsEncoding));
                                        encodedParams.append('&');
                                    }
                                    return encodedParams.toString().getBytes(paramsEncoding);
                                } catch (UnsupportedEncodingException uee) {
                                    throw new RuntimeException("Encoding not supported: " + paramsEncoding, uee);
                                }
                            }

                        };

                        queue.add(transRequest);
                    }  catch (Exception excep) {
                        excep.printStackTrace();
                    }

                }
            });

        }catch(Exception exp){
            exp.printStackTrace();
        }

        // Return the completed view to render on screen
        return convertView;
    }

}
