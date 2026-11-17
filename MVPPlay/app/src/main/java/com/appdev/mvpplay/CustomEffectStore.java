package com.appdev.mvpplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

// the background effects the user added: the file is copied into the app
// (so the original can be deleted) and the title is saved next to it
public class CustomEffectStore {

    public static final int MAX = 10;                       // how many you can add
    private static final long MAX_BYTES = 50L * 1024 * 1024; // 50 MB per file
    private static final String PREFS = "custom_effects";
    private static final String KEY = "list";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONArray readArray(Context c) {
        try {
            return new JSONArray(prefs(c).getString(KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    // your effects, oldest first (an effect whose file is gone is skipped)
    public static synchronized List<EffectBackground.Fx> load(Context c) {
        List<EffectBackground.Fx> list = new ArrayList<>();
        JSONArray arr = readArray(c);
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                String path = o.getString("path");
                if (!new File(path).exists()) continue;
                list.add(EffectBackground.custom(o.getString("id"),
                        o.getString("title"), path, o.getInt("kind")));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    // copies the picked file into the app and saves the title.
    // Call it on a background thread. Returns null if it did not work.
    public static synchronized EffectBackground.Fx add(Context c, String title, Uri source,
                                                       int kind) {
        File dir = new File(c.getFilesDir(), "effects");
        if (!dir.exists() && !dir.mkdirs()) return null;

        String id = "c_" + System.currentTimeMillis();
        File out = new File(dir, id + (kind == EffectBackground.KIND_VIDEO ? ".mp4"
                : kind == EffectBackground.KIND_GIF ? ".gif" : ".img"));

        boolean tooBig = false;
        boolean ok = false;
        try (InputStream in = c.getContentResolver().openInputStream(source);
             OutputStream os = new FileOutputStream(out)) {
            if (in != null) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int n;
                while ((n = in.read(buffer)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) {
                        tooBig = true;
                        break;
                    }
                    os.write(buffer, 0, n);
                }
                ok = !tooBig;
            }
        } catch (Exception e) {
            ok = false;
        }
        if (!ok) {
            out.delete();
            return null;
        }

        try {
            JSONArray arr = readArray(c);
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("title", title);
            o.put("path", out.getAbsolutePath());
            o.put("kind", kind);
            arr.put(o);
            prefs(c).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception e) {
            out.delete();
            return null;
        }
        return EffectBackground.custom(id, title, out.getAbsolutePath(), kind);
    }

    // removes the effect and its saved file
    public static synchronized void delete(Context c, String id) {
        JSONArray arr = readArray(c);
        JSONArray kept = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                if (id.equals(o.getString("id"))) {
                    new File(o.getString("path")).delete();
                } else {
                    kept.put(o);
                }
            } catch (Exception ignored) {
            }
        }
        prefs(c).edit().putString(KEY, kept.toString()).apply();
    }
}