package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;

import android.provider.MediaStore;
import android.support.v4.widget.CircularProgressDrawable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.mobileconnectors.s3.transferutility.*;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.IOUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import com.mittsu.markedview.MarkedView;


public class PostSteemitActivity extends AppCompatActivity implements View.OnClickListener{

    private StepsDBHelper mStepsDBHelper;
    private String notification = "";
    private int min_step_limit = 1000;
    private int min_char_count = 100;
    private Context steemit_post_context;

    //track Choosing Image Intent
    private static final int CHOOSING_IMAGE_REQUEST = 1234;

    private EditText steemitPostContent;

    private Uri fileUri;
    private Bitmap bitmap;
    private ImageView image_preview;

    //tracks whether user synched his Fitbit data to avoid refetching activity count from current device
    private int fitbitSyncDone = 0;

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


    //implementing file upload functionality
    private void uploadFile() {
        final ProgressDialog uploadProgress;
        if (fileUri != null) {

            //create unique image file name
            final String fileName = UUID.randomUUID().toString();

            final File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "/" + fileName);

            createFile(getApplicationContext(), fileUri, file);

            TransferUtility transferUtility =
                    TransferUtility.builder()
                            .context(getApplicationContext())
                            .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                            .s3Client(new AmazonS3Client(AWSMobileClient.getInstance().getCredentialsProvider()))
                            .build();

            //specify content type to be image to be properly recognizable upon rendering
            ObjectMetadata imgMetaData = new ObjectMetadata();
            imgMetaData.setContentType("image/jpeg");

            TransferObserver uploadObserver =
                    transferUtility.upload(fileName, file, imgMetaData);

            //create a new progress dialog to show action is underway
            uploadProgress = new ProgressDialog(steemit_post_context);
            uploadProgress.setMessage("Uploading...0%");
            uploadProgress.show();

            uploadObserver.setTransferListener(new TransferListener() {

                @Override
                public void onStateChanged(int id, TransferState state) {
                    if (TransferState.COMPLETED == state) {
                        try {
                            if (uploadProgress != null && uploadProgress.isShowing()) {
                                uploadProgress.dismiss();
                            }
                        }catch (Exception ex){
                            Log.d(MainActivity.TAG,ex.getMessage());
                        }

                        Toast.makeText(getApplicationContext(), "Upload Completed!", Toast.LENGTH_SHORT).show();

                        String full_img_url = getString(R.string.actifit_usermedia_url)+fileName;
                        String img_markdown_text = "![]("+full_img_url+")";

                        //append the uploaded image url to the text as markdown
                        //if there is any particular selection, replace it too

                        int start = Math.max(steemitPostContent.getSelectionStart(), 0);
                        int end = Math.max(steemitPostContent.getSelectionEnd(), 0);
                        steemitPostContent.getText().replace(Math.min(start, end), Math.max(start, end),
                                img_markdown_text, 0, img_markdown_text.length());

                        file.delete();

                    } else if (TransferState.FAILED == state) {
                        Toast toast = Toast.makeText(getApplicationContext(), "Upload Failed!", Toast.LENGTH_SHORT);
                        TextView v = toast.getView().findViewById(android.R.id.message);
                        v.setTextColor(Color.RED);
                        toast.show();
                        file.delete();
                    }
                }

                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                    int percentDone = (int) percentDonef;
                    uploadProgress.setMessage("Uploading..."+percentDone + "%");
                    //tvFileName.setText("ID:" + id + "|bytesCurrent: " + bytesCurrent + "|bytesTotal: " + bytesTotal + "|" + percentDone + "%");
                }

                @Override
                public void onError(int id, Exception ex) {
                    ex.printStackTrace();
                }

            });
        }
    }

    @Override
    public void onClick(View view) {
        int i = view.getId();

        if (i == R.id.btn_choose_file) {
            showChoosingFile();
        } /*else if (i == R.id.btn_upload) {
            uploadFile();
        }*/
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
        startActivityForResult(Intent.createChooser(intent, "Select Image"), CHOOSING_IMAGE_REQUEST);
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

                uploadFile();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //handle displaying a preview of selected image
    private void setPic(String mCurrentPhotoPath) {
        // Get the dimensions of the View
        int targetW = image_preview.getWidth();
        int targetH = image_preview.getHeight();

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW/targetW, photoH/targetH);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

        Bitmap bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
        image_preview.setImageBitmap(bitmap);
    }

    private void createFile(Context context, Uri srcUri, File dstFile) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(srcUri);
            if (inputStream == null) return;
            OutputStream outputStream = new FileOutputStream(dstFile);
            IOUtils.copy(inputStream, outputStream);
            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_steemit);

        //make sure help with PPKey link click works
        TextView ppHelpLink = findViewById(R.id.posting_key_link);
        ppHelpLink.setMovementMethod(LinkMovementMethod.getInstance());

        //setting context
        this.steemit_post_context = this;

        //getting an instance of DB handler
        mStepsDBHelper = new StepsDBHelper(this);

        //grabbing instances of input data sources
        final EditText stepCountContainer = findViewById(R.id.steemit_step_count);

        //set initial steps display value
        int stepCount = mStepsDBHelper.fetchTodayStepCount();
        //display step count while ensuring we don't display negative value if no steps tracked yet
        stepCountContainer.setText(String.valueOf((stepCount<0?0:stepCount)), TextView.BufferType.EDITABLE);


        EditText steemitPostTitle = findViewById(R.id.steemit_post_title);
        EditText steemitUsername = findViewById(R.id.steemit_username);
        EditText steemitPostingKey = findViewById(R.id.steemit_posting_key);
        steemitPostContent = findViewById(R.id.steemit_post_text);
        TextView measureSectionLabel = findViewById(R.id.measurements_section_lbl);

        TextView heightSizeUnit = findViewById(R.id.measurements_height_unit);
        TextView weightSizeUnit = findViewById(R.id.measurements_weight_unit);
        TextView waistSizeUnit = findViewById(R.id.measurements_waistsize_unit);
        TextView chestSizeUnit = findViewById(R.id.measurements_chest_unit);
        TextView thighsSizeUnit = findViewById(R.id.measurements_thighs_unit);

        final MarkedView mdView = (MarkedView)findViewById(R.id.md_view);
        // call from code
        // MarkedView mdView = new MarkedView(this);

        // set markdown text pattern. ('contents' object is markdown text)
        mdView.setMDText(steemitPostContent.getText().toString());


        //hook change event for report content preview
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
                if(s.length() != 0)
                    mdView.setMDText(steemitPostContent.getText().toString());
            }
        });

        //image_preview = findViewById(R.id.image_preview);

        //initialize AWS settings and configuration

        findViewById(R.id.btn_choose_file).setOnClickListener(this);

        AWSMobileClient.getInstance().initialize(this).execute();

        //Adding default title content for the daily post

        //generating today's date
        Calendar mCalendar = Calendar.getInstance();
        String postTitle = getString(R.string.default_post_title);
        postTitle += " "+new SimpleDateFormat("MMMM d yyyy").format(mCalendar.getTime());

        //postTitle += String.valueOf(mCalendar.get(Calendar.MONTH)+1)+" " +
                //String.valueOf(mCalendar.get(Calendar.DAY_OF_MONTH))+"/"+String.valueOf(mCalendar.get(Calendar.YEAR));
        steemitPostTitle.setText(postTitle);

        //initializing activity options
        String[] activity_type = {
                "Walking", "Jogging", "Running", "Cycling", "Rope Skipping",
                "Dancing","Basketball", "Football", "Boxing", "Tennis", "Table Tennis",
                "Martial Arts", "House Chores", "Moving Around Office", "Shopping","Daily Activity",
                "Aerobics", "Weight Lifting", "Treadmill","Stair Mill", "Elliptical",
                "Hiking", "Gardening", "Rollerblading", "Cricket", "Golf", "Volleyball", "Geocaching"
                };

        //sort options in alpha order
        Arrays.sort(activity_type);

        MultiSelectionSpinner activityTypeSelector = findViewById(R.id.steemit_activity_type);
        activityTypeSelector.setItems(activity_type);

        //retrieving account data for simple reuse. Data is not stored anywhere outside actifit App.
        SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

        steemitUsername.setText(sharedPreferences.getString("actifitUser",""));
        steemitPostingKey.setText(sharedPreferences.getString("actifitPst",""));

        //grab current selection for measure system
        String activeSystem = sharedPreferences.getString("activeSystem",getString(R.string.metric_system));
        //adjust units accordingly
        if (activeSystem.equals(getString(R.string.metric_system))){
            weightSizeUnit.setText("kg");
            heightSizeUnit.setText("cm");
            waistSizeUnit.setText("cm");
            chestSizeUnit.setText("cm");
            thighsSizeUnit.setText("cm");
        }else{
            weightSizeUnit.setText("lb");
            heightSizeUnit.setText("ft");
            waistSizeUnit.setText("in");
            chestSizeUnit.setText("in");
            thighsSizeUnit.setText("in");
        }

        final Activity currentActivity = this;

        //capturing steemit post submission
        Button BtnSubmitSteemit = findViewById(R.id.btn_submit_steemit);
        BtnSubmitSteemit.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(final View arg0) {


                //only if we haven't grabbed fitbit data, we need to grab new sensor data
                if (fitbitSyncDone == 0){
                    //ned to grab new updated activity count before posting
                    int stepCount = mStepsDBHelper.fetchTodayStepCount();

                    //display step count while ensuring we don't display negative value if no steps tracked yet
                    stepCountContainer.setText(String.valueOf((stepCount<0?0:stepCount)), TextView.BufferType.EDITABLE);
                }else{
                    //need to check if a day has passed, to prevent posting again using same fitbit data
                    SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
                    String lastSyncDate = sharedPreferences.getString("fitbitLastSyncDate","");
                    String currentDate = new SimpleDateFormat("yyyyMMdd").format(
                            Calendar.getInstance().getTime());

                    Log.d(MainActivity.TAG,">>>>[Actifit]lastPostDate:"+lastSyncDate);
                    Log.d(MainActivity.TAG,">>>>[Actifit]currentDate:"+currentDate);
                    if (!lastSyncDate.equals("")){
                        if (Integer.parseInt(lastSyncDate) < Integer.parseInt(currentDate)) {
                            notification = getString(R.string.need_sync_fitbit_again);
                            ProgressDialog progress = new ProgressDialog(steemit_post_context);
                            progress.setMessage(notification);
                            progress.show();
                            displayNotification(notification, progress, steemit_post_context, currentActivity, "");
                            return;
                        }
                    }

                }


                //we need to check first if we have a charity setup
                SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

                final String currentCharity = (sharedPreferences.getString("selectedCharity",""));
                final String currentCharityDisplayName = (sharedPreferences.getString("selectedCharityDisplayName",""));

                if (!currentCharity.equals("")){
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

                        AlertDialog.Builder builder = new AlertDialog.Builder(steemit_post_context);
                        builder.setMessage(getString(R.string.current_workout_going_charity) + " "
                                + currentCharityDisplayName + " "
                                + getString(R.string.current_workout_settings_based))
                                .setPositiveButton("Yes", dialogClickListener)
                                .setNegativeButton("No", dialogClickListener).show();
                }else {
                    //connect to the server via a thread to prevent application hangup
                    new PostSteemitRequest(steemit_post_context, currentActivity).execute();
                }
            }
        });

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
        BtnFitbitSync.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(final View arg0) {
                // Connect to fitbit and grab data
                NxFitbitHelper.sendUserToAuthorisation(steemit_post_context);
            }

        });

        //retrieve resulting data from fitbit sync (parameter from the Intent)
        Uri returnUrl = getIntent().getData();
        if (returnUrl != null) {
            NxFitbitHelper fitbit = new NxFitbitHelper(getApplicationContext());
            fitbit.requestAccessTokenFromIntent(returnUrl);

            sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);

            // Get user profile using helper function
            try {
                JSONObject responseProfile = fitbit.getUserProfile();
                Log.d(MainActivity.TAG, "From JSON encodedId: " + responseProfile.getJSONObject("user").getString("encodedId"));
                Log.d(MainActivity.TAG, "From JSON fullName: " + responseProfile.getJSONObject("user").getString("fullName"));

                //check to see if settings allows fetching measurements - default true
                String fetchMeasurements = sharedPreferences.getString("fitbitMeasurements",getString(R.string.fitbit_measurements_on));
                if (fetchMeasurements.equals(getString(R.string.fitbit_measurements_on))) {
                    //grab and update user weight
                    TextView weight = findViewById(R.id.measurements_weight);
                    weight.setText(fitbit.getFieldFromProfile("weight"));

                    //grab and update user height
                    TextView height = findViewById(R.id.measurements_height);
                    height.setText(fitbit.getFieldFromProfile("height"));
                }

            } catch (JSONException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e){
                e.printStackTrace();
            }

            try {
                String soughtInfo = "steps";
                JSONObject stepActivityList = fitbit.getTodayActivity(soughtInfo);
                JSONArray stepActivityArray = stepActivityList.getJSONArray("activities-tracker-"+soughtInfo);
                Log.d(MainActivity.TAG, "From JSON distance:" + stepActivityArray.length() );
                int trackedActivityCount = 0;
                if (stepActivityArray.length()>0){
                    Log.d(MainActivity.TAG, "we found matching records");
                    //loop through records adding up recorded steps
                    for (int i=0;i<stepActivityArray.length();i++){
                        trackedActivityCount += Integer.parseInt(stepActivityArray.getJSONObject(i).getString("value"));
                    }

                    //update value according to activity we were able to grab
                    EditText activityCount = findViewById(R.id.steemit_step_count);
                    activityCount.setText("" + trackedActivityCount);

                    //flag that we synced properly
                    fitbitSyncDone = 1;

                    //store the returned activity count to the DB
                    mStepsDBHelper.manualInsertStepsEntry(trackedActivityCount);

                    //store date of last sync to avoid improper use of older fitbit data
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("fitbitLastSyncDate",
                            new SimpleDateFormat("yyyyMMdd").format(
                                    Calendar.getInstance().getTime()));
                    editor.commit();
                }else{
                    Log.d(MainActivity.TAG, "No auto-tracked activity found for today" );
                }

            } catch (JSONException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e){
                e.printStackTrace();
            }

        } else {
            Log.d(MainActivity.TAG, "Something is wrong with the return value from Fitbit. getIntent().getData() is NULL?");
        }

    }

    /**
     * function handling the display of popup notification
     * @param notification
     */
    void displayNotification(final String notification, final ProgressDialog progress,
                             final Context context, final Activity currentActivity,
                             final String success){
        //render result
        currentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
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

                AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
                builder1.setMessage(notification);

                builder1.setCancelable(true);

                builder1.setPositiveButton(
                        "Dismiss",
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
                AlertDialog alert11 = builder1.create();
                alert11.show();
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
        protected Void doInBackground(String... params) {
            try {
                Log.d(MainActivity.TAG,"click");

                //disable button to prevent multiple clicks
                //arg0.setEnabled(false);

                EditText steemitPostTitle = findViewById(R.id.steemit_post_title);
                EditText steemitUsername = findViewById(R.id.steemit_username);
                EditText steemitPostingKey = findViewById(R.id.steemit_posting_key);
                EditText steemitPostContent = findViewById(R.id.steemit_post_text);
                EditText steemitPostTags = findViewById(R.id.steemit_post_tags);
                EditText steemitStepCount = findViewById(R.id.steemit_step_count);
                MultiSelectionSpinner activityTypeSelector = findViewById(R.id.steemit_activity_type);

                CheckBox fullAFITPay = findViewById(R.id.full_afit_pay);

                EditText heightSize = findViewById(R.id.measurements_height);
                EditText weightSize = findViewById(R.id.measurements_weight);
                EditText bodyFat = findViewById(R.id.measurements_bodyfat);
                EditText chestSize = findViewById(R.id.measurements_chest);
                EditText thighsSize = findViewById(R.id.measurements_thighs);
                EditText waistSize = findViewById(R.id.measurements_waistsize);

                TextView heightSizeUnit = findViewById(R.id.measurements_height_unit);
                TextView weightSizeUnit = findViewById(R.id.measurements_weight_unit);
                TextView waistSizeUnit = findViewById(R.id.measurements_waistsize_unit);
                TextView chestSizeUnit = findViewById(R.id.measurements_chest_unit);
                TextView thighsSizeUnit = findViewById(R.id.measurements_thighs_unit);


                //storing account data for simple reuse. Data is not stored anywhere outside actifit App.
                SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                //skip on spaces, upper case, and @ symbols to properly match steem username patterns
                editor.putString("actifitUser", steemitUsername.getText().toString()
                        .trim().toLowerCase().replace("@",""));
                editor.putString("actifitPst", steemitPostingKey.getText().toString());
                editor.commit();

                //this runs only on live mode
                if (getString(R.string.test_mode).equals("off")){
                    //make sure we have reached the min movement amount
                    if (Integer.parseInt(steemitStepCount.getText().toString()) < min_step_limit) {
                        notification = "You have not reached the minimum " +
                                NumberFormat.getNumberInstance(Locale.US).format(min_step_limit) + " activity yet";
                        displayNotification(notification, progress, context, currentActivity, "");

                        return null;
                    }

                    //make sure the post content has at least the min_char_count
                    if (steemitPostContent.getText().toString().length()
                            <= min_char_count){
                        notification = getString(R.string.min_char_count_error)
                                +" "+ min_char_count
                                +" "+ getString(R.string.characters_plural_label);
                        displayNotification(notification, progress, context, currentActivity, "");

                        return null;
                    }

                    //make sure the user has not posted today already,
                    //and also avoid potential abuse of changing phone clock via comparing to older dates
                    String lastPostDate = sharedPreferences.getString("actifitLastPostDate","");
                    String currentDate = new SimpleDateFormat("yyyyMMdd").format(
                            Calendar.getInstance().getTime());

                    Log.d(MainActivity.TAG,">>>>[Actifit]lastPostDate:"+lastPostDate);
                    Log.d(MainActivity.TAG,">>>>[Actifit]currentDate:"+currentDate);
                    if (!lastPostDate.equals("")){
                        if (Integer.parseInt(lastPostDate) >= Integer.parseInt(currentDate)) {
                            notification = getString(R.string.one_post_per_day_error);
                            displayNotification(notification, progress, context, currentActivity, "");
                            return null;
                        }
                    }

                }

                //let us check if user has selected activities yet
                if (activityTypeSelector.getSelectedIndicies().size()<1){
                    notification = getString(R.string.error_need_select_one_activity);
                    displayNotification(notification, progress, context, currentActivity, "");

                    //reset to enabled
                    //arg0.setEnabled(true);
                    return null;
                }




                //prepare data to be sent along post
                final JSONObject data = new JSONObject();
                try {
                    //skip on spaces, upper case, and @ symbols to properly match steem username patterns
                    data.put("author", steemitUsername.getText().toString()
                            .trim().toLowerCase().replace("@",""));
                    data.put("posting_key", steemitPostingKey.getText());
                    data.put("title", steemitPostTitle.getText());
                    data.put("content", steemitPostContent.getText());
                    data.put("tags", steemitPostTags.getText());
                    data.put("step_count", steemitStepCount.getText());
                    data.put("activity_type", activityTypeSelector.getSelectedItemsAsString());

                    if (fullAFITPay.isChecked()) {
                        data.put("full_afit_pay", "on");
                    }

                    data.put("height", heightSize.getText());
                    data.put("weight", weightSize.getText());
                    data.put("chest", chestSize.getText());
                    data.put("waist", waistSize.getText());
                    data.put("thighs", thighsSize.getText());
                    data.put("bodyfat", bodyFat.getText());

                    data.put("heightUnit", heightSizeUnit.getText());
                    data.put("weightUnit", weightSizeUnit.getText());
                    data.put("chestUnit", chestSizeUnit.getText());
                    data.put("waistUnit", waistSizeUnit.getText());
                    data.put("thighsUnit", thighsSizeUnit.getText());

                    data.put("appType", "Android");


                    //appending security param values
                    data.put( getString(R.string.sec_param), getString(R.string.sec_param_val));

                    //grab app version number
                    try {
                        PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                        String version = pInfo.versionName;
                        data.put("appVersion",version);
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }



                    //choose a charity if one is already selected before

                    sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

                    final String currentCharity = (sharedPreferences.getString("selectedCharity",""));

                    if (!currentCharity.equals("")){
                        data.put("charity", currentCharity);
                    }

                    //append user ID
                    data.put("actifitUserID", sharedPreferences.getString("actifitUserID",""));

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                String inputLine;
                String result = "";
                //use test url only if testing mode is on
                String urlStr = getString(R.string.test_api_url);
                if (getString(R.string.test_mode).equals("off")) {
                    urlStr = getString(R.string.api_url);
                }
                // Headers
                ArrayList<String[]> headers = new ArrayList<>();

                headers.add(new String[]{"Content-Type", "application/json"});
                HttpResultHelper httpResult = new HttpResultHelper();

                httpResult = httpResult.httpPost(urlStr, null, null, data.toString(), headers, 20000);
                BufferedReader in = new BufferedReader(new InputStreamReader(httpResult.getResponse()));
                while ((inputLine = in.readLine()) != null) {
                    result += inputLine;
                }

                Log.d(MainActivity.TAG,">>>test:" + result);

                //check result of action
                if (result.equals("success")) {
                    notification = getString(R.string.success_post);

                    //store date of last successful post to prevent multiple posts per day

                    //storing account data for simple reuse. Data is not stored anywhere outside actifit App.
                    sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);
                    editor = sharedPreferences.edit();
                    editor.putString("actifitLastPostDate",
                            new SimpleDateFormat("yyyyMMdd").format(
                                    Calendar.getInstance().getTime()));
                    editor.commit();
                } else {
                    // notification = getString(R.string.failed_post);
                    notification = result;
                }

                //display proper notification
                displayNotification(notification, progress, context, currentActivity, result);

            }catch (Exception e){

                //display proper notification
                notification = getString(R.string.failed_post);
                displayNotification(notification, progress, context, currentActivity, "");

                Log.d(MainActivity.TAG,"Error connecting:"+e.getMessage());
                e.printStackTrace();
            }
            return null;
        }
    }


}

