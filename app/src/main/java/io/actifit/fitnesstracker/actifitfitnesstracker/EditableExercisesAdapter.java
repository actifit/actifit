package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class EditableExercisesAdapter extends RecyclerView.Adapter<EditableExercisesAdapter.EditableExerciseViewHolder> {

    private List<Exercise> exercises;
    private Context context;
    private OnRemoveExerciseListener listener;

    public interface OnRemoveExerciseListener {
        void onRemoveExercise(Exercise exercise);
    }

    public EditableExercisesAdapter(Context context, List<Exercise> exercises, OnRemoveExerciseListener listener) {
        this.context = context;
        this.exercises = exercises;
        this.listener = listener;
    }

    public void setExercises(List<Exercise> newExercises) {
        this.exercises = newExercises;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EditableExerciseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.editable_exercise_list_item, parent, false);
        return new EditableExerciseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EditableExerciseViewHolder holder, int position) {
        Exercise exercise = exercises.get(position);
        holder.bind(exercise, listener);
    }

    @Override
    public int getItemCount() {
        return exercises.size();
    }

    static class EditableExerciseViewHolder extends RecyclerView.ViewHolder {
        ImageView exerciseImageView;
        TextView exerciseNameTextView;
        ImageButton removeExerciseButton;

        public EditableExerciseViewHolder(@NonNull View itemView) {
            super(itemView);
            exerciseImageView = itemView.findViewById(R.id.exerciseImageView);
            exerciseNameTextView = itemView.findViewById(R.id.exerciseNameTextView);
            removeExerciseButton = itemView.findViewById(R.id.removeExerciseButton);
        }

        public void bind(final Exercise exercise, final OnRemoveExerciseListener listener) {
            exerciseNameTextView.setText(exercise.getName());

            String imageUrl = exercise.getStartPositionImageUrl(); // Or get any relevant image
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(Uri.parse(imageUrl))
                        .placeholder(R.drawable.ic_placeholder_exercise)
                        .error(R.drawable.ic_placeholder_exercise)
                        .into(exerciseImageView);
            } else {
                exerciseImageView.setImageResource(R.drawable.ic_placeholder_exercise);
            }

            removeExerciseButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRemoveExercise(exercise);
                }
            });
        }
    }
}