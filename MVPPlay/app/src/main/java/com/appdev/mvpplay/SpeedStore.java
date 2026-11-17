package com.appdev.mvpplay;

import android.content.Context;

// the playback speed picked on the Now Playing screen
public class SpeedStore {

    public static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};

    // the speed sheet: slider from 0.25x to 3x in steps of 0.05
    public static final float MIN = 0.25f;
    public static final float MAX = 3f;
    public static final float STEP = 0.05f;
    public static final int STEPS = Math.round((MAX - MIN) / STEP);
    public static final float[] PRESETS = {1f, 1.25f, 1.5f, 2f, 3f};

    public static float clamp(float s) {
        return Math.round(Math.max(MIN, Math.min(MAX, s)) * 100) / 100f;
    }

    public static int toProgress(float s) {
        return Math.round((clamp(s) - MIN) / STEP);
    }

    public static float fromProgress(int p) {
        return Math.round((MIN + p * STEP) * 100) / 100f;
    }

    // "1.00x" for the sheet title
    public static String title(float s) {
        return String.format(java.util.Locale.US, "%.2fx", s);
    }

    private static final String PREFS = "speed_prefs";
    private static final String KEY = "speed";

    // -1 = never picked (the old rules apply)
    public static float get(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(KEY, -1f);
    }

    public static void set(Context c, float speed) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY, speed).apply();
    }

    public static String label(float s) {
        float r = Math.round(s * 100) / 100f;
        return (r == (int) r ? String.valueOf((int) r) : String.valueOf(r)) + "x";
    }
}