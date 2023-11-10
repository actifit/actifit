package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.HttpStack;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.squareup.picasso.Picasso;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.tus.java.client.ProtocolException;
import io.tus.java.client.TusClient;
import io.tus.java.client.TusExecutor;
import io.tus.java.client.TusURLMemoryStore;
import io.tus.java.client.TusUpload;
import io.tus.java.client.TusUploader;
import okhttp3.CookieJar;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;
import static android.view.Gravity.CENTER_HORIZONTAL;


public class VideoUploadFragment extends DialogFragment {

    static String[][] statusList = {
            {"uploaded", "0", "Uploaded..▲"},
            {"encoding_queued", "1", "Queued for Encoding..→"},
            {"encoding_ipfs", "2", "Processing Encoding..⏳"},
            {"encoding_failed", "3", "Encoding Failed..x"},
            {"deleted", "4", "Deleted..\uD83D\uDDD1"},
            {"publish_manual", "5", "Ready to publish..✅"},
            {"published", "6", "Published✓"}
    };

    Context ctx;
    String accessToken;
    //private ListView vidView;
    private LinearLayout vidsView;

    public ArrayList<UploadedVideoModel> vidsList;

    private ProgressBar submitLoader;//loader, , thumbUploadProgress, vidUploadProgress;
    TextView thumbProgressPercent, vidProgressPercent;
    Button chooseButton, recordButton, vidSelector;
    HorizontalScrollView hScrollView;
    VideoView newVideoView;
    ImageView newVidThumbView;
    //Button playVid, stopVid;
    TextView noVids;
    LinearLayout newVidLayoutContainer, progressContainer;
    View newVidInnerView;
    String thumbnailName, origFileName, thumbnailUrl;
    double vidDuration;
    long vidSize;
    RequestQueue requestQueue;
    //PostSteemitActivity parent;
    Activity parent;
    //Volley requestQueue;

    public RotateAnimation rotate;
    public TextView refreshList;

    boolean addToPostEnabled = true;

    //track choosing video intent
    private static final int CHOOSING_VID_REQUEST = 1235;

    //PostSteemitActivity parent
    public VideoUploadFragment(Context ctx, String accessToken, Activity parent, boolean addToPostEnabled) {
        this.ctx = ctx;
        this.accessToken = accessToken;
        this.parent = parent;
        this.addToPostEnabled = addToPostEnabled;
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

    //handles the display of video selection
    private void showChoosingFile(ViewGroup container) {

        //ensure we have proper permissions for image upload
        if (shouldAskPermissions()) {
            askPermissions();
        }

        //if (newVidInnerView == null){
        if (newVidInnerView != null){
            //remove old view for old uploads
            newVidLayoutContainer.removeView(newVidInnerView);
        }
        //create new section for video display/upload
        newVidInnerView = LayoutInflater.from(getContext()).inflate(R.layout.vid_single_entry, container, false);
        newVidLayoutContainer.addView(newVidInnerView);
         //}


        /*ActivityResultLauncher<Intent> chooseVideoLauncher =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                // Handle the result here
                                Intent data = result.getData();
                                // Process the selected video
                            }
                        });*/

        Intent intent = new Intent();

        intent.setType("video/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_vid_title)), CHOOSING_VID_REQUEST);
        //chooseVideoLauncher.launch(Intent.createChooser(intent, getString(R.string.select_vid_title)));



    }

    public void setVidsList(JSONArray vidsListParam){
        this.vidsList = new ArrayList<>();
        //clear prior to adding new list
        this.vidsList.clear();
        //this.vidsList = new ArrayList<>();
        if (vidsListParam != null && vidsListParam.length() >0) {
            for (int i = 0; i < vidsListParam.length(); i++) {
                try {
                    this.vidsList.add(new UploadedVideoModel(vidsListParam.getJSONObject(i)));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            chooseButton.performClick();
        }else{

            System.out.println(">>> no user vids");
            //show no user vids msg
            /*TextView tv = new TextView (vidsView.getContext());//(ctx);
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setText(ctx.getString(R.string.no_vids_found));
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(10, 10, 10, 10);

            vidsView.addView(tv);*/

            refreshList.clearAnimation();
            if (submitLoader != null) {
                submitLoader.setVisibility(View.GONE);
            }
            noVids.setVisibility(View.VISIBLE);
        }
    }

    public void showVids(ViewGroup container){

        //videoAdapter = new UploadedVideoAdapter(ctx, vidsList, MainActivity.username);

        //vidView.setAdapter(videoAdapter);
//        if (loader != null) {
//            loader.setVisibility(View.GONE);
//        }
        //clean up all prior vids
        if (vidsView!=null) {
            vidsView.removeAllViewsInLayout();
        }
        vidsView.setPadding(0,0,0,0);

        //loop through vids and display them
        for (int i=0;i<vidsList.size();i++) {
            // Define the URL of the video
            UploadedVideoModel vidEntry = vidsList.get(i);
            String videoUrl = vidEntry.filename;//vidEntry.video_v2;//"https://example.com/video.mp4";
            if (videoUrl != null && !videoUrl.equals("")) {
                View convertView = LayoutInflater.from(getContext()).inflate(R.layout.vid_single_entry, container, false);


                //load video
                if (!videoUrl.startsWith("http")) {
                    videoUrl = ctx.getString(R.string.three_speak_cdn) + "/" + videoUrl.replace("ipfs://", "");
                }

                ImageView img = convertView.findViewById(R.id.thumbnail);
                Handler uiHandler = new Handler(Looper.getMainLooper());
                uiHandler.post(() -> {
                            Picasso.get().load(vidEntry.thumbUrl).into(img);
                        });

                // Parse the video URL
                Uri uri = Uri.parse(videoUrl);

                /*
                ExoPlayer player = new ExoPlayer.Builder(ctx).build();
                PlayerView playerView = convertView.findViewById(R.id.player_view);
                playerView.setPlayer(player);

                // Create a DataSource.Factory
                DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(ctx, Util.getUserAgent(ctx, "Actifit"));

                // Create a MediaSource for the .mp4 file
                MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri));

                player.setMediaSource(videoSource);

                // Prepare the player.
                player.prepare();
                */

                // Define the maximum height (in pixels) you want for the VideoView
                VideoView videoView = convertView.findViewById(R.id.videoView);
                // Set the video URI
                videoView.setVideoURI(uri);

                // Create a media controller to control playback
                MediaController mediaController = new MediaController(getActivity());
                videoView.setMediaController(mediaController);
                videoView.setEnabled(true);
                //
                mediaController.setAnchorView(videoView);

                Button playVid = convertView.findViewById(R.id.playVid);
                Button stopVid = convertView.findViewById(R.id.stopVid);
                playVid.setOnClickListener(v -> {

                    img.setVisibility(View.GONE);
                    videoView.setVisibility(View.VISIBLE);
                    videoView.start();

                    playVid.setVisibility(View.GONE);
                    stopVid.setVisibility(View.VISIBLE);
                });

                stopVid.setOnClickListener(v -> {

                    videoView.pause();
                    playVid.setVisibility(View.VISIBLE);
                    stopVid.setVisibility(View.GONE);
                });

                Button addToPost = convertView.findViewById(R.id.addVidToPost);
                if (addToPostEnabled) {
                    addToPost.setOnClickListener(v -> {
                        //grab video thumb url
                        String thumbUrl = //ctx.getString(R.string.three_speak_cdn) + "/" +
                                vidEntry.thumbUrl.replace("ipfs://", "");
                        String vidLink = "[![](" + thumbUrl + ")](https://3speak.tv/watch?v=" + MainActivity.username + "/" + vidEntry.permlink + ")";
                        ((PostSteemitActivity) parent).setMainVid(vidEntry);
                        ((PostSteemitActivity) parent).appendContent(vidLink);
                        dismiss();
                        //
                    /*
                    let thumbUrl = 'https://ipfs-3speak.b-cdn.net/ipfs/'+vid.thumbnail.replace('ipfs://','');

                    this.vidPostContent =
                            '[![]('+thumbUrl+')](https://3speak.tv/watch?v='+vid.owner+'/'+vid.permlink+')'*/

                    });


                    Button markVidPublished = convertView.findViewById(R.id.markVidPublished);
                    //if video not published and not failed, we can add to post
                    //if (!vidEntry.status.equals("published") && !vidEntry.status.equals("encoding_failed")){
                    if (vidEntry.status.equals("publish_manual")){ //|| vidEntry.status.equals("published")){
                        addToPost.setVisibility(View.VISIBLE);
                        //markVidPublished.setVisibility(View.VISIBLE);
                    }else{
                        addToPost.setVisibility(View.GONE);
                        markVidPublished.setVisibility(View.GONE);
                    }

                    markVidPublished.setOnClickListener( v->{
                        Utils.markVideoPublished(ctx, requestQueue, vidEntry);
                        //Utils.connectSession3S(requestQueue, ctx, this);
                        refreshList.performClick();
                    });
                }else{
                    addToPost.setVisibility(View.GONE);
                }

                //add extra data for vid
                TextView fileName = convertView.findViewById(R.id.title_val);
                fileName.setText(vidEntry.title);

                TextView status = convertView.findViewById(R.id.status_val);
                status.setText(Utils.findMatchingStatus(vidEntry.status, VideoUploadFragment.statusList));

                TextView duration = convertView.findViewById(R.id.duration_val);
                DecimalFormat decimalFormat = new DecimalFormat("#.##");
                String formattedNumber = decimalFormat.format(vidEntry.duration);

                duration.setText(formattedNumber + " sec");
                Double sizeInMB = vidEntry.size;
                if (sizeInMB >0){
                    sizeInMB = vidEntry.size / 1024 / 1024;
                }
                TextView size = convertView.findViewById(R.id.size_val);
                formattedNumber = decimalFormat.format(sizeInMB);

                size.setText(formattedNumber + " MB");

                TextView date = convertView.findViewById(R.id.date_val);
                date.setText(Utils.getTimeDifference(vidEntry.created));

                vidsView.addView(convertView);
            }
        }
        refreshList.clearAnimation();
        if (submitLoader != null) {
            submitLoader.setVisibility(View.GONE);
        }
        vidsView.setGravity(Gravity.LEFT);
        hScrollView.smoothScrollTo(0, 0);

    }

    @Override
    public void onStart() {
        super.onStart();

        // Adjust the dimensions of the dialog
        if (getDialog() != null) {
            int width = ViewGroup.LayoutParams.MATCH_PARENT; // Full width
            int height = ViewGroup.LayoutParams.WRAP_CONTENT; // Adjust as needed

            getDialog().getWindow().setLayout(width, height);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {


        View view = inflater.inflate(R.layout.video_upload_view, container, false);


        rotate = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(2000);
        rotate.setInterpolator(new LinearInterpolator());
        rotate.setRepeatCount(Animation.INFINITE);

        vidsView = view.findViewById(R.id.vidsList);

        newVidLayoutContainer = view.findViewById(R.id.newVidLayoutContainer);
        progressContainer = view.findViewById(R.id.progressContainer);

        /*videoView = view.findViewById(R.id.videoView);
        thumbView = view.findViewById(R.id.thumbView);
        playVid = view.findViewById(R.id.playVid);
        stopVid = view.findViewById(R.id.stopVid);

         */

//        loader = view.findViewById(R.id.loader);

        //thumbUploadProgress = view.findViewById(R.id.thumbUploadProgress);
        //vidUploadProgress = view.findViewById(R.id.vidUploadProgress);

        thumbProgressPercent = view.findViewById(R.id.thumbUploadProgressPercent);
        vidProgressPercent = view.findViewById(R.id.vidUploadProgressPercent);


        //initiate volley with cookie support

        //CustomCookieStore cookieManager;
        //cookieManager = new CustomCookieStore();

        //requestQueue = Volley.newRequestQueue(ctx);
        //requestQueue = new Volley(cookieManager.getCookieJar());

        requestQueue = Volley.newRequestQueue(ctx);
        refreshList = view.findViewById(R.id.refresh_list);

        hScrollView = view.findViewById(R.id.horizontalScrollView);
        noVids = view.findViewById(R.id.no_vids);


        //info boxes
        //set modal for social info
        TextView vidsListBtn = view.findViewById(R.id.vids_list_info);
        TextView uploadInfoBtn = view.findViewById(R.id.upload_vid_info);
        vidsListBtn.setOnClickListener(v -> {
            getActivity().runOnUiThread( () -> {
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());

                AlertDialog pointer = dialogBuilder.setMessage(Html.fromHtml(getString(R.string.vids_list_description)))
                        .setTitle(getString(R.string.actifit_info))
                        .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                        .setPositiveButton(getString(R.string.close_button), null)
                        .create();

                dialogBuilder.show();
            });
        });
        uploadInfoBtn.setOnClickListener(v -> {
            getActivity().runOnUiThread( () -> {
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());

                AlertDialog pointer = dialogBuilder.setMessage(Html.fromHtml(getString(R.string.upload_vid_description)))
                        .setTitle(getString(R.string.actifit_info))
                        .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                        .setPositiveButton(getString(R.string.close_button), null)
                        .create();

                dialogBuilder.show();
            });
        });

        //load up user video data
        //connect to 3speak first
        refreshList.startAnimation(rotate);
        Utils.connectSession3S(requestQueue, ctx, this);


        refreshList.setOnClickListener(v -> {
            refreshList.startAnimation(rotate);
            Utils.connectSession3S(requestQueue, ctx, this);
        });



        //

        vidSelector = view.findViewById(R.id.vidSelector);
        vidSelector.setOnClickListener(v -> {
            showChoosingFile(container);
        });

        chooseButton = view.findViewById(R.id.chooseFile);

        chooseButton.setOnClickListener(v -> {
            //showChoosingFile();
            showVids(container);
        });

        //videoView = view.findViewById(R.id.videoView);

        //show submit video button if video is ready/uploaded and not submitted yet

        recordButton = view.findViewById(R.id.recordVideo);
        recordButton.setOnClickListener(v -> {
            if (newVidInnerView != null){
                //remove old view for old uploads
                newVidLayoutContainer.removeView(newVidInnerView);
            }
            //create new section for video display/upload
            newVidInnerView = LayoutInflater.from(getContext()).inflate(R.layout.vid_single_entry, container, false);
            newVidLayoutContainer.addView(newVidInnerView);
            // on below line opening an intent to capture a video.
            Intent i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            // on below line starting an activity for result.
            startActivityForResult(i, 1);
        });

        // Find and set click listener for the close button
        Button closeButton = view.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> {
            dismiss(); // Dismiss the DialogFragment when the close button is clicked
        });

        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == CHOOSING_VID_REQUEST || requestCode == 1)
                && resultCode == RESULT_OK ) {
            Uri fileUri = data.getData();
            progressContainer.setVisibility(View.VISIBLE);
            //show progress
            //.setVisibility(View.VISIBLE);
            //vidUploadProgress.setVisibility(View.VISIBLE);
            Thread thread = new Thread(() -> {
                try {
                    Bitmap thumb = Utils.generateThumbnail(ctx, fileUri);
                    uploadThumbnail(thumb);
                    vidSize = Utils.getVidSize(ctx, fileUri);
                    vidDuration = Utils.getVidDuration(ctx, fileUri);
                    origFileName = Utils.getOriginalFileName(ctx, fileUri);
                    uploadVideo(fileUri);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            thread.start();

        }
    }

    /*************************************/
    //type param contains whether this is an image upload (0) or vid (1)
    private void tusUploadHandler(int type, Uri fileUri, Bitmap origThumb){
        // Create a new TusClient instance
        TusClient client = new TusClient();
        //TusUploader tusUploader = null;
        try {
            // Configure tus HTTP endpoint. This URL will be used for creating new uploads
            // using the Creation extension
            client.setUploadCreationURL(new URL(getString(R.string.three_speak_tus_upload_url)));

            // Enable resumable uploads by storing the upload URL in memory
            client.enableResuming(new TusURLMemoryStore());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }



        /*Thread thread = new Thread(new Runnable(){
            @Override
            public void run() {*/


                try {
            //grab reference to video
            File file = Utils.getFileFromUri(fileUri, ctx);
            final TusUpload upload = new TusUpload(file);

            // We wrap our uploading code in the TusExecutor class which will automatically catch
            // exceptions and issue retries with small delays between them and take fully
            // advantage of tus' resumability to offer more reliability.
            // This step is optional but highly recommended.


            TusExecutor executor = new TusExecutor() {
                @Override
                protected void makeAttempt() throws ProtocolException, IOException {
                    // First try to resume an upload. If that's not possible we will create a new
                    // upload and get a TusUploader in return. This class is responsible for opening
                    // a connection to the remote server and doing the uploading.
                    TusUploader uploader = client.resumeOrCreateUpload(upload);

                    // Upload the file in chunks of 100*1KB sizes.
                    uploader.setChunkSize(100*1024);

                    // Upload the file as long as data is available. Once the
                    // file has been fully uploaded the method will return -1
                    do {
                        // Calculate the progress using the total size of the uploading file and
                        // the current offset.
                        long totalBytes = upload.getSize();
                        long bytesUploaded = uploader.getOffset();
                        double progress = (double) bytesUploaded / totalBytes * 100;
                        DecimalFormat decimalFormat = new DecimalFormat("##0.00");

                        //String output = String.format("Upload at %6.2f%%.\n", progress);
                        decimalFormat.setMinimumIntegerDigits(1);
                        decimalFormat.setMinimumFractionDigits(2);
                        String formattedString = String.format("Upload at %s%%.\n", decimalFormat.format(progress));

                        System.out.printf("Upload at %06.2f%%.\n", progress);
                        getActivity().runOnUiThread(() -> {
                            if (type == 0) {
                                thumbProgressPercent.setText(Html.fromHtml("&#128444") + " " + formattedString);
                            } else {
                                vidProgressPercent.setText(Html.fromHtml("&#128249") + " " + formattedString);
                            }
                        });
                    } while(uploader.uploadChunk() > -1);

                    // Allow the HTTP connection to be closed and cleaned up
                    uploader.finish();

                    System.out.println("Upload finished.");
                    System.out.format("Upload available at: %s", uploader.getUploadURL().toString());

                    String url = uploader.getUploadURL().toString();
                    String name = url.replace(getString(R.string.three_speak_file_url), "");
                    //getActivity().runOnUiThread(() -> {
                        uploadComplete(type, url, name, origThumb, fileUri);
                    //});

                }
            };
            executor.makeAttempts();



            //tusUploader = client.createUpload(tusUpload);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

         /*   }
        });
        thread.start();*/
    }

    //implementing 3speak thumbnail upload functionality
    private void uploadThumbnail(Bitmap thumbBitmap){
        try {
            Uri thumbUri = Utils.getBitmapFileUri(thumbBitmap);
            tusUploadHandler(0, thumbUri, thumbBitmap);
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    //implementing 3speak upload functionality
    private void uploadVideo(Uri fileUri){
        tusUploadHandler(1, fileUri, null);

    }

    private void uploadComplete(int type, String url, String name, Bitmap thumb, Uri fileUri){
        try {
            System.out.println("name: "+name);
            System.out.println("url: "+url);
            newVidThumbView = newVidInnerView.findViewById(R.id.thumbnail);
            newVideoView = newVidInnerView.findViewById(R.id.videoView);
            submitLoader = newVidInnerView.findViewById(R.id.submitLoader);
            Handler uiHandler = new Handler(Looper.getMainLooper());
            uiHandler.post(() -> {
            if (type == 0) {
                    thumbnailUrl = url;
                    thumbnailName = name;
                    //thumb handler
                    newVidThumbView.setImageBitmap(thumb);
                    newVidThumbView.setVisibility(View.VISIBLE);

            }else{
                //video handler
                //vidUploadProgress.setVisibility(View.GONE);
                //videoView.setVisibility(View.VISIBLE);
                // on below line setting video uri for our video view.
                //newVideoView.setVideoURI(Uri.parse(url));
                newVideoView.setVideoURI(fileUri);

                MediaController mediaController = new MediaController(getActivity());
                newVideoView.setMediaController(mediaController);
                newVideoView.setEnabled(true);
                //
                mediaController.setAnchorView(newVideoView);

                TextView duration = newVidInnerView.findViewById(R.id.duration_val);
                DecimalFormat decimalFormat = new DecimalFormat("#.##");
                String formattedNumber = decimalFormat.format(vidDuration);

                duration.setText(formattedNumber + " sec");
                long sizeInMB = vidSize;
                if (sizeInMB >0){
                    sizeInMB = vidSize / 1024 / 1024;
                }
                TextView size = newVidInnerView.findViewById(R.id.size_val);
                formattedNumber = decimalFormat.format(sizeInMB);

                size.setText(formattedNumber + " MB");

                TextView date = newVidInnerView.findViewById(R.id.date_val);
                date.setText(R.string.Now);

                LinearLayout statusContainer = newVidInnerView.findViewById(R.id.status_container);
                statusContainer.setVisibility(View.GONE);

                // on below line starting a video view
                //videoView.start();
                Button playVid = newVidInnerView.findViewById(R.id.playVid);
                playVid.setVisibility(View.VISIBLE);
                Button stopVid = newVidInnerView.findViewById(R.id.stopVid);
//                playVid.setVisibility(View.VISIBLE);

                playVid.setOnClickListener(v -> {
                    //showChoosingFile();
                    //showVids(container);
                    newVidThumbView.setVisibility(View.GONE);
                    newVideoView.setVisibility(View.VISIBLE);
                    newVideoView.start();
                    //videoView.resume();
                    playVid.setVisibility(View.GONE);
                    stopVid.setVisibility(View.VISIBLE);
                });

                stopVid.setOnClickListener(v -> {
                    //showChoosingFile();
                    //showVids(container);
                    //videoView.stopPlayback();
                    newVideoView.pause();
                    playVid.setVisibility(View.VISIBLE);
                    stopVid.setVisibility(View.GONE);
                });

                Button submitVid = newVidInnerView.findViewById(R.id.submitVid);
                submitVid.setVisibility(View.VISIBLE);
                submitVid.setOnClickListener(v ->{
                    submitVid3Speak(name, origFileName, vidSize, vidDuration, thumbnailName);
                });
                progressContainer.setVisibility(View.GONE);
            }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }



    }


    private void submitVid3Speak(String vidName, String origFileName, long vidSize, double vidLen, String thumbName){
        if (submitLoader != null) {
            submitLoader.setVisibility(View.VISIBLE);
        }
        JSONObject videoInfo = new JSONObject();
        try {
            videoInfo.put("filename", vidName);
            videoInfo.put("oFilename", origFileName);
            videoInfo.put("size", vidSize);
            videoInfo.put("duration", vidLen);
            videoInfo.put("thumbnail", thumbName);
            videoInfo.put("owner", MainActivity.username);
            videoInfo.put("isReel", false);
            System.out.println("submitVid3speak");
            System.out.println(videoInfo.toString());

            String url = getString(R.string.three_speak_upload_vid)+"?app=actifit";

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url,  videoInfo,
                    response -> {
                        // Handle the response here
                        try {
                            getActivity().runOnUiThread(() -> {
                                //show confirmation
                                Toast.makeText(ctx, ctx.getString(R.string.submit_vid_success), Toast.LENGTH_LONG).show();
                            });
                            if (response.has("status")) {


                                //refresh vids list
                                SharedPreferences sharedPreferences = ctx.getSharedPreferences("actifitSets",MODE_PRIVATE);
                                String xcstkn = sharedPreferences.getString(ctx.getString(R.string.three_speak_saved_token),"");
                                final VideoUploadFragment vidFragRef = this;
                                //update vids

                            } else {
                                // Handle other status codes if needed
                                // ...
                                if (submitLoader != null) {
                                    submitLoader.setVisibility(View.GONE);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally  {
                            cleanupVid();
                        }
                    },
                    error -> {
                        // Handle error response
                        error.printStackTrace();
                        getActivity().runOnUiThread(() -> {
                            //show confirmation
                            Toast.makeText(ctx, ctx.getString(R.string.submit_vid_success), Toast.LENGTH_LONG).show();


                            //Toast.makeText(ctx, ctx.getString(R.string.submit_vid_success), Toast.LENGTH_LONG).show();;
                        });
                        if (submitLoader != null) {
                            submitLoader.setVisibility(View.GONE);
                        }
                        cleanupVid();
                    }) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    headers.put("Authorization", "Bearer " + LoginActivity.accessToken);
                    return headers;
                }
            };

            // Add the request to the RequestQueue
            //RequestQueue queue = Volley.newRequestQueue(ctx);
            requestQueue.add(request);

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void cleanupVid(){
        //wait 2 seconds and then refresh user vids
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            refreshList.performClick();
        }, 2000);

        //also remove view
        newVidLayoutContainer.removeView(newVidInnerView);
        newVidInnerView = null;
    }

    /*************************************/

}
