package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.viewpager.widget.PagerAdapter;

import com.squareup.picasso.Picasso;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.List;

public class Slider_items_Pager_Adapter extends PagerAdapter {

    private Context Mcontext;
    private List<Slider_Items_Model_Class> sliderItemsModelClasses;


    public Slider_items_Pager_Adapter(Context Mcontext, List<Slider_Items_Model_Class> slideItemsModelClassList) {
        this.Mcontext = Mcontext;
        this.sliderItemsModelClasses = slideItemsModelClassList;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {

        LayoutInflater inflater = (LayoutInflater) Mcontext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View sliderLayout = inflater.inflate(R.layout.slider_items_layout,null);

        ImageView featured_image = sliderLayout.findViewById(R.id.news_featured_image);
        TextView caption_title = sliderLayout.findViewById(R.id.my_caption_title);

        Handler uiHandler = new Handler(Looper.getMainLooper());
        uiHandler.post(() -> {
            //Picasso.with(ctx)
            Picasso.get()
                    .load(sliderItemsModelClasses.get(position).getFeatured_image_url())
                    .into(featured_image);
        });

        //featured_image.setImageResource();
        caption_title.setText(sliderItemsModelClasses.get(position).getNews_title());
        container.addView(sliderLayout);

        sliderLayout.setOnClickListener(view -> {
            //Toast.makeText   (Mcontext, "test", Toast.LENGTH_LONG).show();
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

            builder.setToolbarColor(Mcontext.getResources().getColor(R.color.actifitRed));

            //animation for showing and closing fitbit authorization screen
            builder.setStartAnimations(Mcontext, R.anim.slide_in_right, R.anim.slide_out_left);

            //animation for back button clicks
            builder.setExitAnimations(Mcontext, android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right);

            CustomTabsIntent customTabsIntent = builder.build();

            customTabsIntent.launchUrl(Mcontext, Uri.parse(sliderItemsModelClasses.get(position).getLink_url()));
        });

        return sliderLayout;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        try {
            container.removeView((View) object);
        }catch(Exception ex){
            ex.printStackTrace();
        }
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
