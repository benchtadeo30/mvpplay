package com.appdev.mvpplay;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// listens to the sound on its way to the speaker and measures how strong
// the low, middle and high tones are. The sound itself is not changed.
@OptIn(markerClass = UnstableApi.class)
public class LevelAudioProcessor extends BaseAudioProcessor {

    private static final int FFT = 2048;   // size of each sound snapshot
    private static final int HOP = 1024;   // a new snapshot every 1024 samples (about 43 per second)
    private static final int BANDS = AudioLevels.BANDS;

    // tuning: if the bars are always too low or too high, change these
    private static final float FLOOR_DB = -65f;   // at or below this level a bar is at the bottom
    private static final float CEIL_DB = -15f;    // at or above this level a bar is at the top
    private static final float TILT_DB = 20f;     // boosts high tones (music has less energy there)

    private final float[] window = new float[FFT];
    private final float[] cosT = new float[FFT / 2];
    private final float[] sinT = new float[FFT / 2];
    private final float[] buf = new float[FFT];
    private final float[] re = new float[FFT];
    private final float[] im = new float[FFT];
    private final float[] bands = new float[BANDS];
    private final int[] binFrom = new int[BANDS];
    private final int[] binTo = new int[BANDS];

    private int fill = 0;
    private int channels = 2;
    private int encoding = C.ENCODING_PCM_16BIT;

    public LevelAudioProcessor() {
        for (int i = 0; i < FFT; i++) {
            window[i] = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * i / (FFT - 1));
        }
        for (int i = 0; i < FFT / 2; i++) {
            cosT[i] = (float) Math.cos(-2 * Math.PI * i / FFT);
            sinT[i] = (float) Math.sin(-2 * Math.PI * i / FFT);
        }
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) {
        int enc = inputAudioFormat.encoding;
        // other formats just pass through untouched (the bars use the fallback bounce)
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
            return AudioFormat.NOT_SET;
        }
        encoding = enc;
        channels = Math.max(1, inputAudioFormat.channelCount);
        buildBands(inputAudioFormat.sampleRate);
        fill = 0;
        return inputAudioFormat;
    }

    // which snapshot bins belong to each of the 40 bars (low to high, log spaced)
    private void buildBands(int sampleRate) {
        float binHz = sampleRate / (float) FFT;
        float fMin = 40f;
        float fMax = Math.min(14000f, sampleRate * 0.45f);
        double ratio = Math.pow(fMax / fMin, 1.0 / BANDS);
        for (int b = 0; b < BANDS; b++) {
            double f0 = fMin * Math.pow(ratio, b);
            double f1 = f0 * ratio;
            int from = Math.max(1, (int) Math.round(f0 / binHz));
            int to = Math.max(from, (int) Math.round(f1 / binHz));
            binFrom[b] = Math.min(from, FFT / 2 - 1);
            binTo[b] = Math.min(to, FFT / 2 - 1);
        }
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int size = inputBuffer.remaining();
        if (size == 0) return;
        if (AudioLevels.enabled) {
            analyze(inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        }
        // pass the sound through unchanged
        ByteBuffer out = replaceOutputBuffer(size);
        out.put(inputBuffer);
        out.flip();
    }

    private void analyze(ByteBuffer src) {
        boolean isFloat = encoding == C.ENCODING_PCM_FLOAT;
        int frameBytes = (isFloat ? 4 : 2) * channels;
        int frames = src.remaining() / frameBytes;
        for (int f = 0; f < frames; f++) {
            float sum = 0f;
            for (int c = 0; c < channels; c++) {
                sum += isFloat ? src.getFloat() : src.getShort() / 32768f;
            }
            buf[fill++] = sum / channels;   // left and right mixed into one
            if (fill == FFT) {
                measure();
                System.arraycopy(buf, HOP, buf, 0, FFT - HOP);
                fill = FFT - HOP;
            }
        }
    }

    // one snapshot: sound -> strength of each band
    private void measure() {
        for (int i = 0; i < FFT; i++) {
            re[i] = buf[i] * window[i];
            im[i] = 0f;
        }
        fft();
        for (int b = 0; b < BANDS; b++) {
            float m = 0f;
            for (int k = binFrom[b]; k <= binTo[b]; k++) {
                float mag = (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]);
                if (mag > m) m = mag;
            }
            float norm = m / (FFT * 0.25f);   // a full-volume pure tone is about 1.0
            float db = 20f * (float) Math.log10(norm + 1e-6f) + TILT_DB * b / (BANDS - 1);
            float v = (db - FLOOR_DB) / (CEIL_DB - FLOOR_DB);
            bands[b] = Math.max(0f, Math.min(1f, v));
        }
        AudioLevels.push(bands);
    }

    // standard in-place FFT
    private void fft() {
        int n = FFT;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                float t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            int half = len >> 1;
            int step = n / len;
            for (int i = 0; i < n; i += len) {
                for (int k = 0; k < half; k++) {
                    float wr = cosT[k * step];
                    float wi = sinT[k * step];
                    int a = i + k;
                    int b = a + half;
                    float tr = re[b] * wr - im[b] * wi;
                    float ti = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - tr;
                    im[b] = im[a] - ti;
                    re[a] += tr;
                    im[a] += ti;
                }
            }
        }
    }
}