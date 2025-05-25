package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.List;

public class ExerciseAdapter extends RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder> {

    private List<Exercise> exerciseList;

    public ExerciseAdapter(List<Exercise> exerciseList) {
        this.exerciseList = exerciseList;
    }

    @NonNull
    @Override
    public ExerciseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.exercise_item, parent, false);
        return new ExerciseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExerciseViewHolder holder, int position) {
        Log.d("wokroutwizardactivity EA", "item at "+position);
        Exercise exercise = exerciseList.get(position);
        holder.exerciseNameTextView.setText(exercise.getName());
        holder.exerciseSetsTextView.setText(exercise.getSets());
        holder.exerciseRepsTextView.setText(exercise.getReps());

        String durationString = exercise.getDuration() == null ? "N/A" :  exercise.getDuration();
        holder.exerciseDurationTextView.setText(durationString);
        // Load image
        Picasso.get()
                .load(exercise.getStartPositionImageUrl())
                .placeholder(R.drawable.ic_launcher_foreground) // Optional placeholder
                .error(R.drawable.ic_launcher_foreground) // Optional error image
                .into(holder.exerciseImageView);

        // Display Days
        if (exercise.getDays() != null && !exercise.getDays().isEmpty()){
            String daysString = String.join(", ", exercise.getDays());
            holder.exerciseDaysTextView.setText(daysString);
        }  else{
            holder.exerciseDaysTextView.setText("Every day"); // default case
        }

        //Setting onClickListener for itemview
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(holder.itemView.getContext(), ExerciseDetailActivity.class);
            intent.putExtra("exercise", exercise); // Pass exercise
            holder.itemView.getContext().startActivity(intent);
        });

    }

    @Override
    public int getItemCount() {
        return exerciseList.size();
    }

    static class ExerciseViewHolder extends RecyclerView.ViewHolder {

        TextView exerciseNameTextView;
        TextView exerciseSetsTextView;
        TextView exerciseRepsTextView;
        TextView exerciseDurationTextView;
        TextView exerciseDaysTextView;
        ImageView exerciseImageView;


        public ExerciseViewHolder(@NonNull View itemView) {
            super(itemView);
            exerciseNameTextView = itemView.findViewById(R.id.exerciseNameTextView);
            exerciseSetsTextView = itemView.findViewById(R.id.exerciseSetsTextView);
            exerciseRepsTextView = itemView.findViewById(R.id.exerciseRepsTextView);
            exerciseDurationTextView = itemView.findViewById(R.id.exerciseDurationTextView);
            exerciseDaysTextView = itemView.findViewById(R.id.exerciseDaysTextView);
            exerciseImageView = itemView.findViewById(R.id.exerciseImageView);
        }
    }
}