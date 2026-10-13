package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Challenge cards for the Arena home list. Also hosts the display helpers the detail screen shares. */
public class ArenaCardAdapter extends ArrayAdapter<ArenaChallenge> {

    private final String me;

    public ArenaCardAdapter(Context ctx, ArrayList<ArenaChallenge> items, String me) {
        super(ctx, 0, items);
        this.me = me;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View v = convertView != null ? convertView
                : LayoutInflater.from(getContext()).inflate(R.layout.arena_card, parent, false);
        ArenaChallenge c = getItem(position);
        if (c == null) return v;
        Context ctx = getContext();

        ((TextView) v.findViewById(R.id.arena_card_title)).setText(c.title);
        ((TextView) v.findViewById(R.id.arena_card_tagline)).setText(taglineText(ctx, c));

        List<String> meta = new ArrayList<>();
        meta.add(recurrenceText(ctx, c));
        meta.add(timeText(ctx, c));
        if (c.participantCount >= 0) {
            meta.add(ctx.getResources().getQuantityString(R.plurals.arena_participants,
                    c.participantCount, c.participantCount));
        }
        ((TextView) v.findViewById(R.id.arena_card_meta)).setText(android.text.TextUtils.join("  ·  ", meta));

        v.findViewById(R.id.arena_card_joined).setVisibility(isJoined(ctx, me, c) ? View.VISIBLE : View.GONE);

        ImageView art = v.findViewById(R.id.arena_card_art);
        Glide.with(ctx).load(ArenaLogic.artUrl(c.art, c.type)).centerCrop().into(art);
        return v;
    }

    /** Joined as far as we can tell, without consuming the stored optimistic op. */
    public static boolean isJoined(Context ctx, String me, ArenaChallenge c) {
        if (me == null || me.isEmpty()) return false;
        ArenaPendingStore.Pending p = ArenaPendingStore.get(ctx, me, c.id);
        ArenaLogic.Membership m = ArenaLogic.membership(c.serverJoined, p.op, p.atMs, System.currentTimeMillis());
        return m == ArenaLogic.Membership.JOINED || m == ArenaLogic.Membership.PENDING_JOIN;
    }

    // Fallback copy for challenges that carry none of their own, indexed by ArenaLogic.KIND_*.
    private static final int[] RECURRENCE = {R.string.arena_recurrence_league, R.string.arena_recurrence_daily,
            R.string.arena_recurrence_event, R.string.arena_recurrence_event};
    private static final int[] TAGLINE = {R.string.arena_tagline_league, R.string.arena_tagline_daily,
            R.string.arena_tagline_event, R.string.arena_tagline_default};
    private static final int[] HOW = {R.string.arena_how_league, R.string.arena_how_daily,
            R.string.arena_how_event, R.string.arena_how_default};
    private static final int[] PRIZE = {R.string.arena_prize_league, R.string.arena_prize_daily,
            R.string.arena_prize_event, R.string.arena_prize_default};

    private static String orFallback(Context ctx, String own, int[] fallback, ArenaChallenge c) {
        return own.isEmpty() ? ctx.getString(fallback[ArenaLogic.catalogKind(c.type)]) : own;
    }

    public static String recurrenceText(Context ctx, ArenaChallenge c) {
        return orFallback(ctx, c.recurrence, RECURRENCE, c);
    }

    public static String taglineText(Context ctx, ArenaChallenge c) {
        return orFallback(ctx, c.tagline, TAGLINE, c);
    }

    public static String howItWorksText(Context ctx, ArenaChallenge c) {
        return orFallback(ctx, c.howItWorks, HOW, c);
    }

    public static String prizeText(Context ctx, ArenaChallenge c) {
        return orFallback(ctx, c.prizeSummary, PRIZE, c);
    }

    /** "Ends in 5h 12m" while running; "Ended 30 Sep 2026" once closed. */
    public static String timeText(Context ctx, ArenaChallenge c) {
        long now = System.currentTimeMillis();
        if (!ArenaLogic.isJoinable(c.state) || ArenaLogic.hasEnded(c.endMs, now)) {
            String date = dateText(ctx, c.endMs);
            return date.isEmpty() ? ctx.getString(R.string.arena_ended)
                    : ctx.getString(R.string.arena_ended_on, date);
        }
        int[] left = ArenaLogic.remaining(c.endMs, now);
        if (c.endMs < 0) return "";
        if (left[0] > 0) return ctx.getString(R.string.arena_ends_in_days, left[0], left[1]);
        if (left[1] > 0) return ctx.getString(R.string.arena_ends_in_hours, left[1], left[2]);
        return ctx.getString(R.string.arena_ends_in_minutes, Math.max(1, left[2]));
    }

    public static String dateText(Context ctx, long ms) {
        return ms < 0 ? "" : DateFormat.getMediumDateFormat(ctx).format(new Date(ms));
    }
}
