package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import android.transition.Slide;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.fragment.app.DialogFragment;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class VideoDialogFragment extends DialogFragment {

    private static final String VIDEO_URL = "file:///android_asset/player.html";

    private static final String ARG_VIDEO_URL = "videoUrl";

    WebView webView;

    public VideoDialogFragment() {
        // Required empty public constructor
    }

    public static VideoDialogFragment newInstance(String videoId) {
        VideoDialogFragment fragment = new VideoDialogFragment();
        Bundle args = new Bundle();
        videoId = videoId.replace("watch?v=","embed/");//watch values need to be replace with embed to function
        args.putString(ARG_VIDEO_URL, videoId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onResume() {
        super.onResume();
        webView.loadUrl(VIDEO_URL);
        // Calculate the height of the DialogFragment based on a desired aspect ratio (e.g., 16:9)
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = width * 9 / 16; // Assuming a 16:9 aspect ratio
        //getDialog().getWindow().setLayout(width, height);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        //dialog.getWindow().requestFeature(STYLE_NO_TITLE);
        return dialog;
    }

    /*@Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

    }*/

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.help_actifit, container, false);

        webView = view.findViewById(R.id.youtubePlayerView);
        webView.setWebViewClient(new AppWebViewClients());

        /*webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(MainActivity.TAG+"console message:", consoleMessage.message());
                return true;
            }
        });*/

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        //webSettings.setUseWideViewPort(true);
        webView.addJavascriptInterface(new JavaScriptInterface(), "android");

        //webView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));


        // Set the enter and exit transitions for the DialogFragment
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setEnterTransition(new Slide(Gravity.BOTTOM));
            setExitTransition(new Slide(Gravity.BOTTOM));
        }

        // Apply the custom animation style
        int animationStyle = R.style.DialogAnimation;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setStyle(DialogFragment.STYLE_NORMAL, animationStyle);
        } else {
            getDialog().getWindow().getAttributes().windowAnimations = animationStyle;
        }

        webView.loadUrl(VIDEO_URL);

        // Find and set click listener for the close button
        Button closeButton = view.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss(); // Dismiss the DialogFragment when the close button is clicked
            }
        });

        return view;
    }

    public class AppWebViewClients extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // TODO Auto-generated method stub
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            // TODO Auto-generated method stub
            super.onPageFinished(view, url);
            try {
                // Adjust the WebView width dynamically based on the DialogFragment width
                int dialogWidth = getResources().getDisplayMetrics().widthPixels;
                view.setLayoutParams(new LinearLayout.LayoutParams(dialogWidth, LinearLayout.LayoutParams.WRAP_CONTENT));
                view.invalidate();

                // Calculate the WebView's content width using JavaScript
                webView.evaluateJavascript("(function() { return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, document.body.offsetWidth, document.documentElement.offsetWidth, document.body.clientWidth, document.documentElement.clientWidth); })();", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        // Parse the content width obtained from JavaScript
                        int contentWidth = Integer.parseInt(value);

                        // Adjust the iframe width and height using JavaScript
                        String js = "javascript:document.getElementById('player-iframe').width = '" + contentWidth + "';"
                                + "document.getElementById('player-iframe').height = '" + (contentWidth * 3 / 4) + "';";
                        webView.evaluateJavascript(js, null);
                    }
                });
            }catch(Exception genEx){
                genEx.printStackTrace();
            }

        }
    }

    private class JavaScriptInterface {
        @JavascriptInterface
        public String getVideoUrl() {
            Bundle args = getArguments();
            if (args != null) {
                return args.getString(ARG_VIDEO_URL);
            }
            return null;
        }
    }
}
