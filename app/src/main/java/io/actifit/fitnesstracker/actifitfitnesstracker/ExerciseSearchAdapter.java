package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide; // Assuming Glide is used for image loading

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.content.Intent;

public class ExerciseSearchAdapter extends RecyclerView.Adapter<ExerciseSearchAdapter.ExerciseViewHolder> {

    private List<Exercise> exerciseList;
    private Context context;
    private OnExerciseClickListener listener;
    private Set<Exercise> selectedExercises;
    private OnSelectionChangeListener selectionChangeListener;

    public interface OnExerciseClickListener {
        void onExerciseClick(Exercise exercise);
    }

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int selectedCount);
    }

    public ExerciseSearchAdapter(Context context, List<Exercise> exerciseList,
                                 OnExerciseClickListener listener,
                                 OnSelectionChangeListener selectionChangeListener) {
        this.context = context;
        this.exerciseList = exerciseList;
        this.listener = listener;
        this.selectedExercises = new HashSet<>(); // Initialize set
        this.selectionChangeListener = selectionChangeListener;
    }

    public void setExerciseList(List<Exercise> newExerciseList) {
        this.exerciseList = newExerciseList;
        // Keep only the selected exercises that are still in the new list
        selectedExercises.retainAll(newExerciseList);
        notifySelectionChanged(); // Notify after list update
        notifyDataSetChanged();
    }

    public Set<Exercise> getSelectedExercises() {
        return selectedExercises;
    }

    public void clearSelections() {
        selectedExercises.clear();
        notifyDataSetChanged(); // Refresh all items to uncheck checkboxes
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedExercises.size());
        }
    }

    @NonNull
    @Override
    public ExerciseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.exercise_list_item_search, parent, false);
        return new ExerciseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExerciseViewHolder holder, int position) {
        Exercise exercise = exerciseList.get(position);
        holder.bind(exercise, listener, selectedExercises);

        holder.exerciseSelectCheckbox.setChecked(selectedExercises.contains(exercise));

        // When the whole item is clicked, toggle selection
        holder.itemView.setOnClickListener(v -> {
            if (selectedExercises.contains(exercise)) {
                selectedExercises.remove(exercise);
            } else {
                selectedExercises.add(exercise);
            }
            holder.exerciseSelectCheckbox.setChecked(selectedExercises.contains(exercise)); // Update checkbox immediately
            notifySelectionChanged(); // Notify activity of selection change

            // Optionally, still trigger the original click listener if needed for detail view
            // This behavior might be confusing with multi-select.
            // For now, a click on the item toggles selection.
            // If you want detail view too, consider a long press for detail or a separate info icon.
        });

        // If you want the icon or name text to *also* trigger detail view (without toggling selection):
         holder.exerciseNameTextView.setOnClickListener(v -> {
             if (listener != null) {
                 listener.onExerciseClick(exercise);
             }
         });
         holder.exerciseImageView.setOnClickListener(v -> {
             if (listener != null) {
                 listener.onExerciseClick(exercise);
             }
         });

        // Prevent checkbox itself from being clickable to avoid double-events,
        // as clicks are handled by the itemView.
        holder.exerciseSelectCheckbox.setClickable(false);
        holder.exerciseSelectCheckbox.setFocusable(false);
    }

    @Override
    public int getItemCount() {
        return exerciseList.size();
    }

    static class ExerciseViewHolder extends RecyclerView.ViewHolder {
        TextView exerciseNameTextView;
        //TextView exerciseBodyPartTextView;
        TextView exerciseEquipmentTextView;
        TextView exerciseLevelTextView;
        ImageView exerciseImageView;
        CheckBox exerciseSelectCheckbox;

        public ExerciseViewHolder(@NonNull View itemView) {
            super(itemView);
            exerciseNameTextView = itemView.findViewById(R.id.exerciseNameTextView);
            //exerciseBodyPartTextView = itemView.findViewById(R.id.exerciseBodyPartTextView);
            exerciseEquipmentTextView = itemView.findViewById(R.id.exerciseEquipmentTextView);
            exerciseLevelTextView = itemView.findViewById(R.id.exerciseLevelTextView);
            exerciseImageView = itemView.findViewById(R.id.exerciseImageView);
            exerciseSelectCheckbox = itemView.findViewById(R.id.exerciseSelectCheckbox);
        }

        public void bind(final Exercise exercise, final OnExerciseClickListener listener, final Set<Exercise> selectedExercises) { // MODIFIED param
            exerciseNameTextView.setText(exercise.getName());
            exerciseEquipmentTextView.setText(String.format(Locale.getDefault(), "%s %s", itemView.getContext().getString(R.string.filter_by_equipment), exercise.getEquipment()));
            exerciseLevelTextView.setText(String.format(Locale.getDefault(), "%s %s", itemView.getContext().getString(R.string.filter_by_level), exercise.getLevel()));

            String imageUrl = exercise.getStartPositionImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(Uri.parse(imageUrl))
                        .placeholder(R.drawable.ic_placeholder_exercise)
                        .error(R.drawable.ic_placeholder_exercise)
                        .into(exerciseImageView);
            } else {
                exerciseImageView.setImageResource(R.drawable.ic_placeholder_exercise);
            }

            // The itemView.setOnClickListener is now handled in onBindViewHolder to manage selection toggle.
            // The original listener.onExerciseClick can still be triggered by individual elements if desired (e.g., long press or an info icon).
        }
    }
}