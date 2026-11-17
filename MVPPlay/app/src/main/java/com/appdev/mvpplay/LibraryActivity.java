package com.appdev.mvpplay;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// the Library tab: Songs, Artists, Favorites and Podcasts
public class LibraryActivity extends BaseActivity {

    private static final String[] EMPTY_TEXT = {
            "No songs yet. Add songs to res/raw.",
            "No artists yet.",
            "No favorites yet. Open a song and tap the heart to add it here.",
            "No podcast episodes yet."};

    private List<Song> allSongs = new ArrayList<>();
    private List<Song> podcasts = new ArrayList<>();
    private RecyclerView rv;
    private TextView tvCount, tvEmpty;
    private TextView[] chips;
    private SongAdapter songAdapter;
    private PodcastAdapter podcastAdapter;
    private int currentChip = 0;
    private BottomNav bottomNav;
    private boolean started = false;
    private SwipeRefreshLayout swipeRefresh;

    // adding your own song
    private View btnAddSong;
    private View btnAddPodcast;
    private final PodcastHelper podcastHelper = new PodcastHelper(this, this::onPodcastsChanged);
    private Uri pendingThumb;
    private ImageView dialogThumb;

    private final ActivityResultLauncher<String[]> audioPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) showAddSongDialog(uri);
            });

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    pendingThumb = uri;
                    if (dialogThumb != null) dialogThumb.setImageURI(uri);
                }
            });

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
        setContentView(R.layout.activity_library);

        WindowInsetsControllerCompat bars =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);

        rv = findViewById(R.id.rvLibrary);
        tvCount = findViewById(R.id.tvCount);
        tvEmpty = findViewById(R.id.tvEmpty);
        rv.setLayoutManager(new LinearLayoutManager(this));

        chips = new TextView[]{findViewById(R.id.chipSongs), findViewById(R.id.chipArtists),
                findViewById(R.id.chipFavorites), findViewById(R.id.chipPodcasts)};
        for (int i = 0; i < chips.length; i++) {
            final int index = i;
            chips[i].setOnClickListener(v -> {
                Anim.pop(v);
                selectChip(index);
                Anim.runList(rv);
            });
        }

        bottomNav = new BottomNav(this, BottomNav.LIBRARY);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.accent_cyan_light);
        swipeRefresh.setOnRefreshListener(this::refreshLibrary);

        btnAddSong = findViewById(R.id.btnAddSongLib);
        btnAddSong.setOnClickListener(v -> audioPicker.launch(new String[]{"audio/*"}));
        btnAddPodcast = findViewById(R.id.btnAddPodcastLib);
        btnAddPodcast.setOnClickListener(v -> podcastHelper.createPodcast());
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        allSongs = MusicLibrary.loadWithCustom(this);
        podcasts = MusicLibrary.loadPodcasts(this);
        selectChip(currentChip);   // reloads the favorites you just changed
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

    // pull down to refresh: songs, artists, favorites and podcasts
    private void refreshLibrary() {
        new Thread(() -> {
            MusicLibrary.clearCache();
            final List<Song> songs = MusicLibrary.loadWithCustom(this);
            final List<Song> eps = MusicLibrary.loadPodcasts(this);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                allSongs = songs;
                podcasts = eps;
                selectChip(currentChip);
                swipeRefresh.setRefreshing(false);
                Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void onPodcastsChanged() {
        podcasts = MusicLibrary.loadPodcasts(this);
        selectChip(currentChip);
    }
    private void selectChip(int index) {
        currentChip = index;
        for (int i = 0; i < chips.length; i++) {
            boolean on = (i == index);
            chips[i].setBackgroundResource(on ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chips[i].setTextColor(on ? ThemeStore.selectedChipText(this)
                    : ContextCompat.getColor(this, R.color.text_secondary));
            chips[i].setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
        songAdapter = null;
        podcastAdapter = null;

        int count;
        String unit;
        if (index == 0) {
            count = allSongs.size();
            unit = count == 1 ? " song" : " songs";
            songAdapter = new SongAdapter(allSongs, songListener(allSongs));
            rv.setAdapter(songAdapter);
        } else if (index == 1) {
            List<ArtistAdapter.Artist> artists = buildArtists();
            count = artists.size();
            unit = count == 1 ? " artist" : " artists";
            rv.setAdapter(new ArtistAdapter(artists, artist -> {
                Intent intent = new Intent(LibraryActivity.this, PlaylistActivity.class);
                intent.putExtra("name", artist.name);
                intent.putExtra("artist", true);
                startActivity(intent);
            }));
        } else if (index == 2) {
            // songs and podcast episodes (yours and official)
            List<Song> pool = new ArrayList<>(allSongs);
            pool.addAll(podcasts);
            List<Song> favorites = FavoritesStore.load(this, pool);
            SortHelper.sortSongs(this, favorites);
            // favorite podcasts go on top
            List<PodcastShows.Show> favShows = new ArrayList<>();
            for (PodcastShows.Show s : PodcastShows.group(this, podcasts)) {
                if (FavoritesStore.isShowFavorite(this, s.name)) favShows.add(s);
            }
            count = favorites.size() + favShows.size();
            unit = count == 1 ? " item" : " items";
            songAdapter = new SongAdapter(favorites, songListener(favorites));
            ShowAdapter favShowAdapter = new ShowAdapter(favShows, show -> {
                Intent intent = new Intent(LibraryActivity.this, ShowActivity.class);
                intent.putExtra("show", show.name);
                startActivity(intent);
            });
            rv.setAdapter(new ConcatAdapter(favShowAdapter, songAdapter));
        } else {
            List<PodcastShows.Show> shows = PodcastShows.group(this, podcasts);
            count = shows.size();
            unit = count == 1 ? " show" : " shows";
            rv.setAdapter(new ShowAdapter(shows, show -> {
                Intent intent = new Intent(LibraryActivity.this, ShowActivity.class);
                intent.putExtra("show", show.name);
                startActivity(intent);
            }));
        }

        tvCount.setText(count + unit);
        btnAddSong.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        btnAddPodcast.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        tvEmpty.setText(EMPTY_TEXT[index]);
        tvEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        rv.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        refreshPlayback();
    }

    // ---------- adding your own song ----------
    private void showAddSongDialog(Uri audio) {
        final CustomSongStore.Info info = CustomSongStore.peek(this, audio);
        pendingThumb = null;

        View view = getLayoutInflater().inflate(R.layout.dialog_add_song, null);
        DialogBg.apply(this, view);
        final EditText etTitle = view.findViewById(R.id.etAddTitle);
        final EditText etArtist = view.findViewById(R.id.etAddArtist);
        final TextView btnSave = view.findViewById(R.id.btnAddSave);
        dialogThumb = view.findViewById(R.id.ivAddThumb);

        etTitle.setText(info.title);
        etArtist.setText(info.artist);
        if (info.art != null) {
            dialogThumb.setImageBitmap(
                    BitmapFactory.decodeByteArray(info.art, 0, info.art.length));
        }

        view.findViewById(R.id.btnPickThumb).setOnClickListener(v -> imagePicker.launch("image/*"));

        final AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.setOnDismissListener(d -> dialogThumb = null);
        view.findViewById(R.id.btnAddCancel).setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            final String title = etTitle.getText().toString().trim();
            String artistText = etArtist.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "Type a title first", Toast.LENGTH_SHORT).show();
                return;
            }
            final String artist = artistText.isEmpty() ? "Unknown Artist" : artistText;
            final Uri thumb = pendingThumb;

            btnSave.setEnabled(false);
            btnSave.setText("Saving...");
            new Thread(() -> {
                boolean ok = CustomSongStore.importSong(getApplicationContext(),
                        audio, title, artist, thumb, info.art);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    if (ok) {
                        allSongs = MusicLibrary.loadWithCustom(this);   // include the new song
                        selectChip(currentChip);                        // refresh the list
                        rv.scrollToPosition(0);                         // show the new song
                        Toast.makeText(this, "Song added", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Couldn't add that song",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });
        dialog.show();
    }

    private List<ArtistAdapter.Artist> buildArtists() {
        Map<String, List<Song>> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Song s : allSongs) {
            List<Song> list = groups.get(s.artist);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(s.artist, list);
            }
            list.add(s);
        }
        List<Map.Entry<String, List<Song>>> entries = new ArrayList<>(groups.entrySet());
        SortHelper.sortGroups(this, entries, e -> e.getKey(),
                e -> SortHelper.totalMs(e.getValue()));

        List<ArtistAdapter.Artist> artists = new ArrayList<>();
        for (Map.Entry<String, List<Song>> entry : entries) {
            SortHelper.sortSongs(this, entry.getValue());
            artists.add(new ArtistAdapter.Artist(entry.getKey(), entry.getValue()));
        }
        return artists;
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
                saveToPlaylist(list.get(position));
            }

            @Override
            public void onSongLongClick(int position) {
                saveToPlaylist(list.get(position));
            }
        };
    }

    private void saveToPlaylist(Song song) {
        // podcast episodes are managed from the podcast page
        for (Song p : podcasts) {
            if (p.resId == song.resId) {
                Toast.makeText(this, "Open the podcast to manage this episode",
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Runnable del = song.isCustom()
                ? () -> confirmDelete(song)
                : () -> confirmDeleteOfficial(song);
        PlaylistDialogs.showSaveToPlaylist(this, song, null, del);
    }

    private void confirmDeleteOfficial(Song song) {
        new AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("Delete \"" + song.title + "\"?")
                .setMessage("This removes the song from the app. You can bring it back later.")
                .setPositiveButton("Delete", (d, which) -> {
                    MediaController c = PlayerConnection.get();
                    if (c != null && isCurrentSong(song)) c.clearMediaItems();
                    CustomPlaylistStore.removeEverywhere(this, song);
                    HiddenSongStore.hide(this, song);
                    allSongs = MusicLibrary.loadWithCustom(this);
                    selectChip(currentChip);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(Song song) {
        new AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("Delete \"" + song.title + "\"?")
                .setMessage("This removes the song from the app. Your original file is not touched.")
                .setPositiveButton("Delete", (d, which) -> {
                    MediaController c = PlayerConnection.get();
                    if (c != null && isCurrentSong(song)) c.clearMediaItems();
                    CustomPlaylistStore.removeEverywhere(this, song);
                    CustomSongStore.delete(this, song);
                    allSongs = MusicLibrary.loadWithCustom(this);
                    selectChip(currentChip);
                })
                .setNegativeButton("Cancel", null)
                .show();
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