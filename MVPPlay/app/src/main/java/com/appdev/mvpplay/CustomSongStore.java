package com.appdev.mvpplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

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

// songs the user adds from the phone: copied into the app's private folder,
// details saved in SharedPreferences
public class CustomSongStore {

    private static final String PREFS = "custom_songs";
    private static final String KEY_LIST = "list";
    private static final String KEY_NEXT = "next_id";

    private static final String[][] PALETTE = {
            {"#2B1B5E", "#7C3AED"},
            {"#0E3B4A", "#22D3EE"},
            {"#3B1A7A", "#C084FC"},
            {"#0F3D2E", "#34D399"},
            {"#4A1B3A", "#F472B6"}
    };

    // what we can read from the chosen file to pre-fill the form
    public static class Info {
        public String title = "";
        public String artist = "";
        public byte[] art;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------- reading ----------
    public static List<Song> load(Context c) {
        List<Song> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(c).getString(KEY_LIST, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                int id = o.getInt("id");
                File audio = new File(o.getString("audio"));
                if (!audio.exists()) continue;

                String thumb = o.optString("thumb", "");
                long durationMs = o.optLong("duration", 0);
                long seconds = durationMs / 1000;
                String duration = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
                String[] colors = PALETTE[id % PALETTE.length];

                out.add(new Song(o.getString("title"), o.getString("artist"), "MY SONG",
                        duration, false,
                        Color.parseColor(colors[0]), Color.parseColor(colors[1]),
                        -id, durationMs, false,
                        Uri.fromFile(audio).toString(),
                        thumb.isEmpty() ? null : thumb));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    // reads the title, artist and embedded picture of the chosen file
    public static Info peek(Context c, Uri audio) {
        Info info = new Info();
        String name = displayName(c, audio);
        int dot = name.lastIndexOf('.');
        info.title = dot > 0 ? name.substring(0, dot) : name;

        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(c, audio);
            String t = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String a = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (t != null && !t.trim().isEmpty()) info.title = t.trim();
            if (a != null && !a.trim().isEmpty()) info.artist = a.trim();
            info.art = r.getEmbeddedPicture();
        } catch (Exception ignored) {
        }
        try {
            r.release();
        } catch (Exception ignored) {
        }
        return info;
    }

    // ---------- adding ----------
    // copies the song (and thumbnail) into the app and saves its details. Call from a background thread.
    public static boolean importSong(Context c, Uri audio, String title, String artist,
                                     Uri thumbUri, byte[] embeddedArt) {
        File dir = new File(c.getFilesDir(), "custom_songs");
        if (!dir.exists() && !dir.mkdirs()) return false;

        int id = nextId(c);
        String name = displayName(c, audio);
        String ext = "mp3";
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);

        File audioFile = new File(dir, "song_" + id + "." + ext);
        try (InputStream in = c.getContentResolver().openInputStream(audio);
             OutputStream out = new FileOutputStream(audioFile)) {
            if (in == null) return false;
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        } catch (IOException e) {
            audioFile.delete();
            return false;
        }

        long duration = 0;
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(audioFile.getAbsolutePath());
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) duration = Long.parseLong(d);
        } catch (Exception ignored) {
        }
        try {
            r.release();
        } catch (Exception ignored) {
        }

        // thumbnail: the picture the user chose, else the one inside the file
        Bitmap bitmap = (thumbUri != null) ? decode(c, thumbUri, 600) : null;
        if (bitmap == null && embeddedArt != null) {
            bitmap = BitmapFactory.decodeByteArray(embeddedArt, 0, embeddedArt.length);
        }
        File thumbFile = null;
        if (bitmap != null) {
            thumbFile = new File(dir, "thumb_" + id + ".jpg");
            try (OutputStream out = new FileOutputStream(thumbFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            } catch (IOException e) {
                thumbFile = null;
            }
        }

        try {
            JSONArray array = new JSONArray(prefs(c).getString(KEY_LIST, "[]"));
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("title", title);
            o.put("artist", artist);
            o.put("audio", audioFile.getAbsolutePath());
            o.put("thumb", thumbFile != null ? thumbFile.getAbsolutePath() : "");
            o.put("duration", duration);
            array.put(o);
            prefs(c).edit().putString(KEY_LIST, array.toString()).apply();
        } catch (JSONException e) {
            return false;
        }
        return true;
    }

    // ---------- deleting ----------
    public static void delete(Context c, Song song) {
        int id = -song.resId;
        try {
            JSONArray old = new JSONArray(prefs(c).getString(KEY_LIST, "[]"));
            JSONArray keep = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.getJSONObject(i);
                if (o.getInt("id") == id) {
                    new File(o.getString("audio")).delete();
                    String thumb = o.optString("thumb", "");
                    if (!thumb.isEmpty()) new File(thumb).delete();
                } else {
                    keep.put(o);
                }
            }
            prefs(c).edit().putString(KEY_LIST, keep.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    // ---------- helpers ----------
    private static synchronized int nextId(Context c) {
        int id = prefs(c).getInt(KEY_NEXT, 1);
        prefs(c).edit().putInt(KEY_NEXT, id + 1).apply();
        return id;
    }

    private static String displayName(Context c, Uri uri) {
        try (Cursor cursor = c.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null) return name;
                }
            }
        } catch (Exception ignored) {
        }
        return "song";
    }

    // loads a picture from the gallery at about the size needed
    private static Bitmap decode(Context c, Uri uri, int maxSide) {
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