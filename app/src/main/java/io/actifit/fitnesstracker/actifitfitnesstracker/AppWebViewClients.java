package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.graphics.Bitmap;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import static android.view.View.VISIBLE;

public class AppWebViewClients extends WebViewClient {

    private ProgressBar taskProgress;

    public AppWebViewClients(ProgressBar taskProgress) {
        this.taskProgress = taskProgress;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        // TODO Auto-generated method stub
        view.loadUrl(url);
        return true;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon)
    {
        // TODO show you progress image
        super.onPageStarted(view, url, favicon);

        //ProgressBar taskProgress = view.findViewById(R.id.loader);
        if (taskProgress != null) {
            taskProgress.setVisibility(VISIBLE);
        }
    }

    @Override
    public void onPageFinished(WebView view, String url) {

        // TODO Auto-generated method stub
        super.onPageFinished(view, url);
        if (taskProgress != null) {
            taskProgress.setVisibility(View.GONE);
            //taskProgress = null;
        }

        //give focus
        view.requestFocus();
        try {
            // Adjust the WebView width dynamically based on the DialogFragment width

                /*
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

                 */
        }catch(Exception genEx){
            genEx.printStackTrace();
        }

    }
}