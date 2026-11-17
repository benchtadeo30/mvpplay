package com.appdev.mvpplay;

import android.content.Context;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

// one place that sorts everything by the choice in Settings (Artist / Title / Length)
public class SortHelper {

    public static long totalMs(List<Song> songs) {
        long total = 0;
        for (Song s : songs) total += s.durationMs;
        return total;
    }

    // songs and episodes (built-in and your own together)
    public static void sortSongs(Context c, List<Song> list) {
        final String mode = AppSettings.sortMode(c);
        Collections.sort(list, (a, b) -> {
            if (AppSettings.SORT_LENGTH.equals(mode)) {
                int r = Long.compare(a.durationMs, b.durationMs);
                if (r != 0) return r;
            } else if (AppSettings.SORT_ARTIST.equals(mode)) {
                int r = a.artist.compareToIgnoreCase(b.artist);
                if (r != 0) return r;
            }
            return a.title.compareToIgnoreCase(b.title);
        });
    }

    // artists, playlists and podcasts: by name, or by total length when Length is chosen
    public static <T> void sortGroups(Context c, List<T> list,
                                      Function<T, String> name, ToLongFunction<T> total) {
        final boolean byLength = AppSettings.SORT_LENGTH.equals(AppSettings.sortMode(c));
        Collections.sort(list, (a, b) -> {
            if (byLength) {
                int r = Long.compare(total.applyAsLong(a), total.applyAsLong(b));
                if (r != 0) return r;
            }
            return name.apply(a).compareToIgnoreCase(name.apply(b));
        });
    }
}