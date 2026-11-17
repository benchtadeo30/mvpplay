package com.appdev.mvpplay;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.InputFilter;
import android.widget.EditText;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

// one modern pop-up used across the app: a rounded card that follows the theme
public class ModernDialog {

    public interface OnPick {
        void onPick(int index);
    }

    public interface OnText {
        void onText(String text);
    }

    // a pop-up with one text box (used to name your own background effect)
    public static void input(Activity a, String title, String hint, String initial,
                             String positive, OnText onText) {
        LinearLayout card = card(a);
        card.addView(titleView(a, title));
        final Dialog dialog = make(a, card);

        final EditText input = new EditText(a);
        input.setHint(hint);
        input.setText(initial);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        input.setTextColor(ContextCompat.getColor(a, R.color.text_primary));
        input.setHintTextColor(ContextCompat.getColor(a, R.color.text_hint));
        input.setPadding(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12));
        input.setBackground(round(ContextCompat.getColor(a, R.color.surface_field), 14,
                Color.TRANSPARENT, a));
        input.setSelection(input.getText().length());
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = dp(a, 14);
        card.addView(input, ip);

        card.addView(buttons(a, dialog, positive, false,
                () -> onText.onText(input.getText().toString().trim())));
        show(dialog, card);

        input.requestFocus();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    // a list of choices (tap one and it is picked right away)
    public static void choose(Activity a, String title, String[] items, int checked, OnPick onPick) {
        LinearLayout card = card(a);
        card.addView(titleView(a, title));
        final Dialog dialog = make(a, card);

        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        listParams.topMargin = dp(a, 14);
        card.addView(list, listParams);

        for (int i = 0; i < items.length; i++) {
            final int index = i;
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = dp(a, 4);
            list.addView(optionRow(a, items[i], i == checked, v -> {
                dialog.dismiss();
                onPick.onPick(index);
            }), rp);
        }
        card.addView(buttons(a, dialog, null, false, null));
        show(dialog, card);
    }

    // a question with Cancel and one main button (red-pink when danger is true)
    public static void confirm(Activity a, String title, String message, String positive,
                               boolean danger, Runnable onYes) {
        LinearLayout card = card(a);
        card.addView(titleView(a, title));
        final Dialog dialog = make(a, card);

        TextView msg = new TextView(a);
        msg.setText(message);
        msg.setTextSize(14);
        msg.setLineSpacing(dp(a, 3), 1f);
        msg.setTextColor(ContextCompat.getColor(a, R.color.text_secondary));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.topMargin = dp(a, 10);
        card.addView(msg, mp);

        card.addView(buttons(a, dialog, positive, danger, onYes));
        show(dialog, card);
    }

    // ---------- pieces ----------
    private static LinearLayout card(Activity a) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 22), dp(a, 22), dp(a, 22), dp(a, 14));
        card.setBackground(round(ContextCompat.getColor(a, R.color.surface_card), 26,
                ContextCompat.getColor(a, R.color.divider), a));
        return card;
    }

    private static TextView titleView(Activity a, String title) {
        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(20);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(ContextCompat.getColor(a, R.color.text_primary));
        return t;
    }

    private static View optionRow(Activity a, String label, boolean selected, View.OnClickListener click) {
        int accent = ContextCompat.getColor(a, R.color.accent_purple);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a, 14), dp(a, 14), dp(a, 14), dp(a, 14));
        row.setBackground(selected
                ? round((accent & 0x00FFFFFF) | 0x26000000, 14, (accent & 0x00FFFFFF) | 0x66000000, a)
                : round(Color.TRANSPARENT, 14, Color.TRANSPARENT, a));
        row.setClickable(true);
        row.setOnClickListener(click);
        Anim.press(row);

        // the radio: a ring, with a dot inside when picked
        FrameLayout radio = new FrameLayout(a);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(a, 2), selected ? accent
                : ContextCompat.getColor(a, R.color.text_hint));
        radio.setBackground(ring);
        if (selected) {
            View dot = new View(a);
            GradientDrawable dotShape = new GradientDrawable();
            dotShape.setShape(GradientDrawable.OVAL);
            dotShape.setColor(accent);
            dot.setBackground(dotShape);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(a, 10), dp(a, 10));
            dotParams.gravity = Gravity.CENTER;
            radio.addView(dot, dotParams);
        }
        row.addView(radio, new LinearLayout.LayoutParams(dp(a, 22), dp(a, 22)));

        TextView text = new TextView(a);
        text.setText(label);
        text.setTextSize(16);
        text.setTypeface(null, selected
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        text.setTextColor(ContextCompat.getColor(a,
                selected ? R.color.text_primary : R.color.text_secondary));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart(dp(a, 14));
        row.addView(text, tp);
        return row;
    }

    // Cancel on the right, and the main button when there is one
    private static View buttons(Activity a, Dialog dialog, String positive, boolean danger,
                                Runnable onYes) {
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(a, 12);
        bar.setLayoutParams(bp);

        TextView cancel = new TextView(a);
        cancel.setText("Cancel");
        cancel.setTextSize(15);
        cancel.setTypeface(null, android.graphics.Typeface.BOLD);
        cancel.setTextColor(ContextCompat.getColor(a, R.color.text_secondary));
        cancel.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));
        cancel.setClickable(true);
        cancel.setOnClickListener(v -> dialog.dismiss());
        Anim.press(cancel);
        bar.addView(cancel);

        if (positive != null) {
            int fill = danger ? 0xFFF472B6 : ContextCompat.getColor(a, R.color.accent_purple);
            TextView yes = new TextView(a);
            yes.setText(positive);
            yes.setTextSize(15);
            yes.setTypeface(null, android.graphics.Typeface.BOLD);
            yes.setTextColor(ColorUtils.calculateLuminance(fill) > 0.5 ? Color.BLACK : Color.WHITE);
            yes.setGravity(Gravity.CENTER);
            yes.setPadding(dp(a, 22), dp(a, 12), dp(a, 22), dp(a, 12));
            yes.setBackground(round(fill, 100, Color.TRANSPARENT, a));
            yes.setClickable(true);
            yes.setOnClickListener(v -> {
                dialog.dismiss();
                if (onYes != null) onYes.run();
            });
            Anim.press(yes);
            LinearLayout.LayoutParams yp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            yp.setMarginStart(dp(a, 6));
            bar.addView(yes, yp);
        }
        return bar;
    }

    private static Dialog make(Activity a, View content) {
        Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.88f);
            w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.6f);
        }
        return dialog;
    }

    // soft fade and grow when it opens
    private static void show(Dialog dialog, View card) {
        card.setAlpha(0f);
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        dialog.setOnShowListener(d -> card.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(180).setInterpolator(new DecelerateInterpolator()).start());
        dialog.show();
    }

    private static GradientDrawable round(int color, int radiusDp, int strokeColor, Activity a) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(a, radiusDp));
        if (strokeColor != Color.TRANSPARENT) shape.setStroke(dp(a, 1), strokeColor);
        return shape;
    }

    private static int dp(Activity a, float value) {
        return (int) (value * a.getResources().getDisplayMetrics().density);
    }
}