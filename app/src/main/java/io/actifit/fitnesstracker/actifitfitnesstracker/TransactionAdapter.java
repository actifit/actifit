package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;

import io.actifit.fitnesstracker.actifitfitnesstracker.TransactionItem;

// Keep your colors defined in res/values/colors.xml and res/values-night/colors.xml

public class TransactionAdapter extends ArrayAdapter<TransactionItem> {

    private Context mContext;
    private int mResource;

    public TransactionAdapter(@NonNull Context context, int resource, @NonNull List<TransactionItem> objects) {
        super(context, resource, objects);
        this.mContext = context;
        this.mResource = resource; // This will now be R.layout.list_item_transaction_modern
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        TransactionItem item = getItem(position);

        ViewHolder holder;
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            // !!! Inflate the new modern layout !!!
            convertView = inflater.inflate(R.layout.list_item_transaction, parent, false);

            holder = new ViewHolder();
            holder.tvActivityType = convertView.findViewById(R.id.textViewActivityType);
            holder.tvTokenAmount = convertView.findViewById(R.id.textViewTokenAmount);
            // !!! Find the new TextView for User/Recipient !!!
            holder.tvUserRecipient = convertView.findViewById(R.id.textViewUserRecipient);
            holder.tvDate = convertView.findViewById(R.id.textViewDate);
            holder.tvNote = convertView.findViewById(R.id.textViewNote);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // Populate the TextViews
        // Activity Type
        if (item.activityType != null && !item.activityType.isEmpty()) {
            holder.tvActivityType.setVisibility(View.VISIBLE);
            holder.tvActivityType.setText(item.activityType); // Just set the type directly now
        } else {
            holder.tvActivityType.setVisibility(View.GONE);
        }

        // Date
        if (item.date != null && !item.date.isEmpty()) {
            holder.tvDate.setVisibility(View.VISIBLE);
            holder.tvDate.setText(item.date); // Just set the date
        } else {
            holder.tvDate.setVisibility(View.GONE);
        }

        // Token Amount (Coloring logic remains)
        String amountText = String.format(Locale.getDefault(), "%.3f AFIT(s)", item.tokenCount);
        // Decide prefix based on sign
        String prefix = "";
        if (item.tokenCount > 0) {
            prefix = "+"; // Add plus sign for positive amounts
            holder.tvTokenAmount.setTextColor(ContextCompat.getColor(mContext, R.color.actifitDarkGreen));
        } else if (item.tokenCount < 0) {
            // Minus sign is already part of the number
            holder.tvTokenAmount.setTextColor(ContextCompat.getColor(mContext, R.color.actifitRed));
        } else {
            // Zero amount
            holder.tvTokenAmount.setTextColor(Color.GRAY); // Neutral color for zero
        }
        holder.tvTokenAmount.setText(prefix + amountText);


        // User/Recipient (Logic to combine/choose)
        String userRecipientText = "";
        if (item.user != null && !item.user.isEmpty() && item.recipient != null && !item.recipient.isEmpty()) {
            // It's likely a transfer between two different users
            if (item.user.equals(item.recipient)) {
                // If user sends to self (less common, but handles edge cases)
                userRecipientText = mContext.getString(R.string.user_lbl) + ": " + item.user; // Or "Self transfer"
            } else if (item.tokenCount > 0) {
                // Received tokens: From User X
                userRecipientText = mContext.getString(R.string.user_lbl) + ": " + item.user;
            } else if (item.tokenCount < 0) {
                // Sent tokens: To Recipient Y
                userRecipientText = mContext.getString(R.string.recipient_lbl) + ": " + item.recipient;
            } else {
                // Amount is zero, maybe show both? Or neither?
                userRecipientText = mContext.getString(R.string.user_lbl) + ": " + item.user + ", " + mContext.getString(R.string.recipient_lbl) + ": " + item.recipient;
            }
        } else if (item.user != null && !item.user.isEmpty()) {
            // Only user populated (e.g., rewards, or sender in some APIs)
            userRecipientText = mContext.getString(R.string.user_lbl) + ": " + item.user;
        } else if (item.recipient != null && !item.recipient.isEmpty()) {
            // Only recipient populated (e.g., receiver in some APIs)
            userRecipientText = mContext.getString(R.string.recipient_lbl) + ": " + item.recipient;
        }
        // Set the text and visibility for the User/Recipient TextView
        if (!userRecipientText.isEmpty()) {
            holder.tvUserRecipient.setVisibility(View.VISIBLE);
            holder.tvUserRecipient.setText(userRecipientText);
        } else {
            holder.tvUserRecipient.setVisibility(View.GONE); // Hide if no user/recipient info
        }


        // Note
        if (item.note != null && !item.note.isEmpty()) {
            holder.tvNote.setVisibility(View.VISIBLE);
            holder.tvNote.setText(mContext.getString(R.string.note_lbl) + ": " + item.note);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }

        // If you add URL, you might add another TextView or make the Note clickable etc.

        return convertView;
    }

    // ViewHolder class updated with the new TextView reference
    static class ViewHolder {
        TextView tvActivityType;
        TextView tvTokenAmount;
        TextView tvUserRecipient; // Updated field
        TextView tvDate;
        TextView tvNote;
    }
}