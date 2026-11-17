package com.appdev.mvpplay;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// keeps the playlists the user made. Official playlists are never stored here.
public class CustomPlaylistStore {

    public static final int ADDED = 0;
    public static final int ALREADY_THERE = 1;
    public static final int NO_PLAYLIST = 2;

    private static final String PREFS = "custom_playlists";
    private static final String KEY = "data";

    // cover colors, picked from the playlist name
    private static final int[][] COLORS = {
            {0xFF4A1B3A, 0xFFF472B6},
            {0xFF1B3A4A, 0xFF38BDF8},
            {0xFF3A2B1B, 0xFFFBBF24},
            {0xFF1B4A3A, 0xFF4ADE80}
    };

    private static JSONObject read(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            return new JSONObject(prefs.getString(KEY, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static void write(Context context, JSONObject data) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, data.toString()).apply();
    }

    // the file name of a song, for example "owl_city__fireflies"
    public static String entryName(Context context, Song song) {
        if (song.isCustom()) return "custom_" + (-song.resId);   // your own songs
        try {
            return context.getResources().getResourceEntryName(song.resId);
        } catch (Exception e) {
            return "";
        }
    }

    // finds a stored playlist name, ignoring capital letters (null if not found)
    private static String findKey(JSONObject data, String name) {
        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.equalsIgnoreCase(name.trim())) return key;
        }
        return null;
    }

    private static boolean has(JSONArray array, String value) {
        for (int i = 0; i < array.length(); i++) {
            if (array.optString(i).equals(value)) return true;
        }
        return false;
    }

    // ---------- reading ----------

    // all the playlists the user made (empty ones too), rebuilt from the songs that exist now
    public static List<Playlist> load(Context context, List<Song> allSongs) {
        List<Playlist> result = new ArrayList<>();
        JSONObject data = read(context);

        // also look through the songs the user added
        allSongs = new ArrayList<>(allSongs);
        allSongs.addAll(CustomSongStore.load(context));

        Iterator<String> names = data.keys();
        while (names.hasNext()) {
            String name = names.next();
            JSONArray files = data.optJSONArray(name);
            if (files == null) continue;

            List<Song> songs = new ArrayList<>();
            for (int i = 0; i < files.length(); i++) {
                String file = files.optString(i);
                for (Song s : allSongs) {
                    if (file.equals(entryName(context, s))) {
                        songs.add(s);
                        break;
                    }
                }
            }

            int[] colors = COLORS[Math.abs(name.hashCode() % COLORS.length)];
            result.add(new Playlist(name,
                    songs.isEmpty() ? "Empty playlist" : "Your own mix",
                    "MINE", songs, colors[0], colors[1], false, true));
        }
        return result;
    }

    public static List<String> names(Context context) {
        List<String> result = new ArrayList<>();
        Iterator<String> keys = read(context).keys();
        while (keys.hasNext()) result.add(keys.next());
        return result;
    }

    public static int count(Context context, String playlistName) {
        JSONObject data = read(context);
        String key = findKey(data, playlistName);
        if (key == null) return 0;
        JSONArray files = data.optJSONArray(key);
        return files == null ? 0 : files.length();
    }

    public static boolean contains(Context context, String playlistName, Song song) {
        JSONObject data = read(context);
        String key = findKey(data, playlistName);
        if (key == null) return false;
        JSONArray files = data.optJSONArray(key);
        return files != null && has(files, entryName(context, song));
    }

    // ---------- changing ----------

    // makes a new playlist. Returns false if the name is an official name or already used.
    public static boolean create(Context context, String name, List<Song> songs) {
        name = name.trim();
        JSONObject data = read(context);
        if (name.isEmpty() || PlaylistLibrary.isOfficialName(name)
                || name.equalsIgnoreCase(SpecialPlaylistStore.name(context))
                || findKey(data, name) != null) {
            return false;
        }

        JSONArray files = new JSONArray();
        for (Song s : songs) {
            String file = entryName(context, s);
            if (!has(files, file)) files.put(file);   // no duplicates
        }
        try {
            data.put(name, files);
        } catch (JSONException e) {
            return false;
        }
        write(context, data);
        return true;
    }

    // adds one song to a playlist (never twice)
    public static int addSong(Context context, String playlistName, Song song) {
        JSONObject data = read(context);
        String key = findKey(data, playlistName);
        if (key == null) return NO_PLAYLIST;

        JSONArray files = data.optJSONArray(key);
        if (files == null) files = new JSONArray();

        String file = entryName(context, song);
        if (has(files, file)) return ALREADY_THERE;

        files.put(file);
        try {
            data.put(key, files);
        } catch (JSONException e) {
            return NO_PLAYLIST;
        }
        write(context, data);
        return ADDED;
    }

    // takes one song out of the playlist (the music file itself is never touched)
    public static void removeSong(Context context, String playlistName, Song song) {
        JSONObject data = read(context);
        String key = findKey(data, playlistName);
        if (key == null) return;

        JSONArray files = data.optJSONArray(key);
        if (files == null) return;

        String file = entryName(context, song);
        JSONArray updated = new JSONArray();
        for (int i = 0; i < files.length(); i++) {
            if (!files.optString(i).equals(file)) updated.put(files.optString(i));
        }
        try {
            data.put(key, updated);
        } catch (JSONException ignored) {
        }
        write(context, data);
    }

    // saves a new song order for a playlist
    public static void saveOrder(Context context, String playlistName, List<Song> ordered) {
        JSONObject data = read(context);
        String key = findKey(data, playlistName);
        if (key == null) return;

        JSONArray files = new JSONArray();
        for (Song s : ordered) {
            String file = entryName(context, s);
            if (!has(files, file)) files.put(file);
        }
        try {
            data.put(key, files);
        } catch (JSONException ignored) {
        }
        write(context, data);
    }

    // renames a playlist and keeps its songs and its place in the list
    public static boolean rename(Context context, String oldName, String newName) {
        newName = newName.trim();
        JSONObject data = read(context);

        String oldKey = findKey(data, oldName);
        if (oldKey == null || newName.isEmpty() || PlaylistLibrary.isOfficialName(newName)
                || newName.equalsIgnoreCase(SpecialPlaylistStore.name(context))) {
            return false;
        }
        String clash = findKey(data, newName);
        if (clash != null && !clash.equals(oldKey)) return false;

        JSONObject renamed = new JSONObject();
        try {
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                renamed.put(key.equals(oldKey) ? newName : key, data.get(key));
            }
        } catch (JSONException e) {
            return false;
        }
        write(context, renamed);
        PlaylistCoverStore.move(context, "custom:" + oldKey.toLowerCase(Locale.ROOT),
                "custom:" + newName.toLowerCase(Locale.ROOT));
        return true;
    }

    // removes a deleted song from every playlist
    public static void removeEverywhere(Context context, Song song) {
        JSONObject data = read(context);
        String file = entryName(context, song);

        List<String> keys = new ArrayList<>();
        Iterator<String> it = data.keys();
        while (it.hasNext()) keys.add(it.next());

        try {
            for (String key : keys) {
                JSONArray files = data.optJSONArray(key);
                if (files == null) continue;
                JSONArray updated = new JSONArray();
                for (int i = 0; i < files.length(); i++) {
                    if (!files.optString(i).equals(file)) updated.put(files.optString(i));
                }
                data.put(key, updated);
            }
        } catch (JSONException ignored) {
        }
        write(context, data);
    }

    public static void delete(Context context, String name) {
        JSONObject data = read(context);
        String key = findKey(data, name);
        if (key != null) {
            data.remove(key);
            write(context, data);
            PlaylistCoverStore.clear(context, "custom:" + key.toLowerCase(Locale.ROOT));
        }
    }
}