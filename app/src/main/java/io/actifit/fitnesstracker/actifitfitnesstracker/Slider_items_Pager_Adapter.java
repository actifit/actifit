package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;

import com.bumptech.glide.Glide;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.List;

public class Slider_items_Pager_Adapter extends PagerAdapter {

    private Context ctx;
    private List<Slider_Items_Model_Class> sliderItemsModelClasses;
    private Activity activity;

    public Slider_items_Pager_Adapter(Context Mcontext,
            List<Slider_Items_Model_Class> slideItemsModelClassList,
            Activity activity) {
        this.ctx = Mcontext;
        this.sliderItemsModelClasses = slideItemsModelClassList;
        this.activity = activity;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        Slider_Items_Model_Class item = sliderItemsModelClasses.get(position);

        if (item.isTweet()) {
            View tweetLayout = inflater.inflate(R.layout.slider_tweet_layout, null);

            TextView tweetText = tweetLayout.findViewById(R.id.tweet_text_content);
            TextView tweetTimestamp = tweetLayout.findViewById(R.id.tweet_timestamp);
            Button likeBtn = tweetLayout.findViewById(R.id.like_on_x_btn);

            tweetText.setText(item.getNews_title());
            tweetTimestamp.setText(item.getTweetTimestamp());

            likeBtn.setOnClickListener(v -> openInCustomTab(item.getLink_url()));

            container.addView(tweetLayout);
            return tweetLayout;
        }

        View sliderLayout = inflater.inflate(R.layout.slider_items_layout, null);

        ImageView featured_image = sliderLayout.findViewById(R.id.news_featured_image);
        TextView caption_title = sliderLayout.findViewById(R.id.my_caption_title);

        Handler uiHandler = new Handler(Looper.getMainLooper());
        uiHandler.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            Glide.with(ctx)
                    .load(item.getFeatured_image_url())
                    .into(featured_image);
        });

        caption_title.setText(item.getNews_title());
        container.addView(sliderLayout);

        sliderLayout.setOnClickListener(view -> openInCustomTab(item.getLink_url()));

        return sliderLayout;
    }

    private void openInCustomTab(String url) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setToolbarColor(ContextCompat.getColor(ctx, R.color.actifitRed));
        builder.setStartAnimations(ctx, R.anim.slide_in_right, R.anim.slide_out_left);
        builder.setExitAnimations(ctx, android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        builder.build().launchUrl(ctx, Uri.parse(url));
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        activity.runOnUiThread(() -> {
            container.removeView((View) object);
        });
    }

    @Override
    public int getCount() {
        return sliderItemsModelClasses.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object o) {
        return view == o;
    }

}
