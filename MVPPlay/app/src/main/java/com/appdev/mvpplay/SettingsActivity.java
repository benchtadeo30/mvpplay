package com.appdev.mvpplay;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.session.MediaController;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class SettingsActivity extends BaseActivity {

    private interface OnToggle {
        void onToggle(boolean on);
    }

    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
    private static final String[] SPEED_LABELS = {"0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x"};
    private static final String[] SORT_MODES = {
            AppSettings.SORT_ARTIST, AppSettings.SORT_TITLE, AppSettings.SORT_LENGTH};
    private static final String[] SORT_LABELS = {"Artist", "Title", "Length"};

    private BottomNav bottomNav;
    private LinearLayout content;
    private TextView tvName, tvSpeed, tvSort, tvTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        WindowInsetsControllerCompat bars =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);

        TRACK_ON = ContextCompat.getColor(this, R.color.toggle_on);
        TRACK_OFF = ContextCompat.getColor(this, R.color.toggle_off);
        content = findViewById(R.id.settingsContent);
        buildScreen();
        bottomNav = new BottomNav(this, BottomNav.NONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bottomNav.onStart();
    }

    @Override
    protected void onStop() {
        bottomNav.onStop();
        super.onStop();
    }

    // ---------- the rows ----------
    private void buildScreen() {
        LinearLayout profile = section("Profile");
        tvName = addValueRow(profile, "Your name", userName(), v -> askName());

        LinearLayout look = section("Look");
        tvTheme = addValueRow(look, "Theme", ThemeStore.label(this), v -> chooseTheme());
        addToggleRow(look, "Show the welcome card", AppSettings.showWelcome(this),
                on -> AppSettings.setShowWelcome(this, on));

        LinearLayout playback = section("Playback");
        tvSpeed = addValueRow(playback, "Podcast speed", speedLabel(), v -> chooseSpeed());
        addToggleRow(playback, "Resume the last song", AppSettings.resumeLast(this),
                on -> AppSettings.setResumeLast(this, on));
        addToggleRow(playback, "Floating video window", AppSettings.floatingWindow(this),
                on -> AppSettings.setFloatingWindow(this, on));

        LinearLayout library = section("Library");
        tvSort = addValueRow(library, "Sort library", sortLabel(), v -> chooseSort());
        addPlainRow(library, "Restore deleted songs and episodes", false,
                v -> confirmRestoreHidden());
        addPlainRow(library, "Clear favorites", false, v -> confirmClearFavorites());
        addPlainRow(library, "Delete my custom playlists", true, v -> confirmDeletePlaylists());

        LinearLayout about = section("About");
        addValueRow(about, "MVPPlay", "Version " + versionName(), null);
    }

    // ---------- your name (same name as the welcome card) ----------
    private SharedPreferences welcomePrefs() {
        return getSharedPreferences("welcome", MODE_PRIVATE);
    }

    private String userName() {
        String name = welcomePrefs().getString("name", "").trim();
        return name.isEmpty() ? "User" : name;
    }

    private void askName() {
        String saved = welcomePrefs().getString("name", "");

        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_name);

        EditText input = dialog.findViewById(R.id.etDialogName);
        TextView btnCancel = dialog.findViewById(R.id.btnDialogCancel);
        TextView btnSave = dialog.findViewById(R.id.btnDialogSave);

        input.setText(saved);
        input.setSelection(saved.length());
        btnCancel.setText("Cancel");

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            welcomePrefs().edit()
                    .putString("name", input.getText().toString().trim())
                    .putBoolean("asked", true)
                    .apply();
            tvName.setText(userName());
            dialog.dismiss();
        });
        // the keyboard's Done key works like the Save button
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                btnSave.performClick();
                return true;
            }
            return false;
        });

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            // transparent window so the rounded corners show, card is 88% of the screen width
            window.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setDimAmount(0.6f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        dialog.show();
        input.requestFocus();
    }

    // ---------- theme ----------
    private void chooseTheme() {
        final int before = ThemeStore.get(this);
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_theme, null);
        sheet.setContentView(view);
        sheet.setOnShowListener(d -> {
            View s = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (s != null) {
                s.setBackgroundColor(Color.TRANSPARENT);   // our own rounded background shows
                BottomSheetBehavior.from(s).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        // preview colors: background, card, accent, text (fixed, so each preview
        // always shows its own theme whatever theme is on now)
        int[][] previews = {
                {0xFF0A0E19, 0xFF141725, 0xFFA78BFA, 0xFFFFFFFF},   // Dark
                {0xFFF6F6F7, 0xFFFFFFFF, 0xFF7C5CE6, 0xFF16171B},   // Light
                {0xFF121212, 0xFF282828, 0xFF1DB954, 0xFFFFFFFF},   // Spotify
                {0xFF0D0A12, 0xFF261B30, 0xFFFF7518, 0xFFFFF4E8},   // Halloween
                {0xFF0B1410, 0xFF1C2E25, 0xFFE5384B, 0xFFFFF8F0},   // Christmas
                {0xFFFFF9EE, 0xFFFFFFFF, 0xFF00ACC1, 0xFF12303A},   // Summer
                {0xFF140A2E, 0x40FFFFFF, 0xFF8B7BFF, 0xFFFFFFFF}    // Glass
        };

        LinearLayout grid = view.findViewById(R.id.themeCards);
        LinearLayout row = null;
        for (int i = 0; i < previews.length; i++) {
            final int index = ThemeStore.ORDER[i];   // themes appear in the picker order
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.bottomMargin = dp(8);
                grid.addView(row, rp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(buildThemeCard(index, previews[index], index == before, v -> {
                sheet.dismiss();
                if (index != before) applyTheme(index);
            }), lp);
        }
        // an odd number of cards: leave room beside the last one so it keeps its size
        if (previews.length % 2 == 1 && row != null) {
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, 1, 1f);
            sp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(new View(this), sp);
        }
        sheet.show();
    }

    // one card: a small mock screen, the theme name and a check when it is on
    private View buildThemeCard(int index, int[] c, boolean selected, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(12));
        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(dp(18));
        frame.setColor(ContextCompat.getColor(this, R.color.surface_field));
        frame.setStroke(dp(2), selected ? c[2] : Color.TRANSPARENT);
        card.setBackground(frame);
        card.setClickable(true);
        card.setOnClickListener(click);
        Anim.press(card);

        // the mock screen
        FrameLayout preview = new FrameLayout(this);
        preview.setBackground(round(c[0], 12));
        card.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(104)));

        FrameLayout.LayoutParams title = new FrameLayout.LayoutParams(dp(34), dp(6));
        title.setMargins(dp(10), dp(12), 0, 0);
        preview.addView(roundView((c[3] & 0x00FFFFFF) | 0xB0000000, 3), title);

        for (int r = 0; r < 2; r++) {
            FrameLayout.LayoutParams bar = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(18));
            bar.setMargins(dp(10), dp(30 + r * 24), dp(10), 0);
            preview.addView(roundView(c[1], 6), bar);
        }

        FrameLayout.LayoutParams pill = new FrameLayout.LayoutParams(dp(42), dp(14));
        pill.setMargins(dp(10), dp(80), 0, 0);
        preview.addView(roundView(c[2], 7), pill);

        if (selected) {
            TextView check = new TextView(this);
            check.setText("✓");
            check.setTextSize(11);
            check.setTypeface(null, android.graphics.Typeface.BOLD);
            check.setGravity(Gravity.CENTER);
            check.setTextColor((index == 2 || index == 3) ? Color.BLACK : Color.WHITE);
            check.setBackground(round(c[2], 10));
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(20), dp(20));
            cp.gravity = Gravity.TOP | Gravity.END;
            cp.setMargins(0, dp(6), dp(6), 0);
            preview.addView(check, cp);
        }

        TextView name = new TextView(this);
        name.setText(ThemeStore.LABELS[index]);
        name.setTextSize(14);
        name.setTypeface(null, selected
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        name.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.text_primary : R.color.text_secondary));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(10);
        card.addView(name, np);
        return card;
    }

    // a plain View with a rounded color (used for the mock screen pieces)
    private View roundView(int color, int radiusDp) {
        View v = new View(this);
        v.setBackground(round(color, radiusDp));
        return v;
    }
    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    // saves the theme, shows the running dog, then redraws the app
    private void applyTheme(final int which) {
        boolean wasLight = ThemeStore.isLight(this);
        ThemeStore.set(this, which);
        final boolean nowLight = ThemeStore.isLight(this);
        AppSettings.setDarkMode(this, !nowLight);   // keeps the old setting in step

        final LoadingDialog loading = new LoadingDialog(this,
                which == ThemeStore.HALLOWEEN
                        ? "Summoning the spooky theme… \uD83C\uDF83"
                        : which == ThemeStore.SUMMER
                          ? "Heading to the beach… \u2600\uFE0F"
                          : "Switching to " + ThemeStore.LABELS[which] + " theme…");
        loading.show();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            loading.dismiss();
            if (isFinishing() || isDestroyed()) return;
            AppCompatDelegate.setDefaultNightMode(nowLight
                    ? AppCompatDelegate.MODE_NIGHT_NO
                    : AppCompatDelegate.MODE_NIGHT_YES);
            // Dark <-> Spotify keeps night mode, so redraw by hand
            if (wasLight == nowLight) recreate();
        }, 1400);
    }

    // ---------- podcast speed ----------
    private String speedLabel() {
        float current = AppSettings.podcastSpeed(this);
        for (int i = 0; i < SPEEDS.length; i++) {
            if (SPEEDS[i] == current) return SPEED_LABELS[i];
        }
        return "1x";
    }

    private void chooseSpeed() {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);

        // the sheet itself: rounded on top, in the card color of the current theme
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(28));
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(ContextCompat.getColor(this, R.color.surface_card));
        float corner = dp(24);
        boxBg.setCornerRadii(new float[]{corner, corner, corner, corner, 0, 0, 0, 0});
        box.setBackground(boxBg);

        // little drag handle
        int handleColor = (ContextCompat.getColor(this, R.color.text_hint) & 0x00FFFFFF) | 0x66000000;
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(40), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(16);
        box.addView(roundView(handleColor, 2), handleParams);

        // title and short line on the left, the current speed big on the right
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Podcast speed");
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        titles.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("How fast podcast episodes play.");
        subtitle.setTextSize(13);
        subtitle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(4);
        titles.addView(subtitle, subParams);

        header.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView currentText = new TextView(this);
        currentText.setTextSize(28);
        currentText.setTypeface(null, android.graphics.Typeface.BOLD);
        currentText.setTextColor(ContextCompat.getColor(this, R.color.accent_purple));
        header.addView(currentText);

        box.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // which chip is on now (if the saved speed is not in the list, 1x)
        float current = AppSettings.podcastSpeed(this);
        int selected = 2;
        for (int i = 0; i < SPEEDS.length; i++) {
            if (SPEEDS[i] == current) selected = i;
        }
        currentText.setText(SPEED_LABELS[selected]);

        // the row of chips
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(20);
        box.addView(chipRow, rowParams);

        final TextView[] chips = new TextView[SPEEDS.length];
        for (int i = 0; i < SPEEDS.length; i++) {
            final int index = i;
            TextView chip = new TextView(this);
            chip.setText(SPEED_LABELS[i]);
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setClickable(true);
            chip.setFocusable(true);
            Anim.press(chip);
            chips[i] = chip;

            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
            chipParams.setMargins(dp(3), 0, dp(3), 0);
            chipRow.addView(chip, chipParams);

            chip.setOnClickListener(v -> {
                AppSettings.setPodcastSpeed(this, SPEEDS[index]);
                // clears any speed picked on Now Playing, so the Settings speed is used for podcasts
                SpeedStore.set(this, -1f);
                tvSpeed.setText(SPEED_LABELS[index]);
                applySpeedNow(SPEEDS[index]);
                currentText.setText(SPEED_LABELS[index]);
                paintSpeedChips(chips, index);
                // a short moment so the new choice can be seen, then the sheet closes
                new Handler(Looper.getMainLooper()).postDelayed(sheet::dismiss, 220);
            });
        }
        paintSpeedChips(chips, selected);

        sheet.setContentView(box);
        sheet.setOnShowListener(d -> {
            View s = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (s != null) {
                s.setBackgroundColor(Color.TRANSPARENT);   // our own rounded background shows
                BottomSheetBehavior.from(s).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        sheet.show();
    }

    // the chosen chip gets the accent look, the others the normal chip look (both follow the theme)
    private void paintSpeedChips(TextView[] chips, int selected) {
        for (int i = 0; i < chips.length; i++) {
            boolean on = (i == selected);
            chips[i].setBackgroundResource(on ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chips[i].setTextColor(ContextCompat.getColor(this,
                    on ? R.color.button_text : R.color.text_secondary));
            chips[i].setTypeface(null, on
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    // if a podcast is playing right now, the new speed starts at once
    private void applySpeedNow(float speed) {
        MediaController c = PlayerConnection.get();
        MediaItem item = (c != null) ? c.getCurrentMediaItem() : null;
        if (item == null) return;
        try {
            int resId = Integer.parseInt(item.mediaId);
            boolean podcast = CustomPodcastStore.isEpisode(resId)
                    || getResources().getResourceEntryName(resId).startsWith("podcast_");
            if (podcast) c.setPlaybackSpeed(speed);
        } catch (Exception ignored) {
        }
    }

    // ---------- sort songs ----------
    private String sortLabel() {
        String mode = AppSettings.sortMode(this);
        for (int i = 0; i < SORT_MODES.length; i++) {
            if (SORT_MODES[i].equals(mode)) return SORT_LABELS[i];
        }
        return SORT_LABELS[0];
    }

    private void chooseSort() {
        String mode = AppSettings.sortMode(this);
        int checked = 0;
        for (int i = 0; i < SORT_MODES.length; i++) {
            if (SORT_MODES[i].equals(mode)) checked = i;
        }
        ModernDialog.choose(this, "Sort library by", SORT_LABELS, checked, which -> {
            AppSettings.setSortMode(this, SORT_MODES[which]);
            tvSort.setText(SORT_LABELS[which]);
        });
    }

    // ---------- clear favorites / delete playlists ----------
    private void confirmClearFavorites() {
        ModernDialog.confirm(this, "Clear favorites?",
                "All hearts are removed. Your songs stay in the app.",
                "Clear", false, () -> {
                    FavoritesStore.clear(this);
                    Toast.makeText(this, "Favorites cleared", Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmRestoreHidden() {
        int count = HiddenSongStore.count(this);
        if (count == 0) {
            Toast.makeText(this, "Nothing to restore", Toast.LENGTH_SHORT).show();
            return;
        }
        ModernDialog.confirm(this,
                "Restore " + count + (count == 1 ? " item?" : " items?"),
                "The official songs and podcast episodes you deleted will come back.",
                "Restore", false, () -> {
                    HiddenSongStore.restoreAll(this);
                    Toast.makeText(this, "Restored", Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmDeletePlaylists() {
        ModernDialog.confirm(this, "Delete all your playlists?",
                "Your songs stay in the app. Only the playlists you made are removed.",
                "Delete", true, () -> {
                    for (String name : CustomPlaylistStore.names(this)) {
                        CustomPlaylistStore.delete(this, name);
                    }
                    Toast.makeText(this, "Playlists deleted", Toast.LENGTH_SHORT).show();
                });
    }

    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName != null ? info.versionName : "1.0";
        } catch (Exception e) {
            return "1.0";
        }
    }

    // ---------- building blocks ----------
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    // a small gray title with a card under it; rows are added to the card
    private LinearLayout section(String label) {
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(12);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.setMargins(dp(2), dp(20), 0, dp(6));
        content.addView(title, tp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        content.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private LinearLayout baseRow(LinearLayout card, String title, boolean danger) {
        if (card.getChildCount() > 0) {
            View divider = new View(this);
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider));
            card.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));

        TextView text = new TextView(this);
        text.setText(title);
        text.setTextSize(15);
        text.setTextColor(danger ? 0xFFF472B6
                : ContextCompat.getColor(this, R.color.text_primary));
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    // a row with a value on the right (and an arrow when it can be tapped)
    private TextView addValueRow(LinearLayout card, String title, String value,
                                 View.OnClickListener click) {
        LinearLayout row = baseRow(card, title, false);
        TextView valueText = new TextView(this);
        valueText.setText(value);
        valueText.setTextSize(13);
        valueText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        row.addView(valueText);

        if (click != null) {
            ImageView arrow = new ImageView(this);
            arrow.setImageResource(R.drawable.ic_chevron_right);
            arrow.setColorFilter(ContextCompat.getColor(this, R.color.text_hint));
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(18), dp(18));
            ap.setMarginStart(dp(4));
            row.addView(arrow, ap);
            row.setOnClickListener(click);
        }
        return valueText;
    }

    private void addPlainRow(LinearLayout card, String title, boolean danger,
                             View.OnClickListener click) {
        baseRow(card, title, danger).setOnClickListener(click);
    }

    private int TRACK_ON;
    private int TRACK_OFF;

    // a row with an on/off switch; tapping anywhere on the row flips it smoothly
    private void addToggleRow(LinearLayout card, String title, boolean on, OnToggle listener) {
        LinearLayout row = baseRow(card, title, false);

        final GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(dp(10));
        final FrameLayout toggle = new FrameLayout(this);
        toggle.setBackground(track);

        final View knob = new View(this);
        GradientDrawable knobShape = new GradientDrawable();
        knobShape.setShape(GradientDrawable.OVAL);
        knobShape.setColor(Color.WHITE);
        knob.setBackground(knobShape);
        FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(dp(16), dp(16));
        kp.gravity = Gravity.CENTER_VERTICAL;
        kp.setMargins(dp(2), 0, 0, 0);
        toggle.addView(knob, kp);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(36), dp(20)));

        // the starting look, without animation
        final boolean[] state = {on};
        final int[] shown = {on ? TRACK_ON : TRACK_OFF};
        final ValueAnimator[] colorAnim = {null};
        track.setColor(shown[0]);
        knob.setTranslationX(on ? dp(16) : 0f);

        row.setOnClickListener(v -> {
            state[0] = !state[0];
            boolean now = state[0];

            // the knob slides across
            knob.animate().cancel();
            knob.animate().translationX(now ? dp(16) : 0f)
                    .setDuration(220)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();

            // the track fades between gray and purple
            if (colorAnim[0] != null) colorAnim[0].cancel();
            ValueAnimator colors = ValueAnimator.ofObject(new ArgbEvaluator(),
                    shown[0], now ? TRACK_ON : TRACK_OFF);
            colors.setDuration(220);
            colors.addUpdateListener(a -> {
                shown[0] = (int) a.getAnimatedValue();
                track.setColor(shown[0]);
            });
            colors.start();
            colorAnim[0] = colors;

            listener.onToggle(now);
        });
    }

    private void paintToggle(FrameLayout toggle, View knob, boolean on) {
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(dp(10));
        track.setColor(on ? 0xFF7C5CFF : 0xFF2A3050);
        toggle.setBackground(track);

        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) knob.getLayoutParams();
        p.gravity = Gravity.CENTER_VERTICAL | (on ? Gravity.END : Gravity.START);
        knob.setLayoutParams(p);
    }
}