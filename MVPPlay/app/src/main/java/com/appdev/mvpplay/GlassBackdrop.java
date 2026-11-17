package com.appdev.mvpplay;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

// the dark gradient and slowly moving glow orbs behind the Glass theme.
// It sits BEHIND the screen, so the glass cards show the glow through them.
public class GlassBackdrop extends View {

    private static final String TAG = "glass_backdrop";

    // adds the backdrop for the Glass theme, removes it for any other theme
    public static void sync(Activity activity) {
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        View existing = root.findViewWithTag(TAG);
        boolean on = ThemeStore.isGlass(activity);
        if (on && existing == null) {
            GlassBackdrop backdrop = new GlassBackdrop(activity);
            backdrop.setTag(TAG);
            root.addView(backdrop, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (!on && existing != null) {
            root.removeView(existing);
        }
    }

    private static final int[] COLORS = {0xFF7C5CFF, 0xFF22D3EE, 0xFFF472B6};
    private static final float[] SIZE = {0.85f, 0.75f, 0.6f};      // orb size vs. screen width
    private static final float[] SPEED_X = {0.11f, 0.08f, 0.14f};
    private static final float[] SPEED_Y = {0.09f, 0.13f, 0.07f};
    private static final float[] PHASE = {0f, 2.1f, 4.2f};

    private final Paint basePaint = new Paint();
    private final Paint[] orbPaint = new Paint[3];
    private final float[] orbRadius = new float[3];
    private final boolean animate;
    private long last = 0;
    private float time = 0f;

    private GlassBackdrop(Context c) {
        super(c);
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        animate = !(am != null && am.isLowRamDevice());   // low-memory phones get a still picture
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        basePaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF0A0720, 0xFF120A2E, 0xFF081426},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        for (int i = 0; i < 3; i++) {
            float r = w * SIZE[i];
            orbRadius[i] = r;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            // the gradient is built around (0,0) once, and the canvas moves it each frame
            p.setShader(new RadialGradient(0, 0, r,
                    new int[]{(COLORS[i] & 0x00FFFFFF) | 0x99000000,
                            (COLORS[i] & 0x00FFFFFF) | 0x33000000,
                            COLORS[i] & 0x00FFFFFF},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            orbPaint[i] = p;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || orbPaint[0] == null) return;

        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0.016f : Math.min(50, now - last) / 1000f;
        last = now;
        if (animate) time += dt;

        canvas.drawRect(0, 0, w, h, basePaint);
        for (int i = 0; i < 3; i++) {
            float x = w * (0.5f + 0.4f * (float) Math.sin(time * SPEED_X[i] * 6.28f + PHASE[i]));
            float y = h * (0.5f + 0.4f * (float) Math.cos(time * SPEED_Y[i] * 6.28f + PHASE[i] * 0.7f));
            canvas.save();
            canvas.translate(x, y);
            canvas.drawCircle(0, 0, orbRadius[i], orbPaint[i]);
            canvas.restore();
        }
        if (animate) postInvalidateOnAnimation();
    }
}