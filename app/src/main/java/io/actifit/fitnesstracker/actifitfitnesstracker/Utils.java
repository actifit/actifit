package io.actifit.fitnesstracker.actifitfitnesstracker;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.github.rjeschke.txtmark.Processor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread;

public class Utils {

    public static JSONArray extraVotesList;

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
                return mins + " min(s)";
            } else if (hours < 24) {
                return hours + " hour(s)";
            } else if (days < 7) {
                return days + " day(s)";
            } else if (weeks < 4) {
                return weeks + " week(s)";
            } else if (months < 12) {
                return months + " month(s)";
            } else {
                return years + " year(s)";
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
    public static Boolean userVotedPost(String voter, JSONArray actVotes , int post_id){

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
                                final APIResponseListener listener) {

        RequestQueue queue = Volley.newRequestQueue(ctx);

        if (user.equals("") || op_name.equals("") || cstm_params == null) {

            Log.e(MainActivity.TAG, "missing params");
            runOnUiThread(() -> {
                taskProgress.setVisibility(View.GONE);
            });
        } else {
            try {

                JSONArray operation = new JSONArray();
                operation.put(0, op_name);
                operation.put(1, cstm_params);

                String bcastUrl = ctx.getString(R.string.perform_trx_link);
                bcastUrl += user +
                            "&operation=[" + operation + "]" +
                            "&bchain=HIVE";
                ;


                //send out transaction
                JsonObjectRequest transRequest = new JsonObjectRequest(Request.Method.GET,
                        bcastUrl, null,
                        response -> runOnUiThread(() -> {
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
    //to handle multiple successive calls
    public interface APIResponseListener {
        //void onResponse(byte[] serializedTransaction);
        void onError(String errorMessage);

        //void onResponse(JSONObject result);

        void onResponse(boolean success);
    }
}
