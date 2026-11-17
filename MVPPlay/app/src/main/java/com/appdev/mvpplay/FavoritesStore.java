package com.appdev.mvpplay;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// remembers which songs have the heart on (stored by file name, like the custom playlists)
public class FavoritesStore {

    private static final String PREFS = "favorites";
    private static final String KEY = "files";

    private static String entryName(Context context, int resId) {
        if (resId < 0) return "custom_" + (-resId);   // songs the user added
        try {
            return context.getResources().getResourceEntryName(resId);
        } catch (Exception e) {
            return "";
        }
    }

    private static Set<String> read(Context context) {
        return new HashSet<>(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY, new HashSet<String>()));
    }

    public static boolean isFavorite(Context context, int resId) {
        return read(context).contains(entryName(context, resId));
    }

    // flips the heart of a song and returns the new state (true = it is a favorite now)
    public static boolean toggle(Context context, int resId) {
        Set<String> files = read(context);
        String name = entryName(context, resId);
        boolean wasOn = files.remove(name);
        if (!wasOn) files.add(name);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY, files).apply();
        return !wasOn;
    }

    // removes every heart (songs, episodes and podcasts)
    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY).remove(KEY_SHOWS).apply();
    }

    // ---------- whole podcasts ----------
    private static final String KEY_SHOWS = "shows";

    private static Set<String> readShows(Context context) {
        return new HashSet<>(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_SHOWS, new HashSet<String>()));
    }

    public static boolean isShowFavorite(Context context, String showName) {
        return readShows(context).contains(showName.toLowerCase());
    }

    // flips the heart of a podcast and returns the new state
    public static boolean toggleShow(Context context, String showName) {
        Set<String> names = readShows(context);
        String key = showName.toLowerCase();
        boolean wasOn = names.remove(key);
        if (!wasOn) names.add(key);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_SHOWS, names).apply();
        return !wasOn;
    }

    // the favorite songs, in the same order as the library (your own songs come last)
    public static List<Song> load(Context context, List<Song> allSongs) {
        Set<String> files = read(context);
        List<Song> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Song s : allSongs) {
            if (files.contains(entryName(context, s.resId)) && seen.add(s.resId)) {
                result.add(s);
            }
        }
        // the songs the user added
        for (Song s : CustomSongStore.load(context)) {
            if (files.contains(entryName(context, s.resId)) && seen.add(s.resId)) {
                result.add(s);
            }
        }
        return result;
    }
}