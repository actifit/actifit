package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
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

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import static android.app.Activity.RESULT_OK;
import static android.view.View.VISIBLE;
import static com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread;
import static io.actifit.fitnesstracker.actifitfitnesstracker.PostSteemitActivity.copyExif;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.picasso.PicassoImagesPlugin;

public class CommentModalDialogFragment extends DialogFragment {
    public Context ctx;
    SingleHivePostModel postEntry;
    //View mainView;
    EditText replyText;

    private static final int COMMENT_IMAGE_REQUEST = 1129;

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
        //View mainView = view;
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

        //EditText
        replyText = view.findViewById(R.id.reply_text);
        TextView mdReplyView = view.findViewById(R.id.reply_preview);

        Button insertImage = view.findViewById(R.id.insert_image_comment);

        insertImage.setOnClickListener(v ->{
            showChoosingFile();
        });
        //Button proceedCommentBtn = replyModalLayout.findViewById(R.id.proceed_comment_btn);

        //mdReplyView.setMDText(replyText.getText().toString());
        //default content for preview
        //mdReplyView.setMDText(ctx.getString(R.string.comment_preview_lbl));
        final Markwon markwon = Markwon.builder(ctx)
                //.usePlugin(ImagesPlugin.create())
                //support HTML
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
                })

                //handle images via available picasso
                //.usePlugin(PicassoImagesPlugin.create(Picasso.get()))

                //handle images via picasso
                .usePlugin(PicassoImagesPlugin.create(new PicassoImagesPlugin.PicassoStore() {
                    @org.checkerframework.checker.nullness.qual.NonNull
                    @Override
                    public RequestCreator load(@org.checkerframework.checker.nullness.qual.NonNull AsyncDrawable drawable) {

                        return Picasso.get()
                                .load(drawable.getDestination())
                                //.resize(desiredWidth, desiredHeight)
                                //.centerCrop()
                                .tag(drawable);
                    }

                    @Override
                    public void cancel(@NonNull AsyncDrawable drawable) {
                        Picasso.get()
                                .cancelTag(drawable);
                    }
                }))

                .build();

        markwon.setMarkdown(mdReplyView, ctx.getString(R.string.comment_preview_lbl));

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

                    String comment_perm = MainActivity.username.replace(".", "-") + "-re-" + postEntry.author.replace(".", "-") + "-" + postEntry.permlink + new SimpleDateFormat("yyyyMMddHHmmssSSS'Z'", Locale.US).format(new Date());
                    comment_perm = comment_perm.replaceAll("[^a-zA-Z0-9-]+", "").toLowerCase();

                    JSONObject cstm_params = new JSONObject();
                    cstm_params.put("author", MainActivity.username);
                    cstm_params.put("permlink", comment_perm);
                    cstm_params.put("title", "");
                    //include comment alongside comment source (android app)
                    cstm_params.put("body", replyText.getText());// + " <br />" + getString(R.string.comment_note));
                    cstm_params.put("parent_author", postEntry.author);
                    cstm_params.put("parent_permlink", postEntry.permlink);

                    JSONObject metaData = new JSONObject();

                    JSONArray tagsArr = new JSONArray();
                    tagsArr.put("hive-193552");
                    tagsArr.put("actifit");
                    metaData.put("tags", tagsArr);

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
                    //mdReplyView.setMDText(replyText.getText().toString());
                    markwon.setMarkdown(mdReplyView, replyText.getText().toString());

                    //store current text
                        /*    SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("steemPostContent",
                                    steemitPostContent.getText().toString());
                            editor.apply();
                            */
                }else{
                    //mdReplyView.setMDText(ctx.getString(R.string.comment_preview_lbl));
                    markwon.setMarkdown(mdReplyView, ctx.getString(R.string.comment_preview_lbl));
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


    /*************************************/
    private Uri fileUri;
    private Bitmap bitmap;

    private void createFile(Context context, Uri srcUri, File dstFile) {
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

    //implementing file upload functionality
    private void uploadFile() {
        final ProgressDialog uploadProgress;
        if (fileUri != null) {

            AWSMobileClient.getInstance().initialize(ctx).execute();

            //create unique image file name
            final String fileName = UUID.randomUUID().toString();

            // final File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            final File file = new File(ctx.getFilesDir(), fileName);

            createFile(ctx, fileUri, file);

            TransferUtility transferUtility =
                    TransferUtility.builder()
                            .context(ctx)
                            .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                            .s3Client(new AmazonS3Client(AWSMobileClient.getInstance().getCredentialsProvider()))
                            .build();

            //specify content type to be image to be properly recognizable upon rendering
            ObjectMetadata imgMetaData = new ObjectMetadata();
            imgMetaData.setContentType("image/jpeg");

            TransferObserver uploadObserver =
                    transferUtility.upload(fileName, file, imgMetaData);

            //create a new progress dialog to show action is underway
            uploadProgress = new ProgressDialog(ctx);
            uploadProgress.setMessage(getString(R.string.start_upload));
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
                            //Log.d(MainActivity.TAG,ex.getMessage());
                        }

                        Toast.makeText(ctx, getString(R.string.upload_complete), Toast.LENGTH_SHORT).show();

                        String full_img_url = getString(R.string.actifit_usermedia_url)+fileName;
                        String img_markdown_text = "![]("+full_img_url+")";

                        //append the uploaded image url to the text as markdown
                        //if there is any particular selection, replace it too

                        int start = Math.max(replyText.getSelectionStart(), 0);
                        int end = Math.max(replyText.getSelectionEnd(), 0);
                        replyText.getText().replace(Math.min(start, end), Math.max(start, end),
                                img_markdown_text, 0, img_markdown_text.length());

                        file.delete();

                    } else if (TransferState.FAILED == state) {
                        Toast toast = Toast.makeText(ctx, getString(R.string.upload_failed), Toast.LENGTH_SHORT);
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
                    uploadProgress.setMessage(getString(R.string.uploading) + percentDone + "%");
                    //tvFileName.setText("ID:" + id + "|bytesCurrent: " + bytesCurrent + "|bytesTotal: " + bytesTotal + "|" + percentDone + "%");
                }

                @Override
                public void onError(int id, Exception ex) {
                    ex.printStackTrace();
                }

            });

            // If your upload does not trigger the onStateChanged method inside your
            // TransferListener, you can directly check the transfer state as shown here.
            if (TransferState.COMPLETED == uploadObserver.getState()) {
                // Handle a completed upload.
                try {
                    if (uploadProgress != null && uploadProgress.isShowing()) {
                        uploadProgress.dismiss();
                    }
                }catch (Exception ex){
                    //Log.d(MainActivity.TAG,ex.getMessage());
                }

                Toast.makeText(ctx, getString(R.string.upload_complete), Toast.LENGTH_SHORT).show();

                String full_img_url = getString(R.string.actifit_usermedia_url)+fileName;
                String img_markdown_text = "![]("+full_img_url+")";

                //append the uploaded image url to the text as markdown
                //if there is any particular selection, replace it too

                int start = Math.max(replyText.getSelectionStart(), 0);
                int end = Math.max(replyText.getSelectionEnd(), 0);
                replyText.getText().replace(Math.min(start, end), Math.max(start, end),
                        img_markdown_text, 0, img_markdown_text.length());

                file.delete();
            }
        }
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
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_img_title)), COMMENT_IMAGE_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (bitmap != null) {
            bitmap.recycle();
        }

        if (requestCode == COMMENT_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            fileUri = data.getData();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(ctx.getContentResolver(), fileUri);

                uploadFile();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
