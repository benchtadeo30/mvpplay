package com.appdev.mvpplay;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

// looks a song up on LRCLIB (call it on a background thread)
public class LyricsFetcher {

    private static final String BASE = "https://lrclib.net/api/";

    // synced lyrics if there are any, otherwise plain lyrics, otherwise null
    public static String fetch(String title, String artist, long durationSec) {
        try {
            boolean hasArtist = artist != null && !artist.trim().isEmpty()
                    && !artist.equalsIgnoreCase("Unknown Artist");

            // 1) exact match: title + artist + length
            if (hasArtist) {
                String url = BASE + "get?track_name=" + enc(title) + "&artist_name=" + enc(artist);
                if (durationSec > 0) url += "&duration=" + durationSec;
                String body = get(url);
                if (body != null) {
                    String r = pick(new JSONObject(body));
                    if (r != null) return r;
                }
            }

            // 2) search, keeping only results with about the same length
            String url = BASE + "search?track_name=" + enc(title)
                    + (hasArtist ? "&artist_name=" + enc(artist) : "");
            String body = get(url);
            if (body == null) return null;
            JSONArray array = new JSONArray(body);

            String plainFallback = null;
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                if (durationSec > 0 && Math.abs(o.optDouble("duration", 0) - durationSec) > 5) {
                    continue;
                }
                String synced = str(o, "syncedLyrics");
                if (!synced.isEmpty()) return synced;
                String plain = str(o, "plainLyrics");
                if (plainFallback == null && !plain.isEmpty()) plainFallback = plain;
            }
            return plainFallback;
        } catch (Exception e) {
            return null;
        }
    }

    private static String pick(JSONObject o) {
        String synced = str(o, "syncedLyrics");
        if (!synced.isEmpty()) return synced;
        String plain = str(o, "plainLyrics");
        return plain.isEmpty() ? null : plain;
    }

    private static String str(JSONObject o, String key) {
        return o.isNull(key) ? "" : o.optString(key, "").trim();
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String get(String address) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "MVPPlay/1.0");
            if (conn.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}