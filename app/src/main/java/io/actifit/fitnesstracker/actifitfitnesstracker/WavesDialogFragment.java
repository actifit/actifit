package io.actifit.fitnesstracker.actifitfitnesstracker;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static io.actifit.fitnesstracker.actifitfitnesstracker.MainActivity.TAG;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.picasso.PicassoImagesPlugin;

public class WavesDialogFragment extends DialogFragment {
    public Context ctx;
    //JSONArray extraVotesList;
    SingleHivePostModel postEntry, postEntryWaves, postEntrySnaps, postEntryLeoThreads, tempPostEntryCont;
    ListView socialView;
    RequestQueue queue = null;
    Button loadMoreBtn, postWaveBtn, expandPreview, retractPreview;
    private ArrayList<SingleHivePostModel> comments;
    private ProgressBar progress, progressMore;
    private PostAdapter postAdapter;
    String start_author, start_permlink;
    EditText replyText;
    TextView mdReplyView;
    //below tracks which post's comments we are fetching
    //as the value increases, we go back in history further
    //0 means today's comments, 1 means yesterday and so on and so forth
    private int postDateContent = 0;

    private static final int WAVE_IMAGE_REQUEST = 7382;

    public WavesDialogFragment() {

    }

    public WavesDialogFragment(Context ctx) {
        this.ctx = ctx;
        //this.extraVotesList = extraVotesList;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (queue==null){
            queue = Volley.newRequestQueue(ctx);
        }
        //progress.setVisibility(View.VISIBLE);
        //int width = getResources().getDisplayMetrics().widthPixels;
        //int height = width * 9 / 16;
        focusTitle();
        //progress.show();
        //loadBalance(queue);
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        //dialog.getWindow().requestFeature(STYLE_NO_TITLE);
        return dialog;
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.waves_page, container, false);
        //View voteModalLayout = LayoutInflater.from(ctx).inflate(R.layout.vote_modal, null);
        renderContent(view);


        return view;
    }

    void focusTitle(){
        if (replyText !=null) {
            replyText.requestFocus();
        }
    }

    private void renderContent(View view){

        socialView = view.findViewById(R.id.contentList);

        progress = view.findViewById(R.id.loader);
        progressMore = view.findViewById(R.id.loaderMore);

        loadMoreBtn = view.findViewById(R.id.load_more);

        postWaveBtn = view.findViewById(R.id.post_wave);

        retractPreview = view.findViewById(R.id.retract_button);
        expandPreview = view.findViewById(R.id.expand_button);

        replyText = view.findViewById(R.id.wave_text);

        comments = new ArrayList<>();

        //initialize needed query
        //tag = getString(R.string.actifit_community);

        // Instantiate the RequestQueue.
        queue = Volley.newRequestQueue(ctx);

        final SharedPreferences sharedPreferences = ctx.getSharedPreferences("actifitSets",MODE_PRIVATE);

        StepsDBHelper mStepsDBHelper = new StepsDBHelper(ctx);

        Button insertImage = view.findViewById(R.id.insert_image_comment);

        insertImage.setOnClickListener(vv ->{

            showChoosingFile();
        });

        mdReplyView = view.findViewById(R.id.md_view);


        //Button proceedCommentBtn = replyModalLayout.findViewById(R.id.proceed_comment_btn);

        //mdReplyView.setMDText(replyText.getText().toString());
        //default content for preview
        //mdReplyView.setMDText(ctx.getString(R.string.wave_preview_lbl));
        final Markwon markwon = Markwon.builder(ctx)
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
                    @org.checkerframework.checker.nullness.qual.NonNull
                    @Override
                    public RequestCreator load(@org.checkerframework.checker.nullness.qual.NonNull AsyncDrawable drawable) {

                        return Picasso.get()
                                .load(drawable.getDestination())
                                //.resize(desiredWidth, desiredHeight)
                                //.centerCrop()
                                .tag(drawable);
                    }

                    @Override
                    public void cancel(@NonNull AsyncDrawable drawable) {
                        Picasso.get()
                                .cancelTag(drawable);
                    }
                }))

                .build();
        markwon.setMarkdown(mdReplyView, ctx.getString(R.string.wave_preview_lbl));

        replyText.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
                if(s.length() != 0) {
                    //mdReplyView.setMDText(replyText.getText().toString());
                    markwon.setMarkdown(mdReplyView, replyText.getText().toString());

                    //store current text
                        /*    SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("steemPostContent",
                                    steemitPostContent.getText().toString());
                            editor.apply();
                            */
                }else{
                    //mdReplyView.setMDText(ctx.getString(R.string.wave_preview_lbl));
                    Markwon markwon = Markwon.create(ctx);
                    markwon.setMarkdown(mdReplyView, ctx.getString(R.string.wave_preview_lbl));
                }
            }
        });


        retractPreview.setOnClickListener( v -> {
            retractPreview.setVisibility(GONE);
            expandPreview.setVisibility(VISIBLE);
            mdReplyView.setVisibility(GONE);
        });

        expandPreview.setOnClickListener( v -> {
            retractPreview.setVisibility(View.VISIBLE);
            expandPreview.setVisibility(GONE);
            mdReplyView.setVisibility(View.VISIBLE);
        });

        postWaveBtn.setOnClickListener(v -> {


            //EditText
            replyText = view.findViewById(R.id.wave_text);

            //proceed with positive action
            //Button proceedCommentBtn = view.findViewById(R.id.proceed_comment_btn);
            //proceedCommentBtn.setOnClickListener(vv ->{
                //DialogInterface.OnClickListener handleCommentAction = (dialogInterface, which) -> {

            String commentStr = replyText.getText().toString();
            if (commentStr.length() < 1){
                Toast.makeText(ctx, ctx.getString(R.string.no_empty_comment),Toast.LENGTH_SHORT).show();
                return;
            }
            ProgressBar taskProgress = view.findViewById(R.id.loader);

            taskProgress.setVisibility(VISIBLE);

            RadioButton wavesRadioButton = view.findViewById(R.id.ecency_waves_option);

                        //check whether to make a WAVE/ecency or a SNAP/peakd

                        //run on its own thread to avoid hiccups
                        Thread trxThread = new Thread(() -> {
                            try {
                                //postEntry = lastPost;
                                String op_name = "comment";

                                String comment_perm = "actifit-"+MainActivity.username.replace(".", "-")
                                        + new SimpleDateFormat("yyyyMMddHHmmssSSS'Z'", Locale.US).format(new Date());
                                //MainActivity.username.replace(".", "-") + "-re-" + postEntry.author.replace(".", "-") + "-" + postEntry.permlink + new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).format(new Date());

                                if (wavesRadioButton.isChecked()) {
                                    postEntry = postEntryWaves;
                                    comment_perm = "wave-" + comment_perm;
                                }else{
                                    postEntry = postEntrySnaps;
                                    comment_perm = "snap-" + comment_perm;
                                }

                                comment_perm = comment_perm.replaceAll("[^a-zA-Z0-9-]", "").toLowerCase();

                                JSONObject cstm_params = new JSONObject();
                                cstm_params.put("author", MainActivity.username);
                                cstm_params.put("permlink", comment_perm);
                                cstm_params.put("title", "");
                                //include comment alongside comment source (android app)
                                cstm_params.put("body", replyText.getText());// + " <br />" + getString(R.string.comment_note));
                                cstm_params.put("parent_author", postEntry.author);
                                cstm_params.put("parent_permlink", postEntry.permlink);

                                JSONObject metaData = new JSONObject();

                                JSONArray tagsArr = new JSONArray();
                                tagsArr.put("hive-193552");
                                tagsArr.put("actifit");
                                metaData.put("tags", tagsArr);

                                metaData.put("app","actifit");

                                //grab app version number
                                try {
                                    metaData.put("step_count", mStepsDBHelper.fetchTodayStepCount());
                                    PackageInfo pInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
                                    String version = pInfo.versionName;
                                    metaData.put("appVersion",version);
                                } catch (PackageManager.NameNotFoundException e) {
                                    e.printStackTrace();
                                }

                                cstm_params.put("json_metadata", metaData.toString());

                                Utils.queryAPI(getContext(), MainActivity.username, op_name, cstm_params, taskProgress,
                                        new Utils.APIResponseListener() {
                                            @Override
                                            public void onResponse(boolean success) {
                                                getActivity().runOnUiThread(() -> {
                                                    taskProgress.setVisibility(GONE);
                                                    Log.e(MainActivity.TAG, "response");
                                                    if (success) {
                                                        Toast.makeText(ctx, ctx.getString(R.string.comment_success), Toast.LENGTH_LONG).show();
                                                        dismiss();
                                                    } else {
                                                        Toast.makeText(ctx, ctx.getString(R.string.comment_error), Toast.LENGTH_LONG).show();
                                                    }
                                                });

                                            }

                                            @Override
                                            public void onError(String errorMessage) {
                                                // Handle the error
                                                getActivity().runOnUiThread(() -> {
                                                    taskProgress.setVisibility(GONE);
                                                    Toast.makeText(ctx, ctx.getString(R.string.comment_error), Toast.LENGTH_LONG).show();
                                                });
                                                Log.e(MainActivity.TAG, errorMessage);
                                            }
                                        }, getActivity());

                            } catch (Exception exc) {
                                exc.printStackTrace();
                            }
                        });
                        trxThread.start();
                    //});

        /*pointer = replyDialogBuilder.setView(view)
                .setTitle(ctx.getString(R.string.reply))
                .setIcon(ctx.getResources().getDrawable(R.drawable.actifit_logo))
                .setPositiveButton(ctx.getString(R.string.reply_action), handleCommentAction)
                .setNegativeButton(ctx.getString(R.string.cancel_action), null)
                .create();


        replyDialogBuilder.show();*/

                    //give focus to the edit text area
                    replyText.requestFocus();
                    replyText.setSelection(replyText.getText().length());


        });

        //set load more functionality
        loadMoreBtn.setOnClickListener(v -> {
            loadMoreBtn.setVisibility(GONE);
            //progressMore.setVisibility(View.VISIBLE);
            //load older posts' content
            //add 2 as we are fetching 2 posts at a time
            postDateContent += 2;
            loadContent(false);
        });


        Button closeButton = view.findViewById(R.id.close_btn);
        closeButton.setOnClickListener(vv -> {
            dismiss(); // Dismiss the DialogFragment when the close button is clicked
        });

        //set modal for social info
        TextView modalBtn = view.findViewById(R.id.social_info);
        modalBtn.setOnClickListener(v -> {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(ctx);

            AlertDialog pointer = dialogBuilder.setMessage(Html.fromHtml(getString(R.string.waves_description)))
                    .setTitle(getString(R.string.short_waves))
                    .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                    .setPositiveButton(getString(R.string.close_button), null)
                    .create();

            dialogBuilder.show();
        });

        //capture scroll event to bottom

        socialView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                // Not needed for this implementation
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (postAdapter != null  && postAdapter.Size() > 0) {
                    if (socialView.getLastVisiblePosition() == totalItemCount - 1
                            && socialView.getChildAt(socialView.getChildCount() - 1).getBottom() <= socialView.getHeight()) {
                        // Scrolled to the bottom
                        // Perform your action here
                        loadMoreBtn.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        /*socialView.post(() -> {
            // Delayed task to be executed after rendering is complete
            progress.setVisibility(View.GONE); // Hide the ProgressBar
        });*/

        //refresh user login
        if (!MainActivity.username.equals("")) {
            String pkey = sharedPreferences.getString("actifitPst", "");

            //authorize user login based on credentials if user is already verified
            if (!pkey.equals("")) {
                String loginAuthUrl = Utils.apiUrl(ctx)+getString(R.string.login_auth);


                JSONObject loginSettings = new JSONObject();
                try {
                    loginSettings.put(getString(R.string.username_param), MainActivity.username);
                    loginSettings.put(getString(R.string.pkey_param), pkey);
                    loginSettings.put(getString(R.string.bchain_param), "HIVE");//default always HIVE
                    loginSettings.put(getString(R.string.keeploggedin_param), false);//TODO make dynamic
                    loginSettings.put(getString(R.string.login_source), getString(R.string.android) + BuildConfig.VERSION_NAME);
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
                                        Log.d(TAG, response.toString());
                                        LoginActivity.accessToken = response.getString(getString(R.string.login_token));
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
                                Log.e(TAG, "Login error");
                            }
                        });

                queue.add(loginRequest);
            }


        }
        //load posts for first time
        loadContent(true);

        //set focus on text entry
        focusTitle();

    }

    //handles the display of image selection
    private void showChoosingFile() {

        //ensure we have proper permissions for image upload
        if (shouldAskPermissions()) {
            askPermissions();
        }

        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_img_title)), WAVE_IMAGE_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (bitmap != null) {
            bitmap.recycle();
        }

        if (requestCode == WAVE_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            fileUri = data.getData();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(ctx.getContentResolver(), fileUri);

                Utils.uploadFile(bitmap, fileUri, replyText, ctx, getActivity());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



    public void loadContent(Boolean showFullProgress){


        HiveRequests hiveReq = new HiveRequests(ctx);

        Thread thread = new Thread(() -> {

            try {
                JSONObject params = new JSONObject();
                params.put("sort", "posts");
                params.put("account", getString(R.string.ecency_waves_account));
                params.put("start_author", start_author);
                params.put("start_permlink", start_permlink);
                params.put("limit", 10);//grab last 10 posts, covering 10 days content
                params.put("observer", "");

                //progress = new ProgressBar(this);
                // progress = new ProgressDialog(this);
                getActivity().runOnUiThread(() -> {
                    if (showFullProgress) {
                        progress.setVisibility(View.VISIBLE);
                    } else {
                        progressMore.setVisibility(View.VISIBLE);
                    }
                });


                //grab array from result
                JSONArray result = hiveReq.getAccountPosts(params);
                //SingleHivePostModel lastPost = null;

                if (result.length() > postDateContent){
                    //fill top most post comments
                    postEntryWaves = new SingleHivePostModel((result.getJSONObject(postDateContent)), ctx);
                    postEntryWaves.isThread = true;
                    postEntryWaves.threadType = getString(R.string.ecency_waves_account);
                    //lastPost = postEntry;
                    comments.addAll(loadComments(postEntryWaves));
                    //also use 2nd post comments to avoid having empty content if post is brand new
                    if (result.length() > postDateContent + 1) {
                        tempPostEntryCont = new SingleHivePostModel((result.getJSONObject(postDateContent + 1)), ctx);
                        tempPostEntryCont.isThread = true;
                        tempPostEntryCont.threadType = getString(R.string.ecency_waves_account);
                        //lastPost = postEntry;
                        comments.addAll(loadComments(tempPostEntryCont));
                    }
                }

                //also load snaps (by peakd) content
                params.put("account", getString(R.string.peakd_snaps_account));
                JSONArray snaps = hiveReq.getAccountPosts(params);
                if (snaps.length() > postDateContent){
                    //fill top most post comments
                    postEntrySnaps = new SingleHivePostModel((snaps.getJSONObject(postDateContent)), ctx);
                    postEntrySnaps.isThread = true;
                    postEntrySnaps.threadType = getString(R.string.peakd_snaps_account);
                    //lastPost = postEntry;
                    comments.addAll(loadComments(postEntrySnaps));
                    //also use 2nd post comments to avoid having empty content if post is brand new
                    if (snaps.length() > postDateContent + 1) {
                        tempPostEntryCont = new SingleHivePostModel((snaps.getJSONObject(postDateContent + 1)), ctx);
                        tempPostEntryCont.isThread = true;
                        tempPostEntryCont.threadType = getString(R.string.peakd_snaps_account);
                        //lastPost = postEntry;
                        comments.addAll(loadComments(tempPostEntryCont));
                    }
                }

                //also load threads (by leo) content
                params.put("account", getString(R.string.leo_threads_account));
                JSONArray leothreads = hiveReq.getAccountPosts(params);
                if (leothreads.length() > postDateContent){
                    //fill top most post comments
                    postEntryLeoThreads = new SingleHivePostModel((leothreads.getJSONObject(postDateContent)), ctx);
                    postEntryLeoThreads.isThread = true;
                    postEntryLeoThreads.threadType = getString(R.string.leo_threads_account);
                    //lastPost = postEntry;
                    comments.addAll(loadComments(postEntryLeoThreads));
                    //also use 2nd post comments to avoid having empty content if post is brand new
                    if (leothreads.length() > postDateContent + 1) {
                        tempPostEntryCont = new SingleHivePostModel((leothreads.getJSONObject(postDateContent + 1)), ctx);
                        tempPostEntryCont.isThread = true;
                        tempPostEntryCont.threadType = getString(R.string.leo_threads_account);
                        //lastPost = postEntry;
                        comments.addAll(loadComments(tempPostEntryCont));
                    }
                }

                Collections.sort(comments);
                Collections.reverse(comments);

                /*
                for (int i = 0; i < result.length(); i++) {
                    // Retrieve each JSON object within the JSON array
                    //JSONObject jsonObject = new JSONObject()

                    postEntry = new SingleHivePostModel((result.getJSONObject(i)));
                    lastPost = postEntry;

                    comments.addAll(loadComments(postEntry));

                    //posts.add(postEntry);

                    //thread.start();

                }
*/
                //update last loaded post author and permlink for pagination purposes
                /*if (lastPost != null) {
                    start_author = lastPost.author;
                    start_permlink = lastPost.permlink;
                }*/
                //Collections.sort(posts);
                // Create the adapter to convert the array to views
                //String pkey = sharedPreferences.getString("actifitPst", "");

                postAdapter = new PostAdapter(ctx, comments, socialView, ctx, true, getActivity());
                //postAdapter = new PostAdapter(getApplicationContext(), posts, socialView, SocialActivity.this, false);

                // Execute UI-related code on the main thread
                getActivity().runOnUiThread(() -> {

                    if (!showFullProgress) {

                        //case for maintaining scroll position upon append
                        int currentPosition = socialView.getFirstVisiblePosition();
                        View v = socialView.getChildAt(0);
                        int topOffset = (v == null) ? 0 : v.getTop();

                        // Set the new adapter
                        socialView.setAdapter(postAdapter);

                        // Restore the scroll position
                        socialView.setSelectionFromTop(currentPosition, topOffset);

                    } else {
                        socialView.setAdapter(postAdapter);
                    }

                    //only hide after ready to render
                    socialView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            // Remove the listener to avoid multiple callbacks
                            socialView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                            // Delayed task to be executed after rendering is complete
                            progress.setVisibility(GONE); // Hide the ProgressBar
                            progressMore.setVisibility(GONE);
                        }
                    });

                    //hide load more button
                    loadMoreBtn.setVisibility(View.INVISIBLE);

                });


            } catch (Exception e) {
                //hide dialog
                progress.setVisibility(GONE);
                progressMore.setVisibility(GONE);
                //progress.hide();
                //actifitTransactionsError.setVisibility(View.VISIBLE);
                e.printStackTrace();
            }



        });
        thread.start();

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
            for (int i = result.length() - 1; i > -1 ; i--) {
                //comments are basically like posts under hive
                SingleHivePostModel commentEntry = new SingleHivePostModel((result.getJSONObject(i)), ctx);
                commentEntry.isThread = postEntry.isThread;
                commentEntry.threadType = postEntry.threadType;
                commentList.add(commentEntry);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return commentList;
        //});
        //thread.start();

    }


    //required function to ask for proper read/write permissions on later Android versions
    protected boolean shouldAskPermissions() {
        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1);
    }

    @TargetApi(23)
    protected void askPermissions() {
        String[] permissions = {
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE"
        };
        int requestCode = 200;
        requestPermissions(permissions, requestCode);
    }


    /*************************************/
    private Uri fileUri;
    private Bitmap bitmap;




}
