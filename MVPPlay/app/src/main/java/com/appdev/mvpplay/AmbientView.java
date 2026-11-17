package com.appdev.mvpplay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

// the "empty" style: nothing covers the background effect, only tiny touches on top:
// a small "NOW PLAYING" pill with moving bars, and a few faint dots drifting up.
// It is also the tap area, so tapping it still changes the view.
public class AmbientView extends View {

    private static final int MAX_DOTS = 18;
    private static final float SPAWN_MS = 260f;   // a new dot about every quarter second
    private static final int BARS = 4;

    private static class Dot {
        float x0, y0, rise, sway, phase, size, age, life;
    }

    private final ArrayList<Dot> dots = new ArrayList<>();
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int accent = 0xFFA78BFA;
    private boolean playing = false;
    private boolean framesRunning = false;
    private long lastFrame = 0L;
    private float spawnTimer = 0f;
    private float clock = 0f;   // drives the moving bars
    private float live = 0f;    // 0 = paused, 1 = playing (eases between the two)

    {
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setLetterSpacing(0.12f);
        textPaint.setColor(0xFFFFFFFF);
    }

    public AmbientView(Context context) {
        super(context);
    }

    public AmbientView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setAccent(int color) {
        accent = color;
        invalidate();
    }

    public void setPlaying(boolean play) {
        if (play == playing) return;
        playing = play;
        if (play) spawnTimer = SPAWN_MS;   // the first dot comes right away
        startFrames();
    }

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            float dt = (lastFrame == 0L) ? 0f : Math.min(now - lastFrame, 64);
            lastFrame = now;
            step(dt);
            invalidate();
            if (playing || !dots.isEmpty() || live > 0.01f) {
                postOnAnimation(this);
            } else {
                framesRunning = false;
                lastFrame = 0L;
                live = 0f;
            }
        }
    };

    private void startFrames() {
        if (framesRunning) return;
        framesRunning = true;
        lastFrame = 0L;
        postOnAnimation(frame);
    }

    private void step(float dt) {
        live += ((playing ? 1f : 0f) - live) * Math.min(1f, dt / 250f);
        if (playing) {
            clock += dt;
            spawnTimer += dt;
            if (spawnTimer >= SPAWN_MS && dots.size() < MAX_DOTS) {
                spawnTimer = 0f;
                spawn();
            }
        }
        for (int i = dots.size() - 1; i >= 0; i--) {
            Dot dot = dots.get(i);
            dot.age += dt;
            if (dot.age >= dot.life) dots.remove(i);
        }
    }

    private void spawn() {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float d = getResources().getDisplayMetrics().density;
        Dot dot = new Dot();
        dot.x0 = w * (0.05f + rnd.nextFloat() * 0.90f);
        dot.y0 = h * (0.50f + rnd.nextFloat() * 0.45f);   // lower half, away from the pill
        dot.rise = h * (0.15f + rnd.nextFloat() * 0.20f);
        dot.sway = w * (0.01f + rnd.nextFloat() * 0.02f);
        dot.phase = rnd.nextFloat() * 6.28f;
        dot.size = d * (1.2f + rnd.nextFloat() * 1.6f);
        dot.life = 4000 + rnd.nextInt(3000);
        dots.add(dot);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (playing || !dots.isEmpty()) startFrames();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        framesRunning = false;
        super.onDetachedFromWindow();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float d = getResources().getDisplayMetrics().density;

        // ----- floating dust -----
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < dots.size(); i++) {
            Dot dot = dots.get(i);
            float t = dot.age / dot.life;
            float x = dot.x0 + (float) Math.sin(dot.phase + t * 4f) * dot.sway;
            float y = dot.y0 - dot.rise * t;
            float a = t < 0.2f ? t / 0.2f : (t > 0.7f ? (1f - t) / 0.3f : 1f);
            int alpha = (int) (Math.max(0f, Math.min(1f, a)) * 140);
            paint.setColor((alpha << 24) | 0x00FFFFFF);
            canvas.drawCircle(x, y, dot.size, paint);
        }

        // ----- "NOW PLAYING" pill, top center -----
        String label = live > 0.5f ? "NOW PLAYING" : "PAUSED";
        textPaint.setTextSize(11 * d);
        float textW = textPaint.measureText(label);
        float barW = 3 * d;
        float barGap = 2 * d;
        float barsW = BARS * barW + (BARS - 1) * barGap;
        float padX = 14 * d;
        float pillW = padX + barsW + 8 * d + textW + padX;
        float pillH = 28 * d;
        if (pillW > w) return;

        float left = (w - pillW) / 2f;
        float top = 10 * d;
        rect.set(left, top, left + pillW, top + pillH);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xB30A0C18);
        canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * d);
        paint.setColor((accent & 0x00FFFFFF) | 0x66000000);
        canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, paint);

        // the tiny moving bars
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent);
        float baseY = top + pillH / 2f + 6 * d;
        float bx = left + padX;
        for (int i = 0; i < BARS; i++) {
            float wave = 0.5f + 0.5f * (float) Math.sin(clock / 170f + i * 1.4f);
            float bh = 3 * d + 9 * d * wave * live;
            rect.set(bx, baseY - bh, bx + barW, baseY);
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, paint);
            bx += barW + barGap;
        }

        // the text
        float textX = left + padX + barsW + 8 * d;
        float textY = top + pillH / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(label, textX, textY, textPaint);
    }
}