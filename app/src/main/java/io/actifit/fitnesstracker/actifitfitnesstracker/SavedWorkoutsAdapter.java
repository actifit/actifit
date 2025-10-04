package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton; // NEW: Import ImageButton
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Your adapter class extends RecyclerView.Adapter<YourViewHolder>
public class SavedWorkoutsAdapter extends RecyclerView.Adapter<SavedWorkoutsAdapter.WorkoutViewHolder> {

    private List<WorkoutPlan> workoutList;
    private OnWorkoutActionListener listener; // CHANGED: Now uses the new combined interface

    // CHANGED: Interface for click listener - Define what happens when an item or its delete button is clicked
    public interface OnWorkoutActionListener {
        void onWorkoutClick(WorkoutPlan workout); // Replaces onWorkoutSelected
        void onDeleteWorkout(WorkoutPlan workout); // NEW: For delete button clicks
    }

    // Constructor to provide the data list and the click listener
    public SavedWorkoutsAdapter(List<WorkoutPlan> workoutList, OnWorkoutActionListener listener) { // CHANGED listener type
        this.workoutList = workoutList;
        this.listener = listener;
    }

    // Method to update the list data from outside (e.g., when API response arrives)
    public void setWorkoutList(List<WorkoutPlan> newWorkouts) {
        this.workoutList = newWorkouts;
        notifyDataSetChanged(); // Tell the RecyclerView to refresh the list display
    }

    // NEW: Method to remove a specific item from the list
    public void removeWorkout(WorkoutPlan workout) {
        int position = workoutList.indexOf(workout);
        if (position != -1) { // Ensure the workout exists in the list
            workoutList.remove(position);
            notifyItemRemoved(position); // Notify adapter that an item has been removed
            // Optionally, if the list becomes empty, notify a range change or trigger a full refresh
            if (workoutList.isEmpty()) {
                notifyDataSetChanged(); // For edge case where list becomes empty
            }
        }
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

        // --- Update Timestamp Formatting ---
        Date timestampDate = workout.getTimestamp(); // <-- Get the Date object

        if (timestampDate != null) {
            // Format the Date object
            // Ensure this format matches what you want to display, e.g., "MMM dd, yyyy HH:mm" for a more common display
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            String formattedDate = sdf.format(timestampDate); // <-- Format the Date
            holder.workoutTimestampTextView.setText("Saved: " + formattedDate); // Or just the date
        } else {
            holder.workoutTimestampTextView.setText("Saved: N/A"); // Handle null timestamp
        }

        holder.workoutExercisesTextView.setText(workout.getExercises().size()+" Exercises");


        // Set click listener for the entire item view (existing functionality, updated to new listener method)
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onWorkoutClick(workout); // CHANGED: Calls onWorkoutClick
            }
        });

        // NEW: Set click listener for the delete button
        holder.deleteWorkoutButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteWorkout(workout); // Calls the new onDeleteWorkout method
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
        TextView workoutExercisesTextView;
        ImageButton deleteWorkoutButton; // NEW: Reference for the delete button

        WorkoutViewHolder(View itemView) {
            super(itemView);
            // Find the TextViews within the workout_list_item.xml layout
            workoutNameTextView = itemView.findViewById(R.id.workoutNameTextView);
            workoutTimestampTextView = itemView.findViewById(R.id.workoutTimestampTextView);
            workoutExercisesTextView = itemView.findViewById(R.id.workoutExerciseCountTextView);
            deleteWorkoutButton = itemView.findViewById(R.id.deleteWorkoutButton); // NEW: Initialize the delete button
        }
    }
}