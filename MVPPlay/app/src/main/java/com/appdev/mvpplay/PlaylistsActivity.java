package com.appdev.mvpplay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PlaylistsActivity extends BaseActivity {

    private final List<Playlist> playlists = new ArrayList<>();
    private PlaylistRowAdapter adapter;
    private TextView tvCount;
    private BottomNav bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlists);

        WindowInsetsControllerCompat bars =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);

        tvCount = findViewById(R.id.tvCount);
        RecyclerView rv = findViewById(R.id.rvPlaylists);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PlaylistRowAdapter(playlists, playlist -> {
            Intent intent = new Intent(PlaylistsActivity.this, PlaylistActivity.class);
            intent.putExtra("name", playlist.name);
            intent.putExtra("special", playlist.special);
            startActivity(intent);
        });
        rv.setAdapter(adapter);

        findViewById(R.id.btnNewPlaylist).setOnClickListener(v ->
                PlaylistDialogs.showCreatePlaylist(this, new ArrayList<>(), this::loadPlaylists));

        bottomNav = new BottomNav(this, BottomNav.PLAYLISTS);
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadPlaylists();   // picks up playlists you just made, changed or deleted
        bottomNav.onStart();
    }

    @Override
    protected void onStop() {
        bottomNav.onStop();
        super.onStop();
    }

    // official playlists first, then the ones you made
    private void loadPlaylists() {
        List<Song> allSongs = MusicLibrary.load(this);
        playlists.clear();
        Playlist special = PlaylistLibrary.buildSpecial(this, allSongs);
        if (special != null) playlists.add(special);   // always on top
        playlists.addAll(PlaylistLibrary.build(allSongs));
        playlists.addAll(CustomPlaylistStore.load(this, allSongs));
        adapter.notifyDataSetChanged();
        tvCount.setText(playlists.size() == 1 ? "1 playlist" : playlists.size() + " playlists");
    }
}