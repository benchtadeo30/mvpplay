package com.appdev.mvpplay;

import android.app.Activity;
import android.app.Application;
import android.content.res.Configuration;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.SeekBar;

import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

// all the animations of the app in one place
public class Anim {

    private static final Set<View> pressed =
            Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static final Set<RecyclerView> watched =
            Collections.newSetFromMap(new WeakHashMap<RecyclerView, Boolean>());

    // ---------- press effect: shrinks when touched, springs back ----------
    public static void press(View v) {
        if (!pressed.add(v)) return;
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float d = view.getResources().getDisplayMetrics().density;
                    if (view.getHeight() > 300 * d) break;   // big overlays do not shrink
                    float scale = view.getWidth() > 220 * d ? 0.97f : 0.9f;
                    view.animate().cancel();
                    view.animate().scaleX(scale).scaleY(scale).setDuration(90)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(240)
                            .setInterpolator(new OvershootInterpolator(2f)).start();
                    break;
                default:
                    break;
            }
            return false;   // the normal click still works
        });
    }

    // gives the press effect to every button inside a screen (and to list rows as they appear)
    public static void pressAll(View root) {
        if (root == null) return;
        if (root instanceof RecyclerView) {
            RecyclerView rv = (RecyclerView) root;
            if (watched.add(rv)) {
                rv.addOnChildAttachStateChangeListener(
                        new RecyclerView.OnChildAttachStateChangeListener() {
                            @Override
                            public void onChildViewAttachedToWindow(View child) {
                                pressAll(child);
                            }

                            @Override
                            public void onChildViewDetachedFromWindow(View child) {
                            }
                        });
            }
        } else if (root.isClickable() && root.getId() != R.id.miniPlayer
                && !(root instanceof EditText)
                && !(root instanceof SeekBar) && !(root instanceof WaveformView)) {
            press(root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) pressAll(group.getChildAt(i));
        }
    }

    // ---------- small effects ----------
    // a quick bounce (hearts, chips, play button)
    public static void pop(View v) {
        v.animate().cancel();
        v.setScaleX(0.7f);
        v.setScaleY(0.7f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(350)
                .setInterpolator(new OvershootInterpolator(3f)).start();
    }

    public static void fadeIn(View v, long ms) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(ms).start();
    }

    // the rows of a list fade and slide in one after another
    public static void runList(RecyclerView rv) {
        LayoutAnimationController c =
                AnimationUtils.loadLayoutAnimation(rv.getContext(), R.anim.layout_list_in);
        rv.setLayoutAnimation(c);
        rv.scheduleLayoutAnimation();
    }

    // ---------- Now Playing slides up from the bottom and back down ----------
    public static void slideUpOpen(Activity a) {
        if (Build.VERSION.SDK_INT >= 34) {
            a.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN,
                    R.anim.slide_up_in, R.anim.hold);
        } else {
            a.overridePendingTransition(R.anim.slide_up_in, R.anim.hold);
        }
    }

    public static void slideDownClose(Activity a) {
        if (Build.VERSION.SDK_INT >= 34) {
            a.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE,
                    R.anim.hold, R.anim.slide_down_out);
        } else {
            a.overridePendingTransition(R.anim.hold, R.anim.slide_down_out);
        }
    }

    // ---------- turns the press effect on for every screen automatically ----------
    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity a) {
                // light mode: dark status bar icons. Login and Splash are always dark.
                if (!(a instanceof SplashActivity)) {
                    boolean night = (a.getResources().getConfiguration().uiMode
                            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                    WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(
                            a.getWindow(), a.getWindow().getDecorView());
                    bars.setAppearanceLightStatusBars(!night);
                    bars.setAppearanceLightNavigationBars(!night);
                }
                HalloweenFx.attach(a);
                final View content = a.findViewById(android.R.id.content);
                if (content != null) content.post(() -> pressAll(content));
            }

            @Override
            public void onActivityCreated(Activity a, Bundle b) {
            }

            @Override
            public void onActivityResumed(Activity a) {
            }

            @Override
            public void onActivityPaused(Activity a) {
            }

            @Override
            public void onActivityStopped(Activity a) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity a, Bundle b) {
            }

            @Override
            public void onActivityDestroyed(Activity a) {
            }
        });
    }
}