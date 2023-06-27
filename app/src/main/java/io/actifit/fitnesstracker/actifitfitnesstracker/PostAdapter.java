package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Image;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.mittsu.markedview.MarkedView;
import com.scottyab.rootbeer.Const;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static androidx.core.content.ContentProviderCompat.requireContext;
import static com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread;
import androidx.fragment.app.DialogFragment;



public class PostAdapter extends ArrayAdapter<SingleHivePostModel> {

    //JSONArray consumedProducts;
    ProgressDialog progress;
    Context ctx;
    ArrayList<SingleHivePostModel> postArray;
    ListView socialView;
    ListView votersView;
    Context socialActivContext;
    ListView commentsList;
    Boolean isComment;//flag whether this is a post or a comment

    public int Size(){
        if (postArray !=null && postArray.size()>0) {
            return postArray.size();
        }
        return 0;
    }

    public PostAdapter(Context context, ArrayList<SingleHivePostModel> postArray, ListView socialView, Context socialActivContext, Boolean isComment){
        super(context, 0, postArray);
        this.postArray = postArray;
        this.socialView = socialView;
        this.socialActivContext = socialActivContext;
        this.isComment = isComment;
        this.ctx = context;
    }

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        try {

            //ctx = getContext();

            // Get the data item for this position
            final SingleHivePostModel postEntry = getItem(position);
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(ctx).inflate(R.layout.post_entry, parent, false);
            }

            // Lookup view for data population

            CardView entryContainer = convertView.findViewById(R.id.entryContainer);
            TextView title = convertView.findViewById(R.id.title);
            //TextView body = convertView.findViewById(R.id.body);
            //TextView permlink = convertView.findViewById(R.id.permlink);
            TextView author = convertView.findViewById(R.id.author);
            final ImageView userProfilePic = convertView.findViewById(R.id.author_pic);
            TextView date = convertView.findViewById(R.id.date);
            ImageView mainImage = convertView.findViewById(R.id.post_image);
            MarkedView mdView = convertView.findViewById(R.id.md_view);
            TextView upvoteCount = convertView.findViewById(R.id.upvote_count);
            TextView commentCount = convertView.findViewById(R.id.comment_count);
            Button expandButton = convertView.findViewById(R.id.expand_button);
            Button retractButton = convertView.findViewById(R.id.retract_button);
            TextView afitRewards= convertView.findViewById(R.id.afit_rewards);
            ImageView afitLogo = convertView.findViewById(R.id.afit_logo);
            TextView payoutVal = convertView.findViewById(R.id.payout_val);
            TextView activityType = convertView.findViewById(R.id.activity_type_list);
            TextView activityCount = convertView.findViewById(R.id.activity_count);
            LinearLayout activityTypeContainer = convertView.findViewById(R.id.activity_type_container);
            LinearLayout activityCountContainer = convertView.findViewById(R.id.activity_count_container);
            Button shareSocialButton = convertView.findViewById(R.id.share_social);
            Button commentButton = convertView.findViewById(R.id.comment_button);
            Button upvoteButton = convertView.findViewById(R.id.upvote_button);
            commentsList = convertView.findViewById(R.id.comments_list);

            if (postEntry.commentsExpanded){
                commentsList.setVisibility(View.VISIBLE);
            }else{
                commentsList.setVisibility(View.GONE);
            }


            View finalConvertView = convertView;

            upvoteButton.setOnClickListener(view ->{
                //open modal for voting, passing the currently selected post/comment for vote
                    //AlertDialog.Builder voteDialogBuilder = new AlertDialog.Builder(new ContextThemeWrapper(ctx, R.style.Theme_AppCompat_Dialog));//(ctx);

                //need to grab context of parent container as initiating under current context crashes the app
                Context mainContext = ((ListView)finalConvertView.getParent()).getContext();

                AlertDialog.Builder voteDialogBuilder = new AlertDialog.Builder(mainContext);
                final View voteModalLayout = LayoutInflater.from(ctx).inflate(R.layout.vote_modal, null);
                TextView author_txt = voteModalLayout.findViewById(R.id.vote_author);
                author_txt.setText(author.getText()+"'s content");

                Button voters_list_btn = voteModalLayout.findViewById(R.id.voters_list_btn);
                ArrayList<VoteEntryAdapter.VoteEntry> voters = new ArrayList<>();

                voters_list_btn.setOnClickListener(subview -> {

                    //grab array from result
                    JSONArray actVotes = postEntry.active_votes;

                    final View votersListLayout = LayoutInflater.from(ctx).inflate(R.layout.voters_page, null);
                    final ListView votersListItem = votersListLayout.findViewById(R.id.votersList);
                    postEntry.voteRshares = 0;

                    //calculate payout total value
                    postEntry.calculateVoteRshares();
                    postEntry.calculateSumPayout();
                    postEntry.calculateRatio();

                    for (int i = 0; i < actVotes.length(); i++) {
                        try {
                            VoteEntryAdapter.VoteEntry vEntry = new VoteEntryAdapter.VoteEntry((actVotes.getJSONObject(i)), postEntry.ratio);
                            voters.add(vEntry);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }



                    VoteEntryAdapter voteAdapter = new VoteEntryAdapter(getContext(), voters);

                    //votersView = subview.findViewById(R.id.votersList);

                    votersListItem.setAdapter(voteAdapter);

                    final View votersListView = LayoutInflater.from(ctx).inflate(R.layout.voters_page, null);
                    AlertDialog.Builder votersListDialogBldr = new AlertDialog.Builder(mainContext);
                    AlertDialog pointer = votersListDialogBldr.setView(votersListLayout)
                            .setTitle(ctx.getString(R.string.voters_list_title))
                            .setIcon(ctx.getResources().getDrawable(R.drawable.actifit_logo))
                            .setPositiveButton(ctx.getString(R.string.close_button), null).create();

                    pointer.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
                    pointer.getWindow().getDecorView().setBackground(ctx.getDrawable(R.drawable.dialog_shape));
                    pointer.show();
                });

                //runOnUiThread(() -> {
                AlertDialog pointer = voteDialogBuilder.setView(voteModalLayout)
                        .setTitle(ctx.getString(R.string.voting_note))
                        .setIcon(ctx.getResources().getDrawable(R.drawable.actifit_logo))
                        .setPositiveButton(ctx.getString(R.string.close_button), null).create();

                pointer.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
                pointer.getWindow().getDecorView().setBackground(ctx.getDrawable(R.drawable.dialog_shape));
                pointer.show();

                //});
            });

            commentButton.setOnClickListener(view -> {
                //if it is already visible, hide it
                commentsList = finalConvertView.findViewById(R.id.comments_list);
                if (commentsList.getVisibility() == View.VISIBLE){
                    commentsList.setVisibility(View.GONE);
                    postEntry.commentsExpanded = false;
                }else {
                    Thread thread = new Thread(() -> {
                        //load data
                        postEntry.comments = loadComments(postEntry);
                        runOnUiThread(() -> {

                            PostAdapter commentAdapter = new PostAdapter(ctx, postEntry.comments, socialView, socialActivContext, true);
                            commentsList.setAdapter(commentAdapter);
                            commentsList.setVisibility(View.VISIBLE);
                            postEntry.commentsExpanded = true;
                        });
                    });
                    thread.start();
                }
            });

            shareSocialButton.setOnClickListener(view -> shareSocial(postEntry));

            TextView payoutIcon = convertView.findViewById(R.id.payout_icon);
            payoutIcon.setOnClickListener(view -> {
                Toast.makeText(ctx, ctx.getString(R.string.payout_details),Toast.LENGTH_SHORT).show();
            });

            payoutVal.setOnClickListener(view -> {
                Toast.makeText(ctx, ctx.getString(R.string.hive_payout_details),Toast.LENGTH_SHORT).show();
            });

            afitRewards.setOnClickListener(view -> {
                Toast.makeText(ctx, ctx.getString(R.string.afit_payout_details),Toast.LENGTH_SHORT).show();
            });

            // Populate the data into the template view using the data object

            //checkmark
            String checkMark = "&#10003;";//"&#9989;";//"✅";
            String xMark = "&#10006;";//"&#10060;";//"❌";
            String hourglass = "&#8987;";
            int colorSuccess = ctx.getResources().getColor(R.color.actifitDarkGreen);
            int colorPending = ctx.getResources().getColor(R.color.actifitRed);


            //map post content
            title.setText(postEntry.title);
            //permlink.setText(postEntry.permlink);

            //body.setText(postEntry.body);
            //convert markdown
            String shortenedContent = Utils.parseMarkdown(postEntry.body);
            //removed extra tags
            shortenedContent = Utils.sanitizeContent(shortenedContent, false);

            shortenedContent = Utils.trimText(shortenedContent, Constants.trimmedTextSize);

            //to be used when setting value upon content retract
            final String finalShortenedContent = shortenedContent;

            mdView.setMDText(finalShortenedContent);

            String activityTypeStr = postEntry.getActivityType();
            activityType.setText(activityTypeStr);
            if (activityTypeStr==""){
                activityTypeContainer.setVisibility(View.GONE);
            }else{
                activityTypeContainer.setVisibility(View.VISIBLE);
            }

            String activityCountStr = postEntry.getActivityCount(true);


            if (activityCountStr==""){
                activityCountContainer.setVisibility(View.GONE);
            }else{
                activityCountContainer.setVisibility(View.VISIBLE);

                //only display AFIT rewards on content with activity count
                //afitRewards.setText (Html.fromHtml(postEntry.afitRewards+" AFIT" + (postEntry.afitRewards>0?checkMark:hourglass)));
                afitRewards.setText (Html.fromHtml(postEntry.afitRewards+" AFIT"));

                //also adjust formatting of activity count
                String activityCountStrNfmt = postEntry.getActivityCount(false);
                Integer actiCountNo = Integer.parseInt(activityCountStrNfmt);
                activityCount.setText(activityCountStr);
                if (actiCountNo >= 10000 ){
                    activityCount.setTextColor(getContext().getResources().getColor(R.color.actifitDarkGreen));
                }else if (actiCountNo >= 5000 ){
                    activityCount.setTextColor(getContext().getResources().getColor(R.color.actifitRed));
                }else {
                    activityCount.setTextColor(getContext().getResources().getColor(android.R.color.tab_indicator_text));
                }

            }




            payoutVal.setText(Html.fromHtml(grabPostPayout(postEntry) + (isPaid(postEntry)?checkMark:hourglass)));

            if (isPaid(postEntry)){
                payoutVal.setTextColor(colorSuccess);
            }else{
                payoutVal.setTextColor(colorPending);
            }


            author.setText('@'+postEntry.author);
            date.setText(Utils.getTimeDifference(postEntry.created));

            upvoteCount.setText(postEntry.active_votes.length()+"");
            commentCount.setText(postEntry.children+"");

            //show comment content
            /*PostAdapter commentAdapter = new PostAdapter(ctx, postEntry.comments, socialView, this.socialActivContext, false);

            // Execute UI-related code on the main thread
            commentsList = convertView.findViewById(R.id.comments_list);
            commentsList.setAdapter(commentAdapter);
            commentsList.setMinimumHeight(200);


            commentsList.setVisibility(View.VISIBLE);*/
            commentsList = convertView.findViewById(R.id.comments_list);

            //ensure proper scrollability under each post/comment/subcomment list
            commentsList.setOnTouchListener((v, event) -> {
                ListView parentListView = Utils.findParentListView(v);
                if (parentListView != null) {
                    int action = event.getActionMasked();

                    switch (action) {
                        case MotionEvent.ACTION_DOWN:
                            // Disable parent ListView scrolling on touch down
                            parentListView.requestDisallowInterceptTouchEvent(true);
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            // Enable parent ListView scrolling on touch up or cancel
                            parentListView.requestDisallowInterceptTouchEvent(false);
                            break;
                    }
                }
                // Handle touch events for the nested ListView
                return false;
            });

            //only show this under no comments to save phone space
            if (!this.isComment) {
                //show title
                title.setVisibility(View.VISIBLE);
                afitRewards.setVisibility(View.VISIBLE);
                //profile pic
                final String userImgUrl = ctx.getString(R.string.hive_image_host_url).replace("USERNAME", postEntry.author);


                //fetch main post image
                String fetchedImageUrl = "";
                try {
                    JSONObject jsonMetadata = postEntry.json_metadata;
                    if (jsonMetadata.has("image")) {
                        JSONArray imageArray = jsonMetadata.getJSONArray("image");
                        if (imageArray.length() > 0) {
                            fetchedImageUrl = imageArray.getString(0);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                final String mainImageUrl = fetchedImageUrl;

                    Handler uiHandler = new Handler(Looper.getMainLooper());
                    uiHandler.post(() -> {
                        //load user image
                        Picasso.get()
                                .load(userImgUrl)
                                .into(userProfilePic);
                        if (mainImageUrl!="") {
                            Picasso.get()
                                    .load(mainImageUrl)
                                    .into(mainImage);
                            mainImage.setVisibility(View.VISIBLE);
                        }
                    });

                afitLogo.setVisibility(View.VISIBLE);
            }else{
                //hide post only sections
                mainImage.setVisibility(View.GONE);
                title.setVisibility(View.GONE);
                afitRewards.setVisibility(View.GONE);
                expandButton.setVisibility(View.GONE);
                afitLogo.setVisibility(View.GONE);
            }

            ScaleAnimation scaler;
            scaler = new ScaleAnimation(1f, 0.8f, 1f,0.8f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            scaler.setDuration(500);
            scaler.setRepeatMode(Animation.REVERSE);
            scaler.setRepeatCount(Animation.INFINITE);


            /*mainImage.setOnClickListener(view -> {
                if (expandButton.getVisibility() == View.VISIBLE) {
                    expandPost(expandButton, retractButton, mdView, mainImage, postEntry);
                }else{
                    retractPost(expandButton, retractButton, mdView, mainImage, finalShortenedContent);
                }
            });

            mdView.setOnClickListener(view -> {
                if (expandButton.getVisibility() == View.VISIBLE) {
                    expandPost(expandButton, retractButton, mdView, mainImage, postEntry);
                }else{
                    retractPost(expandButton, retractButton, mdView, mainImage, finalShortenedContent);
                }
            });*/

            expandButton.setOnClickListener(view -> {
                if (expandButton.getVisibility() == View.VISIBLE) {
                    expandPost(expandButton, retractButton, mdView, mainImage, postEntry);
                }else{
                    retractPost(expandButton, retractButton, mdView, mainImage, finalShortenedContent);
                }
            });



            retractButton.setOnClickListener(view -> retractPost(expandButton, retractButton, mdView, mainImage, finalShortenedContent));

            //activate gadget functionality
            /*
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
                            String loginAuthUrl = getContext().getString(R.string.live_server)
                                    + getContext().getString(R.string.login_auth);


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

                        String bcastUrl = getContext().getString(R.string.perform_trx_link) +
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



                                                String buyUrl = getContext().getString(R.string.activate_gadget_link)+
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
                            @NonNull
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

            */



        }catch(Exception exp){
            exp.printStackTrace();
        }

        // Return the completed view to render on screen
        return convertView;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    ArrayList<SingleHivePostModel> loadComments(SingleHivePostModel postEntry){
        HiveRequests hiveReq = new HiveRequests(ctx);
        ArrayList<SingleHivePostModel> commentList = new ArrayList<>();
        //Thread thread = new Thread(() -> {
            try {
                JSONObject params = new JSONObject();
                params.put("author", postEntry.author);
                params.put("permlink", postEntry.permlink);
                JSONArray result = hiveReq.getComments(params);
                for (int i = 0; i < result.length(); i++) {
                    //comments are basically like posts under hive
                    SingleHivePostModel commentEntry = new SingleHivePostModel((result.getJSONObject(i)));
                    commentList.add(commentEntry);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return commentList;
        //});
        //thread.start();

    }

    void shareSocial(SingleHivePostModel postEntry){
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String shareSubject = ctx.getString(R.string.post_title_other);
        String shareBody = ctx.getString(R.string.post_description);
        shareBody += ctx.getString(R.string.post_title_other) + " "+ctx.getString(R.string.actifit_url)+postEntry.author+"/"+postEntry.permlink;

        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);
        sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody);

        socialActivContext.startActivity(Intent.createChooser(sharingIntent, ctx.getString(R.string.share_via)));

    }

    void expandPost(Button expandButton, Button retractButton, MarkedView mdView, ImageView mainImage, SingleHivePostModel postEntry){
        //expand text visibility
        expandButton.setVisibility(View.GONE);
        retractButton.setVisibility(View.VISIBLE);
        mdView.setMDText(Utils.sanitizeContent(postEntry.body, true));

        ViewGroup.LayoutParams layoutParams = mdView.getLayoutParams();
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;

        mdView.setLayoutParams(layoutParams);

        mainImage.setVisibility(View.GONE);
    }

    void retractPost(Button expandButton, Button retractButton, MarkedView mdView, ImageView mainImage, String finalShortenedContent){
        //retract text visibility
        expandButton.setVisibility(View.VISIBLE);
        retractButton.setVisibility(View.GONE);

        //maintain current position after close
        int currentPosition = socialView.getFirstVisiblePosition();
        View v = socialView.getChildAt(0);
        int topOffset = (v == null) ? 0 : v.getTop();

        // Restore the scroll position
        socialView.setSelectionFromTop(currentPosition, topOffset);

        mdView.setMDText(finalShortenedContent);
        mainImage.setVisibility(View.VISIBLE);
    }

    private Boolean isPaid(SingleHivePostModel postEntry){
        if (postEntry!=null){
            if (postEntry.is_paidout) {
                return true;
            }
        }
        return false;
    }

    private String grabPostPayout(SingleHivePostModel postEntry) {
        if (postEntry.total_payout_value != null && Double.parseDouble(postEntry.total_payout_value.replaceAll("[^\\d.]", "")) != 0) return postEntry.total_payout_value;
        if (postEntry.author_payout_value != null && Double.parseDouble(postEntry.author_payout_value.replaceAll("[^\\d.]", "")) != 0) return postEntry.author_payout_value;
        if (postEntry.pending_payout_value != null && Double.parseDouble(postEntry.pending_payout_value.replaceAll("[^\\d.]", "")) != 0) return postEntry.pending_payout_value;
        return "0.0";
    }

}
