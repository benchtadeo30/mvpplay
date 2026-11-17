package com.appdev.mvpplay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

// a shiny band that sweeps across, plus a few twinkling sparkles.
// Lay it over a gold card. Touches pass through.
public class ShineView extends View {

    private static final long CYCLE_MS = 3400;   // one sweep + rest

    private static final float[] SP_X = {0.18f, 0.52f, 0.82f};
    private static final float[] SP_Y = {0.28f, 0.72f, 0.30f};
    private static final float[] SP_PHASE = {0f, 0.33f, 0.66f};

    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final float density;

    private LinearGradient band;
    private float bandW;
    private float corner = 0f;
    private float t = 0f;
    private ValueAnimator animator;

    public ShineView(Context context) {
        this(context, null);
    }

    public ShineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        sparkPaint.setStyle(Paint.Style.STROKE);
        sparkPaint.setStrokeWidth(1.5f * density);
        sparkPaint.setStrokeCap(Paint.Cap.ROUND);
        sparkPaint.setColor(0xFFFFF8DC);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    // rounded corners of the card under it, in pixels
    public void setCorner(float px) {
        corner = px;
        rebuildClip();
        invalidate();
    }

    private void rebuildClip() {
        rect.set(0, 0, getWidth(), getHeight());
        clip.reset();
        clip.addRoundRect(rect, corner, corner, Path.Direction.CW);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bandW = w * 0.30f;
        band = new LinearGradient(0, 0, bandW, 0,
                new int[]{0x00FFFFFF, 0xB3FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        bandPaint.setShader(band);
        rebuildClip();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (band == null || w <= 0 || h <= 0) return;

        canvas.save();
        canvas.clipPath(clip);

        // the band sweeps during the first 55% of each cycle, then rests
        float p = Math.min(1f, t / 0.55f);
        if (p < 1f) {
            float start = -bandW - h;
            float x = start + p * (w + bandW + 2 * h);
            matrix.setTranslate(x, 0f);
            band.setLocalMatrix(matrix);
            canvas.save();
            canvas.rotate(20f, w / 2f, h / 2f);
            canvas.drawRect(x, -h, x + bandW, 2 * h, bandPaint);
            canvas.restore();
        }

        // tiny sparkles that twinkle
        for (int i = 0; i < SP_X.length; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * (t * 2f + SP_PHASE[i]));
            float s = density * (2.5f + 3f * tw);
            float cx = w * SP_X[i];
            float cy = h * SP_Y[i];
            sparkPaint.setAlpha((int) (255 * tw));
            canvas.drawLine(cx - s, cy, cx + s, cy, sparkPaint);
            canvas.drawLine(cx, cy - s, cx, cy + s, sparkPaint);
        }
        canvas.restore();
    }

    // runs only while the view is on screen (saves battery)
    private void begin() {
        if (animator != null || getVisibility() != VISIBLE
                || getWindowVisibility() != VISIBLE) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(CYCLE_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void end() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        begin();
    }

    @Override
    protected void onDetachedFromWindow() {
        end();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) begin(); else end();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) begin(); else end();
    }
}