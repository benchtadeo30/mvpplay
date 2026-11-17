package com.appdev.mvpplay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

// a turntable: a black record with the song picture as its label and a needle arm.
// play: the arm swings onto the record and the record spins up. pause: the arm lifts and it winds down.
public class VinylView extends View {

    private static final float SPIN_MS = 3000f;    // one full turn at full speed
    private static final float ARM_PARKED = -8f;   // arm angle (degrees) when the needle is off the record
    private static final float ARM_START = 3f;     // needle on the outer groove (start of the song)
    private static final float ARM_END = 17f;      // needle near the label (end of the song)

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Matrix matrix = new Matrix();

    private Bitmap label;
    private BitmapShader labelShader;
    private int fallbackColor = 0xFF7C5CE6;
    private int accent = 0xFFA78BFA;

    private float angle = 0f;          // how far the record has turned
    private float speed = 0f;          // 0 = stopped, 1 = full speed
    private float armDown = 0f;        // 0 = arm parked, 1 = needle on the record
    private float playProgress = 0f;   // 0..1 through the song (moves the needle inward)
    private boolean spinning = false;
    private long lastFrame = 0L;
    private boolean framesRunning = false;
    private ValueAnimator speedAnim, armAnim;

    public VinylView(Context context) {
        super(context);
    }

    public VinylView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    // the picture in the middle (if there is none, a plain color is used)
    public void setLabel(Bitmap bitmap, int fallback) {
        label = bitmap;
        fallbackColor = fallback;
        labelShader = (bitmap != null)
                ? new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                : null;
        invalidate();
    }

    // the theme's accent color (label ring, needle tip, faint marks on the record)
    public void setAccent(int color) {
        accent = color;
        invalidate();
    }

    // how far the song is (0..1): the needle slowly moves toward the center
    public void setPlayProgress(float fraction) {
        playProgress = Math.max(0f, Math.min(1f, fraction));
        invalidate();
    }

    // true: arm drops and the record spins up. false: arm lifts and the record winds down.
    public void setSpinning(boolean play) {
        if (play == spinning) return;
        spinning = play;

        if (speedAnim != null) speedAnim.cancel();
        speedAnim = ValueAnimator.ofFloat(speed, play ? 1f : 0f);
        speedAnim.setDuration(play ? 1100 : 1800);
        speedAnim.setStartDelay(play ? 250 : 0);
        speedAnim.setInterpolator(new DecelerateInterpolator());
        speedAnim.addUpdateListener(a -> speed = (float) a.getAnimatedValue());
        speedAnim.start();

        if (armAnim != null) armAnim.cancel();
        armAnim = ValueAnimator.ofFloat(armDown, play ? 1f : 0f);
        armAnim.setDuration(play ? 750 : 500);
        armAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        armAnim.addUpdateListener(a -> {
            armDown = (float) a.getAnimatedValue();
            invalidate();
        });
        armAnim.start();

        startFrames();
    }

    // one frame at a time, only while the record is turning (saves battery)
    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            if (lastFrame != 0) {
                float dt = Math.min(now - lastFrame, 64);
                angle = (angle + dt * 360f / SPIN_MS * speed) % 360f;
            }
            lastFrame = now;
            invalidate();
            if (spinning || speed > 0.002f) {
                postOnAnimation(this);
            } else {
                framesRunning = false;
                lastFrame = 0L;
            }
        }
    };

    private void startFrames() {
        if (framesRunning) return;
        framesRunning = true;
        lastFrame = 0L;
        postOnAnimation(frame);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (spinning) startFrames();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        framesRunning = false;
        if (speedAnim != null) speedAnim.cancel();
        if (armAnim != null) armAnim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // the light reflection: two soft bright wedges on opposite sides
        float[] positions = {0f, 0.06f, 0.14f, 0.22f, 0.5f, 0.56f, 0.64f, 0.72f, 1f};
        int[] colors = {0x00FFFFFF, 0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF,
                0x00FFFFFF, 0x00FFFFFF, 0x26FFFFFF, 0x00FFFFFF, 0x00FFFFFF};
        sheenPaint.setShader(new SweepGradient(w / 2f, h / 2f, colors, positions));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight());
        if (s <= 0) return;
        float d = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float left = cx - s / 2f;
        float top = cy - s / 2f;
        float r = s * 0.47f;
        float labelR = r * 0.52f;

        // the platter under the record
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0E0E12);
        canvas.drawCircle(cx, cy, r + s * 0.02f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * d);
        paint.setColor(0xFF2A2A33);
        canvas.drawCircle(cx, cy, r + s * 0.02f, paint);

        // the record and its grooves turn
        canvas.save();
        canvas.rotate(angle, cx, cy);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF16161B);
        canvas.drawCircle(cx, cy, r, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, d * 0.6f));
        for (int i = 0; i < 14; i++) {
            paint.setColor(i % 2 == 0 ? 0xFF2C2C36 : 0xFF222229);
            canvas.drawCircle(cx, cy, r * (0.58f + 0.027f * i), paint);
        }
        // the gaps between the tracks
        paint.setStrokeWidth(1.6f * d);
        paint.setColor(0xFF0B0B0E);
        canvas.drawCircle(cx, cy, r * 0.70f, paint);
        canvas.drawCircle(cx, cy, r * 0.83f, paint);
        // shiny edge
        paint.setStrokeWidth(1.5f * d);
        paint.setColor(0xFF33333D);
        canvas.drawCircle(cx, cy, r - 0.75f * d, paint);

        // faint marks so you can see the record turning
        paint.setStrokeWidth(2f * d);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor((accent & 0x00FFFFFF) | 0x55000000);
        oval.set(cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f);
        canvas.drawArc(oval, 30, 40, false, paint);
        canvas.drawArc(oval, 210, 28, false, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        canvas.restore();

        // the glossy reflection stays still while the record turns
        canvas.drawCircle(cx, cy, r, sheenPaint);

        // the label (song picture) turns with the record
        canvas.save();
        canvas.rotate(angle, cx, cy);
        if (labelShader != null) {
            float bw = label.getWidth();
            float bh = label.getHeight();
            float scale = Math.max(2 * labelR / bw, 2 * labelR / bh);
            matrix.reset();
            matrix.setScale(scale, scale);
            matrix.postTranslate(cx - bw * scale / 2f, cy - bh * scale / 2f);
            labelShader.setLocalMatrix(matrix);
            labelPaint.setShader(labelShader);
        } else {
            labelPaint.setShader(null);
            labelPaint.setColor(fallbackColor);
        }
        canvas.drawCircle(cx, cy, labelR, labelPaint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * d);
        paint.setColor(0xCC000000);
        canvas.drawCircle(cx, cy, labelR, paint);
        paint.setStrokeWidth(1.5f * d);
        paint.setColor(accent);
        canvas.drawCircle(cx, cy, labelR + 3 * d, paint);

        // the metal spindle in the middle
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFCFCFD8);
        canvas.drawCircle(cx, cy, r * 0.045f, paint);
        paint.setColor(0xFF0B0B0E);
        canvas.drawCircle(cx, cy, r * 0.02f, paint);
        canvas.restore();

        // the needle arm (top-right corner)
        float px = left + s * 0.90f;
        float py = top + s * 0.10f;
        float target = ARM_START + (ARM_END - ARM_START) * playProgress;
        float armAngle = ARM_PARKED + (target - ARM_PARKED) * armDown;

        canvas.save();
        canvas.translate(s * 0.012f, s * 0.016f);   // soft shadow
        drawArm(canvas, px, py, armAngle, s, true);
        canvas.restore();
        drawArm(canvas, px, py, armAngle, s, false);
    }

    // the arm is drawn pointing down from its pivot, then turned by the angle
    private void drawArm(Canvas canvas, float px, float py, float deg, float s, boolean shadow) {
        float arm = s * 0.52f;
        canvas.save();
        canvas.rotate(deg, px, py);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);

        // counterweight behind the pivot
        paint.setStrokeWidth(s * 0.04f);
        paint.setColor(shadow ? 0x33000000 : 0xFF2B2B35);
        canvas.drawLine(px, py - s * 0.075f, px, py - s * 0.02f, paint);

        // the long tube
        paint.setStrokeWidth(s * 0.017f);
        paint.setColor(shadow ? 0x33000000 : 0xFFC4C4CE);
        canvas.drawLine(px, py, px, py + arm, paint);

        // the head with the needle
        paint.setStrokeWidth(s * 0.036f);
        paint.setColor(shadow ? 0x33000000 : 0xFFE6E6EE);
        canvas.drawLine(px, py + arm, px, py + arm + s * 0.03f, paint);

        if (!shadow) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(accent);
            canvas.drawCircle(px, py + arm + s * 0.052f, s * 0.009f, paint);

            // the round base the arm turns on
            paint.setColor(0xFF3A3A46);
            canvas.drawCircle(px, py, s * 0.05f, paint);
            paint.setColor(0xFF6A6A78);
            canvas.drawCircle(px, py, s * 0.028f, paint);
            paint.setColor(0xFF2B2B35);
            canvas.drawCircle(px, py, s * 0.012f, paint);
        }
        canvas.restore();
        paint.setStrokeCap(Paint.Cap.BUTT);
    }
}