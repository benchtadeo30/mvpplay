package com.appdev.mvpplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// podcasts the user makes: each podcast (show) has a title, description and
// thumbnail, and any number of video episodes. Everything is copied into the
// app's private folder, details saved in SharedPreferences.
public class CustomPodcastStore {

    private static final String PREFS = "custom_podcasts";
    private static final String KEY_SHOWS = "shows";
    private static final String KEY_EPISODES = "episodes";
    private static final String KEY_NEXT = "next_id";

    // episode ids start here so they never clash with custom song ids
    private static final int ID_BASE = 1_000_000;

    public static class ShowInfo {
        public int id;
        public String title = "";
        public String description = "";
        public String thumb = "";
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "custom_podcasts");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    // ---------- reading ----------
    public static List<ShowInfo> loadShows(Context c) {
        List<ShowInfo> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(c).getString(KEY_SHOWS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                ShowInfo s = new ShowInfo();
                s.id = o.getInt("id");
                s.title = o.getString("title");
                s.description = o.optString("description", "");
                s.thumb = o.optString("thumb", "");
                out.add(s);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    public static ShowInfo findShowByTitle(Context c, String title) {
        for (ShowInfo s : loadShows(c)) {
            if (s.title.equalsIgnoreCase(title)) return s;
        }
        return null;
    }

    // all episodes of all your podcasts, as normal Song objects
    // (title = episode, artist = podcast name, plays = description)
    public static List<Song> loadEpisodes(Context c) {
        List<Song> out = new ArrayList<>();
        try {
            List<ShowInfo> shows = loadShows(c);
            JSONArray array = new JSONArray(prefs(c).getString(KEY_EPISODES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                File video = new File(o.getString("video"));
                if (!video.exists()) continue;

                int id = o.getInt("id");
                int showId = o.getInt("show");
                String showTitle = null;
                for (ShowInfo s : shows) if (s.id == showId) showTitle = s.title;
                if (showTitle == null) continue;

                String thumb = o.optString("thumb", "");
                long durationMs = o.optLong("duration", 0);
                long seconds = durationMs / 1000;
                String duration = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);

                out.add(new Song(o.getString("title"), showTitle,
                        o.optString("description", ""), duration, false,
                        Color.parseColor("#2B1B5E"), Color.parseColor("#7C3AED"),
                        -(ID_BASE + id), durationMs, true,
                        Uri.fromFile(video).toString(),
                        thumb.isEmpty() ? null : thumb));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    // ---------- creating (call these from a background thread) ----------
    // returns the new podcast's id, or -1 if it failed
    public static int createShow(Context c, String title, String description, Uri thumbUri) {
        int id = nextId(c);
        String thumbPath = saveBitmap(decode(c, thumbUri, 600), "show_" + id + ".jpg", c);
        try {
            JSONArray array = new JSONArray(prefs(c).getString(KEY_SHOWS, "[]"));
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("title", title);
            o.put("description", description);
            o.put("thumb", thumbPath);
            array.put(o);
            prefs(c).edit().putString(KEY_SHOWS, array.toString()).apply();
        } catch (JSONException e) {
            return -1;
        }
        return id;
    }

    // copies the video into the app and adds it as an episode of the podcast
    public static boolean addEpisode(Context c, int showId, Uri video, String title,
                                     String description, Uri thumbUri) {
        int id = nextId(c);
        File videoFile = new File(dir(c), "ep_" + id + ".mp4");
        try (InputStream in = c.getContentResolver().openInputStream(video);
             OutputStream out = new FileOutputStream(videoFile)) {
            if (in == null) return false;
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        } catch (IOException e) {
            videoFile.delete();
            return false;
        }

        long duration = 0;
        Bitmap frame = null;
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(videoFile.getAbsolutePath());
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) duration = Long.parseLong(d);
            frame = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (Exception ignored) {
        }
        try {
            r.release();
        } catch (Exception ignored) {
        }

        // thumbnail: the picture the user chose, else a frame from the video
        Bitmap bitmap = decode(c, thumbUri, 600);
        if (bitmap == null) bitmap = frame;
        String thumbPath = saveBitmap(bitmap, "ep_" + id + ".jpg", c);

        try {
            JSONArray array = new JSONArray(prefs(c).getString(KEY_EPISODES, "[]"));
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("show", showId);
            o.put("title", title);
            o.put("description", description);
            o.put("video", videoFile.getAbsolutePath());
            o.put("thumb", thumbPath);
            o.put("duration", duration);
            array.put(o);
            prefs(c).edit().putString(KEY_EPISODES, array.toString()).apply();
        } catch (JSONException e) {
            return false;
        }
        return true;
    }

    // true when this id belongs to one of your own podcast episodes
    public static boolean isEpisode(int resId) {
        return resId < -ID_BASE;
    }

    // "EP 1, EP 2..." inside its podcast, in the order the episodes were added
    public static int episodeNumber(Context c, int resId) {
        int id = -resId - ID_BASE;
        try {
            JSONArray array = new JSONArray(prefs(c).getString(KEY_EPISODES, "[]"));
            int showId = -1;
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                if (o.getInt("id") == id) showId = o.getInt("show");
            }
            if (showId < 0) return 0;
            int number = 0;
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                if (o.getInt("show") == showId && o.getInt("id") <= id) number++;
            }
            return number;
        } catch (JSONException e) {
            return 0;
        }
    }

    // the description the user wrote for an episode
    public static String descriptionOf(Context c, int resId) {
        int id = -resId - ID_BASE;
        try {
            JSONArray array = new JSONArray(prefs(c).getString(KEY_EPISODES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                if (o.getInt("id") == id) return o.optString("description", "");
            }
        } catch (JSONException ignored) {
        }
        return "";
    }

    // ---------- deleting ----------
    public static void deleteEpisode(Context c, Song episode) {
        int id = -episode.resId - ID_BASE;
        int showId = -1;
        boolean showStillHasEpisodes = false;
        try {
            JSONArray old = new JSONArray(prefs(c).getString(KEY_EPISODES, "[]"));
            JSONArray keep = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.getJSONObject(i);
                if (o.getInt("id") == id) {
                    showId = o.getInt("show");
                    new File(o.getString("video")).delete();
                    String thumb = o.optString("thumb", "");
                    if (!thumb.isEmpty()) new File(thumb).delete();
                } else {
                    keep.put(o);
                }
            }
            prefs(c).edit().putString(KEY_EPISODES, keep.toString()).apply();

            for (int i = 0; i < keep.length(); i++) {
                if (keep.getJSONObject(i).getInt("show") == showId) showStillHasEpisodes = true;
            }
        } catch (JSONException ignored) {
        }
        // a podcast with no episodes left is removed too
        if (showId >= 0 && !showStillHasEpisodes) deleteShow(c, showId);
    }

    // deletes the podcast and every episode in it
    public static void deleteShow(Context c, int showId) {
        try {
            JSONArray old = new JSONArray(prefs(c).getString(KEY_EPISODES, "[]"));
            JSONArray keep = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.getJSONObject(i);
                if (o.getInt("show") == showId) {
                    new File(o.getString("video")).delete();
                    String thumb = o.optString("thumb", "");
                    if (!thumb.isEmpty()) new File(thumb).delete();
                } else {
                    keep.put(o);
                }
            }
            prefs(c).edit().putString(KEY_EPISODES, keep.toString()).apply();

            JSONArray shows = new JSONArray(prefs(c).getString(KEY_SHOWS, "[]"));
            JSONArray keepShows = new JSONArray();
            for (int i = 0; i < shows.length(); i++) {
                JSONObject o = shows.getJSONObject(i);
                if (o.getInt("id") == showId) {
                    String thumb = o.optString("thumb", "");
                    if (!thumb.isEmpty()) new File(thumb).delete();
                } else {
                    keepShows.put(o);
                }
            }
            prefs(c).edit().putString(KEY_SHOWS, keepShows.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    // ---------- helpers ----------
    private static synchronized int nextId(Context c) {
        int id = prefs(c).getInt(KEY_NEXT, 1);
        prefs(c).edit().putInt(KEY_NEXT, id + 1).apply();
        return id;
    }

    private static String saveBitmap(Bitmap bitmap, String name, Context c) {
        if (bitmap == null) return "";
        File file = new File(dir(c), name);
        try (OutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            return file.getAbsolutePath();
        } catch (IOException e) {
            return "";
        }
    }

    private static Bitmap decode(Context c, Uri uri, int maxSide) {
        if (uri == null) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, options);
            }
            int sample = 1;
            while (options.outWidth / (sample * 2) >= maxSide
                    && options.outHeight / (sample * 2) >= maxSide) {
                sample *= 2;
            }
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }
}