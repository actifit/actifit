package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context; //Might be able to remove this line

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.Buffer;

public class RootChecker {

    public static boolean isDeviceRooted() { // Call and check each function below
        return  checkRoot1() || checkRoot2() || checkRoot3();
    }

    private static boolean checkRoot1() {  // One popular method of checking for root access, is to look for a Test Key.
        String tags = android.os.Build.TAGS; // Variable containing TAGS
        return tags != null && tags.contains("test-keys"); // 'test-keys' represent a custom signature, rather than official.
    } // Official applications are signed with 'release-keys'.

    private static boolean checkRoot2() { // Another method of checking to see if root access is available.
        String[] paths = { "/system/bin/su", "/system/app/Superuser.apk", "/data/local/su", "/sbin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
                "/system/bin/failsafe/su", "/su/bin/su"}; //All of these paths should be locked to the average user. There may be more.
        for (String path : paths) { // for each path in our list/collection of paths,
            if (new File(path).exists()) return true; // return true if path can be reached.
        }
        return false; // Else return false.
    }

    private static boolean checkRoot3() { // A 3rd and final method of checking for root.
        Process process = null; //create a new proccess to run.
        try {
            process = Runtime.getRuntime().exec(new String[] { "/system/xbin/which", "su" }); // Newer devices may need 'sbin' in place of 'xbin'
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream())); // Buffered reader to read and store data.
            if (in.readLine() != null) return true; // if the buffered reader has gathered data, this shows us we have root access, 
            return false; // as the su command is restricted. Else, return false.
        } catch (Throwable t) { 
            return false;
        } finally { // finally reset process for next use.
            if (process != null) process.destroy();
        }
    }
}
