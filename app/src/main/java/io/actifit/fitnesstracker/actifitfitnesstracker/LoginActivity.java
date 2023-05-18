package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AnticipateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.splashscreen.SplashScreen;

import com.android.volley.BuildConfig;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.json.JSONException;
import org.json.JSONObject;



public class LoginActivity extends BaseActivity {

    public String username="";
    private String pkey="";
    public static String accessToken="";
    private RequestQueue queue;
    boolean directionDecided = false;

    EditText userEntry;
    EditText keyEntry;
    Button loginBtn;
    Button skipBtn;
    ProgressDialog progress;

    Context ctx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        //launch splashscreen and append custom exit animation
        SplashScreen.installSplashScreen(this);

                /*.setOnExitAnimationListener(splashScreenView -> {
            final ObjectAnimator slideUp = ObjectAnimator.ofFloat(
                    splashScreenView,
                    String.valueOf(View.TRANSLATION_Y),
                    0f,
                    -splashScreenView.getView().getHeight()
            );
            slideUp.setInterpolator(new AnticipateInterpolator());
            slideUp.setDuration(200L);

            // Call SplashScreenView.remove at the end of your custom animation.
            slideUp.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    splashScreenView.remove();
                }
            });

            // Run your animation.
            slideUp.start();
        });*/

        super.onCreate(savedInstanceState);

        setContentView(R.layout.login_page);

        //installSplashScreen(this);
        //androidx.core.splashscreen.R.

        ctx = this;

        //load login hero image
        final LinearLayout heroImage = findViewById(R.id.login_hero);
        Handler uiAltHandler = new Handler(Looper.getMainLooper());
        String loginImgUrl = getString(R.string.login_img_url);

        // Request the rank of the user while expecting a JSON response
        JsonObjectRequest imgRequest = new JsonObjectRequest
                (Request.Method.GET, loginImgUrl, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        uiAltHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                //Picasso.with(ctx)
                                //fallback image
                                String url = getString(R.string.default_login_img);
                                if (response.has("imgUrl")){
                                    try {
                                        url = response.getString("imgUrl");
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }

                                //temp imageview to load background onto, to avoid other approach possibly not always loading
                                //image due to garbage collection
                                ImageView img = new ImageView(ctx);

                                Picasso.get()
                                        .load(url)
                                        //.placeholder()
                                        .into(img, new Callback() {
                                            @Override
                                            public void onSuccess() {

                                                heroImage.setBackgroundDrawable(img.getDrawable());
                                            }

                                            @Override
                                            public void onError(Exception e) {

                                            }

                                });
                                /*Picasso.get()
                                        .load(url)
                                        //.placeholder()
                                        .into(new Target() {
                                            @Override
                                            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                                                heroImage.setBackground(new BitmapDrawable(bitmap));
                                                heroImage.refreshDrawableState();
                                            }

                                            @Override
                                            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                                                //Toast.makeText(MainActivity.this, "Error : loading wallpaper", Toast.LENGTH_SHORT).show();
                                            }

                                            @Override
                                            public void onPrepareLoad(Drawable placeHolderDrawable) {

                                            }
                                        });*/

                            }
                        });
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // error
                        Log.e(MainActivity.TAG, "Load image error");
                    }

        });

        queue = Volley.newRequestQueue(this);

        queue.add(imgRequest);

        //delay rendering to keep splashscreen showing while we check course of action
        // Set up an OnPreDrawListener to the root view.
        final View content = findViewById(android.R.id.content);
        content.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        // Check if the initial data is ready.
                        if (directionDecided) {
                            // The content is ready; start drawing.
                            content.getViewTreeObserver().removeOnPreDrawListener(this);
                            return true;
                        } else {
                            // The content is not ready; suspend.
                            return false;
                        }
                    }
                });

        final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

        username = sharedPreferences.getString("actifitUser","");
        pkey = sharedPreferences.getString("actifitPst","");


        queryAPI(username, pkey, true);

    }

    private void queryAPI(final String userParam, final String pstKeyParam, boolean firstLoad){
        if (userParam.equals("") || pstKeyParam.equals("")){
            //if none has been set, show normal login screen
            assessLogin(false, firstLoad);
        }else{
            //otherwise check if user can skip login
            String loginAuthUrl = getString(R.string.live_server)
                    + getString(R.string.login_auth);


            JSONObject loginSettings = new JSONObject();
            try {
                loginSettings.put(getString(R.string.username_param), userParam);
                loginSettings.put(getString(R.string.pkey_param), pstKeyParam);
                loginSettings.put(getString(R.string.bchain_param), "HIVE");//default always HIVE
                loginSettings.put(getString(R.string.keeploggedin_param), true);//TODO make dynamic
                loginSettings.put(getString(R.string.login_source), getString(R.string.android) + BuildConfig.VERSION_NAME);
            } catch (JSONException e) {
                //Log.e(MainActivity.TAG, e.getMessage());
            }

            //grab auth token for logged in user
            JsonObjectRequest loginRequest = new JsonObjectRequest(Request.Method.POST,
                    loginAuthUrl, loginSettings,
                    response -> {
                        //store token for reuse when saving settings
                        try {
                            if (response.has("success")) {
                                Log.d(MainActivity.TAG, response.toString());
                                accessToken = response.getString(getString(R.string.login_token));

                                //also store username and posting key
                                final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putString("actifitUser", userParam
                                        .trim().toLowerCase().replace("@",""));
                                editor.putString("actifitPst", pstKeyParam);
                                editor.apply();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        assessLogin(true, firstLoad);
                    },
                    error -> {
                        // error
                        Log.e(MainActivity.TAG, "Login error");
                        assessLogin(true, firstLoad);
                    });

            //to enable waiting for longer time with extra retry
            loginRequest.setRetryPolicy(new DefaultRetryPolicy(
                    MainActivity.connectTimeout,
                    MainActivity.connectMaxRetries,
                    MainActivity.connectSubsequentRetryDelay));

            queue.add(loginRequest);

        }
    }

    private void proceedMain(){
        //has login
        Intent mainIntent = new Intent(this, MainActivity.class);
        if (getIntent().getExtras()!=null && getIntent().getExtras().containsKey("url")) {
            mainIntent.putExtra("url", getIntent().getExtras().getString("url"));
        }
        startActivity(mainIntent);

        //let's close splashscreen
        directionDecided = true;

        //must finish this activity (the login activity will not be shown when click back in main activity)
        finish();
    }

    private void assessLogin(boolean notify, boolean firstLoad){

        //access token works, user logged in, let's proceed
        if (!accessToken.equals("")) {
            proceedMain();
        }
        else {
            if (firstLoad) {
                initializeItems();
            }
            //problem logging in
            if (progress!=null && progress.isShowing()) {
                progress.dismiss();
            }
            if (notify){
                Toast.makeText(ctx,getString(R.string.incorrect_credentials),Toast.LENGTH_LONG).show();
            }

        }
    }

    private void initializeItems(){

        //let's close splashscreen
        directionDecided = true;

        userEntry = findViewById(R.id.username_login);
        keyEntry = findViewById(R.id.posting_key_login);
        loginBtn = findViewById(R.id.loginButton);
        skipBtn = findViewById(R.id.skipButton);

        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //validate input values
                if (userEntry.length()==0){
                    userEntry.setError(getString(R.string.field_required));
                }
                if (keyEntry.length()==0){
                    keyEntry.setError(getString(R.string.field_required));
                }
                try {
                    //show progress
                    progress = new ProgressDialog(ctx);
                    progress.setMessage(getString(R.string.validating_credentials));
                    //if (progress.getWindow().isActive()) {
                    progress.show();
                    //}
                }catch(Exception excp){
                    Log.e(MainActivity.TAG, "Dialog error login");
                }

                //reset access token
                accessToken = "";
                queryAPI(userEntry.getText().toString(), keyEntry.getText().toString(), false);
            }

        });

        skipBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                proceedMain();
            }

        });



        //make sure PPKey link click works
        TextView ppHelpLink = findViewById(R.id.posting_key_link);
        ppHelpLink.setMovementMethod(LinkMovementMethod.getInstance());

        TextView createAccountLink = findViewById(R.id.username_create_account_link);
        createAccountLink.setMovementMethod(LinkMovementMethod.getInstance());

        //display content
        LinearLayout loginContainer = findViewById(R.id.loginContainer);
        loginContainer.setVisibility(View.VISIBLE);

    }
}