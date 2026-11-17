package com.appdev.mvpplay;

public class Song {
    public final String title;
    public final String artist;
    public final String plays;      // bitrate for songs, "Host: ..." for podcasts
    public final String duration;
    public final boolean hiRes;
    public final int colorStart;
    public final int colorEnd;
    public final int resId;         // the file inside res/raw (negative = a custom song)
    public final long durationMs;
    public final boolean hasVideo;  // true when the file contains a video
    public final String uri;        // custom songs only: the saved file (null for res/raw songs)
    public final String thumbPath;  // custom songs only: the saved thumbnail (can be null)

    public Song(String title, String artist, String plays, String duration,
                boolean hiRes, int colorStart, int colorEnd,
                int resId, long durationMs, boolean hasVideo) {
        this(title, artist, plays, duration, hiRes, colorStart, colorEnd,
                resId, durationMs, hasVideo, null, null);
    }

    public Song(String title, String artist, String plays, String duration,
                boolean hiRes, int colorStart, int colorEnd,
                int resId, long durationMs, boolean hasVideo,
                String uri, String thumbPath) {
        this.title = title;
        this.artist = artist;
        this.plays = plays;
        this.duration = duration;
        this.hiRes = hiRes;
        this.colorStart = colorStart;
        this.colorEnd = colorEnd;
        this.resId = resId;
        this.durationMs = durationMs;
        this.hasVideo = hasVideo;
        this.uri = uri;
        this.thumbPath = thumbPath;
    }

    public boolean isCustom() {
        return uri != null;
    }
}