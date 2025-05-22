package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat; // For formatting timestamp
import java.util.Date; // For Date object
import java.util.List;
import java.util.Locale; // For Locale

// Your adapter class extends RecyclerView.Adapter<YourViewHolder>
public class SavedWorkoutsAdapter extends RecyclerView.Adapter<SavedWorkoutsAdapter.WorkoutViewHolder> {

    private List<WorkoutPlan> workoutList;
    private OnWorkoutSelectedListener listener; // Interface for click handling

    // Interface for click listener - Define what happens when an item is clicked
    public interface OnWorkoutSelectedListener {
        void onWorkoutSelected(WorkoutPlan workout); // Pass the clicked WorkoutPlan object
    }

    // Constructor to provide the data list and the click listener
    public SavedWorkoutsAdapter(List<WorkoutPlan> workoutList, OnWorkoutSelectedListener listener) {
        this.workoutList = workoutList;
        this.listener = listener;
    }

    // Method to update the list data from outside (e.g., when API response arrives)
    public void setWorkoutList(List<WorkoutPlan> newWorkouts) {
        this.workoutList = newWorkouts;
        notifyDataSetChanged(); // Tell the RecyclerView to refresh the list display
    }

    // Create new views (invoked by the layout manager)
    @NonNull
    @Override
    public WorkoutViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the layout for a single list item (you need workout_list_item.xml)
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.workout_list_item, parent, false); // Use the list item layout
        return new WorkoutViewHolder(itemView); // Return a new ViewHolder instance
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(@NonNull WorkoutViewHolder holder, int position) {
        WorkoutPlan workout = workoutList.get(position);
        holder.workoutNameTextView.setText(workout.getWorkoutName());
        // Optional: Display description
        //holder.workoutDescriptionTextView.setText(workout.getDescription());


        // --- Update Timestamp Formatting ---
        Date timestampDate = workout.getTimestamp(); // <-- Get the Date object

        if (timestampDate != null) {
            // Format the Date object
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            String formattedDate = sdf.format(timestampDate); // <-- Format the Date
            holder.workoutTimestampTextView.setText("Saved: " + formattedDate); // Or just the date
        } else {
            holder.workoutTimestampTextView.setText("Saved: N/A"); // Handle null timestamp
        }


        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onWorkoutSelected(workout);
            }
        });
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return workoutList == null ? 0 : workoutList.size();
    }

    // --- ViewHolder inner class ---
    // Provides a reference to the views for each data item
    static class WorkoutViewHolder extends RecyclerView.ViewHolder {
        TextView workoutNameTextView;
        TextView workoutTimestampTextView;

        WorkoutViewHolder(View itemView) {
            super(itemView);
            // Find the TextViews within the workout_list_item.xml layout
            workoutNameTextView = itemView.findViewById(R.id.workoutNameTextView);
            workoutTimestampTextView = itemView.findViewById(R.id.workoutTimestampTextView);
        }
    }
}