package com.appdev.mvpplay;

import android.app.Activity;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

public class PlaylistDialogs {

    public interface OnDone {
        void onDone();
    }

    public interface OnRenamed {
        void onRenamed(String newName);
    }

    // ---------- Save to Playlist ----------
    public static void showSaveToPlaylist(Activity activity, Song song, @Nullable OnDone onChanged) {
        showSaveToPlaylist(activity, song, onChanged, null);
    }

    // onDelete is only given for your own songs: it adds a "Delete this song" row
    public static void showSaveToPlaylist(Activity activity, Song song,
                                          @Nullable OnDone onChanged,
                                          @Nullable Runnable onDelete) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_save_playlist, null);
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ((TextView) view.findViewById(R.id.tvDialogSong))
                .setText(song.title + "  •  " + song.artist);

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        // "Create New Playlist": makes it and puts this song in it
        view.findViewById(R.id.rowCreate).setOnClickListener(v -> {
            dialog.dismiss();
            List<Song> first = new ArrayList<>();
            first.add(song);
            showCreatePlaylist(activity, first, onChanged);
        });

        // the user's own playlists (official playlists are automatic and not listed)
        LinearLayout list = view.findViewById(R.id.listPlaylists);
        TextView empty = view.findViewById(R.id.tvEmpty);
        List<String> names = CustomPlaylistStore.names(activity);
        empty.setVisibility(names.isEmpty() ? View.VISIBLE : View.GONE);

        for (String name : names) {
            View row = LayoutInflater.from(activity)
                    .inflate(R.layout.item_dialog_playlist, list, false);

            int count = CustomPlaylistStore.count(activity, name);
            ((TextView) row.findViewById(R.id.tvName)).setText(name);
            ((TextView) row.findViewById(R.id.tvCount))
                    .setText(count == 1 ? "1 song" : count + " songs");

            if (CustomPlaylistStore.contains(activity, name, song)) {
                // already in this playlist: shown faded, cannot be added twice
                row.findViewById(R.id.tvState).setVisibility(View.VISIBLE);
                row.setAlpha(0.5f);
                row.setOnClickListener(v -> Toast.makeText(activity,
                        "Already in \"" + name + "\"", Toast.LENGTH_SHORT).show());
            } else {
                row.setOnClickListener(v -> {
                    int result = CustomPlaylistStore.addSong(activity, name, song);
                    dialog.dismiss();
                    if (result == CustomPlaylistStore.ADDED) {
                        Toast.makeText(activity, "Added to \"" + name + "\"",
                                Toast.LENGTH_SHORT).show();
                        if (onChanged != null) onChanged.onDone();
                    } else if (result == CustomPlaylistStore.ALREADY_THERE) {
                        Toast.makeText(activity, "Already in \"" + name + "\"",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, "That playlist no longer exists",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
            list.addView(row);
        }

        if (onDelete != null) {
            View del = LayoutInflater.from(activity)
                    .inflate(R.layout.item_dialog_playlist, list, false);
            ((TextView) del.findViewById(R.id.tvName)).setText("Delete this song");
            ((TextView) del.findViewById(R.id.tvCount)).setText("Removes it from the app");
            del.setOnClickListener(v -> {
                dialog.dismiss();
                onDelete.run();
            });
            list.addView(del);
        }

        // a long list scrolls instead of growing past the screen
        if (names.size() > 4) {
            ScrollView scroll = view.findViewById(R.id.scrollPlaylists);
            scroll.getLayoutParams().height =
                    (int) (260 * activity.getResources().getDisplayMetrics().density);
        }

        dialog.show();
    }

    // ---------- Create New Playlist ----------
    // initial = the songs to put in it (it can be empty to make an empty playlist)
    // ---------- Create New Playlist ----------
    // initial = the songs to put in it (it can be empty to make an empty playlist)
    public static void showCreatePlaylist(Activity activity, List<Song> initial,
                                          @Nullable OnDone onCreated) {
        String message;
        if (initial.isEmpty()) {
            message = "Give your playlist a name.";
        } else if (initial.size() == 1) {
            message = "\"" + initial.get(0).title + "\" will be added to it.";
        } else {
            message = initial.size() + " songs will be added to it.";
        }

        AppDialog.input(activity, R.drawable.ic_playlist_add, "New playlist", message,
                "Playlist name", "", "Create", 30, text -> {
                    if (text.isEmpty()) return "Please type a name";
                    if (PlaylistLibrary.isOfficialName(text)) {
                        return "That is an official playlist name";
                    }
                    if (!CustomPlaylistStore.create(activity, text, initial)) {
                        return "That name is already used";
                    }
                    Toast.makeText(activity, initial.isEmpty()
                                    ? "Created \"" + text + "\""
                                    : "Created \"" + text + "\" and added your songs",
                            Toast.LENGTH_SHORT).show();
                    if (onCreated != null) onCreated.onDone();
                    return null;
                });
    }

    // ---------- Rename ----------
    public static void showRename(Activity activity, String currentName, OnRenamed onRenamed) {
        showRename(activity, currentName, false, onRenamed);
    }

    // special = the gold playlist (its name is kept in SpecialPlaylistStore)
    // special = the gold playlist (its name is kept in SpecialPlaylistStore)
    public static void showRename(Activity activity, String currentName, boolean special,
                                  OnRenamed onRenamed) {
        AppDialog.input(activity, R.drawable.ic_edit, "Rename playlist", null,
                "Playlist name", currentName, "Rename", 30, text -> {
                    if (text.isEmpty()) return "Please type a name";
                    if (text.equals(currentName)) return null;
                    boolean ok = special
                            ? renameSpecial(activity, text)
                            : CustomPlaylistStore.rename(activity, currentName, text);
                    if (!ok) return "That name is already used";
                    Toast.makeText(activity, "Renamed to \"" + text + "\"",
                            Toast.LENGTH_SHORT).show();
                    onRenamed.onRenamed(text);
                    return null;
                });
    }

    // renames the gold playlist (not an official name, not one of your own playlists)
    private static boolean renameSpecial(Activity activity, String newName) {
        if (PlaylistLibrary.isOfficialName(newName)) return false;
        for (String own : CustomPlaylistStore.names(activity)) {
            if (own.equalsIgnoreCase(newName)) return false;
        }
        SpecialPlaylistStore.setName(activity, newName);
        return true;
    }

    // ---------- Delete ----------
    // ---------- Delete ----------
    public static void showDelete(Activity activity, String name, OnDone onDeleted) {
        AppDialog.confirm(activity, R.drawable.ic_delete, "Delete \"" + name + "\"?",
                "Your songs stay in the app. Only this playlist is removed.",
                "Delete", true, () -> {
                    CustomPlaylistStore.delete(activity, name);
                    Toast.makeText(activity, "Playlist deleted", Toast.LENGTH_SHORT).show();
                    onDeleted.onDone();
                });
    }

    // puts some space around a text box inside a dialog
    private static FrameLayout wrap(Activity activity, EditText input) {
        FrameLayout box = new FrameLayout(activity);
        int side = (int) (24 * activity.getResources().getDisplayMetrics().density);
        box.setPadding(side, 8, side, 0);
        box.addView(input);
        return box;
    }
}