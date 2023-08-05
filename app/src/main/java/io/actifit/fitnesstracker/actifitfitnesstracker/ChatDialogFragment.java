package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.transition.Slide;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import static android.content.Context.MODE_PRIVATE;
import static android.view.View.VISIBLE;

public class ChatDialogFragment extends DialogFragment {

    private static final String VIDEO_URL = "file:///android_asset/stingchat.html";

    private static final String ARG_USER = "user";
    private static final String ARG_PST_KEY = "pstKey";


    WebView webView;
    private int origWebViewHeight = 0;
    static Context ctx;
//    LinearLayout parentLayout;
    //ScrollView scrollView;


    public ChatDialogFragment() {
        // Required empty public constructor
    }

    public static ChatDialogFragment newInstance(Context ctx) {
        ChatDialogFragment fragment = new ChatDialogFragment();
        Bundle args = new Bundle();
        //grab posting key
        final SharedPreferences sharedPreferences =  ctx.getSharedPreferences("actifitSets",MODE_PRIVATE);

        String accountUsername = sharedPreferences.getString("actifitUser","");
        args.putString(ARG_USER, accountUsername);
        String accountPostingKey = sharedPreferences.getString("actifitPst","");
        args.putString(ARG_PST_KEY, accountPostingKey);
        //videoId = videoId.replace("watch?v=","embed/");//watch values need to be replace with embed to function
        //args.putString(ARG_PST_KEY, videoId);
        fragment.setArguments(args);
        ChatDialogFragment.ctx = ctx;

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
        //WORKS BELOW
        //dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        //fix display to fit keyboard
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        //dialog.getWindow().setEnterTransition(Utils.createSlideTransition(ChatDialogFragment.ctx));
        //dialog.getWindow().setExitTransition(Utils.createSlideTransition(ChatDialogFragment.ctx));
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
        View view = inflater.inflate(R.layout.chat_view, container, false);

        webView = view.findViewById(R.id.chatContainer);

        //parentLayout = view.findViewById(R.id.chatParentContainer);

        //scrollView = view.findViewById(R.id.chatSVContainer);

        //pass loader as param
        webView.setWebViewClient(new AppWebViewClients(view.findViewById(R.id.loader), this));

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


        //set modal for additional info
        TextView modalBtn = view.findViewById(R.id.chat_info);
        modalBtn.setOnClickListener(v -> {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this.getContext());

            AlertDialog pointer = dialogBuilder.setMessage(Html.fromHtml(getString(R.string.chat_description)))
                    .setTitle(getString(R.string.chat_title_desc))
                    .setIcon(getResources().getDrawable(R.drawable.actifit_logo))
                    .setPositiveButton(getString(R.string.close_button), null)
                    .create();
            dialogBuilder.show();
        });

/*
        view.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        //measureWebViewAndResize();


                    }


                });
*/



        return view;
    }

    /*public void measureWebViewAndResize() {

        if (webViewHeight == 0) {
            // WebView has not been measured yet, get its width
            int width = webView.getMeasuredWidth();
            int height = webView.getContentHeight();
            if (width > 0 && height > 0) {
                webViewHeight = height;
                getDialog().getWindow().setLayout(width, height);
            }
        }
    }*/

    /*
    public void measureWebViewAndResize() {

        if (getView() == null) return;

        View view = getView();
        WebView webView = view.findViewById(R.id.chatContainer);

        int webViewHeight = webView.getContentHeight();
        int webViewWidth = webView.getWidth();
        System.out.println(">><<>>measureWebView webViewHeight:"+webViewHeight);
        System.out.println(">><<>>measureWebView webViewWidth:"+webViewWidth);
        Rect r = new Rect();
        view.getWindowVisibleDisplayFrame(r);
        int screenHeight = view.getRootView().getHeight();
        System.out.println(">><<>>measureWebView screenHeight:"+screenHeight);
        int keypadHeight = screenHeight - r.bottom;
        System.out.println(">><<>>measureWebView keypadHeight:"+keypadHeight);
        if (keypadHeight > screenHeight * 0.15) { // 0.15 ratio is perhaps enough to determine keypad visible
            // Keyboard is visible, adjust dialog size
            int newHeight = screenHeight - keypadHeight;
            System.out.println(">><<>>measureWebView newHeight:"+newHeight);
            if (newHeight > 0) {
                Dialog dialog = getDialog();
                if (dialog != null) {
                    dialog.getWindow().setLayout(webViewWidth, newHeight);
                    //parentLayout.scrollTo(0, screenHeight);
                    //scrollView.smoothScrollTo(0, scrollView.getBottom());
                }
            }
        }else{
            Dialog dialog = getDialog();
            if (dialog != null) {
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            //restoreOriginalDimensions();
        }
    }

    // Method to restore the original dimensions
    public void restoreOriginalDimensions() {
        Dialog dialog = getDialog();
        if (dialog != null) {
            ViewGroup.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            //dialog.getWindow().setAttributes(params);
        }
    }

*/
    private class JavaScriptInterface {
        @JavascriptInterface
        public String getPstKey() {
            Bundle args = getArguments();
            if (args != null) {
                return args.getString(ARG_PST_KEY);
            }
            return null;
        }
        @JavascriptInterface
        public String getUser(){
            Bundle args = getArguments();
            if (args != null) {
                return args.getString(ARG_USER);
            }
            return null;
        }
    }
}
