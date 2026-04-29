package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.transition.Slide;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import androidx.fragment.app.DialogFragment;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VideoDialogFragment extends DialogFragment {

    private static final String ARG_VIDEO_URL = "videoUrl";
    private static final String PLAYER_HTML = "file:///android_asset/player.html";

    WebView webView;
    private boolean fallbackTriggered = false;

    public VideoDialogFragment() {
    }

    public static VideoDialogFragment newInstance(String videoUrl) {
        VideoDialogFragment fragment = new VideoDialogFragment();
        Bundle args = new Bundle();
        String embedUrl = videoUrl;
        if (embedUrl.contains("watch?v=")) {
            embedUrl = embedUrl.replace("watch?v=", "embed/");
        }
        if (embedUrl.contains("youtube.com/") && !embedUrl.contains("youtube.com/embed/")) {
            String videoId = extractVideoId(embedUrl);
            if (videoId != null) {
                embedUrl = "https://www.youtube.com/embed/" + videoId;
            }
        }
        if (embedUrl.contains("youtu.be/")) {
            String videoId = extractVideoId(embedUrl);
            if (videoId != null) {
                embedUrl = "https://www.youtube.com/embed/" + videoId;
            }
        }
        args.putString(ARG_VIDEO_URL, embedUrl);
        fragment.setArguments(args);
        return fragment;
    }

    private static String extractVideoId(String url) {
        try {
            if (url.contains("youtu.be/")) {
                String id = url.substring(url.lastIndexOf("youtu.be/") + 9);
                int q = id.indexOf('?');
                if (q > 0) id = id.substring(0, q);
                int a = id.indexOf('&');
                if (a > 0) id = id.substring(0, a);
                return id;
            }
            if (url.contains("v=")) {
                int start = url.indexOf("v=") + 2;
                int end = url.indexOf('&', start);
                if (end < 0) end = url.length();
                return url.substring(start, end);
            }
        } catch (Exception e) {
            Log.e(MainActivity.TAG, "Error extracting video ID", e);
        }
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadVideo();
    }

    private void loadVideo() {
        if (webView == null) return;
        Bundle args = getArguments();
        if (args != null) {
            String url = args.getString(ARG_VIDEO_URL);
            if (url != null && !url.isEmpty()) {
                webView.loadUrl(PLAYER_HTML);
            }
        }
    }

    private void openInExternalPlayer() {
        if (getArguments() != null) {
            String embedUrl = getArguments().getString(ARG_VIDEO_URL);
            if (embedUrl != null) {
                String watchUrl = embedUrl;
                if (watchUrl.contains("/embed/")) {
                    watchUrl = watchUrl.replace("/embed/", "/watch?v=");
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl));
                    intent.setPackage("com.google.android.youtube");
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl));
                        startActivity(intent);
                    } catch (Exception e2) {
                        Log.e(MainActivity.TAG, "No video player available", e2);
                    }
                }
            }
        }
        dismiss();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return super.onCreateDialog(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.help_actifit, container, false);

        webView = view.findViewById(R.id.youtubePlayerView);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36");

        webView.addJavascriptInterface(new VideoJSInterface(), "android");
        webView.setWebViewClient(new VideoWebViewClient());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setEnterTransition(new Slide(Gravity.BOTTOM));
            setExitTransition(new Slide(Gravity.BOTTOM));
        }

        int animationStyle = R.style.DialogAnimation;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setStyle(DialogFragment.STYLE_NORMAL, animationStyle);
        } else {
            if (getDialog() != null && getDialog().getWindow() != null) {
                getDialog().getWindow().getAttributes().windowAnimations = animationStyle;
            }
        }

        loadVideo();

        Button closeButton = view.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> {
            if (webView != null) {
                webView.loadUrl("about:blank");
            }
            dismiss();
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.removeJavascriptInterface("android");
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }

    private class VideoJSInterface {
        @JavascriptInterface
        public String getVideoUrl() {
            Bundle args = getArguments();
            if (args != null) {
                return args.getString(ARG_VIDEO_URL);
            }
            return null;
        }
    }

    private class VideoWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (url.startsWith("file:///android_asset/") || url.equals("about:blank")) {
                return false;
            }
            if (url.contains("youtube.com/embed/") || url.contains("youtube-nocookie.com/embed/")) {
                return false;
            }
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                startActivity(intent);
            } catch (Exception e) {
                Log.e(MainActivity.TAG, "Cannot open URL externally", e);
            }
            return true;
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            Log.e(MainActivity.TAG, "Video WebView error: " + errorCode + " " + description);
            if (!fallbackTriggered) {
                fallbackTriggered = true;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> openInExternalPlayer());
                }
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
            super.onReceivedError(view, request, error);
            Log.e(MainActivity.TAG, "Video WebView resource error: " + error.getDescription());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (url.contains("youtube.com") || url.contains("youtube-nocookie.com") || url.contains("ytimg.com") || url.contains("googlevideo.com")) {
                try {
                    URL urlObj = new URL(url);
                    HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                    for (Map.Entry<String, String> h : request.getRequestHeaders().entrySet()) {
                        conn.setRequestProperty(h.getKey(), h.getValue());
                    }
                    conn.setRequestProperty("User-Agent", webView.getSettings().getUserAgentString());
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode >= 400) {
                        conn.disconnect();
                        return super.shouldInterceptRequest(view, request);
                    }

                    Map<String, String> cleanHeaders = new HashMap<>();
                    for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                        if (entry.getKey() == null) continue;
                        String key = entry.getKey().toLowerCase(Locale.ROOT);
                        if (key.equals("x-frame-options") ||
                            key.equals("content-security-policy") ||
                            key.equals("cross-origin-embedder-policy") ||
                            key.equals("cross-origin-opener-policy") ||
                            key.equals("cross-origin-resource-policy") ||
                            key.equals("permissions-policy")) {
                            continue;
                        }
                        cleanHeaders.put(entry.getKey(), entry.getValue().get(0));
                    }

                    String mimeType = conn.getContentType();
                    String mime = "text/html";
                    String encoding = "utf-8";
                    if (mimeType != null) {
                        String[] parts = mimeType.split(";");
                        mime = parts[0].trim();
                        for (int i = 1; i < parts.length; i++) {
                            String part = parts[i].trim();
                            if (part.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                                encoding = part.substring("charset=".length());
                            }
                        }
                    }

                    InputStream is = conn.getInputStream();
                    return new WebResourceResponse(mime, encoding, responseCode, conn.getResponseMessage(), cleanHeaders, is);
                } catch (Exception e) {
                    Log.e(MainActivity.TAG, "shouldInterceptRequest error for " + url, e);
                }
            }
            return super.shouldInterceptRequest(view, request);
        }
    }
}