package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.StrictMode;
import androidx.browser.customtabs.CustomTabsIntent;
import android.util.Log;

import com.github.scribejava.apis.FitbitApi20;
import com.github.scribejava.apis.fitbit.FitBitOAuth2AccessToken;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;


/**
 * this class and relevant Fitbit work across our Actifit solution has been constructed and adapted
 * based on great work done at https://git.nxfifteen.rocks/nxad/test-android-fitbit-oauth/
 * as well as Fitbit official documentation
 */


class NxFitbitHelper {
    @SuppressWarnings("FieldCanBeLocal")
    private String clientid = "";
    @SuppressWarnings("FieldCanBeLocal")
    private String clientSecret = "";
    @SuppressWarnings("FieldCanBeLocal")
    private static String apiCallback = "";
    @SuppressWarnings("FieldCanBeLocal")
    private String apiScope = "activity heartrate location profile weight";

    private JSONObject apiValueProfile;

    private String authCode = "";
    private OAuth20Service service;
    private OAuth2AccessToken accessToken;

    private String fitBitAPIUrl = "https://api.fitbit.com/1/";

    //constructor
    NxFitbitHelper(Context ctxt, boolean postScreen) {
        clientid = ctxt.getString(R.string.fitbit_clientid);
        clientSecret = ctxt.getString(R.string.fitbit_clientsec);
        apiCallback = postScreen?ctxt.getString(R.string.fitbit_apicb)
                :ctxt.getString(R.string.fitbit_apicbmain);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    public String getUserId(){
        return ((FitBitOAuth2AccessToken)this.accessToken).getUserId();
    }

    //function handles redirecting user to proper authorization url via customtabs
    static void sendUserToAuthorisation(Context callingContext, boolean postScreen) {
        NxFitbitHelper helperClass = new NxFitbitHelper(callingContext, postScreen);
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

        builder.setToolbarColor(callingContext.getResources().getColor(R.color.actifitRed));

        //animation for showing and closing fitbit authorization screen
        builder.setStartAnimations(callingContext, R.anim.slide_in_right, R.anim.slide_out_left);

        //animation for back button clicks
        builder.setExitAnimations(callingContext, android.R.anim.slide_in_left,
                android.R.anim.slide_out_right);

        CustomTabsIntent customTabsIntent = builder.build();

        //add expiration period
        //more details about options found here
        //https://dev.fitbit.com/build/reference/web-api/oauth2/#authorization-page
        Map<String, String> addFitbitOptions = new HashMap<String, String>();
        addFitbitOptions.put("expires_in", "2592000");//1 month

        // Remove FLAG_ACTIVITY_NEW_TASK
        Intent intent = customTabsIntent.intent;
        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        //intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);


        customTabsIntent.launchUrl(callingContext, Uri.parse(helperClass.getAuthorizationUrl(addFitbitOptions)));
    }

    private String getAuthorizationUrl(Map<String, String> addFitbitOptions) {
        return getService().getAuthorizationUrl(addFitbitOptions);
    }

    //function handles building up connection to fitbit API with relevant params
    private OAuth20Service createService() {
        return new ServiceBuilder(clientid)
                .apiSecret(clientSecret)
                .defaultScope(apiScope)
                .callback(apiCallback)
                .build(FitbitApi20.instance());
    }

    private OAuth20Service getService() {
        if (service == null) {
            service = createService();
        }
        return service;
    }

    private void setAuthCodeFromIntent(Uri returnUrl) {
        authCode = returnUrl.getQueryParameter("code");
        Log.d(MainActivity.TAG,"Auth Code set to " + authCode);
    }

    //function handles connecting and grabbing an access token
    private void requestAccessToken() {
        try {
            OAuth20Service serviceCall = getService();
            accessToken = serviceCall.getAccessToken(authCode);
            Log.d(MainActivity.TAG,"accesstoken=" + accessToken);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    //function handles processing actual calls to grab Fitbit data for syncing purposes
    JSONObject makeApiRequest(String endPointUrl) throws InterruptedException, ExecutionException, IOException {
        OAuth20Service serviceCall = getService();

        final OAuthRequest activityrequest = new OAuthRequest(Verb.GET, fitBitAPIUrl + endPointUrl);
        serviceCall.signRequest(accessToken, activityrequest);

        final Response response;
        response = serviceCall.execute(activityrequest);

        try {
            return new JSONObject(response.getBody());
        } catch (JSONException e) {
            return null;
        }
    }

    void requestAccessTokenFromIntent(Uri returnUrl) {
        setAuthCodeFromIntent(returnUrl);
        requestAccessToken();
    }

    JSONObject getUserProfile() throws InterruptedException, ExecutionException, IOException {
        if (apiValueProfile == null) {
            apiValueProfile = makeApiRequest("user/-/profile.json");
        }
        return apiValueProfile;
    }

    // Function handles retrieving all auto-recorded recorded today for this user
    // SoughtInfo param decides whether we need: "step", "distance", or other available options
    // For more info https://dev.fitbit.com/build/reference/web-api/activity/
    JSONObject getActivityByDate(String soughtInfo, String targetDate) throws InterruptedException, ExecutionException, IOException {

        // Define which activities we are interested in.
        // Here we only look for auto-registered activities which is fetched using this param
        // For more info https://dev.fitbit.com/build/reference/web-api/activity/
        // Format used: //user/[user-id]/[resource-path]/date/[date]/[period].json
        String queryFormat = "user/-/activities/tracker/" + soughtInfo + "/date/"+targetDate+"/1d.json";

        return makeApiRequest(queryFormat );

    }

    String getFieldFromProfile(String jsonFieldName) {
        String jsonFieldValue = "An error occurred";
        if (apiValueProfile == null) {
            try {
                apiValueProfile = makeApiRequest("user/-/profile.json");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        try {
            jsonFieldValue = apiValueProfile.getJSONObject("user").getString(jsonFieldName);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonFieldValue;
    }

}
