package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.Buffer;

public class RootChecker {

    public static boolean isDeviceRooted() {
        return  checkRoot1() || checkRoot2() || checkRoot3();
    }

    private static boolean checkRoot1() {
        String tags = android.os.Build.TAGS;
        return tags != null && tags.contains("test-keys");
    }

    private static boolean checkRoot2() {
        String[] paths = { "/system/bin/su", "/system/app/Superuser.apk", "/data/local/su", "/sbin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
                "/system/bin/failsafe/su", "/su/bin/su"};
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    private static boolean checkRoot3() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[] { "/system/xbin/which", "su" });
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            if (in.readLine() != null) return true;
            return false;
        } catch (Throwable t) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }
}
