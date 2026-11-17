package com.appdev.mvpplay;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class Thumbs {

    private static final Map<Integer, Integer> idCache = new HashMap<>();

    private static final LruCache<String, Bitmap> bitmapCache =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 1024 / 16)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };

    // finds the picture with the same name as the song file (0 if there is none)
    public static int thumbForRaw(Context context, int rawResId) {
        Integer cached = idCache.get(rawResId);
        if (cached != null) return cached;

        int id = 0;
        try {
            String name = context.getResources().getResourceEntryName(rawResId);
            id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        } catch (Exception ignored) {
        }
        idCache.put(rawResId, id);
        return id;
    }

    // loads the picture at about the size needed (not bigger), and remembers it
    public static Bitmap get(Context context, int drawableId, int sizePx) {
        String key = drawableId + "@" + sizePx;
        Bitmap cached = bitmapCache.get(key);
        if (cached != null) return cached;

        Resources res = context.getResources();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, drawableId, options);

        int sample = 1;
        while (options.outWidth / (sample * 2) >= sizePx
                && options.outHeight / (sample * 2) >= sizePx) {
            sample *= 2;
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeResource(res, drawableId, options);
        if (bitmap != null) {
            bitmapCache.put(key, bitmap);
        }
        return bitmap;
    }

    // a square picture taken from inside a video file (null if it cannot be read)
    public static Bitmap videoFrame(Context context, int rawResId, int sizePx) {
        String key = "video" + rawResId + "@" + sizePx;
        Bitmap cached = bitmapCache.get(key);
        if (cached != null) return cached;

        Bitmap result = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context,
                    Uri.parse("android.resource://" + context.getPackageName() + "/" + rawResId));

            Bitmap frame = retriever.getFrameAtTime(1_000_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) {
                frame = retriever.getFrameAtTime();
            }
            if (frame != null) {
                int side = Math.min(frame.getWidth(), frame.getHeight());
                Bitmap square = Bitmap.createBitmap(frame,
                        (frame.getWidth() - side) / 2, (frame.getHeight() - side) / 2, side, side);
                result = Bitmap.createScaledBitmap(square, sizePx, sizePx, true);
            }
        } catch (Exception ignored) {
        }
        try {
            retriever.release();
        } catch (Exception ignored) {
        }

        if (result != null) {
            bitmapCache.put(key, result);
        }
        return result;
    }

    // a small square JPEG for the notification and lock screen (null if no picture)
    public static byte[] jpegBytes(Context context, int rawResId, int sizePx, boolean hasVideo) {
        Bitmap bitmap = null;

        int id = thumbForRaw(context, rawResId);
        if (id != 0) {
            bitmap = get(context, id, sizePx);
        }
        if (bitmap == null && hasVideo) {
            bitmap = videoFrame(context, rawResId, sizePx);
        }
        if (bitmap == null) return null;

        int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap square = Bitmap.createBitmap(bitmap,
                (bitmap.getWidth() - side) / 2, (bitmap.getHeight() - side) / 2, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(square, sizePx, sizePx, true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out);
        return out.toByteArray();
    }
    // loads a saved picture file (custom songs) at about the size needed
    public static Bitmap file(String path, int sizePx) {
        String key = "file:" + path + "@" + sizePx;
        Bitmap cached = bitmapCache.get(key);
        if (cached != null) return cached;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        int sample = 1;
        while (options.outWidth / (sample * 2) >= sizePx
                && options.outHeight / (sample * 2) >= sizePx) {
            sample *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap != null) bitmapCache.put(key, bitmap);
        return bitmap;
    }

    // the cover for any song or episode: your saved picture, then the built-in
    // picture, then a frame from the video (null = use the gradient)
    public static Bitmap forSong(Context context, Song song, int sizePx) {
        if (song.thumbPath != null) return file(song.thumbPath, sizePx);
        int id = thumbForRaw(context, song.resId);
        if (id != 0) return get(context, id, sizePx);
        if (song.hasVideo && song.resId > 0) return videoFrame(context, song.resId, sizePx);
        return null;
    }

    // notification / lock screen picture for any song (custom or built-in)
    public static byte[] jpegBytes(Context context, Song song, int sizePx) {
        if (song.thumbPath == null) {
            return jpegBytes(context, song.resId, sizePx, song.hasVideo);
        }
        Bitmap bitmap = file(song.thumbPath, sizePx);
        if (bitmap == null) return null;

        int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap square = Bitmap.createBitmap(bitmap,
                (bitmap.getWidth() - side) / 2, (bitmap.getHeight() - side) / 2, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(square, sizePx, sizePx, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out);
        return out.toByteArray();
    }
}