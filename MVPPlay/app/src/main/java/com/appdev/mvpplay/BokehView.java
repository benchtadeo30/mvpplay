package com.appdev.mvpplay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

// the "memories" background: blurred photo, drifting light, floating orbs and sparkles
public class BokehView extends View {

    private static final int ORBS = 16;
    private static final int STARS = 28;

    private final float[] ox = new float[ORBS], or = new float[ORBS], op = new float[ORBS];
    private final int[] oLoops = new int[ORBS], oAlpha = new int[ORBS];
    private final Shader[] orbShader = new Shader[ORBS];

    private final float[] sx = new float[STARS], sy = new float[STARS],
            ss = new float[STARS], sp = new float[STARS];
    private final int[] sSpeed = new int[STARS];

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint photoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path star = new Path();
    private final Rect src = new Rect(), dst = new Rect();

    private Bitmap photo;
    private Shader auroraA, auroraB, vignette;
    private ValueAnimator animator;
    private float t = 0f;

    public BokehView(Context context) {
        super(context);
        init();
    }

    public BokehView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // a 4-point sparkle, size 1
        star.moveTo(0, -1f);
        star.quadTo(0, 0, 1f, 0);
        star.quadTo(0, 0, 0, 1f);
        star.quadTo(0, 0, -1f, 0);
        star.quadTo(0, 0, 0, -1f);
        star.close();
    }

    // a very small copy of the picture; stretched over the screen it looks blurred
    public void setPhoto(Bitmap tiny) {
        photo = tiny;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float d = getResources().getDisplayMetrics().density;
        Random random = new Random(11);

        for (int i = 0; i < ORBS; i++) {
            ox[i] = random.nextFloat() * w;
            or[i] = (10 + random.nextFloat() * 26) * d;
            op[i] = random.nextFloat();
            oLoops[i] = 1 + random.nextInt(3);
            oAlpha[i] = 90 + random.nextInt(110);
            orbShader[i] = new RadialGradient(0, 0, or[i],
                    new int[]{0xFFFFF0C2, 0x88FFC83D, 0x00FFC83D},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        }
        for (int i = 0; i < STARS; i++) {
            sx[i] = random.nextFloat() * w;
            sy[i] = random.nextFloat() * h;
            ss[i] = (2 + random.nextFloat() * 5) * d;
            sp[i] = random.nextFloat();
            sSpeed[i] = 8 + random.nextInt(14);
        }

        auroraA = new RadialGradient(0, 0, w * 0.75f,
                0x55FFB347, 0x00FFB347, Shader.TileMode.CLAMP);
        auroraB = new RadialGradient(0, 0, w * 0.65f,
                0x44FF6F91, 0x00FF6F91, Shader.TileMode.CLAMP);
        vignette = new LinearGradient(0, 0, 0, h,
                new int[]{0xAA000000, 0x00000000, 0x00000000, 0xBB000000},
                new float[]{0f, 0.3f, 0.65f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(48000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0 || auroraA == null) return;
        float d = getResources().getDisplayMetrics().density;
        float twoPi = 6.2832f;

        // 1) the photo, cropped to fill the screen, soft and dim
        if (photo != null) {
            float screen = (float) w / h;
            float pic = (float) photo.getWidth() / photo.getHeight();
            if (pic > screen) {
                int cut = (int) (photo.getWidth() - photo.getHeight() * screen);
                src.set(cut / 2, 0, photo.getWidth() - cut / 2, photo.getHeight());
            } else {
                int cut = (int) (photo.getHeight() - photo.getWidth() / screen);
                src.set(0, cut / 2, photo.getWidth(), photo.getHeight() - cut / 2);
            }
            dst.set(0, 0, w, h);
            photoPaint.setAlpha(95);
            canvas.drawBitmap(photo, src, dst, photoPaint);
        }

        // 2) two big drifting lights, one gold and one rose
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        float ax = w * (0.5f + 0.35f * (float) Math.sin(t * twoPi));
        float ay = h * (0.28f + 0.10f * (float) Math.cos(t * twoPi));
        canvas.save();
        canvas.translate(ax, ay);
        paint.setShader(auroraA);
        canvas.drawCircle(0, 0, w * 0.75f, paint);
        canvas.restore();

        float bx = w * (0.5f - 0.35f * (float) Math.cos(t * twoPi));
        float by = h * (0.72f + 0.10f * (float) Math.sin(t * twoPi));
        canvas.save();
        canvas.translate(bx, by);
        paint.setShader(auroraB);
        canvas.drawCircle(0, 0, w * 0.65f, paint);
        canvas.restore();

        // 3) dark fade at the top and bottom so text stays readable
        paint.setShader(vignette);
        canvas.drawRect(0, 0, w, h, paint);

        // 4) glowing orbs floating up
        for (int i = 0; i < ORBS; i++) {
            float progress = (op[i] + t * oLoops[i]) % 1f;
            float cy = h + or[i] - progress * (h + 2 * or[i]);
            float cx = ox[i] + (float) Math.sin((progress + op[i]) * twoPi) * 18 * d;
            float fade = (float) Math.sin(progress * Math.PI);
            canvas.save();
            canvas.translate(cx, cy);
            paint.setShader(orbShader[i]);
            paint.setAlpha((int) (oAlpha[i] * fade));
            canvas.drawCircle(0, 0, or[i], paint);
            canvas.restore();
        }

        // 4b) shooting stars: three of them, one after another
        paint.setShader(null);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFFFF3C9);
        for (int i = 0; i < 3; i++) {
            float p = (t * 3f + i / 3f) % 1f;
            if (p > 0.12f) continue;                 // it only shows for a short moment
            float k = p / 0.12f;
            float dx = -0.6f * w;
            float dy = 0.3f * h;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            float ux = dx / len;
            float uy = dy / len;
            float hx = w * (1f - 0.25f * i) + k * dx;
            float hy = h * (0.04f + 0.1f * i) + k * dy;
            float fade = (float) Math.sin(k * Math.PI);

            paint.setStyle(Paint.Style.STROKE);
            for (int s = 0; s < 6; s++) {            // the tail fades out
                paint.setStrokeWidth(d * 2.2f * (1f - s / 7f));
                paint.setAlpha((int) (235 * fade * (1f - s / 6f)));
                canvas.drawLine(hx - ux * s * 14 * d, hy - uy * s * 14 * d,
                        hx - ux * (s + 1) * 14 * d, hy - uy * (s + 1) * 14 * d, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);

        // 5) twinkling sparkles
        paint.setShader(null);
        paint.setColor(0xFFFFF3C9);
        for (int i = 0; i < STARS; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin((t * sSpeed[i] + sp[i]) * twoPi);
            tw = tw * tw;
            paint.setAlpha((int) (230 * tw));
            canvas.save();
            canvas.translate(sx[i], sy[i]);
            canvas.scale(ss[i] * (0.6f + 0.6f * tw), ss[i] * (0.6f + 0.6f * tw));
            canvas.drawPath(star, paint);
            canvas.restore();
        }
    }
}