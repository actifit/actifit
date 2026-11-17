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

    private WidgetAnimal() {
    }

    /** The companion still, or null when it could not be rendered (the caller shows the emoji). Widget thread only. */
    static Bitmap get(Context app, int companion, boolean wilting) {
        String key = "widget_animal_v" + CACHE_VERSION + "_" + companion + (wilting ? "_w" : "");
        if (key.equals(cachedKey) && cachedBitmap != null) return cachedBitmap;

        File file = new File(app.getCacheDir(), key + ".png");
        Bitmap bmp = file.isFile() ? BitmapFactory.decodeFile(file.getPath()) : null;
        if (bmp == null) {
            bmp = render(app, companion, wilting);
            if (bmp == null) return null;
            try (OutputStream out = new FileOutputStream(file)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception e) {
                Log.w(TAG, "could not cache the widget animal", e);
            }
        }
        cachedKey = key;
        cachedBitmap = bmp;
        return bmp;
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
