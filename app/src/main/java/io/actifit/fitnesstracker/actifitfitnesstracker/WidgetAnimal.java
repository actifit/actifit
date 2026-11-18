package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.Log;

import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * A still of the companion's Lottie animation for the home-screen widget (RemoteViews cannot host
 * Lottie). Rendered once per companion (and wilting state) on the widget thread, then cached in
 * memory and in the app's cache dir. Kept small: it travels in every widget update.
 */
final class WidgetAnimal {

    private static final String TAG = "ActifitWidget";
    /** 128 px covers the 26-32 dp centre at up to ~4x density; ~64 KB per bitmap. */
    private static final int SIZE_PX = 128;
    /** Bump when the rendering changes, so old cached files are not reused. */
    private static final int CACHE_VERSION = 1;
    /** Wilting companions are drained like the dashboard's aura (AuraView.auraColor: saturation x0.35). */
    private static final float WILTING_SATURATION = 0.35f;

    private static String cachedKey;
    private static Bitmap cachedBitmap;
    /** Keys whose render failed in this process: not re-parsed on every push (the emoji is shown). */
    private static final Set<String> failedKeys = new HashSet<>();

    private WidgetAnimal() {
    }

    /** The companion still, or null when it could not be rendered (the caller shows the emoji). Widget thread only. */
    static Bitmap get(Context app, int companion, boolean wilting) {
        String key = cacheKey(companion, wilting);
        if (key.equals(cachedKey) && cachedBitmap != null) return cachedBitmap;
        if (failedKeys.contains(key)) return null;

        File file = new File(app.getCacheDir(), key + ".png");
        Bitmap bmp = file.isFile() ? BitmapFactory.decodeFile(file.getPath()) : null;
        if (bmp == null) {
            bmp = render(app, companion, wilting);
            if (bmp == null) {
                failedKeys.add(key);
                return null;
            }
            writeCache(bmp, file);
        }
        cachedKey = key;
        cachedBitmap = bmp;
        return bmp;
    }

    /**
     * Derived from the asset name (not the companion index), the wilting state and the app version, so
     * a reordered or replaced animal, or an app update, never reuses an old file.
     */
    private static String cacheKey(int companion, boolean wilting) {
        String asset = AuraView.companionLottieAsset(companion);
        String name = asset.substring(asset.lastIndexOf('/') + 1).replace(".json", "");
        return "widget_animal_v" + CACHE_VERSION + "_" + BuildConfig.VERSION_CODE + "_" + name
                + (wilting ? "_w" : "");
    }

    /** Writes via a temp file + rename, so a failed or interrupted write never leaves a truncated PNG. */
    private static void writeCache(Bitmap bmp, File file) {
        File tmp = new File(file.getPath() + ".tmp");
        boolean ok = false;
        try (OutputStream out = new FileOutputStream(tmp)) {
            ok = bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            Log.w(TAG, "could not cache the widget animal", e);
        }
        if (!ok || !tmp.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static Bitmap render(Context app, int companion, boolean wilting) {
        try {
            LottieResult<LottieComposition> result =
                    LottieCompositionFactory.fromAssetSync(app, AuraView.companionLottieAsset(companion));
            LottieComposition composition = result.getValue();
            if (composition == null) return null;

            LottieDrawable drawable = new LottieDrawable();
            drawable.setComposition(composition);
            drawable.setProgress(0f); // first frame: the Noto emoji at rest
            drawable.setBounds(0, 0, SIZE_PX, SIZE_PX);

            Bitmap bmp = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            if (wilting) {
                ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(WILTING_SATURATION);
                Paint paint = new Paint();
                paint.setColorFilter(new ColorMatrixColorFilter(cm));
                canvas.saveLayer(null, paint);
                drawable.draw(canvas);
                canvas.restore();
            } else {
                drawable.draw(canvas);
            }
            return bmp;
        } catch (Throwable t) {
            Log.w(TAG, "could not render the widget animal", t);
            return null;
        }
    }
}
