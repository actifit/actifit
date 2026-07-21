package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * The "living companion" for the profile screen — a fitness SPIRIT ANIMAL that runs your daily
 * lap inside an energy aura.
 *
 *  - The animal (emoji) reflects how you train and RUNS around the ring to the position of
 *    today's goal progress, bouncing as it goes.
 *  - Its SIZE grows with your fitness ladder tier (Couch → Champion), driven by streak (self)
 *    or rank (others), and higher tiers add flourishes (halo, energy comet).
 *  - When your streak is at risk it WILTS: the aura fades/flickers and the animal falls asleep.
 *
 * Breathing/animation is driven by the frame clock (postInvalidateOnAnimation) so it keeps
 * moving even when "Animator duration scale" is off in Developer Options, and everything is
 * sized proportionally so the same view works at profile size and around the header avatar.
 */
public class AuraView extends View {

    // ── Spirit animals (emoji, name, signature aura colour) ──────────────────────
    private static final int[] ANIMAL_COLORS = {
            0xFF607D8B, // 0 Wolf     — slate (endurance / walking, hiking)
            0xFFFF9800, // 1 Cheetah  — amber (running / sprinting)
            0xFF8D6E63, // 2 Mustang  — brown (cycling / distance)
            0xFF00B8D4, // 3 Dolphin  — aqua  (swimming)
            0xFF5D4037, // 4 Gorilla  — dark  (strength / boxing / crossfit)
            0xFFFF5722, // 5 Tiger    — red-orange (sports / HIIT / cardio)
            0xFFEC407A, // 6 Flamingo — pink  (yoga / pilates / balance)
            0xFF3F51B5  // 7 Eagle    — indigo (mixed / default)
    };
    private static final String[] ANIMAL_NAMES = {
            "Wolf", "Cheetah", "Mustang", "Dolphin", "Gorilla", "Tiger", "Flamingo", "Eagle"
    };
    private static final String[] ANIMAL_EMOJI = {
            "🐺", "🐆", "🐎", "🐬", "🦍", "🐅", "🦩", "🦅"
    };

    // fitness ladder tier names (index = level 0..5)
    private static final String[] TIER_NAMES = {
            "Couch", "Active", "Fit", "Athlete", "Elite", "Champion"
    };

    private static final long PULSE_PERIOD_MS = 2200L;

    public static int companionCount() {
        return ANIMAL_COLORS.length;
    }

    public static int companionColor(int index) {
        return ANIMAL_COLORS[clamp(index, ANIMAL_COLORS.length)];
    }

    public static String companionName(int index) {
        return ANIMAL_NAMES[clamp(index, ANIMAL_NAMES.length)];
    }

    public static String companionEmoji(int index) {
        return ANIMAL_EMOJI[clamp(index, ANIMAL_EMOJI.length)];
    }

    public static String tierName(int level) {
        return TIER_NAMES[clamp(level, TIER_NAMES.length)];
    }

    private static int clamp(int v, int size) {
        if (v < 0) return 0;
        if (v >= size) return size - 1;
        return v;
    }

    private static int clampLevel(int level) {
        return clamp(level, 6);
    }

    private static int clampAlpha(int a) {
        return Math.max(0, Math.min(255, a));
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emojiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private float fillFraction = 0f;   // 0..1 today's goal progress
    private int level = 0;             // 0..5 vitality/ladder tier
    private int companion = 0;         // animal index
    private boolean wilting = false;   // streak at risk → fading/flicker/sleep
    private boolean showAnimal = true; // header aura hides the animal (it lives in the step counter)
    private boolean animating = false;

    public AuraView(Context context) {
        super(context);
        init();
    }

    public AuraView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AuraView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // BlurMaskFilter needs software rendering
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        haloPaint.setStyle(Paint.Style.STROKE);
        haloPaint.setStrokeCap(Paint.Cap.ROUND);
        emojiPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setCompanion(int companionIndex) {
        this.companion = clamp(companionIndex, ANIMAL_COLORS.length);
        invalidate();
    }

    public void setShowAnimal(boolean show) {
        this.showAnimal = show;
        invalidate();
    }

    /**
     * @param fillFraction today's progress toward the goal, 0..1
     * @param level        fitness ladder tier 0..5
     * @param wilting      whether the streak is at risk (fading/sleep state)
     */
    public void setAura(float fillFraction, int level, boolean wilting) {
        this.fillFraction = Math.max(0f, Math.min(1f, fillFraction));
        this.level = clampLevel(level);
        this.wilting = wilting;
        invalidate();
    }

    // tier modulates saturation + brightness of the animal's signature colour;
    // wilting drains it toward a sickly, desaturated fade
    private int auraColor() {
        float[] hsv = new float[3];
        Color.colorToHSV(ANIMAL_COLORS[companion], hsv);
        hsv[1] = Math.min(1f, hsv[1] * (0.6f + 0.08f * level));
        hsv[2] = Math.min(1f, hsv[2] * (0.72f + 0.056f * level));
        if (wilting) {
            hsv[1] *= 0.35f;
            hsv[2] *= 0.85f;
        }
        return Color.HSVToColor(hsv);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        long now = SystemClock.uptimeMillis();

        // smooth 0..1 breathing from the frame clock (immune to animator scale)
        double t = (now % PULSE_PERIOD_MS) / (double) PULSE_PERIOD_MS;
        float breath = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * t)); // 0..1 smooth

        // wilting: irregular flicker that dips the whole aura's intensity
        float intensity = 1f;
        if (wilting) {
            double tf = (now % 1300L) / 1300.0;
            float irregular = (float) ((Math.sin(2 * Math.PI * tf)
                    + 0.6 * Math.sin(2 * Math.PI * tf * 2.3 + 1.0)) / 1.6); // ~ -1..1, uneven
            intensity = 0.4f + 0.6f * (0.5f + 0.5f * irregular);
        }

        int color = auraColor();

        // size everything relative to the view radius so the same aura works at any size
        float R = Math.min(getWidth(), getHeight()) / 2f;
        if (R <= 0) return;
        float stroke = R * 0.13f;
        float glowMax = R * 0.30f;

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float baseRadius = R - glowMax - stroke * 0.5f;
        if (baseRadius <= 0) return;
        float radius = baseRadius * (1f + 0.03f * breath);

        float glowRadius = Math.min(glowMax, R * (0.09f + 0.035f * level) * (0.6f + 0.8f * breath));

        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // flourishes + the running animal only render on the large profile aura; the small
        // header aura stays a clean identity marker (colour + glow + breathing)
        boolean detailed = R > 150f;

        // outer halo ring (Elite, tier 4+)
        if (detailed && level >= 4) {
            haloPaint.setColor(color);
            haloPaint.setStrokeWidth(stroke * 0.5f);
            haloPaint.setAlpha(clampAlpha((int) ((40 + 30 * breath) * intensity)));
            haloPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, stroke), BlurMaskFilter.Blur.NORMAL));
            canvas.drawCircle(cx, cy, radius + stroke * 1.5f, haloPaint);
        }

        // faint full track always visible
        trackPaint.setColor(color);
        trackPaint.setAlpha(clampAlpha((int) (55 * intensity)));
        trackPaint.setStrokeWidth(stroke);
        canvas.drawCircle(cx, cy, radius, trackPaint);

        float sweep = 360f * fillFraction;

        // blurred glow pass — brighter at higher tiers and pulse peaks
        glowPaint.setColor(color);
        glowPaint.setStrokeWidth(stroke);
        glowPaint.setAlpha(clampAlpha((int) ((70 + 120 * breath + level * 6) * intensity)));
        glowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, glowRadius), BlurMaskFilter.Blur.NORMAL));
        if (sweep > 0) {
            canvas.drawArc(arcBounds, -90f, sweep, false, glowPaint);
        } else {
            canvas.drawCircle(cx, cy, radius, glowPaint);
        }

        // solid progress arc — the "track" the animal has run so far
        if (sweep > 0) {
            arcPaint.setColor(color);
            arcPaint.setAlpha(clampAlpha((int) (255 * intensity)));
            arcPaint.setStrokeWidth(stroke);
            arcPaint.setMaskFilter(null);
            canvas.drawArc(arcBounds, -90f, sweep, false, arcPaint);
        }

        // rotating white-hot energy comet (Champion, tier 5)
        if (detailed && level >= 5 && sweep > 0 && !wilting) {
            float rot = 360f * ((now % 2500L) / 2500f);
            arcPaint.setColor(0xFFFFFFFF);
            arcPaint.setAlpha(clampAlpha((int) (190 * intensity)));
            arcPaint.setStrokeWidth(stroke * 0.8f);
            arcPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, stroke * 0.8f), BlurMaskFilter.Blur.NORMAL));
            canvas.drawArc(arcBounds, -90f + rot, 28f, false, arcPaint);
            arcPaint.setMaskFilter(null);
        }

        // the spirit animal sits at today's progress position on the ring (grows with tier).
        // The header aura opts out — the animal lives inside the step counter instead.
        if (showAnimal) {
            drawAnimal(canvas, cx, cy, radius, R);
        }

        if (animating) {
            postInvalidateOnAnimation();
        }
    }

    private void drawAnimal(Canvas canvas, float cx, float cy, float radius, float R) {
        // grows with fitness tier; a touch larger on the small header so it stays legible
        boolean detailed = R > 150f;
        float sizeFactor = detailed ? (0.20f + 0.045f * level) : 0.42f;
        float animalSize = R * sizeFactor;
        // position on the ring = today's progress (top = 0%, clockwise)
        double ang = Math.toRadians(-90f + 360f * fillFraction);
        float ax = cx + (float) (Math.cos(ang) * radius);
        float ay = cy + (float) (Math.sin(ang) * radius);

        emojiPaint.setTextSize(animalSize);
        emojiPaint.setMaskFilter(null);
        Paint.FontMetrics fm = emojiPaint.getFontMetrics();

        float baseline = ay - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(ANIMAL_EMOJI[companion], ax, baseline, emojiPaint);

        if (wilting && detailed) {
            // a little "sleeping" cue so the fading state reads clearly
            emojiPaint.setTextSize(animalSize * 0.5f);
            Paint.FontMetrics zf = emojiPaint.getFontMetrics();
            canvas.drawText("💤", ax + animalSize * 0.45f,
                    ay - animalSize * 0.45f - (zf.ascent + zf.descent) / 2f, emojiPaint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animating = true;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        animating = false;
        super.onDetachedFromWindow();
    }
}
