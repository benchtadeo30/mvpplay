package com.appdev.mvpplay;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Random;

// bats and a ghost that float across every screen in the Halloween theme
public class HalloweenFx {

    private static final String TAG = "halloween_fx";
    private static final Random random = new Random();

    // call when a screen starts: adds the layer in Halloween, removes it in any other theme
    public static void attach(Activity a) {
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null) return;

        View old = content.findViewWithTag(TAG);
        if (old != null) content.removeView(old);
        if (!ThemeStore.isHalloween(a)) return;
        if (a instanceof SplashActivity) return;

        final FrameLayout layer = new FrameLayout(a);
        layer.setTag(TAG);
        layer.setClickable(false);   // touches go straight through to the screen
        layer.setFocusable(false);
        content.addView(layer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // fog, candle glow and cobwebs go in first so the bats and ghost fly in front of them
        HalloweenDecor.addTo(layer);

        layer.post(() -> {
            launch(layer, "\uD83E\uDD87", 16, 14, 0.04f, 0.25f, 0.6f, 6000, 4000, true, 400);
            launch(layer, "\uD83E\uDD87", 14, 12, 0.08f, 0.30f, 0.5f, 7000, 4000, true, 3500);
            launch(layer, "\uD83D\uDC7B", 26, 8, 0.45f, 0.30f, 0.22f, 14000, 6000, false, 6000);
        });
    }

    // sends one flyer across the screen, then sends it again after a short rest
    private static void launch(final FrameLayout layer, final String emoji,
                               final int sizeMin, final int sizeRange,
                               final float yMin, final float yRange, final float alpha,
                               final int durMin, final int durRange,
                               final boolean flap, long startDelay) {
        layer.postDelayed(() -> {
            if (!layer.isAttachedToWindow() || layer.getWidth() == 0) return;

            float density = layer.getResources().getDisplayMetrics().density;
            int w = layer.getWidth();
            int h = layer.getHeight();

            final TextView view = new TextView(layer.getContext());
            view.setText(emoji);
            view.setTextSize(sizeMin + random.nextInt(sizeRange));
            view.setAlpha(alpha);
            layer.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            view.setX(w + 20 * density);
            view.setY(h * (yMin + random.nextFloat() * yRange));

            // wings: the bat squashes up and down quickly
            final ObjectAnimator wings = flap
                    ? ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0.55f) : null;
            if (wings != null) {
                wings.setDuration(180);
                wings.setRepeatMode(ValueAnimator.REVERSE);
                wings.setRepeatCount(ValueAnimator.INFINITE);
                wings.start();
            }

            float drift = (random.nextBoolean() ? 1 : -1) * 40 * density;
            view.animate().x(-60 * density).translationYBy(drift)
                    .setDuration(durMin + random.nextInt(durRange))
                    .setInterpolator(new LinearInterpolator())
                    .withEndAction(() -> {
                        if (wings != null) wings.cancel();
                        layer.removeView(view);
                        if (layer.isAttachedToWindow()) {
                            launch(layer, emoji, sizeMin, sizeRange, yMin, yRange, alpha,
                                    durMin, durRange, flap, 2000 + random.nextInt(5000));
                        }
                    }).start();
        }, startDelay);
    }
}