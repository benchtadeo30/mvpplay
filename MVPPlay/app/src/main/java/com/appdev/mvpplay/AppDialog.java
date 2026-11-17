package com.appdev.mvpplay;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

// modern pop-ups in the style of the app: a rounded card, an icon, big title, pill buttons.
// Colors come from the theme, so every theme looks right.
public final class AppDialog {

    // returns an error message to show under the box, or null when it worked (the pop-up closes)
    public interface Submit {
        String submit(String text);
    }

    public interface OnChoice {
        void onChoice(int index);
    }

    private static final int PINK = 0xFFF472B6;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private AppDialog() {
    }

    // ---------- a text box (new playlist, rename...) ----------
    public static void input(final Activity a, int icon, String title, String message,
                             String hint, String initial, String positive, int maxLength,
                             final Submit submit) {
        LinearLayout box = card(a);
        header(a, box, icon, title, message, false);

        final EditText et = new EditText(a);
        et.setHint(hint);
        et.setText(initial);
        et.setSelectAllOnFocus(true);
        et.setSingleLine(true);
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        et.setTextSize(16);
        et.setTextColor(color(a, R.color.text_primary));
        et.setHintTextColor(color(a, R.color.text_hint));
        et.setBackground(rounded(color(a, R.color.surface_field), dp(a, 14), 0, 0));
        et.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 14));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(MATCH, WRAP);
        ep.topMargin = dp(a, 18);
        box.addView(et, ep);

        final TextView error = new TextView(a);
        error.setTextSize(13);
        error.setTextColor(PINK);
        error.setVisibility(View.GONE);
        LinearLayout.LayoutParams errParams = new LinearLayout.LayoutParams(WRAP, WRAP);
        errParams.topMargin = dp(a, 6);
        errParams.setMarginStart(dp(a, 4));
        box.addView(error, errParams);

        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                error.setVisibility(View.GONE);   // typing again clears the message
            }
        });

        LinearLayout row = buttonRow(a, box, 20);
        TextView cancel = button(a, "Cancel", false, false);
        TextView ok = button(a, positive, true, false);
        row.addView(cancel, weight(a, 1f, 0));
        row.addView(ok, weight(a, 1f, 10));

        final AlertDialog dialog = build(a, box);
        cancel.setOnClickListener(v -> dialog.dismiss());
        final Runnable go = () -> {
            String err = submit.submit(et.getText().toString().trim());
            if (err == null) {
                dialog.dismiss();
            } else {
                error.setText(err);
                error.setVisibility(View.VISIBLE);
                shake(et, dp(a, 6));
            }
        };
        ok.setOnClickListener(v -> go.run());
        et.setOnEditorActionListener((v, actionId, event) -> {
            go.run();
            return true;
        });

        show(a, dialog, box, true);
        et.requestFocus();
    }

    // ---------- yes / no (delete...) ----------
    public static void confirm(Activity a, int icon, String title, String message,
                               String positive, boolean danger, final Runnable onYes) {
        LinearLayout box = card(a);
        header(a, box, icon, title, message, danger);

        LinearLayout row = buttonRow(a, box, 22);
        TextView cancel = button(a, "Cancel", false, false);
        TextView ok = button(a, positive, true, danger);
        row.addView(cancel, weight(a, 1f, 0));
        row.addView(ok, weight(a, 1f, 10));

        final AlertDialog dialog = build(a, box);
        cancel.setOnClickListener(v -> dialog.dismiss());
        ok.setOnClickListener(v -> {
            dialog.dismiss();
            onYes.run();
        });
        show(a, dialog, box, false);
    }

    // ---------- a list of choices ----------
    public static void choices(Activity a, int icon, String title, String[] labels,
                               int[] icons, final OnChoice onChoice) {
        LinearLayout box = card(a);
        header(a, box, icon, title, null, false);

        final AlertDialog dialog = build(a, box);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(a, 16), 0, dp(a, 16), 0);
            row.setBackground(rounded(color(a, R.color.surface_field), dp(a, 16), 0, 0));
            row.setClickable(true);
            row.setFocusable(true);
            Anim.press(row);

            ImageView iv = new ImageView(a);
            iv.setImageResource(icons[i]);
            ImageViewCompat.setImageTintList(iv,
                    ColorStateList.valueOf(color(a, R.color.accent_cyan_light)));
            row.addView(iv, new LinearLayout.LayoutParams(dp(a, 22), dp(a, 22)));

            TextView tv = new TextView(a);
            tv.setText(labels[i]);
            tv.setTextSize(16);
            tv.setTextColor(color(a, R.color.text_primary));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(WRAP, WRAP);
            tp.setMarginStart(dp(a, 14));
            row.addView(tv, tp);

            row.setOnClickListener(v -> {
                dialog.dismiss();
                onChoice.onChoice(index);
            });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(MATCH, dp(a, 54));
            rp.topMargin = dp(a, i == 0 ? 18 : 8);
            box.addView(row, rp);
        }

        LinearLayout row = buttonRow(a, box, 14);
        TextView cancel = button(a, "Cancel", false, false);
        row.addView(cancel, new LinearLayout.LayoutParams(MATCH, dp(a, 48)));
        cancel.setOnClickListener(v -> dialog.dismiss());
        show(a, dialog, box, false);
    }

    // ---------- the pieces ----------
    private static LinearLayout card(Activity a) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a, 24), dp(a, 24), dp(a, 24), dp(a, 20));
        box.setBackground(rounded(color(a, R.color.surface_card), dp(a, 28),
                dp(a, 1), color(a, R.color.divider)));
        return box;
    }

    // icon circle, title and (optional) message
    private static void header(Activity a, LinearLayout box, int icon, String title,
                               String message, boolean danger) {
        int tint = danger ? PINK : color(a, R.color.accent_purple);

        ImageView iv = new ImageView(a);
        iv.setImageResource(icon);
        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(tint));
        iv.setPadding(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 12));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor((tint & 0x00FFFFFF) | 0x26000000);
        iv.setBackground(circle);
        box.addView(iv, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));

        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(22);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(color(a, R.color.text_primary));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(MATCH, WRAP);
        tp.topMargin = dp(a, 16);
        box.addView(t, tp);

        if (message != null && !message.isEmpty()) {
            TextView m = new TextView(a);
            m.setText(message);
            m.setTextSize(15);
            m.setLineSpacing(0f, 1.1f);
            m.setTextColor(color(a, R.color.text_secondary));
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(MATCH, WRAP);
            mp.topMargin = dp(a, 6);
            box.addView(m, mp);
        }
    }

    private static LinearLayout buttonRow(Activity a, LinearLayout box, int topMarginDp) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(MATCH, WRAP);
        rp.topMargin = dp(a, topMarginDp);
        box.addView(row, rp);
        return row;
    }

    private static LinearLayout.LayoutParams weight(Activity a, float w, int startMarginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(a, 48), w);
        p.setMarginStart(dp(a, startMarginDp));
        return p;
    }

    private static TextView button(Activity a, String label, boolean primary, boolean danger) {
        TextView b = new TextView(a);
        b.setText(label);
        b.setTextSize(15);
        b.setTypeface(null, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setClickable(true);
        b.setFocusable(true);
        if (primary && danger) {
            b.setBackground(rounded(PINK, dp(a, 24), 0, 0));
            b.setTextColor(0xFF3A0A1E);
        } else if (primary) {
            b.setBackgroundResource(R.drawable.bg_button_gradient);
            b.setTextColor(color(a, R.color.button_text));
        } else {
            b.setBackground(rounded(color(a, R.color.surface_field), dp(a, 24), 0, 0));
            b.setTextColor(color(a, R.color.text_secondary));
        }
        Anim.press(b);
        return b;
    }

    private static AlertDialog build(Activity a, View box) {
        AlertDialog dialog = new AlertDialog.Builder(a).setView(box).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        return dialog;
    }

    // wide card, darker backdrop and a soft fade-in
    private static void show(Activity a, AlertDialog dialog, View box, boolean keyboard) {
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            int width = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.88f);
            w.setLayout(Math.min(width, dp(a, 420)), WRAP);
            w.setDimAmount(0.7f);
            if (keyboard) {
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        }
        box.setAlpha(0f);
        box.setScaleX(0.92f);
        box.setScaleY(0.92f);
        box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
    }

    private static void shake(View v, float amount) {
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X,
                        0f, -amount, amount, -amount * 0.6f, amount * 0.6f, 0f)
                .setDuration(320).start();
    }

    private static GradientDrawable rounded(int fill, int radiusPx, int strokePx, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, strokeColor);
        return g;
    }

    private static int color(Activity a, int res) {
        return ContextCompat.getColor(a, res);
    }

    private static int dp(Activity a, int value) {
        return (int) (value * a.getResources().getDisplayMetrics().density);
    }
}