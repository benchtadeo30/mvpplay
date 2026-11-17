package com.appdev.mvpplay;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

public class SleepTimer {

    private static final long FADE_MS = 4000;   // the last 4 seconds fade out
    private static final Handler handler = new Handler(Looper.getMainLooper());

    private static long endTimeMs = 0;          // when the countdown ends (0 = no countdown)
    private static boolean endOfSong = false;   // "end of current song" mode
    private static Player.Listener songEndListener = null;

    private static final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (endTimeMs == 0) return;

            long remaining = endTimeMs - SystemClock.elapsedRealtime();
            MediaController c = PlayerConnection.get();

            if (remaining <= 0) {
                // time is up: pause, then put the volume back to normal
                if (c != null) {
                    c.pause();
                    c.setVolume(1f);
                }
                endTimeMs = 0;
                return;
            }

            long delay;
            if (remaining <= FADE_MS) {
                // fade out gently
                if (c != null) c.setVolume(remaining / (float) FADE_MS);
                delay = 150;
            } else {
                delay = Math.min(1000, remaining - FADE_MS);
            }
            handler.postDelayed(this, Math.max(50, delay));
        }
    };

    public static boolean isActive() {
        return endTimeMs != 0 || endOfSong;
    }

    public static boolean isEndOfSong() {
        return endOfSong;
    }

    public static long remainingMs() {
        if (endTimeMs == 0) return 0;
        return Math.max(0, endTimeMs - SystemClock.elapsedRealtime());
    }

    // pause after a number of minutes
    public static void startMinutes(int minutes) {
        cancel();
        endTimeMs = SystemClock.elapsedRealtime() + minutes * 60_000L;
        handler.post(tick);
    }

    // pause when the current song finishes
    public static void startEndOfSong() {
        cancel();
        MediaController c = PlayerConnection.get();
        if (c == null) return;

        endOfSong = true;
        songEndListener = new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                        || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    MediaController ctrl = PlayerConnection.get();
                    if (ctrl != null) ctrl.pause();
                    cancel();
                }
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) cancel();
            }
        };
        c.addListener(songEndListener);
    }

    // turn the timer off and restore normal volume
    public static void cancel() {
        handler.removeCallbacks(tick);
        MediaController c = PlayerConnection.get();
        if (c != null) {
            if (songEndListener != null) c.removeListener(songEndListener);
            c.setVolume(1f);
        }
        endTimeMs = 0;
        endOfSong = false;
        songEndListener = null;
    }
}
