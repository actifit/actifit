package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.tus.java.client.ProtocolException;
import io.tus.java.client.TusClient;
import io.tus.java.client.TusExecutor;
import io.tus.java.client.TusURLMemoryStore;
import io.tus.java.client.TusUpload;
import io.tus.java.client.TusUploader;

import static android.app.Activity.RESULT_OK;

/**
 * Upload a video to 3Speak and add it to the report.
 *
 * Flow (3Speak embed service): Actifit's server issues a short-lived upload token for the
 * logged-in user (POST /video/uploadToken, the 3Speak API key never reaches the phone); the
 * file goes straight to 3Speak by TUS with that token; the report links the video by its
 * embed URL, which plays right away while 3Speak encodes it. The list shows the user's
 * recent uploads (GET /video/myUploads) with 3Speak's public status for each.
 */
public class VideoUploadFragment extends DialogFragment {

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

    // App-wide, never shut down: an upload keeps going after the dialog is closed (its
    // one-time token is already spent), and work never touches a detached fragment.
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static volatile boolean uploading = false;
    // finished uploads from this phone; 3Speak may still report "uploading" for a moment
    private static final Set<String> UPLOADED_HERE = Collections.synchronizedSet(new HashSet<>());

    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout vidsView, newVidLayoutContainer, progressContainer;
    private HorizontalScrollView hScrollView;
    private TextView noVids, vidProgressPercent;
    private RotateAnimation rotate;
    public TextView refreshList;

    public VideoUploadFragment(Context ctx, String accessToken, Activity parent, boolean addToPostEnabled) {
        this.ctx = ctx;
        this.parent = parent;
        this.addToPostEnabled = addToPostEnabled;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.video_upload_view, container, false);

        rotate = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(2000);
        rotate.setInterpolator(new LinearInterpolator());
        rotate.setRepeatCount(Animation.INFINITE);

        vidsView = view.findViewById(R.id.vidsList);
        newVidLayoutContainer = view.findViewById(R.id.newVidLayoutContainer);
        progressContainer = view.findViewById(R.id.progressContainer);
        vidProgressPercent = view.findViewById(R.id.vidUploadProgressPercent);
        view.findViewById(R.id.thumbUploadProgressPercent).setVisibility(View.GONE);
        hScrollView = view.findViewById(R.id.horizontalScrollView);
        noVids = view.findViewById(R.id.no_vids);
        refreshList = view.findViewById(R.id.refresh_list);

        view.findViewById(R.id.vids_list_info).setOnClickListener(v -> showInfo(R.string.vids_list_description));
        view.findViewById(R.id.upload_vid_info).setOnClickListener(v -> showInfo(R.string.upload_vid_description));

        refreshList.setOnClickListener(v -> loadUploads());
        // "show list" button from the old layout: same as refresh
        view.findViewById(R.id.chooseFile).setOnClickListener(v -> loadUploads());

        view.findViewById(R.id.vidSelector).setOnClickListener(v -> {
            if (uploading) return;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_vid_title)),
                    CHOOSING_VID_REQUEST);
        });
        view.findViewById(R.id.recordVideo).setOnClickListener(v -> {
            if (uploading) return;
            startActivityForResult(new Intent(MediaStore.ACTION_VIDEO_CAPTURE), RECORD_VID_REQUEST);
        });
        view.findViewById(R.id.closeButton).setOnClickListener(v -> dismiss());

        loadUploads();
        return view;
    }

    private void showInfo(int msgRes) {
        if (getActivity() == null) return;
        new AlertDialog.Builder(getActivity())
                .setMessage(Html.fromHtml(getString(msgRes)))
                .setTitle(getString(R.string.actifit_info))
                .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                .setPositiveButton(getString(R.string.close_button), null)
                .show();
    }

    private void toast(String msg) {
        ui.post(() -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show());
    }

    // ---------------------------------------------------------------- list

    private void loadUploads() {
        refreshList.startAnimation(rotate);
        noVids.setVisibility(View.GONE);
        final String user = MainActivity.username;
        IO.execute(() -> {
            List<UploadedVideoModel> list = new ArrayList<>();
            boolean failed = false;
            try {
                JSONObject res = httpJson("GET", Utils.apiUrl(ctx) + "video/myUploads", null, true, TIMEOUT_MS);
                JSONArray rows = res.optJSONArray("uploads");
                if (rows == null) throw new IOException(res.optString("error", "no uploads list"));
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    String permlink = row.optString("permlink", "");
                    if (permlink.isEmpty()) continue;
                    JSONObject status = null;
                    try {
                        status = httpJson("GET", ThreeSpeakVideo.statusUrl(permlink), null, false, STATUS_TIMEOUT_MS);
                    } catch (Exception e) {
                        Log.d(TAG, "status " + permlink + ": " + e.getMessage());
                    }
                    UploadedVideoModel v = ThreeSpeakVideo.fromStatus(user, permlink, row.optString("embed_url", ""),
                            row.optString("created", ""), status);
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
            ui.post(() -> {
                if (!isAdded()) return;
                refreshList.clearAnimation();
                showUploads(list);
                if (loadFailed) toast(ctx.getString(R.string.vids_list_load_failed));
            });
        });
    }

    private void showUploads(List<UploadedVideoModel> list) {
        vidsView.removeAllViews();
        noVids.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        for (UploadedVideoModel v : list) vidsView.addView(entryView(v));
        vidsView.setGravity(Gravity.LEFT);
        hScrollView.smoothScrollTo(0, 0);
    }

    private View entryView(UploadedVideoModel v) {
        View entry = LayoutInflater.from(ctx).inflate(R.layout.vid_single_entry, vidsView, false);
        DecimalFormat df = new DecimalFormat("#.##");

        ((TextView) entry.findViewById(R.id.title_val)).setText(v.title == null ? "" : v.title);
        ((TextView) entry.findViewById(R.id.status_val)).setText(statusLabel(v));
        ((TextView) entry.findViewById(R.id.duration_val)).setText(df.format(v.duration) + " sec");
        entry.findViewById(R.id.size_val).setVisibility(View.GONE);
        TextView date = entry.findViewById(R.id.date_val);
        if (v.created != null && v.created.length() >= 19) {
            date.setText(Utils.getTimeDifference(v.created.substring(0, 19)));
        }

        ImageView thumb = entry.findViewById(R.id.thumbnail);
        if (v.thumbnail_url != null) {
            Glide.with(ctx).load(v.thumbnail_url).into(thumb);
        }
        // playback happens on 3Speak; open the player in the browser
        thumb.setOnClickListener(x -> openInBrowser(v.embed_url));
        Button play = entry.findViewById(R.id.playVid);
        play.setVisibility(View.VISIBLE);
        play.setOnClickListener(x -> openInBrowser(v.embed_url));
        for (int id : new int[]{R.id.stopVid, R.id.deleteVid, R.id.submitVid, R.id.markVidPublished, R.id.submitLoader}) {
            View b = entry.findViewById(id);
            if (b != null) b.setVisibility(View.GONE);
        }

        Button addToPost = entry.findViewById(R.id.addVidToPost);
        if (addToPostEnabled && parent instanceof PostSteemitActivity
                && ThreeSpeakVideo.canAttach(v.status, UPLOADED_HERE.contains(v.permlink))) {
            addToPost.setVisibility(View.VISIBLE);
            addToPost.setOnClickListener(x -> {
                PostSteemitActivity post = (PostSteemitActivity) parent;
                post.setMainVid(v);
                post.appendContent(ThreeSpeakVideo.reportSnippet(MainActivity.username, v.permlink, v.embed_url));
                dismiss();
            });
        } else {
            addToPost.setVisibility(View.GONE);
        }
        return entry;
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
            Log.d(TAG, "open player: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- upload

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
        progressContainer.setVisibility(View.VISIBLE);
        vidProgressPercent.setVisibility(View.VISIBLE);
        vidProgressPercent.setText(getString(R.string.vid_upload_starting));
        // strings resolved here: the dialog may be gone when the upload ends
        final String msgUnavailable = getString(R.string.vid_upload_unavailable);
        final String msgFailed = getString(R.string.vid_upload_failed);
        final String msgDone = getString(R.string.vid_upload_done);
        IO.execute(() -> {
            String error = null;
            try {
                long size = Utils.getVidSize(ctx, fileUri);
                String fileName = Utils.getOriginalFileName(ctx, fileUri);
                if (size <= 0) throw new IOException("unknown file size");

                JSONObject body = new JSONObject().put("short", false);
                JSONObject tk = httpJson("POST", Utils.apiUrl(ctx) + "video/uploadToken", body, true, TIMEOUT_MS);
                String token = tk.optString("token", "");
                String permlink = tk.optString("permlink", "");
                if (token.isEmpty() || permlink.isEmpty()) {
                    // our errors come as {error}, an expired login as {success:false, message}
                    String serverMsg = tk.optString("error", tk.optString("message", ""));
                    error = serverMsg.isEmpty() ? msgUnavailable : serverMsg;
                } else {
                    tusUpload(fileUri, size, fileName, tk.optString("upload_url", ThreeSpeakVideo.EMBED_BASE + "/uploads"), token);
                    UPLOADED_HERE.add(permlink);
                    toast(msgDone);
                }
            } catch (Throwable e) {
                Log.d(TAG, "upload: " + e.getMessage());
                error = msgFailed;
            }
            final String err = error;
            uploading = false;
            ui.post(() -> {
                if (!isAdded()) {
                    if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show();
                    return;
                }
                progressContainer.setVisibility(View.GONE);
                if (err != null) {
                    new AlertDialog.Builder(getActivity()).setMessage(err)
                            .setPositiveButton(getString(R.string.close_button), null).show();
                }
                loadUploads();
            });
        });
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
    private void tusUpload(Uri fileUri, long size, String fileName, String uploadUrl, String token)
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
            long seconds = durationSeconds(fileUri);
            if (seconds > 0) meta.put("duration", String.valueOf(seconds));
            upload.setMetadata(meta);

            final DecimalFormat df = new DecimalFormat("0.0");
            TusExecutor executor = new TusExecutor() {
                @Override
                protected void makeAttempt() throws ProtocolException, IOException {
                    TusUploader uploader = client.resumeOrCreateUpload(upload);
                    uploader.setChunkSize(CHUNK_SIZE);
                    do {
                        double pct = (double) uploader.getOffset() / size * 100;
                        ui.post(() -> {
                            if (isAdded()) vidProgressPercent.setText(ctx.getString(R.string.vid_uploading, df.format(pct)));
                        });
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
    private JSONObject httpJson(String method, String url, JSONObject body, boolean auth, int timeoutMs) throws Exception {
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

    /** Is there already a Hive post at @user/permlink? Unknown (node down) counts as no. */
    private boolean alreadyPosted(String user, String permlink) {
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
