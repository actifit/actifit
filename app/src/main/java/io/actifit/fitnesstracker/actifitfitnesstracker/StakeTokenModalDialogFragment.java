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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;

import com.android.volley.RequestQueue;
import com.squareup.picasso.Picasso;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;

import static android.content.Context.MODE_PRIVATE;
import static android.view.View.VISIBLE;
import static com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread;

public class StakeTokenModalDialogFragment extends DialogFragment {
    public Context ctx;
    float userBalance;
    RequestQueue queue;
    TextView stakeNote, curBalance;
    private boolean heToken = false;
    String symbol, icon, unstakePeriod;
    int mode;//0 is stake. 1 is unstake

    public StakeTokenModalDialogFragment() {

    }

    public StakeTokenModalDialogFragment(Context ctx, String userBalance, RequestQueue queue,
                                         int mode, boolean heToken, String symbol, String icon,
                                         String unstakePeriod) {
        this.ctx = ctx;
        this.queue = queue;
        this.mode = mode;
        this.heToken = heToken;
        this.symbol = symbol;
        this.icon = icon;
        this.unstakePeriod = unstakePeriod;
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
        View view = inflater.inflate(R.layout.stake_token_modal, container, false);

        renderContent(view);

        return view;
    }

    private void renderContent(View view){

        TextView title = view.findViewById(R.id.title);

        if (mode == 1){
            title.setText(getString(R.string.unstake));
        }

        EditText recipient = view.findViewById(R.id.recipient);

        EditText amount = view.findViewById(R.id.amount);

        EditText memo = view.findViewById(R.id.memo);

        Button maxButton = view.findViewById(R.id.max_btn);

        ProgressBar taskProgress = view.findViewById(R.id.loader);

        //by default set recipient as own self in staking
        recipient.setText(MainActivity.username);

        try {
            ImageView tokenIcon = view.findViewById(R.id.token_icon);
            Handler uiHandler = new Handler(Looper.getMainLooper());
            uiHandler.post(() -> {
                Picasso.get().load(icon).into(tokenIcon);
            });
        }catch(Exception ex){
            ex.printStackTrace();
        }

        //token = view.findViewById(R.id.token);
        stakeNote = view.findViewById(R.id.send_note);
        curBalance = view.findViewById(R.id.cur_balance);

        stakeNote.setText(getString(R.string.stake_token_note).replace("_PERIOD_", unstakePeriod));

        DecimalFormat decimalFormat = new DecimalFormat("#,###,##0.000");

        curBalance.setText(String.format("%s %s", decimalFormat.format(userBalance), symbol));

        /*if (isHeToken()){
            exchangeNote.setVisibility(View.GONE);
        }else{
            exchangeNote.setVisibility(VISIBLE);
        }*/

        Button stakeButton = view.findViewById(R.id.proceed_stake_btn);
        if (mode == 1) {
            stakeButton.setText(getString(R.string.unstake));
        }

        maxButton.setOnClickListener(v->{
            amount.setText(userBalance+"");
        });

        stakeButton.setOnClickListener(v ->{

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
            //String tokenName = token.getSelectedItem().toString();

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
                    JSONObject cstm_params = new JSONObject();

                    //power up
                    String op_name = "transfer_to_vesting";
                    cstm_params.put("from", MainActivity.username);
                    cstm_params.put("to", recipientVal);
                    DecimalFormat decimalFormatUp = new DecimalFormat("0.000");//precision 3
                    cstm_params.put("amount", decimalFormatUp.format(amountVal) + " HIVE");

                    //power down
                    if (mode == 1) {
                        op_name = "withdraw_vesting";

                        cstm_params = new JSONObject();
                        cstm_params.put("account", MainActivity.username);
                        //convert amount to VESTS
                        Double vests = WalletActivity.powerToVests(WalletActivity.hiveChainInfo, Double.parseDouble(amountVal+""));
                        System.out.println(">>>>> vests:"+vests);
                        //if (1==1) return;
                        DecimalFormat decimalFormat2 = new DecimalFormat("0.000000");//precision 6
                        cstm_params.put("vesting_shares", decimalFormat2.format(vests)+" VESTS");

                    }

                    //cstm_params.put("json", json_op_details);

                    //support for HE token transfers
                    if (isHeToken()){
                        op_name = "custom_json";

                        cstm_params = new JSONObject();

                        JSONArray required_auths= new JSONArray();
                        required_auths.put(MainActivity.username);

                        JSONArray required_posting_auths = new JSONArray();

                        String action = "stake";
                        if (mode == 1){
                            action = "unstake";
                        }

                        cstm_params.put("required_auths", required_auths);
                        cstm_params.put("required_posting_auths", required_posting_auths);
                        cstm_params.put("id", getString(R.string.hive_engine_custom_param_network));
                        //cstm_params.put("json", json_op_details);
                        cstm_params.put("json",
                                "{\"contractName\": \"tokens\" , " +
                                        "\"contractAction\": \""+action+"\" , " +
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
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Log.e(MainActivity.TAG, "error writing custom json:"+errorMessage);
                                runOnUiThread(() -> {
                                    Toast.makeText(ctx, ctx.getString(R.string.trx_success), Toast.LENGTH_LONG).show();
                                    taskProgress.setVisibility(View.GONE);
                                    dismiss();
                                });

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

    public void setHeToken(boolean heToken, String symbol) {
        this.heToken = heToken;
        this.symbol = symbol;
        System.out.println("<<<<<symbol:"+symbol);
    }
}
