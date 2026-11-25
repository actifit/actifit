package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Anything built into the APK is public, and Google suspended the Gemini project after the
 * key in BuildConfig was harvested from the app. No Google API key may sit in BuildConfig or
 * in the app's resources (local, gitignored files included: release builds are made from
 * them). Firebase's keys come from google-services.json at build time and are not checked.
 */
public class NoApiKeysInAppTest {

    private static final Pattern KEY = Pattern.compile("AIza[0-9A-Za-z_\\-]{35}|AQ\\.A[0-9A-Za-z_\\-]{40,}");

    @Test
    public void buildConfigHoldsNoApiKey() throws Exception {
        List<String> bad = new ArrayList<>();
        for (Field f : BuildConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                Object v = f.get(null);
                if (v != null && KEY.matcher(v.toString()).find()) bad.add(f.getName());
            }
        }
        assertTrue("API key in BuildConfig: " + bad + " - call it through the bot instead", bad.isEmpty());
    }

    @Test
    public void resourcesHoldNoApiKey() throws Exception {
        File res = new File("src/main/res");
        if (!res.isDirectory()) res = new File("app/src/main/res"); // run from the project root
        assertTrue("res folder not found from " + new File(".").getAbsolutePath(), res.isDirectory());
        List<String> bad = new ArrayList<>();
        scan(res, bad);
        assertTrue("API key in resources: " + bad + " - call it through the bot instead", bad.isEmpty());
    }

    private static void scan(File dir, List<String> bad) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scan(f, bad);
            } else if (f.getName().endsWith(".xml") || f.getName().endsWith(".json")) {
                String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                if (KEY.matcher(s).find()) bad.add(f.getPath());
            }
        }
    }
}
