package com.appdev.mvpplay;

import android.animation.ObjectAnimator;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class SearchActivity extends BaseActivity {

    private List<Song> allSongs = new ArrayList<>();
    private List<Song> podcasts = new ArrayList<>();
    private List<Song> customSongs = new ArrayList<>();
    private EditText etSearch;
    private View btnClear;
    private ImageView btnVoice;
    private ObjectAnimator micPulse;
    private TextView tvHint, labelSongs, labelArtists, labelPodcasts, labelPlaylists;
    private RecyclerView rvSongs, rvArtists, rvPodcasts, rvPlaylists;
    private SongAdapter songAdapter;
    private PodcastAdapter podcastAdapter;
    private BottomNav bottomNav;
    private boolean started = false;

    // built-in songs + the songs the user added
    private List<Song> searchPool() {
        List<Song> pool = new ArrayList<>(allSongs);
        pool.addAll(customSongs);
        return pool;
    }

    private final ActivityResultLauncher<Intent> voiceLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                setVoiceActive(false);
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData()
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        String spoken = matches.get(0);
                        etSearch.setText(spoken);
                        etSearch.setSelection(spoken.length());
                    }
                }
            });

    private void startVoiceSearch() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a song, artist, or podcast");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        setVoiceActive(true);
        try {
            voiceLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            setVoiceActive(false);
            Toast.makeText(this, "Voice search isn't supported on this device",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void setVoiceActive(boolean active) {
        if (active) {
            btnVoice.setBackgroundResource(R.drawable.bg_circle_cyan);
            btnVoice.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            micPulse = ObjectAnimator.ofFloat(btnVoice, "alpha", 1f, 0.4f);
            micPulse.setDuration(600);
            micPulse.setRepeatCount(ObjectAnimator.INFINITE);
            micPulse.setRepeatMode(ObjectAnimator.REVERSE);
            micPulse.start();
        } else {
            if (micPulse != null) {
                micPulse.cancel();
                micPulse = null;
            }
            btnVoice.setAlpha(1f);
            btnVoice.setBackground(null);
            btnVoice.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.text_hint)));
        }
    }

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
        setContentView(R.layout.activity_search);

        WindowInsetsControllerCompat bars =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);

        etSearch = findViewById(R.id.etSearch);
        btnClear = findViewById(R.id.btnClear);
        btnVoice = findViewById(R.id.btnVoice);
        tvHint = findViewById(R.id.tvHint);
        labelSongs = findViewById(R.id.labelSongs);
        labelArtists = findViewById(R.id.labelArtists);
        labelPodcasts = findViewById(R.id.labelPodcasts);
        labelPlaylists = findViewById(R.id.labelPlaylists);
        rvSongs = findViewById(R.id.rvSongs);
        rvArtists = findViewById(R.id.rvArtists);
        rvPodcasts = findViewById(R.id.rvPodcasts);
        rvPlaylists = findViewById(R.id.rvPlaylists);
        for (RecyclerView rv : new RecyclerView[]{rvSongs, rvArtists, rvPodcasts, rvPlaylists}) {
            rv.setLayoutManager(new LinearLayoutManager(this));
            rv.setNestedScrollingEnabled(false);
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                search(s.toString());
            }
        });
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });
        btnClear.setOnClickListener(v -> etSearch.setText(""));
        btnVoice.setOnClickListener(v -> startVoiceSearch());

        bottomNav = new BottomNav(this, BottomNav.SEARCH);
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        allSongs = MusicLibrary.load(this);
        podcasts = MusicLibrary.loadPodcasts(this);
        customSongs = CustomSongStore.load(this);
        search(etSearch.getText().toString());
        bottomNav.onStart();
        PlayerConnection.connect(this, c -> {
            if (!started) return;
            c.addListener(playerListener);
            refreshPlayback();
        });
    }

    @Override
    protected void onStop() {
        started = false;
        MediaController c = PlayerConnection.get();
        if (c != null) c.removeListener(playerListener);
        bottomNav.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (micPulse != null) micPulse.cancel();
        super.onDestroy();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
    }

    // ---------- searching ----------
    private void search(String raw) {
        btnClear.setVisibility(raw.isEmpty() ? View.GONE : View.VISIBLE);
        btnVoice.setVisibility(raw.isEmpty() ? View.VISIBLE : View.GONE);
        String q = raw.trim().toLowerCase(Locale.ROOT);

        List<Song> songs = new ArrayList<>();
        List<ArtistAdapter.Artist> artists = new ArrayList<>();
        List<Song> episodes = new ArrayList<>();
        List<Playlist> lists = new ArrayList<>();

        if (!q.isEmpty()) {
            for (Song s : searchPool()) {
                if (s.title.toLowerCase(Locale.ROOT).contains(q)
                        || s.artist.toLowerCase(Locale.ROOT).contains(q)) {
                    songs.add(s);
                }
            }

            // artists whose name matches, with all of their songs
            Map<String, List<Song>> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Song s : searchPool()) {
                if (!s.artist.toLowerCase(Locale.ROOT).contains(q)) continue;
                List<Song> list = groups.get(s.artist);
                if (list == null) {
                    list = new ArrayList<>();
                    groups.put(s.artist, list);
                }
                list.add(s);
            }
            for (Map.Entry<String, List<Song>> entry : groups.entrySet()) {
                artists.add(new ArtistAdapter.Artist(entry.getKey(), entry.getValue()));
            }

            // podcast episodes: the episode name or the show name
            for (Song e : podcasts) {
                if (e.title.toLowerCase(Locale.ROOT).contains(q)
                        || e.artist.toLowerCase(Locale.ROOT).contains(q)) {
                    episodes.add(e);
                }
            }

            List<Playlist> everything = new ArrayList<>(PlaylistLibrary.build(allSongs));
            everything.addAll(CustomPlaylistStore.load(this, allSongs));
            for (Playlist p : everything) {
                if (p.name.toLowerCase(Locale.ROOT).contains(q)) lists.add(p);
            }
        }

        showSection(labelSongs, rvSongs, "Songs", songs.size());
        songAdapter = songs.isEmpty() ? null : new SongAdapter(songs, songListener(songs));
        rvSongs.setAdapter(songAdapter);

        showSection(labelArtists, rvArtists, "Artists", artists.size());
        rvArtists.setAdapter(artists.isEmpty() ? null : new ArtistAdapter(artists, artist -> {
            Intent intent = new Intent(SearchActivity.this, PlaylistActivity.class);
            intent.putExtra("name", artist.name);
            intent.putExtra("artist", true);
            startActivity(intent);
        }));

        showSection(labelPodcasts, rvPodcasts, "Podcasts", episodes.size());
        podcastAdapter = episodes.isEmpty() ? null
                : new PodcastAdapter(episodes, podcastListener(episodes));
        rvPodcasts.setAdapter(podcastAdapter);

        showSection(labelPlaylists, rvPlaylists, "Playlists", lists.size());
        rvPlaylists.setAdapter(lists.isEmpty() ? null : new PlaylistRowAdapter(lists, playlist -> {
            Intent intent = new Intent(SearchActivity.this, PlaylistActivity.class);
            intent.putExtra("name", playlist.name);
            startActivity(intent);
        }));

        boolean none = songs.isEmpty() && artists.isEmpty() && episodes.isEmpty() && lists.isEmpty();
        if (q.isEmpty()) {
            tvHint.setText("Type to search your songs, artists, podcasts and playlists.");
        } else {
            tvHint.setText("No results for \"" + raw.trim() + "\"");
        }
        tvHint.setVisibility(none ? View.VISIBLE : View.GONE);
        refreshPlayback();
    }

    private void showSection(TextView label, RecyclerView list, String title, int count) {
        label.setText(title + "  (" + count + ")");
        int visibility = count == 0 ? View.GONE : View.VISIBLE;
        label.setVisibility(visibility);
        list.setVisibility(visibility);
    }

    // ---------- taps ----------
    private SongAdapter.OnSongClickListener songListener(final List<Song> list) {
        return new SongAdapter.OnSongClickListener() {
            @Override
            public void onSongClick(int position) {
                Song tapped = list.get(position);
                if (!isCurrentSong(tapped)) playQueue(list, position);
                openNowPlaying();
            }

            @Override
            public void onPlayClick(int position) {
                Song tapped = list.get(position);
                MediaController c = PlayerConnection.get();
                if (isCurrentSong(tapped) && c != null) {
                    if (c.isPlaying()) c.pause(); else c.play();
                } else {
                    playQueue(list, position);
                }
            }

            @Override
            public void onSaveClick(int position) {
                PlaylistDialogs.showSaveToPlaylist(SearchActivity.this, list.get(position), null);
            }

            @Override
            public void onSongLongClick(int position) {
                PlaylistDialogs.showSaveToPlaylist(SearchActivity.this, list.get(position), null);
            }
        };
    }

    private PodcastAdapter.Listener podcastListener(final List<Song> list) {
        return new PodcastAdapter.Listener() {
            @Override
            public void onEpisodeClick(int position) {
                Song episode = list.get(position);
                if (!isCurrentSong(episode)) playQueue(list, position);
                openNowPlaying();
            }

            @Override
            public void onListenClick(int position) {
                Song episode = list.get(position);
                if (episode.hasVideo) {
                    if (!isCurrentSong(episode)) playQueue(list, position);
                    openNowPlaying();
                    return;
                }
                MediaController c = PlayerConnection.get();
                if (isCurrentSong(episode) && c != null) {
                    if (c.isPlaying()) c.pause(); else c.play();
                } else {
                    playQueue(list, position);
                }
            }
        };
    }

    private void playQueue(List<Song> queue, int position) {
        MainActivity.activePlaylistName = null;
        PlayerConnection.play(this, queue, position);
    }

    private boolean isCurrentSong(Song song) {
        MediaController c = PlayerConnection.get();
        MediaItem item = (c != null) ? c.getCurrentMediaItem() : null;
        return item != null && String.valueOf(song.resId).equals(item.mediaId);
    }

    private void openNowPlaying() {
        startActivity(new Intent(this, NowPlayingActivity.class));
    }

    private void refreshPlayback() {
        MediaController c = PlayerConnection.get();
        if (c == null) return;
        MediaItem item = c.getCurrentMediaItem();
        String mediaId = (item != null) ? item.mediaId : null;
        if (songAdapter != null) songAdapter.setPlaybackState(mediaId, c.isPlaying());
        if (podcastAdapter != null) podcastAdapter.setPlaybackState(mediaId, c.isPlaying());
    }
}