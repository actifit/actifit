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
 * The "living companion" for the profile screen — an energy aura drawn entirely in code.
 *
 * The user picks a COMPANION element (Ember, Tide, Verdant, Storm, Frost, Solar) which sets the
 * aura's colour identity. Its VITALITY (level 0..5, driven by streak for self / rank for others)
 * modulates how bright, saturated and large the aura is, and the arc SWEEP shows today's goal
 * progress. It continuously breathes so an open profile feels alive.
 *
 * The breathing is driven by the frame clock (postInvalidateOnAnimation) rather than a
 * ValueAnimator, so it keeps animating even when "Animator duration scale" is turned off in
 * Developer Options.
 */
public class AuraView extends View {

    // ── Companion elements ───────────────────────────────────────────────────────
    private static final int[] COMPANION_COLORS = {
            0xFFFF5722, // 0 Ember   — fiery orange-red
            0xFF00B8D4, // 1 Tide    — aqua
            0xFF2E9E5B, // 2 Verdant — green
            0xFF7C4DFF, // 3 Storm   — violet
            0xFF4FC3F7, // 4 Frost   — ice blue
            0xFFFFB300  // 5 Solar   — gold
    };
    private static final String[] COMPANION_NAMES = {
            "Ember", "Tide", "Verdant", "Storm", "Frost", "Solar"
    };
    private static final String[] COMPANION_EMOJI = {
            "🔥", "🌊", "🌿", "⚡", "❄️", "☀️"
    };

    // vitality tier names (index = level 0..5)
    private static final String[] TIER_NAMES = {
            "Dormant", "Spark", "Ember", "Blaze", "Radiant", "Ascendant"
    };

    private static final long PULSE_PERIOD_MS = 2200L;

    public static int companionCount() {
        return COMPANION_COLORS.length;
    }

    public static int companionColor(int index) {
        return COMPANION_COLORS[clamp(index, COMPANION_COLORS.length)];
    }

    public static String companionName(int index) {
        return COMPANION_NAMES[clamp(index, COMPANION_NAMES.length)];
    }

    public static String companionEmoji(int index) {
        return COMPANION_EMOJI[clamp(index, COMPANION_EMOJI.length)];
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

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private float fillFraction = 0f;   // 0..1 today's goal progress
    private int level = 0;             // 0..5 vitality tier
    private int companion = 0;         // element index
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
    }

    public void setCompanion(int companionIndex) {
        this.companion = clamp(companionIndex, COMPANION_COLORS.length);
        invalidate();
    }

    /**
     * @param fillFraction today's progress toward the goal, 0..1
     * @param level        vitality tier 0..5
     */
    public void setAura(float fillFraction, int level) {
        this.fillFraction = Math.max(0f, Math.min(1f, fillFraction));
        this.level = clampLevel(level);
        invalidate();
    }

    // vitality modulates saturation + brightness of the chosen companion colour
    private int auraColor() {
        float[] hsv = new float[3];
        Color.colorToHSV(COMPANION_COLORS[companion], hsv);
        hsv[1] = Math.min(1f, hsv[1] * (0.6f + 0.08f * level));
        hsv[2] = Math.min(1f, hsv[2] * (0.72f + 0.056f * level));
        return Color.HSVToColor(hsv);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // smooth 0..1 breathing from the frame clock (immune to animator scale)
        double t = (SystemClock.uptimeMillis() % PULSE_PERIOD_MS) / (double) PULSE_PERIOD_MS;
        float breath = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * t)); // 0..1 smooth

        int color = auraColor();

        // size everything relative to the view radius so the same aura works at any size
        // (the large profile header and the small top-bar icon both use this view)
        float R = Math.min(getWidth(), getHeight()) / 2f;
        if (R <= 0) return;
        float stroke = R * 0.13f;
        float glowMax = R * 0.30f;

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float baseRadius = R - glowMax - stroke * 0.5f;
        if (baseRadius <= 0) return;
        // subtle overall breathing of the ring itself
        float radius = baseRadius * (1f + 0.03f * breath);

        // glow grows with tier and breathes clearly, capped so it never clips the bounds
        float glowRadius = Math.min(glowMax, R * (0.09f + 0.035f * level) * (0.6f + 0.8f * breath));

        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // faint full track always visible
        trackPaint.setColor(color);
        trackPaint.setAlpha(55);
        trackPaint.setStrokeWidth(stroke);
        canvas.drawCircle(cx, cy, radius, trackPaint);

        float sweep = 360f * fillFraction;

        // blurred glow pass — brighter at higher tiers and pulse peaks
        glowPaint.setColor(color);
        glowPaint.setStrokeWidth(stroke);
        int glowAlpha = (int) (70 + 120 * breath + level * 6);
        glowPaint.setAlpha(Math.max(0, Math.min(255, glowAlpha)));
        glowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, glowRadius), BlurMaskFilter.Blur.NORMAL));
        if (sweep > 0) {
            canvas.drawArc(arcBounds, -90f, sweep, false, glowPaint);
        } else {
            // no progress today — the whole ring softly glows so the companion still looks alive
            canvas.drawCircle(cx, cy, radius, glowPaint);
        }

        // solid progress arc on top
        if (sweep > 0) {
            arcPaint.setColor(color);
            arcPaint.setAlpha(255);
            arcPaint.setStrokeWidth(stroke);
            canvas.drawArc(arcBounds, -90f, sweep, false, arcPaint);
        }

        if (animating) {
            postInvalidateOnAnimation();
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
