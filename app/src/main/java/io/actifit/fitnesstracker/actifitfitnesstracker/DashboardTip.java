package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;

import java.text.NumberFormat;

/**
 * The dashboard tip, built on the device from today's steps, the streak and the 7-day average.
 * It used to come from Gemini; these numbers can come from Health Connect, and Health Connect
 * data must not be sent to a third party for a feature that needs no server.
 */
final class DashboardTip {

    private DashboardTip() {
    }

    static final int KEEP_STREAK = 0;
    static final int START = 1;
    static final int TEN_K = 2;
    static final int ABOVE_AVG = 3;
    static final int STREAK = 4;
    static final int BELOW_AVG = 5;
    static final int GENERIC = 6;

    static final int TEN_K_STEPS = 10000;
    static final int STREAK_TIP_MIN = 3;

    /** Which tip fits, first match wins. */
    static int kind(int today, int streak, int avg7) {
        if (today < WidgetState.STREAK_THRESHOLD && streak > 0) return KEEP_STREAK;
        if (today == 0) return START;
        if (today >= TEN_K_STEPS) return TEN_K;
        if (avg7 > 0 && today > avg7) return ABOVE_AVG;
        if (streak >= STREAK_TIP_MIN) return STREAK;
        if (avg7 > 0 && today < avg7) return BELOW_AVG;
        return GENERIC;
    }

    static String text(Context ctx, int today, int streak, int avg7) {
        NumberFormat nf = NumberFormat.getIntegerInstance();
        switch (kind(today, streak, avg7)) {
            case KEEP_STREAK:
                return ctx.getString(R.string.tip_keep_streak, streak,
                        nf.format(WidgetState.STREAK_THRESHOLD - today));
            case START:
                return ctx.getString(R.string.tip_start);
            case TEN_K:
                return ctx.getString(R.string.tip_ten_k, nf.format(today));
            case ABOVE_AVG:
                return ctx.getString(R.string.tip_above_avg, nf.format(today - avg7));
            case STREAK:
                return ctx.getString(R.string.tip_streak, streak);
            case BELOW_AVG:
                return ctx.getString(R.string.tip_below_avg, nf.format(avg7 - today));
            default:
                return ctx.getString(R.string.tip_generic);
        }
    }
}
