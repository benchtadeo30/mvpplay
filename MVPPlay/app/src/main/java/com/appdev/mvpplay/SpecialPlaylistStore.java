package com.appdev.mvpplay;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

// the name of the special gold playlist (the user can rename it)
public class SpecialPlaylistStore {

    public static final String DEFAULT_NAME = "Benjie's Playlist";
    private static final String PREFS = "special_playlist";
    private static final String KEY = "name";
    private static final String KEY_SONGS = "songs";

    public static String name(Context c) {
        String saved = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
        return (saved == null || saved.trim().isEmpty()) ? DEFAULT_NAME : saved.trim();
    }

    // an empty name puts the original name back
    public static void setName(Context c, String name) {
        android.content.SharedPreferences.Editor e =
                c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (name == null || name.trim().isEmpty()) e.remove(KEY);
        else e.putString(KEY, name.trim());
        e.apply();
    }

    // saves the full song list (order included) of the gold playlist
    public static void saveSongs(Context c, List<Song> songs) {
        JSONArray array = new JSONArray();
        for (Song s : songs) {
            String file = CustomPlaylistStore.entryName(c, s);
            if (!file.isEmpty()) array.put(file);
        }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_SONGS, array.toString()).apply();
    }

    // the saved list, or null if you never changed the playlist
    public static List<Song> applyOverride(Context c, List<Song> allSongs) {
        String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SONGS, null);
        if (raw == null) return null;
        List<Song> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String file = array.optString(i);
                for (Song s : allSongs) {
                    if (file.equals(CustomPlaylistStore.entryName(c, s)) && !result.contains(s)) {
                        result.add(s);
                        break;
                    }
                }
            }
        } catch (JSONException e) {
            return null;
        }
        return result;
    }
}