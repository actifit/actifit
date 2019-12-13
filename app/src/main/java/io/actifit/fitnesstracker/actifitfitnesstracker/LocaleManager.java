package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;

import java.util.Locale;

public class LocaleManager {

    /**
     * For english locale
     */
    public static final String LANGUAGE_KEY_ENGLISH = "en";

    /***
     * For other supported locales
     */
    public static final String LANGUAGE_KEY_PORTUGUESE = "pt";

    public static final String LANGUAGE_KEY_KOREAN = "ko";

    public static final String LANGUAGE_KEY_ARABIC = "ar";

    public static final String LANGUAGE_KEY_YORUBA = "yo";

    public static final String LANGUAGE_KEY_DUTCH = "nl";

    public static final String LANGUAGE_KEY_HINDI = "hi";

    public static final String LANGUAGE_KEY_ITALIAN = "it";

    public static final String LANGUAGE_KEY_GERMAN = "de";

    public static final String LANGUAGE_KEY_SPANISH = "es";

    public static final String LANGUAGE_KEY_TURKISH = "tr";

    public static final String LANGUAGE_KEY_UKRAINIAN = "uk";

    public static final String LANGUAGE_KEY_BRAZIL = "br";
    /**
     *  SharedPreferences Key
     */
    private static final String LANGUAGE_KEY = "language_key";

    /**
     * set current pref locale
     * @param mContext
     * @return
     */
    public static Context setLocale(Context mContext) {
        return updateResources(mContext, getLanguagePref(mContext));
    }

    /**
     * Set new Locale with context
     * @param mContext
     * @param mLocaleKey
     * @return
     */
    public static Context setNewLocale(Context mContext, String mLocaleKey) {
        setLanguagePref(mContext, mLocaleKey);
        return updateResources(mContext, mLocaleKey);
    }

    /**
     * Get saved Locale from SharedPreferences
     * @param mContext current context
     * @return current locale key by default return english locale
     */
    public static String getLanguagePref(Context mContext) {
        SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        return mPreferences.getString(LANGUAGE_KEY, LANGUAGE_KEY_ENGLISH);
    }

    /**
     *  set pref key
     * @param mContext
     * @param localeKey
     */
    private static void setLanguagePref(Context mContext, String localeKey) {
        SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mPreferences.edit().putString(LANGUAGE_KEY, localeKey).commit();
    }

    /**
     * update resource
     * @param context
     * @param language
     * @return
     */
    private static Context updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        if (Build.VERSION.SDK_INT >= 17) {
            config.setLocale(locale);
            context = context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
        return context;
    }

    /**
     * get current locale
     * @param res
     * @return
     */
    public static Locale getLocale(Resources res) {
        Configuration config = res.getConfiguration();
        return Build.VERSION.SDK_INT >= 24 ? config.getLocales().get(0) : config.locale;
    }


    public static int getSelectedLang(Context context){
        switch (LocaleManager.getLanguagePref(context)){
            case LocaleManager.LANGUAGE_KEY_ENGLISH: return 0;
            case LocaleManager.LANGUAGE_KEY_PORTUGUESE: return 1;
            case LocaleManager.LANGUAGE_KEY_KOREAN: return 2;
            case LocaleManager.LANGUAGE_KEY_ARABIC: return 3;
            case LocaleManager.LANGUAGE_KEY_YORUBA: return 4;
            case LocaleManager.LANGUAGE_KEY_DUTCH: return 5;
            case LocaleManager.LANGUAGE_KEY_HINDI: return 6;
            case LocaleManager.LANGUAGE_KEY_ITALIAN: return 7;
            case LocaleManager.LANGUAGE_KEY_GERMAN: return 8;
            case LocaleManager.LANGUAGE_KEY_SPANISH: return 9;
            case LocaleManager.LANGUAGE_KEY_TURKISH: return 10;
            case LocaleManager.LANGUAGE_KEY_UKRAINIAN: return 11;
            default: return 0;
        }
    }

    public static void updateLangChoice(Context context, int selectedLang){
        switch (selectedLang){
            case 0: LocaleManager.setNewLocale(context, LocaleManager.LANGUAGE_KEY_ENGLISH);
                break;
            case 1: LocaleManager.setNewLocale(context, LocaleManager.LANGUAGE_KEY_PORTUGUESE);
                break;
            case 2: LocaleManager.setNewLocale(context, LocaleManager.LANGUAGE_KEY_KOREAN);
                break;
            case 3: LocaleManager.setNewLocale(context, LocaleManager.LANGUAGE_KEY_ARABIC);
                break;
            case 4: LocaleManager.setNewLocale(context, LocaleManager.LANGUAGE_KEY_YORUBA);
                break;
            case 5: LocaleManager.setNewLocale(context, LocaleManager.LANGUAGE_KEY_DUTCH);
                break;
            case 6: LocaleManager.setNewLocale(context, LocaleManager.LANGUAGE_KEY_HINDI);
                break;
            case 7: LocaleManager.setNewLocale(context, LocaleManager.LANGUAGE_KEY_ITALIAN);
                break;
            case 8: LocaleManager.setNewLocale(context, LocaleManager.LANGUAGE_KEY_GERMAN);
                break;
            case 9: LocaleManager.setNewLocale(context, LocaleManager.LANGUAGE_KEY_SPANISH);
                break;
            case 10: LocaleManager.setNewLocale(context, LocaleManager.LANGUAGE_KEY_TURKISH);
                break;
            case 11: LocaleManager.setNewLocale(context, LocaleManager.LANGUAGE_KEY_UKRAINIAN);
                break;
        }
        SettingsActivity.languageModified = false;
        SettingsActivity.langChoice = -1;
    }

}
