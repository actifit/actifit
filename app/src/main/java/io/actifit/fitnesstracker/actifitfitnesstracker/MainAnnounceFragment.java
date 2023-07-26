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
import androidx.fragment.app.DialogFragment;

import com.squareup.picasso.Picasso;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import static android.content.Context.MODE_PRIVATE;

public class MainAnnounceFragment extends DialogFragment {

    Context ctx;
    Slider_Items_Model_Class mainAnnounce;

    public MainAnnounceFragment(Context ctx, Slider_Items_Model_Class mainAnnouce) {
        this.mainAnnounce = mainAnnouce;
        this.ctx = ctx;
    }


    @Override
    public void onResume() {
        super.onResume();

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
        View view = inflater.inflate(R.layout.announce_view, container, false);

        ImageView featured_image = view.findViewById(R.id.news_featured_image);
        TextView caption_title = view.findViewById(R.id.my_caption_title);

        Handler uiHandler = new Handler(Looper.getMainLooper());
        uiHandler.post(() -> {
            //Picasso.with(ctx)
            Picasso.get()
                    .load(this.mainAnnounce.getFeatured_image_url())
                    .into(featured_image);
        });

        //featured_image.setImageResource();
        caption_title.setText(this.mainAnnounce.getNews_title());

        // Find and set click listener for the close button
        Button detailsButton = view.findViewById(R.id.detailsButton);
        detailsButton.setOnClickListener(v -> {
            //Toast.makeText   (Mcontext, "test", Toast.LENGTH_LONG).show();
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

            builder.setToolbarColor(ctx.getResources().getColor(R.color.actifitRed));

            //animation for showing and closing fitbit authorization screen
            builder.setStartAnimations(ctx, R.anim.slide_in_right, R.anim.slide_out_left);

            //animation for back button clicks
            builder.setExitAnimations(ctx, android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right);

            CustomTabsIntent customTabsIntent = builder.build();

            customTabsIntent.launchUrl(ctx, Uri.parse(this.mainAnnounce.getLink_url()));
        });

        // Find and set click listener for the close button
        Button closeButton = view.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> {
            dismiss(); // Dismiss the DialogFragment when the close button is clicked
        });

        return view;
    }

}
