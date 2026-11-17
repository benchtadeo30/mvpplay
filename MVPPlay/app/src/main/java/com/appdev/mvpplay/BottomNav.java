package com.appdev.mvpplay;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

// the bottom of the main screens: the mini player and the 4 tabs
public class BottomNav {

    public static final int HOME = 0, SEARCH = 1, LIBRARY = 2, PLAYLISTS = 3, NONE = -1;

    private final Activity activity;
    private final int selected;
    private final View miniPlayer;
    private final ImageView miniCover, miniPlay;
    private final TextView miniTitle, miniArtist;
    private boolean started = false;
    private String shownMediaId = null;

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onEvents(Player player, Player.Events events) {
            refreshMini();
        }
    };

    public BottomNav(Activity activity, int selected) {
        this.activity = activity;
        this.selected = selected;

        miniPlayer = activity.findViewById(R.id.miniPlayer);
        miniCover = activity.findViewById(R.id.miniCover);
        miniPlay = activity.findViewById(R.id.miniPlay);
        miniTitle = activity.findViewById(R.id.miniTitle);
        miniArtist = activity.findViewById(R.id.miniArtist);

        // the 4 tabs: the selected one is lavender and bold
        int[] tabs = {R.id.navHome, R.id.navSearch, R.id.navLibrary, R.id.navPlaylists};
        int[] icons = {R.id.navHomeIcon, R.id.navSearchIcon, R.id.navLibraryIcon, R.id.navPlaylistsIcon};
        int[] labels = {R.id.navHomeLabel, R.id.navSearchLabel, R.id.navLibraryLabel, R.id.navPlaylistsLabel};
        for (int i = 0; i < tabs.length; i++) {
            final int tab = i;
            boolean on = (i == selected);
            int active = ThemeStore.isSpotify(activity) ? Color.WHITE
                    : ContextCompat.getColor(activity, R.color.accent_lavender);
            int color = on ? active : ContextCompat.getColor(activity, R.color.text_hint);

            ImageViewCompat.setImageTintList(
                    (ImageView) activity.findViewById(icons[i]), ColorStateList.valueOf(color));
            TextView label = activity.findViewById(labels[i]);
            label.setTextColor(color);
            label.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);

            activity.findViewById(tabs[i]).setOnClickListener(v -> go(tab));
        }

        // mini player: tap opens Now Playing, the two buttons play/pause and skip
        // the gear icon in the top bar of every tab opens Settings
        View gear = activity.findViewById(R.id.btnSettings);
        if (gear != null) {
            gear.setOnClickListener(v ->
                    activity.startActivity(new Intent(activity, SettingsActivity.class)));
        }

        miniPlayer.setOnClickListener(v ->
                activity.startActivity(new Intent(activity, NowPlayingActivity.class)));
        setupSwipeAway();
        miniPlay.setOnClickListener(v -> {
            MediaController c = PlayerConnection.get();
            if (c == null) return;
            if (c.isPlaying()) c.pause(); else c.play();
        });
        activity.findViewById(R.id.miniPrev).setOnClickListener(v -> {
            MediaController c = PlayerConnection.get();
            if (c != null) c.seekToPrevious();
        });
        activity.findViewById(R.id.miniNext).setOnClickListener(v -> {
            MediaController c = PlayerConnection.get();
            if (c != null) c.seekToNext();
        });
    }

    // ---------- swipe the mini player away ----------
    private void setupSwipeAway() {
        final int slop = ViewConfiguration.get(activity).getScaledTouchSlop() * 2;
        final float[] downX = new float[1];
        final boolean[] dragging = {false};

        miniPlayer.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = e.getRawX();
                    dragging[0] = false;
                    return false;   // taps still work

                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - downX[0];
                    if (!dragging[0] && Math.abs(dx) > slop) {
                        dragging[0] = true;
                        v.setPressed(false);
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (dragging[0]) {
                        v.setTranslationX(dx);
                        v.setAlpha(1f - 0.7f * Math.min(1f, Math.abs(dx) / v.getWidth()));
                        return true;
                    }
                    return false;
                }

                case MotionEvent.ACTION_UP:
                    if (dragging[0]) {
                        dragging[0] = false;
                        float dx = v.getTranslationX();
                        if (Math.abs(dx) > v.getWidth() * 0.35f) {
                            dismissMini(dx > 0);
                        } else {
                            snapBack(v);
                        }
                        return true;
                    }
                    return false;

                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        dragging[0] = false;
                        snapBack(v);
                        return true;
                    }
                    return false;

                default:
                    return false;
            }
        });
    }

    private void snapBack(View v) {
        v.animate().translationX(0f).alpha(1f).setDuration(200).start();
    }

    // slides the bar off the screen, then stops the music and clears the queue
    private void dismissMini(boolean toRight) {
        final View v = miniPlayer;
        v.animate().translationX(toRight ? v.getWidth() : -v.getWidth()).alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    MediaController c = PlayerConnection.get();
                    if (c != null) {
                        c.stop();
                        c.clearMediaItems();
                    }
                    v.setVisibility(View.GONE);
                    v.setTranslationX(0f);   // ready for the next song
                    v.setAlpha(1f);
                    shownMediaId = null;
                }).start();
    }

    // call from the screen's onStart / onStop
    public void onStart() {
        started = true;
        PlayerConnection.connect(activity, c -> {
            if (!started) return;
            c.addListener(listener);
            refreshMini();
        });
    }

    public void onStop() {
        started = false;
        MediaController c = PlayerConnection.get();
        if (c != null) c.removeListener(listener);
    }

    private void go(int tab) {
        if (tab == selected) return;

        if (tab == HOME) {
            // back to the Home screen that is already open underneath
            Intent home = new Intent(activity, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(home);
            activity.overridePendingTransition(0, 0);
            return;
        }

        Class<?> target = (tab == SEARCH) ? SearchActivity.class
                : (tab == LIBRARY) ? LibraryActivity.class : PlaylistsActivity.class;
        activity.startActivity(new Intent(activity, target));
        activity.overridePendingTransition(0, 0);
        if (selected != HOME) activity.finish();   // tabs replace each other, Home stays below
    }

    // shows the song that is loaded in the player (hidden when nothing is loaded)
    private void refreshMini() {
        MediaController c = PlayerConnection.get();
        MediaItem item = (c != null) ? c.getCurrentMediaItem() : null;
        if (item == null) {
            miniPlayer.setVisibility(View.GONE);
            shownMediaId = null;
            return;
        }
        miniPlayer.setVisibility(View.VISIBLE);

        CharSequence title = item.mediaMetadata.title;
        CharSequence artist = item.mediaMetadata.artist;
        miniTitle.setText(title != null ? title : "Unknown");
        miniTitle.setSelected(true);   // a TextView only scrolls while it is "selected"
        miniArtist.setText(artist != null ? artist : "");
        miniPlay.setImageResource(c.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);

        if (!item.mediaId.equals(shownMediaId)) {
            shownMediaId = item.mediaId;
            bindCover(item);
        }
    }

    // the small cover: your picture if there is one, otherwise the song's gradient
    private void bindCover(MediaItem item) {
        float density = activity.getResources().getDisplayMetrics().density;

        int colorStart = 0xFF2B1B5E;
        int colorEnd = 0xFF7C3AED;
        Bundle extras = item.mediaMetadata.extras;
        if (extras != null) {
            colorStart = extras.getInt("colorStart", colorStart);
            colorEnd = extras.getInt("colorEnd", colorEnd);
        }
        GradientDrawable cover = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{colorStart, colorEnd});
        cover.setCornerRadius(density * 10);
        miniCover.setBackground(cover);

        Bitmap picture = null;
        int size = (int) (44 * density);
        String thumbPath = (extras != null) ? extras.getString("thumbPath") : null;
        if (thumbPath != null) {
            // a song you added: the picture saved in the app
            picture = Thumbs.file(thumbPath, size);
        } else {
            try {
                int resId = Integer.parseInt(item.mediaId);
                int thumbId = Thumbs.thumbForRaw(activity, resId);
                if (thumbId != 0) picture = Thumbs.get(activity, thumbId, size);
            } catch (NumberFormatException ignored) {
            }
        }
        miniCover.setImageBitmap(picture);
    }
}