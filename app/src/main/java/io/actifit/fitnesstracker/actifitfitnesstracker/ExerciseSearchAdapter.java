package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide; // Assuming Glide is used for image loading

import java.util.List;
import java.util.Locale;
import android.content.Intent;

public class ExerciseSearchAdapter extends RecyclerView.Adapter<ExerciseSearchAdapter.ExerciseViewHolder> {

    private List<Exercise> exerciseList;
    private Context context;
    private OnExerciseClickListener listener;

    public interface OnExerciseClickListener {
        void onExerciseClick(Exercise exercise);
    }

    public ExerciseSearchAdapter(Context context, List<Exercise> exerciseList, OnExerciseClickListener listener) {
        this.context = context;
        this.exerciseList = exerciseList;
        this.listener = listener;
    }

    public void setExerciseList(List<Exercise> newExerciseList) {
        this.exerciseList = newExerciseList;
        notifyDataSetChanged();
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
        holder.bind(exercise, listener);
    }

    @Override
    public int getItemCount() {
        return exerciseList.size();
    }

    static class ExerciseViewHolder extends RecyclerView.ViewHolder {
        TextView exerciseNameTextView;
        TextView exerciseBodyPartTextView;
        TextView exerciseEquipmentTextView;
        TextView exerciseLevelTextView;
        ImageView exerciseImageView;

        public ExerciseViewHolder(@NonNull View itemView) {
            super(itemView);
            exerciseNameTextView = itemView.findViewById(R.id.exerciseNameTextView);
            exerciseBodyPartTextView = itemView.findViewById(R.id.exerciseBodyPartTextView);
            exerciseEquipmentTextView = itemView.findViewById(R.id.exerciseEquipmentTextView);
            exerciseLevelTextView = itemView.findViewById(R.id.exerciseLevelTextView);
            exerciseImageView = itemView.findViewById(R.id.exerciseImageView);
        }

        public void bind(final Exercise exercise, final OnExerciseClickListener listener) {
            exerciseNameTextView.setText(exercise.getName());
            exerciseBodyPartTextView.setText(String.format(Locale.getDefault(), "%s %s", itemView.getContext().getString(R.string.filter_by_body_part), exercise.getBodyPart()));
            exerciseEquipmentTextView.setText(String.format(Locale.getDefault(), "%s %s", itemView.getContext().getString(R.string.filter_by_equipment), exercise.getEquipment()));
            exerciseLevelTextView.setText(String.format(Locale.getDefault(), "%s %s", itemView.getContext().getString(R.string.filter_by_level), exercise.getLevel()));


            // Load image using Glide
            /*if (exercise.getImages() != null && !exercise.getImages().isEmpty()) {
                String imageFileName = exercise.getImages().get(0);
                // Assuming exercise images are in 'assets/exercise_images/'
                String imageUrl = "file:///android_asset/exercise_images/" + imageFileName;
                Glide.with(itemView.getContext())
                        .load(Uri.parse(imageUrl))
                        .placeholder(R.drawable.ic_placeholder_exercise)
                        .error(R.drawable.ic_placeholder_exercise)
                        .into(exerciseImageView);
            } else {
                exerciseImageView.setImageResource(R.drawable.ic_placeholder_exercise);
            }*/
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

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onExerciseClick(exercise);
                }
            });
        }
    }
}