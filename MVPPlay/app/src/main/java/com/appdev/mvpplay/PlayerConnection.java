package com.appdev.mvpplay;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

public class PlayerConnection {

    public interface Callback {
        void onReady(MediaController controller);
    }

    private static MediaController controller;
    private static ListenableFuture<MediaController> future;

    // connects to the background player (or reuses the existing connection)
    public static void connect(Context context, Callback callback) {
        if (controller != null && !controller.isConnected()) {
            controller = null;
            future = null;
        }
        if (controller != null) {
            callback.onReady(controller);
            return;
        }

        Context app = context.getApplicationContext();
        if (future == null) {
            SessionToken token = new SessionToken(app,
                    new ComponentName(app, PlaybackService.class));
            future = new MediaController.Builder(app, token).buildAsync();
        }
        future.addListener(() -> {
            try {
                controller = future.get();
                callback.onReady(controller);
            } catch (Exception e) {
                future = null;
            }
        }, ContextCompat.getMainExecutor(app));
    }

    // plays the list, starting at the chosen song
    public static void play(Context context, List<Song> songs, int startIndex) {
        start(context, songs, startIndex, 0, true);
    }

    // loads the list at a song and position; autoPlay = false leaves it paused
    public static void start(Context context, List<Song> songs, int startIndex,
                             long startPositionMs, boolean autoPlay) {
        connect(context, c -> {
            List<MediaItem> items = new ArrayList<>();
            for (Song s : songs) {
                // custom song: the saved file. Built-in song: the file in res/raw.
                Uri uri = (s.uri != null)
                        ? Uri.parse(s.uri)
                        : Uri.parse("android.resource://"
                                    + context.getPackageName() + "/" + s.resId);

                Bundle extras = new Bundle();
                extras.putInt("colorStart", s.colorStart);
                extras.putInt("colorEnd", s.colorEnd);
                extras.putBoolean("hasVideo", s.hasVideo);
                // custom songs: pass the file and thumbnail to the Now Playing screen
                if (s.uri != null) extras.putString("uri", s.uri);
                if (s.thumbPath != null) extras.putString("thumbPath", s.thumbPath);

                MediaMetadata.Builder metaBuilder = new MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setExtras(extras);

                // small picture for the notification and lock screen
                byte[] artwork = Thumbs.jpegBytes(context, s, 320);
                if (artwork != null) {
                    metaBuilder.setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER);
                }
                MediaMetadata meta = metaBuilder.build();

                items.add(new MediaItem.Builder()
                        .setMediaId(String.valueOf(s.resId))
                        .setUri(uri)
                        .setMediaMetadata(meta)
                        .build());
            }
            c.setMediaItems(items, startIndex, startPositionMs);
            c.prepare();
            if (autoPlay) c.play();
        });
    }

    // lets screens read the current player (null until connected)
    public static MediaController get() {
        return controller;
    }
}