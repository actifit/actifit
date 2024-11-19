package io.actifit.fitnesstracker.actifitfitnesstracker;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentManager;

import com.deepl.api.DeepLException;
import com.deepl.api.Translator;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Objects;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.picasso.PicassoImagesPlugin;


public class PostAdapter extends ArrayAdapter<SingleHivePostModel> {

    Context ctx;
    Activity activity;
    ArrayList<SingleHivePostModel> postArray;
    ListView socialView;
    Context socialActivContext;
    ListView commentsList;
    //TextView translationNotice;
    Boolean isComment = false;//flag whether this is a post or a comment
    Boolean isTranslated = false;
    //Boolean isExpanded = false;
    static JSONArray extraVotesList;
    static Context keyMainContext;
    // obtain an instance of Markwon
    Markwon markwon;
    Translator translator;


    public int Size(){
        if (postArray !=null && postArray.size()>0) {
            return postArray.size();
        }
        return 0;
    }

    public PostAdapter(Context context, ArrayList<SingleHivePostModel> postArray,
                       ListView socialView, Context socialActivContext, Boolean isComment,
                       Activity activity){
        super(context, 0, postArray);
        this.postArray = postArray;
        this.socialView = socialView;
        this.socialActivContext = socialActivContext;
        this.isComment = isComment;
        this.ctx = context;
        this.activity = activity;

        //translate
        String authKey = ctx.getString(R.string.deepl_api_key);
        //TranslatorOptions options =
        //        new TranslatorOptions().; //.setMaxRetries(8).setTimeout(Duration.ofSeconds(
        //                5));
        this.translator = new Translator(authKey);//, options);

    }

    private boolean userNewlyVotedPost(String voter, String permlink){ //int post_id){
        if (extraVotesList != null && extraVotesList.length() > 0) {
            for (int j=0;j<extraVotesList.length();j++) {
                try {
                    JSONObject entry = extraVotesList.getJSONObject(j);
                    String entVoter = entry.optString("voter");
                    String permlnk = entry.optString("permlink");
                    //int entPostId = entry.optInt("post_id");

                    // Perform your search or comparison here
                    if (voter.equals(entVoter) && permlink.equals(permlnk)){ //post_id == entPostId) {
                        // Found the desired entry
                        return true;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    //@RequiresApi(api = Build.VERSION_CODES.O)
    //@SuppressLint("ClickableViewAccessibility")

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
            //MarkedView mdView = convertView.findViewById(R.id.md_view);
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
            Button replyButton = convertView.findViewById(R.id.reply_button);
            //translationNotice = convertView.findViewById(R.id.translation_notice_lbl);
            TextView translateBtn = convertView.findViewById(R.id.translate);
            RelativeLayout progressBarBody = convertView.findViewById(R.id.progressBarBody);

            //TextView threadType = convertView.findViewById(R.id.threadType);
            ImageView threadTypeImage = convertView.findViewById(R.id.threadTypeImage);

            if (postEntry!=null && postEntry.isThread){
                //threadType.setVisibility(VISIBLE);
                //threadType.setText(postEntry.threadType);
                //threadImage.setIcon(getResources().getDrawable(R.drawable.actifit_logo));
                if (postEntry.threadType.equals(ctx.getString(R.string.peakd_snaps_account))) {
                    threadTypeImage.setImageDrawable(ctx.getResources().getDrawable(R.drawable.peakd));
                }else if (postEntry.threadType.equals(ctx.getString(R.string.ecency_waves_account)))
                    threadTypeImage.setImageDrawable(ctx.getResources().getDrawable(R.drawable.ecency));
            }else{
                //threadType.setVisibility(GONE);
            }

            TextView body = convertView.findViewById(R.id.body);

            author.setText('@'+postEntry.author);

            commentsList = convertView.findViewById(R.id.comments_list);

            boolean isExtraVote = userNewlyVotedPost(MainActivity.username, postEntry.permlink); //postEntry.post_id);

            //check if user has voted for this post
            if (Utils.userVotedPost(MainActivity.username, postEntry.active_votes, postEntry.permlink)//postEntry.post_id)
                || isExtraVote){
                //upvoteButton.setBackgroundColor(getContext().getResources().getColor(R.color.actifitDarkGreen));
                upvoteButton.setTextColor(getContext().getResources().getColor(R.color.actifitDarkGreen));
            }else{
                //upvoteButton.setBackgroundColor(getContext().getResources().getColor(R.color.actifitRed));
                upvoteButton.setTextColor(getContext().getResources().getColor(R.color.colorWhite));
            }

            if (postEntry.commentsExpanded){
                commentsList.setVisibility(VISIBLE);
                if (!this.isComment) {
                    expandButton.setVisibility(GONE);
                    retractButton.setVisibility(VISIBLE);
                }

            }else{
                commentsList.setVisibility(GONE);
                if (!this.isComment) {
                    expandButton.setVisibility(VISIBLE);
                    retractButton.setVisibility(GONE);
                }
            }


            /*if (postEntry.isTranslated){
                translationNotice.setVisibility(VISIBLE);
            }else{
                translationNotice.setVisibility(GONE);
            }*/



            View finalConvertView = convertView;



            replyButton.setOnClickListener(view -> {

                if (MainActivity.username == null || MainActivity.username.isEmpty()) {
                    Toast.makeText(ctx, ctx.getString(R.string.username_missing), Toast.LENGTH_LONG).show();
                    return;
                }

                //show and highlight reply box
                //test fetching hive global properties
                //grabHiveGlobProperties();
                //open modal for voting, passing the currently selected post/comment for vote
                //AlertDialog.Builder voteDialogBuilder = new AlertDialog.Builder(new ContextThemeWrapper(ctx, R.style.Theme_AppCompat_Dialog));//(ctx);

                //need to grab context of parent container as initiating under current context crashes the app
                //Context mainContext = ((ListView)finalConvertView.getParent()).getContext();
                if (!this.isComment) {
                    PostAdapter.keyMainContext = ((ListView)finalConvertView.getParent()).getContext();
                }

                if ( PostAdapter.keyMainContext == null){
                    PostAdapter.keyMainContext = ctx;
                }

                CommentModalDialogFragment dialogFragment =
                        new CommentModalDialogFragment(PostAdapter.keyMainContext, postEntry);
                FragmentManager fmgr = ((AppCompatActivity) PostAdapter.keyMainContext).getSupportFragmentManager();
                dialogFragment.show(fmgr, "comment_modal");

            });

            upvoteButton.setOnClickListener(view ->{
                if (MainActivity.username == null || MainActivity.username.length() <1) {
                    Toast.makeText(ctx, ctx.getString(R.string.username_missing), Toast.LENGTH_LONG).show();
                    return;
                }
                if (!this.isComment) {
                    PostAdapter.keyMainContext = ((ListView)finalConvertView.getParent()).getContext();
                }
                if ( PostAdapter.keyMainContext == null){
                    PostAdapter.keyMainContext = ctx;
                }
                VoteModalDialogFragment dialogFragment =
                        new VoteModalDialogFragment(PostAdapter.keyMainContext, postEntry,
                                extraVotesList, socialView);
                FragmentManager fmgr = ((AppCompatActivity) PostAdapter.keyMainContext).getSupportFragmentManager();
                if (fmgr.isDestroyed()){
                    PostAdapter.keyMainContext = ctx;
                    fmgr = ((AppCompatActivity) PostAdapter.keyMainContext).getSupportFragmentManager();
                }
                dialogFragment.show(fmgr, "vote_modal");
            });

            commentButton.setOnClickListener(view -> {
                //if it is already visible, hide it
                commentsList = finalConvertView.findViewById(R.id.comments_list);
                if (commentsList.getVisibility() == VISIBLE){
                    commentsList.setVisibility(GONE);
                    postEntry.commentsExpanded = false;
                }else {
                    //show loader

                    ProgressBar loader = finalConvertView.findViewById(R.id.loader);
                    Thread thread = new Thread(() -> {
                        //load data
                        activity.runOnUiThread(() -> {
                            loader.setVisibility(VISIBLE);
                        });
                        postEntry.comments = loadComments(postEntry);


                        PostAdapter commentAdapter = new PostAdapter(ctx, postEntry.comments,
                                socialView, socialActivContext, true, activity);
                        activity.runOnUiThread(() -> {
                            commentsList.setAdapter(commentAdapter);
                            commentsList.setVisibility(VISIBLE);
                            postEntry.commentsExpanded = true;

                            loader.setVisibility(GONE);
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

            expandButton.setOnClickListener(view -> {
                if (expandButton.getVisibility() == VISIBLE) {
                    expandPost(expandButton, retractButton, mainImage, postEntry,
                            body, progressBarBody);
                }else{
                    retractPost(expandButton, retractButton, mainImage, postEntry,
                            body, progressBarBody);
                }
            });

            retractButton.setOnClickListener(view -> retractPost(expandButton, retractButton,
                    mainImage, postEntry, body, progressBarBody));

            translateBtn.setOnClickListener((View v) -> {
                handleTranslation(postEntry, body, progressBarBody, activity);//, translationNotice);

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

            //to be used when setting value upon content retract
            //final String finalShortenedContent = displayContent;


            try {
                String activityTypeStr = postEntry.getActivityType();
                activityType.setText(activityTypeStr);
                if (activityTypeStr == "") {
                    activityTypeContainer.setVisibility(GONE);
                } else {
                    activityTypeContainer.setVisibility(VISIBLE);
                }

                String activityCountStr = postEntry.getActivityCount(true);


                if (Objects.equals(activityCountStr, "")) {
                    activityCountContainer.setVisibility(GONE);
                } else {
                    activityCountContainer.setVisibility(VISIBLE);

                    //only display AFIT rewards on content with activity count
                    //afitRewards.setText (Html.fromHtml(postEntry.afitRewards+" AFIT" + (postEntry.afitRewards>0?checkMark:hourglass)));
                    afitRewards.setText(Html.fromHtml(postEntry.afitRewards + " AFIT"));

                    //also adjust formatting of activity count
                    String activityCountStrNfmt = postEntry.getActivityCount(false);
                    int actiCountNo = Integer.parseInt(activityCountStrNfmt);
                    activityCount.setText(activityCountStr);
                    if (actiCountNo >= 10000) {
                        activityCount.setTextColor(getContext().getResources().getColor(R.color.actifitDarkGreen));
                    } else if (actiCountNo >= 5000) {
                        activityCount.setTextColor(getContext().getResources().getColor(R.color.actifitRed));
                    } else {
                        activityCount.setTextColor(getContext().getResources().getColor(android.R.color.tab_indicator_text));
                    }

                }


                payoutVal.setText(Html.fromHtml(grabPostPayout(postEntry) + (isPaid(postEntry) ? checkMark : hourglass)));

                if (isPaid(postEntry)) {
                    payoutVal.setTextColor(colorSuccess);
                } else {
                    payoutVal.setTextColor(colorPending);
                }
            }catch(Exception ee){
                ee.printStackTrace();
            }

            date.setText(Utils.getTimeDifference(postEntry.created));
            int voteCount = postEntry.active_votes.length();
            if (isExtraVote) {
                voteCount += 1;
            }
            upvoteCount.setText(voteCount + "");
            commentCount.setText(postEntry.children+"");

            //profile pic
            final String userImgUrl = ctx.getString(R.string.hive_image_host_url).replace("USERNAME", postEntry.author);
            Handler uiHandler = new Handler(Looper.getMainLooper());
            uiHandler.post(() -> {
                //load user image
                Picasso.get()
                        .load(userImgUrl)
                        .into(userProfilePic);
            });

            //mdView.setMDText(finalShortenedContent);
            // parse markdown and create styled text

            this.markwon = Markwon.builder(ctx)
                    //.usePlugin(ImagesPlugin.create())
                    //support HTML
                    .usePlugin(HtmlPlugin.create())

                    //for handling link clicks
                    .usePlugin(new AbstractMarkwonPlugin() {
                        @Override
                        public void configureConfiguration(MarkwonConfiguration.Builder builder) {
                            builder.linkResolver((view, link) -> {
                                // react to link click here

                                // Create an Intent to open the URL in an external browser
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                // Verify that there's an app available to handle this intent
                                if (intent.resolveActivity(view.getContext().getPackageManager()) != null) {
                                    view.getContext().startActivity(intent);
                                }
                            });
                        }
                    })

                    //handle images via available picasso
                    //.usePlugin(PicassoImagesPlugin.create(Picasso.get()))

                    //handle images via picasso
                    .usePlugin(PicassoImagesPlugin.create(new PicassoImagesPlugin.PicassoStore() {
                        @NonNull
                        @Override
                        public RequestCreator load(@NonNull AsyncDrawable drawable) {

                            int originalWidth = Math.round(drawable.getMinimumWidth());
                            int originalHeight = Math.round(drawable.getMinimumHeight());

                            // Calculate desired width and height based on container size
                            // For example, you might use the container's width for width and a fixed height
                            int desiredWidth = body.getWidth(); // Replace with actual container width
                            int desiredHeight = Math.round(desiredWidth * originalHeight / originalWidth); // Calculate desired height
                            if (desiredHeight <=0 && desiredWidth <=0){
                                return Picasso.get()
                                        .load(drawable.getDestination())
                                        .tag(drawable);
                            }

                            return Picasso.get()
                                    .load(drawable.getDestination())
                                    .resize(desiredWidth, desiredHeight)
                                    .centerCrop()
                                    .tag(drawable);
                                    //.fit();
                                    //.fit()
                                    //.centerCrop();
                                    //.resize(targetWidth, targetHeight) // Specify your target width and height
                                    //.fit()
                                    //.centerCrop()
                                    //.into(drawable);
                                    //.tag(drawable);
                        }

                        @Override
                        public void cancel(@NonNull AsyncDrawable drawable) {
                            Picasso.get()
                                    .cancelTag(drawable);
                        }
                    }))

                    .build();

            //if (this.isComment){
                //expand to fit
                ViewGroup.LayoutParams layoutParams = body.getLayoutParams();
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;

                body.setLayoutParams(layoutParams);
            //}

            renderContent(postEntry, body, progressBarBody);//, translationNotice);

            /*final Spanned markdown = markwon.toMarkdown(finalShortenedContent);
            // use it on a TextView
            markwon.setParsedMarkdown(body, markdown);*/

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
                title.setVisibility(VISIBLE);
                afitRewards.setVisibility(VISIBLE);


                //fetch main post image
                String fetchedImageUrl = "";
                try {
                    JSONObject jsonMetadata = postEntry.json_metadata;
                    if (jsonMetadata.has("image")) {
                        JSONArray imageArray = jsonMetadata.getJSONArray("image");
                        if (imageArray.length() > 0) {

                            //fetch first non-empty image
                            for (int j=0;j<imageArray.length();j++){
                                fetchedImageUrl = imageArray.getString(j);
                                if (fetchedImageUrl.startsWith("http")){
                                    break;
                                }
                            }

                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                final String mainImageUrl = fetchedImageUrl;

                    //Handler uiHandler = new Handler(Looper.getMainLooper());
                    uiHandler.post(() -> {

                        try {
                            //also load main post image
                            if (mainImageUrl != "") {
                                Picasso.get()
                                        .load(mainImageUrl)
                                        .error(R.drawable.ic_launcher_background)
                                        //added those to fix issues with large images, although they delay loading images a bit yet useful
                                        //to avoid any crashes. In particular the fit function call
                                        //https://futurestud.io/tutorials/picasso-image-resizing-scaling-and-fit

                                        .fit()
                                        .centerCrop()
                                        //.resize(mainImage.getLayoutParams().width, mainImage.getLayoutParams().height)
                                        .into(mainImage);
                                mainImage.setVisibility(VISIBLE);
                            }
                        }catch(Exception err){
                            System.out.println(err);
                            mainImage.setVisibility(GONE);
                        }
                    });

                afitLogo.setVisibility(VISIBLE);
            }else{
                //hide post only sections
                mainImage.setVisibility(GONE);
                title.setVisibility(GONE);
                afitRewards.setVisibility(GONE);
                expandButton.setVisibility(GONE);
                afitLogo.setVisibility(GONE);
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


        }catch(Exception exp){
            exp.printStackTrace();
        }
        // Return the completed view to render on screen
        return convertView;
    }




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
                    SingleHivePostModel commentEntry = new SingleHivePostModel((result.getJSONObject(i)), ctx);
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

    void expandPost(Button expandButton, Button retractButton, //MarkedView mdView,
                    ImageView mainImage, SingleHivePostModel postEntry,
                    TextView body, RelativeLayout progressBarBody){
        //expand text visibility
        expandButton.setVisibility(GONE);
        retractButton.setVisibility(VISIBLE);
        postEntry.isExpanded = true;
        //mdView.setMDText(Utils.sanitizeContent(postEntry.body, true));

        //body.setText(mdView.text);

        // parse markdown and create styled text
        /*String textToUse = postEntry.body;
        if (postEntry.isTranslated){
            textToUse = postEntry.translatedText;
        }*/
        renderContent(postEntry, body, progressBarBody);//, translationNotice);

        /*
        ViewGroup.LayoutParams layoutParams = body.getLayoutParams();
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;

        body.setLayoutParams(layoutParams);*/
        //mdView.setMinimumHeight(700);

        mainImage.setVisibility(GONE);
    }

    void retractPost(Button expandButton, Button retractButton, //MarkedView mdView,
                     ImageView mainImage, SingleHivePostModel postEntry,
                     TextView body, RelativeLayout progressBarBody){
        //retract text visibility
        expandButton.setVisibility(VISIBLE);
        retractButton.setVisibility(GONE);
        postEntry.isExpanded = false;

        //maintain current position after close
        int currentPosition = socialView.getFirstVisiblePosition();
        View v = socialView.getChildAt(0);
        int topOffset = (v == null) ? 0 : v.getTop();

        // Restore the scroll position
        socialView.setSelectionFromTop(currentPosition, topOffset);

        //mdView.setMDText(finalShortenedContent);
        renderContent(postEntry, body, progressBarBody);//, translationNotice);

        mainImage.setVisibility(VISIBLE);
    }

    void handleTranslation(SingleHivePostModel postEntry, TextView body,
                           RelativeLayout progressBarBody, Activity activity){//, TextView _translationNotice){


        if (!postEntry.isTranslated){
            progressBarBody.setVisibility(VISIBLE);
            body.setVisibility(GONE);
            //_translationNotice.setVisibility(VISIBLE);
            //if (translatedBody.getVisibility() == View.GONE) {
            Thread thread = new Thread(() -> {
                String result = "";
                if (postEntry.translatedText.isEmpty()) {
                    try {
                        result = translator.translateText(postEntry.body, null, "EN-US").getText();
                        postEntry.translatedText = result;
                        postEntry.isTranslated = true;
                    } catch (DeepLException e) {
                        Log.e(MainActivity.TAG, "deepl exception" + e.getMessage());
                    } catch (InterruptedException e) {
                        Log.e(MainActivity.TAG, "deepl InterruptedException" + e.getMessage());
                    }
                }else{
                    postEntry.isTranslated = true;
                }

                activity.runOnUiThread(() -> {
                    renderContent(postEntry, body, progressBarBody);//, _translationNotice);

                });
            });
            thread.start();
        } else {
            postEntry.isTranslated = false;
            //_translationNotice.setVisibility(GONE);
            /*if (retractButton.getVisibility() == VISIBLE) {
                expandPost(expandButton, retractButton, mainImage, postEntry, body);
            }else{
                retractPost(expandButton, retractButton, mainImage, finalShortenedContent, body);
            }*/
            renderContent(postEntry, body, progressBarBody);//, _translationNotice);
                    /*translatedBody.setVisibility(View.GONE);
                    body.setVisibility(View.VISIBLE);*/
        }
    }

    void renderContent(SingleHivePostModel postEntry, TextView body, RelativeLayout progressBarBody){//, TextView translationNotice){
        String finalText = postEntry.shortBody;
        if (this.isComment || postEntry.isExpanded) {
            finalText = postEntry.body;
        }
        if (postEntry.isTranslated){
            //translationNotice.setVisibility(VISIBLE);
            if (this.isComment || postEntry.isExpanded){
                finalText = postEntry.translatedText;
            }else{
                finalText = postEntry.getTrimmedTranslatedContent();
            }
        }

        Spanned markdown = markwon.toMarkdown(Utils.sanitizeContent(finalText, true));

        // use it on a TextView
        markwon.setParsedMarkdown(body, markdown);
        body.setVisibility(VISIBLE);
        progressBarBody.setVisibility(GONE);
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
        if (postEntry.total_payout_value != null &&
                Double.parseDouble(postEntry.total_payout_value.replaceAll("[^\\d.]", "")) != 0)
            return postEntry.total_payout_value;
        if (postEntry.author_payout_value != null &&
                Double.parseDouble(postEntry.author_payout_value.replaceAll("[^\\d.]", "")) != 0)
            return postEntry.author_payout_value;
        if (postEntry.pending_payout_value != null &&
                Double.parseDouble(postEntry.pending_payout_value.replaceAll("[^\\d.]", "")) != 0)
            return postEntry.pending_payout_value;
        return "0.0";
    }

}
