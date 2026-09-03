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
        TextView stats = convertView.findViewById(R.id.friend_stats);
        TextView statsMutual = convertView.findViewById(R.id.friend_stats_mutual);
        Button action = convertView.findViewById(R.id.friend_action_btn);
        ProgressBar progress = convertView.findViewById(R.id.friend_action_progress);

        progress.setVisibility(View.GONE);
        stats.setVisibility(View.GONE);
        statsMutual.setVisibility(View.GONE);

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

        // SUGGESTED rows carry an activity/mutual subtitle that fills in once enrichment lands.
        if (m.type == FriendModel.Type.SUGGESTED) {
            stats.setVisibility(View.VISIBLE);
            if (!m.enriched) {
                stats.setText(R.string.friends_stats_loading);   // placeholder reserves line 1
            } else {
                String line1 = buildPrimaryStats(m);
                stats.setText(line1 != null ? line1 : "");
            }
            // Mutual friends get their own line so they can never be truncated by line 1.
            if (m.enriched && m.mutualCount != null && m.mutualCount > 0) {
                statsMutual.setText(getContext().getResources().getQuantityString(
                        R.plurals.friends_stat_mutual, m.mutualCount, m.mutualCount));
                statsMutual.setVisibility(View.VISIBLE);
            }
        }
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
            case SUGGESTED:
                labelRes = R.string.friends_action_add;
                tint = R.color.md_theme_primary;
                break;
            case FRIEND:
            default:
                labelRes = R.string.friends_action_unfriend;
                tint = R.color.md_theme_textSecondary;
                break;
        }
        action.setText(labelRes);
        action.setContentDescription(getContext().getString(labelRes) + " @" + m.username);
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

    /**
     * Line 1 of a suggestion: "1.3K reports · 20K AFIT". Built only from parts that actually
     * loaded, so a failed enrichment fetch (field left null) is omitted rather than shown as a
     * misleading "0" — and a genuine zero (fetch returned 0) is shown honestly.
     */
    private String buildPrimaryStats(FriendModel m) {
        java.util.ArrayList<String> top = new java.util.ArrayList<>();
        if (m.activityCount != null) {
            top.add(getContext().getResources().getQuantityString(
                    R.plurals.friends_stat_reports, m.activityCount, compact(m.activityCount)));
        }
        if (m.afit != null && !m.afit.isEmpty()) {
            top.add(getContext().getString(R.string.friends_stat_afit, m.afit));
        }
        return top.isEmpty() ? null : android.text.TextUtils.join(" · ", top);
    }

    /** Compact large numbers: 1303 → "1.3K", 44573 → "45K", 2_100_000 → "2.1M". */
    public static String compact(long n) {
        if (n < 1000) return String.valueOf(n);
        String[] units = {"K", "M", "B"};
        double val = n;
        int u = -1;
        while (val >= 1000 && u < units.length - 1) { val /= 1000; u++; }
        // One decimal only below 10 (1.3K); 10+ rounds to a whole number (45K, 90K).
        String num = (val >= 10 || val == Math.floor(val))
                ? String.valueOf(Math.round(val))
                : String.format(java.util.Locale.US, "%.1f", val);
        return num + units[u];
    }
}
