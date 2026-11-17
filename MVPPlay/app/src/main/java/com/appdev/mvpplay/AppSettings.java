package com.appdev.mvpplay;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.List;

// the choices made on the Settings screen, saved so they stay after the app closes
public class AppSettings {

    public static final String SORT_ARTIST = "artist";
    public static final String SORT_TITLE = "title";
    public static final String SORT_LENGTH = "length";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    // ---------- on / off ----------
    public static boolean showWelcome(Context c) {
        return prefs(c).getBoolean("show_welcome", true);
    }

    public static void setShowWelcome(Context c, boolean on) {
        prefs(c).edit().putBoolean("show_welcome", on).apply();
    }

    public static boolean resumeLast(Context c) {
        return prefs(c).getBoolean("resume_last", true);
    }

    public static void setResumeLast(Context c, boolean on) {
        prefs(c).edit().putBoolean("resume_last", on).apply();
    }

    public static boolean floatingWindow(Context c) {
        return prefs(c).getBoolean("floating_window", true);
    }

    public static void setFloatingWindow(Context c, boolean on) {
        prefs(c).edit().putBoolean("floating_window", on).apply();
    }

    // ---------- dark mode (dark is the default) ----------
    public static boolean darkMode(Context c) {
        return prefs(c).getBoolean("dark_mode", true);
    }

    public static void setDarkMode(Context c, boolean on) {
        prefs(c).edit().putBoolean("dark_mode", on).apply();
    }

    // ---------- podcast speed ----------
    public static float podcastSpeed(Context c) {
        return prefs(c).getFloat("podcast_speed", 1f);
    }

    public static void setPodcastSpeed(Context c, float speed) {
        prefs(c).edit().putFloat("podcast_speed", speed).apply();
    }

    // ---------- volume (the level from before the mute button was pressed) ----------
    public static int volumeBeforeMute(Context c, int fallback) {
        return prefs(c).getInt("volume_before_mute", fallback);
    }

    public static void setVolumeBeforeMute(Context c, int volume) {
        prefs(c).edit().putInt("volume_before_mute", volume).apply();
    }

    // ---------- sort order of the songs ----------
    public static String sortMode(Context c) {
        return prefs(c).getString("sort", SORT_ARTIST);
    }

    public static void setSortMode(Context c, String mode) {
        prefs(c).edit().putString("sort", mode).apply();
    }

    public static void sortSongs(Context context, List<Song> songs) {
        final String mode = sortMode(context);
        Collections.sort(songs, (a, b) -> {
            if (SORT_TITLE.equals(mode)) return a.title.compareToIgnoreCase(b.title);
            if (SORT_LENGTH.equals(mode)) return Long.compare(a.durationMs, b.durationMs);
            int byArtist = a.artist.compareToIgnoreCase(b.artist);
            return byArtist != 0 ? byArtist : a.title.compareToIgnoreCase(b.title);
        });
    }

    // ---------- resume the last song ----------
    public static void saveLast(Context c, String file, long positionMs) {
        prefs(c).edit().putString("last_file", file).putLong("last_position", positionMs).apply();
    }

    public static String lastFile(Context c) {
        return prefs(c).getString("last_file", "");
    }

    public static long lastPosition(Context c) {
        return prefs(c).getLong("last_position", 0L);
    }

    // loads the last song at the place where it stopped, paused (only if the player is empty)
    public static void restoreLast(final Context context, final List<Song> songs,
                                   final List<Song> podcasts) {
        if (!resumeLast(context)) return;
        final String file = lastFile(context);
        if (file.isEmpty()) return;
        final long position = lastPosition(context);

        PlayerConnection.connect(context, c -> {
            if (c.getCurrentMediaItem() != null) return;   // something is already loaded
            List<Song> queue = songs;
            int index = indexOf(context, queue, file);
            if (index < 0) {
                queue = podcasts;
                index = indexOf(context, queue, file);
            }
            if (index < 0) return;
            MainActivity.activePlaylistName = null;
            PlayerConnection.start(context, queue, index, position, false);
        });
    }

    private static int indexOf(Context context, List<Song> list, String file) {
        for (int i = 0; i < list.size(); i++) {
            try {
                if (file.equals(context.getResources().getResourceEntryName(list.get(i).resId))) {
                    return i;
                }
            } catch (Exception ignored) {
            }
        }
        return -1;
    }
}