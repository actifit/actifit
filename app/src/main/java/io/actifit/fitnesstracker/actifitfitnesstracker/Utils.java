package io.actifit.fitnesstracker.actifitfitnesstracker;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.transition.Slide;
import android.transition.Transition;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.github.rjeschke.txtmark.Processor;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicReference;

import androidx.exifinterface.media.ExifInterface;

    public class Utils {

        private static final String TAG = "Utils";

        // --- Your other Utils methods (uploadFile, etc.) ---

        /**
         * Creates a temporary file from a bitmap and copies EXIF data from the source URI.
         * This method must be called on a background thread.
         *
         * @param bitmap   The scaled bitmap to compress and save.
         * @param context  The application context.
         * @param srcUri   The content URI of the original image source (for EXIF).
         * @param dstFile  The temporary file to write the bitmap and EXIF data to.
         * @throws IOException if a file operation fails.
         */
        public static void createFile(Bitmap bitmap, Context context, Uri srcUri, File dstFile) throws IOException {

            InputStream inputStream = null;
            OutputStream outputStream = null;
            ExifInterface oldExif = null; // Hold EXIF data from the original source

            try {
                // --- Step 1: Read EXIF data from the original source URI ---
                // Open the source stream
                inputStream = context.getContentResolver().openInputStream(srcUri);
                if (inputStream == null) {
                    Log.w(TAG, "createFile: Could not open input stream for URI: " + srcUri);
                    // Decide if this should throw an exception or just return
                    throw new IOException("Could not open input stream for URI: " + srcUri);
                }

                // Read EXIF from the input stream
                // Requires AndroidX ExifInterface library and API 24+ for InputStream constructor
                try {
                    oldExif = new ExifInterface(inputStream);
                    Log.d(TAG, "Successfully read EXIF data from source URI.");
                } catch (IOException e) {
                    Log.e(TAG, "Failed to read EXIF data from source URI: " + srcUri, e);
                    // Continue without EXIF if reading fails, or handle as a critical error
                }


            } finally {
                // --- Close the source input stream immediately after reading EXIF ---
                if (inputStream != null) {
                    try {
                        inputStream.close();
                        Log.d(TAG, "Closed source input stream.");
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing source input stream", e);
                    }
                }
            }

            try {
                // --- Step 2: Write the compressed bitmap to the destination file ---
                outputStream = new FileOutputStream(dstFile);
                // Use a reasonable compression quality, 50 might be too low. Try 80-90.
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
                Log.d(TAG, "Compressed bitmap to destination file: " + dstFile.getAbsolutePath());

            } finally {
                // --- Close the destination output stream after writing the bitmap ---
                if (outputStream != null) {
                    try {
                        outputStream.close();
                        Log.d(TAG, "Closed destination output stream.");
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing destination output stream", e);
                    }
                }
            }

            // --- Step 3: Copy EXIF data to the destination file if available ---
            if (oldExif != null) {
                try {
                    // Open the destination file *again* with ExifInterface for writing
                    ExifInterface newExif = new ExifInterface(dstFile.getAbsolutePath());

                    // Use the copyExif helper to transfer attributes
                    copyExifAttributes(oldExif, newExif);

                    // Save the attributes to the new file
                    newExif.saveAttributes();
                    Log.d(TAG, "Successfully copied and saved EXIF attributes to destination file.");

                } catch (IOException e) {
                    Log.e(TAG, "Failed to copy or save EXIF data to destination file: " + dstFile.getAbsolutePath(), e);
                    // Log the error but allow the upload to proceed without EXIF if saving fails
                }
            } else {
                Log.d(TAG, "No source EXIF data to copy or API < 24.");
            }

            // Note: bitmap.recycle() should be called *after* this method returns
            // by the code that called createFile (e.g., in processImageForUpload or uploadFile)
            // as the caller passed the bitmap and knows when its lifecycle ends.
            // In the previous refactoring of uploadFile, we added recycle there, which is appropriate.
        }

        /**
         * Copies selected EXIF attributes from a source ExifInterface object to a destination.
         *
         * @param oldExif The source ExifInterface (from original file).
         * @param newExif The destination ExifInterface (for the temporary file).
         */
        private static void copyExifAttributes(ExifInterface oldExif, ExifInterface newExif) {
            String[] attributes = new String[]
                    {
                            ExifInterface.TAG_APERTURE_VALUE, // Added common tags
                            ExifInterface.TAG_ARTIST,
                            ExifInterface.TAG_BITS_PER_SAMPLE,
                            ExifInterface.TAG_BRIGHTNESS_VALUE,
                            ExifInterface.TAG_CAMERA_OWNER_NAME,
                            ExifInterface.TAG_COLOR_SPACE,
                            ExifInterface.TAG_COMPONENTS_CONFIGURATION,
                            ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL,
                            ExifInterface.TAG_CONTRAST,
                            ExifInterface.TAG_CUSTOM_RENDERED,
                            ExifInterface.TAG_DATETIME, // Original tags
                            ExifInterface.TAG_DATETIME_DIGITIZED, // Original tags
                            ExifInterface.TAG_DATETIME_ORIGINAL, // Added
                            ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION,
                            ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
                            ExifInterface.TAG_EXIF_VERSION, // Added
                            ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
                            ExifInterface.TAG_EXPOSURE_INDEX,
                            ExifInterface.TAG_EXPOSURE_MODE,
                            ExifInterface.TAG_EXPOSURE_TIME, // Original tags
                            ExifInterface.TAG_FLASH, // Original tags
                            ExifInterface.TAG_FLASH_ENERGY,
                            ExifInterface.TAG_FOCAL_LENGTH, // Original tags
                            ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT,
                            ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION,
                            ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION,
                            ExifInterface.TAG_GAIN_CONTROL,
                            ExifInterface.TAG_GPS_ALTITUDE, // Original tags
                            ExifInterface.TAG_GPS_ALTITUDE_REF, // Original tags
                            ExifInterface.TAG_GPS_AREA_INFORMATION,
                            ExifInterface.TAG_GPS_DATESTAMP, // Original tags
                            ExifInterface.TAG_GPS_DEST_BEARING,
                            ExifInterface.TAG_GPS_DEST_BEARING_REF,
                            ExifInterface.TAG_GPS_DEST_DISTANCE,
                            ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
                            ExifInterface.TAG_GPS_DEST_LATITUDE,
                            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
                            ExifInterface.TAG_GPS_DEST_LONGITUDE,
                            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
                            ExifInterface.TAG_GPS_DIFFERENTIAL,
                            ExifInterface.TAG_GPS_DOP,
                            ExifInterface.TAG_GPS_IMG_DIRECTION,
                            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
                            ExifInterface.TAG_GPS_LATITUDE, // Original tags
                            ExifInterface.TAG_GPS_LATITUDE_REF, // Original tags
                            ExifInterface.TAG_GPS_LONGITUDE, // Original tags
                            ExifInterface.TAG_GPS_LONGITUDE_REF, // Original tags
                            ExifInterface.TAG_GPS_MEASURE_MODE,
                            ExifInterface.TAG_GPS_PROCESSING_METHOD, // Original tags
                            ExifInterface.TAG_GPS_SATELLITES,
                            ExifInterface.TAG_GPS_SPEED,
                            ExifInterface.TAG_GPS_SPEED_REF,
                            ExifInterface.TAG_GPS_STATUS,
                            ExifInterface.TAG_GPS_TIMESTAMP, // Original tags
                            ExifInterface.TAG_GPS_TRACK,
                            ExifInterface.TAG_GPS_TRACK_REF,
                            ExifInterface.TAG_GPS_VERSION_ID,
                            ExifInterface.TAG_IMAGE_DESCRIPTION,
                            ExifInterface.TAG_IMAGE_UNIQUE_ID,
                            ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT,
                            ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH,
                            ExifInterface.TAG_LENS_MAKE, // Added
                            ExifInterface.TAG_LENS_MODEL, // Added
                            ExifInterface.TAG_LENS_SPECIFICATION,
                            ExifInterface.TAG_LIGHT_SOURCE,
                            ExifInterface.TAG_MAKE, // Original tags
                            ExifInterface.TAG_MAX_APERTURE_VALUE,
                            ExifInterface.TAG_METERING_MODE,
                            ExifInterface.TAG_MODEL, // Original tags
                            ExifInterface.TAG_NEW_SUBFILE_TYPE,
                            ExifInterface.TAG_OECF,
                            ExifInterface.TAG_OFFSET_TIME, // Added
                            ExifInterface.TAG_OFFSET_TIME_DIGITIZED, // Added
                            ExifInterface.TAG_OFFSET_TIME_ORIGINAL, // Added
                            ExifInterface.TAG_ORF_ASPECT_FRAME,
                            ExifInterface.TAG_ORF_PREVIEW_IMAGE_START,
                            ExifInterface.TAG_ORF_THUMBNAIL_IMAGE,
                            ExifInterface.TAG_ORIENTATION, // Original tags - IMPORTANT for correct display
                            ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION,
                            ExifInterface.TAG_PIXEL_X_DIMENSION,
                            ExifInterface.TAG_PIXEL_Y_DIMENSION,
                            ExifInterface.TAG_PLANAR_CONFIGURATION,
                            ExifInterface.TAG_PRIMARY_CHROMATICITIES,
                            ExifInterface.TAG_REFERENCE_BLACK_WHITE,
                            ExifInterface.TAG_RELATED_SOUND_FILE,
                            ExifInterface.TAG_RESOLUTION_UNIT, // Added
                            ExifInterface.TAG_ROWS_PER_STRIP,
                            ExifInterface.TAG_SAMPLES_PER_PIXEL,
                            ExifInterface.TAG_SATURATION,
                            ExifInterface.TAG_SCENE_CAPTURE_TYPE,
                            ExifInterface.TAG_SCENE_TYPE,
                            ExifInterface.TAG_SENSING_METHOD,
                            ExifInterface.TAG_SHARPNESS,
                            ExifInterface.TAG_SHUTTER_SPEED_VALUE,
                            ExifInterface.TAG_SPATIAL_FREQUENCY_RESPONSE,
                            ExifInterface.TAG_SPECTRAL_SENSITIVITY,
                            ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY,
                            ExifInterface.TAG_STRIP_BYTE_COUNTS,
                            ExifInterface.TAG_STRIP_OFFSETS,
                            ExifInterface.TAG_SUBSEC_TIME, // Original tags
                            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, // Added
                            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, // Added
                            ExifInterface.TAG_SUBJECT_AREA,
                            ExifInterface.TAG_SUBJECT_DISTANCE,
                            ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
                            ExifInterface.TAG_SUBJECT_LOCATION,
                            ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH,
                            ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH,
                            ExifInterface.TAG_TRANSFER_FUNCTION,
                            ExifInterface.TAG_USER_COMMENT,
                            ExifInterface.TAG_WHITE_BALANCE, // Original tags
                            ExifInterface.TAG_WHITE_POINT,
                            ExifInterface.TAG_X_RESOLUTION, // Added
                            ExifInterface.TAG_Y_RESOLUTION // Added
                    };

            for (String attribute : attributes) {
                String value = oldExif.getAttribute(attribute);
                if (value != null) {
                    newExif.setAttribute(attribute, value);
                    // Log.d(TAG, "Copied EXIF attribute: " + attribute + " = " + value); // Optional: Log copied tags
                }
            }
        }

    public static void uploadFile(Bitmap bitmap, Uri fileUri, EditText textBox,
                                  Context ctx, Activity activity) {

        // Declare dialog here so it can be accessed in the UI thread runnable and callbacks
        //final ProgressDialog uploadProgress;

        if (fileUri != null) {

            // --- CREATE AND SHOW DIALOG ON THE MAIN THREAD ---
            // This block needs to run on the UI thread
            // Use the activity's runOnUiThread method
            // Declare a *final* dialog variable to be able to access it inside the runOnUiThread runnable
            //final ProgressDialog[] dialogHolder = new ProgressDialog[1]; // Use an array or AtomicReference to hold the dialog
            /*
            activity.runOnUiThread(() -> {
                try {
                    // CREATE the ProgressDialog here on the UI thread
                    dialogHolder[0] = new ProgressDialog(activity);
                    dialogHolder[0].setMessage(ctx.getString(R.string.start_upload));
                    // SHOW the dialog here on the UI thread
                    dialogHolder[0].show();
                    Log.d(TAG, "ProgressDialog created and shown on UI thread.");
                } catch (Exception e) {
                    // Log potential errors during dialog creation/showing, though less likely now
                    Log.e(TAG, "Error showing progress dialog", e);
                    // You might want to notify the user or handle this failure
                    Toast.makeText(ctx, "Could not show progress dialog.", Toast.LENGTH_SHORT).show();
                }
            });

            */

            // --- END CREATE AND SHOW DIALOG ON THE MAIN THREAD ---

            // Assign the dialog reference *after* the UI thread block has likely executed
            // Note: runOnUiThread is asynchronous, but the next lines will likely run
            // shortly after the UI thread picks up the runnable. Using the holder array
            // ensures you get the instance created on the UI thread.
            //uploadProgress = dialogHolder[0];


            // IMPORTANT: Ensure the remaining file operations and network call
            // are still happening on a BACKGROUND thread.
            // This whole `uploadFile` method is currently called within the
            // ExecutorService's Runnable from processImageForUpload, which is correct.


            // Create unique image file name
            final String fileName = UUID.randomUUID().toString() + ".jpg"; // Add extension
            // Using cache dir is often better for temporary files than getFilesDir()
            final File file = new File(ctx.getCacheDir(), fileName); // Changed to cacheDir

            try {
                // Create file from Bitmap or original Uri - THIS NEEDS TO BE ON A BACKGROUND THREAD
                // Since uploadFile is already on a background thread, calling createFile here is fine.
                Log.d(TAG, "Creating temporary file: " + file.getAbsolutePath());
                createFile(bitmap, ctx, fileUri, file); // Call your createFile method
                Log.d(TAG, "Temporary file created.");

            } catch (IOException e) {
                Log.e(TAG, "Error creating temporary file for upload", e);
                // Dismiss dialog and show error on the main thread if file creation fails
                activity.runOnUiThread(() -> {
                    /*if (uploadProgress != null && uploadProgress.isShowing()) {
                        uploadProgress.dismiss();
                    }*/
                    Toast.makeText(ctx, "Failed to prepare image for upload.", Toast.LENGTH_SHORT).show();
                });
                // Exit the method early as we can't proceed
                return;
            }


            // Prepare the file to be uploaded - THIS NEEDS TO BE ON A BACKGROUND THREAD
            // Read the created temporary file
            String mimeType = ctx.getContentResolver().getType(fileUri);
            if (mimeType == null) {
                // Fallback if getContentResolver().getType() returns null
                mimeType = "image/*"; // Or a more specific default like "image/jpeg"
                Log.w(TAG, "Could not determine MIME type for URI, using default: " + mimeType);
            }
            RequestBody requestFile = RequestBody.create(MediaType.parse(mimeType), file);
            MultipartBody.Part body = MultipartBody.Part.createFormData("image", file.getName(), requestFile); // "image" should match server's expected field name
            String token = ctx.getString(R.string.media_upload_key); // Replace with your token


            // Create Retrofit client and call API - Can be on background thread
            // NOTE: Ensure RetrofitClient.getClient() is thread-safe or created once.
            ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
            Call<ResponseBody> call = apiService.uploadImage(body, token);

            // Enqueue the call - Can be on background thread. Callback runs on Main Thread by default.
            call.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull retrofit2.Response<ResponseBody> response) {
                    // This callback runs on the MAIN THREAD by default because it's enqueued.
                    // runOnUiThread is redundant here but harmless.
                    // activity.runOnUiThread(() -> { // Redundant

                    /*if (uploadProgress != null && uploadProgress.isShowing()) {
                        uploadProgress.dismiss();
                    }*/
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Upload successful. Response code: " + response.code());
                        Toast.makeText(ctx, ctx.getString(R.string.upload_complete), Toast.LENGTH_SHORT).show();
                        // Assuming server returns the final URL or part of it
                        String fullImgUrl = ctx.getString(R.string.actifit_usermedia_url) + fileName; // This assumes the server serves the file using the *same* name you sent. Verify your server's API response for the actual URL.
                        String imgMarkdownText = "![](" + fullImgUrl + ")";
                        // Append the uploaded image URL to the text as markdown
                        // NOTE: Modifying UI (textBox) must be on the main thread. This is fine here.
                        if (textBox != null) { // Add null check for safety
                            int start = Math.max(textBox.getSelectionStart(), 0);
                            int end = Math.max(textBox.getSelectionEnd(), 0);
                            textBox.getText().replace(Math.min(start, end), Math.max(start, end),
                                    imgMarkdownText, 0, imgMarkdownText.length());
                        } else {
                            Log.w(TAG, "EditText is null, cannot insert image URL.");
                        }

                    } else {
                        Log.e(TAG, "Upload failed. Response code: " + response.code() + ", Message: " + response.message());
                        // Attempt to read error body if available
                        try {
                            String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                            Log.e(TAG, "Error Body: " + errorBody);
                        } catch (IOException e) {
                            Log.e(TAG, "Error reading error body", e);
                        }
                        showError(ctx); // Pass context
                    }

                    // Delete the temporary file regardless of success/failure
                    if (file.exists()) {
                        if (file.delete()) {
                            Log.d(TAG, "Temporary file deleted: " + file.getAbsolutePath());
                        } else {
                            Log.w(TAG, "Failed to delete temporary file: " + file.getAbsolutePath());
                        }
                    }

                    // }); // Redundant runOnUiThread closing bracket
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    // This callback runs on the MAIN THREAD by default.
                    // runOnUiThread is redundant but harmless.
                    // activity.runOnUiThread(() -> { // Redundant

                    /*if (uploadProgress != null && uploadProgress.isShowing()) {
                        uploadProgress.dismiss();
                    }*/
                    Log.e(TAG, "Upload network failure", t); // Log the full stack trace
                    showError(ctx); // Pass context

                    // Delete the temporary file on failure too
                    if (file.exists()) {
                        if (file.delete()) {
                            Log.d(TAG, "Temporary file deleted after failure: " + file.getAbsolutePath());
                        } else {
                            Log.w(TAG, "Failed to delete temporary file after failure: " + file.getAbsolutePath());
                        }
                    }

                    // }); // Redundant runOnUiThread closing bracket
                }

                // Made showError static and accepts Context
                private void showError(Context context) {
                    // This method is called from Retrofit callbacks (Main Thread),
                    // so runOnUiThread inside it is redundant but harmless.
                    // activity.runOnUiThread(() -> { // Redundant
                    Toast toast = Toast.makeText(context, context.getString(R.string.upload_failed), Toast.LENGTH_SHORT);
                    // Make sure getView() and findViewById are safe - accessing internal Toast views can be fragile.
                    // A simpler approach is just a standard red Toast if supported by system.
                    // If you need custom Toast appearance, use a custom layout with Toast or a Snackbar/Dialog.
                    try {
                        TextView v = toast.getView().findViewById(android.R.id.message);
                        if (v != null) {
                            v.setTextColor(Color.RED);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Could not set Toast text color", e);
                        // Fallback to default Toast appearance
                    }
                    toast.show();
                    // }); // Redundant runOnUiThread closing bracket
                }
            });
        } else {
            // Handle case where fileUri is unexpectedly null
            Log.e(TAG, "uploadFile called with null fileUri");
            activity.runOnUiThread(() -> {
                Toast.makeText(ctx, "Error: No file selected for upload.", Toast.LENGTH_SHORT).show();
            });
        }

        // Ensure the bitmap is recycled *after* it's been used by createFile
        // and any other processing. If you only use it in createFile,
        // you could recycle it after the createFile call here (on background thread),
        // assuming Retrofit/OkHttp doesn't need the bitmap itself (it shouldn't, it needs the file).
        if (bitmap != null && !bitmap.isRecycled()) {
            Log.d(TAG, "Recycling bitmap after use.");
            bitmap.recycle();
        }
    }


    //removes any tags that do not match predefined list
    public static String sanitizeContent(String htmlContent, Boolean minimal) {
        // Parse the HTML content
        /*Document dirtyDocument = Jsoup.parseBodyFragment(htmlContent);

        // Clean the HTML using a safelist to allow only certain tags and attributes
        Safelist safelist = Safelist.none().addTags("br", "p", "div"); // Add additional tags if needed
        Cleaner cleaner = new Cleaner(safelist);
        Document cleanDocument = cleaner.clean(dirtyDocument);

        // Extract the sanitized text from the clean document
        String sanitizedText = cleanDocument.body().text();

        return sanitizedText;*/

        Document dirtyDocument = Jsoup.parseBodyFragment(htmlContent);

        // Define the list of tags and attributes to be removed
        String[] tagsToRemove = {"script", "style", "iframe", "object", "embed", "applet", "img", "a"};
        if (minimal) {
            tagsToRemove = new String[]{"script", "style", "iframe", "object", "embed", "applet"};
        }

        String[] attributesToRemove = {"onclick", "onload"}; // Add more attributes if needed

        // Remove the specified tags and attributes from the HTML
        Safelist safelist = Safelist.relaxed().removeTags(tagsToRemove).removeAttributes(":all", attributesToRemove);
        Cleaner cleaner = new Cleaner(safelist);
        Document cleanDocument = cleaner.clean(dirtyDocument);

        // Extract the sanitized text from the clean document
        String sanitizedText = cleanDocument.body().text();

        return sanitizedText;
    }

    public static String trimText(String text, int limit){
        // Trim the processed text to 140 characters
        if (text.length() > limit) {
            text = text.substring(0, limit);
        }

        // Ensure that the trimmed text ends on a word boundary
        text = text.replaceAll("\\s+$", "");

        return text;
    }

    public static String parseMarkdown(String markdown) {
        // Process the Markdown content
        String processedText = Processor.process(markdown);

        // Exclude image tags from the processed text
        //processedText = processedText.replaceAll("!\\[.*?]\\(.*?\\)", "");



        // Ensure that the trimmed text ends on a word boundary
        //processedText = processedText.replaceAll("\\s+$", "");

        return processedText;
    }

    public static Date getFormattedDate(String dateParam){
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

        try {
            Date paramDate = format.parse(dateParam);
            return paramDate;
        }catch(Exception ex){
            ex.printStackTrace();
        }
        return new Date();
    }

    public static boolean isPastTime(String dateParam){
        Date localDate = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

        try {
            Date paramDate = format.parse(dateParam);
            long currentDate = System.currentTimeMillis();
            long difference = paramDate.getTime() - currentDate - localDate.getTimezoneOffset() * 60000;
            if (difference < 0)
                return true;
        }catch (Exception ex){
            ex.printStackTrace();
        }
        return false;
    }

    //handles displaying the post date/time in "ago" format
    public static String getTimeDifference(String dateParam) {
        Date localDate = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

        try{
            Date paramDate = format.parse(dateParam);
            long currentDate = System.currentTimeMillis();
            long difference = Math.abs(paramDate.getTime() - currentDate - localDate.getTimezoneOffset() * 60000);

            long mins = TimeUnit.MILLISECONDS.toMinutes(difference);
            long hours = TimeUnit.MILLISECONDS.toHours(difference);
            long days = TimeUnit.MILLISECONDS.toDays(difference);
            long weeks = days / 7;
            long months = weeks / 4;
            long years = months / 12;
            long remainingMonths = months % 12;

            if (mins < 60) {
                return mins + " min(s) ago";
            } else if (hours < 24) {
                return hours + " hour(s) ago";
            } else if (days < 7) {
                return days + " day(s) ago";
            } else if (weeks < 4) {
                return weeks + " week(s) ago";
            } else if (months < 12) {
                return months + " month(s) ago";
            } else {
                return years + " year(s) ago";
                // return years + " years and " + remainingMonths + " month(s)";
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return "";
    }

    // Recursive method to find the parent ListView
    public static ListView findParentListView(View view) {
        if (view.getParent() instanceof ListView) {
            return (ListView) view.getParent();
        } else if (view.getParent() instanceof View) {
            return findParentListView((View) view.getParent());
        } else {
            return null;
        }
    }

    //checks if a user has an active vote on a post
    public static Boolean userVotedPost(String voter, JSONArray actVotes , String permlink){// int post_id){
        //no logged in user case
        if (voter == null || voter == "" || voter.length() < 1) return false;
        for (int i = 0; i < actVotes.length(); i++) {
            try {
                VoteEntryAdapter.VoteEntry vEntry = new VoteEntryAdapter.VoteEntry((actVotes.getJSONObject(i)), 0);//second param not needed here
                if (vEntry.voter.equals(voter)){
                    return true;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    //perform calls to API
    public static void queryAPI(Context ctx, String user, String op_name,
                                JSONObject cstm_params,
                                final ProgressBar taskProgress,
                                final APIResponseListener listener,
                                final Activity activity) {

        RequestQueue queue = Volley.newRequestQueue(ctx);

        if (user.equals("") || op_name.equals("") || cstm_params == null) {

            Log.e(MainActivity.TAG, "missing params");
            activity.runOnUiThread(() -> {
                taskProgress.setVisibility(View.GONE);
            });
        } else {
            try {

                JSONArray operation = new JSONArray();
                operation.put(0, op_name);
                operation.put(1, cstm_params);

                String bcastUrl = Utils.apiUrl(ctx)+ctx.getString(R.string.perform_trx_link);
                bcastUrl += user +
                            "&operation=[" + URLEncoder.encode(String.valueOf(operation), "UTF-8") + "]" +
                            "&bchain=HIVE";
                ;


                //send out transaction
                JsonObjectRequest transRequest = new JsonObjectRequest(Request.Method.GET,
                        bcastUrl, null,
                        response -> activity.runOnUiThread(() -> {
                            taskProgress.setVisibility(View.GONE);
                            Log.d(MainActivity.TAG, response.toString());

                            //
                            if (response.has("success")) {
                                //successfully wrote to chain gadget purchase
                                try {
                                    JSONObject bcastRes = response.getJSONObject("trx").getJSONObject("tx");


                                    if (listener != null) {
                                        listener.onResponse(true);
                                    }

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                Log.d(MainActivity.TAG, "Error querying blockchain");
                                if (listener != null) {
                                    listener.onResponse(false);
                                }
                                //progress.dismiss();
                                //deactivateGadget.clearAnimation();
                                //Toast.makeText(getContext(), getContext().getString(R.string.error_deactivate_product), Toast.LENGTH_LONG).show();
                            }
                        }),
                        error -> {
                            // error
                            if (listener != null) {
                                listener.onResponse(false);
                            }
                            //progress.dismiss();
                            //deactivateGadget.clearAnimation();
                            //Toast.makeText(getContext(), getContext().getString(R.string.error_deactivate_product), Toast.LENGTH_LONG).show();
                        }) {
                    @Override
                    public Map<String, String> getHeaders() throws AuthFailureError {
                        final Map<String, String> params = new HashMap<>();
                        params.put("Content-Type", "application/json");
                        params.put(ctx.getString(R.string.validation_header), ctx.getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken);
                        return params;
                    }
                };

                //to enable waiting for longer time with extra retry
                transRequest.setRetryPolicy(new DefaultRetryPolicy(
                        MainActivity.connectTimeout,
                        MainActivity.connectMaxRetries,
                        MainActivity.connectSubsequentRetryDelay));

                queue.add(transRequest);
            } catch (Exception excep) {
                excep.printStackTrace();
            }

        }
    }


    //marks a notification as read
    public static void markNotifRead(Context ctx, Activity activity, String user, String notifId){
        try {
            RequestQueue queue = Volley.newRequestQueue(ctx);

            String bcastUrl = Utils.apiUrl(ctx)+ctx.getString(R.string.mark_notif_read);
            bcastUrl += notifId + "/?user=" + user;


            //send out transaction
            JsonObjectRequest transRequest = new JsonObjectRequest(Request.Method.GET,
                    bcastUrl, null,
                    response -> activity.runOnUiThread(() -> {
                        /*Log.d(MainActivity.TAG, response.toString());
                        try{
                            //
                            if (response.has("status") && response.getString("status").equals("success")) {
                                //successfully marked as read
                                try {
                                    //JSONObject bcastRes = response.getJSONObject("trx").getJSONObject("tx");
                                    displayNotification(ctx.getString(R.string.trx_success), null, ctx, null, false);

                                } catch (Exception e) {
                                    e.printStackTrace();
                                    displayNotification(ctx.getString(R.string.trx_error), null, ctx, null, false);
                                }
                            } else {
                                Log.d(MainActivity.TAG, "Error querying blockchain");
                                displayNotification(ctx.getString(R.string.trx_error), null, ctx, null, false);
                            }
                        }catch(Exception exca){
                            exca.printStackTrace();
                        }*/
                    }),
                    error -> {
                        // error

                    }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    final Map<String, String> params = new HashMap<>();
                    params.put("Content-Type", "application/json");
                    params.put(ctx.getString(R.string.validation_header), ctx.getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken);
                    return params;
                }
            };

            //to enable waiting for longer time with extra retry
            transRequest.setRetryPolicy(new DefaultRetryPolicy(
                    MainActivity.connectTimeout,
                    MainActivity.connectMaxRetries,
                    MainActivity.connectSubsequentRetryDelay));

            queue.add(transRequest);
        } catch (Exception excep) {
            excep.printStackTrace();
        }
    }

    //perform calls to API
    public static void queryAPIPost(Context ctx, String user, String actvKey, String op_name,
                                JSONObject cstm_params,
                                final ProgressBar taskProgress,
                                final APIResponseListener listener,
                                    Activity activity) {

        RequestQueue queue = Volley.newRequestQueue(ctx);

        if (user.equals("") || op_name.equals("") || cstm_params == null) {

            Log.e(MainActivity.TAG, "missing params");
            activity.runOnUiThread(() -> {
                taskProgress.setVisibility(View.GONE);
            });
        } else {
            try {


                String bcastUrl = Utils.apiUrl(ctx)+ctx.getString(R.string.perform_trx_post_link);
                bcastUrl += user +
                        "&bchain=HIVE";
                ;

                JSONArray operation = new JSONArray();
                operation.put(0, op_name);
                operation.put(1, cstm_params);

                JSONArray op_array = new JSONArray();
                op_array.put(operation);

                //sending post data
                JSONObject body = new JSONObject();

                body.put("operation", op_array.toString());
                body.put("active", actvKey);


                //send out transaction
                JsonObjectRequest transRequest = new JsonObjectRequest(Request.Method.POST,
                        bcastUrl, body,
                        response ->  activity.runOnUiThread(() -> {
                            taskProgress.setVisibility(View.GONE);
                            Log.d(MainActivity.TAG, response.toString());

                            //
                            if (response.has("success")) {
                                //successfully wrote to chain gadget purchase
                                try {
                                    JSONObject bcastRes = response.getJSONObject("trx").getJSONObject("tx");


                                    if (listener != null) {
                                        listener.onResponse(true);
                                    }

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                Log.d(MainActivity.TAG, "Error querying blockchain");
                                if (listener != null) {
                                    listener.onResponse(false);
                                }
                            }
                        }),
                        error -> {
                            // error
                            if (listener != null) {
                                listener.onResponse(false);
                            }
                        }) {
                    @Override
                    public Map<String, String> getHeaders() throws AuthFailureError {
                        final Map<String, String> params = new HashMap<>();
                        params.put("Content-Type", "application/json");
                        params.put(ctx.getString(R.string.validation_header), ctx.getString(R.string.validation_pre_data) + " " + LoginActivity.accessToken);
                        return params;
                    }
                };

                //to enable waiting for longer time with extra retry
                transRequest.setRetryPolicy(new DefaultRetryPolicy(
                        MainActivity.connectTimeout,
                        MainActivity.connectMaxRetries,
                        MainActivity.connectSubsequentRetryDelay));

                queue.add(transRequest);
            } catch (Exception excep) {
                excep.printStackTrace();
            }

        }
    }

    //to handle multiple successive calls
    public interface APIResponseListener {
        //void onResponse(byte[] serializedTransaction);
        void onError(String errorMessage);

        //void onResponse(JSONObject result);

        void onResponse(boolean success);
    }

    public static Transition createSlideTransition(Context ctx) {
        Slide slide = new Slide(Gravity.END);
        slide.setDuration(ctx.getResources().getInteger(android.R.integer.config_longAnimTime));
        return slide;
    }

    static void displayNotification(final String notification, final ProgressDialog progress,
                             final Context context, final Activity currentActivity,
                             final Boolean closeScreen){
        //render result
        currentActivity.runOnUiThread(() -> {
            //hide the progressDialog
            if (progress!=null) {
                progress.dismiss();
            }

            AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
            builder1.setMessage(notification);

            builder1.setCancelable(true);

            builder1.setPositiveButton(
                    context.getString(R.string.dismiss_button),
                    (dialog, id) -> {
                        dialog.cancel();
                        //if we need to close current Activity
                        if (closeScreen) {
                            //close current screen
                            Log.d(MainActivity.TAG,">>>Finish");
                            currentActivity.finish();
                        }
                    });
            //create and display alert window
            AlertDialog alert11 = builder1.create();
            builder1.show();
        });

    }

    public static File getFileFromUri(Uri uri, Context ctx) {
        String filePath = null;
        if ("content".equals(uri.getScheme())) {
            String[] projection = {MediaStore.Images.Media.DATA};
            Cursor cursor = ctx.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                filePath = cursor.getString(column_index);
                cursor.close();
            }
        } else if ("file".equals(uri.getScheme())) {
            filePath = uri.getPath();
        }

        if (filePath != null) {
            return new File(filePath);
        } else {
            return null;
        }
    }

    @SuppressLint("Range")
    public static String getOriginalFileName(Context context, Uri uri) {
        String fileName = null;
        String scheme = uri.getScheme();

        if (scheme != null && scheme.equals("content")) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);

            try {
                if (cursor != null && cursor.moveToFirst()) {
                    fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else if (scheme != null && scheme.equals("file")) {
            fileName = new File(uri.getPath()).getName();
        }

        return fileName;
    }


    public static long getVidSize(Context ctx, Uri videoUri){
        long fileSizeInBytes = 0;
        try{
            String filePath = getRealPathFromURI(ctx, videoUri);
            File file = new File(filePath);
            fileSizeInBytes = file.length();
            //vidSize = (float)fileSizeInBytes / (1024 * 1024); // Convert to MB
        }catch (Exception ex){
            ex.printStackTrace();
        }
        return fileSizeInBytes;
    }

    public static String getRealPathFromURI(Context ctx, Uri contentUri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = ctx.getContentResolver().query(contentUri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    public static double getVidDuration(Context ctx, Uri videoUri){
        long vidDuration = 0l;
        try{
            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(ctx, videoUri);
            vidDuration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            vidDuration = vidDuration / 1000;//comes in millisecs
        }catch (Exception ex){
            ex.printStackTrace();
        }
        return vidDuration;
    }

    //generate video thumbnail

    public static Bitmap generateThumbnail(Context ctx, Uri videoUri) {
//        MediaStore.Video.Media.

        System.out.println(">>>>> generate thumb");
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        System.out.println(">>>>> mediaMetadataRetriever initiated");
        mediaMetadataRetriever.setDataSource(ctx, videoUri);
        System.out.println(">>>>> mediaMetadataRetriever set");
        // Get the video duration in milliseconds.
        long videoDuration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

        // Calculate the time at which to take the thumbnailTime.
        long thumbnailTime = videoDuration / 4;

        Bitmap thumbnail = mediaMetadataRetriever.getFrameAtTime(thumbnailTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        try {
            mediaMetadataRetriever.release();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return thumbnail;
    }

    //create a uri/temp file from bitmap
    public static Uri getBitmapFileUri(Bitmap bitmap) throws IOException {
        File file = File.createTempFile("bitmap", ".jpg");
        OutputStream outputStream = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        outputStream.close();

        return Uri.fromFile(file);
    }

    public static void loadUserVids(RequestQueue requestQueue, Context ctx, String xcstkn, VideoUploadFragment parent){
        //remove # from start if exists
        if (xcstkn.startsWith("#")){
            xcstkn = xcstkn.substring(1);
        }
        String apiUrl = ctx.getString(R.string.three_speak_user_vids_url);

        String finalXcstkn = xcstkn;
        JsonArrayRequest jsonObjectRequest = new JsonArrayRequest
                (Request.Method.GET, apiUrl, null, new Response.Listener<JSONArray>() {

                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            // Handle the response here
                            System.out.println(response.toString());

                            // Check if the response contains data
                            if (response != null && response.length() >0){
                            //if (response.has("data")) {
                                // set user videos array
                                JSONArray userVidList = response;//.getJSONArray("data");
                                parent.setVidsList(userVidList);
//                                System.out.println(this.userVidList.toString());
                            }else{
                                parent.setVidsList(null);
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                            parent.refreshList.clearAnimation();
                        }
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // Handle error
                        error.printStackTrace();
                        parent.refreshList.clearAnimation();
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", "Bearer " + finalXcstkn);
                return headers;
            }
        };

        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
                MainActivity.connectTimeout,
                MainActivity.connectMaxRetries,
                MainActivity.connectSubsequentRetryDelay));

// Add the request to the RequestQueue
        //RequestQueue requestQueue = Volley.newRequestQueue(ctx);
        requestQueue.add(jsonObjectRequest);
    }

    public static void connectSession3S(RequestQueue requestQueue, Context ctx, VideoUploadFragment parent){
        SharedPreferences sharedPreferences = ctx.getSharedPreferences("actifitSets",MODE_PRIVATE);
        String xcstkn = sharedPreferences.getString(ctx.getString(R.string.three_speak_saved_token),"");

        //if we already have access token, let's proceed loading vids
        if (!xcstkn.equals("")){
            //Utils.loadUserVids(requestQueue, ctx, xcstkn, parent);
            Utils.grab3speakCookie(requestQueue, ctx, xcstkn, parent);
            return;
        }

        //parent.refreshList.startAnimation(parent.rotate);

        String loginUrl = ctx.getString(R.string.three_speak_login_url).replace("_USERNAME_", MainActivity.username);

        //RequestQueue requestQueue = Volley.newRequestQueue(ctx);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, loginUrl, null, response -> {
                    try {
                        String memo = "";

                        //Map<String, String> headers = response.headers;
                        //String cookies = headers.get("Set-Cookie");

                        // Assuming the response is in JSON format
                        if (response.has("memo")) {
                            memo = response.getString("memo");

                            //verify token legitimacy
                            String apiUrl = ctx.getString(R.string.three_speak_actifit_verify_url).replace("_USERNAME_",MainActivity.username);

                            // Create the JSON object to be sent in the request body
                            JSONObject jsonBody = new JSONObject();
                            try {
                                jsonBody.put("memo", memo);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            JsonObjectRequest jsonInnerObjectRequest = new JsonObjectRequest
                                    (Request.Method.POST, apiUrl, jsonBody, response1 -> {
                                        try {
                                            // Handle the response here
                                            System.out.println(response1.toString());
                                            if (response1.has("error")){
                                                parent.refreshList.clearAnimation();
                                            }else if (response1.has("xcstkn")){
                                                //store the memo
                                                String tkn = response1.getString("xcstkn");
                                                if (tkn.startsWith("#")){
                                                    tkn = tkn.substring(1);
                                                }
                                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                                editor.putString(ctx.getString(R.string.three_speak_saved_token), tkn);
                                                editor.apply();

                                                Utils.grab3speakCookie(requestQueue ,ctx, tkn, parent);


                                            }
                                        } catch (Exception exx) {
                                            exx.printStackTrace();
                                        }
                                    }, new Response.ErrorListener() {

                                        @Override
                                        public void onErrorResponse(VolleyError error) {
                                            // Handle error
                                            error.printStackTrace();
                                        }
                                    }) {
                                @Override
                                public Map<String, String> getHeaders() {
                                    Map<String, String> headers = new HashMap<>();
                                    headers.put("Content-Type", "application/json");
                                    headers.put("x-acti-token", "Bearer " + LoginActivity.accessToken);
                                    return headers;
                                }
                            };

                            requestQueue.add(jsonInnerObjectRequest);


                        }

                        System.out.println(memo); // Log the memo

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }, error -> {
                    // Handle error
                    error.printStackTrace();
                });

// Add the request to the RequestQueue
        requestQueue.add(jsonObjectRequest);

    }

    public static JSONArray grab3SpeakDefaultBenefic(){
        // Create a JSONArray to hold the beneficiary list
        JSONArray benefList = new JSONArray();
        try {
            // Create beneficiary objects and add them to the JSONArray
            JSONObject beneficiary1 = new JSONObject();
            beneficiary1.put("account", "spk.beneficiary");
            beneficiary1.put("weight", 1000);
            benefList.put(beneficiary1);

            /*JSONObject beneficiary2 = new JSONObject();

            beneficiary2.put("account", "threespeakleader");

            beneficiary2.put("weight", 100);
            benefList.put(beneficiary2);*/
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return benefList;
    }


    public static void delete3spkVid(RequestQueue requestQueue, Context ctx,
                                     //String xcstkn,
                                     VideoUploadFragment parent,
                                     String vidPermlink){
        //remove # from start if exists
        /*if (xcstkn.startsWith("#")){
            xcstkn = xcstkn.substring(1);
        }*/

        String apiUrl = ctx.getString(R.string.three_speak_user_delete_vid).replace("_VIDPERM_",
                vidPermlink);

        //String finalXcstkn = xcstkn;
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, apiUrl, null, new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            // Handle the response here
                            System.out.println(response.toString());

                            // Check if the response contains data
                            if (response != null){

                                if (response.has("success")) {
                                    //if (response.getBoolean("status")){
                                        Toast.makeText(ctx, ctx.getString(R.string.video_deleted_success),
                                                Toast.LENGTH_LONG).show();
                                    //}
                                }
                                // set user videos array
                                //JSONObject response1 = response;//.getJSONArray("data");
                                //parent.setVidsList(userVidList);
//                                System.out.println(this.userVidList.toString());
                            }else{
                                //parent.setVidsList(null);
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                            //parent.refreshList.clearAnimation();
                        }
                        //parent.refreshList.clearAnimation();
                        if (parent.submitLoader != null) {
                            parent.submitLoader.setVisibility(View.GONE);
                        }
                        //refresh vids list
                        parent.refreshList.performClick();
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // Handle error
                        error.printStackTrace();
                        //parent.refreshList.clearAnimation();
                        if (parent.submitLoader != null) {
                            parent.submitLoader.setVisibility(View.GONE);
                        }
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", "Bearer " + LoginActivity.accessToken);
                return headers;
            }
        };

        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
                MainActivity.connectTimeout,
                MainActivity.connectMaxRetries,
                MainActivity.connectSubsequentRetryDelay));

// Add the request to the RequestQueue
        //RequestQueue requestQueue = Volley.newRequestQueue(ctx);
        requestQueue.add(jsonObjectRequest);
    }


    public static void markVideoPublished(Context ctx, RequestQueue requestQueue, UploadedVideoModel vidEntry){
        JSONObject videoInfo = new JSONObject();
        try {
            videoInfo.put("videoId", vidEntry._id);
            videoInfo.put("title", vidEntry.title);
            videoInfo.put("description", vidEntry.description);
            videoInfo.put("tags", vidEntry.tags);
            videoInfo.put("thumbnail", vidEntry.thumbnail);

            System.out.println("markVideoPublished");
            System.out.println(videoInfo.toString());

            String url = ctx.getString(R.string.three_speak_user_vids_url)+"/iPublished";

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url,  videoInfo,
                    response -> {
                        // Handle the response here
                        try {

                            if (response.has("success")) {

                                //show confirmation
                                /*Toast.makeText(ctx, ctx.getString(R.string.submit_vid_success), Toast.LENGTH_LONG);
                                //refresh vids list
                                SharedPreferences sharedPreferences = ctx.getSharedPreferences("actifitSets",MODE_PRIVATE);
                                String xcstkn = sharedPreferences.getString(ctx.getString(R.string.three_speak_saved_token),"");
*/
                            } else {
                                // Handle other status codes if needed
                                // ...

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    },
                    error -> {
                        // Handle error response
                        error.printStackTrace();

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

    public static void grab3speakCookie(RequestQueue requestQueue, Context ctx,
                                         String tkn, VideoUploadFragment parent){
        String loginUrl = ctx.getString(R.string.three_speak_login_url)
                .replace("_USERNAME_", MainActivity.username)
                +"&access_token="+tkn;

        //RequestQueue requestQueue = Volley.newRequestQueue(ctx);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, loginUrl, null, response -> {
                    try {

                        //also load user vids
                        Utils.loadUserVids(requestQueue, ctx, tkn, parent);

                    } catch (Exception e) {
                        e.printStackTrace();
                        parent.refreshList.clearAnimation();
                    }
                }, error -> {
                    // Handle error
                    error.printStackTrace();
                });

// Add the request to the RequestQueue
        requestQueue.add(jsonObjectRequest);
    }

    public static String findMatchingStatus(String status_code, String[][] statusList){
        String vidStatus = status_code;

        String matchingDescription = null;

// Iterate through the statusList array
        for (String[] status : statusList) {
            if (vidStatus.equals(status[0])) {
                matchingDescription = status[2];
                break;
            }
        }

        return matchingDescription;

    }

    public static String grabUserDefaultVoteWeight(){
        if (MainActivity.username != ""){
            if (MainActivity.userSettings != null && MainActivity.userSettings.has("default_vote_weight")){
                try {
                    return MainActivity.userSettings.getInt("default_vote_weight")+"";
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return "10";//standard default value
    }

    public static void loadUserSettings(RequestQueue queue, Context ctx){
        if (MainActivity.username != ""){
            // This holds the url to connect to the API and grab the settings.
            String settingsUrl = Utils.apiUrl(ctx)+ ctx.getString(R.string.fetch_settings)
                    +"/" + MainActivity.username;

            JsonObjectRequest settingsRequest = new JsonObjectRequest(Request.Method.GET,
                    settingsUrl, null, new Response.Listener<JSONObject>() {

                @Override
                public void onResponse(JSONObject settingsList) {
                    if (settingsList.has("settings")) {
                        try {
                            MainActivity.userSettings = settingsList.getJSONObject("settings");
                        }catch(Exception ex) {
                            ex.printStackTrace();
                        }
                    }

                }
            }, new Response.ErrorListener() {

                @Override
                public void onErrorResponse(VolleyError error) {
                    // TODO: Handle error
                    Log.e(MainActivity.TAG, "error connecting");
                }
            });

            // Add request to be processed
            queue.add(settingsRequest);

        }
    }

    public static String apiUrl(Context ctx){
        String[] apis = ctx.getResources().getStringArray(R.array.APIs);
        Random r = new Random();
        int index = r.nextInt(apis.length);
        return apis[index];
    }


    /***********************************************************/
    /** implementation of exercise data mapping portion
     * relying on github repo
     * https://github.com/yuhonas/free-exercise-db
     * */

    private static final String IMAGE_BASE_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/";

    public static List<Exercise> loadExercisesFromAssets(Context context) {
        List<Exercise> exercises = new ArrayList<>();
        try {
            InputStream inputStream = context.getAssets().open("exercises.json");
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();

            String json = new String(buffer, "UTF-8");
            //Create type of list for gson parsing
            Type listType = new TypeToken<List<ExerciseModel>>(){}.getType();
            List<ExerciseModel> exerciseModels = new Gson().fromJson(json,listType );

            //map from ExerciseModel to Exercise objects
            for (ExerciseModel model : exerciseModels) {
                List<String> days = new ArrayList<>();
                days.add(model.getDay());
                model.setFirstImage(IMAGE_BASE_URL);
                exercises.add(new Exercise(model.getName(), String.valueOf(model.getSets()), model.getReps(),
                        model.getDuration(), model.getImages(), days, model.getBodyPart(), model.getEquipment(), model.getId(), model.getTarget(), model.getPrimaryMuscles(),model.getSecondaryMuscles(), model.getInstructions()));
            }
        } catch (IOException e) {
            Log.e("WorkoutWizardActivity", "Error reading exercises.json", e);
            return null;
        }

        return exercises;
    }
    public static  ExerciseModel findMatchingExercise(String aiExerciseName,  Map<String, ExerciseModel> allExercisesMap) {
        if (allExercisesMap == null || allExercisesMap.isEmpty()) {
            return null;
        }
        String normalizedAIExerciseName = normalizeString(aiExerciseName);
        for(Map.Entry<String, ExerciseModel> entry : allExercisesMap.entrySet()){
            String databaseExerciseName = normalizeString(entry.getKey());
            if(databaseExerciseName.contains(normalizedAIExerciseName) || normalizedAIExerciseName.contains(databaseExerciseName)){
                return entry.getValue();
            }
        }
        return null;
    }


    public static  ExerciseModel getExerciseModel(Exercise exercise) {
        ExerciseModel model = new ExerciseModel();
        model.setName(exercise.getName());
        model.setDuration(exercise.getDuration());
        model.setImages(exercise.getImages());//Setting images here
        model.setReps(exercise.getReps());
        model.setSets(exercise.getSets());
        if(exercise.getDays() != null && !exercise.getDays().isEmpty()){
            model.setDay(exercise.getDays().get(0));
        }
        model.setBodyPart(exercise.getBodyPart());
        model.setEquipment(exercise.getEquipment());
        model.setId(exercise.getId());
        model.setTarget(exercise.getTarget());
        model.setPrimaryMuscles(exercise.getPrimaryMuscles());
        model.setSecondaryMuscles(exercise.getSecondaryMuscles());
        model.setInstructions(exercise.getInstructions());
        return model;
    }
    private static String normalizeString(String input){
        if (input == null) return "";
        return input.trim().toLowerCase();
    }

    /**
     * Resolves a theme attribute and sets it as the background of a View.
     * Particularly useful for attributes that reference Drawables or Colors,
     * like ?android:attr/windowBackground or ?attr/colorSurface.
     *
     * @param view The View to set the background on.
     * @param attributeId The Android resource ID of the theme attribute (e.g., android.R.attr.windowBackground).
     * @return true if the attribute was resolved and applied, false otherwise.
     */
    public static boolean setBackgroundFromThemeAttribute(View view, int attributeId) {
        if (view == null) {
            return false;
        }
        Context context = view.getContext();
        TypedValue typedValue = new TypedValue();

        // Resolve the theme attribute
        boolean resolved = context.getTheme().resolveAttribute(attributeId, typedValue, true);

        if (resolved) {
            // The attribute was resolved successfully
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                // The resolved value is a direct color integer (e.g., #RRGGBB or #AARRGGBB)
                view.setBackgroundColor(typedValue.data);
                return true;
            } else {
                // The resolved value is likely a reference to a resource (Drawable or Color Resource)
                // Use getDrawable to correctly load the resource from its ID
                if (typedValue.resourceId != 0) {
                    try {
                        Drawable drawable = ContextCompat.getDrawable(context, typedValue.resourceId);
                        if (drawable != null) {
                            view.setBackground(drawable); // Set the Drawable background
                            return true;
                        }
                    } catch (Exception e) {
                        // Handle potential errors if the resource ID is invalid or cannot be loaded
                        e.printStackTrace();
                    }
                }
                // If resourceId is 0 and not a direct color, it might be a complex type not handled here,
                // or the attribute resolved but points to nothing loadable as a background.
            }
        }
        // Attribute did not resolve, or resource loading failed
        return false;
    }

    /**
     * Checks if the current configuration's UI mode is set to night (dark mode).
     * This reflects the actual theme currently applied to the given Context.
     *
     * @param context The Context whose configuration should be checked (e.g., Activity context).
     * @return true if the current UI mode is night, false otherwise.
     */
    public static boolean isDarkModeActive(Context context) {
        if (context == null) {
            // Cannot determine UI mode without a valid context
            return false;
        }
        int nightModeFlags = context.getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    // Helper function to convert dp to pixels
    public static int dpToPx(Context ctx, int dp) {
        return (int) (dp * ctx.getResources().getDisplayMetrics().density);
    }

    /***************** Fetch user balance ********************/
    public interface BalanceFetchListener {
        void onBalanceFetched(double balance);
        void onBalanceFetchFailed(String errorMessage); // Or pass an Exception
    }

    public static void fetchUserBalance(Context ctx, String username, boolean fullBalance, BalanceFetchListener listener) {

        if (username.isEmpty()) {
            // Handle invalid input immediately and notify the listener
            if (listener != null) {
                listener.onBalanceFetchFailed("Username is empty.");
            }
            return;
        }

        String balanceUrl = Utils.apiUrl(ctx) + ctx.getString(R.string.user_balance_api_url) + username
                + (fullBalance ? "?fullBalance=1" : "");

        RequestQueue queue = Volley.newRequestQueue(ctx);

        JsonObjectRequest balanceRequest = new JsonObjectRequest(
                Request.Method.GET, balanceUrl, null,
                response -> {
                    // This block runs when the server responds successfully
                    try {
                        double balance = response.getDouble("tokens");
                        Log.d(TAG, "Balance fetched: " + balance);
                        // Pass the fetched balance back via the listener
                        if (listener != null) {
                            listener.onBalanceFetched(balance);
                        }

                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error fetching balance", e);
                        // Notify the listener about the parsing error
                        if (listener != null) {
                            listener.onBalanceFetchFailed("Failed to parse balance data.");
                        }
                    }
                },
                error -> {
                    // This block runs if there's a network or server error
                    Log.e(TAG, "Volley error fetching balance", error);
                    String errorMessage = "Network error fetching balance.";
                    if (error != null && error.getMessage() != null) {
                        errorMessage += " " + error.getMessage();
                    }
                    // Notify the listener about the error
                    if (listener != null) {
                        listener.onBalanceFetchFailed(errorMessage);
                    }
                }
        );

        // Add balance request to be processed (asynchronously)
        queue.add(balanceRequest);

    }

}
