package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.graphics.Color; // Android Color class
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat; // Recommended way to get colors

import java.util.List;
import java.util.Locale; // For formatting numbers

// Make sure you have these colors defined in res/values/colors.xml
// and res/values-night/colors.xml for dark mode support:
// <color name="positive_amount_color">#00AA00</color> <!-- Green for light mode -->
// <color name="negative_amount_color">#CC0000</color> <!-- Red for light mode -->
// Example dark mode colors in res/values-night/colors.xml:
// <color name="positive_amount_color">#66FF66</color> <!-- Lighter green for dark mode -->
// <color name="negative_amount_color">#FF6666</color> <!-- Lighter red for dark mode -->


public class TransactionAdapter extends ArrayAdapter<TransactionItem> {

    private Context mContext;
    private int mResource;

    /**
     * Default constructor for the TransactionAdapter
     * @param context The application context
     * @param resource The layout resource id for the list items
     * @param objects The list of TransactionItem objects to display
     */
    public TransactionAdapter(@NonNull Context context, int resource, @NonNull List<TransactionItem> objects) {
        super(context, resource, objects);
        this.mContext = context;
        this.mResource = resource; // This will be R.layout.list_item_transaction
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        // Get the transaction item for this position
        TransactionItem item = getItem(position);

        // ViewHolder pattern for performance (optional but recommended for ListViews)
        ViewHolder holder;
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(mResource, parent, false); // Inflate your custom layout

            holder = new ViewHolder();
            holder.tvActivityType = convertView.findViewById(R.id.textViewActivityType);
            holder.tvTokenAmount = convertView.findViewById(R.id.textViewTokenAmount);
            holder.tvUser = convertView.findViewById(R.id.textViewUser);
            holder.tvDate = convertView.findViewById(R.id.textViewDate);
            holder.tvNote = convertView.findViewById(R.id.textViewNote);
            // Find other TextViews for Recipient etc.
            convertView.setTag(holder); // Store the holder with the view
        } else {
            holder = (ViewHolder) convertView.getTag(); // Reuse the holder
        }

        // Populate the TextViews with data from the TransactionItem
        // Use helper method or check for nulls and set text/visibility

        // Activity Type
        if (item.activityType != null && !item.activityType.isEmpty()) {
            holder.tvActivityType.setVisibility(View.VISIBLE);
            holder.tvActivityType.setText(mContext.getString(R.string.activity_type_lbl) + ": " + item.activityType);
        } else {
            holder.tvActivityType.setVisibility(View.GONE); // Hide if no data
        }

        // Token Amount (This is where the coloring happens)
        // Format the number and add " AFIT(s)"
        // Using String.format for cleaner number display, e.g., 5.123 or -2.000
        String amountText = String.format(Locale.getDefault(), "%.3f AFIT(s)", item.tokenCount);
        holder.tvTokenAmount.setText(mContext.getString(R.string.token_count_lbl) + ": " + amountText);

        // --- Conditional Coloring for the Token Amount TextView ---
        if (item.tokenCount > 0) {
            holder.tvTokenAmount.setTextColor(ContextCompat.getColor(mContext, R.color.actifitDarkGreen));
        } else if (item.tokenCount < 0) {
            holder.tvTokenAmount.setTextColor(ContextCompat.getColor(mContext, R.color.actifitRed));
        } else {
            // Amount is zero, use a default/neutral color (e.g., gray or default text color)
            holder.tvTokenAmount.setTextColor(Color.GRAY); // Or get a neutral color from colors.xml
        }
        // ---------------------------------------------------------

        // User
        if (item.user != null && !item.user.isEmpty()) {
            holder.tvUser.setVisibility(View.VISIBLE);
            holder.tvUser.setText(mContext.getString(R.string.user_lbl) + ": " + item.user);
        } else {
            holder.tvUser.setVisibility(View.GONE);
        }

        // Date
        if (item.date != null && !item.date.isEmpty()) {
            holder.tvDate.setVisibility(View.VISIBLE);
            holder.tvDate.setText(mContext.getString(R.string.date_added_lbl) + ": " + item.date);
        } else {
            holder.tvDate.setVisibility(View.GONE);
        }

        // Note
        if (item.note != null && !item.note.isEmpty()) {
            holder.tvNote.setVisibility(View.VISIBLE);
            holder.tvNote.setText(mContext.getString(R.string.note_lbl) + ": " + item.note);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }

        // Populate other fields (Recipient etc.) similarly

        // Return the completed view
        return convertView;
    }

    // ViewHolder class to cache view lookups for smoother scrolling
    static class ViewHolder {
        TextView tvActivityType;
        TextView tvTokenAmount;
        TextView tvUser;
        TextView tvDate;
        TextView tvNote;
        // Add other TextViews
    }
}