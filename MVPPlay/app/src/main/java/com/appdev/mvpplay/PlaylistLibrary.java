package com.appdev.mvpplay;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class PlaylistLibrary {

    private static final long FOUR_MINUTES = 4 * 60 * 1000L;

    // the names of the official playlists (nobody can reuse these)
    public static final String[] OFFICIAL_NAMES =
            {"All Songs", "Shuffle Mix", "Quick Hits", "Long Plays"};

    public static boolean isOfficialName(String name) {
        for (String official : OFFICIAL_NAMES) {
            if (official.equalsIgnoreCase(name.trim())) return true;
        }
        return false;
    }

    // the special gold playlist: these files from res/raw, in this order.
    // If you want to add or remove a song later, edit this list.
    private static final String[] SPECIAL_FILES = {
            "cavendish_music__funny_song",
            "multo__cup_of_joe",
            "taylor_swift__opalite",
            "boyce_avenue__here_comes_the_sun_original_the_beatles",
            "one_direction__night_changes",
            "aiza_seguerra__till_there_was_you",
            "ellie_goulding__how_long_will_i_love_you"
    };

    // null when none of its songs are in the app (yet)
    public static Playlist buildSpecial(Context context, List<Song> all) {
        List<Song> songs = new ArrayList<>();
        for (String file : SPECIAL_FILES) {
            for (Song s : all) {
                if (s.isCustom() || s.resId <= 0) continue;
                try {
                    if (file.equals(context.getResources().getResourceEntryName(s.resId))) {
                        songs.add(s);
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (songs.isEmpty()) return null;
        return new Playlist(SpecialPlaylistStore.name(context), "A special collection",
                "SPECIAL", songs, 0xFFB8860B, 0xFFFFD54F, false, false, true);
    }

    // to change the official playlists later, edit this one method
    public static List<Playlist> build(List<Song> all) {
        List<Playlist> result = new ArrayList<>();
        if (all.isEmpty()) return result;

        List<Song> quick = new ArrayList<>();
        List<Song> longer = new ArrayList<>();
        for (Song s : all) {
            if (s.durationMs < FOUR_MINUTES) quick.add(s); else longer.add(s);
        }

        result.add(new Playlist("All Songs", "Everything in your library", "LIBRARY",
                all, 0xFF2B1B5E, 0xFF7C3AED, false, false));
        result.add(new Playlist("Shuffle Mix", "A fresh random order", "SHUFFLE",
                all, 0xFF0F3D2E, 0xFF34D399, true, false));
        if (!quick.isEmpty()) {
            result.add(new Playlist("Quick Hits", "Songs under 4 minutes", "UNDER 4 MIN",
                    quick, 0xFF0E3B4A, 0xFF22D3EE, false, false));
        }
        if (!longer.isEmpty()) {
            result.add(new Playlist("Long Plays", "4 minutes and longer", "4 MIN +",
                    longer, 0xFF3B1A7A, 0xFFC084FC, false, false));
        }
        return result;
    }
}