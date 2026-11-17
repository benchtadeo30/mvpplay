package com.appdev.mvpplay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import java.util.Random;

// the Halloween scenery: drifting fog, a flickering candle glow and cobwebs in the corners
public final class HalloweenDecor {

    private HalloweenDecor() {}

    // adds fog + glow and the cobwebs to the Halloween layer (called from HalloweenFx)
    public static void addTo(FrameLayout layer) {
        Context context = layer.getContext();
        layer.addView(new AmbientView(context), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        layer.addView(new CobwebView(context), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // fog drifting along the bottom + an orange candle glow that flickers in the bottom-left corner
    private static class AmbientView extends View {
        private final Paint fogPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();
        private ValueAnimator animator;
        private float phase = 0f;          // 0..1, moves the fog
        private float flicker = 1f;        // 0.55..1, brightness of the glow
        private float flickerTarget = 1f;
        private long lastFlickerPick = 0L;
        private float glowRadius = 0f;

        AmbientView(Context context) {
            super(context);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            glowRadius = Math.max(w, h) * 0.55f;
            // orange glow, strongest in the bottom-left corner
            glowPaint.setShader(new RadialGradient(0f, h, glowRadius,
                    new int[]{0x55FF7A00, 0x00FF7A00}, null, Shader.TileMode.CLAMP));
            // one soft blob of fog (a circle of radius 1 that is stretched when drawn)
            fogPaint.setShader(new RadialGradient(0f, 0f, 1f,
                    new int[]{0x55D6C6FF, 0x00D6C6FF}, null, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            // candle glow
            glowPaint.setAlpha((int) (255 * flicker));
            canvas.drawCircle(0f, h, glowRadius, glowPaint);

            // three fog blobs drifting left to right at different heights
            float rx = w * 0.6f;
            float ry = h * 0.09f;
            for (int i = 0; i < 3; i++) {
                float p = (phase + i / 3f) % 1f;
                float x = -rx + p * (w + 2 * rx);
                float y = h * (0.80f + 0.07f * i);
                canvas.save();
                canvas.translate(x, y);
                canvas.scale(rx, ry);
                canvas.drawCircle(0f, 0f, 1f, fogPaint);
                canvas.restore();
            }
        }

        private void begin() {
            if (animator != null || getWindowVisibility() != VISIBLE) return;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(45000);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                long now = System.currentTimeMillis();
                if (now - lastFlickerPick > 90) {
                    lastFlickerPick = now;
                    flickerTarget = 0.55f + random.nextFloat() * 0.45f;
                }
                flicker += (flickerTarget - flicker) * 0.25f;
                invalidate();
            });
            animator.start();
        }

        // stops everything when the screen is not visible (saves battery)
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
            if (visibility == VISIBLE) begin();
            else end();
        }
    }

    // a cobweb in the top-left and top-right corner of the screen
    private static class CobwebView extends View {
        private static final int STRANDS = 5;   // threads going out from the corner
        private static final int RINGS = 4;     // curved threads joining them
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float size;

        CobwebView(Context context) {
            super(context);
            float density = context.getResources().getDisplayMetrics().density;
            size = 90 * density;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(density);
            paint.setColor(0x66FFFFFF);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawWeb(canvas, false);   // top-left
            drawWeb(canvas, true);    // top-right (mirrored)
        }

        private void drawWeb(Canvas canvas, boolean right) {
            canvas.save();
            if (right) {
                canvas.translate(getWidth(), 0f);
                canvas.scale(-1f, 1f);
            }
            for (int i = 0; i < STRANDS; i++) {
                double angle = Math.toRadians(90.0 * i / (STRANDS - 1));
                canvas.drawLine(0f, 0f,
                        (float) (Math.cos(angle) * size), (float) (Math.sin(angle) * size), paint);
            }
            for (int ring = 1; ring <= RINGS; ring++) {
                float r = size * ring / RINGS;
                path.reset();
                for (int i = 0; i < STRANDS; i++) {
                    double angle = Math.toRadians(90.0 * i / (STRANDS - 1));
                    float x = (float) (Math.cos(angle) * r);
                    float y = (float) (Math.sin(angle) * r);
                    if (i == 0) {
                        path.moveTo(x, y);
                    } else {
                        // thread sags a little toward the corner
                        double mid = Math.toRadians(90.0 * (i - 0.5) / (STRANDS - 1));
                        path.quadTo((float) (Math.cos(mid) * r * 0.8),
                                (float) (Math.sin(mid) * r * 0.8), x, y);
                    }
                }
                canvas.drawPath(path, paint);
            }
            canvas.restore();
        }
    }
}