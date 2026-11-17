package com.appdev.mvpplay;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.res.ColorStateList;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

// the page of one podcast show: its cover and its episodes (EP 1, EP 2, ...)
public class ShowActivity extends BaseActivity {

    private String showName;
    private final List<Song> episodes = new ArrayList<>();
    private EpisodeAdapter adapter;
    private boolean started = false;

    // "Add episode" for podcasts you made
    private final PodcastHelper podcastHelper = new PodcastHelper(this, this::onChanged);

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
        setContentView(R.layout.activity_show);

        WindowInsetsControllerCompat bars =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);

        showName = getIntent().getStringExtra("show");

        RecyclerView rv = findViewById(R.id.rvEpisodes);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setNestedScrollingEnabled(false);
        adapter = new EpisodeAdapter(episodes, new EpisodeAdapter.Listener() {
            @Override
            public void onEpisodeClick(int position) {
                if (!isCurrentSong(episodes.get(position))) playQueue(position);
                openNowPlaying();
            }

            @Override
            public void onPlayClick(int position) {
                MediaController c = PlayerConnection.get();
                if (isCurrentSong(episodes.get(position)) && c != null) {
                    if (c.isPlaying()) c.pause(); else c.play();
                } else {
                    playQueue(position);
                }
            }

            @Override
            public void onMoreClick(int position) {
                confirmDeleteEpisode(episodes.get(position));
            }

            @Override
            public void onFavoriteClick(int position) {
                boolean now = FavoritesStore.toggle(ShowActivity.this, episodes.get(position).resId);
                adapter.notifyItemChanged(position);
                Toast.makeText(ShowActivity.this,
                        now ? "Added to Favorites" : "Removed from Favorites",
                        Toast.LENGTH_SHORT).show();
            }
        });
        adapter.setShowMore(true);
        adapter.setShowHeart(true);
        rv.setAdapter(adapter);
        Anim.runList(rv);

        if (!reload()) {
            finish();
            return;
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPlayAll).setOnClickListener(v -> {
            playQueue(0);
            openNowPlaying();
        });
        findViewById(R.id.btnAddEpisode).setOnClickListener(v -> {
            CustomPodcastStore.ShowInfo mine = CustomPodcastStore.findShowByTitle(this, showName);
            if (mine != null) podcastHelper.addEpisode(mine.id);
        });
        findViewById(R.id.btnDeleteShow).setOnClickListener(v -> confirmDeleteShow());
        findViewById(R.id.btnShowFav).setOnClickListener(v -> {
            boolean now = FavoritesStore.toggleShow(this, showName);
            paintShowHeart();
            Anim.pop(findViewById(R.id.btnShowFav));
            Toast.makeText(this, now ? "Podcast added to Favorites"
                    : "Podcast removed from Favorites", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        if (adapter != null) adapter.notifyDataSetChanged();   // hearts may have changed
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
        super.onStop();
    }

    // reads the show again and refills the page. false = the show has no episodes left
    private boolean reload() {
        long totalMs = 0;
        List<Song> found = new ArrayList<>();
        for (PodcastShows.Show show : PodcastShows.group(this, MusicLibrary.loadPodcasts(this))) {
            if (show.name.equals(showName)) {
                found = show.episodes;
                totalMs = show.totalMs();
            }
        }
        episodes.clear();
        episodes.addAll(found);
        if (episodes.isEmpty()) return false;

        int count = episodes.size();
        ((TextView) findViewById(R.id.tvShowName)).setText(showName);
        ((TextView) findViewById(R.id.tvShowMeta)).setText(
                (count == 1 ? "1 episode" : count + " episodes")
                        + "  •  " + Math.max(1L, totalMs / 60000) + " min");
        bindCover(episodes.get(0));

        // your own podcast: show its description and the Add episode button
        CustomPodcastStore.ShowInfo mine = CustomPodcastStore.findShowByTitle(this, showName);
        TextView tvDesc = findViewById(R.id.tvShowDesc);
        String desc = (mine != null) ? mine.description : "";
        tvDesc.setText(desc);
        tvDesc.setVisibility(desc.isEmpty() ? View.GONE : View.VISIBLE);

        View add = findViewById(R.id.btnAddEpisode);
        add.setVisibility(mine != null ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams del =
                (LinearLayout.LayoutParams) findViewById(R.id.btnDeleteShow).getLayoutParams();
        del.setMarginStart(mine != null ? (int) (10 * getResources().getDisplayMetrics().density) : 0);
        findViewById(R.id.btnDeleteShow).setLayoutParams(del);

        adapter.notifyDataSetChanged();
        paintShowHeart();
        return true;
    }

    // pink filled heart when this podcast is a favorite
    private void paintShowHeart() {
        ImageView heart = findViewById(R.id.btnShowFav);
        boolean liked = FavoritesStore.isShowFavorite(this, showName);
        heart.setImageResource(liked ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        ImageViewCompat.setImageTintList(heart, ColorStateList.valueOf(
                liked ? Color.parseColor("#F472B6")
                        : ContextCompat.getColor(this, R.color.text_primary)));
    }

    // called after an episode was added
    private void onChanged() {
        if (!reload()) finish();
    }

    // ---------- deleting ----------
    private void confirmDeleteEpisode(final Song episode) {
        new AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("Delete \"" + episode.title + "\"?")
                .setMessage(episode.isCustom()
                        ? "This removes the episode from the app. Your original video is not touched."
                        : "This removes the episode from the app. You can bring it back later.")
                .setPositiveButton("Delete", (d, which) -> {
                    MediaController c = PlayerConnection.get();
                    if (c != null && isCurrentSong(episode)) c.clearMediaItems();
                    removeEpisode(episode);
                    if (!reload()) finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteShow() {
        new AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("Delete \"" + showName + "\"?")
                .setMessage("This removes the podcast and all its episodes from the app. "
                        + "Your original videos are not touched.")
                .setPositiveButton("Delete", (d, which) -> {
                    MediaController c = PlayerConnection.get();
                    if (c != null) {
                        for (Song s : episodes) {
                            if (isCurrentSong(s)) {
                                c.clearMediaItems();
                                break;
                            }
                        }
                    }
                    for (Song s : new ArrayList<>(episodes)) removeEpisode(s);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // your own episode: the files are deleted. Official episode: it is hidden.
    private void removeEpisode(Song episode) {
        if (episode.isCustom()) {
            CustomPodcastStore.deleteEpisode(this, episode);
        } else {
            HiddenSongStore.hide(this, episode);
        }
    }

    // the big cover: your picture, a video frame, or the gradient
    private void bindCover(Song first) {
        ImageView cover = findViewById(R.id.ivShowCover);
        float density = getResources().getDisplayMetrics().density;
        int size = (int) (160 * density);

        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{first.colorStart, first.colorEnd});
        background.setCornerRadius(density * 20);
        cover.setBackground(background);
        cover.setImageBitmap(Thumbs.forSong(this, first, size));
    }

    // the queue is the show's episodes, so Next goes to the next episode
    private void playQueue(int position) {
        MainActivity.activePlaylistName = null;
        PlayerConnection.play(this, new ArrayList<>(episodes), position);
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
        if (c == null || adapter == null) return;
        MediaItem item = c.getCurrentMediaItem();
        adapter.setPlaybackState(item != null ? item.mediaId : null, c.isPlaying());
    }
}