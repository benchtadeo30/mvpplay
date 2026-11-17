package com.appdev.mvpplay;

import android.content.Context;

// the theme the user picked: Dark, Light or Spotify
public class ThemeStore {

    public static final int DARK = 0, LIGHT = 1, SPOTIFY = 2, HALLOWEEN = 3, CHRISTMAS = 4,
            SUMMER = 5, GLASS = 6;
    public static final String[] LABELS = {"Dark", "Light", "Spotify", "Halloween", "Christmas",
            "Summer", "Glass"};

    // the order the themes show in the picker (the numbers above are what gets saved)
    public static final int[] ORDER = {DARK, LIGHT, SPOTIFY, GLASS, HALLOWEEN, CHRISTMAS, SUMMER};

    private static final String PREFS = "theme_prefs";
    private static final String KEY = "theme";

    // first run after this update: follow the old Dark mode switch
    public static int get(Context c) {
        int saved = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, -1);
        if (saved >= 0) return saved;
        return AppSettings.darkMode(c) ? DARK : LIGHT;
    }

    public static void set(Context c, int theme) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, theme).apply();
    }

    public static boolean isLight(Context c) {
        return get(c) == LIGHT || get(c) == SUMMER;   // Summer is a light theme
    }

    public static boolean isSummer(Context c) {
        return get(c) == SUMMER;
    }

    public static boolean isGlass(Context c) {
        return get(c) == GLASS;
    }

    public static boolean isHalloween(Context c) {
        return get(c) == HALLOWEEN;
    }
    public static boolean isChristmas(Context c) {
        return get(c) == CHRISTMAS;
    }
    public static boolean isSpotify(Context c) {
        return get(c) == SPOTIFY;
    }

    // text on a selected chip: black on Spotify's green, otherwise the normal text color
    public static int selectedChipText(Context c) {
        return (isSpotify(c) || isHalloween(c)) ? android.graphics.Color.BLACK
                : androidx.core.content.ContextCompat.getColor(c, R.color.text_primary);
    }

    public static String label(Context c) {
        return LABELS[get(c)];
    }
}