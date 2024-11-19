package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class SurveyFragment extends DialogFragment {

    Context ctx;
    Survey_Entry_Class survey;
    String accessToken;

    public SurveyFragment(Context ctx, Survey_Entry_Class survey, String accessToken) {
        this.survey = survey;
        this.ctx = ctx;
        this.accessToken = accessToken;
    }


    @Override
    public void onResume() {
        super.onResume();

    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        //dialog.getWindow().requestFeature(STYLE_NO_TITLE);
        return dialog;
    }

    /*@Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

    }*/

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.survey_view, container, false);

        //ImageView featured_image = view.findViewById(R.id.news_featured_image);
        TextView caption_title = view.findViewById(R.id.my_caption_title);
        TextView survey_note = view.findViewById(R.id.survey_notice);
        survey_note.setText(getString(R.string.survey_reward_temp).replace("_REWARD_", this.survey.getSurvey_reward()+" AFIT Reward"));


        /*Handler uiHandler = new Handler(Looper.getMainLooper());
        uiHandler.post(() -> {
            //Picasso.with(ctx)
            Picasso.get()
                    .load(this.mainAnnounce.getFeatured_image_url())
                    .into(featured_image);
        });*/

        //featured_image.setImageResource();
        caption_title.setText(this.survey.getTitle());

        RadioGroup radioGroup = view.findViewById(R.id.survey_options);
        if (this.survey.getSurvey_options() !=null) {

            for (int i = 0; i < this.survey.getSurvey_options().length(); i++) {
                RadioButton radioButton = new RadioButton(getContext());
                try {
                    radioButton.setText(this.survey.getSurvey_options().getString(i));
                    radioGroup.addView(radioButton);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        }

        Activity activity = getActivity();

        Button voteButton = view.findViewById(R.id.voteButton);
        ProgressBar loader = view.findViewById(R.id.loader);
        //final Activity activRef = this;
        voteButton.setOnClickListener(v -> {
            //Toast.makeText(this.ctx,"test"+radioGroup.getCheckedRadioButtonId(), Toast.LENGTH_LONG);

            if (radioGroup.getCheckedRadioButtonId() == -1){
                Utils.displayNotification(ctx.getString(R.string.select_option), null, ctx, getActivity(), false);
                return;
            }

            RequestQueue queue = Volley.newRequestQueue(this.ctx);

            loader.setVisibility(View.VISIBLE);


            String voteSurveyUrl = Utils.apiUrl(ctx)+getString(R.string.vote_survey_url).replace("_USER_", MainActivity.username)
                    .replace("_ID_", this.survey.getId())
                    .replace("_OPTION_",radioGroup.getCheckedRadioButtonId()+"");
            final String success_notification = getString(R.string.trx_success);
            final String error_notification = getString(R.string.trx_error);

            // Process claim rewards request
            JsonObjectRequest req = new JsonObjectRequest
                    (Request.Method.GET, voteSurveyUrl, null, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {

                            // Display the result
                            activity.runOnUiThread(() -> {
                            try {

                                            loader.setVisibility(View.GONE);
                                            if (response.has("status") && (response.getString("status").equals("success"))) {
                                                //Toast.makeText(ctx, getString(R.string.trx_success), Toast.LENGTH_LONG);
                                                Utils.displayNotification(getString(R.string.vote_success), null, ctx, getActivity(), false);
                                                dismiss();
                                            } else {
                                                //Toast.makeText(ctx, getString(R.string.trx_error), Toast.LENGTH_LONG);
                                                Utils.displayNotification(getString(R.string.vote_error), null, ctx, getActivity(), false);
                                            }


                                //Utils.displayNotification(success_notification, null, ctx, this, false);
                                /*if (hiveClaim.has("success")) {
                                    displayNotification(success_notification, null, callerContext, callerActivity, false);

                                } else if (!hiveClaim.getString("error").equals("")) {
                                    displayNotification(hiveClaim.getString("error"), null, callerContext, callerActivity, false);
                                } else {
                                    displayNotification(error_notification, null, callerContext, callerActivity, false);
                                }*/
                            } catch (Exception e) {
                                e.printStackTrace();
                                //displayNotification(error_notification, null, callerContext, callerActivity, false);
                            }
                            });

                        }
                    }, new Response.ErrorListener() {

                        @Override
                        public void onErrorResponse(VolleyError error) {
                            //hide dialog
                            //error.printStackTrace();
                            loader.setVisibility(View.GONE);
                            Log.e(MainActivity.TAG, "error voting");
                            //displayNotification(error_notification, null, callerContext, callerActivity, false);
                        }
                    }) {

                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    final Map<String, String> params = new HashMap<>();
                    params.put("Content-Type", "application/json");
                    params.put(getString(R.string.validation_header), getString(R.string.validation_pre_data) + " " + accessToken);
                    return params;
                }
            };

            queue.add(req);

        });

        // Find and set click listener for the close button
        /*Button detailsButton = view.findViewById(R.id.detailsButton);
        detailsButton.setOnClickListener(v -> {
            //Toast.makeText   (Mcontext, "test", Toast.LENGTH_LONG).show();
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

            builder.setToolbarColor(ctx.getResources().getColor(R.color.actifitRed));

            //animation for showing and closing fitbit authorization screen
            builder.setStartAnimations(ctx, R.anim.slide_in_right, R.anim.slide_out_left);

            //animation for back button clicks
            builder.setExitAnimations(ctx, android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right);

            CustomTabsIntent customTabsIntent = builder.build();

            customTabsIntent.launchUrl(ctx, Uri.parse(this.survey.getUrl()));
        });*/

        // Find and set click listener for the close button
        Button closeButton = view.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> {
            dismiss(); // Dismiss the DialogFragment when the close button is clicked
        });

        return view;
    }

}
