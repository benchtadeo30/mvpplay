package com.appdev.mvpplay;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.text.InputFilter;
import android.view.WindowManager;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

// the welcome card on Home: greeting + name + the chihuahua for the time of day
public class WelcomeCard {

    private static final String PREFS = "welcome";
    private static final String KEY_NAME = "name";
    private static final String KEY_ASKED = "asked";   // true after the name was asked once

    private final Activity activity;
    private final TextView tvGreeting, tvName;
    private final ImageView ivDog, ivBackground;
    private final List<ObjectAnimator> dogAnimators = new ArrayList<>();
    private int shownPart = -1;       // which background picture is on screen
    private int animatingPart = -1;   // which dog animation is running
    private int shownLook = -1;   // which theme look the background was drawn for

    public WelcomeCard(Activity activity) {
        this.activity = activity;
        tvGreeting = activity.findViewById(R.id.tvGreeting);
        tvName = activity.findViewById(R.id.tvGreetingName);
        ivDog = activity.findViewById(R.id.ivChihuahua);
        ivBackground = activity.findViewById(R.id.ivWelcomeBackground);

        View card = activity.findViewById(R.id.cardWelcome);
        card.setOnClickListener(v -> askName());

        // round the corners of the picture
        final float radius = 16 * activity.getResources().getDisplayMetrics().density;
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        card.setClipToOutline(true);
        refresh();

        // the very first time: ask for the name once
        if (!prefs().getBoolean(KEY_ASKED, false)) {
            ivDog.post(this::askName);
        }
    }

    // 0 = normal themes, 1 = Halloween, 2 = Christmas, 3 = Summer
    private int look() {
        if (ThemeStore.isHalloween(activity)) return 1;
        if (ThemeStore.isChristmas(activity)) return 2;
        if (ThemeStore.isSummer(activity)) return 3;
        return 0;
    }

    // 0 = morning (5:00 - 11:59), 1 = afternoon (12:00 - 17:59), 2 = evening artwork (18:00 - 4:59)
    // the greeting text becomes "Still Up?" from 0:00 - 4:59, see isLateNight()
    private static int partOfDay() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) return 0;
        if (hour >= 12 && hour < 18) return 1;
        return 2;
    }

    // true from 12:00 AM to 4:59 AM, when the card says "Still Up?"
    private static boolean isLateNight() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= 0 && hour < 5;
    }

    // updates the greeting, the name and the chihuahua
    public void refresh() {
        // Settings can hide the whole welcome card
        View card = activity.findViewById(R.id.cardWelcome);
        boolean visible = AppSettings.showWelcome(activity);
        card.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            stop();
            return;
        }

        int part = partOfDay();
        String[] greetings = {"Good Morning", "Good Afternoon", "Good Evening"};
        int look = look();
        int[][] dogSets = {
                {R.drawable.chihuahua_morning,            // normal: a normal face
                        R.drawable.chihuahua_afternoon,   // winking
                        R.drawable.chihuahua_evening},    // sleeping clothes
                {R.drawable.chihal1,                      // Halloween: pumpkin
                        R.drawable.chihal3,               // skeleton
                        R.drawable.chihal2},              // witch, winking
                {R.drawable.christmas_chi1,               // Christmas: morning
                        R.drawable.christmas_chi2,        // afternoon
                        R.drawable.christmas_chi3},       // evening
                {R.drawable.chisum1,                      // Summer: morning
                        R.drawable.chisum2,               // afternoon
                        R.drawable.chisum3}};             // evening
        int[] dogs = dogSets[look];

        tvGreeting.setText(isLateNight() ? "Still Up?" : greetings[part]);
        tvName.setText(userName());
        ivDog.setImageResource(dogs[part]);

        if (part != shownPart || look != shownLook) {
            shownPart = part;
            shownLook = look;
            showBackground(part, look);
        }
        if (part != animatingPart) startDogAnimation(part);
    }

    // the picture behind the card, placed a little above the middle so the sun stays in view
    private void showBackground(int part, int look) {
        int[][] pictureSets = {
                {R.drawable.welcome_morning, R.drawable.welcome_afternoon, R.drawable.welcome_evening},
                {R.drawable.hal_morning, R.drawable.hal_afternoon, R.drawable.hal_evening},
                {R.drawable.christmas_morning, R.drawable.christmas_afternoon, R.drawable.christmas_evening},
                {R.drawable.summer_morning, R.drawable.summer_afternoon, R.drawable.summer_evening}};
        int[] pictures = pictureSets[look];
        ivBackground.setImageResource(pictures[part]);
        ivBackground.post(() -> {
            Drawable d = ivBackground.getDrawable();
            if (d == null || ivBackground.getWidth() == 0) return;
            float scale = Math.max(
                    ivBackground.getWidth() / (float) d.getIntrinsicWidth(),
                    ivBackground.getHeight() / (float) d.getIntrinsicHeight());
            float dx = (ivBackground.getWidth() - d.getIntrinsicWidth() * scale) * 0.5f;
            float dy = (ivBackground.getHeight() - d.getIntrinsicHeight() * scale) * 0.2f;
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate(dx, dy);
            ivBackground.setImageMatrix(matrix);
        });
    }

    // morning: happy hops, afternoon: wiggle, evening: slow float
    private void startDogAnimation(int part) {
        stop();
        animatingPart = part;
        float d = activity.getResources().getDisplayMetrics().density;

        // the dog moves around its feet (the dog picture is 104dp in the layout)
        ivDog.setPivotX(52 * d);
        ivDog.setPivotY(94 * d);

        if (part == 0) {
            ObjectAnimator hop = ObjectAnimator.ofPropertyValuesHolder(ivDog,
                    PropertyValuesHolder.ofKeyframe(View.TRANSLATION_Y,
                            key(0f, 0f, null),
                            key(0.18f, -12 * d, new DecelerateInterpolator()),
                            key(0.36f, 0f, new AccelerateInterpolator()),
                            key(0.50f, -6 * d, new DecelerateInterpolator()),
                            key(0.62f, 0f, new AccelerateInterpolator()),
                            key(1f, 0f, null)));
            hop.setDuration(2000);
            run(hop);
        } else if (part == 1) {
            TimeInterpolator ease = new AccelerateDecelerateInterpolator();
            ObjectAnimator wiggle = ObjectAnimator.ofPropertyValuesHolder(ivDog,
                    PropertyValuesHolder.ofKeyframe(View.ROTATION,
                            key(0f, 0f, null),
                            key(0.08f, -9f, ease),
                            key(0.20f, 9f, ease),
                            key(0.32f, -6f, ease),
                            key(0.42f, 5f, ease),
                            key(0.50f, 0f, ease),
                            key(1f, 0f, null)));
            wiggle.setDuration(2600);
            run(wiggle);
        } else {
            ObjectAnimator drift = ObjectAnimator.ofFloat(ivDog, View.TRANSLATION_Y, 0f, -8 * d);
            drift.setDuration(2600);
            drift.setRepeatMode(ValueAnimator.REVERSE);
            drift.setInterpolator(new AccelerateDecelerateInterpolator());
            run(drift);

            ObjectAnimator breathe = ObjectAnimator.ofPropertyValuesHolder(ivDog,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.04f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.04f));
            breathe.setDuration(2600);
            breathe.setRepeatMode(ValueAnimator.REVERSE);
            breathe.setInterpolator(new AccelerateDecelerateInterpolator());
            run(breathe);
        }
    }

    // stops the dog (called when Home is not on screen, which saves battery)
    public void stop() {
        for (ObjectAnimator animator : dogAnimators) animator.cancel();
        dogAnimators.clear();
        animatingPart = -1;
        ivDog.setTranslationY(0f);
        ivDog.setRotation(0f);
        ivDog.setScaleX(1f);
        ivDog.setScaleY(1f);
    }

    private void run(ObjectAnimator animator) {
        animator.setRepeatCount(ValueAnimator.INFINITE);
        if (animator.getInterpolator() == null) animator.setInterpolator(new LinearInterpolator());
        dogAnimators.add(animator);
        animator.start();
    }

    private static Keyframe key(float fraction, float value, TimeInterpolator interpolator) {
        Keyframe keyframe = Keyframe.ofFloat(fraction, value);
        if (interpolator != null) keyframe.setInterpolator(interpolator);
        return keyframe;
    }

    private String userName() {
        String name = prefs().getString(KEY_NAME, "").trim();
        return name.isEmpty() ? "User" : name;
    }

    private SharedPreferences prefs() {
        return activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
    }

    // pop-up that asks for the name (first time, or when the card is tapped)
    private void askName() {
        String saved = prefs().getString(KEY_NAME, "");

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_name);

        EditText input = dialog.findViewById(R.id.etDialogName);
        TextView btnCancel = dialog.findViewById(R.id.btnDialogCancel);
        TextView btnSave = dialog.findViewById(R.id.btnDialogSave);

        input.setText(saved);
        input.setSelection(saved.length());
        btnCancel.setText(saved.isEmpty() ? "Skip" : "Cancel");

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            prefs().edit().putString(KEY_NAME, input.getText().toString().trim()).apply();
            refresh();
            dialog.dismiss();
        });
        // the keyboard's Done key works like the Save button
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnSave.performClick();
                return true;
            }
            return false;
        });

        // whatever the user picks, the name is not asked again by itself
        dialog.setOnDismissListener(d -> prefs().edit().putBoolean(KEY_ASKED, true).apply());

        Window window = dialog.getWindow();
        if (window != null) {
            // transparent window so the rounded corners show, card is 88% of the screen width
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.88f);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setDimAmount(0.6f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        dialog.show();
        input.requestFocus();
    }
}