package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public class LetterDrawable extends Drawable {
    private final String letter;
    private Paint paint;

    public LetterDrawable(String letter) {
        this.letter = letter;
        paint = new Paint();
        paint.setColor(Color.BLACK); // Change the color as needed
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5); // Adjust the stroke width as needed
        paint.setAntiAlias(true);
    }

    @Override
    public void draw(Canvas canvas) {
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Draw the circle
        int radius = Math.min(width, height) / 2;
        int centerX = width / 2;
        int centerY = height / 2;
        canvas.drawCircle(centerX, centerY, radius, paint);

        // Draw the letter in the center
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(radius * 2);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(letter, centerX, centerY + (paint.getTextSize() / 3), paint);
    }

    @Override
    public void setAlpha(int alpha) {
        // Implement if needed
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // Implement if needed
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
