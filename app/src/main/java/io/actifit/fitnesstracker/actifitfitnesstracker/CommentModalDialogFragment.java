package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.mittsu.markedview.MarkedView;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import static android.view.View.VISIBLE;
import static com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread;

public class CommentModalDialogFragment extends DialogFragment {
    public Context ctx;
    SingleHivePostModel postEntry;

    public CommentModalDialogFragment() {

    }

    public CommentModalDialogFragment(Context ctx, SingleHivePostModel postEntry) {
        this.ctx = ctx;
        this.postEntry = postEntry;
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
        View view = inflater.inflate(R.layout.comment_modal, container, false);
        //View voteModalLayout = LayoutInflater.from(ctx).inflate(R.layout.vote_modal, null);
        renderCommentContent(view);


        return view;
    }

    private void renderCommentContent(View view){

        AlertDialog.Builder replyDialogBuilder = new AlertDialog.Builder(PostAdapter.keyMainContext);
        //AlertDialog.Builder replyDialogBuilder = new AlertDialog.Builder(mainContext);
        //final View replyModalLayout = LayoutInflater.from(ctx).inflate(R.layout.comment_modal, null);
        TextView author_txt = view.findViewById(R.id.reply_author);
        //MarkedView mdView = view.findViewById(R.id.md_view);
        author_txt.setText("@"+postEntry.author+" 's content");

        EditText replyText = view.findViewById(R.id.reply_text);
        MarkedView mdReplyView = view.findViewById(R.id.reply_preview);


        //Button proceedCommentBtn = replyModalLayout.findViewById(R.id.proceed_comment_btn);

        //mdReplyView.setMDText(replyText.getText().toString());
        //default content for preview
        mdReplyView.setMDText(ctx.getString(R.string.comment_preview_lbl));


        AlertDialog pointer;

        //proceed with positive action
        Button proceedCommentBtn = view.findViewById(R.id.proceed_comment_btn);
        proceedCommentBtn.setOnClickListener(v ->{
        //DialogInterface.OnClickListener handleCommentAction = (dialogInterface, which) -> {

            String commentStr = replyText.getText().toString();
            if (commentStr.length() < 1){
                Toast.makeText(ctx, ctx.getString(R.string.no_empty_comment),Toast.LENGTH_SHORT).show();
                return;
            }
            ProgressBar taskProgress = view.findViewById(R.id.loader);

            taskProgress.setVisibility(VISIBLE);

            //run on its own thread to avoid hiccups
            Thread trxThread = new Thread(() -> {
                try {

                    String op_name = "comment";

                    String comment_perm = MainActivity.username.replace(".", "-") + "-re-" + postEntry.author.replace(".", "-") + "-" + postEntry.permlink + new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).format(new Date());
                    comment_perm = comment_perm.replaceAll("[^a-zA-Z0-9]+", "").toLowerCase();

                    JSONObject cstm_params = new JSONObject();
                    cstm_params.put("author", MainActivity.username);
                    cstm_params.put("permlink", comment_perm);
                    cstm_params.put("title", "");
                    //include comment alongside comment source (android app)
                    cstm_params.put("body", replyText.getText() + getString(R.string.comment_note));
                    cstm_params.put("parent_author", postEntry.author);
                    cstm_params.put("parent_permlink", postEntry.permlink);

                    JSONObject metaData = new JSONObject();

                    metaData.put("tags","['hive-193552', 'actifit']");
                    metaData.put("app","actifit");

                    //grab app version number
                    try {
                        PackageInfo pInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
                        String version = pInfo.versionName;
                        metaData.put("appVersion",version);
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }

                    cstm_params.put("json_metadata", metaData.toString());

                    Utils.queryAPI(getContext(), MainActivity.username, op_name, cstm_params, taskProgress,
                            new Utils.APIResponseListener() {
                                @Override
                                public void onResponse(boolean success) {
                                    runOnUiThread(() -> {
                                        taskProgress.setVisibility(View.GONE);
                                        Log.e(MainActivity.TAG, "response");
                                        if (success) {
                                            Toast.makeText(ctx, ctx.getString(R.string.comment_success), Toast.LENGTH_LONG).show();
                                            dismiss();
                                        } else {
                                            Toast.makeText(ctx, ctx.getString(R.string.comment_error), Toast.LENGTH_LONG).show();
                                        }
                                    });

                                }

                                @Override
                                public void onError(String errorMessage) {
                                    // Handle the error
                                    runOnUiThread(() -> {
                                        taskProgress.setVisibility(View.GONE);
                                        Toast.makeText(ctx, ctx.getString(R.string.comment_error), Toast.LENGTH_LONG).show();
                                    });
                                    Log.e(MainActivity.TAG, errorMessage);
                                }
                            });

                } catch (Exception exc) {
                    exc.printStackTrace();
                }
            });
            trxThread.start();
        });

        /*pointer = replyDialogBuilder.setView(view)
                .setTitle(ctx.getString(R.string.reply))
                .setIcon(ctx.getResources().getDrawable(R.drawable.actifit_logo))
                .setPositiveButton(ctx.getString(R.string.reply_action), handleCommentAction)
                .setNegativeButton(ctx.getString(R.string.cancel_action), null)
                .create();


        replyDialogBuilder.show();*/

        //give focus to the edit text area
        replyText.requestFocus();
        replyText.setSelection(replyText.getText().length());


        replyText.addTextChangedListener(new TextWatcher() {

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
                    mdReplyView.setMDText(replyText.getText().toString());

                    //store current text
                        /*    SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("steemPostContent",
                                    steemitPostContent.getText().toString());
                            editor.apply();
                            */
                }else{
                    mdReplyView.setMDText(ctx.getString(R.string.comment_preview_lbl));
                }
            }
        });

        ProgressBar taskProgress = view.findViewById(R.id.loader);

        String shortenedContent = Utils.parseMarkdown(postEntry.body);
        //removed extra tags
        shortenedContent = Utils.sanitizeContent(shortenedContent, false);

        shortenedContent = Utils.trimText(shortenedContent, Constants.trimmedTextSize);

        //to be used when setting value upon content retract
        //final String finalShortenedContent = shortenedContent;

        //mdView.setMDText(finalShortenedContent);

        Button closeButton = view.findViewById(R.id.close_btn);
        closeButton.setOnClickListener(v -> {
            dismiss(); // Dismiss the DialogFragment when the close button is clicked
        });
    }
}
