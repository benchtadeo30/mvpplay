package com.appdev.mvpplay;

import android.os.SystemClock;

// the meeting point between the player (writes) and the waveform (reads)
public final class AudioLevels {

    public static final int BANDS = 40;   // must match BAR_COUNT in WaveformView
    private static final int RING = 64;
    private static final long STALE_MS = 300;

    // the player only analyses the sound while Now Playing is open (saves battery)
    public static volatile boolean enabled = false;

    private static final Object LOCK = new Object();
    private static final float[][] frames = new float[RING][BANDS];
    private static final long[] times = new long[RING];
    private static int head = 0;
    private static int count = 0;

    private AudioLevels() {
    }

    // called by the player about 43 times a second
    static void push(float[] bands) {
        synchronized (LOCK) {
            System.arraycopy(bands, 0, frames[head], 0, BANDS);
            times[head] = SystemClock.uptimeMillis();
            head = (head + 1) % RING;
            if (count < RING) count++;
        }
    }

    // copies the levels from "delayMs ago" into out.
    // false = no fresh data (paused, or nothing is being analysed)
    public static boolean read(float[] out, long delayMs) {
        long target = SystemClock.uptimeMillis() - delayMs;
        synchronized (LOCK) {
            for (int n = 0; n < count; n++) {
                int idx = (head - 1 - n + RING * 2) % RING;
                if (times[idx] <= target) {
                    if (target - times[idx] > STALE_MS) return false;
                    System.arraycopy(frames[idx], 0, out, 0, BANDS);
                    return true;
                }
            }
        }
        return false;
    }
}