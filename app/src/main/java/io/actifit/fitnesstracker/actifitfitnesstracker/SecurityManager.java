package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Base64;

import com.scottyab.rootbeer.RootBeer;

import java.security.MessageDigest;

/**
 * Single source of truth for the app's integrity checks: app-signature validation,
 * package-name validation, SIM presence, emulator detection and root detection.
 *
 * Enforcement is driven by {@code BuildConfig.ENFORCE_SECURITY} (release = on, debug = off,
 * overridable via {@code enforce.security} in local.properties/CI) — NOT by a runtime string
 * flag. Callers run {@link #runSecurityChecks(String)} only when enforcement is enabled and
 * must halt the app when the returned result is not {@code passed}.
 *
 * All checks fail closed: any inability to positively verify integrity is treated as a failure.
 */
public class SecurityManager {

    private final Context context;

    public SecurityManager(Context context) {
        this.context = context;
    }

    /** Outcome of {@link #runSecurityChecks(String)}: pass, or the first failure and its reason. */
    public static class SecurityResult {
        public final boolean passed;
        /** String resource shown to the user when {@code !passed}; 0 when passed. */
        public final int reasonResId;
        /** Internal label for logging; never shown to the user. */
        public final String logTag;

        private SecurityResult(boolean passed, int reasonResId, String logTag) {
            this.passed = passed;
            this.reasonResId = reasonResId;
            this.logTag = logTag;
        }

        static SecurityResult pass() {
            return new SecurityResult(true, 0, "ok");
        }

        static SecurityResult fail(int reasonResId, String logTag) {
            return new SecurityResult(false, reasonResId, logTag);
        }
    }

    /**
     * Runs every integrity check in order and returns the first failure, or a passing result.
     * Fails closed throughout.
     *
     * @param expectedPackageName the package name the release is expected to run under
     */
    public SecurityResult runSecurityChecks(String expectedPackageName) {
        if (!checkAppSignature()) {
            return SecurityResult.fail(R.string.security_concerns, "signature tampered");
        }
        if (!context.getPackageName().equals(expectedPackageName)) {
            return SecurityResult.fail(R.string.security_concerns, "package name tampered");
        }
        if (!isSimAvailable()) {
            return SecurityResult.fail(R.string.no_valid_sim, "no valid SIM");
        }
        if (isEmulator()) {
            return SecurityResult.fail(R.string.emulator_device, "emulator detected");
        }
        if (isDeviceRooted()) {
            return SecurityResult.fail(R.string.device_rooted, "device rooted");
        }
        return SecurityResult.pass();
    }

    /**
     * Verifies the running APK is signed with the expected certificate.
     * Uses the modern signing-certificates API (available since minSdk 28) and compares the
     * SHA-1 digest against {@code R.string.sign_key}. Fails closed: returns false if the
     * signature cannot be read or does not match.
     */
    public boolean checkAppSignature() {
        final String expected = context.getString(R.string.sign_key);
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo signingInfo = packageInfo.signingInfo;
            if (signingInfo == null) {
                return false;
            }
            Signature[] signatures = signingInfo.hasMultipleSigners()
                    ? signingInfo.getApkContentsSigners()
                    : signingInfo.getSigningCertificateHistory();
            if (signatures == null) {
                return false;
            }
            for (Signature signature : signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                final String currentSignature = Base64.encodeToString(md.digest(), Base64.DEFAULT);
                if (expected.equals(currentSignature)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // fail closed: inability to verify the signature is treated as a tampered app
            return false;
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
        // fail closed: a device with no telephony service is treated as not a valid phone
        if (telephonyManager == null) {
            return false;
        }
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
}
