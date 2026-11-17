package com.appdev.mvpplay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.media3.common.MediaItem;
import androidx.media3.session.MediaController;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// the lyrics pop-up of the Now Playing screen
public class LyricsSheet {

    private static final long USER_PAUSE_MS = 3500;   // auto-scroll waits after you scroll
    private static final float SMALL = 0.88f;         // size of the lines that are not playing

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<TextView> lineViews = new ArrayList<>();
    private List<LrcParser.Line> synced = new ArrayList<>();
    private ObjectAnimator[] colorAnims = new ObjectAnimator[0];

    private BottomSheetDialog sheet;
    private ScrollView scroll;
    private LinearLayout linesBox, messageBox;
    private TextView tvSong, tvArtist, tvPill, msgIcon, msgTitle, msgSub;
    private View progressFill;
    private ObjectAnimator scrollAnim, pulseAnim;
    private boolean autoScrolling = false;
    private long userScrollUntil = 0;

    private boolean open = false;
    private String shownId = "?";
    private String key = null;
    private String title = "";
    private String artist = "";
    private long durationSec = 0;
    private int current = -1;
    private int token = 0;
    private int waited = 0;

    public LyricsSheet(Activity activity) {
        this.activity = activity;
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density);
    }

    private int color(int res) {
        return ContextCompat.getColor(activity, res);
    }

    public void dismiss() {
        if (sheet != null && sheet.isShowing()) sheet.dismiss();
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    // ---------- the pop-up ----------
    public void show() {
        if (open) return;
        sheet = new BottomSheetDialog(activity);

        // the sheet: rounded on top, a soft purple glow fading into the card color
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(12), dp(22), dp(22));
        int surface = color(R.color.surface_card);
        int tint = ColorUtils.blendARGB(surface, color(R.color.accent_purple), 0.22f);
        GradientDrawable boxBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{tint, surface, surface});
        float corner = dp(28);
        boxBg.setCornerRadii(new float[]{corner, corner, corner, corner, 0, 0, 0, 0});
        box.setBackground(boxBg);

        // little drag handle
        GradientDrawable handleShape = new GradientDrawable();
        handleShape.setColor((color(R.color.text_hint) & 0x00FFFFFF) | 0x66000000);
        handleShape.setCornerRadius(dp(2));
        View handle = new View(activity);
        handle.setBackground(handleShape);
        LinearLayout.LayoutParams hp = lp(dp(40), dp(4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = dp(16);
        box.addView(handle, hp);

        // header: small label, song title, artist, and a status pill on the right
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout names = new LinearLayout(activity);
        names.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(activity);
        label.setText("LYRICS");
        label.setTextSize(11);
        label.setTypeface(null, Typeface.BOLD);
        label.setLetterSpacing(0.18f);
        label.setTextColor(color(R.color.accent_cyan_light));
        names.addView(label);

        tvSong = new TextView(activity);
        tvSong.setTextSize(22);
        tvSong.setTypeface(null, Typeface.BOLD);
        tvSong.setSingleLine(true);
        tvSong.setEllipsize(TextUtils.TruncateAt.END);
        tvSong.setTextColor(color(R.color.text_primary));
        LinearLayout.LayoutParams sp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(2);
        names.addView(tvSong, sp);

        tvArtist = new TextView(activity);
        tvArtist.setTextSize(14);
        tvArtist.setSingleLine(true);
        tvArtist.setEllipsize(TextUtils.TruncateAt.END);
        tvArtist.setTextColor(color(R.color.text_secondary));
        names.addView(tvArtist);

        header.addView(names, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        tvPill = new TextView(activity);
        tvPill.setTextSize(11);
        tvPill.setTypeface(null, Typeface.BOLD);
        tvPill.setLetterSpacing(0.1f);
        tvPill.setTextColor(color(R.color.accent_cyan_light));
        tvPill.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor((color(R.color.accent_cyan_light) & 0x00FFFFFF) | 0x26000000);
        pillBg.setCornerRadius(dp(100));
        tvPill.setBackground(pillBg);
        tvPill.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.setMarginStart(dp(12));
        header.addView(tvPill, pp);

        box.addView(header, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // thin progress bar of the song
        FrameLayout track = new FrameLayout(activity);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setColor((color(R.color.text_hint) & 0x00FFFFFF) | 0x33000000);
        trackBg.setCornerRadius(dp(2));
        track.setBackground(trackBg);
        progressFill = new View(activity);
        GradientDrawable fillBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{color(R.color.accent_purple), color(R.color.accent_cyan_light)});
        fillBg.setCornerRadius(dp(2));
        progressFill.setBackground(fillBg);
        progressFill.setPivotX(0f);
        progressFill.setScaleX(0f);
        track.addView(progressFill, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams trp = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        trp.topMargin = dp(16);
        box.addView(track, trp);

        // the lyrics area: scrolling lines with soft fading edges, and a message layer on top
        int areaH = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.52f);
        FrameLayout area = new FrameLayout(activity);

        scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(56));
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setNestedScrollingEnabled(false);   // never pass leftover swipes to the sheet
        // swipes on the lyrics scroll the lyrics, they never drag the sheet down
        scroll.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return false;
        });
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            // a scroll that is not ours: the user is reading, so auto-scroll waits
            if (!autoScrolling) userScrollUntil = SystemClock.uptimeMillis() + USER_PAUSE_MS;
        });
        linesBox = new LinearLayout(activity);
        linesBox.setOrientation(LinearLayout.VERTICAL);
        // room above and below so the first and last lines can reach the middle
        linesBox.setPadding(dp(2), areaH / 2 - dp(20), dp(2), areaH / 2);
        scroll.addView(linesBox, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        area.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        messageBox = new LinearLayout(activity);
        messageBox.setOrientation(LinearLayout.VERTICAL);
        messageBox.setGravity(Gravity.CENTER);
        messageBox.setPadding(dp(24), dp(24), dp(24), dp(24));
        msgIcon = new TextView(activity);
        msgIcon.setText("\u266A");
        msgIcon.setGravity(Gravity.CENTER);
        msgIcon.setTextSize(44);
        msgIcon.setTextColor(color(R.color.accent_cyan_light));
        messageBox.addView(msgIcon);
        msgTitle = new TextView(activity);
        msgTitle.setTextSize(17);
        msgTitle.setTypeface(null, Typeface.BOLD);
        msgTitle.setGravity(Gravity.CENTER);
        msgTitle.setTextColor(color(R.color.text_primary));
        LinearLayout.LayoutParams mt = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mt.topMargin = dp(10);
        messageBox.addView(msgTitle, mt);
        msgSub = new TextView(activity);
        msgSub.setTextSize(13);
        msgSub.setGravity(Gravity.CENTER);
        msgSub.setTextColor(color(R.color.text_hint));
        LinearLayout.LayoutParams ms = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ms.topMargin = dp(6);
        messageBox.addView(msgSub, ms);
        messageBox.setVisibility(View.GONE);
        area.addView(messageBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams ap = lp(ViewGroup.LayoutParams.MATCH_PARENT, areaH);
        ap.topMargin = dp(6);
        box.addView(area, ap);

        // buttons
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout bSearch = actionButton(R.drawable.ic_search, "Search",
                color(R.color.accent_cyan_light));
        LinearLayout bEdit = actionButton(R.drawable.ic_edit, "Add / edit",
                color(R.color.text_primary));
        LinearLayout bRemove = actionButton(R.drawable.ic_delete, "Remove", 0xFFF472B6);
        row.addView(bSearch, buttonParams(0));
        row.addView(bEdit, buttonParams(8));
        row.addView(bRemove, buttonParams(8));
        LinearLayout.LayoutParams rp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(12);
        box.addView(row, rp);

        // link to the website where the user can look up the lyrics
        TextView link = new TextView(activity);
        link.setText("Can't find it? Get the lyrics on lrclib.net \u2197");
        link.setTextSize(13);
        link.setGravity(Gravity.CENTER);
        link.setTextColor(color(R.color.accent_lavender));
        link.setPaintFlags(link.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        link.setPadding(dp(8), dp(12), dp(8), dp(4));
        link.setClickable(true);
        link.setFocusable(true);
        link.setOnClickListener(v -> openLrclib());
        Anim.press(link);
        box.addView(link, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        bSearch.setOnClickListener(v -> search());
        bEdit.setOnClickListener(v -> showEditor());
        bRemove.setOnClickListener(v -> {
            if (key == null) return;
            LyricsStore.delete(activity, key);
            clearLines();
            setPill("");
            showMessage("Lyrics removed", "Tap Search or Add / edit.", false);
        });

        sheet.setContentView(box);
        sheet.setOnShowListener(d -> {
            View s = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (s != null) {
                s.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(s);
                behavior.setSkipCollapsed(true);
                behavior.setDraggable(true);   // the header and handle can close the sheet
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        sheet.setOnDismissListener(d -> {
            open = false;
            token++;
            handler.removeCallbacksAndMessages(null);
            stopPulse();
            if (scrollAnim != null) scrollAnim.cancel();
        });

        open = true;
        shownId = "?";
        waited = 0;
        sheet.show();
        handler.post(tick);
    }

    // a pill button with an icon (used for Search, Add / edit and Remove)
    private LinearLayout actionButton(int iconRes, String label, int tint) {
        LinearLayout b = new LinearLayout(activity);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundResource(R.drawable.bg_chip);
        b.setClickable(true);
        b.setFocusable(true);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(tint);
        b.addView(icon, lp(dp(16), dp(16)));

        TextView t = new TextView(activity);
        t.setText(label);
        t.setTextSize(13);
        t.setTypeface(null, Typeface.BOLD);
        t.setSingleLine(true);
        t.setTextColor(tint);
        LinearLayout.LayoutParams tp = lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.setMarginStart(dp(6));
        b.addView(t, tp);

        Anim.press(b);
        return b;
    }

    private TextView button(String label, int textColor) {
        TextView b = new TextView(activity);
        b.setText(label);
        b.setTextSize(14);
        b.setTypeface(null, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setTextColor(textColor);
        b.setBackgroundResource(R.drawable.bg_chip);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams(int startMarginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1f);
        p.setMarginStart(dp(startMarginDp));
        return p;
    }

    // ---------- follows the player ----------
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!open) return;
            MediaController c = PlayerConnection.get();
            if (c != null) {
                MediaItem item = c.getCurrentMediaItem();
                String id = (item != null) ? item.mediaId : null;
                if (!Objects.equals(id, shownId)) {
                    loadSong(c, item);
                } else {
                    long position = c.getCurrentPosition();
                    highlight(position);
                    updateProgress(position, c.getDuration());
                }
            }
            handler.postDelayed(this, 250);
        }
    };

    private void updateProgress(long position, long duration) {
        if (progressFill == null || duration <= 0) return;
        float fraction = Math.max(0f, Math.min(1f, position / (float) duration));
        progressFill.animate().scaleX(fraction).setStartDelay(0).setDuration(250)
                .setInterpolator(new LinearInterpolator()).start();
    }

    // a new song: show its saved lyrics, or look them up
    private void loadSong(MediaController c, MediaItem item) {
        if (item != null) {
            long d = c.getDuration();
            // the length is not known for a moment after a song starts: wait a little
            if (d <= 0 && waited++ < 8) return;
            durationSec = d > 0 ? d / 1000 : 0;
        }
        waited = 0;
        token++;
        shownId = (item != null) ? item.mediaId : null;
        clearLines();
        progressFill.setScaleX(0f);

        if (item == null) {
            key = null;
            tvSong.setText("");
            tvArtist.setText("");
            setPill("");
            showMessage("Nothing is playing", "", false);
            return;
        }

        CharSequence t = item.mediaMetadata.title;
        CharSequence a = item.mediaMetadata.artist;
        title = (t != null) ? t.toString() : "";
        artist = (a != null) ? a.toString() : "";
        tvSong.setText(title);
        tvArtist.setText(artist.isEmpty() ? "Unknown artist" : artist);

        key = keyFor(item.mediaId);
        if (key == null) {
            setPill("");
            showMessage("Lyrics are only for songs", "Podcasts and videos have no lyrics.", false);
            return;
        }
        String saved = LyricsStore.load(activity, key);
        if (saved != null) display(saved); else search();
    }

    // the name the lyrics are saved under (null for podcasts)
    private String keyFor(String mediaId) {
        try {
            int id = Integer.parseInt(mediaId);
            if (CustomPodcastStore.isEpisode(id)) return null;
            if (id < 0) return "custom_" + (-id);
            String name = activity.getResources().getResourceEntryName(id);
            return name.startsWith("podcast_") ? null : name;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- messages and the status pill ----------
    private void setPill(String text) {
        tvPill.setText(text);
        tvPill.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showMessage(String line1, String line2, boolean pulse) {
        stopPulse();
        msgTitle.setText(line1);
        msgSub.setText(line2);
        msgSub.setVisibility(line2.isEmpty() ? View.GONE : View.VISIBLE);
        messageBox.setVisibility(View.VISIBLE);
        messageBox.setAlpha(0f);
        messageBox.animate().alpha(1f).setStartDelay(0).setDuration(300).start();
        if (pulse) {
            pulseAnim = ObjectAnimator.ofPropertyValuesHolder(msgIcon,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f, 1.2f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 1.2f),
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0.35f, 1f));
            pulseAnim.setDuration(900);
            pulseAnim.setRepeatMode(ValueAnimator.REVERSE);
            pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnim.start();
        }
    }

    private void hideMessage() {
        stopPulse();
        messageBox.animate().cancel();
        messageBox.setVisibility(View.GONE);
    }

    private void stopPulse() {
        if (pulseAnim != null) {
            pulseAnim.cancel();
            pulseAnim = null;
        }
        if (msgIcon != null) {
            msgIcon.setScaleX(1f);
            msgIcon.setScaleY(1f);
            msgIcon.setAlpha(1f);
        }
    }

    // ---------- showing the lyrics ----------
    private void clearLines() {
        if (scrollAnim != null) scrollAnim.cancel();
        for (ObjectAnimator a : colorAnims) if (a != null) a.cancel();
        linesBox.removeAllViews();
        lineViews.clear();
        synced = new ArrayList<>();
        colorAnims = new ObjectAnimator[0];
        current = -1;
        userScrollUntil = 0;
        scroll.scrollTo(0, 0);
    }

    private TextView addLine(String text, boolean isSynced) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setGravity(Gravity.START);
        tv.setTextColor(color(R.color.text_primary));
        if (isSynced) {
            tv.setTextSize(24);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setLineSpacing(0f, 1.08f);
            tv.setPadding(dp(2), dp(11), dp(2), dp(11));
            // grow and shrink from the left edge, so the text never jumps sideways
            tv.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) -> {
                v.setPivotX(0f);
                v.setPivotY((b - t) / 2f);
            });
        } else {
            tv.setTextSize(18);
            tv.setLineSpacing(0f, 1.1f);
            tv.setPadding(dp(2), dp(5), dp(2), dp(5));
            tv.setAlpha(0.92f);
        }
        linesBox.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private void display(String raw) {
        clearLines();
        hideMessage();
        synced = LrcParser.parse(raw);
        if (!synced.isEmpty()) {
            colorAnims = new ObjectAnimator[synced.size()];
            for (LrcParser.Line line : synced) {
                TextView tv = addLine(line.text.isEmpty() ? "\u266A" : line.text, true);
                final long time = line.timeMs;
                tv.setOnClickListener(v -> {
                    userScrollUntil = 0;   // jump now, even if you were scrolling a moment ago
                    MediaController c = PlayerConnection.get();
                    if (c != null) c.seekTo(time);
                });
                lineViews.add(tv);
            }
            applyStyles(-1, false);
            setPill("SYNCED");
        } else {
            for (String s : LrcParser.plain(raw)) lineViews.add(addLine(s.isEmpty() ? " " : s, false));
            setPill("PLAIN");
        }
        playIntro();
    }

    // the first lines slide up and fade in one after another
    private void playIntro() {
        int n = Math.min(lineViews.size(), 10);
        for (int i = 0; i < n; i++) {
            TextView tv = lineViews.get(i);
            float target = tv.getAlpha();
            tv.setAlpha(0f);
            tv.setTranslationY(dp(18));
            tv.animate().alpha(target).translationY(0f)
                    .setStartDelay(i * 45L).setDuration(450)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    // the lines near the active one are brighter, far ones fade away
    private float alphaFor(int index, int active) {
        if (index == active) return 1f;
        return Math.max(0.2f, 0.6f - 0.12f * Math.abs(index - active));
    }

    // size, brightness, color and glow of every line (animated or at once)
    private void applyStyles(int active, boolean animate) {
        int accent = color(R.color.accent_cyan_light);
        int normal = color(R.color.text_primary);
        for (int i = 0; i < lineViews.size(); i++) {
            TextView tv = lineViews.get(i);
            boolean on = (i == active);
            float alpha = alphaFor(i, active);
            float scale = on ? 1f : SMALL;
            int textColor = on ? accent : normal;

            if (animate) {
                boolean differs = Math.abs(tv.getAlpha() - alpha) > 0.01f
                        || Math.abs(tv.getScaleX() - scale) > 0.01f;
                if (differs) {
                    tv.animate().alpha(alpha).scaleX(scale).scaleY(scale)
                            .setStartDelay(0).setDuration(380)
                            .setInterpolator(new DecelerateInterpolator(1.4f)).start();
                }
                if (tv.getCurrentTextColor() != textColor) {
                    if (colorAnims[i] != null) colorAnims[i].cancel();
                    colorAnims[i] = ObjectAnimator.ofArgb(tv, "textColor",
                            tv.getCurrentTextColor(), textColor);
                    colorAnims[i].setDuration(320);
                    colorAnims[i].start();
                }
            } else {
                tv.setAlpha(alpha);
                tv.setScaleX(scale);
                tv.setScaleY(scale);
                tv.setTextColor(textColor);
            }
            // a soft glow on the line that is playing
            if (on) tv.setShadowLayer(dp(14), 0f, 0f, (accent & 0x00FFFFFF) | 0x99000000);
            else tv.setShadowLayer(0f, 0f, 0f, 0);
        }
    }

    // lights up the line that matches the song position and glides to it
    private void highlight(long positionMs) {
        if (synced.isEmpty() || lineViews.size() != synced.size()) return;
        int index = -1;
        for (int i = 0; i < synced.size(); i++) {
            if (synced.get(i).timeMs <= positionMs) index = i; else break;
        }
        if (index == current) return;
        current = index;
        applyStyles(index, true);
        if (index >= 0) glideTo(lineViews.get(index));
    }

    // smooth scroll so the line sits in the middle (waits while you are reading by hand)
    private void glideTo(final TextView tv) {
        if (SystemClock.uptimeMillis() < userScrollUntil) return;
        if (tv.getHeight() == 0) {   // not measured yet
            scroll.post(() -> glideTo(tv));
            return;
        }
        int target = Math.max(0, tv.getTop() + tv.getHeight() / 2 - scroll.getHeight() / 2);
        if (scrollAnim != null) scrollAnim.cancel();
        scrollAnim = ObjectAnimator.ofInt(scroll, "scrollY", scroll.getScrollY(), target);
        scrollAnim.setDuration(600);
        scrollAnim.setInterpolator(new DecelerateInterpolator(1.6f));
        scrollAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                autoScrolling = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                autoScrolling = false;
            }
        });
        scrollAnim.start();
    }

    // ---------- looking the song up online ----------
    private void search() {
        if (key == null || title.isEmpty()) {
            setPill("");
            showMessage("Lyrics are only for songs", "Podcasts and videos have no lyrics.", false);
            return;
        }
        final int my = ++token;
        final String k = key;
        final String t = title;
        final String a = artist;
        final long d = durationSec;
        clearLines();
        setPill("SEARCHING");
        showMessage("Finding lyrics\u2026", "", true);

        io.execute(() -> {
            final String result = LyricsFetcher.fetch(t, a, d);
            activity.runOnUiThread(() -> {
                if (!open || my != token) return;
                if (result != null) {
                    LyricsStore.save(activity, k, result);
                    display(result);
                } else {
                    clearLines();
                    setPill("NOT FOUND");
                    showMessage("No lyrics found",
                            "Check your internet, or find them on lrclib.net and paste them with Add / edit.", false);
                }
            });
        });
    }

    // opens lrclib.net in the browser
    private void openLrclib() {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://lrclib.net/")));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "No browser found on this phone", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- pasting your own lyrics ----------
    private void showEditor() {
        if (key == null) {
            Toast.makeText(activity, "Lyrics are only available for songs",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final String k = key;

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(20), dp(20), dp(20));
        DialogBg.apply(activity, box);

        TextView head = new TextView(activity);
        head.setText("Add lyrics");
        head.setTextSize(20);
        head.setTextColor(color(R.color.text_primary));
        box.addView(head);

        TextView tip = new TextView(activity);
        tip.setText("Paste the lyrics. Lines with time tags like [01:23.45] will light up in time with the song.");
        tip.setTextSize(13);
        tip.setTextColor(color(R.color.text_hint));
        LinearLayout.LayoutParams tp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(6);
        box.addView(tip, tp);

        final EditText et = new EditText(activity);
        et.setBackgroundResource(R.drawable.bg_input);
        et.setHint("Lyrics");
        et.setHintTextColor(color(R.color.text_hint));
        et.setTextColor(color(R.color.text_primary));
        et.setTextSize(15);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setMinLines(8);
        et.setMaxLines(12);
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        String saved = LyricsStore.load(activity, k);
        if (saved != null) et.setText(saved);
        LinearLayout.LayoutParams ep = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = dp(12);
        box.addView(et, ep);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = button("Cancel", color(R.color.accent_lavender));
        cancel.setBackgroundResource(R.drawable.bg_guest);
        TextView save = button("Save", color(R.color.button_text));
        save.setBackgroundResource(R.drawable.bg_button_gradient);
        row.addView(cancel, buttonParams(0));
        row.addView(save, buttonParams(10));
        LinearLayout.LayoutParams rp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(16);
        box.addView(row, rp);

        final AlertDialog dialog = new AlertDialog.Builder(activity).setView(box).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String text = et.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(activity, "Type or paste some lyrics first",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            LyricsStore.save(activity, k, text);
            dialog.dismiss();
            if (open && k.equals(key)) display(text);
        });
        dialog.show();
    }
}