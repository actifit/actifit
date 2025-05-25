package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.Intent; // For opening URLs
import android.graphics.Color;
import android.net.Uri; // For parsing URL string
import android.text.format.DateUtils; // For relative time
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast; // Optional: Show toast on URL click error
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat; // Use HtmlCompat for safety if needed, or just plain text

import java.text.ParseException; // For date parsing errors
import java.text.SimpleDateFormat; // For date parsing
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit; // For date calculations if not using DateUtils

// Keep your colors defined (positive_amount_color, negative_amount_color, actifitRed)

public class TransactionAdapter extends ArrayAdapter<TransactionItem> {

    private Context mContext;
    private int mResource;
    // Define date format based on your JSON 'date' string format
    // Example assuming "yyyy-MM-dd HH:mm:ss"
    private SimpleDateFormat originalDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());


    public TransactionAdapter(@NonNull Context context, int resource, @NonNull List<TransactionItem> objects) {
        super(context, resource, objects);
        this.mContext = context;
        this.mResource = resource; // R.layout.list_item_transaction_modern
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        TransactionItem item = getItem(position);

        ViewHolder holder;
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(R.layout.list_item_transaction, parent, false);

            holder = new ViewHolder();
            holder.tvActivityType = convertView.findViewById(R.id.textViewActivityType);
            holder.tvTokenAmount = convertView.findViewById(R.id.textViewTokenAmount);
            holder.tvUserRecipient = convertView.findViewById(R.id.textViewUserRecipient);
            holder.tvDate = convertView.findViewById(R.id.textViewDate);
            holder.tvNote = convertView.findViewById(R.id.textViewNote);
            holder.tvUrl = convertView.findViewById(R.id.textViewUrl); // Find the new TextView

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // Make sure the view holder holds the current item reference
        // This is useful for listeners accessing the data
        holder.item = item;

        // Populate TextViews (same as before)
        if (item.activityType != null && !item.activityType.isEmpty()) {
            holder.tvActivityType.setVisibility(View.VISIBLE);
            holder.tvActivityType.setText(item.activityType);
        } else {
            holder.tvActivityType.setVisibility(View.GONE);
        }

        // Token Amount (Coloring logic remains)
        String amountText = String.format(Locale.getDefault(), "%.3f AFIT(s)", item.tokenCount);
        String prefix = "";
        if (item.tokenCount > 0) {
            prefix = "+";
            holder.tvTokenAmount.setTextColor(ContextCompat.getColor(mContext, R.color.actifitDarkGreen));
        } else if (item.tokenCount < 0) {
            holder.tvTokenAmount.setTextColor(ContextCompat.getColor(mContext, R.color.actifitRed));
        } else {
            holder.tvTokenAmount.setTextColor(Color.GRAY);
        }
        holder.tvTokenAmount.setText(prefix + amountText);


        // User/Recipient (Logic remains - potentially refine based on transaction types)
        String userRecipientText = "";
        // ... (your logic to determine userRecipientText based on item.user, item.recipient, item.tokenCount) ...
        // Example: Simplified logic
        if (item.tokenCount > 0 && item.user != null && !item.user.isEmpty()) {
            userRecipientText = mContext.getString(R.string.user_lbl) + ": " + item.user; // Received from User
        } else if (item.tokenCount < 0 && item.recipient != null && !item.recipient.isEmpty()) {
            userRecipientText = mContext.getString(R.string.recipient_lbl) + ": " + item.recipient; // Sent to Recipient
        } else if (item.user != null && !item.user.isEmpty()) {
            userRecipientText = mContext.getString(R.string.user_lbl) + ": " + item.user; // Default to user if no clear direction
        } else if (item.recipient != null && !item.recipient.isEmpty()) {
            userRecipientText = mContext.getString(R.string.recipient_lbl) + ": " + item.recipient; // Default to recipient
        }
        // Set the text and visibility for the User/Recipient TextView
        if (!userRecipientText.isEmpty()) {
            holder.tvUserRecipient.setVisibility(View.VISIBLE);
            holder.tvUserRecipient.setText(userRecipientText);
        } else {
            holder.tvUserRecipient.setVisibility(View.GONE);
        }


        // Note
        if (item.note != null && !item.note.isEmpty()) {
            holder.tvNote.setVisibility(View.VISIBLE);
            holder.tvNote.setText(mContext.getString(R.string.note_lbl) + ": " + item.note);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }

        // ---------------------------------------------------------
        // Date Display (Relative Time initially, Toast Original Date on click)
        if (item.parsedDate != null) {
            holder.tvDate.setVisibility(View.VISIBLE);

            // Calculate and set relative time
            long now = System.currentTimeMillis();
            // You can adjust the resolution (e.g., DateUtils.SECOND_IN_MILLIS) if needed
            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                    item.parsedDate.getTime(),
                    now,
                    DateUtils.MINUTE_IN_MILLIS, // Minimum resolution
                    DateUtils.FORMAT_ABBREV_RELATIVE
            );
            holder.tvDate.setText(relativeTime);

            // Set the click listener for showing the Toast
            holder.tvDate.setOnClickListener(v -> {
                // Use the item reference from the holder
                TransactionItem currentItem = holder.item;

                if (currentItem.date != null && !currentItem.date.isEmpty()) {
                    // Show the original date string in a Toast
                    Toast.makeText(mContext, currentItem.date, Toast.LENGTH_SHORT).show();
                } else {
                    // Fallback Toast if original date string somehow missing (shouldn't happen if parsedDate exists)
                    Toast.makeText(mContext, "Date information not available", Toast.LENGTH_SHORT).show();
                }
            });

        } else if (item.date != null && !item.date.isEmpty()) {
            // parsedDate is null (parsing failed), just show the original date string
            holder.tvDate.setVisibility(View.VISIBLE);
            holder.tvDate.setText(item.date);
            // Still make it clickable, maybe the user wants to see the raw string toast? Or make it non-clickable.
            // If you want it non-clickable when parsing fails:
            holder.tvDate.setOnClickListener(null); // Disable click if date couldn't be parsed
        } else {
            // No date data at all
            holder.tvDate.setVisibility(View.GONE);
            holder.tvDate.setOnClickListener(null); // Ensure listener is null
        }
        // ---------------------------------------------------------


        // ---------------------------------------------------------
        // URL Display and Clicking (Same as before)
        if (item.url != null && !item.url.isEmpty()
                && !item.url.equals("undefined")) {
            holder.tvUrl.setVisibility(View.VISIBLE);
            holder.tvUrl.setText(R.string.details_button); // e.g., "View Post" from strings.xml

            // Set click listener to open the URL
            holder.tvUrl.setOnClickListener(v -> {
                TransactionItem currentItem = holder.item; // Use holder.item
                if (currentItem.url != null && !currentItem.url.isEmpty()) {
                    try {
                        String urlToParse = currentItem.url; // Start with the original URL
                        if (urlToParse.startsWith("/")) {
                            urlToParse = urlToParse.substring(1); // Remove the leading slash
                        }

                        Uri uri = Uri.parse(getContext().getString(R.string.actifit_url)
                                + urlToParse);
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        // Use mContext.startActivity directly. If mContext is not an Activity,
                        // the NEW_TASK flag is needed. It's safer to add it.
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(mContext, "Could not open link", Toast.LENGTH_SHORT).show();
                        Log.e("WalletActivity", "Failed to open URL: " + currentItem.url, e); // Log the error
                    }
                }
            });
        } else {
            holder.tvUrl.setVisibility(View.GONE);
            holder.tvUrl.setOnClickListener(null);
        }
        // ---------------------------------------------------------


        return convertView;
    }

    // ViewHolder class updated with the new TextView reference and item reference
    static class ViewHolder {
        TextView tvActivityType;
        TextView tvTokenAmount;
        TextView tvUserRecipient;
        TextView tvDate;
        TextView tvNote;
        TextView tvUrl; // Added URL TextView
        TransactionItem item; // Added reference to the item
    }
}