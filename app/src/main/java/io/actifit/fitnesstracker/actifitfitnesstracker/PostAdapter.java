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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.deepl.api.DeepLException;
import com.deepl.api.Translator;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    Boolean isComment = false;
    static JSONArray extraVotesList;
    static Context keyMainContext;
    Markwon markwon;

    /* --- DEEPL CODE REMOVED ---
    Translator translator;
    */

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

        /* --- DEEPL INITIALIZATION REMOVED ---
        //translate
        String authKey = ctx.getString(R.string.deepl_api_key);
        this.translator = new Translator(authKey);
        */
    }

    private boolean userNewlyVotedPost(String voter, String permlink){
        if (extraVotesList != null && extraVotesList.length() > 0) {
            for (int j=0;j<extraVotesList.length();j++) {
                try {
                    JSONObject entry = extraVotesList.getJSONObject(j);
                    String entVoter = entry.optString("voter");
                    String permlnk = entry.optString("permlink");
                    if (voter.equals(entVoter) && permlink.equals(permlnk)){
                        return true;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        try {
            final SingleHivePostModel postEntry = getItem(position);
            if (convertView == null) {
                convertView = LayoutInflater.from(ctx).inflate(R.layout.post_entry, parent, false);
            }

            CardView entryContainer = convertView.findViewById(R.id.entryContainer);
            TextView title = convertView.findViewById(R.id.title);
            TextView author = convertView.findViewById(R.id.author);
            final ImageView userProfilePic = convertView.findViewById(R.id.author_pic);
            TextView date = convertView.findViewById(R.id.date);
            ImageView mainImage = convertView.findViewById(R.id.post_image);
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
            TextView translateBtn = convertView.findViewById(R.id.translate);
            RelativeLayout progressBarBody = convertView.findViewById(R.id.progressBarBody);
            ImageView threadTypeImage = convertView.findViewById(R.id.threadTypeImage);
            TextView body = convertView.findViewById(R.id.body);
            commentsList = convertView.findViewById(R.id.comments_list);

            if (postEntry!=null && postEntry.isThread) {
                if (postEntry.threadType.equals(ctx.getString(R.string.peakd_snaps_account))) {
                    threadTypeImage.setImageDrawable(ctx.getResources().getDrawable(R.drawable.peakd));
                } else if (postEntry.threadType.equals(ctx.getString(R.string.ecency_waves_account))) {
                    threadTypeImage.setImageDrawable(ctx.getResources().getDrawable(R.drawable.ecency));
                } else if (postEntry.threadType.equals(ctx.getString(R.string.leo_threads_account))) {
                    threadTypeImage.setImageDrawable(ctx.getResources().getDrawable(R.drawable.inleo));
                }
            }

            author.setText('@'+postEntry.author);
            boolean isExtraVote = userNewlyVotedPost(MainActivity.username, postEntry.permlink);

            if (Utils.userVotedPost(MainActivity.username, postEntry.active_votes, postEntry.permlink) || isExtraVote){
                upvoteButton.setTextColor(getContext().getResources().getColor(R.color.actifitDarkGreen));
            }else{
                upvoteButton.setTextColor(getContext().getResources().getColor(R.color.colorWhite));
            }

            if (postEntry.commentsExpanded){
                commentsList.setVisibility(View.VISIBLE);
                if (!this.isComment) {
                    expandButton.setVisibility(View.GONE);
                    retractButton.setVisibility(View.VISIBLE);
                }
            }else{
                commentsList.setVisibility(View.GONE);
                if (!this.isComment) {
                    expandButton.setVisibility(View.VISIBLE);
                    retractButton.setVisibility(View.GONE);
                }
            }

            View finalConvertView = convertView;

            replyButton.setOnClickListener(view -> {
                if (MainActivity.username == null || MainActivity.username.isEmpty()) {
                    Toast.makeText(ctx, ctx.getString(R.string.username_missing), Toast.LENGTH_LONG).show();
                    return;
                }
                if (!this.isComment) {
                    PostAdapter.keyMainContext = ((ListView)finalConvertView.getParent()).getContext();
                }
                if ( PostAdapter.keyMainContext == null){
                    PostAdapter.keyMainContext = ctx;
                }
                CommentModalDialogFragment dialogFragment = new CommentModalDialogFragment(PostAdapter.keyMainContext, postEntry);
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
                VoteModalDialogFragment dialogFragment = new VoteModalDialogFragment(PostAdapter.keyMainContext, postEntry, extraVotesList, socialView);
                FragmentManager fmgr = ((AppCompatActivity) PostAdapter.keyMainContext).getSupportFragmentManager();
                if (fmgr.isDestroyed()){
                    PostAdapter.keyMainContext = ctx;
                    fmgr = ((AppCompatActivity) PostAdapter.keyMainContext).getSupportFragmentManager();
                }
                dialogFragment.show(fmgr, "vote_modal");
            });

            commentButton.setOnClickListener(view -> {
                commentsList = finalConvertView.findViewById(R.id.comments_list);
                if (commentsList.getVisibility() == View.VISIBLE){
                    commentsList.setVisibility(View.GONE);
                    postEntry.commentsExpanded = false;
                }else {
                    ProgressBar loader = finalConvertView.findViewById(R.id.loader);
                    Thread thread = new Thread(() -> {
                        activity.runOnUiThread(() -> loader.setVisibility(View.VISIBLE));
                        postEntry.comments = loadComments(postEntry);
                        PostAdapter commentAdapter = new PostAdapter(ctx, postEntry.comments, socialView, socialActivContext, true, activity);
                        activity.runOnUiThread(() -> {
                            commentsList.setAdapter(commentAdapter);
                            commentsList.setVisibility(View.VISIBLE);
                            postEntry.commentsExpanded = true;
                            loader.setVisibility(View.GONE);
                        });
                    });
                    thread.start();
                }
            });

            shareSocialButton.setOnClickListener(view -> shareSocial(postEntry));
            TextView payoutIcon = convertView.findViewById(R.id.payout_icon);
            payoutIcon.setOnClickListener(view -> Toast.makeText(ctx, ctx.getString(R.string.payout_details),Toast.LENGTH_SHORT).show());
            payoutVal.setOnClickListener(view -> Toast.makeText(ctx, ctx.getString(R.string.hive_payout_details),Toast.LENGTH_SHORT).show());
            afitRewards.setOnClickListener(view -> Toast.makeText(ctx, ctx.getString(R.string.afit_payout_details),Toast.LENGTH_SHORT).show());
            expandButton.setOnClickListener(view -> expandPost(expandButton, retractButton, mainImage, postEntry, body, progressBarBody));
            retractButton.setOnClickListener(view -> retractPost(expandButton, retractButton, mainImage, postEntry, body, progressBarBody));

            translateBtn.setOnClickListener((View v) -> handleTranslation(postEntry, body, progressBarBody, activity));

            String checkMark = "✓";
            String hourglass = "⌛";
            int colorSuccess = ctx.getResources().getColor(R.color.actifitDarkGreen);
            int colorPending = ctx.getResources().getColor(R.color.actifitRed);
            title.setText(postEntry.title);

            try {
                String activityTypeStr = postEntry.getActivityType();
                activityType.setText(activityTypeStr);
                if (activityTypeStr.isEmpty()) {
                    activityTypeContainer.setVisibility(View.GONE);
                } else {
                    activityTypeContainer.setVisibility(View.VISIBLE);
                }

                String activityCountStr = postEntry.getActivityCount(true);
                if (Objects.equals(activityCountStr, "")) {
                    activityCountContainer.setVisibility(View.GONE);
                } else {
                    activityCountContainer.setVisibility(View.VISIBLE);
                    afitRewards.setText(Html.fromHtml(postEntry.afitRewards + " AFIT"));
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
            if (isExtraVote) voteCount += 1;
            upvoteCount.setText(String.valueOf(voteCount));
            commentCount.setText(String.valueOf(postEntry.children));

            final String userImgUrl = ctx.getString(R.string.hive_image_host_url).replace("USERNAME", postEntry.author);
            Handler uiHandler = new Handler(Looper.getMainLooper());
            uiHandler.post(() -> Picasso.get().load(userImgUrl).into(userProfilePic));

            this.markwon = Markwon.builder(ctx)
                    .usePlugin(HtmlPlugin.create())
                    .usePlugin(new AbstractMarkwonPlugin() {
                        @Override
                        public void configureConfiguration(MarkwonConfiguration.Builder builder) {
                            builder.linkResolver((view, link) -> {
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                if (intent.resolveActivity(view.getContext().getPackageManager()) != null) {
                                    view.getContext().startActivity(intent);
                                }
                            });
                        }
                    })
                    .usePlugin(PicassoImagesPlugin.create(new PicassoImagesPlugin.PicassoStore() {
                        @NonNull @Override
                        public RequestCreator load(@NonNull AsyncDrawable drawable) {
                            int desiredWidth = body.getWidth();
                            if (desiredWidth <= 0) {
                                return Picasso.get().load(drawable.getDestination()).tag(drawable);
                            }
                            return Picasso.get().load(drawable.getDestination()).resize(desiredWidth, 0).tag(drawable);
                        }
                        @Override
                        public void cancel(@NonNull AsyncDrawable drawable) {
                            Picasso.get().cancelTag(drawable);
                        }
                    }))
                    .build();

            renderContent(postEntry, body, progressBarBody);

            commentsList = convertView.findViewById(R.id.comments_list);
            commentsList.setOnTouchListener((v, event) -> {
                ListView parentListView = Utils.findParentListView(v);
                if (parentListView != null) {
                    parentListView.requestDisallowInterceptTouchEvent(event.getAction() != MotionEvent.ACTION_UP && event.getAction() != MotionEvent.ACTION_CANCEL);
                }
                return false;
            });

            if (!this.isComment) {
                title.setVisibility(View.VISIBLE);
                afitRewards.setVisibility(View.VISIBLE);
                String fetchedImageUrl = "";
                try {
                    JSONObject jsonMetadata = postEntry.json_metadata;
                    if (jsonMetadata.has("image")) {
                        JSONArray imageArray = jsonMetadata.getJSONArray("image");
                        if (imageArray.length() > 0) {
                            for (int j = 0; j < imageArray.length(); j++) {
                                fetchedImageUrl = imageArray.getString(j);
                                if (fetchedImageUrl.startsWith("http")) break;
                            }
                        }
                    }
                } catch (JSONException e) { e.printStackTrace(); }

                final String mainImageUrl = fetchedImageUrl;
                uiHandler.post(() -> {
                    try {
                        if (!mainImageUrl.isEmpty()) {
                            Picasso.get().load(mainImageUrl).error(R.drawable.ic_launcher_background).fit().centerCrop().into(mainImage);
                            mainImage.setVisibility(View.VISIBLE);
                        } else {
                            mainImage.setVisibility(View.GONE);
                        }
                    } catch (Exception err) {
                        System.out.println(err);
                        mainImage.setVisibility(View.GONE);
                    }
                });
                afitLogo.setVisibility(View.VISIBLE);
            } else {
                mainImage.setVisibility(View.GONE);
                title.setVisibility(View.GONE);
                afitRewards.setVisibility(View.GONE);
                expandButton.setVisibility(View.GONE);
                afitLogo.setVisibility(View.GONE);
            }

        } catch(Exception exp) {
            exp.printStackTrace();
        }
        return convertView;
    }

    ArrayList<SingleHivePostModel> loadComments(SingleHivePostModel postEntry){
        HiveRequests hiveReq = new HiveRequests(ctx);
        ArrayList<SingleHivePostModel> commentList = new ArrayList<>();
        try {
            JSONObject params = new JSONObject();
            params.put("author", postEntry.author);
            params.put("permlink", postEntry.permlink);
            JSONArray result = hiveReq.getComments(params);
            for (int i = 0; i < result.length(); i++) {
                SingleHivePostModel commentEntry = new SingleHivePostModel((result.getJSONObject(i)), ctx);
                commentList.add(commentEntry);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return commentList;
    }

    void shareSocial(SingleHivePostModel postEntry){
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String shareSubject = ctx.getString(R.string.post_title_other);
        String shareBody = ctx.getString(R.string.post_description) + ctx.getString(R.string.post_title_other) + " " + ctx.getString(R.string.actifit_url) + postEntry.author + "/" + postEntry.permlink;
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);
        sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
        socialActivContext.startActivity(Intent.createChooser(sharingIntent, ctx.getString(R.string.share_via)));
    }

    void expandPost(Button expandButton, Button retractButton, ImageView mainImage, SingleHivePostModel postEntry, TextView body, RelativeLayout progressBarBody){
        expandButton.setVisibility(View.GONE);
        retractButton.setVisibility(View.VISIBLE);
        postEntry.isExpanded = true;
        renderContent(postEntry, body, progressBarBody);
        mainImage.setVisibility(View.GONE);
    }

    void retractPost(Button expandButton, Button retractButton, ImageView mainImage, SingleHivePostModel postEntry, TextView body, RelativeLayout progressBarBody){
        expandButton.setVisibility(View.VISIBLE);
        retractButton.setVisibility(View.GONE);
        postEntry.isExpanded = false;
        int currentPosition = socialView.getFirstVisiblePosition();
        View v = socialView.getChildAt(0);
        int topOffset = (v == null) ? 0 : v.getTop();
        socialView.setSelectionFromTop(currentPosition, topOffset);
        renderContent(postEntry, body, progressBarBody);
        mainImage.setVisibility(View.VISIBLE);
    }

    void handleTranslation(SingleHivePostModel postEntry, TextView body, RelativeLayout progressBarBody, Activity activity){
        if (!postEntry.isTranslated){
            progressBarBody.setVisibility(View.VISIBLE);
            body.setVisibility(View.GONE);
            try {
                String geminiApiKey = ctx.getString(R.string.gemini_api_key);
                GenerativeModel generativeModel = new GenerativeModel("gemini-1.5-flash-latest", geminiApiKey);
                GenerativeModelFutures modelFutures = GenerativeModelFutures.from(generativeModel);
                String prompt = "Translate the following text into English. Provide only the translated text without any additional comments or introductions. The text is: \"" + postEntry.body + "\"";
                Content content = new Content.Builder().addText(prompt).build();

                Futures.addCallback(modelFutures.generateContent(content),
                        new FutureCallback<GenerateContentResponse>() {
                            @Override
                            public void onSuccess(GenerateContentResponse result) {
                                String translatedText = result.getText();
                                activity.runOnUiThread(() -> {
                                    postEntry.translatedText = translatedText != null ? translatedText.trim() : "";
                                    postEntry.isTranslated = true;
                                    renderContent(postEntry, body, progressBarBody);
                                });
                            }
                            @Override
                            public void onFailure(@NonNull Throwable t) {
                                activity.runOnUiThread(() -> {
                                    Log.e("GeminiTranslation", "Translation failed", t);
                                    Toast.makeText(ctx, "Translation Failed", Toast.LENGTH_SHORT).show();
                                    postEntry.isTranslated = false;
                                    renderContent(postEntry, body, progressBarBody);
                                });
                            }
                        },
                        ContextCompat.getMainExecutor(ctx));

            } catch (Exception e) {
                Log.e("GeminiTranslation", "Error initializing Gemini or making call", e);
                Toast.makeText(ctx, "Translation service error", Toast.LENGTH_SHORT).show();
                activity.runOnUiThread(() -> {
                    postEntry.isTranslated = false;
                    renderContent(postEntry, body, progressBarBody);
                });
            }
        } else {
            postEntry.isTranslated = false;
            renderContent(postEntry, body, progressBarBody);
        }
    }

    void renderContent(SingleHivePostModel postEntry, TextView body, RelativeLayout progressBarBody){
        String finalText = postEntry.shortBody;
        if (this.isComment || postEntry.isExpanded) {
            finalText = postEntry.body;
        }
        if (postEntry.isTranslated){
            if (this.isComment || postEntry.isExpanded){
                finalText = postEntry.translatedText;
            } else {
                finalText = postEntry.getTrimmedTranslatedContent();
            }
        }
        String sanitizedText = Utils.sanitizeContent(finalText, true);
        String processedText = convertImageLinksToMarkdown(sanitizedText);
        Spanned markdown = markwon.toMarkdown(processedText);
        markwon.setParsedMarkdown(body, markdown);
        body.setVisibility(View.VISIBLE);
        progressBarBody.setVisibility(View.GONE);
    }

    private Boolean isPaid(SingleHivePostModel postEntry){
        if (postEntry!=null){
            return postEntry.is_paidout;
        }
        return false;
    }

    private String grabPostPayout(SingleHivePostModel postEntry) {
        if (postEntry.total_payout_value != null && Double.parseDouble(postEntry.total_payout_value.replaceAll("[^\\d.]", "")) != 0)
            return postEntry.total_payout_value;
        if (postEntry.author_payout_value != null && Double.parseDouble(postEntry.author_payout_value.replaceAll("[^\\d.]", "")) != 0)
            return postEntry.author_payout_value;
        if (postEntry.pending_payout_value != null && Double.parseDouble(postEntry.pending_payout_value.replaceAll("[^\\d.]", "")) != 0)
            return postEntry.pending_payout_value;
        return "0.0";
    }

    private String convertImageLinksToMarkdown(String text) {
        Pattern pattern = Pattern.compile("(https?://\\S+?\\.(jpg|jpeg|png|gif))");
        Matcher matcher = pattern.matcher(text);
        StringBuffer modifiedText = new StringBuffer();
        while (matcher.find()) {
            String imageUrl = matcher.group();
            matcher.appendReplacement(modifiedText, "![](" + imageUrl + ")");
        }
        matcher.appendTail(modifiedText);
        return modifiedText.toString();
    }

    private String convertYouTubeLinksToEmbed(String text) {
        Pattern pattern = Pattern.compile("(https?://(?:www\\.)?(youtube\\.com/(watch\\?v=|shorts/)|youtu\\.be/|you\\.tube/)([a-zA-Z0-9_-]+))");
        Matcher matcher = pattern.matcher(text);
        StringBuffer modifiedText = new StringBuffer();
        while (matcher.find()) {
            String videoId = matcher.group(4);
            String embedCode = "<div style='position: relative; padding-bottom: 56.25%; height: 0; overflow: hidden;'><iframe style='position: absolute; top: 0; left: 0; width: 100%; height: 100%;' src='https://www.youtube.com/embed/" + videoId + "' frameborder='0' allowfullscreen></iframe></div>";
            matcher.appendReplacement(modifiedText, embedCode);
        }
        matcher.appendTail(modifiedText);
        return modifiedText.toString();
    }
}
