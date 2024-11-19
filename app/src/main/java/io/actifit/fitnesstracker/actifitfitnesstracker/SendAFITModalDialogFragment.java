package io.actifit.fitnesstracker.actifitfitnesstracker;

import static android.content.Context.MODE_PRIVATE;
import static android.view.View.VISIBLE;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

public class SendAFITModalDialogFragment extends DialogFragment {
    public Context ctx;
    ListView socialView;
    float userBalance;
    RequestQueue queue;

    public SendAFITModalDialogFragment() {

    }

    public SendAFITModalDialogFragment(Context ctx, String userBalance, RequestQueue queue) {
        this.ctx = ctx;
        this.queue = queue;
        //this.extraVotesList = extraVotesList;
        try {
            if (userBalance !="") {
                this.userBalance = Float.parseFloat(userBalance.replace(",", ""));
            }
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        //dialog.getWindow().requestFeature(STYLE_NO_TITLE);
        return dialog;
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.send_afit_modal, container, false);
        //View voteModalLayout = LayoutInflater.from(ctx).inflate(R.layout.vote_modal, null);
        renderContent(view);

        return view;
    }

    private void renderContent(View view){

        //AlertDialog.Builder voteDialogBuilder = new AlertDialog.Builder(ctx);
        //View voteModalLayout = LayoutInflater.from(ctx).inflate(R.layout.vote_modal, null);
        //View voteModalLayout = view;
        EditText recipient = view.findViewById(R.id.recipient);

        EditText amount = view.findViewById(R.id.amount);

        EditText note = view.findViewById(R.id.note);

        ProgressBar taskProgress = view.findViewById(R.id.loader);

        //AlertDialog pointer = null;

        Button sendButton = view.findViewById(R.id.proceed_send_btn);

        sendButton.setOnClickListener(v ->{

            //proceed_vote_btn.setOnClickListener(subview -> {
            /*if (!TextUtils.isDigitsOnly(amount.getText())) {
                Toast.makeText(ctx, ctx.getString(R.string.vote_percent_incorrect), Toast.LENGTH_SHORT).show();
                return;
            }*/
            if (String.valueOf(amount.getText()).trim().equals("")){
                Toast.makeText(ctx, ctx.getString(R.string.send_afit_range_error), Toast.LENGTH_SHORT).show();
                return;
            }
            float amountVal = Float.parseFloat(String.valueOf(amount.getText()));
            if (amountVal < 0 || amountVal > 10000) {
                Toast.makeText(ctx, ctx.getString(R.string.send_afit_range_error), Toast.LENGTH_SHORT).show();
                return;
            }


            String recipientVal = recipient.getText().toString();

            if (recipientVal.trim() == ""){
                Toast.makeText(ctx, ctx.getString(R.string.recipient_empty), Toast.LENGTH_SHORT).show();
                return;
            }

            if (recipientVal.trim().equals(MainActivity.username)){
                Toast.makeText(ctx, ctx.getString(R.string.recipient_same_user), Toast.LENGTH_SHORT).show();
                return;
            }

            if (amountVal > userBalance){
                Toast.makeText(ctx, ctx.getString(R.string.within_balance_error), Toast.LENGTH_SHORT).show();
                return;
            }
            final SharedPreferences sharedPreferences = ctx.getSharedPreferences("actifitSets",MODE_PRIVATE);
            String fundsPassVal = sharedPreferences.getString("fundsPass", "");

            if (fundsPassVal.equals("")){
                Toast.makeText(ctx, ctx.getString(R.string.funds_pass_error), Toast.LENGTH_SHORT).show();
                return;
            }

            String noteVal = note.getText().toString();

            taskProgress.setVisibility(VISIBLE);

            Activity activity = getActivity();

            //run on its own thread to avoid hiccups
            Thread th = new Thread(() -> {
                //runOnUiThread(() -> {
                try {

                    String sendFundsUrl = Utils.apiUrl(ctx)+getString(R.string.send_afit_url).replace("USERNAME", MainActivity.username)
                            .replace("TARGET",recipientVal)
                            .replace("VAL",amountVal+"")
                            .replace("FUNDSP",fundsPassVal)
                            .replace("USERNOTE", noteVal);

                    // Request the rank of the user while expecting a JSON response
                    JsonObjectRequest sendFundsReq = new JsonObjectRequest
                            (Request.Method.GET, sendFundsUrl, null, new Response.Listener<JSONObject>() {
                                @Override
                                public void onResponse(JSONObject response) {

                                    // Display the result
                                    try {
                                        if (response.has("status")) {
                                            String status = response.getString("status");
                                            if (status.equals("Success")){
                                                //also write query to chain
                                                try {
                                                    String op_name = "custom_json";

                                                    JSONObject cstm_params = new JSONObject();

                                                    JSONArray required_auths= new JSONArray();
                                                    JSONArray required_posting_auths = new JSONArray();
                                                    required_posting_auths.put(MainActivity.username);

                                                    //cstm_params.put("required_auths", "[]");
                                                    cstm_params.put("required_auths", required_auths);
                                                    cstm_params.put("required_posting_auths", required_posting_auths);
                                                    cstm_params.put("id", "actifit");
                                                    //cstm_params.put("json", json_op_details);
                                                    cstm_params.put("json",
                                                            "{\"action\": \"Tip\" , " +
                                                                    "\"amount\": \"" + amountVal + "\", " +
                                                                    "\"recipient\": \"" + recipientVal + "\"," +
                                                                    "\"note\": \"" + noteVal + "\"}");

                                                    //no need to wait for response
                                                    Utils.queryAPI(getContext(), MainActivity.username, op_name, cstm_params, taskProgress,
                                                            new Utils.APIResponseListener() {
                                                                @Override
                                                                public void onResponse(boolean success) {
                                                                    Log.e(MainActivity.TAG, "custom json complete:"+success);
                                                                    activity.runOnUiThread(() -> {
                                                                        Toast.makeText(ctx, ctx.getString(R.string.trx_success), Toast.LENGTH_LONG).show();
                                                                        taskProgress.setVisibility(View.GONE);
                                                                        dismiss();
                                                                    });
                                                                    // Step 5: Perform another API call
                                                                    /*runOnUiThread(() -> {
                                                                        taskProgress.setVisibility(View.GONE);
                                                                        if (success) {

                                                                        } else {
                                                                            Toast.makeText(ctx, ctx.getString(R.string.vote_error), Toast.LENGTH_LONG).show();
                                                                        }
                                                                    });*/
                                                                }

                                                                @Override
                                                                public void onError(String errorMessage) {
                                                                    Log.e(MainActivity.TAG, "error writing custom json:"+errorMessage);
                                                                    activity.runOnUiThread(() -> {
                                                                        Toast.makeText(ctx, ctx.getString(R.string.trx_success), Toast.LENGTH_LONG).show();
                                                                        taskProgress.setVisibility(View.GONE);
                                                                        dismiss();
                                                                    });
                                                                    // Handle the error
                                                                    /*runOnUiThread(() -> {
                                                                        //taskProgress.setVisibility(View.GONE);
                                                                        //Toast.makeText(ctx, ctx.getString(R.string.vote_error), Toast.LENGTH_LONG).show();
                                                                    });*/
                                                                }
                                                            }, activity);

                                                } catch (Exception exc) {
                                                    exc.printStackTrace();
                                                }
                                            }
                                        }
                                    } catch (Exception exc) {
                                        exc.printStackTrace();
                                        Toast.makeText(ctx, ctx.getString(R.string.trx_error), Toast.LENGTH_LONG).show();
                                    }
                                    //taskProgress.setVisibility(View.GONE);
                                }}, new Response.ErrorListener(){

                                @Override
                                    public void onErrorResponse (VolleyError error){
                                    Log.e(MainActivity.TAG, "error sending funds"+error.getMessage());
                                        // Handle the error
                                        activity.runOnUiThread(() -> {
                                            taskProgress.setVisibility(View.GONE);
                                            Toast.makeText(ctx, ctx.getString(R.string.trx_error), Toast.LENGTH_LONG).show();
                                        });

                                    }
                            });
                    queue.add(sendFundsReq);
                } catch (Exception exc) {
                    exc.printStackTrace();
                }
            });
            th.start();
        });


        Button closeButton = view.findViewById(R.id.close_btn);
        closeButton.setOnClickListener(v -> {
            dismiss(); // Dismiss the DialogFragment when the close button is clicked
        });
    }
}
