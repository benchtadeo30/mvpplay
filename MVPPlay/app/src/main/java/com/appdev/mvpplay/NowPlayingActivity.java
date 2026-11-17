package com.appdev.mvpplay;

import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.media.AudioManager;
import android.view.Gravity;
import android.view.ViewGroup;
import android.content.pm.ActivityInfo;
import android.view.WindowManager;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.RequiresApi;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.util.Rational;
import java.util.ArrayList;
import java.util.List;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class NowPlayingActivity extends BaseActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean started = false;      // true while this screen is visible
    private boolean dragging = false;
    private boolean liked = false;

    // what the screen is currently showing (used to detect changes)
    private String boundMediaId = "?";
    private String lastStateKey = null;

    // the song that is playing (null for podcasts and videos: they cannot be saved)
    private List<Song> musicSongs;
    private Song currentSong;

    private ImageView ivCover, ivPlayPause, ivShuffle, ivRepeat, ivHeart, ivSleep;
    private VinylView vinyl;
    private NotesView notes;              // small cover with floating music notes
    private CassetteView cassette;        // cassette tape with spinning reels
    private TiltCardView tilt;            // floating 3D card
    private AmbientView emptyTap;         // tap area plus tiny effects for the empty style
    private static final int STYLE_COVER = 0, STYLE_VINYL = 1, STYLE_NOTES = 2,
            STYLE_CASSETTE = 3, STYLE_TILT = 4, STYLE_EMPTY = 5;
    private static final int STYLE_COUNT = 6;
    private int coverStyle = STYLE_COVER;
    private boolean vinylStyle = false;   // true only while coverStyle is STYLE_VINYL
    private EffectBackground effectBg;    // the GIF / mp4 behind the screen
    private LyricsSheet lyricsSheet;      // the lyrics pop-up

    // opens the phone's file picker when the user adds their own effect
    private final ActivityResultLauncher<String[]> effectFilePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) handlePickedEffect(uri);
            });

    // the "tap to change view" hint on top of the cover
    private FrameLayout coverHint;
    private View hintRing;
    private ObjectAnimator hintPulse;
    private boolean hintShown = false;    // shown once each time this screen opens
    private final Runnable hideHintRunnable = () -> hideCoverHint(true);

    private TextView tvSpeedBtn;
    private TextView tvTitle, tvArtist, tvElapsed, tvRemaining, tvTagFormat, tvTagRate, tvSleep;
    private View dotShuffle, dotRepeat, btnSave;
    private WaveformView waveform;
    private TextureView textureVideo;
    private View eqBadge, coverArea, svDescription;
    private TextView tvDescription, tvPlayingFrom, tvPlayingFromName;

    // episode list inside the podcast screen
    private RecyclerView rvNpEpisodes;
    private TextView tvEpisodesLabel;
    private EpisodeAdapter episodeAdapter;
    private List<Song> episodeList = new ArrayList<>();
    private String episodesShow = null;
    private String lastScrolledId = null;

    // custom songs: where the saved file and thumbnail are
    private String currentUri = null;
    private String currentThumbPath = null;

    // full screen video
    private LinearLayout contentArea;
    private View btnExpand, fsOverlay, fsControls;
    private ImageView fsPlayIcon;
    private TextView fsElapsed, fsRemaining;
    private SeekBar fsSeek;
    private boolean fullscreen = false;
    private boolean fsDragging = false;
    private boolean podcastMode = false;

    // floating video window (picture-in-picture)
    private View btnPip;
    private boolean inPip = false;
    private static final String PIP_ACTION = "com.appdev.mvpplay.PIP_CONTROL";
    private static final String PIP_EXTRA = "control";
    private static final int PIP_PREV = 1, PIP_PLAY = 2, PIP_NEXT = 3;

    // the buttons inside the floating window send their taps here
    private final BroadcastReceiver pipReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!PIP_ACTION.equals(intent.getAction())) return;
            MediaController c = PlayerConnection.get();
            if (c == null) return;
            switch (intent.getIntExtra(PIP_EXTRA, 0)) {
                case PIP_PREV:
                    c.seekToPrevious();
                    break;
                case PIP_PLAY:
                    if (c.isPlaying()) c.pause(); else c.play();
                    break;
                case PIP_NEXT:
                    c.seekToNext();
                    break;
            }
        }
    };

    // Back leaves full screen first (it is only on while full screen is open)
    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            exitFullscreen();
        }
    };

    // reacts to anything that changes in the background player
    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onEvents(Player player, Player.Events events) {
            if (events.contains(Player.EVENT_VIDEO_SIZE_CHANGED)) {
                applyVideoSize(player.getVideoSize());
            }
            syncWithPlayer(player);
        }
    };

    // every 0.3 seconds: re-check the player, move the progress, update the sleep label
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            MediaController c = PlayerConnection.get();
            if (c != null) {
                syncWithPlayer(c);
                if (!dragging) {
                    long duration = c.getDuration();
                    long position = c.getCurrentPosition();
                    if (duration > 0) {
                        waveform.setProgress((float) position / duration);
                        vinyl.setPlayProgress((float) position / duration);
                        cassette.setPlayProgress((float) position / duration);
                    }
                    showTimes(position, duration);
                }
            }
            updateSpeedLabel();
            updateSleepLabel();
            updateFullscreenProgress();
            handler.postDelayed(this, 300);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_now_playing);

        WindowInsetsControllerCompat bars =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);

        ivCover = findViewById(R.id.ivCover);
        vinyl = findViewById(R.id.vinylView);
        vinyl.setAccent(ContextCompat.getColor(this, R.color.accent_purple));
        notes = findViewById(R.id.notesView);
        notes.setAccent(ContextCompat.getColor(this, R.color.accent_purple));
        cassette = findViewById(R.id.cassetteView);
        cassette.setAccent(ContextCompat.getColor(this, R.color.accent_purple));
        tilt = findViewById(R.id.tiltView);
        tilt.setAccent(ContextCompat.getColor(this, R.color.accent_purple));
        emptyTap = findViewById(R.id.emptyTap);
        emptyTap.setAccent(ContextCompat.getColor(this, R.color.accent_purple));
        android.content.SharedPreferences np = getSharedPreferences("now_playing", MODE_PRIVATE);
        coverStyle = np.getInt("coverStyle",
                np.getBoolean("vinyl", false) ? STYLE_VINYL : STYLE_COVER);
        vinylStyle = (coverStyle == STYLE_VINYL);
        // tap the picture to switch to the next style
        ivCover.setOnClickListener(v -> toggleCoverStyle());
        vinyl.setOnClickListener(v -> toggleCoverStyle());
        notes.setOnClickListener(v -> toggleCoverStyle());
        cassette.setOnClickListener(v -> toggleCoverStyle());
        tilt.setOnClickListener(v -> toggleCoverStyle());
        emptyTap.setOnClickListener(v -> toggleCoverStyle());
        ivPlayPause = findViewById(R.id.ivPlayPause);
        ivShuffle = findViewById(R.id.ivShuffle);
        ivRepeat = findViewById(R.id.ivRepeat);
        ivHeart = findViewById(R.id.ivHeart);
        ivSleep = findViewById(R.id.ivSleep);
        tvTitle = findViewById(R.id.tvTitle);
        tvArtist = findViewById(R.id.tvArtist);
        tvElapsed = findViewById(R.id.tvElapsed);
        tvRemaining = findViewById(R.id.tvRemaining);
        tvTagFormat = findViewById(R.id.tvTagFormat);
        tvTagRate = findViewById(R.id.tvTagRate);
        tvSleep = findViewById(R.id.tvSleep);
        dotShuffle = findViewById(R.id.dotShuffle);
        dotRepeat = findViewById(R.id.dotRepeat);
        btnSave = findViewById(R.id.btnSave);
        waveform = findViewById(R.id.waveform);
        textureVideo = findViewById(R.id.textureVideo);
        eqBadge = findViewById(R.id.eqBadge);
        coverArea = findViewById(R.id.coverArea);
        svDescription = findViewById(R.id.svDescription);
        tvDescription = findViewById(R.id.tvDescription);
        tvPlayingFrom = findViewById(R.id.tvPlayingFrom);
        tvPlayingFromName = findViewById(R.id.tvPlayingFromName);
        setupFullscreen();
        buildCoverHint();

        rvNpEpisodes = findViewById(R.id.rvNpEpisodes);
        tvEpisodesLabel = findViewById(R.id.tvEpisodesLabel);
        rvNpEpisodes.setLayoutManager(new LinearLayoutManager(this));
        rvNpEpisodes.setNestedScrollingEnabled(false);

        // round the corners of the video like the cover
        final float cornerRadius = 24 * getResources().getDisplayMetrics().density;
        textureVideo.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        textureVideo.setClipToOutline(true);

        // makes a long title scroll sideways (short titles stay still)
        tvTitle.setSelected(true);

        tint(ivHeart, R.color.accent_lavender);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnMore).setOnClickListener(v -> showMoreMenu());
        effectBg = new EffectBackground(this, findViewById(R.id.npRoot));
        findViewById(R.id.btnEffects).setOnClickListener(v -> showEffectPicker());
        findViewById(R.id.btnProfile).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // play / pause
        findViewById(R.id.btnPlayPause).setOnClickListener(v -> {
            MediaController c = PlayerConnection.get();
            if (c == null) return;
            if (c.isPlaying()) c.pause(); else c.play();
        });

        // next / previous
        findViewById(R.id.btnNext).setOnClickListener(v -> {
            MediaController c = PlayerConnection.get();
            if (c != null) c.seekToNext();
        });
        findViewById(R.id.btnPrev).setOnClickListener(v -> {
            MediaController c = PlayerConnection.get();
            if (c != null) c.seekToPrevious();
        });

        // shuffle on / off
        findViewById(R.id.btnShuffle).setOnClickListener(v -> {
            MediaController c = PlayerConnection.get();
            if (c == null) return;
            if (!c.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) {
                Toast.makeText(this, "Shuffle is not available", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean turnOn = !c.getShuffleModeEnabled();
            c.setShuffleModeEnabled(turnOn);
            Toast.makeText(this, turnOn ? "Shuffle on" : "Shuffle off",
                    Toast.LENGTH_SHORT).show();
        });

        // repeat: off -> all -> one -> off
        findViewById(R.id.btnRepeat).setOnClickListener(v -> {
            MediaController c = PlayerConnection.get();
            if (c == null) return;
            if (!c.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)) {
                Toast.makeText(this, "Repeat is not available", Toast.LENGTH_SHORT).show();
                return;
            }
            int mode = c.getRepeatMode();
            if (mode == Player.REPEAT_MODE_OFF) {
                c.setRepeatMode(Player.REPEAT_MODE_ALL);
                Toast.makeText(this, "Repeat all", Toast.LENGTH_SHORT).show();
            } else if (mode == Player.REPEAT_MODE_ALL) {
                c.setRepeatMode(Player.REPEAT_MODE_ONE);
                Toast.makeText(this, "Repeat one", Toast.LENGTH_SHORT).show();
            } else {
                c.setRepeatMode(Player.REPEAT_MODE_OFF);
                Toast.makeText(this, "Repeat off", Toast.LENGTH_SHORT).show();
            }
        });

        // heart: adds or removes the song from Favorites
        ivHeart.setOnClickListener(v -> {
            int resId = currentResId();
            if (resId == 0) return;
            boolean nowLiked = FavoritesStore.toggle(this, resId);
            showHeart(nowLiked);
            Anim.pop(ivHeart);
            Toast.makeText(this, nowLiked ? "Added to Favorites" : "Removed from Favorites",
                    Toast.LENGTH_SHORT).show();
        });

        // share the song name
        findViewById(R.id.btnShare).setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, "Listening to " + tvTitle.getText()
                    + " by " + tvArtist.getText() + " on MVPPLAY");
            startActivity(Intent.createChooser(send, "Share"));
        });

        // Save to Playlist
        btnSave.setOnClickListener(v -> {
            if (currentSong == null) {
                Toast.makeText(this, "Only songs can be saved to playlists",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            PlaylistDialogs.showSaveToPlaylist(this, currentSong, null);
        });

        // sleep timer
        findViewById(R.id.btnSleep).setOnClickListener(v -> showSleepDialog());
        addVolumeButton();
        addSpeedButton();
        addLyricsButton();

        // save the current song to one of your playlists
        findViewById(R.id.btnSave).setOnClickListener(v -> saveCurrentSong());

        // tap or drag the waveform to jump inside the song
        waveform.setOnSeekListener(new WaveformView.OnSeekListener() {
            @Override
            public void onScrub(float fraction) {
                dragging = true;
                MediaController c = PlayerConnection.get();
                if (c != null && c.getDuration() > 0) {
                    showTimes((long) (fraction * c.getDuration()), c.getDuration());
                }
            }

            @Override
            public void onSeekEnd(float fraction) {
                MediaController c = PlayerConnection.get();
                if (c != null && c.getDuration() > 0) {
                    c.seekTo((long) (fraction * c.getDuration()));
                }
                dragging = false;
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        AudioLevels.enabled = true;
        PlayerConnection.connect(this, c -> {
            if (!started) return;          // the screen was closed before the player answered
            c.addListener(playerListener);
            setVideoEnabled(c, true);
            c.setVideoTextureView(textureVideo);
            boundMediaId = "?";            // force a fresh fill-in of the screen
            lastStateKey = null;
            syncWithPlayer(c);
            handler.removeCallbacks(ticker);
            handler.post(ticker);
            if (!hintShown) {
                hintShown = true;
                handler.postDelayed(this::showCoverHint, 700);   // after the screen slides in
            }
        });
    }

    @Override
    protected void onStop() {
        started = false;
        AudioLevels.enabled = false;
        if (effectBg != null) effectBg.hide();
        if (lyricsSheet != null) lyricsSheet.dismiss();
        handler.removeCallbacks(ticker);
        hideCoverHint(false);
        vinyl.setSpinning(false);
        updateStylePlaying();
        MediaController c = PlayerConnection.get();
        if (c != null) {
            c.removeListener(playerListener);
            c.clearVideoTextureView(textureVideo);
            setVideoEnabled(c, false);   // no picture needed in the background, only sound
        }
        super.onStop();
    }

    // opens the "Save to playlist" pop-up for the song that is playing now
    private void saveCurrentSong() {
        MediaController c = PlayerConnection.get();
        MediaItem item = (c != null) ? c.getCurrentMediaItem() : null;
        if (item == null) {
            Toast.makeText(this, "Play a song first", Toast.LENGTH_SHORT).show();
            return;
        }

        Song found = null;
        for (Song s : MusicLibrary.loadWithCustom(this)) {
            if (String.valueOf(s.resId).equals(item.mediaId)) {
                found = s;
                break;
            }
        }
        if (found == null) {
            // podcast episodes are not part of the song playlists
            Toast.makeText(this, "Only songs can be saved to playlists",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        PlaylistDialogs.showSaveToPlaylist(this, found, null);
    }
    // pop-up list for the sleep timer
    // pop-up for the sleep timer: a bottom sheet with time chips
    private void showSleepDialog() {
        final String[] chipLabels = {"5 min", "10 min", "15 min", "30 min", "45 min", "60 min"};
        final int[] minutes = {5, 10, 15, 30, 45, 60};
        final boolean running = SleepTimer.isActive() || SleepTimer.isEndOfSong();

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
        GradientDrawable handleShape = new GradientDrawable();
        handleShape.setColor((ContextCompat.getColor(this, R.color.text_hint) & 0x00FFFFFF) | 0x66000000);
        handleShape.setCornerRadius(dp(2));
        View handle = new View(this);
        handle.setBackground(handleShape);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(40), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(16);
        box.addView(handle, handleParams);

        // title and a line that says what the timer is doing
        TextView title = new TextView(this);
        title.setText("Sleep timer");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(SleepTimer.isEndOfSong()
                ? "Music will pause when this song ends."
                : SleepTimer.isActive()
                  ? "Music will pause in " + formatTime(SleepTimer.remainingMs())
                  : "Music pauses when the timer ends.");
        subtitle.setTextSize(13);
        subtitle.setTextColor(ContextCompat.getColor(this, running
                ? R.color.accent_cyan_light : R.color.text_secondary));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(4);
        box.addView(subtitle, subParams);

        // the time chips: 2 rows of 3
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(r == 0 ? 20 : 8);
            box.addView(row, rowParams);

            for (int col = 0; col < 3; col++) {
                final int index = r * 3 + col;
                TextView chip = sleepButton(chipLabels[index],
                        ContextCompat.getColor(this, R.color.text_primary));
                LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
                if (col > 0) chipParams.setMarginStart(dp(8));
                row.addView(chip, chipParams);

                chip.setOnClickListener(v -> {
                    SleepTimer.startMinutes(minutes[index]);
                    Toast.makeText(this, "Music will pause in " + minutes[index] + " minutes",
                            Toast.LENGTH_SHORT).show();
                    sheet.dismiss();
                    updateSleepLabel();
                });
            }
        }

        // pause when the song ends
        TextView endOfSong = sleepButton("End of current song",
                ContextCompat.getColor(this, R.color.accent_cyan_light));
        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        endParams.topMargin = dp(16);
        box.addView(endOfSong, endParams);
        endOfSong.setOnClickListener(v -> {
            if (PlayerConnection.get() == null) {
                Toast.makeText(this, "Play a song first", Toast.LENGTH_SHORT).show();
            } else {
                SleepTimer.startEndOfSong();
                Toast.makeText(this, "Music will pause when this song ends",
                        Toast.LENGTH_SHORT).show();
            }
            sheet.dismiss();
            updateSleepLabel();
        });

        // turn the timer off (pink while a timer is running, quiet when there is none)
        TextView turnOff = sleepButton("Turn off timer", running
                ? 0xFFF472B6 : ContextCompat.getColor(this, R.color.text_hint));
        LinearLayout.LayoutParams offParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        offParams.topMargin = dp(8);
        box.addView(turnOff, offParams);
        turnOff.setOnClickListener(v -> {
            SleepTimer.cancel();
            Toast.makeText(this, "Sleep timer off", Toast.LENGTH_SHORT).show();
            sheet.dismiss();
            updateSleepLabel();
        });

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

    // one rounded button of the sleep timer sheet (same chip look as the rest of the app)
    private TextView sleepButton(String label, int textColor) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(null, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setTextColor(textColor);
        button.setBackgroundResource(R.drawable.bg_chip);
        button.setClickable(true);
        button.setFocusable(true);
        Anim.press(button);
        return button;
    }

    // the Sleep label shows the countdown while a timer is running
    private void updateSleepLabel() {
        if (SleepTimer.isEndOfSong()) {
            tvSleep.setText("End of song");
            tvSleep.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan_light));
            tint(ivSleep, R.color.accent_cyan_light);
        } else if (SleepTimer.isActive()) {
            tvSleep.setText("Sleep " + formatTime(SleepTimer.remainingMs()));
            tvSleep.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan_light));
            tint(ivSleep, R.color.accent_cyan_light);
        } else {
            tvSleep.setText("Sleep");
            tvSleep.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tint(ivSleep, R.color.text_secondary);
        }
    }

    // compares the screen with the player and updates only what changed
    private void syncWithPlayer(Player p) {
        MediaItem item = p.getCurrentMediaItem();
        String id = (item != null) ? item.mediaId : null;
        boolean changed = false;
        if (!Objects.equals(id, boundMediaId)) {
            boundMediaId = id;
            bindSong(item);
            changed = true;
        }

        String key = p.isPlaying() + "|" + p.getShuffleModeEnabled() + "|" + p.getRepeatMode();
        if (!key.equals(lastStateKey)) {
            lastStateKey = key;
            updateControls(p);
            changed = true;
        }
        if (changed) updateEpisodePlayback(p);
    }

    // updates buttons: play/pause icon, shuffle, repeat, waveform bounce
    private void updateControls(Player p) {
        ivPlayPause.setImageResource(p.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        Anim.pop(ivPlayPause);
        fsPlayIcon.setImageResource(p.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        refreshPip();
        waveform.setPlaying(p.isPlaying());

        boolean shuffle = p.getShuffleModeEnabled();
        tint(ivShuffle, shuffle ? R.color.accent_cyan_light : R.color.text_primary);
        dotShuffle.setVisibility(shuffle ? View.VISIBLE : View.INVISIBLE);

        int mode = p.getRepeatMode();
        ivRepeat.setImageResource(mode == Player.REPEAT_MODE_ONE
                ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        tint(ivRepeat, mode == Player.REPEAT_MODE_OFF
                ? R.color.text_primary : R.color.accent_cyan_light);
        dotRepeat.setVisibility(mode == Player.REPEAT_MODE_OFF ? View.INVISIBLE : View.VISIBLE);
        vinyl.setSpinning(started && p.isPlaying() && vinyl.getVisibility() == View.VISIBLE);
        updateStylePlaying();
    }

    // looks for the playing song in your music list (podcasts and videos are not in it)
    private Song findSong(String mediaId) {
        if (musicSongs == null) musicSongs = MusicLibrary.loadWithCustom(this);
        for (Song s : musicSongs) {
            if (String.valueOf(s.resId).equals(mediaId)) return s;
        }
        return null;
    }

    // fills in the song: title, artist, cover or video, tags, waveform pattern
    private void bindSong(@Nullable MediaItem item) {
        if (item == null) {
            tvTitle.setText("Nothing playing");
            tvArtist.setText("");
            currentSong = null;
            btnSave.setVisibility(View.GONE);
            return;
        }

        CharSequence title = item.mediaMetadata.title;
        CharSequence artist = item.mediaMetadata.artist;
        tvTitle.setText(title != null ? title : "Unknown");
        tvArtist.setText(artist != null ? artist : "Unknown Artist");
        cassette.setTexts(title != null ? title.toString() : "Unknown",
                artist != null ? artist.toString() : "Unknown Artist");

        // the Save button only works for songs
        currentSong = findSong(item.mediaId);
        btnSave.setVisibility(currentSong != null ? View.VISIBLE : View.GONE);

        int colorStart = 0xFF2B1B5E;
        int colorEnd = 0xFF7C3AED;
        Bundle extras = item.mediaMetadata.extras;
        if (extras != null) {
            colorStart = extras.getInt("colorStart", colorStart);
            colorEnd = extras.getInt("colorEnd", colorEnd);
        }

        currentUri = (extras != null) ? extras.getString("uri") : null;
        currentThumbPath = (extras != null) ? extras.getString("thumbPath") : null;

        int resId = 0;
        try {
            resId = Integer.parseInt(item.mediaId);
        } catch (NumberFormatException ignored) {
        }

        boolean hasVideo = extras != null && extras.getBoolean("hasVideo", false);
        showVideo(hasVideo);
        showPodcastMode(resId, artist);
        showHeart(resId != 0 && FavoritesStore.isFavorite(this, resId));
        waveform.setSeed(resId);
        waveform.jumpTo(0f);
        loadFileInfo(resId, colorStart, colorEnd);
        applyCoverStyle(false);
        applyEffect();
        if (!isMusicMode()) hideCoverHint(false);   // podcasts and videos cannot switch
        if (ivCover.getVisibility() == View.VISIBLE) Anim.fadeIn(ivCover, 400);
        Anim.fadeIn(tvTitle, 300);
        Anim.fadeIn(tvArtist, 300);
    }

    // ---------- "tap to change view" hint ----------
    // a pill at the bottom and a pulsing badge in the corner, laid over the cover.
    // It does not catch touches, so tapping the cover still works under it.
    private void buildCoverHint() {
        coverHint = new FrameLayout(this);
        coverHint.setVisibility(View.GONE);

        // the pill
        TextView pill = new TextView(this);
        pill.setText("Tap to change view");
        pill.setTextSize(12);
        pill.setTypeface(null, Typeface.BOLD);
        pill.setTextColor(Color.WHITE);
        pill.setPadding(dp(14), dp(7), dp(14), dp(7));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(0xCC0A0C18);
        pillBg.setCornerRadius(dp(100));
        pill.setBackground(pillBg);
        FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        pillParams.bottomMargin = dp(14);
        coverHint.addView(pill, pillParams);

        // the pulsing ring behind the badge
        int accent = ContextCompat.getColor(this, R.color.accent_purple);
        hintRing = new View(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(2), accent);
        hintRing.setBackground(ring);
        FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(dp(32), dp(32));
        ringParams.gravity = Gravity.BOTTOM | Gravity.END;
        ringParams.setMargins(0, 0, dp(14), dp(10));
        coverHint.addView(hintRing, ringParams);

        // the badge
        ImageView badge = new ImageView(this);
        badge.setImageResource(R.drawable.ic_disc);
        badge.setColorFilter(Color.WHITE);
        badge.setPadding(dp(6), dp(6), dp(6), dp(6));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(accent);
        badge.setBackground(badgeBg);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(32), dp(32));
        badgeParams.gravity = Gravity.BOTTOM | Gravity.END;
        badgeParams.setMargins(0, 0, dp(14), dp(10));
        coverHint.addView(badge, badgeParams);

        hintPulse = ObjectAnimator.ofPropertyValuesHolder(hintRing,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.8f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.8f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.9f, 0f));
        hintPulse.setDuration(1400);
        hintPulse.setRepeatCount(ValueAnimator.INFINITE);

        // lay it over the whole cover area
        ViewGroup area = (ViewGroup) coverArea;
        if (area instanceof ConstraintLayout) {
            ConstraintLayout.LayoutParams fill = new ConstraintLayout.LayoutParams(0, 0);
            fill.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            fill.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            fill.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            fill.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            area.addView(coverHint, fill);
        } else {
            area.addView(coverHint, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private void showCoverHint() {
        if (coverHint == null || !started || !isMusicMode() || fullscreen || inPip) return;
        handler.removeCallbacks(hideHintRunnable);
        coverHint.animate().cancel();
        coverHint.setAlpha(0f);
        coverHint.setVisibility(View.VISIBLE);
        coverHint.animate().alpha(1f).setDuration(300).start();
        hintPulse.start();
        handler.postDelayed(hideHintRunnable, 4500);
    }

    private void hideCoverHint(boolean animate) {
        handler.removeCallbacks(hideHintRunnable);
        if (coverHint == null) return;
        hintPulse.cancel();
        coverHint.animate().cancel();
        if (animate && coverHint.getVisibility() == View.VISIBLE) {
            coverHint.animate().alpha(0f).setDuration(400)
                    .withEndAction(() -> coverHint.setVisibility(View.GONE)).start();
        } else {
            coverHint.setVisibility(View.GONE);
        }
    }

    // ---------- background effects ----------
    // plays the saved effect for music; podcasts and videos keep their own picture
    private void applyEffect() {
        if (effectBg == null) return;
        EffectBackground.Fx fx = EffectBackground.byKey(this,
                getSharedPreferences("now_playing", MODE_PRIVATE).getString("fx", ""));
        boolean on = started && fx != null && EffectBackground.canPlay(fx)
                && !EffectBackground.isLowRam(this) && isMusicMode()
                && !fullscreen && !inPip;
        if (on) effectBg.show(fx); else effectBg.hide();
    }

    // bottom sheet with a grid of previews and a "No effect" tile
    private void showEffectPicker() {
        if (EffectBackground.isLowRam(this)) {
            Toast.makeText(this, "Effects are off on this phone to save memory",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        final String current = getSharedPreferences("now_playing", MODE_PRIVATE)
                .getString("fx", "");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(28));
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(ContextCompat.getColor(this, R.color.surface_card));
        float corner = dp(24);
        boxBg.setCornerRadii(new float[]{corner, corner, corner, corner, 0, 0, 0, 0});
        box.setBackground(boxBg);

        GradientDrawable handleShape = new GradientDrawable();
        handleShape.setColor((ContextCompat.getColor(this, R.color.text_hint) & 0x00FFFFFF) | 0x66000000);
        handleShape.setCornerRadius(dp(2));
        View handle = new View(this);
        handle.setBackground(handleShape);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(40), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(16);
        box.addView(handle, handleParams);

        TextView title = new TextView(this);
        title.setText("Background effect");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Plays behind the music screen.");
        subtitle.setTextSize(13);
        subtitle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(4);
        box.addView(subtitle, subParams);

        // the tiles: "No effect" first, then every effect this phone can play
        final List<EffectBackground.Fx> items = new ArrayList<>();
        items.add(null);
        for (EffectBackground.Fx fx : EffectBackground.all(this)) {
            if (EffectBackground.canPlay(fx)) items.add(fx);
        }
        items.add(EffectBackground.ADD);   // the last tile: add your own

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            final EffectBackground.Fx fx = items.get(i);
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.topMargin = dp(8);
                grid.addView(row, rp);
            }
            boolean selected = (fx == null) ? current.isEmpty()
                    : fx != EffectBackground.ADD && fx.key.equals(current);
            View tile = effectTile(fx == null ? "No effect" : fx.label, fx, selected, sheet, v -> {
                if (fx == EffectBackground.ADD) {
                    sheet.dismiss();
                    startAddEffect();
                    return;
                }
                getSharedPreferences("now_playing", MODE_PRIVATE).edit()
                        .putString("fx", fx == null ? "" : fx.key).apply();
                sheet.dismiss();
                applyEffect();
            });
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(110), 1f);
            tp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(tile, tp);
        }
        // an odd number of tiles: keep the last one the same size
        if (items.size() % 2 == 1 && row != null) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(grid);
        box.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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

    // one tile: a preview picture, the name at the bottom, a ring when it is the chosen one,
    // and a small x on the effects you added yourself
    private View effectTile(String label, @Nullable final EffectBackground.Fx fx, boolean selected,
                            final BottomSheetDialog sheet, View.OnClickListener click) {
        int accent = ContextCompat.getColor(this, R.color.accent_purple);
        boolean isAdd = (fx == EffectBackground.ADD);

        FrameLayout tile = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ContextCompat.getColor(this, R.color.surface_field));
        bg.setCornerRadius(dp(16));
        tile.setBackground(bg);
        tile.setClipToOutline(true);
        tile.setClickable(true);
        tile.setOnClickListener(click);
        Anim.press(tile);

        if (fx != null && !isAdd) {
            final ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(preview, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            EffectBackground.IO.execute(() -> {
                final Bitmap b = EffectBackground.thumbnail(this, fx, 360);
                if (b != null) runOnUiThread(() -> preview.setImageBitmap(b));
            });
        } else {
            ImageView icon = new ImageView(this);
            icon.setImageResource(isAdd ? R.drawable.ic_add : R.drawable.ic_close);
            icon.setColorFilter(isAdd ? accent
                    : ContextCompat.getColor(this, R.color.text_secondary));
            FrameLayout.LayoutParams ipar = new FrameLayout.LayoutParams(dp(28), dp(28));
            ipar.gravity = Gravity.CENTER;
            tile.addView(icon, ipar);
        }

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(13);
        name.setTypeface(null, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setPadding(dp(8), dp(6), dp(8), dp(6));
        if (fx != null && !isAdd) {
            name.setTextColor(Color.WHITE);
            name.setBackgroundColor(0x99000000);
        } else {
            name.setTextColor(isAdd ? accent
                    : ContextCompat.getColor(this, R.color.text_secondary));
        }
        FrameLayout.LayoutParams nameParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.gravity = Gravity.BOTTOM;
        tile.addView(name, nameParams);

        if (selected) {
            View ring = new View(this);
            GradientDrawable ringShape = new GradientDrawable();
            ringShape.setColor(Color.TRANSPARENT);
            ringShape.setCornerRadius(dp(16));
            ringShape.setStroke(dp(3), accent);
            ring.setBackground(ringShape);
            tile.addView(ring, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // the small x: only on the effects the user added
        if (fx != null && fx.isCustom()) {
            ImageView del = new ImageView(this);
            del.setImageResource(R.drawable.ic_close);
            del.setColorFilter(Color.WHITE);
            del.setPadding(dp(6), dp(6), dp(6), dp(6));
            GradientDrawable delBg = new GradientDrawable();
            delBg.setShape(GradientDrawable.OVAL);
            delBg.setColor(0xB0000000);
            del.setBackground(delBg);
            del.setClickable(true);
            del.setContentDescription("Delete " + fx.label);
            del.setOnClickListener(v -> confirmDeleteEffect(fx, sheet));
            FrameLayout.LayoutParams delParams = new FrameLayout.LayoutParams(dp(28), dp(28));
            delParams.gravity = Gravity.TOP | Gravity.END;
            delParams.setMargins(0, dp(6), dp(6), 0);
            tile.addView(del, delParams);
        }
        return tile;
    }

    // ---------- your own effects ----------
    private void startAddEffect() {
        if (CustomEffectStore.load(this).size() >= CustomEffectStore.MAX) {
            Toast.makeText(this, "You can add up to " + CustomEffectStore.MAX
                    + " effects. Delete one first.", Toast.LENGTH_LONG).show();
            return;
        }
        effectFilePicker.launch(new String[]{"image/*", "video/mp4"});
    }

    // checks the picked file, then asks for a title
    private void handlePickedEffect(final Uri uri) {
        String type = getContentResolver().getType(uri);
        String mime = (type == null) ? "" : type.toLowerCase(Locale.ROOT);

        int kind;
        if (mime.startsWith("video/")) {
            if (!mime.equals("video/mp4")) {
                Toast.makeText(this, "Only mp4 videos are supported", Toast.LENGTH_SHORT).show();
                return;
            }
            kind = EffectBackground.KIND_VIDEO;
        } else if (mime.equals("image/gif")) {
            kind = EffectBackground.KIND_GIF;
            if (Build.VERSION.SDK_INT < 28) {
                kind = EffectBackground.KIND_IMAGE;   // older phones: the first picture only
                Toast.makeText(this, "GIFs show as a still picture on this phone",
                        Toast.LENGTH_LONG).show();
            }
        } else if (mime.startsWith("image/")) {
            kind = EffectBackground.KIND_IMAGE;
        } else {
            Toast.makeText(this, "That file type is not supported", Toast.LENGTH_SHORT).show();
            return;
        }

        final int finalKind = kind;
        if (finalKind != EffectBackground.KIND_VIDEO) {
            askEffectTitle(uri, finalKind);
            return;
        }

        // video: it can be 30 seconds at most
        EffectBackground.IO.execute(() -> {
            long durationMs = -1;
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(this, uri);
                String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (d != null) durationMs = Long.parseLong(d);
            } catch (Exception ignored) {
            }
            try {
                r.release();
            } catch (Exception ignored) {
            }
            final long dur = durationMs;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (dur < 0) {
                    Toast.makeText(this, "Couldn't read that video", Toast.LENGTH_SHORT).show();
                } else if (dur > 30500) {
                    Toast.makeText(this, "Videos can be 30 seconds at most",
                            Toast.LENGTH_LONG).show();
                } else {
                    askEffectTitle(uri, finalKind);
                }
            });
        });
    }

    private void askEffectTitle(final Uri uri, final int kind) {
        ModernDialog.input(this, "Name your effect", "For example: My beach", "", "Save",
                text -> saveCustomEffect(uri, kind, text));
    }

    // copies the file into the app, saves the title and starts using the effect
    private void saveCustomEffect(final Uri uri, final int kind, String text) {
        final String title = text.isEmpty() ? "My effect" : text;
        Toast.makeText(this, "Saving…", Toast.LENGTH_SHORT).show();
        EffectBackground.IO.execute(() -> {
            final EffectBackground.Fx fx = CustomEffectStore.add(this, title, uri, kind);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (fx == null) {
                    Toast.makeText(this, "Couldn't save that file (50 MB at most)",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                getSharedPreferences("now_playing", MODE_PRIVATE).edit()
                        .putString("fx", fx.key).apply();   // use it right away
                applyEffect();
                Toast.makeText(this, "Effect saved", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void confirmDeleteEffect(final EffectBackground.Fx fx, final BottomSheetDialog sheet) {
        ModernDialog.confirm(this, "Delete \"" + fx.label + "\"?",
                "This removes your effect from the app. Your original file is not touched.",
                "Delete", true, () -> {
                    if (effectBg != null) effectBg.hide();
                    android.content.SharedPreferences sp =
                            getSharedPreferences("now_playing", MODE_PRIVATE);
                    if (fx.key.equals(sp.getString("fx", ""))) {
                        sp.edit().putString("fx", "").apply();
                    }
                    CustomEffectStore.delete(this, fx.key);
                    sheet.dismiss();
                    applyEffect();
                    showEffectPicker();   // reopen the picker with the updated list
                });
    }

    // ---------- cover style: square picture or spinning vinyl ----------
    // only music can use the vinyl (podcasts and videos keep their own picture)
    private boolean isMusicMode() {
        return !podcastMode && textureVideo.getVisibility() != View.VISIBLE;
    }

    // tap the picture: switches style and remembers the choice
    private void toggleCoverStyle() {
        if (!isMusicMode()) return;
        hideCoverHint(true);   // they found it, so the hint is not needed any more
        coverStyle = (coverStyle + 1) % STYLE_COUNT;
        vinylStyle = (coverStyle == STYLE_VINYL);
        getSharedPreferences("now_playing", MODE_PRIVATE)
                .edit().putInt("coverStyle", coverStyle).apply();
        applyCoverStyle(true);
    }

    // shows the vinyl or the square cover, with a smooth switch when you tap
    private void applyCoverStyle(boolean animate) {
        // styles drawn by their own view (notes, cassette, tilt card)
        View active = isMusicMode() ? styleViewFor(coverStyle) : null;
        for (View v : new View[]{notes, cassette, tilt, emptyTap}) {
            if (v != active) {
                resetLook(v);
                v.setVisibility(View.GONE);
            }
        }
        if (active != null) {
            resetLook(vinyl);
            vinyl.setVisibility(View.GONE);
            vinyl.setSpinning(false);
            ivCover.animate().cancel();
            ivCover.setVisibility(View.INVISIBLE);
            eqBadge.setVisibility(View.GONE);
            if (active.getVisibility() != View.VISIBLE) {
                active.setVisibility(View.VISIBLE);
                if (animate) {
                    active.setAlpha(0f);
                    active.animate().alpha(1f).setDuration(350).start();
                }
            }
            updateStylePlaying();
            return;
        }
        updateStylePlaying();   // makes sure the hidden views stop animating

        // video: showVideo() already did the work
        if (textureVideo.getVisibility() == View.VISIBLE) {
            resetLook(vinyl);
            vinyl.setVisibility(View.GONE);
            vinyl.setSpinning(false);
            return;
        }

        if (vinylStyle && isMusicMode()) {
            eqBadge.setVisibility(View.GONE);   // the needle arm uses that corner
            if (vinyl.getVisibility() != View.VISIBLE) {
                resetLook(vinyl);
                vinyl.setVisibility(View.VISIBLE);
                if (animate) {
                    // the record drops in with a small bounce while the cover sinks away
                    vinyl.setAlpha(0f);
                    vinyl.setScaleX(0.8f);
                    vinyl.setScaleY(0.8f);
                    vinyl.animate().alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(520)
                            .setInterpolator(new OvershootInterpolator(1.2f))
                            .start();
                    ivCover.animate().cancel();
                    ivCover.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f)
                            .setDuration(260)
                            .withEndAction(() -> {
                                if (vinylStyle) ivCover.setVisibility(View.INVISIBLE);
                            }).start();
                } else {
                    ivCover.animate().cancel();
                    ivCover.setVisibility(View.INVISIBLE);
                }
            } else {
                ivCover.animate().cancel();
                ivCover.setVisibility(View.INVISIBLE);
            }
            updateVinylSpin();
        } else {
            eqBadge.setVisibility(View.VISIBLE);
            boolean wasVinyl = vinyl.getVisibility() == View.VISIBLE;
            vinyl.setSpinning(false);   // the needle lifts and the record winds down
            if (wasVinyl && animate) {
                ivCover.animate().cancel();
                ivCover.setVisibility(View.VISIBLE);
                ivCover.setAlpha(0f);
                ivCover.setScaleX(0.9f);
                ivCover.setScaleY(0.9f);
                ivCover.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(380)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                vinyl.animate().cancel();
                vinyl.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f)
                        .setDuration(300)
                        .withEndAction(() -> {
                            if (!vinylStyle) {
                                vinyl.setVisibility(View.GONE);
                                resetLook(vinyl);
                            }
                        }).start();
            } else {
                resetLook(vinyl);
                vinyl.setVisibility(View.GONE);
                resetLook(ivCover);
                ivCover.setVisibility(View.VISIBLE);
            }
        }
    }

    // puts a view back to its normal size and opacity
    private void resetLook(View v) {
        v.animate().cancel();
        v.setAlpha(1f);
        v.setScaleX(1f);
        v.setScaleY(1f);
    }

    // the record turns only while music plays and the screen is open
    private void updateVinylSpin() {
        MediaController c = PlayerConnection.get();
        vinyl.setSpinning(started && c != null && c.isPlaying()
                && vinyl.getVisibility() == View.VISIBLE);
    }

    // which view draws a style (null = cover or vinyl, which have their own code)
    private View styleViewFor(int style) {
        if (style == STYLE_NOTES) return notes;
        if (style == STYLE_CASSETTE) return cassette;
        if (style == STYLE_TILT) return tilt;
        if (style == STYLE_EMPTY) return emptyTap;
        return null;
    }

    // notes, reels and the card sway run only while music plays, the screen is open
    // and that style is the one on screen
    private void updateStylePlaying() {
        MediaController c = PlayerConnection.get();
        boolean on = started && c != null && c.isPlaying();
        notes.setPlaying(on && notes.getVisibility() == View.VISIBLE);
        cassette.setPlaying(on && cassette.getVisibility() == View.VISIBLE);
        tilt.setPlaying(on && tilt.getVisibility() == View.VISIBLE);
        emptyTap.setPlaying(on && emptyTap.getVisibility() == View.VISIBLE);
    }

    // reads the cover art and the format / bitrate / sample rate from the file itself
    private void loadFileInfo(int resId, int colorStart, int colorEnd) {
        Bitmap art = null;
        String format = "AUDIO";
        String bitrate = null;
        String sampleRate = null;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, currentUri != null
                    ? Uri.parse(currentUri)
                    : Uri.parse("android.resource://" + getPackageName() + "/" + resId));

            byte[] picture = retriever.getEmbeddedPicture();
            if (picture != null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;   // keeps memory small
                art = BitmapFactory.decodeByteArray(picture, 0, picture.length, options);
            }

            String mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            if (mime != null) {
                if (mime.contains("mpeg")) format = "MP3";
                else if (mime.contains("flac")) format = "FLAC";
                else if (mime.contains("mp4") || mime.contains("aac")) format = "AAC";
                else if (mime.contains("ogg")) format = "OGG";
                else if (mime.contains("wav")) format = "WAV";
            }

            String bitrateValue = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            if (bitrateValue != null) {
                bitrate = (Long.parseLong(bitrateValue) / 1000) + " KBPS";
            }

            if (Build.VERSION.SDK_INT >= 31) {
                String rateValue = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE);
                if (rateValue != null) {
                    sampleRate = String.format(Locale.US, "%.1f KHZ", Long.parseLong(rateValue) / 1000f);
                }
            }
        } catch (Exception ignored) {
        }
        try {
            retriever.release();
        } catch (Exception ignored) {
        }

        // cover: your own picture first, then the built-in art, then the gradient
        Bitmap thumb;
        if (currentThumbPath != null) {
            thumb = Thumbs.file(currentThumbPath, 900);
        } else {
            int thumbId = Thumbs.thumbForRaw(this, resId);
            thumb = (thumbId != 0) ? Thumbs.get(this, thumbId, 900) : null;
        }
        if (thumb != null) {
            ivCover.setImageBitmap(thumb);
        } else if (art != null) {
            ivCover.setImageBitmap(art);
        } else {
            ivCover.setImageDrawable(new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, new int[]{colorStart, colorEnd}));
        }

        // the same picture is the center label of the vinyl and the cover of the notes view
        vinyl.setLabel(thumb != null ? thumb : art, colorEnd);
        notes.setCover(thumb != null ? thumb : art, colorEnd);        notes.setCover(thumb != null ? thumb : art, colorEnd);
        cassette.setCover(thumb != null ? thumb : art, colorEnd);
        tilt.setCover(thumb != null ? thumb : art, colorEnd);
        // tags
        tvTagFormat.setText(bitrate != null ? format + " · " + bitrate : format);
        if (sampleRate != null) {
            tvTagRate.setText(sampleRate);
            tvTagRate.setVisibility(View.VISIBLE);
        } else {
            tvTagRate.setVisibility(View.GONE);
        }
    }

    // pink filled heart when the song is a favorite, lavender outline when not
    private void showHeart(boolean isLiked) {
        liked = isLiked;
        ivHeart.setImageResource(isLiked ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        ImageViewCompat.setImageTintList(ivHeart, ColorStateList.valueOf(
                isLiked ? Color.parseColor("#F472B6")
                        : ContextCompat.getColor(this, R.color.accent_lavender)));
    }

    // the file id of the song that is playing now (0 = nothing)
    private int currentResId() {
        MediaController c = PlayerConnection.get();
        MediaItem item = (c != null) ? c.getCurrentMediaItem() : null;
        if (item == null) return 0;
        try {
            return Integer.parseInt(item.mediaId);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean isPodcastFile(int resId) {
        if (CustomPodcastStore.isEpisode(resId)) return true;
        try {
            return getResources().getResourceEntryName(resId).startsWith("podcast_");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void finish() {
        super.finish();
        Anim.slideDownClose(this);
    }

    // ---------- the ⋮ menu ----------
    private void showMoreMenu() {
        final MediaController c = PlayerConnection.get();
        MediaItem item = (c != null) ? c.getCurrentMediaItem() : null;
        if (item == null) {
            Toast.makeText(this, "Play something first", Toast.LENGTH_SHORT).show();
            return;
        }
        final Song song = findAnySong(item.mediaId);
        final int resId = currentResId();

        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_now_menu, null);
        sheet.setContentView(view);
        sheet.setOnShowListener(d -> {
            View s = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (s != null) {
                s.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior.from(s).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        ((TextView) view.findViewById(R.id.tvMenuTitle)).setText(tvTitle.getText());
        ((TextView) view.findViewById(R.id.tvMenuArtist)).setText(tvArtist.getText());
        LinearLayout list = view.findViewById(R.id.menuRows);

        if (resId != 0) {
            boolean liked = FavoritesStore.isFavorite(this, resId);
            addMenuRow(list, liked ? R.drawable.ic_favorite : R.drawable.ic_favorite_border,
                    liked ? "Remove from Favorites" : "Add to Favorites", false, sheet, () -> {
                        boolean now = FavoritesStore.toggle(this, resId);
                        showHeart(now);
                        Anim.pop(ivHeart);
                        Toast.makeText(this, now ? "Added to Favorites"
                                : "Removed from Favorites", Toast.LENGTH_SHORT).show();
                    });
        }
        if (currentSong != null) {
            addMenuRow(list, R.drawable.ic_playlist_add, "Save to playlist", false, sheet,
                    () -> PlaylistDialogs.showSaveToPlaylist(this, currentSong, null));
        }
        addMenuRow(list, R.drawable.ic_music_note, "Lyrics", false, sheet,
                this::showLyrics);
        addMenuRow(list, R.drawable.ic_bolt, "Playback speed", false, sheet,
                this::showSpeedDialog);
        addMenuRow(list, R.drawable.ic_dark_mode, "Sleep timer", false, sheet,
                this::showSleepDialog);
        addMenuRow(list, R.drawable.ic_share, "Share", false, sheet,
                () -> findViewById(R.id.btnShare).performClick());
        if (song != null) {
            addMenuRow(list, R.drawable.ic_delete, "Delete", true, sheet,
                    () -> confirmDeleteCurrent(song));
        }
        sheet.show();
    }

    private void addMenuRow(LinearLayout list, int icon, String label, boolean danger,
                            final BottomSheetDialog sheet, final Runnable action) {
        int color = danger ? 0xFFF472B6
                : ContextCompat.getColor(this, R.color.text_primary);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(14), dp(8), dp(14));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            sheet.dismiss();
            action.run();
        });

        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(danger ? color
                : ContextCompat.getColor(this, R.color.text_secondary));
        row.addView(iv, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(16);
        tv.setTextColor(color);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.setMarginStart(dp(16));
        row.addView(tv, tp);

        list.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // looks for the playing item in songs (built-in and yours) and podcast episodes
    private Song findAnySong(String mediaId) {
        List<Song> all = new ArrayList<>(MusicLibrary.loadWithCustom(this));
        all.addAll(MusicLibrary.loadPodcasts(this));
        for (Song s : all) {
            if (String.valueOf(s.resId).equals(mediaId)) return s;
        }
        return null;
    }

    // your own: files are deleted. Official: it is hidden (Settings can restore it).
    // your own: files are deleted. Official: it is hidden (Settings can restore it).
    private void confirmDeleteCurrent(final Song song) {
        AppDialog.confirm(this, R.drawable.ic_delete, "Delete \"" + song.title + "\"?",
                song.isCustom()
                        ? "This removes it from the app. Your original file is not touched."
                        : "This removes it from the app. You can bring it back in Settings.",
                "Delete", true, () -> {
                    MediaController c = PlayerConnection.get();
                    if (c != null) c.clearMediaItems();
                    if (CustomPodcastStore.isEpisode(song.resId)) {
                        CustomPodcastStore.deleteEpisode(this, song);
                    } else if (song.isCustom()) {
                        CustomPlaylistStore.removeEverywhere(this, song);
                        CustomSongStore.delete(this, song);
                    } else {
                        CustomPlaylistStore.removeEverywhere(this, song);
                        HiddenSongStore.hide(this, song);
                    }
                    finish();
                });
    }

    // ---------- playback speed ----------
    // a "1x" button just before the volume button
    private void addSpeedButton() {
        ViewGroup row = (ViewGroup) findViewById(R.id.btnSleep).getParent();
        tvSpeedBtn = new TextView(this);
        tvSpeedBtn.setText("1x");
        tvSpeedBtn.setTextSize(14);
        tvSpeedBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSpeedBtn.setGravity(Gravity.CENTER);
        tvSpeedBtn.setMinWidth(dp(44));
        tvSpeedBtn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tvSpeedBtn.setContentDescription("Playback speed");
        tvSpeedBtn.setClickable(true);
        tvSpeedBtn.setOnClickListener(v -> showSpeedDialog());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        params.gravity = Gravity.CENTER_VERTICAL;
        row.addView(tvSpeedBtn, Math.max(0, row.getChildCount() - 1), params);
    }

    private void showSpeedDialog() {
        final MediaController c = PlayerConnection.get();
        if (c == null) {
            Toast.makeText(this, "Play something first", Toast.LENGTH_SHORT).show();
            return;
        }

        final BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_speed, null);
        dialog.setContentView(view);
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundColor(Color.TRANSPARENT);   // our own rounded background shows
                BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        final TextView tvValue = view.findViewById(R.id.tvSpeedValue);
        final SeekBar seek = view.findViewById(R.id.seekSpeed);
        final LinearLayout chipRow = view.findViewById(R.id.speedChips);
        final TextView[] chips = new TextView[SpeedStore.PRESETS.length];

        // quick-pick chips, with "Normal" under 1.0
        for (int i = 0; i < chips.length; i++) {
            final float preset = SpeedStore.PRESETS[i];

            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cp.setMargins(dp(4), 0, dp(4), 0);

            TextView chip = new TextView(this);
            chip.setText(String.valueOf(preset));
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            chip.setClickable(true);
            chip.setOnClickListener(v -> applySheetSpeed(c, preset, tvValue, seek, chips));
            column.addView(chip, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

            TextView caption = new TextView(this);
            caption.setText(preset == 1f ? "Normal" : "");
            caption.setTextSize(10);
            caption.setGravity(Gravity.CENTER);
            caption.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
            LinearLayout.LayoutParams capParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            capParams.topMargin = dp(4);
            column.addView(caption, capParams);

            chips[i] = chip;
            chipRow.addView(column, cp);
        }

        seek.setMax(SpeedStore.STEPS);
        float start = SpeedStore.clamp(c.getPlaybackParameters().speed);
        seek.setProgress(SpeedStore.toProgress(start));
        tvValue.setText(SpeedStore.title(start));
        paintSpeedChips(chips, start);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                applySheetSpeed(c, SpeedStore.fromProgress(progress), tvValue, seek, chips);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });

        view.findViewById(R.id.btnSpeedMinus).setOnClickListener(v -> applySheetSpeed(c,
                SpeedStore.clamp(SpeedStore.fromProgress(seek.getProgress()) - SpeedStore.STEP),
                tvValue, seek, chips));
        view.findViewById(R.id.btnSpeedPlus).setOnClickListener(v -> applySheetSpeed(c,
                SpeedStore.clamp(SpeedStore.fromProgress(seek.getProgress()) + SpeedStore.STEP),
                tvValue, seek, chips));

        dialog.show();
    }

    // sets the speed on the player and updates everything in the sheet
    private void applySheetSpeed(MediaController c, float speed, TextView tvValue,
                                 SeekBar seek, TextView[] chips) {
        speed = SpeedStore.clamp(speed);
        SpeedStore.set(this, speed);
        c.setPlaybackSpeed(speed);
        tvValue.setText(SpeedStore.title(speed));
        int progress = SpeedStore.toProgress(speed);
        if (seek.getProgress() != progress) seek.setProgress(progress);
        paintSpeedChips(chips, speed);
        updateSpeedLabel();
    }

    // the chip that matches the current speed is highlighted
    private void paintSpeedChips(TextView[] chips, float speed) {
        for (int i = 0; i < chips.length; i++) {
            boolean on = Math.abs(SpeedStore.PRESETS[i] - speed) < 0.001f;
            chips[i].setBackgroundResource(on ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chips[i].setTextColor(ContextCompat.getColor(this,
                    on ? R.color.text_primary : R.color.text_secondary));
            chips[i].setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    // the button shows the speed, in cyan when it is not normal
    private void updateSpeedLabel() {
        MediaController c = PlayerConnection.get();
        if (c == null || tvSpeedBtn == null) return;
        float speed = c.getPlaybackParameters().speed;
        tvSpeedBtn.setText(SpeedStore.label(speed));
        boolean normal = Math.abs(speed - 1f) < 0.01f;
        tvSpeedBtn.setTextColor(ContextCompat.getColor(this,
                normal ? R.color.text_secondary : R.color.accent_cyan_light));
    }

    // ---------- lyrics ----------
    // a small note button at the end of the Share / Save / Sleep row
    private void addLyricsButton() {
        // goes in the title row, right before the heart
        ViewGroup row = (ViewGroup) ivHeart.getParent();
        ImageView lyrics = new ImageView(this);
        lyrics.setImageResource(R.drawable.ic_music_note);
        lyrics.setColorFilter(ContextCompat.getColor(this, R.color.accent_lavender));
        lyrics.setBackgroundResource(R.drawable.bg_play_btn);
        lyrics.setContentDescription("Lyrics");
        lyrics.setPadding(dp(11), dp(11), dp(11), dp(11));
        lyrics.setClickable(true);
        lyrics.setFocusable(true);
        lyrics.setOnClickListener(v -> showLyrics());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
        params.gravity = Gravity.CENTER_VERTICAL;
        params.setMarginStart(dp(12));
        row.addView(lyrics, row.indexOfChild(ivHeart), params);
    }

    private void showLyrics() {
        if (lyricsSheet == null) lyricsSheet = new LyricsSheet(this);
        lyricsSheet.show();
    }

    // ---------- volume (the phone's music volume) ----------
    // a small speaker button at the end of the Share / Save / Sleep row
    private void addVolumeButton() {
        ViewGroup row = (ViewGroup) findViewById(R.id.btnSleep).getParent();
        ImageView volume = new ImageView(this);
        volume.setImageResource(R.drawable.ic_volume_up);
        volume.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
        volume.setContentDescription("Volume");
        volume.setPadding(dp(10), dp(10), dp(10), dp(10));
        volume.setClickable(true);
        volume.setOnClickListener(v -> showVolumeDialog());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(40));
        params.gravity = Gravity.CENTER_VERTICAL;
        row.addView(volume, params);
    }

    // pop-up with the volume slider and the mute button
    private void showVolumeDialog() {
        final AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        final int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        View view = getLayoutInflater().inflate(R.layout.dialog_volume, null);
        final SeekBar seek = view.findViewById(R.id.seekVolume);
        final ImageView ivMute = view.findViewById(R.id.ivMute);
        final TextView tvPercent = view.findViewById(R.id.tvVolumePercent);

        seek.setMax(max);
        int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        seek.setProgress(current);
        paintVolume(ivMute, tvPercent, current, max);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                setMusicVolume(audio, progress);
                paintVolume(ivMute, tvPercent, progress, max);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });

        // mute: remembers the level, unmute: brings it back
        ivMute.setOnClickListener(v -> {
            int now = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int target;
            if (now > 0) {
                AppSettings.setVolumeBeforeMute(this, now);
                target = 0;
            } else {
                target = Math.min(max, Math.max(1, AppSettings.volumeBeforeMute(this, max / 2)));
            }
            setMusicVolume(audio, target);
            seek.setProgress(target);
            paintVolume(ivMute, tvPercent, target, max);
        });

        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private void setMusicVolume(AudioManager audio, int volume) {
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
        } catch (SecurityException ignored) {
        }
    }

    private void paintVolume(ImageView icon, TextView percent, int volume, int max) {
        icon.setImageResource(volume == 0 ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);
        percent.setText(volume == 0 ? "Muted" : (volume * 100 / max) + "%");
    }

    // podcast episodes get a bigger picture/video and a description card under it
    private void showPodcastMode(int resId, @Nullable CharSequence show) {
        String fileName = "";
        try {
            fileName = getResources().getResourceEntryName(resId);
        } catch (Exception ignored) {
        }
        boolean podcast = fileName.startsWith("podcast_") || CustomPodcastStore.isEpisode(resId);

        // the pill at the top
        tvPlayingFrom.setText(podcast ? "NOW PLAYING PODCAST" : "PLAYING FROM LIBRARY");
        String label = "Your Music";
        if (podcast && show != null) {
            int ep = PodcastShows.episodeNumberOf(this, resId);
            label = show + (ep > 0 ? "  •  EP " + ep : "");
        }
        tvPlayingFromName.setText(label);

        podcastMode = podcast;
        // no background effects in the podcast section, so the effects button is hidden there
        findViewById(R.id.btnEffects).setVisibility(podcast ? View.GONE : View.VISIBLE);
        svDescription.setVisibility(podcast && !fullscreen && !inPip ? View.VISIBLE : View.GONE);
        if (!podcast) {
            setWeight(coverArea, 1f);   // music: the cover takes all the free space
            return;
        }

        // podcast: the episode list gets a bit more room than the video
        setWeight(coverArea, 1f);
        setWeight(svDescription, 1.3f);

        if (show != null) loadEpisodes(show.toString());

        // the description is the string named exactly like the file in res/raw
        String description = null;
        if (CustomPodcastStore.isEpisode(resId)) {
            description = CustomPodcastStore.descriptionOf(this, resId);
        } else {
            int stringId = getResources().getIdentifier(fileName, "string", getPackageName());
            if (stringId != 0) description = getString(stringId);
        }
        tvDescription.setText(description != null && !description.isEmpty()
                ? description : "No description yet for this episode.");
        svDescription.scrollTo(0, 0);
    }

    // builds the episode list of the show that is playing (only when the show changes)
    private void loadEpisodes(String showName) {
        if (showName.equals(episodesShow)) return;
        episodesShow = showName;
        lastScrolledId = null;
        episodeList = new ArrayList<>();
        for (PodcastShows.Show s : PodcastShows.group(this, MusicLibrary.loadPodcasts(this))) {
            if (s.name.equals(showName)) {
                episodeList = s.episodes;
                break;
            }
        }
        tvEpisodesLabel.setText("Episodes  (" + episodeList.size() + ")");
        episodeAdapter = new EpisodeAdapter(episodeList, new EpisodeAdapter.Listener() {
            @Override
            public void onEpisodeClick(int position) {
                // tap on the row: switch to that episode (we are already on Now Playing)
                if (!isCurrentEpisode(episodeList.get(position))) playEpisode(position);
            }

            @Override
            public void onPlayClick(int position) {
                MediaController c = PlayerConnection.get();
                if (isCurrentEpisode(episodeList.get(position)) && c != null) {
                    if (c.isPlaying()) c.pause(); else c.play();
                } else {
                    playEpisode(position);
                }
            }
        });
        rvNpEpisodes.setAdapter(episodeAdapter);
    }

    private void playEpisode(int position) {
        MainActivity.activePlaylistName = null;
        PlayerConnection.play(this, new ArrayList<>(episodeList), position);
    }

    private boolean isCurrentEpisode(Song song) {
        MediaController c = PlayerConnection.get();
        MediaItem item = (c != null) ? c.getCurrentMediaItem() : null;
        return item != null && String.valueOf(song.resId).equals(item.mediaId);
    }

    // highlights the playing episode (cyan title, pause icon)
    private void updateEpisodePlayback(Player p) {
        if (episodeAdapter == null || !podcastMode) return;
        MediaItem item = p.getCurrentMediaItem();
        String id = (item != null) ? item.mediaId : null;
        episodeAdapter.setPlaybackState(id, p.isPlaying());

        // smoothly scroll to the episode that is playing (only when the episode changes)
        if (id != null && !id.equals(lastScrolledId)) {
            lastScrolledId = id;
            for (int i = 0; i < episodeList.size(); i++) {
                if (String.valueOf(episodeList.get(i).resId).equals(id)) {
                    rvNpEpisodes.smoothScrollToPosition(i);
                    break;
                }
            }
        }
    }

    private void setWeight(View view, float weight) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.weight = weight;
        view.setLayoutParams(params);
    }

    // ---------- floating video window (picture-in-picture) ----------
    private void enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this, "Floating window is not supported on this phone",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        MediaController c = PlayerConnection.get();
        if (c == null || !c.isPlaying()) {
            Toast.makeText(this, "Play the video first", Toast.LENGTH_SHORT).show();
            return;
        }
        enterPictureInPictureMode(buildPipParams());
    }

    // the window shape and the 3 buttons (previous, play/pause, next)
    @RequiresApi(Build.VERSION_CODES.O)
    private PictureInPictureParams buildPipParams() {
        MediaController c = PlayerConnection.get();
        boolean playing = c != null && c.isPlaying();

        // keep the window the same shape as the video
        int w = 16;
        int h = 9;
        if (c != null) {
            VideoSize size = c.getVideoSize();
            if (size.width > 0 && size.height > 0) {
                w = Math.round(size.width * size.pixelWidthHeightRatio);
                h = size.height;
            }
        }
        // Android only accepts shapes between 1:2.39 and 2.39:1
        float ratio = w / (float) h;
        if (ratio > 2.39f) {
            w = 239;
            h = 100;
        } else if (ratio < 0.42f) {
            w = 42;
            h = 100;
        }

        List<RemoteAction> actions = new ArrayList<>();
        actions.add(pipAction(R.drawable.ic_skip_previous, "Previous", PIP_PREV));
        actions.add(pipAction(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                playing ? "Pause" : "Play", PIP_PLAY));
        actions.add(pipAction(R.drawable.ic_skip_next, "Next", PIP_NEXT));

        return new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(w, h))
                .setActions(actions)
                .build();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private RemoteAction pipAction(int iconRes, String title, int control) {
        Intent intent = new Intent(PIP_ACTION)
                .setPackage(getPackageName())
                .putExtra(PIP_EXTRA, control);
        PendingIntent pending = PendingIntent.getBroadcast(this, control, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Icon icon = Icon.createWithResource(this, iconRes).setTint(Color.WHITE);
        return new RemoteAction(icon, title, title, pending);
    }

    // updates the buttons and the shape while the floating window is open
    private void refreshPip() {
        if (!inPip || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        setPictureInPictureParams(buildPipParams());
    }

    // the system calls this when the floating window opens or closes
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
                                              Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        inPip = isInPictureInPictureMode;

        if (inPip) {
            ContextCompat.registerReceiver(this, pipReceiver, new IntentFilter(PIP_ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
        } else {
            try {
                unregisterReceiver(pipReceiver);
            } catch (IllegalArgumentException ignored) {
            }
        }
        showOnlyVideo(inPip);
    }

    // the floating window shows only the video, the normal screen comes back after
    private void showOnlyVideo(boolean on) {
        if (fullscreen) return;

        for (int i = 0; i < contentArea.getChildCount(); i++) {
            View child = contentArea.getChildAt(i);
            if (child != coverArea) child.setVisibility(on ? View.GONE : View.VISIBLE);
        }
        contentArea.setPadding(on ? 0 : dp(20), on ? 0 : dp(4), on ? 0 : dp(20), on ? 0 : dp(8));
        setCoverMargins(on ? 0 : 10, on ? 0 : 6);
        coverArea.setBackgroundColor(on ? Color.BLACK : Color.TRANSPARENT);
        textureVideo.setClipToOutline(!on);

        boolean video = textureVideo.getVisibility() == View.VISIBLE;
        btnExpand.setVisibility(!on && video ? View.VISIBLE : View.GONE);
        btnPip.setVisibility(!on && video && AppSettings.floatingWindow(this)
                ? View.VISIBLE : View.GONE);
        svDescription.setVisibility(!on && podcastMode ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(pipReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDestroy();
    }

    // ---------- full screen video ----------
    private void setupFullscreen() {
        contentArea = findViewById(R.id.contentArea);
        btnExpand = findViewById(R.id.btnExpand);
        fsOverlay = findViewById(R.id.fsOverlay);
        fsControls = findViewById(R.id.fsControls);
        fsPlayIcon = findViewById(R.id.fsPlayIcon);
        fsElapsed = findViewById(R.id.fsElapsed);
        fsRemaining = findViewById(R.id.fsRemaining);
        fsSeek = findViewById(R.id.fsSeek);

        // the full screen controls sit on the dark video, so they stay light in both modes
        ImageViewCompat.setImageTintList((ImageView) findViewById(R.id.fsPrev),
                ColorStateList.valueOf(Color.WHITE));
        ImageViewCompat.setImageTintList((ImageView) findViewById(R.id.fsNext),
                ColorStateList.valueOf(Color.WHITE));
        fsElapsed.setTextColor(0xFF22D3EE);
        fsRemaining.setTextColor(0xFFC9C7D8);

        // the normal screen stays upright; only full screen follows the phone
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getOnBackPressedDispatcher().addCallback(this, backCallback);

        btnExpand.setOnClickListener(v -> enterFullscreen());
        btnPip = findViewById(R.id.btnPip);
        btnPip.setOnClickListener(v -> enterPip());
        findViewById(R.id.btnExitFs).setOnClickListener(v -> exitFullscreen());

        // tap the picture: hide or show the controls
        fsOverlay.setOnClickListener(v -> fsControls.setVisibility(
                fsControls.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        findViewById(R.id.fsPlayPause).setOnClickListener(v -> {
            MediaController c = PlayerConnection.get();
            if (c == null) return;
            if (c.isPlaying()) c.pause(); else c.play();
        });
        findViewById(R.id.fsNext).setOnClickListener(v -> {
            MediaController c = PlayerConnection.get();
            if (c != null) c.seekToNext();
        });
        findViewById(R.id.fsPrev).setOnClickListener(v -> {
            MediaController c = PlayerConnection.get();
            if (c != null) c.seekToPrevious();
        });

        fsSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                MediaController c = PlayerConnection.get();
                if (c != null && c.getDuration() > 0) {
                    long position = c.getDuration() * progress / 1000;
                    fsElapsed.setText(formatTime(position));
                    fsRemaining.setText("-" + formatTime(Math.max(0, c.getDuration() - position)));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                fsDragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                MediaController c = PlayerConnection.get();
                if (c != null && c.getDuration() > 0) {
                    c.seekTo(c.getDuration() * bar.getProgress() / 1000);
                }
                fsDragging = false;
            }
        });
    }

    private void enterFullscreen() {
        if (fullscreen) return;
        fullscreen = true;
        backCallback.setEnabled(true);

        // hide everything except the video area
        for (int i = 0; i < contentArea.getChildCount(); i++) {
            View child = contentArea.getChildAt(i);
            if (child != coverArea) child.setVisibility(View.GONE);
        }

        // no padding, no margins, square corners: the video uses the whole screen
        contentArea.setPadding(0, 0, 0, 0);
        setCoverMargins(0, 0);
        coverArea.setBackgroundColor(Color.BLACK);
        textureVideo.setClipToOutline(false);

        btnExpand.setVisibility(View.GONE);
        btnPip.setVisibility(View.GONE);
        fsControls.setVisibility(View.VISIBLE);
        fsOverlay.setVisibility(View.VISIBLE);

        // hide the status and navigation bars, follow how the phone is held
        WindowInsetsControllerCompat bars =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.hide(WindowInsetsCompat.Type.systemBars());
        bars.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        updateFullscreenProgress();
    }

    private void exitFullscreen() {
        if (!fullscreen) return;
        fullscreen = false;
        backCallback.setEnabled(false);

        fsOverlay.setVisibility(View.GONE);
        coverArea.setBackgroundColor(Color.TRANSPARENT);
        textureVideo.setClipToOutline(true);
        setCoverMargins(10, 6);
        contentArea.setPadding(dp(20), dp(4), dp(20), dp(8));

        for (int i = 0; i < contentArea.getChildCount(); i++) {
            contentArea.getChildAt(i).setVisibility(View.VISIBLE);
        }
        // the description card shows only for podcasts
        svDescription.setVisibility(podcastMode ? View.VISIBLE : View.GONE);
        btnExpand.setVisibility(
                textureVideo.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);

        WindowInsetsControllerCompat bars =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.hide(WindowInsetsCompat.Type.systemBars());
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        btnPip.setVisibility(AppSettings.floatingWindow(this)
                ? btnExpand.getVisibility() : View.GONE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    // moves the full screen seek bar and its times (called every 0.3 seconds)
    private void updateFullscreenProgress() {
        if (!fullscreen || fsDragging) return;
        MediaController c = PlayerConnection.get();
        if (c == null) return;

        long duration = c.getDuration();
        long position = c.getCurrentPosition();
        if (duration > 0) fsSeek.setProgress((int) (1000L * position / duration));
        fsElapsed.setText(formatTime(position));
        fsRemaining.setText(duration > 0
                ? "-" + formatTime(Math.max(0, duration - position)) : "-00:00");
    }

    private void setCoverMargins(int topDp, int bottomDp) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) coverArea.getLayoutParams();
        params.topMargin = dp(topDp);
        params.bottomMargin = dp(bottomDp);
        coverArea.setLayoutParams(params);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    // video: the video replaces the cover. Audio: the cover comes back.
    private void showVideo(boolean show) {
        if (!show && fullscreen) exitFullscreen();   // the next item has no video
        btnExpand.setVisibility(show && !fullscreen && !inPip ? View.VISIBLE : View.GONE);
        btnPip.setVisibility(show && !fullscreen && !inPip
                && AppSettings.floatingWindow(this) ? View.VISIBLE : View.GONE);
        textureVideo.setVisibility(show ? View.VISIBLE : View.GONE);
        ivCover.setVisibility(show ? View.GONE : View.VISIBLE);
        eqBadge.setVisibility(show ? View.GONE : View.VISIBLE);

        if (show) {
            MediaController c = PlayerConnection.get();
            if (c != null) applyVideoSize(c.getVideoSize());
        }
    }

    // keeps the real proportions of the video (no stretching)
    private void applyVideoSize(VideoSize size) {
        if (size.width == 0 || size.height == 0) return;

        float width = size.width * size.pixelWidthHeightRatio;
        float height = size.height;

        ConstraintLayout.LayoutParams params =
                (ConstraintLayout.LayoutParams) textureVideo.getLayoutParams();
        params.dimensionRatio = width + ":" + height;
        textureVideo.setLayoutParams(params);
        refreshPip();
    }

    // turns the video picture on or off (the sound is never affected)
    private void setVideoEnabled(MediaController c, boolean enabled) {
        c.setTrackSelectionParameters(c.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
                .build());
    }

    private void showTimes(long positionMs, long durationMs) {
        tvElapsed.setText(formatTime(positionMs));
        tvRemaining.setText(durationMs > 0
                ? "-" + formatTime(Math.max(0, durationMs - positionMs)) : "-00:00");
    }

    private String formatTime(long ms) {
        long totalSeconds = Math.max(0, ms) / 1000;
        return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private void tint(ImageView view, int colorRes) {
        ImageViewCompat.setImageTintList(view,
                ColorStateList.valueOf(ContextCompat.getColor(this, colorRes)));
    }
}