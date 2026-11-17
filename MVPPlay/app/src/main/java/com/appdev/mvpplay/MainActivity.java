package com.appdev.mvpplay;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.EditText;
import android.widget.ImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends BaseActivity {

    // name of the playlist that is playing now (null = playing from somewhere else)
    static String activePlaylistName = null;

    private List<Song> allSongs;
    private List<Song> podcastEpisodes;
    private final List<Playlist> officialPlaylists = new ArrayList<>();
    private final List<Playlist> customPlaylists = new ArrayList<>();

    private RecyclerView rvSongs, rvPlaylists, rvCustomPlaylists, rvPodcasts;
    private TextView tvSongsTitle, tvSongsAction, tvPodcastsEmpty, tvSaveSongsLabel;
    private View sectionSongs, sectionPlaylists, sectionPodcasts;
    private View ivSeeAllChevron, btnSaveSongs, cardCustomEmpty;
    private View[] chipViews;
    private View[] chipDots;
    private TextView[] chipTexts;

    private SongAdapter songAdapter;
    private PlaylistAdapter playlistAdapter;
    private PlaylistAdapter customAdapter;
    private PodcastAdapter podcastAdapter;

    private int currentChip = 0;
    private boolean selectingSongs = false;
    private WelcomeCard welcome;
    private BottomNav bottomNav;
    private String shownSort;
    private int shownHidden;
    private List<Song> recommended = new ArrayList<>();
    private final PodcastHelper podcastHelper = new PodcastHelper(this, this::refreshPodcasts);
    private SwipeRefreshLayout swipeRefresh;

    // adding your own song
    private View btnAddSong;
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

    // listens to the background player so the lists can show what is playing
    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            refreshPlaybackState();
        }

        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            refreshPlaybackState();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);

        // views
        rvSongs = findViewById(R.id.rvSongs);
        rvPlaylists = findViewById(R.id.rvPlaylists);
        rvCustomPlaylists = findViewById(R.id.rvCustomPlaylists);
        rvPodcasts = findViewById(R.id.rvPodcasts);
        tvSongsTitle = findViewById(R.id.tvSongsTitle);
        tvSongsAction = findViewById(R.id.tvSongsAction);
        tvPodcastsEmpty = findViewById(R.id.tvPodcastsEmpty);
        tvSaveSongsLabel = findViewById(R.id.tvSaveSongsLabel);
        ivSeeAllChevron = findViewById(R.id.ivSeeAllChevron);
        btnSaveSongs = findViewById(R.id.btnSaveSongs);
        cardCustomEmpty = findViewById(R.id.cardCustomEmpty);
        sectionSongs = findViewById(R.id.sectionSongs);
        sectionPlaylists = findViewById(R.id.sectionPlaylists);
        sectionPodcasts = findViewById(R.id.sectionPodcasts);

        rvSongs.setLayoutManager(new LinearLayoutManager(this));
        rvSongs.setNestedScrollingEnabled(false);
        rvPlaylists.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvCustomPlaylists.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvPodcasts.setLayoutManager(new LinearLayoutManager(this));
        rvPodcasts.setNestedScrollingEnabled(false);

        // data
        allSongs = MusicLibrary.load(this);
        podcastEpisodes = MusicLibrary.loadPodcasts(this);

        // official and custom playlists: two separate rows
        playlistAdapter = new PlaylistAdapter(officialPlaylists, playlistListener(officialPlaylists));
        customAdapter = new PlaylistAdapter(customPlaylists, playlistListener(customPlaylists));
        rvPlaylists.setAdapter(playlistAdapter);
        rvCustomPlaylists.setAdapter(customAdapter);
        refreshPlaylists();

        View.OnClickListener createPlaylist = v ->
                PlaylistDialogs.showCreatePlaylist(this, new ArrayList<>(), this::refreshPlaylists);
        findViewById(R.id.tvNewPlaylist).setOnClickListener(createPlaylist);
        findViewById(R.id.btnCreateEmpty).setOnClickListener(createPlaylist);

        // podcasts: one card per show, tapping it opens the page with its episodes
        List<PodcastShows.Show> shows = PodcastShows.group(this, podcastEpisodes);
        rvPodcasts.setAdapter(new ShowAdapter(shows, show -> {
            Intent intent = new Intent(MainActivity.this, ShowActivity.class);
            intent.putExtra("show", show.name);
            startActivity(intent);
        }));

        boolean noEpisodes = podcastEpisodes.isEmpty();
        rvPodcasts.setVisibility(noEpisodes ? View.GONE : View.VISIBLE);
        tvPodcastsEmpty.setVisibility(noEpisodes ? View.VISIBLE : View.GONE);

        // chips
        chipViews = new View[]{
                findViewById(R.id.chipAll), findViewById(R.id.chipMusic),
                findViewById(R.id.chipPlaylists), findViewById(R.id.chipPodcasts)};
        chipDots = new View[]{
                findViewById(R.id.dotAll), findViewById(R.id.dotMusic),
                findViewById(R.id.dotPlaylists), findViewById(R.id.dotPodcasts)};
        chipTexts = new TextView[]{
                findViewById(R.id.txtAll), findViewById(R.id.txtMusic),
                findViewById(R.id.txtPlaylists), findViewById(R.id.txtPodcasts)};

        for (int i = 0; i < chipViews.length; i++) {
            final int index = i;
            chipViews[i].setOnClickListener(v -> {
                Anim.pop(v);
                selectChip(index);
                Anim.runList(rvSongs);
            });
        }

        // on the All chip this says SEE ALL, on the Music chip it says SELECT / CANCEL
        tvSongsAction.setOnClickListener(v -> {
            if (currentChip == 1) {
                setSelectingSongs(!selectingSongs);
            } else {
                selectChip(1);
            }
        });
        btnSaveSongs.setOnClickListener(v -> saveSelectedSongs());
        btnAddSong = findViewById(R.id.btnAddSong);
        btnAddSong.setOnClickListener(v -> audioPicker.launch(new String[]{"audio/*"}));
        findViewById(R.id.tvExplorePodcasts).setOnClickListener(v -> selectChip(3));
        findViewById(R.id.btnCreatePodcast).setOnClickListener(v -> podcastHelper.createPodcast());

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.accent_cyan_light);
        swipeRefresh.setOnRefreshListener(this::refreshAll);

        selectChip(0);

        // welcome card: greeting and chihuahua for the time of day
        welcome = new WelcomeCard(this);
        bottomNav = new BottomNav(this, BottomNav.HOME);
        findViewById(R.id.ivSearch).setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class)));
        shownSort = AppSettings.sortMode(this);
        shownHidden = HiddenSongStore.count(this);
        // "Resume the last song" is turned off here, so the mini player stays hidden after a restart
        // AppSettings.restoreLast(this, allSongs, podcastEpisodes);

        // ask for notification permission (Android 13 and above)
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        refreshPlaylists();   // picks up playlists you just made, changed or deleted
        refreshPodcasts();    // picks up podcasts you just made or deleted
        if (welcome != null) welcome.refresh();   // the time of day may have changed
        if (bottomNav != null) bottomNav.onStart();

        // the sort order may have been changed in Settings
        if (!AppSettings.sortMode(this).equals(shownSort)) {
            shownSort = AppSettings.sortMode(this);
            AppSettings.sortSongs(this, allSongs);
            refreshPlaylists();
            selectChip(currentChip);
        }
        // a song may have been deleted from the Library screen
        if (HiddenSongStore.count(this) != shownHidden) {
            shownHidden = HiddenSongStore.count(this);
            allSongs = MusicLibrary.load(this);
            refreshPlaylists();
            selectChip(currentChip);
        }

        PlayerConnection.connect(this, c -> {
            c.addListener(playerListener);
            refreshPlaybackState();
        });
    }

    @Override
    protected void onStop() {
        if (welcome != null) welcome.stop();
        if (bottomNav != null) bottomNav.onStop();
        MediaController c = PlayerConnection.get();
        if (c != null) {
            c.removeListener(playerListener);
        }
        super.onStop();
    }

    // ---------- pull down to refresh ----------
    private void refreshAll() {
        new Thread(() -> {
            MusicLibrary.clearCache();
            final List<Song> songs = MusicLibrary.load(this);
            final List<Song> podcasts = MusicLibrary.loadPodcasts(this);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;

                allSongs = songs;
                podcastEpisodes = podcasts;

                // podcasts
                List<PodcastShows.Show> shows = PodcastShows.group(this, podcastEpisodes);
                rvPodcasts.setAdapter(new ShowAdapter(shows, show -> {
                    Intent intent = new Intent(MainActivity.this, ShowActivity.class);
                    intent.putExtra("show", show.name);
                    startActivity(intent);
                }));
                boolean noEpisodes = podcastEpisodes.isEmpty();
                rvPodcasts.setVisibility(noEpisodes ? View.GONE : View.VISIBLE);
                tvPodcastsEmpty.setVisibility(noEpisodes ? View.VISIBLE : View.GONE);

                // playlists and songs (including your own songs)
                refreshPlaylists();
                pickRecommended();
                selectChip(currentChip);
                if (welcome != null) welcome.refresh();

                swipeRefresh.setRefreshing(false);
                Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    // ---------- chips ----------
    private void selectChip(int index) {
        currentChip = index;

        for (int i = 0; i < chipViews.length; i++) {
            boolean selected = (i == index);
            chipViews[i].setBackgroundResource(
                    selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chipDots[i].setVisibility(selected ? View.VISIBLE : View.GONE);
            chipTexts[i].setTextColor(selected ? ThemeStore.selectedChipText(this)
                    : ContextCompat.getColor(this, R.color.text_secondary));
            chipTexts[i].setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        }

        boolean all = (index == 0);
        boolean music = (index == 1);
        boolean playlistsChip = (index == 2);
        boolean podcastsChip = (index == 3);

        sectionSongs.setVisibility((all || music) ? View.VISIBLE : View.GONE);
        sectionPlaylists.setVisibility((all || playlistsChip) ? View.VISIBLE : View.GONE);
        sectionPodcasts.setVisibility((all || podcastsChip) ? View.VISIBLE : View.GONE);

        if (music) {
            tvSongsTitle.setText("Your Music");
            rvSongs.setAdapter(makeSongAdapter(MusicLibrary.loadWithCustom(this)));
        } else {
            tvSongsTitle.setText("Recommended Songs");
            ensureRecommended();
            rvSongs.setAdapter(makeSongAdapter(recommended));
        }

        // a new list always starts with ticking turned off
        selectingSongs = false;
        updateSongsHeader();
    }

    // ---------- recommended songs ----------
    // picks 3 random songs from the built-in songs and your own
    private void pickRecommended() {
        List<Song> pool = new ArrayList<>(MusicLibrary.loadWithCustom(this));
        Collections.shuffle(pool);
        recommended = new ArrayList<>(pool.subList(0, Math.min(3, pool.size())));
    }

    // keeps the same picks, unless there are none or one of them was deleted
    private void ensureRecommended() {
        List<Song> pool = MusicLibrary.loadWithCustom(this);
        boolean valid = !recommended.isEmpty() || pool.isEmpty();
        for (Song r : recommended) {
            boolean found = false;
            for (Song s : pool) {
                if (s.resId == r.resId) {
                    found = true;
                    break;
                }
            }
            if (!found) valid = false;
        }
        if (!valid) pickRecommended();
    }

    // ---------- ticking songs to make a playlist (Music chip) ----------
    private void setSelectingSongs(boolean on) {
        selectingSongs = on;
        songAdapter.setSelectionMode(on);
        updateSongsHeader();
    }

    private void updateSongsHeader() {
        boolean music = (currentChip == 1);
        tvSongsAction.setText(music ? (selectingSongs ? "CANCEL" : "SELECT") : "SEE ALL");
        ivSeeAllChevron.setVisibility(music ? View.GONE : View.VISIBLE);
        btnSaveSongs.setVisibility(selectingSongs ? View.VISIBLE : View.GONE);
        btnAddSong.setVisibility(music && !selectingSongs ? View.VISIBLE : View.GONE);
        int count = (songAdapter != null) ? songAdapter.selectedCount() : 0;
        tvSaveSongsLabel.setText("Save " + count + (count == 1 ? " song" : " songs") + " as playlist");
        btnSaveSongs.setAlpha(count > 0 ? 1f : 0.5f);
    }

    private void saveSelectedSongs() {
        if (songAdapter.selectedCount() == 0) {
            Toast.makeText(this, "Tick at least one song first", Toast.LENGTH_SHORT).show();
            return;
        }
        PlaylistDialogs.showCreatePlaylist(this, songAdapter.selectedSongs(), () -> {
            setSelectingSongs(false);
            refreshPlaylists();
        });
    }

    // ---------- songs ----------
    // builds the songs list and decides what each tap does
    private SongAdapter makeSongAdapter(List<Song> list) {
        songAdapter = new SongAdapter(list, new SongAdapter.OnSongClickListener() {
            @Override
            public void onSongClick(int position) {
                if (selectingSongs) {
                    songAdapter.toggle(position);
                    updateSongsHeader();
                    return;
                }
                // row body: make sure this song is the current one, then open Now Playing
                Song tapped = list.get(position);
                if (!isCurrentSong(tapped)) playFromLibrary(list, position);
                openNowPlaying();
            }

            @Override
            public void onPlayClick(int position) {
                // round icon: only play or pause, stay on Home
                Song tapped = list.get(position);
                MediaController c = PlayerConnection.get();
                if (isCurrentSong(tapped) && c != null) {
                    togglePlayPause(c);
                } else {
                    playFromLibrary(list, position);
                }
            }

            @Override
            public void onSaveClick(int position) {
                openSaveDialog(list.get(position));
            }

            @Override
            public void onSongLongClick(int position) {
                if (selectingSongs) {
                    songAdapter.toggle(position);
                    updateSongsHeader();
                } else {
                    openSaveDialog(list.get(position));
                }
            }
        });
        refreshPlaybackState();
        return songAdapter;
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
                        selectChip(currentChip);   // reloads the Music list
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

    // same popup for official and custom songs (custom ones also get a Delete row)
    private void openSaveDialog(Song song) {
        Runnable del = song.isCustom()
                ? () -> confirmDeleteCustomSong(song)
                : () -> confirmDeleteOfficialSong(song);
        PlaylistDialogs.showSaveToPlaylist(this, song, this::refreshPlaylists, del);
    }

    private void confirmDeleteOfficialSong(Song song) {
        AppDialog.confirm(this, R.drawable.ic_delete, "Delete \"" + song.title + "\"?",
                "This removes the song from the app. You can bring it back later.",
                "Delete", true, () -> {
                    MediaController c = PlayerConnection.get();
                    if (c != null && isCurrentSong(song)) c.clearMediaItems();
                    CustomPlaylistStore.removeEverywhere(this, song);
                    HiddenSongStore.hide(this, song);
                    shownHidden = HiddenSongStore.count(this);
                    allSongs = MusicLibrary.load(this);
                    refreshPlaylists();
                    selectChip(currentChip);
                });
    }

    private void confirmDeleteCustomSong(Song song) {
        AppDialog.confirm(this, R.drawable.ic_delete, "Delete \"" + song.title + "\"?",
                "This removes the song from the app. Your original file is not touched.",
                "Delete", true, () -> {
                    MediaController c = PlayerConnection.get();
                    if (c != null && isCurrentSong(song)) c.clearMediaItems();
                    CustomPlaylistStore.removeEverywhere(this, song);
                    CustomSongStore.delete(this, song);
                    refreshPlaylists();
                    selectChip(currentChip);
                });
    }

    // ---------- podcasts ----------
    private void refreshPodcasts() {
        if (rvPodcasts == null) return;
        podcastEpisodes = MusicLibrary.loadPodcasts(this);
        List<PodcastShows.Show> shows = PodcastShows.group(this, podcastEpisodes);
        rvPodcasts.setAdapter(new ShowAdapter(shows, show -> {
            Intent intent = new Intent(MainActivity.this, ShowActivity.class);
            intent.putExtra("show", show.name);
            startActivity(intent);
        }));
        boolean none = podcastEpisodes.isEmpty();
        rvPodcasts.setVisibility(none ? View.GONE : View.VISIBLE);
        tvPodcastsEmpty.setVisibility(none ? View.VISIBLE : View.GONE);
    }

    // ---------- playlists ----------
    // the same taps work for both rows (official and custom)
    private PlaylistAdapter.Listener playlistListener(List<Playlist> list) {
        return new PlaylistAdapter.Listener() {
            @Override
            public void onPlaylistClick(int position) {
                // card: open the playlist page
                Intent intent = new Intent(MainActivity.this, PlaylistActivity.class);
                intent.putExtra("name", list.get(position).name);
                intent.putExtra("special", list.get(position).special);
                startActivity(intent);
            }

            @Override
            public void onPlayClick(int position) {
                // round button: play or pause, stay on Home
                Playlist p = list.get(position);
                if (p.songs.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Add some songs first",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                MediaController c = PlayerConnection.get();
                if (isPlaylistActive(p) && c != null) {
                    togglePlayPause(c);
                } else {
                    startPlaylist(p);
                }
            }
        };
    }

    // reads the playlists again: official ones above, the ones you made below
    private void refreshPlaylists() {
        if (allSongs == null || playlistAdapter == null || customAdapter == null) return;

        officialPlaylists.clear();
        officialPlaylists.addAll(PlaylistLibrary.build(allSongs));
        customPlaylists.clear();
        customPlaylists.addAll(CustomPlaylistStore.load(this, MusicLibrary.loadWithCustom(this)));

        SortHelper.sortGroups(this, officialPlaylists, p -> p.name,
                p -> SortHelper.totalMs(p.songs));
        SortHelper.sortGroups(this, customPlaylists, p -> p.name,
                p -> SortHelper.totalMs(p.songs));

        // the special gold playlist is always the first card in Official Playlists
        Playlist special = PlaylistLibrary.buildSpecial(this, allSongs);
        if (special != null) officialPlaylists.add(0, special);

        playlistAdapter.notifyDataSetChanged();
        customAdapter.notifyDataSetChanged();

        boolean none = customPlaylists.isEmpty();
        rvCustomPlaylists.setVisibility(none ? View.GONE : View.VISIBLE);
        cardCustomEmpty.setVisibility(none ? View.VISIBLE : View.GONE);
    }

    private void startPlaylist(Playlist playlist) {
        List<Song> queue = new ArrayList<>(playlist.songs);
        if (playlist.shuffled) Collections.shuffle(queue);
        playQueue(queue, 0, playlist.name);
    }

    private boolean isPlaylistActive(Playlist playlist) {
        MediaController c = PlayerConnection.get();
        return playlist.name.equals(activePlaylistName)
                && c != null && c.getCurrentMediaItem() != null;
    }

    // plays from the whole music library, starting at the tapped song, so Next and Previous
    // go through all your songs (not only the 3 shown under Trending)
    private void playFromLibrary(List<Song> shown, int position) {
        Song tapped = shown.get(position);
        List<Song> queue = MusicLibrary.loadWithCustom(this);
        int index = -1;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).resId == tapped.resId) {
                index = i;
                break;
            }
        }
        if (index < 0) {   // not found in the library: play the list exactly as shown
            queue = shown;
            index = position;
        }
        playQueue(new ArrayList<>(queue), index, null);
    }

    // ---------- playing ----------
    // starts a list of songs, remembering which playlist it came from
    private void playQueue(List<Song> queue, int position, String playlistName) {
        activePlaylistName = playlistName;
        PlayerConnection.play(this, queue, position);
    }

    private boolean isCurrentSong(Song song) {
        MediaController c = PlayerConnection.get();
        MediaItem item = (c != null) ? c.getCurrentMediaItem() : null;
        return item != null && String.valueOf(song.resId).equals(item.mediaId);
    }

    private void togglePlayPause(MediaController c) {
        if (c.isPlaying()) c.pause(); else c.play();
    }

    private void openNowPlaying() {
        startActivity(new Intent(this, NowPlayingActivity.class));
        Anim.slideUpOpen(this);
    }

    // tells every list which item is current and whether it is playing
    private void refreshPlaybackState() {
        MediaController c = PlayerConnection.get();
        if (c == null) return;

        MediaItem item = c.getCurrentMediaItem();
        String mediaId = (item != null) ? item.mediaId : null;
        boolean playing = c.isPlaying();

        if (songAdapter != null) songAdapter.setPlaybackState(mediaId, playing);
        if (playlistAdapter != null) playlistAdapter.setPlaybackState(activePlaylistName, playing);
        if (customAdapter != null) customAdapter.setPlaybackState(activePlaylistName, playing);
        if (podcastAdapter != null) podcastAdapter.setPlaybackState(mediaId, playing);
    }
}