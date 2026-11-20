package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.tus.java.client.ProtocolException;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import io.tus.java.client.TusClient;
import io.tus.java.client.TusExecutor;
import io.tus.java.client.TusURLMemoryStore;
import io.tus.java.client.TusUpload;
import io.tus.java.client.TusUploader;

import static android.app.Activity.RESULT_OK;

/**
 * The video sheet: upload a video to 3Speak and add one to the report.
 *
 * Flow (3Speak embed service): Actifit's server issues a short-lived upload token for the
 * logged-in user (POST /video/uploadToken, the 3Speak API key never reaches the phone); the
 * file goes straight to 3Speak by TUS with that token; the report links the video by its
 * embed URL, which plays right away while 3Speak encodes it. The list shows the user's
 * recent uploads (GET /video/myUploads) with 3Speak's public status for each.
 */
public class VideoUploadFragment extends BottomSheetDialogFragment {

    private static final String TAG = "VideoUpload";
    private static final int CHOOSING_VID_REQUEST = 1235;
    private static final int RECORD_VID_REQUEST = 1;
    private static final int TIMEOUT_MS = 20000;
    private static final int STATUS_TIMEOUT_MS = 5000;
    private static final String HIVE_RPC = "https://hiveapi.actifit.io";
    private static final int CHUNK_SIZE = 1024 * 1024;

    private final Context ctx;
    private final Activity parent;
    private final boolean addToPostEnabled;

    // App-wide, never shut down: an upload keeps going after the sheet is closed (its
    // one-time token is already spent), and work never touches a detached fragment.
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static volatile boolean uploading = false;
    // finished uploads from this phone; 3Speak may still report "uploading" for a moment
    private static final Set<String> UPLOADED_HERE = Collections.synchronizedSet(new HashSet<>());
    // the running upload, so a sheet opened again mid-upload shows its progress
    private static volatile VideoUploadFragment active;
    private static volatile long uploadSent, uploadTotal, uploadSeconds;
    private static volatile Bitmap uploadPreview;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private LayoutInflater rowInflater;
    private LinearLayout vidsList, uploadCard, emptyState;
    private TextView subtitle, uploadMeta, uploadBytes, uploadPct, oneAtATime, recentLabel;
    private ImageView uploadFrame;
    private LinearProgressIndicator uploadProgress;
    private MaterialButton vidSelector, recordVideo;
    private View loader;
    private ImageButton refreshList;
    private RotateAnimation rotate;

    public VideoUploadFragment(Context ctx, String accessToken, Activity parent, boolean addToPostEnabled) {
        this.ctx = ctx;
        this.parent = parent;
        this.addToPostEnabled = addToPostEnabled;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d instanceof BottomSheetDialog) {
            BottomSheetBehavior<?> b = ((BottomSheetDialog) d).getBehavior();
            b.setSkipCollapsed(true);
            b.setState(BottomSheetBehavior.STATE_EXPANDED);
            // our layout draws the rounded sheet
            View sheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) sheet.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.video_upload_view, container, false);
        rowInflater = inflater;

        rotate = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(1200);
        rotate.setInterpolator(new LinearInterpolator());
        rotate.setRepeatCount(Animation.INFINITE);

        vidsList = view.findViewById(R.id.vidsList);
        uploadCard = view.findViewById(R.id.uploadCard);
        emptyState = view.findViewById(R.id.emptyState);
        subtitle = view.findViewById(R.id.vidsSubtitle);
        uploadMeta = view.findViewById(R.id.uploadMeta);
        uploadBytes = view.findViewById(R.id.uploadBytes);
        uploadPct = view.findViewById(R.id.uploadPct);
        uploadFrame = view.findViewById(R.id.uploadFrame);
        uploadFrame.setClipToOutline(true);
        uploadProgress = view.findViewById(R.id.uploadProgress);
        oneAtATime = view.findViewById(R.id.oneAtATime);
        recentLabel = view.findViewById(R.id.recentLabel);
        loader = view.findViewById(R.id.loader);
        refreshList = view.findViewById(R.id.refresh_list);
        vidSelector = view.findViewById(R.id.vidSelector);
        recordVideo = view.findViewById(R.id.recordVideo);
        subtitle.setText(getString(R.string.vids_sheet_subtitle, 0, ThreeSpeakVideo.DAILY_LIMIT));

        refreshList.setOnClickListener(v -> loadUploads());
        view.findViewById(R.id.closeButton).setOnClickListener(v -> dismiss());
        vidSelector.setOnClickListener(v -> {
            if (uploading) return;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_vid_title)),
                    CHOOSING_VID_REQUEST);
        });
        recordVideo.setOnClickListener(v -> {
            if (uploading) return;
            startActivityForResult(new Intent(MediaStore.ACTION_VIDEO_CAPTURE), RECORD_VID_REQUEST);
        });

        active = this;
        showUploadState();
        loadUploads();
        return view;
    }

    @Override
    public void onDestroyView() {
        if (active == this) active = null;
        super.onDestroyView();
    }

    private void toast(String msg) {
        ui.post(() -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show());
    }

    // ---------------------------------------------------------------- list

    private void loadUploads() {
        refreshList.startAnimation(rotate);
        if (vidsList.getChildCount() == 0) loader.setVisibility(View.VISIBLE);
        final String user = MainActivity.username;
        IO.execute(() -> {
            List<UploadedVideoModel> list = new ArrayList<>();
            List<String> created = new ArrayList<>();
            boolean failed = false;
            try {
                JSONObject res = httpJson("GET", Utils.apiUrl(ctx) + "video/myUploads", null, true, TIMEOUT_MS);
                JSONArray rows = res.optJSONArray("uploads");
                if (rows == null) throw new IOException(res.optString("error", "no uploads list"));
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    String permlink = row.optString("permlink", "");
                    if (permlink.isEmpty()) continue;
                    created.add(row.optString("created", ""));
                    JSONObject status = null;
                    try {
                        status = httpJson("GET", ThreeSpeakVideo.statusUrl(permlink), null, false, STATUS_TIMEOUT_MS);
                    } catch (Exception e) {
                        Log.d(TAG, "status " + permlink + ": " + e.getMessage());
                    }
                    UploadedVideoModel v = ThreeSpeakVideo.fromStatus(user, permlink, row.optString("embed_url", ""),
                            row.optString("created", ""), status);
                    // our own frame beats 3Speak's, which may be the report's activity image
                    String frame = thumbs().getString(permlink, null);
                    if (frame != null) v.thumbnail_url = frame;
                    v.frame_url = frame;
                    // already used in a report? (the report post takes the video's permlink)
                    if (ThreeSpeakVideo.canAttach(v.status, UPLOADED_HERE.contains(permlink)) && alreadyPosted(user, permlink)) {
                        v.status = ThreeSpeakVideo.STATUS_POSTED;
                    }
                    list.add(v);
                }
            } catch (Throwable e) {
                Log.d(TAG, "myUploads: " + e.getMessage());
                failed = true;
            }
            final boolean loadFailed = failed;
            final int today = ThreeSpeakVideo.uploadsInLastDay(created, System.currentTimeMillis());
            ui.post(() -> {
                if (!isAdded()) return;
                refreshList.clearAnimation();
                loader.setVisibility(View.GONE);
                showUploads(list, loadFailed);
                if (!loadFailed) subtitle.setText(getString(R.string.vids_sheet_subtitle, today, ThreeSpeakVideo.DAILY_LIMIT));
                if (loadFailed) Toast.makeText(ctx, getString(R.string.vids_list_load_failed), Toast.LENGTH_LONG).show();
            });
        });
    }

    private void showUploads(List<UploadedVideoModel> list, boolean loadFailed) {
        vidsList.removeAllViews();
        boolean empty = list.isEmpty();
        emptyState.setVisibility(empty && !loadFailed ? View.VISIBLE : View.GONE);
        recentLabel.setVisibility(empty ? View.GONE : View.VISIBLE);
        for (UploadedVideoModel v : list) vidsList.addView(entryView(v));
    }

    private View entryView(UploadedVideoModel v) {
        View entry = rowInflater.inflate(R.layout.vid_single_entry, vidsList, false);
        String status = v.status == null ? "" : v.status;

        ((TextView) entry.findViewById(R.id.date_val)).setText(ThreeSpeakVideo.uploadTimeLabel(v.created,
                System.currentTimeMillis(), TimeZone.getDefault(), Locale.getDefault(),
                getString(R.string.vid_today), getString(R.string.vid_yesterday)));

        TextView duration = entry.findViewById(R.id.duration_val);
        if (v.duration > 0) duration.setText(ThreeSpeakVideo.durationLabel(v.duration));
        else duration.setVisibility(View.GONE);

        FrameLayout thumbFrame = entry.findViewById(R.id.thumbFrame);
        thumbFrame.setClipToOutline(true);
        ImageView thumbIcon = entry.findViewById(R.id.thumbIcon);
        boolean playable = !ThreeSpeakVideo.STATUS_MISSING.equals(status)
                && !"failed".equals(status) && !"deleted".equals(status);
        if (playable) {
            if (v.thumbnail_url != null) Glide.with(ctx).load(v.thumbnail_url).into((ImageView) entry.findViewById(R.id.thumbnail));
            // playback happens on 3Speak; open the player in the browser
            thumbFrame.setOnClickListener(x -> openInBrowser(v.embed_url));
        } else {
            thumbFrame.setBackgroundResource(R.drawable.vid_bg_thumb_missing);
            thumbFrame.setClickable(false);
            thumbFrame.setContentDescription(null);
            thumbIcon.setImageResource(R.drawable.vid_ic_alert);
            thumbIcon.setImageTintList(ColorStateList.valueOf(0xFF8A8486));
        }

        TextView chip = entry.findViewById(R.id.status_val);
        chip.setText(statusLabel(v));
        int[] colors = chipColors(status);
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setCornerRadius(999f);
        chipBg.setColor(ContextCompat.getColor(ctx, colors[0]));
        chip.setBackground(chipBg);
        chip.setTextColor(ContextCompat.getColor(ctx, colors[1]));
        if ("processing".equals(status)) {
            LinearProgressIndicator bar = entry.findViewById(R.id.encodeProgress);
            bar.setVisibility(View.VISIBLE);
            bar.setProgress((int) Math.round(v.encodingProgress));
        }

        MaterialButton add = entry.findViewById(R.id.addVidToPost);
        if (addToPostEnabled && parent instanceof PostSteemitActivity
                && ThreeSpeakVideo.canAttach(status, UPLOADED_HERE.contains(v.permlink))) {
            add.setVisibility(View.VISIBLE);
            if (v.permlink.equals(((PostSteemitActivity) parent).attachedVideoPermlink())) {
                add.setText(R.string.vid_added);
                add.setEnabled(false);
            } else {
                add.setOnClickListener(x -> {
                    ((PostSteemitActivity) parent).setMainVid(v);
                    dismiss();
                });
            }
        }
        if (ThreeSpeakVideo.STATUS_POSTED.equals(status)) {
            MaterialButton view = entry.findViewById(R.id.viewPost);
            view.setVisibility(View.VISIBLE);
            view.setOnClickListener(x -> openInBrowser(ThreeSpeakVideo.postUrl(MainActivity.username, v.permlink)));
        }
        return entry;
    }

    /** Background and text color of the status chip. */
    private static int[] chipColors(String status) {
        switch (status) {
            case "uploading":
            case "processing":
                return new int[]{R.color.vid_chip_amber_bg, R.color.vid_chip_amber_fg};
            case "published":
                return new int[]{R.color.vid_chip_green_bg, R.color.vid_chip_green_fg};
            case "failed":
            case ThreeSpeakVideo.STATUS_MISSING:
                return new int[]{R.color.vid_chip_red_bg, R.color.vid_chip_red_fg};
            default:
                return new int[]{R.color.vid_chip_grey_bg, R.color.vid_chip_grey_fg};
        }
    }

    private String statusLabel(UploadedVideoModel v) {
        String s = v.status == null ? "" : v.status;
        switch (s) {
            case "uploading":
                return getString(R.string.vid_status_uploading);
            case "processing":
                return getString(R.string.vid_status_processing, new DecimalFormat("#").format(v.encodingProgress));
            case "published":
                return getString(R.string.vid_status_ready);
            case "failed":
                return getString(R.string.vid_status_failed);
            case "deleted":
                return getString(R.string.vid_status_deleted);
            case ThreeSpeakVideo.STATUS_MISSING:
                return getString(R.string.vid_status_missing);
            case ThreeSpeakVideo.STATUS_POSTED:
                return getString(R.string.vid_status_posted);
            default:
                return getString(R.string.vid_status_unknown);
        }
    }

    private void openInBrowser(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Log.d(TAG, "open: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- upload

    /** The upload card and the upload buttons, from the running upload's shared state. */
    private void showUploadState() {
        if (uploadCard == null) return;
        uploadCard.setVisibility(uploading ? View.VISIBLE : View.GONE);
        oneAtATime.setVisibility(uploading ? View.VISIBLE : View.GONE);
        vidSelector.setEnabled(!uploading);
        recordVideo.setEnabled(!uploading);
        if (!uploading) return;
        Bitmap preview = uploadPreview;
        if (preview != null) uploadFrame.setImageBitmap(preview);
        String size = mb(uploadTotal);
        uploadMeta.setText(uploadSeconds > 0 ? ThreeSpeakVideo.durationLabel(uploadSeconds) + " · " + size + " MB" : size + " MB");
        if (uploadTotal > 0 && uploadSent > 0) {
            int pct = (int) (uploadSent * 100 / uploadTotal);
            uploadProgress.setIndeterminate(false);
            uploadProgress.setProgressCompat(pct, true);
            uploadBytes.setText(getString(R.string.vid_upload_bytes, mb(uploadSent), size));
            uploadPct.setText(pct + "%");
        } else {
            uploadProgress.setIndeterminate(true);
            uploadBytes.setText(getString(R.string.vid_upload_starting));
            uploadPct.setText("");
        }
    }

    private static String mb(long bytes) {
        return new DecimalFormat("0.0").format(bytes / (1024.0 * 1024.0));
    }

    /** Refreshes whichever sheet is open now (if any) from the upload's shared state. */
    private void postUploadState() {
        ui.post(() -> {
            VideoUploadFragment f = active;
            if (f != null && f.isAdded()) f.showUploadState();
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode != CHOOSING_VID_REQUEST && requestCode != RECORD_VID_REQUEST)
                || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        startUpload(data.getData());
    }

    private void startUpload(Uri fileUri) {
        uploading = true;
        uploadSent = 0;
        uploadTotal = 0;
        uploadSeconds = 0;
        uploadPreview = null;
        showUploadState();
        // strings resolved here: the sheet may be gone when the upload ends
        final String msgUnavailable = getString(R.string.vid_upload_unavailable);
        final String msgFailed = getString(R.string.vid_upload_failed);
        final String msgDone = getString(R.string.vid_upload_done);
        IO.execute(() -> {
            String error = null;
            try {
                long size = Utils.getVidSize(ctx, fileUri);
                String fileName = Utils.getOriginalFileName(ctx, fileUri);
                if (size <= 0) throw new IOException("unknown file size");
                uploadTotal = size;
                uploadSeconds = durationSeconds(fileUri);
                postUploadState();

                JSONObject body = new JSONObject().put("short", false);
                JSONObject tk = httpJson("POST", Utils.apiUrl(ctx) + "video/uploadToken", body, true, TIMEOUT_MS);
                String token = tk.optString("token", "");
                String permlink = tk.optString("permlink", "");
                if (token.isEmpty() || permlink.isEmpty()) {
                    // our errors come as {error}, an expired login as {success:false, message}
                    String serverMsg = tk.optString("error", tk.optString("message", ""));
                    error = serverMsg.isEmpty() ? msgUnavailable : serverMsg;
                } else {
                    // the frame is taken now, while the picked file is still readable
                    File frame = frameFile(fileUri);
                    try {
                        if (frame != null) {
                            BitmapFactory.Options o = new BitmapFactory.Options();
                            o.inSampleSize = 4;
                            uploadPreview = BitmapFactory.decodeFile(frame.getPath(), o);
                            postUploadState();
                        }
                        tusUpload(fileUri, size, fileName, uploadSeconds,
                                tk.optString("upload_url", ThreeSpeakVideo.EMBED_BASE + "/uploads"), token);
                        UPLOADED_HERE.add(permlink);
                        toast(msgDone);
                        // best effort: a missing frame never fails the upload
                        String url = frame == null ? null : uploadFrame(frame);
                        if (url != null) thumbs().edit().putString(permlink, url).apply();
                    } finally {
                        if (frame != null) frame.delete();
                    }
                }
            } catch (Throwable e) {
                Log.d(TAG, "upload: " + e.getMessage());
                error = msgFailed;
            }
            final String err = error;
            uploading = false;
            uploadPreview = null;
            ui.post(() -> {
                VideoUploadFragment f = active;
                if (f == null || !f.isAdded()) {
                    if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show();
                    return;
                }
                f.showUploadState();
                if (err != null && f.getActivity() != null) {
                    new AlertDialog.Builder(f.getActivity()).setMessage(err)
                            .setPositiveButton(f.getString(R.string.close_button), null).show();
                }
                f.loadUploads();
            });
        });
    }

    /** Frames uploaded from this phone, by video permlink (3Speak has no frame of its own). */
    private SharedPreferences thumbs() {
        return ctx.getSharedPreferences("threespeakThumbs", Context.MODE_PRIVATE);
    }

    /** A JPEG of the frame a quarter into the video, at most 1280 px, or null. */
    private File frameFile(Uri fileUri) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(ctx, fileUri);
            String ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long atUs = ms == null ? 0 : Long.parseLong(ms) * 1000 / 4;
            Bitmap frame = r.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 1280, 1280);
            if (frame == null) return null;
            File file = new File(ctx.getCacheDir(), UUID.randomUUID() + ".jpg");
            try (FileOutputStream out = new FileOutputStream(file)) {
                frame.compress(Bitmap.CompressFormat.JPEG, 85, out);
            } finally {
                frame.recycle();
            }
            return file;
        } catch (Throwable e) {
            Log.d(TAG, "frame: " + e.getMessage());
            return null;
        } finally {
            try { r.release(); } catch (Exception ignored) { }
        }
    }

    /** Uploads the frame to Actifit's media server, like report photos. Returns its URL, or null. */
    private String uploadFrame(File file) {
        try {
            MultipartBody.Part part = MultipartBody.Part.createFormData("image", file.getName(),
                    RequestBody.create(MediaType.parse("image/jpeg"), file));
            retrofit2.Response<ResponseBody> res = RetrofitClient.getClient().create(ApiService.class)
                    .uploadImage(part, ctx.getString(R.string.media_upload_key)).execute();
            if (res.body() != null) res.body().close();
            return res.isSuccessful() ? ctx.getString(R.string.actifit_usermedia_url) + file.getName() : null;
        } catch (Throwable e) {
            Log.d(TAG, "frame upload: " + e.getMessage());
            return null;
        }
    }

    /** The video's length in whole seconds, or 0 when the file has no readable duration. */
    private long durationSeconds(Uri fileUri) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(ctx, fileUri);
            String ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return ms == null ? 0 : Math.round(Long.parseLong(ms) / 1000.0);
        } catch (Exception e) {
            return 0;
        } finally {
            try { r.release(); } catch (Exception ignored) { }
        }
    }

    /** Sends the file to 3Speak by TUS, authorised by the one-time upload token. */
    private void tusUpload(Uri fileUri, long size, String fileName, long seconds, String uploadUrl, String token)
            throws IOException, ProtocolException {
        TusClient client = new TusClient();
        client.setUploadCreationURL(new URL(uploadUrl));
        client.enableResuming(new TusURLMemoryStore());
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        client.setHeaders(headers);

        InputStream in = ctx.getContentResolver().openInputStream(fileUri);
        if (in == null) throw new IOException("cannot open video");
        try {
            TusUpload upload = new TusUpload();
            upload.setInputStream(in);
            upload.setSize(size);
            upload.setFingerprint(fileUri.toString() + "-" + size);
            Map<String, String> meta = new HashMap<>();
            meta.put("filename", fileName == null ? "video.mp4" : fileName);
            String type = ctx.getContentResolver().getType(fileUri);
            meta.put("filetype", type == null ? "video/mp4" : type);
            if (seconds > 0) meta.put("duration", String.valueOf(seconds));
            upload.setMetadata(meta);

            TusExecutor executor = new TusExecutor() {
                @Override
                protected void makeAttempt() throws ProtocolException, IOException {
                    TusUploader uploader = client.resumeOrCreateUpload(upload);
                    uploader.setChunkSize(CHUNK_SIZE);
                    do {
                        uploadSent = uploader.getOffset();
                        postUploadState();
                    } while (uploader.uploadChunk() > -1);
                    uploader.finish();
                }
            };
            if (!executor.makeAttempts()) throw new IOException("upload interrupted");
        } finally {
            try { in.close(); } catch (IOException ignored) { }
        }
    }

    // ---------------------------------------------------------------- http

    /** Small blocking JSON call (run on the io thread). Error bodies are returned too. */
    private static JSONObject httpJson(String method, String url, JSONObject body, boolean auth, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("Accept", "application/json");
            if (auth) c.setRequestProperty("x-acti-token", "Bearer " + LoginActivity.accessToken);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = is == null ? "" : readAll(is);
            try {
                return new JSONObject(text);
            } catch (Exception notJson) {
                throw new IOException("HTTP " + code);
            }
        } finally {
            c.disconnect();
        }
    }

    /**
     * Is there already a Hive post at @user/permlink? Unknown (node down) counts as no.
     * Blocking: call it off the main thread.
     */
    static boolean alreadyPosted(String user, String permlink) {
        try {
            JSONObject req = new JSONObject().put("jsonrpc", "2.0").put("id", 1)
                    .put("method", "condenser_api.get_content")
                    .put("params", new JSONArray().put(user).put(permlink));
            return ThreeSpeakVideo.isExistingPost(httpJson("POST", HIVE_RPC, req, false, STATUS_TIMEOUT_MS));
        } catch (Exception e) {
            Log.d(TAG, "get_content " + permlink + ": " + e.getMessage());
            return false;
        }
    }

    private static String readAll(InputStream is) throws IOException {
        try (InputStream in = is; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
