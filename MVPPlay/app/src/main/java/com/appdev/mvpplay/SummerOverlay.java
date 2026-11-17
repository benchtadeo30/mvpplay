package com.appdev.mvpplay;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.Random;

// summer decorations on top of any screen: sun glow, floating bubbles,
// drifting sparkles and soft waves along the bottom. Touches pass through.
public class SummerOverlay extends View {

    private static final String TAG = "summer_overlay";
    private static final int[] SPARK_COLORS = {0xFFFFB300, 0xFF4DD0E1};

    // adds the overlay for the Summer theme, removes it for any other theme
    public static void sync(Activity activity) {
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        View existing = root.findViewWithTag(TAG);
        boolean on = ThemeStore.isSummer(activity);
        if (on && existing == null) {
            SummerOverlay overlay = new SummerOverlay(activity);
            overlay.setTag(TAG);
            root.addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (!on && existing != null) {
            root.removeView(existing);
        }
    }

    private final float d;
    private final Random random = new Random();
    private final Paint sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wave = new Path();

    private final int bubbles, sparkles;
    private float[] bx, by, br, bv, bp;       // bubbles: x, y, radius, speed, sway
    private float[] sx, sy, ss, sv, sp;       // sparkles: x, y, size, speed, phase
    private float sunX, sunY, sunR;
    private long last = 0;
    private float time = 0f;

    private SummerOverlay(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;

        // fewer pieces on low-memory phones
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        boolean low = am != null && am.isLowRamDevice();
        bubbles = low ? 5 : 10;
        sparkles = low ? 6 : 12;

        wavePaint.setStyle(Paint.Style.FILL);
        fillPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(1f * d);
        sparkPaint.setStyle(Paint.Style.STROKE);
        sparkPaint.setStrokeWidth(1.2f * d);
        sparkPaint.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // sun glow in the top-right corner
        sunX = w - 10 * d;
        sunY = 10 * d;
        sunR = 170 * d;
        sunPaint.setShader(new RadialGradient(sunX, sunY, sunR,
                new int[]{0x66FFD54F, 0x26FFB300, 0x00FFB300},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));

        bx = new float[bubbles];
        by = new float[bubbles];
        br = new float[bubbles];
        bv = new float[bubbles];
        bp = new float[bubbles];
        for (int i = 0; i < bubbles; i++) {
            bx[i] = random.nextFloat() * w;
            by[i] = random.nextFloat() * h;
            br[i] = (3 + random.nextFloat() * 6) * d;
            bv[i] = (18 + random.nextFloat() * 28) * d;
            bp[i] = 0.5f + random.nextFloat();
        }

        sx = new float[sparkles];
        sy = new float[sparkles];
        ss = new float[sparkles];
        sv = new float[sparkles];
        sp = new float[sparkles];
        for (int i = 0; i < sparkles; i++) {
            sx[i] = random.nextFloat() * w;
            sy[i] = random.nextFloat() * h;
            ss[i] = (2 + random.nextFloat() * 2.5f) * d;
            sv[i] = (6 + random.nextFloat() * 8) * d;
            sp[i] = random.nextFloat() * 6f;
        }
    }

    // one soft wave layer along the bottom edge
    private void drawWave(Canvas canvas, int w, int h, float baseY, float amp,
                          float wavelength, float phase, int color) {
        wave.reset();
        wave.moveTo(0, h);
        float step = 12 * d;
        for (float x = 0; x <= w + step; x += step) {
            wave.lineTo(x, baseY + amp * (float) Math.sin(x / wavelength * 6.2832f + phase));
        }
        wave.lineTo(w + step, h);
        wave.close();
        wavePaint.setColor(color);
        canvas.drawPath(wave, wavePaint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0.016f : Math.min(50, now - last) / 1000f;
        last = now;
        time += dt;
        int w = getWidth(), h = getHeight();
        if (bx == null || w == 0) {
            postInvalidateOnAnimation();
            return;
        }

        // sun glow, breathing slowly
        sunPaint.setAlpha((int) (200 + 55 * Math.sin(time * 0.8f)));
        canvas.drawCircle(sunX, sunY, sunR, sunPaint);

        // bubbles rise and sway
        for (int i = 0; i < bubbles; i++) {
            by[i] -= bv[i] * dt;
            bx[i] += (float) Math.sin(time * bp[i] + i) * 10 * d * dt;
            if (by[i] < -br[i]) {
                by[i] = h + br[i];
                bx[i] = random.nextFloat() * w;
            }
            fillPaint.setColor(0x2200ACC1);
            canvas.drawCircle(bx[i], by[i], br[i], fillPaint);
            ringPaint.setColor(0x9900ACC1);
            canvas.drawCircle(bx[i], by[i], br[i], ringPaint);
            fillPaint.setColor(0xCCFFFFFF);
            canvas.drawCircle(bx[i] - br[i] * 0.35f, by[i] - br[i] * 0.35f, br[i] * 0.22f, fillPaint);
        }

        // sparkles drift sideways and twinkle
        for (int i = 0; i < sparkles; i++) {
            sx[i] += sv[i] * dt;
            if (sx[i] > w + 10 * d) {
                sx[i] = -10 * d;
                sy[i] = random.nextFloat() * h;
            }
            float t = 0.5f + 0.5f * (float) Math.sin(time * 2.4f + sp[i]);
            float s = ss[i] * (0.6f + 0.6f * t);
            sparkPaint.setColor(SPARK_COLORS[i % SPARK_COLORS.length]);
            sparkPaint.setAlpha((int) (235 * t));
            canvas.drawLine(sx[i] - s, sy[i], sx[i] + s, sy[i], sparkPaint);
            canvas.drawLine(sx[i], sy[i] - s, sx[i], sy[i] + s, sparkPaint);
        }

        // two soft waves along the bottom
        drawWave(canvas, w, h, h - 16 * d, 5 * d, 220 * d, time * 0.9f, 0x3800ACC1);
        drawWave(canvas, w, h, h - 10 * d, 4 * d, 160 * d, -time * 1.3f + 1f, 0x4A0097A7);

        postInvalidateOnAnimation();
    }
}