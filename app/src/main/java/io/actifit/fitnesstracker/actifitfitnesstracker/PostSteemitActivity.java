package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.bitcoinj.core.TransactionBroadcast.random;
import static java.lang.Integer.parseInt;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.widget.NestedScrollView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.picasso.PicassoImagesPlugin;

import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.Button;

import io.actifit.fitnesstracker.actifitfitnesstracker.AiService;
import io.actifit.fitnesstracker.actifitfitnesstracker.AiResponse;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;


public class PostSteemitActivity extends BaseActivity implements View.OnClickListener{

    private StepsDBHelper mStepsDBHelper;
    private String notification = "";
    private int min_step_limit = 1;
    private static final int min_reward_limit = 5000;
    private int min_char_count = 100;
    private Context steemit_post_context;

    //track Choosing Image Intent
    private static final int CHOOSING_IMAGE_REQUEST = 1234;

    //track choosing video intent
    private static final int CHOOSING_VID_REQUEST = 1235;

    private EditText steemitPostContent;
    private NestedScrollView nestedScrollView;

    private UploadedVideoModel selVidEntry;

    private Uri fileUri;
    private Bitmap bitmap;
    private ImageView image_preview;

    private EditText stepCountContainer;
    private Activity currentActivity;

    //tracks whether user synched his Fitbit data to avoid refetching activity count from current device
    private static int fitbitSyncDone = 0;

    //tracks whether user wants to post yesterday's data instead
    private static boolean yesterdayReport = false;

    private String fitbitUserId;


    EditText steemitPostTitle;

    TextView measureSectionLabel;

    //references to the new points system in post
    TextView titleCountRef, dateCountRef, activityCountRef, activityTypeCountRef, tagsCountRef,
            contentCountRef, charCount, minCharCount, charInfo;

    TextView heightSizeUnit;
    TextView weightSizeUnit;
    TextView waistSizeUnit;
    TextView chestSizeUnit;
    TextView thighsSizeUnit;

    EditText steemitPostTags;

    //CheckBox fullAFITPay;

    EditText heightSize;
    EditText weightSize;
    EditText bodyFat;
    EditText chestSize;
    EditText thighsSize;
    EditText waistSize;

    //MarkedView mdView;
    TextView mdView;

    MultiSelectionSpinner activityTypeSelector;

    String accountUsername, accountPostingKey, accountActivityCount, finalPostTitle, finalPostTags,
            finalPostContent;
    int selectedActivityCount;
    String heightVal, weightVal, waistVal, chestVal, thighsVal, bodyFatVal,
            heightUnit, weightUnit, waistUnit, chestUnit, thighsUnit;

    String selectedActivitiesVal;

    //Boolean fullAFITPayVal;


    /* CHANGES FOR FIXING IMAGE UPLOAD */
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> videoPickerLauncher; // Added launcher for video
    // Use an ExecutorService for background tasks
    private ExecutorService executorService;
    final String TAG = "PostSteemitActivity";
    /* CHANGES FOR FIXING IMAGE UPLOAD */

    //required function to ask for proper read/write permissions on later Android versions
    protected boolean shouldAskPermissions() {
        return true;
    }

    protected void askPermissions() {
        String[] permissions = {
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE"
        };
        int requestCode = 200;
        requestPermissions(permissions, requestCode);
    }


    @Override
    public void onClick(View view) {
        int i = view.getId();

        if (i == R.id.btn_choose_file) {
            showChoosingFile(0);
        } else if (i == R.id.btn_video_post) {
            //show video modal
            VideoUploadFragment dialog = new VideoUploadFragment(getApplicationContext(), LoginActivity.accessToken, this, true);
            //dialog.getView().setMinimumWidth(400);
            dialog.show(getSupportFragmentManager(), "video_upload_fragment");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shut down the executor service when the activity is destroyed
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow(); // Use shutdownNow to interrupt ongoing tasks
        }
        // No need to explicitly recycle the bitmap here anymore
        // if you are only creating it within the background task
    }

    //handles the display of image selection
    private void showChoosingFile(int type) {

        //ensure we have proper permissions for image upload
        if (shouldAskPermissions()) {
            askPermissions();
        }

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        if (type==0) {
            intent.setType("image/*");
            imagePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.select_img_title)));
        }else{
            intent.setType("video/*");
            videoPickerLauncher.launch(Intent.createChooser(intent, getString(R.string.select_img_title)));
        }

    }

    /**
     * Processes the selected image URI by loading a scaled bitmap off the main thread
     * and then initiating the upload.
     *
     * @param imageUri The content URI of the selected image.
     */
    private void processImageForUpload(Uri imageUri) {
        // Store the URI if needed elsewhere
        fileUri = imageUri;

        // Show a message or progress indicator on the UI thread
        runOnUiThread(() -> {
            // Example: Toast, update a TextView, show a progress bar
            Toast.makeText(PostSteemitActivity.this, "Processing image...", Toast.LENGTH_SHORT).show();
            // showProgressBar();
        });

        executorService.submit(() -> {
            Bitmap scaledBitmap = null;
            InputStream inputStream = null;
            try {
                // --- Step 1: Load scaled bitmap for preview/processing ---
                // Open input stream from the Uri using ContentResolver
                inputStream = getContentResolver().openInputStream(imageUri);
                if (inputStream == null) {
                    throw new FileNotFoundException("Could not open input stream for URI: " + imageUri);
                }

                // Decode bounds to get image dimensions without loading the full image
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(inputStream, null, options);

                // Close the stream after decoding bounds and open a new one for full decode
                try { inputStream.close(); } catch (IOException e) { Log.e(TAG, "Error closing stream after bounds decode", e); }
                inputStream = getContentResolver().openInputStream(imageUri);
                if (inputStream == null) {
                    throw new FileNotFoundException("Could not re-open input stream for URI: " + imageUri);
                }


                // Calculate inSampleSize to scale down the image
                // Example: Target max dimensions 1024x1024. Adjust as needed.
                final int MAX_DIMENSION = 1024;
                int photoW = options.outWidth;
                int photoH = options.outHeight;
                int scaleFactor = 1;
                if (photoW > MAX_DIMENSION || photoH > MAX_DIMENSION) {
                    scaleFactor = Math.min(photoW / MAX_DIMENSION, photoH / MAX_DIMENSION);
                }
                // Ensure scale factor is at least 1
                if (scaleFactor < 1) {
                    scaleFactor = 1;
                }
                options.inSampleSize = scaleFactor;

                // Decode the image with inSampleSize set
                options.inJustDecodeBounds = false; // Now load the actual bitmap
                scaledBitmap = BitmapFactory.decodeStream(inputStream, null, options);

                if (scaledBitmap == null) {
                    throw new IOException("Failed to decode bitmap from stream for URI: " + imageUri);
                }

                // --- Step 2: Initiate Upload ---
                // Pass the original Uri and potentially the scaled bitmap to your upload utility
                // Make sure Utils.uploadFile is thread-safe or correctly uses background threads itself.
                // Assuming Utils.uploadFile takes the original Uri for data streaming
                // and the scaled bitmap for related operations (like preview/thumbnail).
                Log.d(TAG, "Bitmap decoded (scaled). Dimensions: " + scaledBitmap.getWidth() + "x" + scaledBitmap.getHeight());

                // This call *must not* block the current background thread excessively.
                // If Utils.uploadFile does heavy work (like actual network upload),
                // it should handle its own threading internally.
                Utils.uploadFile( scaledBitmap, imageUri, steemitPostContent, getApplicationContext(),
                        PostSteemitActivity.this );

                // --- Step 3: Update UI or indicate completion ---
                runOnUiThread(() -> {
                    // Example: Hide progress bar, show success message
                    Toast.makeText(PostSteemitActivity.this, "Image processed and upload initiated.", Toast.LENGTH_SHORT).show();
                    // hideProgressBar();
                });

            } catch (FileNotFoundException e) {
                Log.e(TAG, "File not found processing URI: " + imageUri, e);
                handleProcessingError("Error: File not found.", e);
            } catch (IOException e) {
                Log.e(TAG, "IO Error processing URI: " + imageUri, e);
                handleProcessingError("Error reading image data.", e);
            } catch (OutOfMemoryError e) {
                // Catch OOM here specifically, although scaled loading significantly reduces risk
                Log.e(TAG, "OutOfMemoryError processing URI: " + imageUri, e);
                handleProcessingError("Error: Image too large or memory issue.", e);
            } catch (Exception e) {
                // Catch any other unexpected errors during processing
                Log.e(TAG, "Unexpected error processing URI: " + imageUri, e);
                handleProcessingError("An unexpected error occurred.", e);
            } finally {
                // Close the input stream in finally block
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing input stream", e);
                    }
                }
                // The scaledBitmap might be recycled by the caller (Utils.uploadFile)
                // or if not needed afterwards. If you store it as a member,
                // manage its lifecycle (recycle when done or before loading new).
                // For this example, we pass it to Utils.uploadFile and assume
                // Utils or subsequent logic handles its lifecycle.
                // if (scaledBitmap != null && !scaledBitmap.isRecycled()) {
                //     // If you don't need the scaledBitmap after passing it to Utils.uploadFile
                //     // you might recycle it here, *but* ensure Utils.uploadFile is done with it.
                //     // scaledBitmap.recycle();
                // }
            }
        });
    }

    /**
     * Helper to handle processing errors and update UI on the main thread.
     */
    private void handleProcessingError(final String userMessage, final Throwable error) {
        runOnUiThread(() -> {
            // Example: Show toast and hide progress bar
            Toast.makeText(PostSteemitActivity.this, userMessage, Toast.LENGTH_LONG).show();
            // hideProgressBar();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (bitmap != null) {
            bitmap.recycle();
        }

        if (requestCode == CHOOSING_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            fileUri = data.getData();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), fileUri);

                Utils.uploadFile( bitmap, fileUri, steemitPostContent, getApplicationContext(),
                        PostSteemitActivity.this );

            } catch (IOException e) {
                e.printStackTrace();
            }
        }else if (requestCode == CHOOSING_VID_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            //here capture video
            fileUri = data.getData();

            //uploadThumbnail(Utils.generateThumbnail(getApplicationContext(), fileUri));
            //uploadVideo();
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {



        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_steemit);


        /*Toolbar postToolbar = findViewById(R.id.post_toolbar);
        setSupportActionBar(postToolbar);*/

        //setting context
        this.steemit_post_context = this;

        //getting an instance of DB handler
        mStepsDBHelper = new StepsDBHelper(this);

        //grabbing instances of input data sources
        stepCountContainer = findViewById(R.id.steemit_step_count);

        //set initial steps display value
        int stepCount = mStepsDBHelper.fetchTodayStepCount();
        //display step count while ensuring we don't display negative value if no steps tracked yet
        stepCountContainer.setText(String.valueOf((Math.max(stepCount, 0))), TextView.BufferType.EDITABLE);


        //retrieving account data for simple reuse. Data is not stored anywhere outside actifit App.
        final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

        accountUsername = sharedPreferences.getString("actifitUser","");

        accountPostingKey = sharedPreferences.getString("actifitPst","");

        steemitPostTitle = findViewById(R.id.steemit_post_title);

        steemitPostContent = findViewById(R.id.steemit_post_text);

        measureSectionLabel = findViewById(R.id.measurements_section_lbl);

        nestedScrollView = findViewById(R.id.nestedScrollView);

        heightSizeUnit = findViewById(R.id.measurements_height_unit);
        weightSizeUnit = findViewById(R.id.measurements_weight_unit);
        waistSizeUnit = findViewById(R.id.measurements_waistsize_unit);
        chestSizeUnit = findViewById(R.id.measurements_chest_unit);
        thighsSizeUnit = findViewById(R.id.measurements_thighs_unit);

        //fill in point references
        titleCountRef = findViewById(R.id.titleCount);
        dateCountRef = findViewById(R.id.dateCount);
        activityCountRef = findViewById(R.id.activityCount);
        activityTypeCountRef = findViewById(R.id.activityTypeCount);
        tagsCountRef = findViewById(R.id.tagsCount);
        contentCountRef = findViewById(R.id.contentCount);
        charCount = findViewById(R.id.charCount);
        minCharCount = findViewById(R.id.minCharCount);
        charInfo = findViewById(R.id.charInfo);


        //final EditText steemitPostContentInner = findViewById(R.id.steemit_post_text);
        steemitPostTags = findViewById(R.id.steemit_post_tags);
        activityTypeSelector = findViewById(R.id.steemit_activity_type);

        //fullAFITPay = findViewById(R.id.full_afit_pay);

        heightSize = findViewById(R.id.measurements_height);
        weightSize = findViewById(R.id.measurements_weight);
        bodyFat = findViewById(R.id.measurements_bodyfat);
        chestSize = findViewById(R.id.measurements_chest);
        thighsSize = findViewById(R.id.measurements_thighs);
        waistSize = findViewById(R.id.measurements_waistsize);

        heightSizeUnit = findViewById(R.id.measurements_height_unit);
        weightSizeUnit = findViewById(R.id.measurements_weight_unit);
        waistSizeUnit = findViewById(R.id.measurements_waistsize_unit);
        chestSizeUnit = findViewById(R.id.measurements_chest_unit);
        thighsSizeUnit = findViewById(R.id.measurements_thighs_unit);

        mdView = findViewById(R.id.md_view);

        // call from code
        // MarkedView mdView = new MarkedView(this);

        //started here
        Button aiButton = findViewById(R.id.btn_ai_suggest);
        aiButton.setText("AI");
        aiButton.setOnClickListener(v -> showAiPopup());

        EditText contentField = findViewById(R.id.steemit_post_text);


        steemitPostTitle.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                String title = steemitPostTitle.getText().toString();
                if (title.trim().isEmpty()){
                    titleCountRef.setTextColor(getResources().getColor(R.color.actifitRed));
                }else{
                    titleCountRef.setTextColor(getResources().getColor(R.color.actifitDarkGreen));
                }
            }
        });

        //hook to event of adjusting tag content for the post
        steemitPostTags.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                String text = steemitPostTags.getText().toString();
                if (text.trim().isEmpty()){
                    tagsCountRef.setTextColor(getResources().getColor(R.color.actifitRed));
                }else{
                    tagsCountRef.setTextColor(getResources().getColor(R.color.actifitDarkGreen));
                }
            }
        });


        final Markwon markwon = Markwon.builder(steemit_post_context)
                //.usePlugin(ImagesPlugin.create())
                //support HTML
                //.usePlugin(HtmlPlugin.create())
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
                })//note: onCreate ends here

                //handle images via available picasso
                //.usePlugin(PicassoImagesPlugin.create(Picasso.get()))

                //handle images via picasso
                .usePlugin(PicassoImagesPlugin.create(new PicassoImagesPlugin.PicassoStore() {
                    @org.checkerframework.checker.nullness.qual.NonNull
                    @Override
                    public RequestCreator load(@org.checkerframework.checker.nullness.qual.NonNull AsyncDrawable drawable) {
                        try {
                            return Picasso.get()
                                    .load(drawable.getDestination())
                                    //.resize(desiredWidth, desiredHeight)
                                    //.centerCrop()
                                    .tag(drawable);
                        }catch(Exception e){
                            return null;
                        }
                    }

                    @Override
                    public void cancel(@NonNull AsyncDrawable drawable) {
                        Picasso.get()
                                .cancelTag(drawable);
                    }
                }))

                .build();

        //hook change event for report content preview and saving the text to prevent data loss
        steemitPostContent.addTextChangedListener(new TextWatcher() {

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
                    //mdView.setMDText(steemitPostContent.getText().toString());
                    try {
                        markwon.setMarkdown(mdView, steemitPostContent.getText().toString());
                    }catch(Exception e){
                        Log.e(MainActivity.TAG, "error ontextchanged markwon");
                    }

                    //store my current text
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("steemPostContent",
                            steemitPostContent.getText().toString());
                    editor.apply();

                }
                if(s.length() < min_char_count) {
                    contentCountRef.setTextColor(getResources().getColor(R.color.actifitRed));
                    charCount.setTextColor(getResources().getColor(R.color.actifitRed));
                }else{
                    contentCountRef.setTextColor(getResources().getColor(R.color.actifitDarkGreen));
                    charCount.setTextColor(getResources().getColor(R.color.actifitDarkGreen));
                }
                //show count
                charCount.setText(s.length()+"");


            }
        });


        try {
            String json = sharedPreferences.getString("selVidEntry", "");
            // Convert back to UploadedVideoModel
            Gson gson = new Gson();
            selVidEntry = gson.fromJson(json, UploadedVideoModel.class);
        }catch(Exception ex1){
            ex1.printStackTrace();
        }

        //try to load editor content if it was stored previously
        String priorContent = sharedPreferences.getString("steemPostContent","");
        if (!priorContent.trim().isEmpty()) {
            steemitPostContent.setText(priorContent);

        }else{
            //Generate a random integer between 1 and 6
            int minValue = 1;
            int maxValue = parseInt(getString(R.string.report_text_hint_count));
            int randomNumber = random.nextInt(maxValue - minValue + 1) + minValue;
            String resourceName = "report_text_hint_" + randomNumber;
            int resourceId = getResources().getIdentifier(resourceName, "string", getPackageName());

            steemitPostContent.setHint(getString(resourceId));

        }

        markwon.setMarkdown(mdView, steemitPostContent.getText().toString());
        // set markdown text pattern. ('contents' object is markdown text)
        //mdView.setMDText(steemitPostContent.getText().toString());

        //hooking to date change event for activity

        //need to check if user switched to fetch yesterday's data

        RadioGroup reportDateOptionGroup = findViewById(R.id.report_date_option_group);

        //check if the user is allowed to post yesterday's report
        Calendar myCalendar = Calendar.getInstance();

        myCalendar.add(Calendar.DATE, -1);

        //get yesterday's date
        String currentDate = new SimpleDateFormat("yyyyMMdd").format(
                myCalendar.getTime());
        //check last recorded post date
        String lastPostDate = sharedPreferences.getString("actifitLastPostDate","");

        if (!lastPostDate.isEmpty()){
            if (parseInt(lastPostDate) >= parseInt(currentDate)) {
                //need to disable yesterday's option
                RadioButton yesterdayOption = findViewById(R.id.report_yesterday_option);
                yesterdayOption.setEnabled(false);
                yesterdayReport = false;
                //ensure today is selected
                reportDateOptionGroup.check(R.id.report_today_option);
            }
        }


        //make sure to select proper radio button in case it was previously set
        if (yesterdayReport){
            reportDateOptionGroup.check(R.id.report_yesterday_option);
        }

        final TextView fitbitSyncNotice = findViewById(R.id.fitbit_sync_notice);

        //event listener for change in selection
        reportDateOptionGroup.setOnCheckedChangeListener((group, checkedId) -> {
            //common code for both cases

            //if user had synced Fitbit before, we need to notify that they need to sync again after change of date
            if (fitbitSyncDone > 0) {
                fitbitSyncNotice.setVisibility(View.VISIBLE);
                //reset that we fetched fitbit data
                fitbitSyncDone = 0;
            }

            if (checkedId == R.id.report_today_option) {//we have today's option
                //set initial steps display value
                int stepCount1 = mStepsDBHelper.fetchTodayStepCount();
                //display step count while ensuring we don't display negative value if no steps tracked yet
                stepCountContainer.setText(String.valueOf((Math.max(stepCount1, 0))), TextView.BufferType.EDITABLE);

                yesterdayReport = false;
            } else if (checkedId == R.id.report_yesterday_option) {
                int stepCount1;//yesterday's option
                //set initial steps display value
                stepCount1 = mStepsDBHelper.fetchYesterdayStepCount();
                //display step count while ensuring we don't display negative value if no steps tracked yet
                stepCountContainer.setText(String.valueOf((Math.max(stepCount1, 0))), TextView.BufferType.EDITABLE);

                yesterdayReport = true;
            }
        });

        // Initialize the ExecutorService
        executorService = Executors.newSingleThreadExecutor(); // Use a single thread for serial processing

        findViewById(R.id.btn_choose_file).setOnClickListener(this);

        findViewById(R.id.btn_video_post).setOnClickListener(this);

        // Launcher for picking images
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            // Process the selected image Uri off the main thread
                            processImageForUpload(uri);
                        } else {
                            Log.e(TAG, "Image picker returned null Uri");
                            // Optional: Show a message to the user
                            Toast.makeText(PostSteemitActivity.this, "Failed to get image URI", Toast.LENGTH_SHORT).show();
                        }
                    } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                        // User cancelled the operation
                        Log.d(TAG, "Image selection cancelled");
                        // Optional: Show a message or perform other action
                    } else {
                        // Handle other result codes if necessary
                        Log.e(TAG, "Image picker returned result code: " + result.getResultCode());
                        // Optional: Show an error message
                        Toast.makeText(PostSteemitActivity.this, "Image selection failed", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // Launcher for picking videos (assuming similar handling structure needed)
        videoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            // Handle the video Uri - this is where your original video logic goes
                            fileUri = uri; // Store video Uri if needed
                            // Assuming your video handling is separate from image bitmap loading
                            // Call your video processing/upload logic here, potentially also off the main thread
                            // uploadThumbnail(Utils.generateThumbnail(getApplicationContext(), fileUri)); // This might need background thread
                            // uploadVideo(); // This definitely needs background thread
                            Log.d(TAG, "Video selected: " + uri);
                            Toast.makeText(PostSteemitActivity.this, "Video Selected (Processing not implemented)", Toast.LENGTH_SHORT).show(); // Placeholder
                        } else {
                            Log.e(TAG, "Video picker returned null Uri");
                            Toast.makeText(PostSteemitActivity.this, "Failed to get video URI", Toast.LENGTH_SHORT).show();
                        }
                    } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                        Log.d(TAG, "Video selection cancelled");
                    } else {
                        Log.e(TAG, "Video picker returned result code: " + result.getResultCode());
                        Toast.makeText(PostSteemitActivity.this, "Video selection failed", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        //Adding default title content for the daily post

        //generating today's date
        Calendar mCalendar = Calendar.getInstance();
        String postTitle = getString(R.string.default_post_title);
        //set date in title accordingly
        if (yesterdayReport){
            mCalendar.add(Calendar.DATE, -1);
        }
        postTitle += " "+new SimpleDateFormat("MMMM d yyyy").format(mCalendar.getTime());

        //postTitle += String.valueOf(mCalendar.get(Calendar.MONTH)+1)+" " +
        //String.valueOf(mCalendar.get(Calendar.DAY_OF_MONTH))+"/"+String.valueOf(mCalendar.get(Calendar.YEAR));
        steemitPostTitle.setText(postTitle);

        //initializing activity options
        String[] activity_type = {
                getString(R.string.Walking), getString(R.string.Jogging), getString(R.string.Running), getString(R.string.Cycling),
                getString(R.string.RopeSkipping), getString(R.string.Dancing),getString(R.string.Basketball), getString(R.string.Football),
                getString(R.string.Boxing), getString(R.string.Tennis), getString(R.string.TableTennis),
                getString(R.string.MartialArts), getString(R.string.HouseChores), getString(R.string.MovingAroundOffice),
                getString(R.string.Shopping),getString(R.string.DailyActivity), getString(R.string.Aerobics),
                getString(R.string.WeightLifting), getString(R.string.Treadmill),getString(R.string.StairMill),
                getString(R.string.Elliptical), getString(R.string.Hiking), getString(R.string.Gardening),
                getString(R.string.Rollerblading), getString(R.string.Cricket), getString(R.string.Golf),
                getString(R.string.Volleyball), getString(R.string.Geocaching), getString(R.string.Shoveling),
                getString(R.string.Skiing), getString(R.string.Scootering), getString(R.string.Photowalking),
                getString(R.string.KettlebellTraining), getString(R.string.Bootcamp), getString(R.string.Gym),
                getString(R.string.Skating), getString(R.string.Hockey), getString(R.string.Swimming),
                getString(R.string.ChasingPokemons), getString(R.string.Badminton), getString(R.string.PickleBall),
                getString(R.string.Snowshoeing),getString(R.string.Sailing),getString(R.string.Kayaking), getString(R.string.Kidplay),
                getString(R.string.HomeImprovement), getString(R.string.YardWork), getString(R.string.StairClimbing),
                getString(R.string.Yoga), getString(R.string.Stretching),getString(R.string.Calisthenics),
                getString(R.string.StreetWorkout), getString(R.string.Plogging), getString(R.string.Crossfit),getString(R.string.FitnessGaming)
        };

        //sort options in alpha order
        Arrays.sort(activity_type);

        activityTypeSelector = findViewById(R.id.steemit_activity_type);
        activityTypeSelector.setHighlighterView(activityTypeCountRef);
        activityTypeSelector.setItems(activity_type);

        //grab current selection for measure system
        String activeSystem = sharedPreferences.getString("activeSystem",getString(R.string.metric_system_ntt));
        //adjust units accordingly
        if (activeSystem.equals(getString(R.string.metric_system_ntt))){
            weightSizeUnit.setText(getString(R.string.kg_unit));
            heightSizeUnit.setText(getString(R.string.cm_unit));
            waistSizeUnit.setText(getString(R.string.cm_unit));
            chestSizeUnit.setText(getString(R.string.cm_unit));
            thighsSizeUnit.setText(getString(R.string.cm_unit));
        }else{
            weightSizeUnit.setText(getString(R.string.lb_unit));
            heightSizeUnit.setText(getString(R.string.ft_unit));
            waistSizeUnit.setText(getString(R.string.in_unit));
            chestSizeUnit.setText(getString(R.string.in_unit));
            thighsSizeUnit.setText(getString(R.string.in_unit));
        }

        //popup display about min chat count requirement
        charInfo.setOnClickListener(view -> {

            AlertDialog.Builder dBuilder = new AlertDialog.Builder(steemit_post_context);
            String msg = getString(R.string.min_content_requirement);
            AlertDialog pointer = dBuilder.setMessage(Html.fromHtml(msg))
                    .setTitle(getString(R.string.content_requirement))
                    .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                    .setCancelable(true)
                    .setNegativeButton(getString(R.string.close_button), (dialog, id) -> dialog.dismiss()).create();

            dBuilder.show();
                /*pointer.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
                pointer.getWindow().getDecorView().setBackground(getDrawable(R.drawable.dialog_shape));
                pointer.show();*/
        });

        currentActivity = this;

        //capturing steemit post submission
        Button BtnSubmitSteemit = findViewById(R.id.post_to_steem_btn);
        BtnSubmitSteemit.setOnClickListener(arg0 -> ProcessPost());

        ScaleAnimation scaler = new ScaleAnimation(1f, 0.98f, 1f,0.98f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaler.setDuration(300);
        scaler.setRepeatMode(Animation.REVERSE);
        scaler.setRepeatCount(Animation.INFINITE);

        BtnSubmitSteemit.setAnimation(scaler);

        /* fixing scrollability of content within the post content section */

        steemitPostContent.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (v.getId() == R.id.steemit_post_text) {

                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    switch (event.getAction() & MotionEvent.ACTION_MASK) {
                        case MotionEvent.ACTION_UP:
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                            break;
                    }

                }
                return false;
            }
        });





        /***************** Fitbit Sync Implementation ****************/

        //capturing fitbit sync action
        Button BtnFitbitSync = findViewById(R.id.fitbit_sync);
        BtnFitbitSync.setOnClickListener(arg0 -> {
            // Connect to fitbit and grab data
            NxFitbitHelper.sendUserToAuthorisation(steemit_post_context, true);
        });

        //retrieve resulting data from fitbit sync (parameter from the Intent)
        Uri returnUrl = getIntent().getData();
        if (returnUrl != null) {
            try {
                NxFitbitHelper fitbit = new NxFitbitHelper(getApplicationContext(), true);
                fitbit.requestAccessTokenFromIntent(returnUrl);

                // Get user profile using helper function
                try {
                    JSONObject responseProfile = fitbit.getUserProfile();
                    //Log.d(MainActivity.TAG, "From JSON encodedId: " + responseProfile.getJSONObject("user"));
                    //Log.d(MainActivity.TAG, "From JSON fullName: " + responseProfile.getJSONObject("user").getString("fullName"));

                    //essential for capability to fetch measurements
                    responseProfile.getJSONObject("user");

                    //grab userId
                    fitbitUserId = fitbit.getUserId();

                    //check to see if settings allows fetching measurements - default true
                    String fetchMeasurements = sharedPreferences.getString("fitbitMeasurements", getString(R.string.fitbit_measurements_on_ntt));
                    if (fetchMeasurements.equals(getString(R.string.fitbit_measurements_on_ntt))) {

                        //grab and update user weight
                        TextView weight = findViewById(R.id.measurements_weight);
                        weight.setText(fitbit.getFieldFromProfile("weight"));

                        //grab and update user height
                        TextView height = findViewById(R.id.measurements_height);
                        height.setText(fitbit.getFieldFromProfile("height"));
                    }

                } catch (JSONException | InterruptedException | ExecutionException | IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    String soughtInfo = "steps";
                    String targetDate = "today";
                    //fetch yesterday data in case this is yesterday's option
                    if (yesterdayReport) {
                        targetDate = new SimpleDateFormat("yyyy-MM-dd").format(mCalendar.getTime());
                    }
                    JSONObject stepActivityList = fitbit.getActivityByDate(soughtInfo, targetDate);
                    JSONArray stepActivityArray = stepActivityList.getJSONArray("activities-tracker-" + soughtInfo);
                    Log.d(MainActivity.TAG, "From JSON distance:" + stepActivityArray.length());
                    int trackedActivityCount = 0;
                    if (stepActivityArray.length() > 0) {
                        Log.d(MainActivity.TAG, "we found matching records");
                        //loop through records adding up recorded steps
                        for (int i = 0; i < stepActivityArray.length(); i++) {
                            trackedActivityCount += parseInt(stepActivityArray.getJSONObject(i).getString("value"));
                        }

                        //update value according to activity we were able to grab
                        EditText activityCount = findViewById(R.id.steemit_step_count);
                        activityCount.setText("" + trackedActivityCount);

                        //flag that we synced properly
                        fitbitSyncDone = 1;

                        //store date of last sync to avoid improper use of older fitbit data
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("fitbitLastSyncDate",
                                new SimpleDateFormat("yyyyMMdd").format(
                                        mCalendar.getTime()));
                        editor.apply();
                    } else {
                        Log.d(MainActivity.TAG, "No auto-tracked activity found for today");
                    }

                } catch (JSONException | InterruptedException | ExecutionException | IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }catch(Exception myExc){
                myExc.printStackTrace();
                Toast.makeText(getApplicationContext(), getString(R.string.error_fitbit_fecth), Toast.LENGTH_SHORT).show();
            }

        } else {
            Log.d(MainActivity.TAG, "Something is wrong with the return value from Fitbit. getIntent().getData() is NULL?");
        }

        focusTitle();

    }

    @Override
    public void onResume() {
        super.onResume();
        focusTitle();

    }

    void focusTitle(){
        if (steemitPostTitle != null){
            steemitPostTitle.requestFocus();
        }
    }

    public void setMainVid(UploadedVideoModel vidEntry){
        this.selVidEntry = vidEntry;
        //store in shared preferences a copy of the video to ensure we have access to data if
        //user revisits this post later
        Gson gson = new Gson();
        String json = gson.toJson(vidEntry);

        SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("selVidEntry", json);
        editor.apply();
    }

    public void appendContent(String text){
        steemitPostContent.setText(steemitPostContent.getText().append(text));
    }

    void focusContent(){
        if (steemitPostContent != null && currentActivity != null){
            currentActivity.runOnUiThread(() -> {
                steemitPostContent.requestFocus();
                if (nestedScrollView != null) {
                    nestedScrollView.scrollTo(0, steemitPostContent.getBottom());
                }
            });
        }
    }

    /**
     * function handling the display of popup notification
     * @param notification
     * @param permLink
     */
    void displayNotification(final String notification, final ProgressDialog progress,
                             final Context context, final Activity currentActivity,
                             final String success, final String permLink){
        //render result
        currentActivity.runOnUiThread(() -> {
            //hide the progressDialog
            try{
                if (progress != null && progress.isShowing()) {
                    progress.dismiss();
                }
            }catch(Exception e){
                e.printStackTrace();
            }
            /*spinner=findViewById(R.id.progressBar);
            spinner.setVisibility(View.GONE);*/

            final AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
            builder1.setMessage(notification);

            if (success.equals("success")){
                builder1.setIcon(getResources().getDrawable(R.drawable.success_icon));
                builder1.setTitle("Actifit Success");
                builder1.setNeutralButton(getString(R.string.view_post_button),
                        (dialog, id) -> {

                            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

                            builder.setToolbarColor(getResources().getColor(R.color.actifitRed));

                            //animation for showing and closing screen
                            builder.setStartAnimations(steemit_post_context, R.anim.slide_in_right, R.anim.slide_out_left);

                            //animation for back button clicks
                            builder.setExitAnimations(steemit_post_context, android.R.anim.slide_in_left,
                                    android.R.anim.slide_out_right);

                            CustomTabsIntent customTabsIntent = builder.build();
                            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                            try {
                                customTabsIntent.launchUrl(steemit_post_context, Uri.parse(getString(R.string.actifit_url)+accountUsername+"/"+permLink));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (success.equals("success")) {
                                //close current screen
                                Log.d(MainActivity.TAG,">>>Finish");
                                currentActivity.finish();
                            }
                            //dialog.cancel();
                        });

                builder1.setNegativeButton(getString(R.string.share_post_button),
                        (dialog, id) -> {
                            //dialog.cancel();

                            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                            sharingIntent.setType("text/plain");
                            String shareSubject = getString(R.string.post_title);
                            String shareBody = getString(R.string.post_description);
                            shareBody += getString(R.string.post_title) + " "+getString(R.string.actifit_url)+accountUsername+"/"+permLink;

                            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);
                            sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody);

                            PostSteemitActivity.this.startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)));
                            if (success.equals("success")) {
                                //close current screen
                                Log.d(MainActivity.TAG,">>>Finish");
                                currentActivity.finish();
                            }
                        });
            }else{
                builder1.setIcon(getResources().getDrawable(R.drawable.error_icon));
                builder1.setTitle("Actifit Error");
            }

            builder1.setCancelable(true);

            builder1.setPositiveButton(
                    getString(R.string.dismiss_button),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            if (success.equals("success")) {
                                //close current screen
                                Log.d(MainActivity.TAG,">>>Finish");
                                currentActivity.finish();
                            }
                        }
                    });


            //create and display alert window
            try {
                AlertDialog alert11 = builder1.create();
                //alert11.show();
                builder1.show();
            }catch(Exception e){
                //Log.e(MainActivity.TAG, e.getMessage());
            }
        });

        //finish();
    }

    private class PostSteemitRequest extends AsyncTask<String, Void, Void> {
        ProgressDialog progress;
        private final Context context;
        private Activity currentActivity;
        public PostSteemitRequest(Context c, Activity currentActivity){
            this.context = c;
            this.currentActivity = currentActivity;
        }
        protected void onPreExecute(){
            //create a new progress dialog to show action is underway
            progress = new ProgressDialog(this.context);
            progress.setMessage(getString(R.string.sending_post));
            progress.show();
        }

        /*protected void onPostExecute(Void result){
            if (progress != null){
                if (progress.isShowing()){
                    progress.hide();
                }
            }
        }*/
        protected Void doInBackground(String... params) {
            try {
                Log.d(MainActivity.TAG,"click");

                //disable button to prevent multiple clicks
                //arg0.setEnabled(false);



                //storing account data for simple reuse. Data is not stored anywhere outside actifit App.
                final SharedPreferences[] sharedPreferences = {getSharedPreferences("actifitSets", MODE_PRIVATE)};
                final SharedPreferences.Editor[] editor = {sharedPreferences[0].edit()};
                //skip on spaces, upper case, and @ symbols to properly match steem username patterns
                editor[0].putString("actifitUser", accountUsername
                        .trim().toLowerCase().replace("@",""));
                editor[0].putString("actifitPst", accountPostingKey);
                editor[0].apply();


                //if (1==1) return null;

                //set proper target date
                Calendar mCalendar = Calendar.getInstance();

                if (yesterdayReport){
                    //go back one day
                    mCalendar.add(Calendar.DATE, -1);
                }

                String targetDate = new SimpleDateFormat("yyyyMMdd").format(
                        mCalendar.getTime());

                //this runs only on live mode
                if (getString(R.string.test_mode).equals("off")) {
                    //make sure we have reached the min movement amount
                    if (parseInt(accountActivityCount) < min_step_limit) {
                        notification = getString(R.string.min_activity_not_reached) + " " +
                                NumberFormat.getNumberInstance(Locale.US).format(min_step_limit) + " " + getString(R.string.not_yet);
                        displayNotification(notification, progress, context, currentActivity, "", "");

                        return null;
                    }

                    //make sure the post content has at least the min_char_count
                    if (finalPostContent.length()
                            <= min_char_count) {
                        notification = getString(R.string.min_char_count_error)
                                + " " + min_char_count
                                + " " + getString(R.string.characters_plural_label);
                        displayNotification(notification, progress, context, currentActivity, "", "");

                        focusContent();

                        return null;
                    }

                    //make sure the user has not posted today already,
                    //and also avoid potential abuse of changing phone clock via comparing to older dates
                    String lastPostDate = sharedPreferences[0].getString("actifitLastPostDate", "");

                    Log.d(MainActivity.TAG, ">>>>[Actifit]lastPostDate:" + lastPostDate);
                    Log.d(MainActivity.TAG, ">>>>[Actifit]currentDate:" + targetDate);



                    if (!lastPostDate.equals("")) {
                        if (parseInt(lastPostDate) >= parseInt(targetDate)) {
                            notification = getString(R.string.one_post_per_day_error);
                            displayNotification(notification, progress, context, currentActivity, "", "");
                            return null;
                        }
                    }


                    //let us check if user has selected activities yet
                    if (selectedActivityCount < 1) {
                        notification = getString(R.string.error_need_select_one_activity);
                        displayNotification(notification, progress, context, currentActivity, "", "");

                        //reset to enabled
                        //arg0.setEnabled(true);
                        return null;
                    }
                }

                //no need to send detailed step data if this is a fitbit sync
                String stepDataString = "";
                if (fitbitSyncDone == 0) {
                    //prepare relevant day detailed data
                    ArrayList<ActivitySlot> timeSlotActivity = mStepsDBHelper.fetchDateTimeSlotActivity(targetDate);



                    //loop through the data to prepare it for proper display
                    for (int position = 0; position < timeSlotActivity.size(); position++) {
                        try {
                            //grab date entry according to stored format
                            String slotTime = (timeSlotActivity.get(position)).slot;
                            String slotEntryFormat = slotTime;
                            if (slotTime.length() < 4) {
                                //no leading zero, add leading zero
                                slotEntryFormat = "0" + slotTime;
                            }

                            //append to display
                            stepDataString += slotEntryFormat + (timeSlotActivity.get(position)).activityCount + "|";

                        } catch (Exception ex) {
                            Log.d(MainActivity.TAG, ex.toString());
                            ex.printStackTrace();
                        }
                    }
                }

                //prepare data to be sent along post
                final JSONObject data = new JSONObject();
                try {
                    //skip on spaces, upper case, and @ symbols to properly match steem username patterns
                    data.put("author", accountUsername
                            .trim().toLowerCase().replace("@",""));
                    data.put("posting_key", accountPostingKey);
                    data.put("title", finalPostTitle);
                    data.put("content", finalPostContent);
                    data.put("tags", finalPostTags);
                    data.put("step_count", accountActivityCount);
                    data.put("activity_type", selectedActivitiesVal);

                    /*if (fullAFITPayVal) {
                        data.put("full_afit_pay", "on");
                    }*/

                    data.put("height", heightVal);
                    data.put("weight", weightVal);
                    data.put("chest", chestVal);
                    data.put("waist", waistVal);
                    data.put("thighs", thighsVal);
                    data.put("bodyfat", bodyFatVal);

                    data.put("heightUnit", heightUnit);
                    data.put("weightUnit", weightUnit);
                    data.put("chestUnit", chestUnit);
                    data.put("waistUnit", waistUnit);
                    data.put("thighsUnit", thighsUnit);

                    data.put("appType", "Android");

                    //append detailed activity data
                    data.put("detailedActivity", stepDataString);

                    //appending security param values
                    data.put( getString(R.string.sec_param), getString(R.string.sec_param_val));

                    //append user timezone
                    /*Date dt = new Date();
                    Calendar cal = Calendar.getInstance();

                    TimeZone tz = cal.getTimeZone();
                    tz.getRawOffset();*/
                    try {
                        TimeZone tz = TimeZone.getDefault();
                        Calendar cal = Calendar.getInstance(tz);
                        int offsetInMillis = tz.getOffset(cal.getTimeInMillis());

                        String offset = String.format("%02d:%02d", Math.abs(offsetInMillis / 3600000), Math.abs((offsetInMillis / 60000) % 60));
                        offset = "GMT" + (offsetInMillis >= 0 ? "+" : "-") + offset;

                        data.put("timeZone", offset);
                    }catch  (Exception e){
                        e.printStackTrace();
                    }

                    //grab app version number
                    try {
                        PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                        String version = pInfo.versionName;
                        data.put("appVersion",version);
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }


                    //if this is a yesterday post, make sure to include this data
                    if (yesterdayReport){
                        data.put("yesterdayReport", "1");
                    }

                    //also append the date used
                    data.put("activityDate", targetDate);

                    /**************************************/
                    //3speak vid section
                    // Assuming vid is an instance of some class or object that has the specified properties
                    if (selVidEntry != null) {
                        try {
                            // Create sourceMap JSONArray
                            JSONArray sourceMap = new JSONArray();

                            JSONObject thumbnailMap = new JSONObject();
                            thumbnailMap.put("type", "thumbnail");
                            thumbnailMap.put("url", selVidEntry.thumbnail);
                            sourceMap.put(thumbnailMap);

                            JSONObject videoMap = new JSONObject();
                            videoMap.put("type", "video");
                            videoMap.put("url", selVidEntry.video_v2);
                            videoMap.put("format", "m3u8");
                            sourceMap.put(videoMap);

                            // Create content JSONObject
                            JSONObject content = new JSONObject();
                            content.put("description", "");
                            JSONArray tags = new JSONArray();
                            tags.put("actifit");
                            tags.put("3speak");
                            content.put("tags", tags);

                            // Create info JSONObject
                            JSONObject info = new JSONObject();
                            info.put("platform", "3speak");
                            info.put("title", selVidEntry.title);
                            info.put("author", MainActivity.username);
                            info.put("permlink", selVidEntry.permlink);
                            info.put("duration", selVidEntry.duration);
                            info.put("filesize", selVidEntry.size);
                            info.put("file", selVidEntry.filename);
                            info.put("lang", "en");
                            info.put("firstUpload", false);
                            info.put("video_v2", selVidEntry.video_v2);
                            info.put("sourceMap", sourceMap);

                            // Create video JSONObject
                            JSONObject video = new JSONObject();
                            video.put("info", info);
                            video.put("content", content);

                            // Create vidJsonMeta JSONObject
                            JSONObject vidJsonMeta = new JSONObject();
                            vidJsonMeta.put("video",video);

                            //include as part of the json_metadata
                            data.put("video", vidJsonMeta.toString());

                            //add 3speak beneficiary data
                            JSONArray spkBenefic = Utils.grab3SpeakDefaultBenefic();
                            try {
                                JSONArray vidBenefic = new JSONArray(selVidEntry.beneficiaries);
                                for (int k = 0; k <vidBenefic.length();k++)
                                    spkBenefic.put(vidBenefic.getJSONObject(k));
                            }catch(Exception exc){
                                exc.printStackTrace();
                            }
                            data.put("spkBenefic", spkBenefic);

                            //also append vid permalink to ensure it gets recognized on 3speak side
                            data.put("spkPermlink",selVidEntry.permlink);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }

                    //also send out any additional beneficiaries
                    String extraBeneficList = sharedPreferences[0].getString("AdditionalBeneficiaries", "");
                    if (!extraBeneficList.isEmpty()) {
                        data.put("addBenefic", extraBeneficList);
                    }

                    Log.d("PostHive", extraBeneficList);

                    //if (true) return null;
                    /************************************/

                    //choose a charity if one is already selected before

                    sharedPreferences[0] = getSharedPreferences("actifitSets",MODE_PRIVATE);

                    final String currentCharity = (sharedPreferences[0].getString("selectedCharity",""));

                    if (!currentCharity.equals("")){
                        data.put("charity", currentCharity);
                    }

                    //append user ID
                    data.put("actifitUserID", sharedPreferences[0].getString("actifitUserID",""));

                    //append data tracking source to see if this is a device reading or a fitbit one
                    //if there was a Fitbit sync, also need to send out that this is Fitbit data
                    //if (1 == 1){
                    if (fitbitSyncDone == 1){
                        data.put("dataTrackingSource", getString(R.string.fitbit_tracking_ntt));
                        if (fitbitUserId == null || fitbitUserId.equals("")){
                            //missing permission to fitbit
                            notification = getString(R.string.fitbit_permissions_missing);
                            displayNotification(notification, progress, context, currentActivity, "", "");
                            return null;
                        }

                        //also append encrypted user identifier
                        MessageDigest md = MessageDigest.getInstance(getString(R.string.fitbit_user_enc));
                        byte[] digest = md.digest(fitbitUserId.getBytes());
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < digest.length; i++) {
                            sb.append(Integer.toString((digest[i] & 0xff) + 0x100, 16).substring(1));
                        }
                        System.out.println(sb);

                        data.put("fitbitUserId", sb.toString());
                    }else {
                        data.put("dataTrackingSource", sharedPreferences[0].getString("dataTrackingSystem",
                                getString(R.string.device_tracking_ntt)));
                    }
                    //append report STEEM payout type
                    data.put("reportSTEEMPayMode", sharedPreferences[0].getString("reportSTEEMPayMode",""));

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                //String inputLine;
                final String[] result = {""};
                //use test url only if testing mode is on
                String urlStr = getString(R.string.test_api_url);
                //if (getString(R.string.test_mode).equals("off")) {
                urlStr = getString(R.string.api_url_new);
                //}

                RequestQueue queue = Volley.newRequestQueue(getApplicationContext());

                JsonObjectRequest sendPostRequest = new JsonObjectRequest
                        (Request.Method.POST, urlStr, data, response -> {
                            //hide dialog
                            //progress.hide();
                            // Display the result
                            /*if (1==1) {
                                throw new RuntimeException("This is an error message");
                            }*/
                            try {
                                //check result of action
                                if (response.has("status") && response.getString("status").equals("success")) {
                                    notification = getString(R.string.success_post);

                                    //storing account data for simple reuse. Data is not stored anywhere outside actifit App.
                                    sharedPreferences[0] = getSharedPreferences("actifitSets", MODE_PRIVATE);
                                    editor[0] = sharedPreferences[0].edit();
                                    editor[0].putString("actifitLastPostDate", targetDate);
                                    //also clear editor text content
                                    editor[0].putString("steemPostContent", "");
                                    editor[0].apply();
                                    result[0] = response.getString("status");
                                } else {
                                    // notification = getString(R.string.failed_post);
                                    notification = response.getString("msg");//result;
                                }

                                //display proper notification
                                String permlink = (response.has("permlink"))?response.getString("permlink"):"";
                                displayNotification(notification, progress, context, currentActivity, result[0], permlink);

                            }catch (Exception e){

                                //display proper notification
                                notification = getString(R.string.failed_post);
                                displayNotification(notification, progress, context, currentActivity, "", "");

                                Log.d(MainActivity.TAG,"Error connecting");
                                e.printStackTrace();
                            }
                        }, error -> {
                            //hide dialog
                            //progress.hide();
                            //actifitBalance.setText(getString(R.string.unable_fetch_afit_balance));
                            //notification = getString(R.string.failed_post);
                            notification = error.getMessage();
                            displayNotification(notification, progress, context, currentActivity, "", "");
                            error.printStackTrace();
                        });

                //make sure sent only once
                sendPostRequest.setRetryPolicy(new DefaultRetryPolicy(
                        0,
                        DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                // Add balance request to be processed
                queue.add(sendPostRequest);

            }catch (Exception e){

                //display proper notification
                //            notification = getString(R.string.failed_post);
                //            displayNotification(notification, progress, context, currentActivity, "");
                try {
                    displayNotification(e.getMessage(), progress, context, currentActivity, "", "");
                }catch(Exception inner){
                    displayNotification("unknown error", progress, context, currentActivity, "", "");
                }
                Log.d(MainActivity.TAG,"Error connecting");
                e.printStackTrace();
            }
            return null;
        }
    }

    String currentCharityDisplayName="";

    private void ProcessPost(){

        //only if we haven't grabbed fitbit data, we need to grab new sensor data
        if (fitbitSyncDone == 0){
            int stepCount = 0;
            if (yesterdayReport){
                stepCount = mStepsDBHelper.fetchYesterdayStepCount();
            }else{
                stepCount = mStepsDBHelper.fetchTodayStepCount();
            }
            //display step count while ensuring we don't display negative value if no steps tracked yet
            stepCountContainer.setText(String.valueOf((stepCount<0?0:stepCount)), TextView.BufferType.EDITABLE);
        }else{
            //need to check if a day has passed, to prevent posting again using same fitbit data
            SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
            String lastSyncDate = sharedPreferences.getString("fitbitLastSyncDate","");

            //generating today's date
            Calendar mCalendar = Calendar.getInstance();
            //set date in title accordingly
            if (yesterdayReport){
                mCalendar.add(Calendar.DATE, -1);
            }

            String targetDate = new SimpleDateFormat("yyyyMMdd").format(
                    mCalendar.getTime());

            Log.d(MainActivity.TAG,">>>>[Actifit]lastPostDate:"+lastSyncDate);
            Log.d(MainActivity.TAG,">>>>[Actifit]currentDate:"+targetDate);
            if (!lastSyncDate.equals("")){
                if (parseInt(lastSyncDate) < parseInt(targetDate)) {
                    notification = getString(R.string.need_sync_fitbit_again);
                    ProgressDialog progress = new ProgressDialog(steemit_post_context);
                    progress.setMessage(notification);
                    progress.show();
                    displayNotification(notification, progress, steemit_post_context, currentActivity, "", "");
                    return;
                }
            }

            EditText activityCount = findViewById(R.id.steemit_step_count);
            int trackedActivityCount = parseInt(activityCount.getText().toString());

            //store the returned activity count to the DB
            mStepsDBHelper.manualInsertStepsEntry(trackedActivityCount);

        }



        //we need to check first if we have a charity setup
        SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

        final String currentCharity = sharedPreferences.getString("selectedCharity","");
        currentCharityDisplayName = sharedPreferences.getString("selectedCharityDisplayName","");

        //accountUsername = steemitUsername.getText().toString();
        //accountPostingKey = steemitPostingKey.getText().toString();
        accountActivityCount = stepCountContainer.getText().toString();
        finalPostTitle = steemitPostTitle.getText().toString();
        selectedActivityCount = activityTypeSelector.getSelectedIndicies().size();

        finalPostContent = steemitPostContent.getText().toString();

        //check if we have a video and the video still is part of the content
        if (selVidEntry != null){
            String soughtString = "https://3speak.tv/watch?v="+MainActivity.username+"/"+selVidEntry.permlink;
            if (!finalPostContent.contains(soughtString)){
                //video has been removed, set as null
                selVidEntry = null;
            }
        }

        finalPostTags = steemitPostTags.getText().toString();

        selectedActivitiesVal = activityTypeSelector.getSelectedItemsAsString();

        //fullAFITPayVal = fullAFITPay.isChecked();

        heightVal = heightSize.getText().toString();
        weightVal = weightSize.getText().toString();
        chestVal = chestSize.getText().toString();
        waistVal = waistSize.getText().toString();
        thighsVal = thighsSize.getText().toString();
        bodyFatVal = bodyFat.getText().toString();

        heightUnit = heightSizeUnit.getText().toString();
        weightUnit = weightSizeUnit.getText().toString();
        chestUnit = chestSizeUnit.getText().toString();
        waistUnit = waistSizeUnit.getText().toString();
        thighsUnit = thighsSizeUnit.getText().toString();

        //make sure user has proper balance to earn AFIT rewards, and notify them accordingly


        processPostFinal(currentCharity);
    }

    private void processPostFinal(String currentCharity){
        if (!currentCharity.isEmpty()){
            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case DialogInterface.BUTTON_POSITIVE:
                            //go ahead posting
                            processPostMinRewards();
                            break;

                        case DialogInterface.BUTTON_NEGATIVE:
                            //cancel
                            break;
                    }
                }
            };

            AlertDialog.Builder builder = new AlertDialog.Builder(steemit_post_context);
            builder.setMessage(getString(R.string.current_workout_going_charity) + " "
                            + currentCharityDisplayName + " "
                            + getString(R.string.current_workout_settings_based))
                    .setPositiveButton(getString(R.string.yes_button), dialogClickListener)
                    .setNegativeButton(getString(R.string.no_button), dialogClickListener).show();
        }else {
            //connect to the server via a thread to prevent application hangup
            processPostMinRewards();
        }
    }

    //function handles confirming less than min rewards requirement
    private void processPostMinRewards(){
        //this runs only on live mode
        if (getString(R.string.test_mode).equals("off")) {
            //make sure we have reached the min movement amount
            if (parseInt(accountActivityCount) < min_reward_limit) {

                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE:
                                //go ahead posting
                                new PostSteemitRequest(steemit_post_context, currentActivity).execute();
                                break;

                            case DialogInterface.BUTTON_NEGATIVE:
                                //cancel
                                break;
                        }
                    }
                };
                String notfMsg = getString(R.string.min_activity_not_reached) + " " +
                        NumberFormat.getNumberInstance(Locale.US).format(min_reward_limit) + " " + getString(R.string.not_yet)
                        + " " + getString(R.string.for_rewards)
                        + " " + getString(R.string.confirm_proceed);
                AlertDialog.Builder builder = new AlertDialog.Builder(steemit_post_context);
                builder.setMessage(notfMsg)
                        .setPositiveButton(getString(R.string.yes_button), dialogClickListener)
                        .setNegativeButton(getString(R.string.no_button), dialogClickListener).show();
            }else {
                //connect to the server via a thread to prevent application hangup
                new PostSteemitRequest(steemit_post_context, currentActivity).execute();
            }
        }else {
            //connect to the server via a thread to prevent application hangup
            new PostSteemitRequest(steemit_post_context, currentActivity).execute();
        }
    }


    //// This is the main content box on your main screen (e.g. steemitPostContent)

    private void showAiPopup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.ai_popup, null);
        builder.setView(dialogView);

        // Find popup views
        EditText aiInputText = dialogView.findViewById(R.id.ai_input_text);
        Spinner aiActionSpinner = dialogView.findViewById(R.id.ai_action_spinner);
        Button btnQuery = dialogView.findViewById(R.id.btn_query);
        Button btnClear = dialogView.findViewById(R.id.btn_clear);
        TextView aiPreviewText = dialogView.findViewById(R.id.ai_preview_text);
        Button btnAccept = dialogView.findViewById(R.id.btn_accept);
        btnAccept.setEnabled(false);
        btnAccept.setAlpha(0.5f);     // this creates a faded look
        btnAccept.setEnabled(false);  // can't click unless response is created


        EditText steemitPostContent = findViewById(R.id.steemit_post_text);
        TextView mdView = findViewById(R.id.md_view);

        AlertDialog dialog = builder.create();
        dialog.show();


        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"None", "Summarize", "Expand"}
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        aiActionSpinner.setAdapter(adapter);

        btnQuery.setOnClickListener(v -> {
            String userText = aiInputText.getText().toString().trim();
            String action = aiActionSpinner.getSelectedItem().toString();

            if (userText.isEmpty()) {
                aiPreviewText.setText("Please enter some text first.");
                return;
            }

            String prompt;
            switch (action.toLowerCase()) {
                case "summarize":
                    prompt = "Please summarize this content:\n" + userText;
                    break;
                case "expand":
                    prompt = "Please expand this content:\n" + userText;
                    break;
                default:
                    prompt = userText;
                    break;
            }

            // this shows loading message in preview
            aiPreviewText.setText("[AI is thinking…]");

            // Send prompt to AI
            AiService aiService = new AiService();
            aiService.generateFromFreePrompt(prompt, new AiService.ResponseCallback() {
                @Override
                public void onSuccess(AiResponse response) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        String aiReply = response.getRawText();
                        aiPreviewText.setText(aiReply); //Only show inside popup for now,wait for accept btn

                        if (!aiReply.isEmpty()){
                            btnAccept.setEnabled(true);
                            btnAccept.setAlpha(1f);//this changes color to bright red when ai response is created
                            btnAccept.setEnabled(true);

                        }
                    });
                }

                @Override
                public void onFailure(String errorMessage) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            aiPreviewText.setText("[AI error] " + errorMessage));
                }
            });
        });

        btnAccept.setOnClickListener(v -> {
            String acceptedText = aiPreviewText.getText().toString().trim();
            if (!acceptedText.isEmpty() && !acceptedText.equals("AI response will appear here...") && !acceptedText.equals("[AI is thinking…]")) {
                steemitPostContent.setText(acceptedText);  //this updates the main screen
                mdView.setText(acceptedText);              //this updates the preview if used
                dialog.dismiss();                          //this closes the popup
            } else {
                Toast.makeText(this, "No valid AI response to accept.", Toast.LENGTH_SHORT).show();
            }
        });
        btnClear.setOnClickListener(v -> aiInputText.setText(""));
    }



}

