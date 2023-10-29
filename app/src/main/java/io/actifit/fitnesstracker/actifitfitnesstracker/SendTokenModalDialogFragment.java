package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.squareup.picasso.Picasso;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;

import static android.content.Context.MODE_PRIVATE;
import static android.view.View.VISIBLE;
import static com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread;

public class SendTokenModalDialogFragment extends DialogFragment {
    public Context ctx;
    float userBalance;
    RequestQueue queue;
    Spinner token;
    LinearLayout tokenContainer;
    TextView exchangeNote;
    private boolean heToken = false;
    String symbol, icon;

    float mainTokenBalance, secTokenBalance;
    String mainTokenSymbol, secTokenSymbol = "HBD";

    public SendTokenModalDialogFragment() {

    }

    public SendTokenModalDialogFragment(Context ctx, String userBalance, RequestQueue queue) {
        this.ctx = ctx;
        this.queue = queue;

        try {
            if (userBalance !="") {
                //cleanup user balance from formatting, and parse
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

        renderContent(view);

        return view;
    }

    private void renderContent(View view){

        EditText recipient = view.findViewById(R.id.recipient);

        EditText amount = view.findViewById(R.id.amount);

        EditText memo = view.findViewById(R.id.memo);

        TextView balance = view.findViewById(R.id.cur_balance);

        Button maxButton = view.findViewById(R.id.max_btn);

        ProgressBar taskProgress = view.findViewById(R.id.loader);

        token = view.findViewById(R.id.token);
        exchangeNote = view.findViewById(R.id.send_note);
        tokenContainer = view.findViewById(R.id.tokenContainer);
        //balance.setText(userBalance+"");
        DecimalFormat decimalFormat = new DecimalFormat("#,###,##0.000");

        balance.setText(String.format("%s %s", decimalFormat.format(userBalance), symbol));

        if (isHeToken()){
            tokenContainer.setVisibility(View.GONE);
            exchangeNote.setVisibility(View.GONE);
        }else{
            tokenContainer.setVisibility(VISIBLE);
            exchangeNote.setVisibility(VISIBLE);
        }
        if (!icon.equals("")) {
            //placeholder or error fallback
            LetterDrawable placeholderDrawable = new LetterDrawable(symbol.substring(0, 1));

            try {
                ImageView tokenIcon = view.findViewById(R.id.token_icon);
                Handler uiHandler = new Handler(Looper.getMainLooper());
                uiHandler.post(() -> {
                    Picasso.get().load(icon)
                            .placeholder(placeholderDrawable)
                            .error(placeholderDrawable)
                            .into(tokenIcon);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }


        Button sendButton = view.findViewById(R.id.proceed_send_btn);

        maxButton.setOnClickListener(v->{
            amount.setText(userBalance+"");
        });

        //capture change event
        token.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Code to execute when an item is selected
                String selectedItem = parent.getItemAtPosition(position).toString();
                //if (selectedItem)
                //System.out.println(">>>>>"+selectedItem);
                if (selectedItem.equals(secTokenSymbol)){
                    userBalance = secTokenBalance;
                    symbol = secTokenSymbol;
                }else{
                    userBalance = mainTokenBalance;
                    symbol = mainTokenSymbol;
                }
                DecimalFormat decimalFormat = new DecimalFormat("#,###,##0.000");

                balance.setText(String.format("%s %s", decimalFormat.format(userBalance), symbol));
                // Perform actions with the selected item
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Code to execute when nothing is selected
            }
        });

        sendButton.setOnClickListener(v ->{

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

                    //support for HE token transfers
                    if (isHeToken()){
                        op_name = "custom_json";

                        cstm_params = new JSONObject();

                        JSONArray required_auths= new JSONArray();
                        required_auths.put(MainActivity.username);

                        JSONArray required_posting_auths = new JSONArray();

                        cstm_params.put("required_auths", required_auths);
                        cstm_params.put("required_posting_auths", required_posting_auths);
                        cstm_params.put("id", getString(R.string.hive_engine_custom_param_network));
                        //cstm_params.put("json", json_op_details);
                        cstm_params.put("json",
                                "{\"contractName\": \"tokens\" , " +
                                        "\"contractAction\": \"transfer\" , " +
                                        "\"contractPayload\": {" +
                                            "\"symbol\": \"" + symbol + "\", " +
                                            "\"to\": \"" + recipientVal + "\"," +
                                            "\"quantity\": \"" + String.format("%.3f", amountVal) + "\"," +
                                            "\"memo\": \"" + memoVal + "\"}}");
                    }

                    System.out.println(">>>>>>>>>>>>>>custom_params:"+cstm_params.toString());
                    System.out.println(">>>>>>>>>>>>>>op_name"+op_name.toString());

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

    public boolean isHeToken() {
        return heToken;
    }

    public void setHeToken(boolean heToken, String symbol, String icon) {
        this.heToken = heToken;
        this.symbol = symbol;
        this.icon = icon;
        System.out.println("<<<<<symbol:"+symbol);
    }

    public void setSecToken(float secTokenBalance, String secTokenSymbol){
        this.mainTokenBalance = this.userBalance;
        this.mainTokenSymbol = this.symbol;
        this.secTokenBalance = secTokenBalance;
        this.secTokenSymbol = secTokenSymbol;
    }
}
