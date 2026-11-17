package com.appdev.mvpplay;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Random;

// falling snow + a twinkling lights garland, drawn on top of any screen
// while the Christmas theme is on. Touches pass straight through it.
public class ChristmasOverlay extends View {

    private static final String TAG = "christmas_overlay";
    private static final int[] BULB_COLORS = {0xFFE5384B, 0xFF2ECC71, 0xFFFFD54F, 0xFF4FC3F7};

    // adds the overlay when the theme is Christmas, removes it for any other theme
    public static void sync(Activity activity) {
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        View existing = root.findViewWithTag(TAG);
        boolean on = ThemeStore.isChristmas(activity);
        if (on && existing == null) {
            ChristmasOverlay overlay = new ChristmasOverlay(activity);
            overlay.setTag(TAG);
            root.addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (!on && existing != null) {
            root.removeView(existing);
        }
    }

    private final float d;
    private final Random random = new Random();
    private final Paint snowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wirePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bulbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wire = new Path();
    private final int[] loc = new int[2];

    private final int flakes;
    private float[] sx, sy, sr, sv, sp;       // snow: x, y, radius, speed, sway
    private float[] bx, by;                   // bulbs: positions
    private int[] bc;                         // bulbs: colors
    private long last = 0;
    private float time = 0f;

    private ChristmasOverlay(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;

        // fewer snowflakes on low-memory phones
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        boolean low = am != null && am.isLowRamDevice();
        flakes = low ? 16 : 34;

        snowPaint.setColor(Color.WHITE);
        wirePaint.setStyle(Paint.Style.STROKE);
        wirePaint.setStrokeWidth(1.5f * d);
        wirePaint.setColor(0xFF2E7D4F);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(this::rebuildGarland);   // insets are known after the first layout
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        sx = new float[flakes];
        sy = new float[flakes];
        sr = new float[flakes];
        sv = new float[flakes];
        sp = new float[flakes];
        for (int i = 0; i < flakes; i++) {
            sx[i] = random.nextFloat() * w;
            sy[i] = random.nextFloat() * h;
            sr[i] = (1.2f + random.nextFloat() * 2f) * d;
            sv[i] = (24 + random.nextFloat() * 40) * d;
            sp[i] = 0.6f + random.nextFloat();
        }
        rebuildGarland();
    }

    // the wire hangs in a few soft swags just under the status bar
    private void rebuildGarland() {
        int w = getWidth();
        if (w == 0) return;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
        int status = insets == null ? 0
                : insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
        getLocationInWindow(loc);
        float top = Math.max(0, status - loc[1]) + 2 * d;

        int swags = Math.max(3, Math.round(w / (110 * d)));
        float segW = w / (float) swags;
        float sag = 14 * d;
        int per = 3;                                  // bulbs per swag
        bx = new float[swags * per];
        by = new float[swags * per];
        bc = new int[swags * per];

        wire.reset();
        wire.moveTo(0, top);
        int n = 0;
        for (int i = 0; i < swags; i++) {
            float x0 = i * segW, x1 = x0 + segW;
            float xc = (x0 + x1) / 2f, yc = top + 2 * sag;
            wire.quadTo(xc, yc, x1, top);
            for (int k = 1; k <= per; k++) {
                float t = k / (per + 1f), u = 1 - t;
                bx[n] = u * u * x0 + 2 * u * t * xc + t * t * x1;
                by[n] = u * u * top + 2 * u * t * yc + t * t * top;
                bc[n] = BULB_COLORS[n % BULB_COLORS.length];
                n++;
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0.016f : Math.min(50, now - last) / 1000f;
        last = now;
        time += dt;

        // lights garland
        if (bx != null) {
            canvas.drawPath(wire, wirePaint);
            for (int i = 0; i < bx.length; i++) {
                float tw = 0.55f + 0.45f * (float) Math.sin(time * 2.2f + i * 1.7f);
                float y = by[i] + 5 * d;
                canvas.drawLine(bx[i], by[i], bx[i], y - 3 * d, wirePaint);
                glowPaint.setColor(bc[i]);
                glowPaint.setAlpha((int) (70 * tw));
                canvas.drawCircle(bx[i], y, 8 * d, glowPaint);
                bulbPaint.setColor(bc[i]);
                bulbPaint.setAlpha((int) (255 * tw));
                canvas.drawCircle(bx[i], y, 3.5f * d, bulbPaint);
            }
        }

        // snow
        if (sx != null) {
            float w = getWidth(), h = getHeight();
            for (int i = 0; i < flakes; i++) {
                sy[i] += sv[i] * dt;
                sx[i] += (float) Math.sin(time * sp[i] + i) * 12 * d * dt;
                if (sy[i] > h + sr[i]) {
                    sy[i] = -sr[i];
                    sx[i] = random.nextFloat() * w;
                }
                snowPaint.setAlpha(110 + (int) (100 * Math.min(1f, sr[i] / (3.2f * d))));
                canvas.drawCircle(sx[i], sy[i], sr[i], snowPaint);
            }
        }
        postInvalidateOnAnimation();
    }
}