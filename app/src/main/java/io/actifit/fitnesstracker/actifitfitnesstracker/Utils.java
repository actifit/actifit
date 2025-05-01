package io.actifit.fitnesstracker.actifitfitnesstracker;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.ExifInterface;
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

public class Utils {

    public static JSONArray extraVotesList;

    //Compression Based Update

    public static void createFile(Bitmap bitmap, Context context, Uri srcUri, File dstFile) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(srcUri);
            if (inputStream == null) return;

            OutputStream outputStream = new FileOutputStream(dstFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream);

            //special copyexif from stream to file
            copyExif(inputStream, dstFile.getAbsolutePath());

            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void copyExif(InputStream originalPath, String newPath) throws IOException {

        String[] attributes = new String[]
                {
                        ExifInterface.TAG_DATETIME,
                        ExifInterface.TAG_DATETIME_DIGITIZED,
                        ExifInterface.TAG_EXPOSURE_TIME,
                        ExifInterface.TAG_FLASH,
                        ExifInterface.TAG_FOCAL_LENGTH,
                        ExifInterface.TAG_GPS_ALTITUDE,
                        ExifInterface.TAG_GPS_ALTITUDE_REF,
                        ExifInterface.TAG_GPS_DATESTAMP,
                        ExifInterface.TAG_GPS_LATITUDE,
                        ExifInterface.TAG_GPS_LATITUDE_REF,
                        ExifInterface.TAG_GPS_LONGITUDE,
                        ExifInterface.TAG_GPS_LONGITUDE_REF,
                        ExifInterface.TAG_GPS_PROCESSING_METHOD,
                        ExifInterface.TAG_GPS_TIMESTAMP,
                        ExifInterface.TAG_MAKE,
                        ExifInterface.TAG_MODEL,
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.TAG_SUBSEC_TIME,
                        ExifInterface.TAG_WHITE_BALANCE
                };

        ExifInterface oldExif = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            oldExif = new ExifInterface(originalPath);
        }
        ExifInterface newExif = new ExifInterface(newPath);

        if (attributes.length > 0) {
            for (int i = 0; i < attributes.length; i++) {
                String value = oldExif.getAttribute(attributes[i]);
                if (value != null)
                    newExif.setAttribute(attributes[i], value);
            }
            newExif.saveAttributes();
        }
    }

    public static void uploadFile(Bitmap bitmap, Uri fileUri, EditText textBox,
                                  Context ctx, Activity activity) {
        final ProgressDialog uploadProgress;
        if (fileUri != null) {
            // Create unique image file name
            final String fileName = UUID.randomUUID().toString();
            final File file = new File(ctx.getFilesDir(), fileName);

            // Create file from URI
            createFile(bitmap, ctx, fileUri, file);

            // Show progress dialog
            uploadProgress = new ProgressDialog(activity);
            uploadProgress.setMessage(ctx.getString(R.string.start_upload));

            activity.runOnUiThread(() -> {
                uploadProgress.show();
            });

            // Prepare the file to be uploaded
            RequestBody requestFile = RequestBody.create(MediaType.parse(ctx.getContentResolver().getType(fileUri)), file);
            MultipartBody.Part body = MultipartBody.Part.createFormData("image", file.getName(), requestFile);
            String token = ctx.getString(R.string.media_upload_key); // Replace with your token

            // Create Retrofit client and call API
            ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
            Call<ResponseBody> call = apiService.uploadImage(body, token);
            call.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull retrofit2.Response<ResponseBody> response) {
                    activity.runOnUiThread(() -> {
                        if (uploadProgress != null && uploadProgress.isShowing()) {
                            uploadProgress.dismiss();
                        }
                        if (response.isSuccessful()) {
                            Toast.makeText(ctx, ctx.getString(R.string.upload_complete), Toast.LENGTH_SHORT).show();
                            String fullImgUrl = ctx.getString(R.string.actifit_usermedia_url) + fileName;
                            String imgMarkdownText = "![](" + fullImgUrl + ")";
                            // Append the uploaded image URL to the text as markdown
                            int start = Math.max(textBox.getSelectionStart(), 0);
                            int end = Math.max(textBox.getSelectionEnd(), 0);
                            textBox.getText().replace(Math.min(start, end), Math.max(start, end),
                                    imgMarkdownText, 0, imgMarkdownText.length());
                            file.delete();
                        } else {
                            showError();
                        }
                    });
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    activity.runOnUiThread(() -> {
                        if (uploadProgress != null && uploadProgress.isShowing()) {
                            uploadProgress.dismiss();
                        }
                        showError();
                    });
                }

                private void showError() {
                    activity.runOnUiThread(() -> {
                        Toast toast = Toast.makeText(ctx, ctx.getString(R.string.upload_failed), Toast.LENGTH_SHORT);
                        TextView v = toast.getView().findViewById(android.R.id.message);
                        v.setTextColor(Color.RED);
                        toast.show();
                    });
                }
            });
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
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            //if we need to close current Activity
                            if (closeScreen) {
                                //close current screen
                                Log.d(MainActivity.TAG,">>>Finish");
                                currentActivity.finish();
                            }
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

}
