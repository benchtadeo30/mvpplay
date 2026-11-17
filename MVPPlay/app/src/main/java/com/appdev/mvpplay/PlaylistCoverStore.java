package com.appdev.mvpplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// the pictures the user picked as playlist covers.
// The picture is copied into the app, so the original file is never touched.
public class PlaylistCoverStore {

    public static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private static final String PREFS = "playlist_covers";
    private static final int SIZE = 720;   // the saved picture is a square of this many pixels

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "playlist_covers");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    // which playlist a picture belongs to (the gold one keeps its key when it is renamed)
    public static String keyFor(Playlist p) {
        if (p.special) return "special";
        if (p.custom) return "custom:" + p.name.trim().toLowerCase(Locale.ROOT);
        if ("ARTIST".equals(p.tag)) return "artist:" + p.name;
        return "official:" + p.name;
    }

    // the built-in cover of a playlist (0 = none). Only the gold playlist has one.
    public static int defaultCover(Playlist p) {
        return p.special ? R.drawable.benjieplaythumb : 0;
    }

    // the saved picture file of a playlist (null = no custom cover)
    public static String path(Context c, String key) {
        String file = prefs(c).getString(key, null);
        if (file == null) return null;
        File f = new File(dir(c), file);
        return f.exists() ? f.getAbsolutePath() : null;
    }

    // call on a background thread. Returns false if the picture could not be used.
    public static boolean save(Context c, String key, Uri uri) {
        Bitmap square = decodeSquare(c, uri);
        if (square == null) return false;

        // every save gets a new file name, so an old cached picture is never shown by mistake
        String name = "cover_" + System.currentTimeMillis() + ".jpg";
        File out = new File(dir(c), name);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            square.compress(Bitmap.CompressFormat.JPEG, 90, fos);
        } catch (Exception e) {
            out.delete();
            return false;
        }
        deleteFile(c, prefs(c).getString(key, null));
        prefs(c).edit().putString(key, name).apply();
        return true;
    }

    // back to the default cover
    public static void clear(Context c, String key) {
        deleteFile(c, prefs(c).getString(key, null));
        prefs(c).edit().remove(key).apply();
    }

    // a playlist was renamed: its picture follows the new name
    public static void move(Context c, String oldKey, String newKey) {
        if (oldKey.equals(newKey)) return;
        String file = prefs(c).getString(oldKey, null);
        if (file == null) return;
        deleteFile(c, prefs(c).getString(newKey, null));
        prefs(c).edit().remove(oldKey).putString(newKey, file).apply();
    }

    private static void deleteFile(Context c, String name) {
        if (name != null) new File(dir(c), name).delete();
    }

    // reads the picture, shrinks it and cuts a square from the middle
    private static Bitmap decodeSquare(Context c, Uri uri) {
        try {
            Bitmap b = Build.VERSION.SDK_INT >= 28 ? decodeModern(c, uri) : decodeOld(c, uri);
            if (b == null) return null;
            int side = Math.min(b.getWidth(), b.getHeight());
            Bitmap square = Bitmap.createBitmap(b,
                    (b.getWidth() - side) / 2, (b.getHeight() - side) / 2, side, side);
            if (side > SIZE) square = Bitmap.createScaledBitmap(square, SIZE, SIZE, true);
            return square;
        } catch (Throwable e) {
            return null;
        }
    }

    // Android 9 and newer: also turns photos the right way up
    @RequiresApi(28)
    private static Bitmap decodeModern(Context c, Uri uri) throws Exception {
        ImageDecoder.Source src = ImageDecoder.createSource(c.getContentResolver(), uri);
        return ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
            int w = info.getSize().getWidth();
            int h = info.getSize().getHeight();
            float scale = Math.min(1f, SIZE / (float) Math.max(1, Math.min(w, h)));
            decoder.setTargetSize(Math.max(1, Math.round(w * scale)),
                    Math.max(1, Math.round(h * scale)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
    }

    // older phones
    private static Bitmap decodeOld(Context c, Uri uri) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, options);
        }
        int sample = 1;
        while (Math.min(options.outWidth, options.outHeight) / (sample * 2) >= SIZE) sample *= 2;
        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, options);
        }
    }
}