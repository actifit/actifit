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
            // 0..7: sport-derived defaults
            0xFF607D8B, // 0 Wolf     — slate (endurance / walking, hiking)
            0xFFFF9800, // 1 Rabbit   — amber (running / sprinting)
            0xFF8D6E63, // 2 Mustang  — brown (cycling / distance)
            0xFF00B8D4, // 3 Dolphin  — aqua  (swimming)
            0xFF5D4037, // 4 Gorilla  — dark  (strength / boxing / crossfit)
            0xFFFF5722, // 5 Tiger    — red-orange (sports / HIIT / cardio)
            0xFFEC407A, // 6 Flamingo — pink  (yoga / pilates / balance)
            0xFF3F51B5, // 7 Eagle    — indigo (mixed / default)
            // 8..19: extra free-choice animals
            0xFFF9A825, // 8  Lion
            0xFF795548, // 9  Bear
            0xFF546E7A, // 10 Shark
            0xFFA1887F, // 11 Kangaroo
            0xFFEF6C00, // 12 Fox
            0xFF455A64, // 13 Panda
            0xFF388E3C, // 14 Turtle
            0xFFB08968, // 15 Dog
            0xFF37474F, // 16 Penguin
            0xFF6D4C41, // 17 Owl
            0xFF00897B, // 18 Dragon
            0xFFAB47BC  // 19 Unicorn
    };
    private static final String[] ANIMAL_NAMES = {
            "Wolf", "Rabbit", "Mustang", "Dolphin", "Gorilla", "Tiger", "Flamingo", "Eagle",
            "Lion", "Bear", "Shark", "Kangaroo", "Fox", "Panda", "Turtle", "Dog",
            "Penguin", "Owl", "Dragon", "Unicorn"
    };
    private static final String[] ANIMAL_EMOJI = {
            "🐺", "🐇", "🐎", "🐬", "🦍", "🐅", "🦩", "🦅",
            "🦁", "🐻", "🦈", "🦘", "🦊", "🐼", "🐢", "🐕",
            "🐧", "🦉", "🐉", "🦄"
    };
    // Noto Animated Emoji Lottie assets (bundled in assets/animals/, keyed by emoji codepoint)
    private static final String[] ANIMAL_LOTTIE = {
            "animals/1f43a.json", // Wolf
            "animals/1f407.json", // Rabbit
            "animals/1f40e.json", // Mustang (horse)
            "animals/1f42c.json", // Dolphin
            "animals/1f98d.json", // Gorilla
            "animals/1f405.json", // Tiger
            "animals/1f9a9.json", // Flamingo
            "animals/1f985.json", // Eagle
            "animals/1f981.json", // Lion
            "animals/1f43b.json", // Bear
            "animals/1f988.json", // Shark
            "animals/1f998.json", // Kangaroo
            "animals/1f98a.json", // Fox
            "animals/1f43c.json", // Panda
            "animals/1f422.json", // Turtle
            "animals/1f415.json", // Dog
            "animals/1f427.json", // Penguin
            "animals/1f989.json", // Owl
            "animals/1f409.json", // Dragon
            "animals/1f984.json"  // Unicorn
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

    public static String companionLottieAsset(int index) {
        return ANIMAL_LOTTIE[clamp(index, ANIMAL_LOTTIE.length)];
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
    private final Paint emojiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // opaque disc painted in the card colour behind the centre content of the multi-ring dashboard,
    // so the step count / labels sit on a clean, high-contrast surface instead of over the arcs+glow
    private final Paint centerFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    // secondary activity-ring colours (outer ring uses the animal's signature colour)
    private static final int COLOR_DISTANCE = 0xFF00C9B1; // teal
    private static final int COLOR_CALORIES = 0xFFFFB300; // amber

    private float fillFraction = 0f;   // 0..1 today's step-goal progress (outer ring)
    private float distFraction = 0f;   // 0..1 distance-goal progress (middle ring)
    private float calFraction = 0f;    // 0..1 calorie-goal progress (inner ring)
    private boolean multiRing = false; // profile shows 3 activity rings; header shows 1
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
     * Multi-metric "activity rings" mode (profile): outer = steps, middle = distance,
     * inner = calories, each a 0..1 fraction of its own goal. The animal runs the outer ring.
     */
    public void setActivityRings(float steps, float distance, float calories,
                                 int level, boolean wilting) {
        this.multiRing = true;
        this.fillFraction = clamp01(steps);
        this.distFraction = clamp01(distance);
        this.calFraction = clamp01(calories);
        this.level = clampLevel(level);
        this.wilting = wilting;
        invalidate();
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
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

    private boolean nightMode() {
        return (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * In dark mode, lifts a ring colour to a minimum brightness so dark companion colours
     * (e.g. Penguin slate) don't vanish against the dark card. No-op in light mode / for
     * already-bright colours.
     */
    private int visibleRingColor(int color) {
        if (!nightMode()) return color;
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (hsv[2] < 0.72f) hsv[2] = 0.72f;
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
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        if (multiRing) {
            // three concentric activity rings: outer steps, middle distance, inner calories
            float stroke = R * 0.085f;
            float glowMax = R * 0.15f;
            float outer = (R - glowMax - stroke * 0.5f) * (1f + 0.03f * breath);
            if (outer <= 0) return;
            float gap = stroke * 2.3f;
            float middle = outer - gap;
            float inner = middle - gap;
            float glowRadius = Math.min(glowMax, R * (0.06f + 0.03f * level) * (0.6f + 0.8f * breath));

            if (inner > 0) {
                drawRing(canvas, cx, cy, inner, stroke, COLOR_CALORIES, calFraction, false, 0, intensity, breath);
            }
            if (middle > 0) {
                drawRing(canvas, cx, cy, middle, stroke, COLOR_DISTANCE, distFraction, false, 0, intensity, breath);
            }
            drawRing(canvas, cx, cy, outer, stroke, color, fillFraction, true, glowRadius, intensity, breath);

            // clean opaque centre in the card colour: fills the clear zone inside the innermost ring
            // (and masks any inward glow bleed) so overlaid counter text reads crisply
            float discRadius = inner - stroke * 0.5f;
            if (discRadius > 0) {
                centerFillPaint.setStyle(Paint.Style.FILL);
                centerFillPaint.setColor(getContext().getColor(
                        io.actifit.fitnesstracker.actifitfitnesstracker.R.color.md_theme_cardBackground));
                canvas.drawCircle(cx, cy, discRadius, centerFillPaint);
            }

            if (showAnimal) {
                drawAnimal(canvas, cx, cy, outer, R);
            }
        } else {
            // single ring (header / compact identity marker)
            float stroke = R * 0.13f;
            float glowMax = R * 0.30f;
            float radius = (R - glowMax - stroke * 0.5f) * (1f + 0.03f * breath);
            if (radius <= 0) return;
            float glowRadius = Math.min(glowMax, R * (0.09f + 0.035f * level) * (0.6f + 0.8f * breath));
            drawRing(canvas, cx, cy, radius, stroke, color, fillFraction, true, glowRadius, intensity, breath);

            if (showAnimal) {
                drawAnimal(canvas, cx, cy, radius, R);
            }
        }

        if (animating) {
            postInvalidateOnAnimation();
        }
    }

    // draws one ring: faint full track + optional blurred glow + the progress arc
    private void drawRing(Canvas canvas, float cx, float cy, float radius, float stroke, int color,
                          float fraction, boolean glow, float glowRadius, float intensity, float breath) {
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        boolean night = nightMode();
        int arcColor = night ? visibleRingColor(color) : color;

        // track groove: coloured in light mode (looks good); a neutral light groove in dark mode so
        // every ring stays visible regardless of how dark its colour is
        trackPaint.setColor(night ? 0xFFFFFFFF : color);
        trackPaint.setAlpha(clampAlpha((int) ((night ? 46 : 55) * intensity)));
        trackPaint.setStrokeWidth(stroke);
        canvas.drawCircle(cx, cy, radius, trackPaint);

        float sweep = 360f * fraction;
        if (glow) {
            glowPaint.setColor(arcColor);
            glowPaint.setStrokeWidth(stroke);
            glowPaint.setAlpha(clampAlpha((int) ((70 + 120 * breath + level * 6) * intensity)));
            glowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, glowRadius), BlurMaskFilter.Blur.NORMAL));
            if (sweep > 0) {
                canvas.drawArc(arcBounds, -90f, sweep, false, glowPaint);
            } else {
                canvas.drawCircle(cx, cy, radius, glowPaint);
            }
        }
        if (sweep > 0) {
            arcPaint.setColor(arcColor);
            arcPaint.setAlpha(clampAlpha((int) (255 * intensity)));
            arcPaint.setStrokeWidth(stroke);
            arcPaint.setMaskFilter(null);
            canvas.drawArc(arcBounds, -90f, sweep, false, arcPaint);
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
