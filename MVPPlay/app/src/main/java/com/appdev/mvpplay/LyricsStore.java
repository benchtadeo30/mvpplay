package com.appdev.mvpplay;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

// keeps the lyrics of each song on the phone (one small text file per song)
public class LyricsStore {

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "lyrics");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File file(Context c, String key) {
        return new File(dir(c), key + ".txt");
    }

    // the saved lyrics (null if there are none)
    public static String load(Context c, String key) {
        File f = file(c, key);
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            return text.trim().isEmpty() ? null : text;
        } catch (Exception e) {
            return null;
        }
    }

    public static void save(Context c, String key, String text) {
        try (OutputStream out = new FileOutputStream(file(c, key))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public static void delete(Context c, String key) {
        file(c, key).delete();
    }
}