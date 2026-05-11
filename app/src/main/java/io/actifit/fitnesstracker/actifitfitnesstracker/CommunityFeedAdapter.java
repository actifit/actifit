package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class CommunityFeedAdapter extends RecyclerView.Adapter<CommunityFeedAdapter.ViewHolder> {

    private final Context ctx;
    private final List<SingleHivePostModel> posts;

    public CommunityFeedAdapter(Context ctx, List<SingleHivePostModel> posts) {
        this.ctx = ctx;
        this.posts = posts;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.community_post_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SingleHivePostModel post = posts.get(position);
        holder.username.setText("@" + post.author);
        holder.steps.setText(post.hasActivityCount() ? post.getActivityCount(true) : "");
        Glide.with(ctx)
                .load("https://images.hive.blog/u/" + post.author + "/avatar/small")
                .circleCrop()
                .placeholder(R.drawable.community_avatar_placeholder)
                .into(holder.avatar);
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final TextView username;
        final TextView steps;

        ViewHolder(View v) {
            super(v);
            avatar = v.findViewById(R.id.community_avatar);
            username = v.findViewById(R.id.community_username);
            steps = v.findViewById(R.id.community_steps);
        }
    }
}
