package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The logged-in user's Hive avatar for the home-screen widget, as a small circle. Downloaded at most
 * once a day (or when the user changes) on the widget's download thread, never on the main thread or
 * the render thread, and cached in the app's cache dir; a failed download keeps the cached picture
 * (or none). The cache fields are shared by the render and download threads, hence the locking.
 */
final class WidgetAvatar {

    private static final String TAG = "ActifitWidget";
    /** 80 px covers the 18-22 dp avatar at ~3.5x density; ~25 KB per bitmap. */
    private static final int SIZE_PX = 80;
    private static final int TIMEOUT_MS = 5000;
    /** Hard cap for one download, whatever the per-step timeouts allow (connect per address, redirects, slow reads). */
    private static final long DEADLINE_MS = 10_000L;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    /** Larger images are refused rather than decoded (decode-bomb guard). */
    private static final long MAX_PIXELS = 4_000_000L;

    private static final String PREFS = "actifitWidget";
    private static final String KEY_USER = "avatarUser";
    private static final String KEY_DATE = "avatarDate";
    private static final String FILE_PREFIX = "widget_avatar_";

    /** Disconnects a download that runs past {@link #DEADLINE_MS}. */
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "actifit-widget-avatar-deadline");
        t.setDaemon(true);
        return t;
    });

    // guarded by WidgetAvatar.class
    private static String cachedUser;
    private static Bitmap cachedBitmap;
    /** user + day of the last download scheduled in this process, so it is never scheduled twice */
    private static String attempted;
    /** user whose files were last kept by pruneOtherUsers ("" = logged out), so it runs once per change */
    private static String prunedFor;

    private WidgetAvatar() {
    }

    /** The cached avatar for {@code user}, or null (none yet / logged out). Never touches the network. */
    static synchronized Bitmap cached(Context app, String user) {
        if (user == null || user.isEmpty()) return null;
        if (user.equals(cachedUser) && cachedBitmap != null) return cachedBitmap;
        File file = file(app, user);
        Bitmap bmp = file.isFile() ? BitmapFactory.decodeFile(file.getPath()) : null;
        if (bmp != null) {
            cachedUser = user;
            cachedBitmap = bmp;
        }
        return bmp;
    }

    /**
     * True when the avatar should be downloaded now (a new user, or not yet fetched today). Marks it as
     * attempted, so renders that run before the download finishes don't schedule it again.
     */
    static synchronized boolean needsRefresh(Context app, String user) {
        if (user == null || user.isEmpty()) return false;
        String today = today();
        String key = user + "|" + today;
        if (key.equals(attempted)) return false;
        SharedPreferences wp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (user.equals(wp.getString(KEY_USER, "")) && today.equals(wp.getString(KEY_DATE, ""))
                && file(app, user).isFile()) {
            return false;
        }
        attempted = key;
        return true;
    }

    /** Keeps only {@code keepUser}'s cached avatar (null = logged out: none). Cheap after the first call per user. */
    static synchronized void pruneOtherUsers(Context app, String keepUser) {
        String keep = keepUser == null ? "" : keepUser;
        if (keep.equals(prunedFor)) return;
        prunedFor = keep;
        String keepName = keep.isEmpty() ? null : file(app, keep).getName();
        File[] files = app.getCacheDir().listFiles((dir, name) -> name.startsWith(FILE_PREFIX));
        if (files != null) {
            for (File f : files) {
                if (!f.getName().equals(keepName)) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
        if (!keep.equals(cachedUser)) {
            cachedUser = null;
            cachedBitmap = null;
        }
    }

    /** Downloads, crops and caches the avatar. Blocking, at most ~{@link #DEADLINE_MS}: download thread only. */
    static boolean refresh(Context app, String user) {
        String today = today();
        long deadline = System.currentTimeMillis() + DEADLINE_MS;
        HttpURLConnection conn = null;
        ScheduledFuture<?> watchdog = null;
        try {
            String url = app.getString(R.string.hive_image_host_url)
                    .replace("USERNAME", URLEncoder.encode(user, "UTF-8"));
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            final HttpURLConnection c = conn;
            watchdog = WATCHDOG.schedule(c::disconnect, DEADLINE_MS, TimeUnit.MILLISECONDS);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return false;
            byte[] data = readCapped(conn.getInputStream(), deadline);
            if (data == null) return false;
            Bitmap circle = decodeCircle(data);
            if (circle == null) return false;
            synchronized (WidgetAvatar.class) {
                if (!user.equals(prunedFor)) return false; // logged out / switched user meanwhile
                writeCache(circle, file(app, user));
                cachedUser = user;
                cachedBitmap = circle;
            }
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_USER, user).putString(KEY_DATE, today).apply();
            return true;
        } catch (Exception | OutOfMemoryError e) {
            Log.w(TAG, "widget avatar download failed", e);
            return false;
        } finally {
            if (watchdog != null) watchdog.cancel(false);
            if (conn != null) conn.disconnect();
        }
    }

    private static byte[] readCapped(InputStream in, long deadline) throws IOException {
        try (InputStream is = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = is.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES || System.currentTimeMillis() > deadline) return null;
            }
            return out.toByteArray();
        }
    }

    /** Decodes at a reduced size (inSampleSize), centre-crops to a square and clips it to a circle. */
    private static Bitmap decodeCircle(byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        if ((long) bounds.outWidth * bounds.outHeight > MAX_PIXELS) return null;
        // from the larger side, so the decoded bitmap is never much bigger than needed
        int larger = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (larger / (sample * 2) >= SIZE_PX) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap src = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (src == null) return null;

        Bitmap out = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888);
        float scale = SIZE_PX / (float) Math.min(src.getWidth(), src.getHeight());
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate((SIZE_PX - src.getWidth() * scale) / 2f, (SIZE_PX - src.getHeight() * scale) / 2f);
        BitmapShader shader = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(m);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(shader);
        new Canvas(out).drawCircle(SIZE_PX / 2f, SIZE_PX / 2f, SIZE_PX / 2f, paint);
        src.recycle();
        return out;
    }

    /** Writes via a temp file + rename, so a failed or interrupted write never leaves a truncated PNG. */
    private static void writeCache(Bitmap bmp, File file) {
        File tmp = new File(file.getPath() + ".tmp");
        boolean ok = false;
        try (OutputStream out = new FileOutputStream(tmp)) {
            ok = bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            Log.w(TAG, "could not cache the widget avatar", e);
        }
        if (!ok || !tmp.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static File file(Context app, String user) {
        // Hive names are [a-z0-9.-]; keep anything else out of the file name
        return new File(app.getCacheDir(), FILE_PREFIX + user.replaceAll("[^a-z0-9.-]", "_") + ".png");
    }

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(new Date());
    }
}
