package com.appdev.mvpplay;

import java.util.List;

public class Playlist {
    public final String name;
    public final String subtitle;
    public final String tag;
    public final List<Song> songs;
    public final int colorStart;
    public final int colorEnd;
    public final boolean shuffled;   // true: songs are played in a new random order each time
    public final boolean custom;     // true: made by the user. false: an official playlist
    public final boolean special;    // true: the gold special playlist

    public Playlist(String name, String subtitle, String tag, List<Song> songs,
                    int colorStart, int colorEnd, boolean shuffled, boolean custom) {
        this(name, subtitle, tag, songs, colorStart, colorEnd, shuffled, custom, false);
    }

    public Playlist(String name, String subtitle, String tag, List<Song> songs,
                    int colorStart, int colorEnd, boolean shuffled, boolean custom,
                    boolean special) {
        this.special = special;
        this.name = name;
        this.subtitle = subtitle;
        this.tag = tag;
        this.songs = songs;
        this.colorStart = colorStart;
        this.colorEnd = colorEnd;
        this.shuffled = shuffled;
        this.custom = custom;
    }

    // for example: "3 tracks • 12m"
    public String stats() {
        if (songs.isEmpty()) return "Empty";

        long totalMs = 0;
        for (Song s : songs) totalMs += s.durationMs;
        long minutes = Math.max(1, totalMs / 60000);

        String tracks = songs.size() == 1 ? "1 track" : songs.size() + " tracks";
        String time = minutes >= 60
                ? (minutes / 60) + "h " + (minutes % 60) + "m"
                : minutes + "m";
        return tracks + " • " + time;
    }
}