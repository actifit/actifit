package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.squareup.picasso.Picasso;
import java.util.Arrays;

public class ExerciseDetailActivity extends AppCompatActivity {

    private TextView exerciseNameTextView;
    private TextView equipmentTextView;
    private TextView primaryMusclesTextView;
    private TextView secondaryMusclesTextView;
    private TextView instructionsTextView;
    private ImageView startPositionImageView;
    private ImageView endPositionImageView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise_detail);

        exerciseNameTextView = findViewById(R.id.exerciseNameTextView);
        equipmentTextView = findViewById(R.id.equipmentTextView);
        primaryMusclesTextView = findViewById(R.id.primaryMusclesTextView);
        secondaryMusclesTextView = findViewById(R.id.secondaryMusclesTextView);
        instructionsTextView = findViewById(R.id.instructionsTextView);
        startPositionImageView = findViewById(R.id.startPositionImageView);
        endPositionImageView = findViewById(R.id.endPositionImageView);

        Exercise exercise = (Exercise) getIntent().getSerializableExtra("exercise");

        if(exercise != null){
            exerciseNameTextView.setText(exercise.getName());

            equipmentTextView.setText("Equipment: " + exercise.getEquipment());

            String primaryMuscles =  "Primary Muscles: " + (exercise.getPrimaryMuscles() != null ? String.join(", ", exercise.getPrimaryMuscles()): "N/A");
            primaryMusclesTextView.setText(primaryMuscles);

            String secondaryMuscles =  "Secondary Muscles: " + (exercise.getSecondaryMuscles() != null ? String.join(", ", exercise.getSecondaryMuscles()): "N/A");
            secondaryMusclesTextView.setText(secondaryMuscles);

            String instructions = "Instructions: " + (exercise.getInstructions() != null ? String.join("\n", exercise.getInstructions()): "N/A");
            instructionsTextView.setText(instructions);
            // Load images (You can load 2 specific images or display first image, will do the latter for simplicity).
            String startImageUrl = exercise.getStartPositionImageUrl();
            String endImageUrl = exercise.getEndPositionImageUrl();


            if (startImageUrl != null && !startImageUrl.isEmpty()) {
                Picasso.get()
                        .load(startImageUrl)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(startPositionImageView);
            } else{
                startPositionImageView.setImageResource(R.drawable.ic_launcher_foreground);
            }

            if (endImageUrl != null && !endImageUrl.isEmpty()) {
                Picasso.get()
                        .load(endImageUrl)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(endPositionImageView);
            } else{
                endPositionImageView.setImageResource(R.drawable.ic_launcher_foreground);
            }
        }

    }
}