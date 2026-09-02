package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

/**
 * Renders friend / request rows. Row type decides the action button and whether it's a
 * section header. Tapping the avatar/handle opens the user's profile; tapping the action
 * button delegates to the host activity (which performs the write + refreshes).
 */
public class FriendEntryAdapter extends ArrayAdapter<FriendModel> {

    public interface ActionHandler {
        void onAction(FriendModel model);
    }

    private final ActionHandler handler;
    private final String hiveImgTpl;

    public FriendEntryAdapter(Context ctx, ArrayList<FriendModel> items, ActionHandler handler) {
        super(ctx, 0, items);
        this.handler = handler;
        this.hiveImgTpl = ctx.getString(R.string.hive_image_host_url);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final FriendModel m = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.friend_list_entry, parent, false);
        }

        TextView header = convertView.findViewById(R.id.friend_section_header);
        ImageView avatar = convertView.findViewById(R.id.friend_avatar);
        TextView handle = convertView.findViewById(R.id.friend_handle);
        Button action = convertView.findViewById(R.id.friend_action_btn);
        ProgressBar progress = convertView.findViewById(R.id.friend_action_progress);

        progress.setVisibility(View.GONE);

        if (m == null) {
            return convertView;
        }

        if (m.type == FriendModel.Type.HEADER) {
            header.setText(m.username);
            header.setVisibility(View.VISIBLE);
            avatar.setVisibility(View.GONE);
            handle.setVisibility(View.GONE);
            action.setVisibility(View.GONE);
            convertView.setClickable(false);
            convertView.setOnClickListener(null);
            return convertView;
        }

        header.setVisibility(View.GONE);
        avatar.setVisibility(View.VISIBLE);
        handle.setVisibility(View.VISIBLE);
        action.setVisibility(View.VISIBLE);

        handle.setText("@" + m.username);
        avatar.setContentDescription(getContext().getString(R.string.friends_avatar_desc, m.username));
        Glide.with(getContext())
                .load(hiveImgTpl.replace("USERNAME", m.username))
                .placeholder(R.drawable.default_pic)
                .error(R.drawable.default_pic)
                .into(avatar);

        int labelRes;
        int tint;
        switch (m.type) {
            case RECEIVED:
                labelRes = R.string.friends_action_accept;
                tint = R.color.actifitDarkGreen;
                break;
            case SENT:
                labelRes = R.string.friends_action_cancel;
                tint = R.color.md_theme_textSecondary;
                break;
            case FRIEND:
            default:
                labelRes = R.string.friends_action_unfriend;
                tint = R.color.md_theme_textSecondary;
                break;
        }
        action.setText(labelRes);
        action.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(getContext().getResources().getColor(tint)));

        final View progressRef = progress;
        final Button actionRef = action;
        action.setOnClickListener(v -> {
            actionRef.setVisibility(View.INVISIBLE);
            progressRef.setVisibility(View.VISIBLE);
            if (handler != null) handler.onAction(m);
        });

        View.OnClickListener openProfile = v -> {
            Intent i = new Intent(getContext(), ProfileActivity.class);
            i.putExtra(ProfileActivity.EXTRA_USERNAME, m.username);
            getContext().startActivity(i);
        };
        avatar.setOnClickListener(openProfile);
        handle.setOnClickListener(openProfile);
        convertView.setOnClickListener(openProfile);

        return convertView;
    }
}
