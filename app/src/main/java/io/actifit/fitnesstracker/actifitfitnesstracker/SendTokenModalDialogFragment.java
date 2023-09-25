package io.actifit.fitnesstracker.actifitfitnesstracker;

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
import android.widget.Spinner;
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

import static android.content.Context.MODE_PRIVATE;
import static android.view.View.VISIBLE;
import static com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread;

public class SendTokenModalDialogFragment extends DialogFragment {
    public Context ctx;
    ListView socialView;
    float userBalance;
    RequestQueue queue;
    Spinner token;

    public SendTokenModalDialogFragment() {

    }

    public SendTokenModalDialogFragment(Context ctx, String userBalance, RequestQueue queue) {
        this.ctx = ctx;
        this.queue = queue;
        //this.extraVotesList = extraVotesList;
        try {
            if (userBalance !="") {
                this.userBalance = Float.parseFloat(userBalance);
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
        View view = inflater.inflate(R.layout.send_token_modal, container, false);
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

        EditText memo = view.findViewById(R.id.memo);

        ProgressBar taskProgress = view.findViewById(R.id.loader);

        token = view.findViewById(R.id.token);

        //AlertDialog pointer = null;

        Button sendButton = view.findViewById(R.id.proceed_send_btn);

        sendButton.setOnClickListener(v ->{

            //proceed_vote_btn.setOnClickListener(subview -> {
            /*if (!TextUtils.isDigitsOnly(amount.getText())) {
                Toast.makeText(ctx, ctx.getString(R.string.vote_percent_incorrect), Toast.LENGTH_SHORT).show();
                return;
            }*/

            final SharedPreferences sharedPreferences = ctx.getSharedPreferences("actifitSets",MODE_PRIVATE);
            String activKeyVal = sharedPreferences.getString("actvKey", "");

            if (activKeyVal.equals("")){
                Toast.makeText(ctx, ctx.getString(R.string.activ_key_error), Toast.LENGTH_SHORT).show();
                return;
            }


            if (String.valueOf(amount.getText()).trim().equals("")){
                Toast.makeText(ctx, ctx.getString(R.string.send_token_range_error), Toast.LENGTH_SHORT).show();
                return;
            }

            //which token is selected
            String tokenName = token.getSelectedItem().toString();

            float amountVal = Float.parseFloat(String.valueOf(amount.getText()));
            if (amountVal < 0 || amountVal > userBalance) {
                Toast.makeText(ctx, ctx.getString(R.string.send_token_range_error), Toast.LENGTH_SHORT).show();
                return;
            }


            String recipientVal = recipient.getText().toString();

            if (recipientVal.trim() == ""){
                Toast.makeText(ctx, ctx.getString(R.string.recipient_empty), Toast.LENGTH_SHORT).show();
                return;
            }

            String memoVal = memo.getText().toString();

            taskProgress.setVisibility(VISIBLE);

            //run on its own thread to avoid hiccups
            Thread th = new Thread(() -> {
                //runOnUiThread(() -> {
                try {


                    String op_name = "transfer";

                    JSONObject cstm_params = new JSONObject();

                    cstm_params.put("from", MainActivity.username);
                    cstm_params.put("to", recipientVal);
                    cstm_params.put("amount", String.format("%.3f", amountVal) + " " + tokenName);
                    cstm_params.put("memo", memoVal);
                    //cstm_params.put("json", json_op_details);

                    //no need to wait for response
                    Utils.queryAPIPost(getContext(), MainActivity.username, activKeyVal,
                            op_name, cstm_params, taskProgress,
                        new Utils.APIResponseListener() {
                            @Override
                            public void onResponse(boolean success) {
                                Log.e(MainActivity.TAG, "custom json complete:"+success);
                                runOnUiThread(() -> {

                                    if (success) {

                                        Toast.makeText(ctx, ctx.getString(R.string.trx_success), Toast.LENGTH_LONG).show();
                                        taskProgress.setVisibility(View.GONE);
                                        dismiss();
                                    }else{
                                        Toast.makeText(ctx, ctx.getString(R.string.trx_error), Toast.LENGTH_LONG).show();
                                        taskProgress.setVisibility(View.GONE);
                                    }
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
                                runOnUiThread(() -> {
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
                        });
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
