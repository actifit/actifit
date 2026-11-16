package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

/**
 * The "Your token earnings" bottom sheet: one row per token shown in the earnings panel, each
 * with a status chip (Earning / Not earning / Checking...) and, when not earning, the reason and
 * an optional action. The rules live in {@link EarningsStatus}; this class only draws them.
 * {@link #update} redraws the rows in place while the sheet is open, so it can be shown before
 * the balance and account lookups have returned.
 */
final class EarningsSheet {

    /** A token row: display symbol, icon, and its current status. */
    static final class Row {
        final String symbol;
        final int iconRes;
        final EarningsStatus.TokenStatus status;
        /** Optional action for a NO_ACCOUNT reason (for example, open the signup page). */
        final Runnable action;
        final int actionLabelRes;

        Row(String symbol, int iconRes, EarningsStatus.TokenStatus status) {
            this(symbol, iconRes, status, null, 0);
        }

        Row(String symbol, int iconRes, EarningsStatus.TokenStatus status,
            Runnable action, int actionLabelRes) {
            this.symbol = symbol;
            this.iconRes = iconRes;
            this.status = status;
            this.action = action;
            this.actionLabelRes = actionLabelRes;
        }
    }

    private final Activity activity;
    private final BottomSheetDialog dialog;
    private final LinearLayout rowsContainer;

    EarningsSheet(Activity activity) {
        this.activity = activity;
        this.dialog = new BottomSheetDialog(activity, R.style.AppTheme_BottomSheet);
        View content = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_earnings, null);
        this.rowsContainer = content.findViewById(R.id.earnings_token_rows);
        content.findViewById(R.id.earnings_got_it).setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(content);
    }

    boolean isShowing() {
        return dialog.isShowing();
    }

    void show(Row... rows) {
        update(rows);
        if (!activity.isFinishing() && !activity.isDestroyed()) dialog.show();
    }

    /** Redraws all rows. Safe to call while the sheet is open; must run on the UI thread. */
    void update(Row... rows) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        rowsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (Row row : rows) {
            View view = inflater.inflate(R.layout.item_earnings_token, rowsContainer, false);
            bind(view, row);
            rowsContainer.addView(view);
        }
    }

    private void bind(View view, Row row) {
        ImageView icon = view.findViewById(R.id.earnings_token_icon);
        TextView name = view.findViewById(R.id.earnings_token_name);
        TextView chip = view.findViewById(R.id.earnings_token_chip);
        TextView reason = view.findViewById(R.id.earnings_token_reason);
        MaterialButton action = view.findViewById(R.id.earnings_token_action);

        icon.setImageResource(row.iconRes);
        name.setText(row.symbol);

        EarningsStatus.TokenStatus status = row.status;
        int colorRes;
        String marker;
        int labelRes;
        switch (status.state) {
            case EARNING:
                colorRes = R.color.md_theme_success;
                marker = "●";
                labelRes = R.string.earnings_status_earning;
                break;
            case NOT_EARNING:
                colorRes = R.color.md_theme_warning;
                marker = "▲";
                labelRes = R.string.earnings_status_not_earning;
                break;
            case CHECKING:
                colorRes = R.color.md_theme_textSecondary;
                marker = "○";
                labelRes = R.string.earnings_status_checking;
                break;
            default:
                colorRes = R.color.md_theme_textSecondary;
                marker = "–";
                labelRes = R.string.earnings_status_unavailable;
                break;
        }
        int color = ContextCompat.getColor(activity, colorRes);

        // Coloured marker + label in the regular text colour, on a tinted pill. Keeps the label
        // readable in both themes (the success green alone is too light for text on white).
        SpannableStringBuilder label = new SpannableStringBuilder(marker);
        label.setSpan(new ForegroundColorSpan(color), 0, marker.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.append(' ').append(activity.getString(labelRes));
        chip.setText(label);

        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadius(activity.getResources().getDisplayMetrics().density * 999);
        pill.setColor(Color.argb(46, Color.red(color), Color.green(color), Color.blue(color)));
        chip.setBackground(pill);

        String reasonText = null;
        if (status.state == EarningsStatus.State.NOT_EARNING) {
            if (status.reason == EarningsStatus.Reason.LOW_BALANCE) {
                reasonText = activity.getString(R.string.earnings_reason_low_balance,
                        MainActivity.formatValue(status.required), row.symbol,
                        MainActivity.formatValue(status.current));
            } else if (status.reason == EarningsStatus.Reason.NO_ACCOUNT) {
                reasonText = activity.getString(R.string.earnings_reason_no_blurt_account);
            }
        }
        reason.setText(reasonText);
        reason.setVisibility(reasonText == null ? View.GONE : View.VISIBLE);

        boolean showAction = status.state == EarningsStatus.State.NOT_EARNING
                && status.reason == EarningsStatus.Reason.NO_ACCOUNT
                && row.action != null;
        if (showAction) {
            action.setText(row.actionLabelRes);
            action.setOnClickListener(v -> row.action.run());
            action.setVisibility(View.VISIBLE);
        } else {
            action.setVisibility(View.GONE);
        }

        // Read the row as one sentence in TalkBack: "BLURT, Not earning, No Blurt account yet".
        View header = view.findViewById(R.id.earnings_token_header);
        header.setContentDescription(row.symbol + ", " + activity.getString(labelRes)
                + (reasonText == null ? "" : ", " + reasonText));
        header.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        chip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        reason.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
}
