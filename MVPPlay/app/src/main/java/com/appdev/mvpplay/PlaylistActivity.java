package com.appdev.mvpplay;

import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import androidx.core.widget.ImageViewCompat;
import android.animation.ObjectAnimator;
import android.animation.Keyframe;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.content.Intent;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.EditText;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlaylistActivity extends BaseActivity {

    private String playlistName;
    private Playlist playlist;
    private List<Song> allSongs;
    private boolean selecting = false;

    private PlaylistSongAdapter adapter;
    private PlaylistSongAdapter.Listener songListener;
    private ItemTouchHelper touchHelper;
    private RecyclerView rvSongs;
    private ImageView ivHero;
    private TextView tvPlayLabel, tvSelect, tvSaveLabel, tvEmptySongs;
    private View btnSaveSelected, rowManage;
    private View[] bars;
    private final List<ObjectAnimator> barAnimators = new ArrayList<>();

    private View[] rings;      // the 4 round circles
    private View[] ripples;    // the 2 rings that spread outward
    private final List<ObjectAnimator> waveAnimators = new ArrayList<>();
    private boolean waveOn = false;
    private BokehView bokeh;   // the floating lights (gold playlist only)
    private GoldRingView goldRing;   // the spinning light around the play button

    // opens the phone's picture picker when the user changes a cover
    private final ActivityResultLauncher<String[]> coverPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) saveCover(uri);
            });

    // true for playlists you can add to, remove from and reorder
    private boolean editable() {
        return playlist != null && (playlist.custom || playlist.special);
    }

    private Uri importThumb;
    private byte[] importArt;
    private ImageView importThumbView;

    // step 1 of import: choose the audio file from the phone
    private final ActivityResultLauncher<String[]> audioPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) showImportForm(uri);
            });

    // optional: choose a thumbnail picture
    private final ActivityResultLauncher<String[]> thumbPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && importThumbView != null) {
                    importThumb = uri;
                    importThumbView.setImageURI(uri);
                }
            });

    // reacts to anything that changes in the background player
    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            refreshPlayback();
        }

        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            refreshPlayback();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);

        WindowInsetsControllerCompat bar =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bar.setAppearanceLightStatusBars(false);
        bar.setAppearanceLightNavigationBars(false);

        playlistName = getIntent().getStringExtra("name");
        allSongs = MusicLibrary.load(this);

        // views
        rvSongs = findViewById(R.id.rvSongs);
        ivHero = findViewById(R.id.ivHero);
        tvPlayLabel = findViewById(R.id.tvPlayLabel);
        tvSelect = findViewById(R.id.tvSelect);
        tvSaveLabel = findViewById(R.id.tvSaveLabel);
        tvEmptySongs = findViewById(R.id.tvEmptySongs);
        btnSaveSelected = findViewById(R.id.btnSaveSelected);
        rowManage = findViewById(R.id.rowManage);
        bars = new View[]{findViewById(R.id.bar1), findViewById(R.id.bar2),
                findViewById(R.id.bar3), findViewById(R.id.bar4), findViewById(R.id.bar5)};
        rings = new View[]{findViewById(R.id.heroGlow), findViewById(R.id.heroRing)};
        ripples = new View[]{findViewById(R.id.ripple1), findViewById(R.id.ripple2)};

        rvSongs.setLayoutManager(new LinearLayoutManager(this));
        rvSongs.setNestedScrollingEnabled(false);

        // what each tap on a song row does
        songListener = new PlaylistSongAdapter.Listener() {
            @Override
            public void onRowClick(int position) {
                if (selecting) {
                    adapter.toggle(position);
                    updateSaveLabel();
                    return;
                }
                // play the song (if it is not the current one) and open Now Playing
                Song song = playlist.songs.get(position);
                if (!(isPlaylistActive() && isCurrentSong(song))) {
                    startQueue(position);
                }
                startActivity(new Intent(PlaylistActivity.this, NowPlayingActivity.class));
                Anim.slideUpOpen(PlaylistActivity.this);
            }

            @Override
            public void onRowLongClick(int position) {
                if (selecting) {
                    adapter.toggle(position);
                    updateSaveLabel();
                    return;
                }
                // long press: Save to Playlist, with a Delete row for every song
                final Song song = playlist.songs.get(position);
                PlaylistDialogs.showSaveToPlaylist(PlaylistActivity.this, song,
                        PlaylistActivity.this::reloadPlaylist,
                        () -> confirmDeleteSong(song));
            }

            @Override
            public void onPlayClick(int position) {
                // round button: play or pause, stay on this page
                Song song = playlist.songs.get(position);
                MediaController c = PlayerConnection.get();
                if (isPlaylistActive() && isCurrentSong(song) && c != null) {
                    if (c.isPlaying()) c.pause(); else c.play();
                } else {
                    startQueue(position);
                }
            }

            @Override
            public void onRemoveClick(int position) {
                // the music file itself is never touched
                Song song = playlist.songs.get(position);
                if (playlist.special) {
                    List<Song> copy = new ArrayList<>(playlist.songs);
                    copy.remove(position);
                    SpecialPlaylistStore.saveSongs(PlaylistActivity.this, copy);
                } else {
                    CustomPlaylistStore.removeSong(PlaylistActivity.this, playlist.name, song);
                }
                Toast.makeText(PlaylistActivity.this,
                        "Removed from \"" + playlist.name + "\"", Toast.LENGTH_SHORT).show();
                reloadPlaylist();
            }
        };

        // buttons
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnHero).setOnClickListener(v -> onPlaylistButton());
        findViewById(R.id.btnPlayAll).setOnClickListener(v -> onPlaylistButton());
        tvSelect.setOnClickListener(v -> setSelecting(!selecting));
        btnSaveSelected.setOnClickListener(v -> saveSelected());

        findViewById(R.id.btnRename).setOnClickListener(v ->
                PlaylistDialogs.showRename(this, playlist.name, playlist.special, newName -> {
                    // keep the "playing from" name in step with the new name
                    if (playlist.name.equals(MainActivity.activePlaylistName)) {
                        MainActivity.activePlaylistName = newName;
                    }
                    playlistName = newName;
                    reloadPlaylist();
                }));
        findViewById(R.id.btnDelete).setOnClickListener(v ->
                PlaylistDialogs.showDelete(this, playlist.name, this::finish));
        findViewById(R.id.btnCover).setOnClickListener(v -> showCoverMenu());
        findViewById(R.id.btnAddSong).setOnClickListener(v -> showAddSongs());

        if (!loadPlaylist()) {
            finish();
            return;
        }
        fillInfo();
        bindSongList();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // the playlist may have changed on another screen (for example from Now Playing)
        if (playlist != null) {
            reloadPlaylist();
        }
        PlayerConnection.connect(this, c -> {
            c.addListener(playerListener);
            refreshPlayback();
        });
    }

    @Override
    protected void onStop() {
        MediaController c = PlayerConnection.get();
        if (c != null) {
            c.removeListener(playerListener);
        }
        animateBars(false);
        animateWave(false);
        super.onStop();
    }

    // ---------- loading ----------
    private boolean loadPlaylist() {
        // an artist page: all the songs of one artist (opened from the Artists tab)
        if (getIntent().getBooleanExtra("artist", false)) {
            List<Song> songs = new ArrayList<>();
            for (Song s : MusicLibrary.loadWithCustom(this)) {
                if (s.artist.equals(playlistName)) songs.add(s);
            }
            if (songs.isEmpty()) return false;
            playlist = new Playlist(playlistName,
                    songs.size() == 1 ? "1 song" : songs.size() + " songs",
                    "ARTIST", songs, 0xFF2B1B5E, 0xFF7C3AED, false, false);
            return true;
        }
        // the special gold playlist (its name can change, so it is found by this flag)
        if (getIntent().getBooleanExtra("special", false)) {
            Playlist special = PlaylistLibrary.buildSpecial(this, allSongs);
            if (special == null) return false;
            List<Song> saved = SpecialPlaylistStore.applyOverride(this,
                    MusicLibrary.loadWithCustom(this));
            if (saved != null) {
                special.songs.clear();
                special.songs.addAll(saved);
            }
            playlist = special;
            return true;
        }

        List<Playlist> everything = new ArrayList<>(PlaylistLibrary.build(allSongs));
        everything.addAll(CustomPlaylistStore.load(this, MusicLibrary.loadWithCustom(this)));
        for (Playlist p : everything) {
            if (p.name.equals(playlistName)) {
                playlist = p;
                return true;
            }
        }
        return false;
    }

    // reads the playlist again and redraws the page
    private void reloadPlaylist() {
        allSongs = MusicLibrary.load(this);
        if (!loadPlaylist()) {
            finish();
            return;
        }
        fillInfo();
        bindSongList();
    }

    private void bindSongList() {
        selecting = false;
        tvSelect.setText("SELECT");
        btnSaveSelected.setVisibility(View.GONE);

        adapter = new PlaylistSongAdapter(playlist.songs, songListener);
        adapter.setRemovable(editable());
        adapter.setGlass(playlist.special);
        rvSongs.setAdapter(adapter);

        // drag to reorder (only for playlists you made)
        if (touchHelper != null) touchHelper.attachToRecyclerView(null);
        touchHelper = null;
        adapter.setReorderable(editable());
        if (editable()) {
            touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override
                public boolean isLongPressDragEnabled() {
                    return false;   // only the handle starts a drag
                }

                @Override
                public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder from,
                                      RecyclerView.ViewHolder to) {
                    return adapter.onMove(from.getBindingAdapterPosition(),
                            to.getBindingAdapterPosition());
                }

                @Override
                public void onSwiped(RecyclerView.ViewHolder holder, int direction) {
                }

                @Override
                public void clearView(RecyclerView rv, RecyclerView.ViewHolder holder) {
                    super.clearView(rv, holder);
                    holder.itemView.setAlpha(1f);
                    saveOrder();   // the row was dropped
                }

                @Override
                public void onSelectedChanged(RecyclerView.ViewHolder holder, int state) {
                    super.onSelectedChanged(holder, state);
                    if (holder != null && state == ItemTouchHelper.ACTION_STATE_DRAG) {
                        holder.itemView.setAlpha(0.85f);   // the lifted row looks lifted
                    }
                }
            });
            touchHelper.attachToRecyclerView(rvSongs);
            adapter.setTouchHelper(touchHelper);
        }

        boolean empty = playlist.songs.isEmpty();
        tvEmptySongs.setVisibility(empty ? View.VISIBLE : View.GONE);
        tvSelect.setVisibility(empty ? View.GONE : View.VISIBLE);
        refreshPlayback();
    }

    // ---------- the real info of this playlist ----------
    private void fillInfo() {
        int maxKbps = maxKbps(playlist.songs);
        long totalMinutes = 0;
        for (Song s : playlist.songs) totalMinutes += s.durationMs / 60000;
        totalMinutes = Math.max(1, totalMinutes);
        boolean empty = playlist.songs.isEmpty();

        ((TextView) findViewById(R.id.tvTagLeft)).setText(
                maxKbps > 0 ? "UP TO " + maxKbps + " KBPS" : "LOCAL AUDIO");
        ((TextView) findViewById(R.id.tvTagRight)).setText(playlist.stats());
        ((TextView) findViewById(R.id.tvKicker)).setText(playlist.tag);
        ((TextView) findViewById(R.id.tvSub)).setText(playlist.subtitle);

        ((TextView) findViewById(R.id.tvCard1Title)).setText(
                playlist.songs.size() == 1 ? "1 track" : playlist.songs.size() + " tracks");
        ((TextView) findViewById(R.id.tvCard1Sub)).setText(
                empty ? "Nothing yet" : "Total " + totalMinutes + " min");
        ((TextView) findViewById(R.id.tvCard2Title)).setText(
                maxKbps > 0 ? maxKbps + " KBPS" : "Local files");
        ((TextView) findViewById(R.id.tvCard2Sub)).setText("Highest quality");

        // Rename and Delete exist only for playlists you made
        // every playlist can get its own cover; only your own playlists (and the gold one)
        // can be renamed, and only your own can be deleted
        boolean artistPage = getIntent().getBooleanExtra("artist", false);
        rowManage.setVisibility(artistPage ? View.GONE : View.VISIBLE);
        findViewById(R.id.btnRename).setVisibility(
                playlist.custom || playlist.special ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnDelete).setVisibility(
                (playlist.custom && !playlist.special) ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnAddSong).setVisibility(editable() ? View.VISIBLE : View.GONE);

        // the last word of the name gets the purple-to-cyan gradient
        // (the gold playlist gets a gold gradient on the whole name)
        String text = playlist.name;
        GradientSpan.startColor = playlist.special ? 0xFFFFF1B8
                : ContextCompat.getColor(this, R.color.accent_purple);
        GradientSpan.endColor = playlist.special ? 0xFFFFB92E
                : ContextCompat.getColor(this, R.color.accent_cyan_light);
        SpannableString headline = new SpannableString(text);
        int start = playlist.special ? 0 : text.lastIndexOf(' ') + 1;
        headline.setSpan(new GradientSpan(text.length() - start), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((TextView) findViewById(R.id.tvHeadline)).setText(headline);
        applyGoldLook();
    }

    // the gold look of the special playlist (the normal look is already in the layout)
    private void applyGoldLook() {
        if (!playlist.special) return;
        float d = getResources().getDisplayMetrics().density;
        applyGoldBackground(d);

        // soft gold glow behind the button
        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(78 * d);
        glow.setColors(new int[]{0x99FFD54F, 0x00FFD54F});
        findViewById(R.id.heroGlow).setBackground(glow);

        // the button itself: a gold disc with a dark play icon and a shine
        GradientDrawable disc = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF8A6208, 0xFFD9A520, 0xFFFFE27A, 0xFFD9A520, 0xFF8A6208});
        disc.setShape(GradientDrawable.OVAL);
        disc.setStroke((int) (2 * d), 0xFFFFE9A3);
        findViewById(R.id.heroRing).setBackground(disc);
        ImageViewCompat.setImageTintList(ivHero, ColorStateList.valueOf(GoldStyle.TEXT_DARK));
        ShineView heroShine = findViewById(R.id.heroShine);
        heroShine.setCorner(56 * d);
        heroShine.setVisibility(View.VISIBLE);

        // the rings that spread outward are gold too
        for (View ripple : ripples) {
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(Color.TRANSPARENT);
            ring.setStroke((int) (2 * d), 0xFFFFD54F);
            ripple.setBackground(ring);
        }

        // equalizer bars and the small label above the headline
        for (View bar : bars) bar.setBackgroundTintList(ColorStateList.valueOf(0xFFFFD54F));
        polishGoldText(d);
        addGoldRing(d);

        // the Play Playlist button: gold with a shine
        findViewById(R.id.btnPlayAll).setBackground(GoldStyle.background(this, 26));
        tvPlayLabel.setTextColor(GoldStyle.TEXT_DARK);
        ImageViewCompat.setImageTintList((ImageView) findViewById(R.id.ivPlayArrow),
                ColorStateList.valueOf(GoldStyle.TEXT_DARK));
        ShineView playShine = findViewById(R.id.playShine);
        playShine.setCorner(26 * d);
        playShine.setVisibility(View.VISIBLE);
    }

    // a warm "memories" background (gold playlist only)
    private void applyGoldBackground(float d) {
        ViewGroup content = findViewById(android.R.id.content);

        // layer 1: deep night with a warm amber bottom
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF05070D, 0xFF120C08, 0xFF2A1708});

        // layer 2: golden glow behind the play button
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.5f, 0.17f);
        glow.setGradientRadius(380 * d);
        glow.setColors(new int[]{0x66FFC83D, 0x00FFC83D});

        // layer 3: candlelight at the bottom
        GradientDrawable warm = new GradientDrawable();
        warm.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        warm.setGradientCenter(0.5f, 1f);
        warm.setGradientRadius(420 * d);
        warm.setColors(new int[]{0x44FF9A3C, 0x00FF9A3C});

        content.setBackground(new LayerDrawable(new Drawable[]{base, glow, warm}));
        findViewById(R.id.playlistRoot).setBackgroundColor(Color.TRANSPARENT);

        // the photo, lights and sparkles sit behind everything (added only once)
        if (bokeh == null) {
            bokeh = new BokehView(this);
            bokeh.setPhoto(loadTinyPhoto());
            content.addView(bokeh, 0, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // the two info cards become glassy gold
        glassCard(R.id.cardInfo1, d);
        glassCard(R.id.cardInfo2, d);
    }

    private void glassCard(int id, float d) {
        View card = findViewById(id);
        if (card == null) return;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x33FFFFFF, 0x14FFC83D});
        g.setCornerRadius(20 * d);
        g.setStroke((int) d, 0x55FFD54F);
        card.setBackground(g);
    }

    // a tiny copy of benjieplaythumb; stretched later, it looks softly blurred
    private Bitmap loadTinyPhoto() {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4;
            Bitmap b = BitmapFactory.decodeResource(getResources(),
                    R.drawable.benjieplaythumb, o);
            if (b == null) return null;
            int w = 36;
            int h = Math.max(1, w * b.getHeight() / b.getWidth());
            return Bitmap.createScaledBitmap(b, w, h, true);
        } catch (Throwable e) {
            return null;
        }
    }

    // warm cream and gold text that is easy to read on the glowing background
    private void polishGoldText(float d) {
        TextView kicker = findViewById(R.id.tvKicker);
        kicker.setText("\u2726  SPECIAL  \u2726");
        kicker.setTextColor(0xFFFFE9A3);
        kicker.setLetterSpacing(0.3f);
        kicker.setShadowLayer(10 * d, 0, 0, 0xFFFFC83D);

        ((TextView) findViewById(R.id.tvHeadline))
                .setShadowLayer(16 * d, 0, 3 * d, 0xCC000000);

        TextView sub = findViewById(R.id.tvSub);
        sub.setTextColor(0xFFFFF3D6);
        sub.setShadowLayer(8 * d, 0, 2 * d, 0xAA000000);

        ((TextView) findViewById(R.id.tvCard1Title)).setTextColor(0xFFFFFFFF);
        ((TextView) findViewById(R.id.tvCard2Title)).setTextColor(0xFFFFFFFF);
        ((TextView) findViewById(R.id.tvCard1Sub)).setTextColor(0xFFFFE3A8);
        ((TextView) findViewById(R.id.tvCard2Sub)).setTextColor(0xFFFFE3A8);

        ((TextView) findViewById(R.id.btnAddSong)).setTextColor(0xFFFFD54F);
        tvSelect.setTextColor(0xFFFFD54F);

        goldRow(R.id.btnCover);
        goldRow(R.id.btnRename);
    }

    // paints the icon and the text of a small button row cream
    private void goldRow(int id) {
        ViewGroup row = findViewById(id);
        if (row == null) return;
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof TextView) {
                ((TextView) v).setTextColor(0xFFFFE9A3);
            } else if (v instanceof ImageView) {
                ImageViewCompat.setImageTintList((ImageView) v,
                        ColorStateList.valueOf(0xFFFFE9A3));
            }
        }
    }

    // the comet of light that circles the play button (added only once)
    private void addGoldRing(float d) {
        if (goldRing != null) return;
        FrameLayout box = findViewById(R.id.heroBox);
        if (box == null) return;
        goldRing = new GoldRingView(this);
        int size = (int) (128 * d);
        box.addView(goldRing, 2, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
    }

    // songs show their bitrate like "192K": find the biggest number
    private int maxKbps(List<Song> songs) {
        int max = 0;
        for (Song s : songs) {
            if (s.plays != null && s.plays.endsWith("K")) {
                try {
                    max = Math.max(max,
                            Integer.parseInt(s.plays.substring(0, s.plays.length() - 1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }

    // pop-up: choose a picture, or go back to the default cover
    private void showCoverMenu() {
        final String key = PlaylistCoverStore.keyFor(playlist);
        boolean hasCustom = PlaylistCoverStore.path(this, key) != null;
        String[] labels = hasCustom
                ? new String[]{"Choose a picture", "Use the default cover"}
                : new String[]{"Choose a picture"};
        int[] icons = hasCustom
                ? new int[]{R.drawable.ic_add, R.drawable.ic_remove}
                : new int[]{R.drawable.ic_add};
        AppDialog.choices(this, R.drawable.ic_disc, "Playlist cover", labels, icons, which -> {
            if (which == 0) {
                coverPicker.launch(new String[]{"image/*"});
            } else {
                PlaylistCoverStore.clear(this, key);
                Toast.makeText(this, "Default cover is back", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // copies the picked picture into the app (on a background thread)
    private void saveCover(final Uri uri) {
        final String key = PlaylistCoverStore.keyFor(playlist);
        Toast.makeText(this, "Saving cover…", Toast.LENGTH_SHORT).show();
        PlaylistCoverStore.IO.execute(() -> {
            final boolean ok = PlaylistCoverStore.save(getApplicationContext(), key, uri);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, ok ? "Cover saved. You will see it in the Playlists list."
                        : "Couldn't use that picture", Toast.LENGTH_SHORT).show();
            });
        });
    }

    // your own song: its files are deleted. Official song: it is hidden.
    // your own song: its files are deleted. Official song: it is hidden.
    private void confirmDeleteSong(final Song song) {
        AppDialog.confirm(this, R.drawable.ic_delete, "Delete \"" + song.title + "\"?",
                song.isCustom()
                        ? "This removes the song from the app. Your original file is not touched."
                        : "This removes the song from the app. You can bring it back later in Settings.",
                "Delete", true, () -> {
                    MediaController c = PlayerConnection.get();
                    if (c != null && isCurrentSong(song)) c.clearMediaItems();
                    CustomPlaylistStore.removeEverywhere(this, song);
                    if (song.isCustom()) {
                        CustomSongStore.delete(this, song);
                    } else {
                        HiddenSongStore.hide(this, song);
                    }
                    reloadPlaylist();   // closes the page if nothing is left
                });
    }

    // ---------- playing ----------
    private boolean isPlaylistActive() {
        MediaController c = PlayerConnection.get();
        return playlist.name.equals(MainActivity.activePlaylistName)
                && c != null && c.getCurrentMediaItem() != null;
    }

    private boolean isCurrentSong(Song song) {
        MediaController c = PlayerConnection.get();
        MediaItem item = (c != null) ? c.getCurrentMediaItem() : null;
        return item != null && String.valueOf(song.resId).equals(item.mediaId);
    }

    // the big button and the Play Playlist button
    private void onPlaylistButton() {
        if (playlist.songs.isEmpty()) {
            Toast.makeText(this, "Add some songs first", Toast.LENGTH_SHORT).show();
            return;
        }
        MediaController c = PlayerConnection.get();
        if (isPlaylistActive() && c != null) {
            if (c.isPlaying()) c.pause(); else c.play();
        } else {
            List<Song> queue = new ArrayList<>(playlist.songs);
            if (playlist.shuffled) Collections.shuffle(queue);
            MainActivity.activePlaylistName = playlist.name;
            PlayerConnection.play(this, queue, 0);
        }
    }

    // plays this playlist in order, starting at one song
    private void startQueue(int startIndex) {
        MainActivity.activePlaylistName = playlist.name;
        PlayerConnection.play(this, new ArrayList<>(playlist.songs), startIndex);
    }

    // updates the hero button, the button text, the equalizer bars and the song rows
    private void refreshPlayback() {
        MediaController c = PlayerConnection.get();
        if (c == null || adapter == null) return;

        boolean active = isPlaylistActive();
        boolean playing = active && c.isPlaying();

        ivHero.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        tvPlayLabel.setText(playing ? "Pause Playlist"
                : (active ? "Resume Playlist" : "Play Playlist"));
        animateBars(playing);
        animateWave(playing);

        MediaItem item = c.getCurrentMediaItem();
        adapter.setPlaybackState(item != null ? item.mediaId : null, active && c.isPlaying());
    }

    // the circles pulse and two rings spread outward while the playlist plays;
    // when it is paused everything eases back to rest
    private void animateWave(boolean on) {
        if (on == waveOn) return;
        waveOn = on;

        for (ObjectAnimator animator : waveAnimators) animator.cancel();
        waveAnimators.clear();

        if (on) {
            // soft pulse: each circle grows a little, one after another
            float[] grow = {1.14f, 1.08f};
            for (int i = 0; i < rings.length; i++) {
                View ring = rings[i];
                ring.animate().cancel();
                ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(ring,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, ring.getScaleX(), grow[i]),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, ring.getScaleY(), grow[i]));
                pulse.setDuration(2400);
                pulse.setStartDelay(i * 300L);
                pulse.setRepeatMode(ValueAnimator.REVERSE);
                pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.setInterpolator(new AccelerateDecelerateInterpolator());
                pulse.start();
                waveAnimators.add(pulse);
            }

            // ripples: a ring grows steadily outward and only fades near the end
            for (int i = 0; i < ripples.length; i++) {
                View ripple = ripples[i];
                ripple.animate().cancel();
                ripple.setAlpha(0f);

                ObjectAnimator expand = ObjectAnimator.ofPropertyValuesHolder(ripple,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 2.5f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 2.5f));
                expand.setDuration(4200);
                expand.setStartDelay(i * 2100L);
                expand.setRepeatCount(ValueAnimator.INFINITE);
                expand.setInterpolator(new DecelerateInterpolator(0.8f));
                expand.start();
                waveAnimators.add(expand);

                ObjectAnimator fade = ObjectAnimator.ofPropertyValuesHolder(ripple,
                        PropertyValuesHolder.ofKeyframe(View.ALPHA,
                                Keyframe.ofFloat(0f, 0f),
                                Keyframe.ofFloat(0.12f, 0.8f),
                                Keyframe.ofFloat(0.65f, 0.45f),
                                Keyframe.ofFloat(1f, 0f)));
                fade.setDuration(4200);
                fade.setStartDelay(i * 2100L);
                fade.setRepeatCount(ValueAnimator.INFINITE);
                fade.setInterpolator(new LinearInterpolator());
                fade.start();
                waveAnimators.add(fade);
            }
        } else {
            // pause: the circles ease back to their size, the ripples fade out
            for (View ring : rings) {
                ring.animate().cancel();
                ring.animate().scaleX(1f).scaleY(1f).setDuration(700)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
            for (View ripple : ripples) {
                ripple.animate().cancel();
                ripple.animate().alpha(0f).setDuration(500).start();
            }
        }
    }

    // the little equalizer bars bounce while the playlist plays
    private void animateBars(boolean on) {
        if (on && barAnimators.isEmpty()) {
            for (int i = 0; i < bars.length; i++) {
                bars[i].animate().cancel();
                ObjectAnimator animator = ObjectAnimator.ofFloat(bars[i], View.SCALE_Y, 1f, 0.35f);
                animator.setDuration(380 + i * 90L);
                animator.setRepeatMode(ValueAnimator.REVERSE);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.start();
                barAnimators.add(animator);
            }
        } else if (!on) {
            for (ObjectAnimator animator : barAnimators) animator.cancel();
            barAnimators.clear();
            // settle back slowly instead of snapping
            for (View bar : bars) {
                bar.animate().cancel();
                bar.animate().scaleY(1f).setDuration(500)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        }
    }

    // saves the new order, and reorders the queue if this playlist is playing now
    private void saveOrder() {
        if (!editable()) return;
        if (playlist.special) {
            SpecialPlaylistStore.saveSongs(this, playlist.songs);
        } else {
            CustomPlaylistStore.saveOrder(this, playlist.name, playlist.songs);
        }

        MediaController c = PlayerConnection.get();
        if (c == null || !isPlaylistActive()
                || c.getMediaItemCount() != playlist.songs.size()) return;
        for (int i = 0; i < playlist.songs.size(); i++) {
            String id = String.valueOf(playlist.songs.get(i).resId);
            for (int j = i; j < c.getMediaItemCount(); j++) {
                if (c.getMediaItemAt(j).mediaId.equals(id)) {
                    if (j != i) c.moveMediaItem(j, i);
                    break;
                }
            }
        }
    }

    // ---------- ticking songs to make a new playlist ----------
    private void setSelecting(boolean on) {
        selecting = on;
        adapter.setSelectionMode(on);
        tvSelect.setText(on ? "CANCEL" : "SELECT");
        btnSaveSelected.setVisibility(on ? View.VISIBLE : View.GONE);
        updateSaveLabel();
    }

    private void updateSaveLabel() {
        int count = adapter.selectedCount();
        tvSaveLabel.setText("Save " + count + (count == 1 ? " song" : " songs") + " as playlist");
        btnSaveSelected.setAlpha(count > 0 ? 1f : 0.5f);
    }

    private void saveSelected() {
        if (adapter.selectedCount() == 0) {
            Toast.makeText(this, "Tick at least one song first", Toast.LENGTH_SHORT).show();
            return;
        }
        PlaylistDialogs.showCreatePlaylist(this, adapter.selectedSongs(),
                () -> setSelecting(false));
    }

    // ---------- adding songs ----------
    // pick songs from everything in the app that is not in this playlist yet
    private void showAddSongs() {
        List<Song> candidates = new ArrayList<>();
        for (Song s : MusicLibrary.loadWithCustom(this)) {
            boolean already = false;
            for (Song p : playlist.songs) {
                if (p.resId == s.resId) { already = true; break; }
            }
            if (!already) candidates.add(s);
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_pick_songs, null);
        DialogBg.apply(this, view);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        RecyclerView rv = view.findViewById(R.id.rvPick);
        TextView btnAdd = view.findViewById(R.id.btnPickAdd);
        rv.setLayoutManager(new LinearLayoutManager(this));

        final PlaylistSongAdapter[] ref = new PlaylistSongAdapter[1];
        ref[0] = new PlaylistSongAdapter(candidates, new PlaylistSongAdapter.Listener() {
            @Override public void onRowClick(int position) {
                ref[0].toggle(position);
                int n = ref[0].selectedCount();
                btnAdd.setText("Add " + n + (n == 1 ? " song" : " songs"));
                btnAdd.setAlpha(n > 0 ? 1f : 0.5f);
            }
            @Override public void onRowLongClick(int position) { }
            @Override public void onPlayClick(int position) { }
            @Override public void onRemoveClick(int position) { }
        });
        ref[0].setSelectionMode(true);
        rv.setAdapter(ref[0]);
        btnAdd.setAlpha(0.5f);

        view.findViewById(R.id.btnPickCancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnImportPhone).setOnClickListener(v -> {
            dialog.dismiss();
            audioPicker.launch(new String[]{"audio/*"});
        });
        btnAdd.setOnClickListener(v -> {
            List<Song> picked = ref[0].selectedSongs();
            if (picked.isEmpty()) {
                Toast.makeText(this, "Tick at least one song", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            addSongs(picked);
        });
        dialog.show();
    }

    private void addSongs(List<Song> picked) {
        if (playlist.special) {
            List<Song> all = new ArrayList<>(playlist.songs);
            all.addAll(picked);
            SpecialPlaylistStore.saveSongs(this, all);
        } else if (playlist.custom) {
            for (Song s : picked) CustomPlaylistStore.addSong(this, playlist.name, s);
        }
        Toast.makeText(this, picked.size() == 1 ? "Song added" : picked.size() + " songs added",
                Toast.LENGTH_SHORT).show();
        reloadPlaylist();
    }

    // step 2 of import: the same form you use for "Add your own song"
    private void showImportForm(Uri audio) {
        CustomSongStore.Info info = CustomSongStore.peek(this, audio);
        importThumb = null;
        importArt = info.art;

        View view = getLayoutInflater().inflate(R.layout.dialog_add_song, null);
        DialogBg.apply(this, view);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        importThumbView = view.findViewById(R.id.ivAddThumb);
        EditText etTitle = view.findViewById(R.id.etAddTitle);
        EditText etArtist = view.findViewById(R.id.etAddArtist);
        etTitle.setText(info.title);
        etArtist.setText(info.artist);
        if (info.art != null) {
            importThumbView.setImageBitmap(
                    BitmapFactory.decodeByteArray(info.art, 0, info.art.length));
        }

        view.findViewById(R.id.btnPickThumb).setOnClickListener(v ->
                thumbPicker.launch(new String[]{"image/*"}));
        view.findViewById(R.id.btnAddCancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnAddSave).setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String artist = etArtist.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "Please type a song title", Toast.LENGTH_SHORT).show();
                return;
            }
            if (artist.isEmpty()) artist = "Unknown Artist";
            dialog.dismiss();
            importAndAdd(audio, title, artist);
        });
        dialog.show();
    }

    // step 3 of import: copy the file into the app, then put the new song in this playlist
    private void importAndAdd(final Uri audio, final String title, final String artist) {
        final Set<Integer> before = new HashSet<>();
        for (Song s : CustomSongStore.load(this)) before.add(s.resId);
        final Uri thumb = importThumb;
        final byte[] art = importArt;

        Toast.makeText(this, "Importing song…", Toast.LENGTH_SHORT).show();
        PlaylistCoverStore.IO.execute(() -> {
            final boolean ok = CustomSongStore.importSong(getApplicationContext(),
                    audio, title, artist, thumb, art);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!ok) {
                    Toast.makeText(this, "Couldn't import that file", Toast.LENGTH_SHORT).show();
                    return;
                }
                List<Song> added = new ArrayList<>();
                for (Song s : CustomSongStore.load(this)) {
                    if (!before.contains(s.resId)) added.add(s);   // the new song
                }
                if (added.isEmpty()) {
                    reloadPlaylist();
                } else {
                    addSongs(added);
                }
            });
        });
    }

    // paints a word with the purple-to-cyan gradient
    private static class GradientSpan extends CharacterStyle implements UpdateAppearance {
        private static int startColor = 0xFFA78BFA;
        private static int endColor = 0xFF5EE7FF;
        private final int letters;

        GradientSpan(int letters) {
            this.letters = letters;
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            float width = paint.getTextSize() * letters * 0.6f;
            paint.setShader(new LinearGradient(0, 0, width, 0,
                    startColor, endColor, Shader.TileMode.CLAMP));
        }
    }
}