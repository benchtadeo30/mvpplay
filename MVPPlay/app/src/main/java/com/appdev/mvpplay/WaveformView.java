package com.appdev.mvpplay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Random;

public class WaveformView extends View {

    public interface OnSeekListener {
        void onScrub(float fraction);     // while the finger is moving
        void onSeekEnd(float fraction);   // when the finger is lifted
    }

    private static final int BAR_COUNT = 40;

    private final float[] heights = new float[BAR_COUNT];
    private final Paint barPlayed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barIdle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackIdle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPlayed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;

    private float targetProgress = 0f;   // the real position of the song
    private float shownProgress = 0f;    // what is drawn (glides toward the real one)
    private float bounce = 0f;           // 0 = bars settled, 1 = full equalizer bounce
    private boolean playing = false;
    private boolean touching = false;
    private boolean attached = false;
    private boolean animating = false;
    private OnSeekListener listener;

    private static final long DELAY_MS = 200;   // the sound is heard a little after it is analysed
    private final float[] raw = new float[BAR_COUNT];      // levels just read from the player
    private final float[] levels = new float[BAR_COUNT];   // smoothed levels that are drawn
    private long lastDataTime = 0L;

    // runs once per screen frame while something is moving
    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            boolean more = false;

            // glide the shown progress toward the real position
            if (!touching) {
                float diff = targetProgress - shownProgress;
                if (Math.abs(diff) > 0.0004f) {
                    shownProgress += diff * 0.2f;
                    more = true;
                } else {
                    shownProgress = targetProgress;
                }
            }

            // ease the bounce up while playing, and down when paused
            float goal = playing ? 1f : 0f;
            float bounceDiff = goal - bounce;
            if (Math.abs(bounceDiff) > 0.01f) {
                bounce += bounceDiff * 0.08f;
                more = true;
            } else {
                bounce = goal;
            }
            if (bounce > 0f) more = true;

            // real sound levels: bars shoot up fast and fall back slowly
            if (playing || bounce > 0f) {
                boolean got = AudioLevels.read(raw, DELAY_MS);
                if (got) lastDataTime = SystemClock.uptimeMillis();
                for (int i = 0; i < BAR_COUNT; i++) {
                    float target = got ? raw[i] : 0f;
                    float k = target > levels[i] ? 0.55f : 0.14f;
                    levels[i] += (target - levels[i]) * k;
                    if (levels[i] > 0.01f) more = true;
                }
            }

            invalidate();

            if (more && attached) {
                postOnAnimation(this);
            } else {
                animating = false;
            }
        }
    };

    public WaveformView(Context context) {
        this(context, null);
    }

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        barIdle.setColor(ContextCompat.getColor(context, R.color.surface_segment));
        trackIdle.setColor(ContextCompat.getColor(context, R.color.surface_segment));
        thumb.setColor(ContextCompat.getColor(context, R.color.text_primary));
        setSeed(0);
    }

    private void startAnimating() {
        if (!animating && attached) {
            animating = true;
            postOnAnimation(frame);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        startAnimating();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        animating = false;
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }

    public void setOnSeekListener(OnSeekListener listener) {
        this.listener = listener;
    }

    // gives each song its own bar pattern
    public void setSeed(long seed) {
        Random random = new Random(seed);
        float previous = 0.5f;
        for (int i = 0; i < BAR_COUNT; i++) {
            float value = 0.15f + 0.85f * random.nextFloat();
            previous = previous * 0.5f + value * 0.5f;
            heights[i] = previous;
        }
        invalidate();
    }

    // the real position: the drawn progress glides toward it
    public void setProgress(float fraction) {
        targetProgress = Math.max(0f, Math.min(1f, fraction));
        startAnimating();
    }

    // jump without gliding (used when a new song starts)
    public void jumpTo(float fraction) {
        targetProgress = Math.max(0f, Math.min(1f, fraction));
        shownProgress = targetProgress;
        invalidate();
        startAnimating();
    }

    // true while music is playing: the bars bounce
    public void setPlaying(boolean isPlaying) {
        playing = isPlaying;
        if (isPlaying) lastDataTime = SystemClock.uptimeMillis();   // grace time before the fallback
        startAnimating();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        LinearGradient gradient = new LinearGradient(0, 0, w, 0,
                ContextCompat.getColor(getContext(), R.color.accent_cyan_light),
                ContextCompat.getColor(getContext(), R.color.accent_purple),
                Shader.TileMode.CLAMP);
        barPlayed.setShader(gradient);
        trackPlayed.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float time = SystemClock.uptimeMillis() / 1000f;

        // the layout follows the height of the view: bars on top, the thin track at the bottom
        float thumbR = 8 * density;
        float trackH = 4 * density;
        float trackY = h - thumbR - density;
        float barsBottom = trackY - thumbR - 6 * density;
        float maxBarHeight = Math.max(barsBottom - 2 * density, 12 * density);

        float slot = w / BAR_COUNT;
        float barWidth = slot * 0.55f;

        // no live data for 1.5 seconds while playing: use the old random bounce instead
        boolean noData = playing && SystemClock.uptimeMillis() - lastDataTime > 1500;

        // bars
        for (int i = 0; i < BAR_COUNT; i++) {
            float wobble = 0.5f + 0.5f * (float) Math.sin(time * (4.5f + (i % 5) * 0.9f) + i * 1.3f);
            float restH = heights[i] * maxBarHeight * 0.18f;          // paused: low bars (keeps a little of the song's shape)
            float fallbackH = restH * (1f - 0.55f * (1f - wobble));   // old random bounce
            float liveH = levels[i] * maxBarHeight;                   // the real sound
            float playH = noData ? fallbackH : liveH;
            float barH = Math.max(restH + (playH - restH) * bounce, 6 * density);
            float cx = slot * (i + 0.5f);
            rect.set(cx - barWidth / 2, barsBottom - barH, cx + barWidth / 2, barsBottom);
            boolean played = (i + 0.5f) / BAR_COUNT <= shownProgress;
            canvas.drawRoundRect(rect, barWidth / 2, barWidth / 2, played ? barPlayed : barIdle);
        }

        // thin track with the round handle
        rect.set(0, trackY - trackH / 2, w, trackY + trackH / 2);
        canvas.drawRoundRect(rect, trackH / 2, trackH / 2, trackIdle);

        float px = Math.max(thumbR, Math.min(w - thumbR, shownProgress * w));
        rect.set(0, trackY - trackH / 2, px, trackY + trackH / 2);
        canvas.drawRoundRect(rect, trackH / 2, trackH / 2, trackPlayed);

        canvas.drawCircle(px, trackY, thumbR, thumb);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float fraction = Math.max(0f, Math.min(1f, event.getX() / Math.max(1, getWidth())));
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                touching = true;
                targetProgress = fraction;
                shownProgress = fraction;
                invalidate();
                if (listener != null) listener.onScrub(fraction);
                return true;
            case MotionEvent.ACTION_UP:
                touching = false;
                targetProgress = fraction;
                shownProgress = fraction;
                invalidate();
                startAnimating();
                if (listener != null) listener.onSeekEnd(fraction);
                return true;
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                startAnimating();
                if (listener != null) listener.onSeekEnd(targetProgress);
                return true;
        }
        return super.onTouchEvent(event);
    }
}