package com.appdev.mvpplay;

import android.content.Context;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MusicLibrary {

    // cover placeholder colors, reused in order
    private static final String[][] PALETTE = {
            {"#2B1B5E", "#7C3AED"},
            {"#0E3B4A", "#22D3EE"},
            {"#3B1A7A", "#C084FC"},
            {"#0F3D2E", "#34D399"},
            {"#4A1B3A", "#F472B6"}
    };

    private static final String PODCAST_PREFIX = "podcast_";
    private static final String EFFECT_PREFIX = "fx_";   // background effects are not songs

    // the scanned lists are kept in memory, so the tabs open fast
    private static List<Song> songsCache;
    private static List<Song> podcastsCache;

    // forgets the scanned lists so the next load reads everything again
    public static void clearCache() {
        songsCache = null;
        podcastsCache = null;
    }

    // all the songs (every file in res/raw that does NOT start with "podcast_")
    public static List<Song> load(Context context) {
        if (songsCache == null) songsCache = loadFiles(context, false);
        List<Song> copy = new ArrayList<>(songsCache);
        HiddenSongStore.removeHidden(context, copy);   // songs you deleted
        AppSettings.sortSongs(context, copy);   // the order chosen in Settings
        return copy;
    }

    // the built-in songs plus the songs the user added
    public static List<Song> loadWithCustom(Context context) {
        // built-in songs and your own, sorted together by the Settings choice
        List<Song> list = new ArrayList<>(load(context));
        list.addAll(CustomSongStore.load(context));
        SortHelper.sortSongs(context, list);
        return list;
    }

    // all the podcast episodes (files that start with "podcast_")
    // For a podcast: title = episode, artist = show name, plays = "Host: ..." (or empty)
    public static List<Song> loadPodcasts(Context context) {
        if (podcastsCache == null) podcastsCache = loadFiles(context, true);
        List<Song> list = new ArrayList<>(podcastsCache);
        HiddenSongStore.removeHidden(context, list);              // official episodes you deleted
        list.addAll(CustomPodcastStore.loadEpisodes(context));    // your own podcasts
        return list;
    }

    private static List<Song> loadFiles(Context context, boolean podcasts) {
        List<Song> items = new ArrayList<>();

        // every file in res/raw becomes a field of R.raw
        Field[] fields = R.raw.class.getFields();
        Arrays.sort(fields, (a, b) -> a.getName().compareTo(b.getName()));

        int index = 0;
        for (Field field : fields) {
            String name = field.getName();
            if (name.startsWith(EFFECT_PREFIX)) continue;   // GIFs and the mp4 effect are not songs
            boolean isPodcast = name.startsWith(PODCAST_PREFIX);
            if (isPodcast != podcasts) continue;

            try {
                int resId = field.getInt(null);

                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                Uri uri = Uri.parse("android.resource://" + context.getPackageName() + "/" + resId);
                retriever.setDataSource(context, uri);

                String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                String bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
                String hasVideoValue = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
                boolean hasVideo = "yes".equals(hasVideoValue);
                try {
                    retriever.release();
                } catch (Exception ignored) {
                }

                long durationMs = durationStr != null ? Long.parseLong(durationStr) : 0;
                long totalSeconds = durationMs / 1000;
                String duration = String.format(Locale.US, "%02d:%02d",
                        totalSeconds / 60, totalSeconds % 60);

                String[] colors = PALETTE[index % PALETTE.length];
                int colorStart = Color.parseColor(colors[0]);
                int colorEnd = Color.parseColor(colors[1]);

                if (podcasts) {
                    String body = name.substring(PODCAST_PREFIX.length());
                    String show = "Podcast";
                    String episode = prettyName(body);
                    if (body.contains("__")) {
                        String[] parts = body.split("__", 2);
                        show = prettyName(parts[0]);
                        episode = prettyName(parts[1].replaceFirst("^ep\\d+_", ""));
                    }
                    String host = (artist != null && !artist.trim().isEmpty())
                            ? "Host: " + artist.trim() : "";

                    items.add(new Song(episode, show, host, duration, false,
                            colorStart, colorEnd, resId, durationMs, hasVideo));
                } else {
                    // file name wins when it uses the artist__title format
                    if (name.contains("__")) {
                        String[] parts = name.split("__", 2);
                        artist = prettyName(parts[0]);
                        artist = artist.replaceFirst("^X(?=\\d)", "");   // "X6cyclemind" -> "6cyclemind"
                        title = prettyName(parts[1]);
                    }
                    if (title == null || title.trim().isEmpty()) {
                        title = prettyName(name);
                    }
                    if (artist == null || artist.trim().isEmpty()) {
                        artist = "Unknown Artist";
                    }

                    String plays = "MP3";
                    if (bitrateStr != null) {
                        plays = (Long.parseLong(bitrateStr) / 1000) + "K";
                    }

                    items.add(new Song(title, artist, plays, duration, false,
                            colorStart, colorEnd, resId, durationMs, hasVideo));
                }
                index++;

            } catch (Exception e) {
                // not an audio file, skip it
            }
        }
        if (!podcasts) AppSettings.sortSongs(context, items);   // the order chosen in Settings
        return items;
    }

    // "midnight_echoes" -> "Midnight Echoes"
    private static String prettyName(String fileName) {
        String[] words = fileName.replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}