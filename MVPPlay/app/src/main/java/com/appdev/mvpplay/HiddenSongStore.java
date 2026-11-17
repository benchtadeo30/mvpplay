package com.appdev.mvpplay;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

// official songs can't be deleted from the APK, so "deleting" one hides it
public class HiddenSongStore {

    private static final String PREFS = "hidden_songs";
    private static final String KEY = "names";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Set<String> load(Context c) {
        return new HashSet<>(prefs(c).getStringSet(KEY, new HashSet<>()));
    }

    public static int count(Context c) {
        return load(c).size();
    }

    public static void hide(Context c, Song song) {
        try {
            String name = c.getResources().getResourceEntryName(song.resId);
            Set<String> set = load(c);
            set.add(name);
            prefs(c).edit().putStringSet(KEY, set).apply();
        } catch (Exception ignored) {
        }
    }

    public static void restoreAll(Context c) {
        prefs(c).edit().remove(KEY).apply();
    }

    // takes the hidden official songs out of a list
    public static void removeHidden(Context c, List<Song> songs) {
        Set<String> hidden = load(c);
        if (hidden.isEmpty()) return;
        Iterator<Song> it = songs.iterator();
        while (it.hasNext()) {
            Song s = it.next();
            if (s.resId <= 0) continue;   // your own songs are handled by CustomSongStore
            try {
                if (hidden.contains(c.getResources().getResourceEntryName(s.resId))) it.remove();
            } catch (Exception ignored) {
            }
        }
    }
}