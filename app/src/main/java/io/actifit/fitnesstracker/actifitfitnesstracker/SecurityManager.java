package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.widget.Toast;

import com.scottyab.rootbeer.RootBeer;

import java.security.MessageDigest;

/**
 * Handles security and integrity checks: app signature validation,
 * emulator detection, root detection, and SIM card verification.
 * Extracted from MainActivity to reduce class size.
 */
public class SecurityManager {

    private static final String VALID = "0";
    private static final String INVALID = "1";

    private final Context context;

    public SecurityManager(Context context) {
        this.context = context;
    }

    public boolean checkAppSignature() {
        final String SIGNATURE = context.getString(R.string.sign_key);
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            for (Signature signature : packageInfo.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                final String currentSignature = Base64.encodeToString(md.digest(), Base64.DEFAULT);
                if (SIGNATURE.equals(currentSignature)) {
                    return true;
                }
            }
        } catch (Exception e) {
            return true;
        }
        return false;
    }

    public boolean isEmulator() {
        return Build.FINGERPRINT.contains("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT)
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.HARDWARE.contains("andy");
    }

    public boolean isSimAvailable() {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getSimState() != TelephonyManager.SIM_STATE_ABSENT;
    }

    public boolean isDeviceRooted() {
        RootBeer rootBeer = new RootBeer(context);
        return rootBeer.isRootedWithoutBusyBoxCheck();
    }

    public boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void runSecurityChecks(String testMode, String expectedPackageName, Runnable killCallback) {
        if (!testMode.equals("on")) {
            if (!checkAppSignature()) {
                killCallback.run();
                return;
            }
            if (!context.getPackageName().equals(expectedPackageName)) {
                killCallback.run();
                return;
            }
            if (!isSimAvailable()) {
                killCallback.run();
                return;
            }
            if (isEmulator()) {
                killCallback.run();
                return;
            }
            if (isDeviceRooted()) {
                killCallback.run();
                return;
            }
        }
    }
}
