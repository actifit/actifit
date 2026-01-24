package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.transition.Slide;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import static android.content.Context.MODE_PRIVATE;

public class MainAnnounceFragment extends DialogFragment {

    private String featuredImageUrl;
    private String newsTitle;
    private String linkUrl;

    public MainAnnounceFragment() {
        // Required empty public constructor
    }

    public static MainAnnounceFragment newInstance(Slider_Items_Model_Class mainAnnounce) {
        MainAnnounceFragment fragment = new MainAnnounceFragment();
        Bundle args = new Bundle();
        args.putString("featured_image_url", mainAnnounce.getFeatured_image_url());
        args.putString("news_title", mainAnnounce.getNews_title());
        args.putString("link_url", mainAnnounce.getLink_url());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            featuredImageUrl = getArguments().getString("featured_image_url");
            newsTitle = getArguments().getString("news_title");
            linkUrl = getArguments().getString("link_url");
        }
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        // dialog.getWindow().requestFeature(STYLE_NO_TITLE);
        return dialog;
    }

    /*
     * @Override
     * public void onViewStateRestored(Bundle savedInstanceState) {
     * super.onViewStateRestored(savedInstanceState);
     * 
     * }
     */

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.announce_view, container, false);

        ImageView featured_image = view.findViewById(R.id.news_featured_image);
        TextView caption_title = view.findViewById(R.id.my_caption_title);

        Handler uiHandler = new Handler(Looper.getMainLooper());
        uiHandler.post(() -> {
            if (getContext() != null) {
                Glide.with(getContext())
                        .load(this.featuredImageUrl)
                        .override(800, 500) // Constrain size to prevent dialog overflow
                        .centerCrop() // Match XML scaleType
                        .into(featured_image);
            }
        });

        // featured_image.setImageResource();
        if (getContext() != null) {
            int textColor = ContextCompat.getColor(getContext(), R.color.colorBlack);
            caption_title.setTextColor(textColor);
        }
        caption_title.setText(this.newsTitle);

        // Find and set click listener for the close button
        Button detailsButton = view.findViewById(R.id.detailsButton);
        detailsButton.setOnClickListener(v -> {
            if (getContext() != null) {
                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

                builder.setToolbarColor(getContext().getResources().getColor(R.color.actifitRed));

                // animation for showing and closing fitbit authorization screen
                builder.setStartAnimations(getContext(), R.anim.slide_in_right, R.anim.slide_out_left);

                // animation for back button clicks
                builder.setExitAnimations(getContext(), android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right);

                CustomTabsIntent customTabsIntent = builder.build();

                customTabsIntent.launchUrl(getContext(), Uri.parse(this.linkUrl));
            }
        });

        // Find and set click listener for the close button
        Button closeButton = view.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> {
            dismiss(); // Dismiss the DialogFragment when the close button is clicked
        });

        return view;
    }

}
