package com.appdev.mvpplay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

// a cover card that floats in 3D. It sways gently while music plays,
// and you can drag over it to tilt it. A quick tap still counts as a click.
public class TiltCardView extends View {

    private static final float MAX_TILT = 24f;   // degrees when you drag to the edge
    // if the card tilts the wrong way when you drag, change one of these to -1f
    private static final float SIGN_X = 1f;
    private static final float SIGN_Y = 1f;

    private static final float[] SHADOW_GROW = {0.06f, 0.04f, 0.02f, 0f};
    private static final int[] SHADOW_ALPHA = {0x0C, 0x14, 0x1E, 0x28};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();
    private final Matrix camMatrix = new Matrix();
    private final Camera camera = new Camera();

    private Bitmap cover;
    private BitmapShader coverShader;
    private LinearGradient glareShader;
    private int fallbackColor = 0xFF7C5CE6;
    private int accent = 0xFFA78BFA;

    private float rotX = 0f, rotY = 0f;   // current tilt in degrees
    private float clock = 0f;             // drives the gentle sway
    private float live = 0f;              // 0 = paused (card rests flat), 1 = playing (card floats)
    private boolean playing = false;
    private boolean framesRunning = false;
    private long lastFrame = 0L;

    private boolean touching = false;
    private float touchX, touchY, downX, downY;
    private long downTime;
    private int slop;

    public TiltCardView(Context context) {
        super(context);
        init(context);
    }

    public TiltCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setCover(Bitmap bitmap, int fallback) {
        cover = bitmap;
        fallbackColor = fallback;
        coverShader = (bitmap != null)
                ? new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                : null;
        invalidate();
    }

    public void setAccent(int color) {
        accent = color;
        invalidate();
    }

    public void setPlaying(boolean play) {
        if (play == playing) return;
        playing = play;
        startFrames();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float s = Math.min(w, h) * 0.80f;
        // the camera sits 3 card-sizes away: a soft, natural 3D look
        camera.setLocation(0f, 0f, -(s * 3f) / 72f);
        // the light band of the glare
        float gw = s * 0.55f;
        glareShader = new LinearGradient(-gw / 2f, 0f, gw / 2f, 0f,
                new int[]{0x00FFFFFF, 0x59FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        glarePaint.setShader(glareShader);
    }

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            float dt = (lastFrame == 0L) ? 0f : Math.min(now - lastFrame, 64);
            lastFrame = now;
            step(dt);
            invalidate();
            boolean moving = Math.abs(rotX) > 0.05f || Math.abs(rotY) > 0.05f;
            if (playing || touching || live > 0.01f || moving) {
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

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    private void step(float dt) {
        live += ((playing ? 1f : 0f) - live) * Math.min(1f, dt / 300f);
        if (live > 0.01f) clock += dt;

        float targetX, targetY;
        if (touching) {
            float half = Math.max(1f, Math.min(getWidth(), getHeight()) * 0.80f / 2f);
            float nx = clamp((touchX - getWidth() / 2f) / half);
            float ny = clamp((touchY - getHeight() / 2f) / half);
            targetY = nx * MAX_TILT * SIGN_Y;
            targetX = -ny * MAX_TILT * SIGN_X;
        } else {
            targetY = (float) Math.sin(clock / 1400f) * 9f * live;
            targetX = (float) Math.cos(clock / 1900f) * 6f * live;
        }
        float k = Math.min(1f, dt / (touching ? 70f : 220f));
        rotX += (targetX - rotX) * k;
        rotY += (targetY - rotY) * k;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = e.getX();
                downY = e.getY();
                downTime = e.getEventTime();
                touchX = downX;
                touchY = downY;
                touching = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                startFrames();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                touchX = e.getX();
                touchY = e.getY();
                startFrames();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                touching = false;
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                // a quick tap (no drag) switches to the next style
                if (dx * dx + dy * dy < slop * slop && e.getEventTime() - downTime < 300) {
                    performClick();
                }
                startFrames();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                touching = false;
                startFrames();
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (playing) startFrames();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        framesRunning = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0 || glareShader == null) return;
        float d = getResources().getDisplayMetrics().density;
        float s = Math.min(w, h) * 0.80f;
        float half = s / 2f;
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = s * 0.07f;
        float bob = (float) Math.sin(clock / 1100f) * s * 0.012f * live;
        float lean = rotY / MAX_TILT;   // -1..1
        float lift = rotX / MAX_TILT;   // -1..1

        // soft shadow on the "ground" (it moves a little when the card tilts)
        float sx = cx - lean * s * 0.05f;
        float sy = cy + s * 0.05f + lift * s * 0.04f;
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < SHADOW_GROW.length; i++) {
            float g = s * SHADOW_GROW[i];
            rect.set(sx - half - g, sy - half - g, sx + half + g, sy + half + g);
            paint.setColor(SHADOW_ALPHA[i] << 24);
            canvas.drawRoundRect(rect, radius + g, radius + g, paint);
        }

        // the card, turned in 3D around its center
        camera.save();
        camera.rotateX(rotX);
        camera.rotateY(rotY);
        camera.getMatrix(camMatrix);
        camera.restore();
        camMatrix.preTranslate(-cx, -cy);
        camMatrix.postTranslate(cx, cy + bob);
        canvas.save();
        canvas.concat(camMatrix);

        rect.set(cx - half, cy - half, cx + half, cy + half);
        if (coverShader != null) {
            float cw = cover.getWidth();
            float ch = cover.getHeight();
            float scale = Math.max(s / cw, s / ch);
            matrix.reset();
            matrix.setScale(scale, scale);
            matrix.postTranslate(cx - cw * scale / 2f, cy - ch * scale / 2f);
            coverShader.setLocalMatrix(matrix);
            coverPaint.setShader(coverShader);
        } else {
            coverPaint.setShader(null);
            coverPaint.setColor(fallbackColor);
        }
        canvas.drawRoundRect(rect, radius, radius, coverPaint);

        // the glare slides across the card as it tilts
        matrix.reset();
        matrix.setRotate(20f);
        matrix.postTranslate(cx + lean * half * 1.2f, cy);
        glareShader.setLocalMatrix(matrix);
        canvas.drawRoundRect(rect, radius, radius, glarePaint);

        // thin light border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * d);
        paint.setColor(0x66FFFFFF);
        canvas.drawRoundRect(rect, radius, radius, paint);
        canvas.restore();
    }
}