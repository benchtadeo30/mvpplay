package com.appdev.mvpplay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlaylistSongAdapter extends RecyclerView.Adapter<PlaylistSongAdapter.RowViewHolder> {

    public interface Listener {
        void onRowClick(int position);       // tap on the row: play it (or tick it)
        void onRowLongClick(int position);   // long press: Save to Playlist
        void onPlayClick(int position);      // round button: play or pause only
        void onRemoveClick(int position);    // the X button: remove from a custom playlist
    }

    private final List<Song> songs;
    private final Listener listener;

    private boolean selectionMode = false;
    private boolean removable = false;       // true for playlists the user made
    private final Set<Integer> selected = new HashSet<>();

    private String currentMediaId = null;
    private boolean isPlaying = false;

    public PlaylistSongAdapter(List<Song> songs, Listener listener) {
        this.songs = songs;
        this.listener = listener;
    }

    private boolean glass = false;           // glassy gold rows (gold playlist only)

    public void setGlass(boolean on) {
        glass = on;
        notifyDataSetChanged();
    }
    private boolean reorderable = false;     // true for playlists the user made
    private ItemTouchHelper touchHelper;

    public void setReorderable(boolean on) {
        reorderable = on;
        notifyDataSetChanged();
    }

    public void setTouchHelper(ItemTouchHelper helper) {
        touchHelper = helper;
    }

    // moves a row while it is being dragged
    public boolean onMove(int from, int to) {
        if (from < 0 || to < 0 || from >= songs.size() || to >= songs.size()) return false;
        Song moved = songs.remove(from);
        songs.add(to, moved);
        notifyItemMoved(from, to);
        return true;
    }

    public void setRemovable(boolean removable) {
        this.removable = removable;
        notifyDataSetChanged();
    }

    public void setPlaybackState(String mediaId, boolean playing) {
        this.currentMediaId = mediaId;
        this.isPlaying = playing;
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean on) {
        selectionMode = on;
        selected.clear();
        notifyDataSetChanged();
    }

    public void toggle(int position) {
        if (!selected.remove(position)) selected.add(position);
        notifyItemChanged(position);
    }

    public int selectedCount() {
        return selected.size();
    }

    // the ticked songs, in the order they appear in the list
    public List<Song> selectedSongs() {
        List<Song> result = new ArrayList<>();
        for (int i = 0; i < songs.size(); i++) {
            if (selected.contains(i)) result.add(songs.get(i));
        }
        return result;
    }

    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist_song, parent, false);
        return new RowViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RowViewHolder holder, int position) {
        Song song = songs.get(position);
        Context ctx = holder.itemView.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        holder.tvTitle.setText(song.title);
        holder.tvTitle.setSelected(true);   // a TextView only scrolls while it is "selected"
        holder.tvSub.setText(song.artist + "  •  " + song.duration);

        // cover: your picture, then a frame from the video, then the gradient
        GradientDrawable cover = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{song.colorStart, song.colorEnd});
        cover.setCornerRadius(density * 10);
        holder.vCover.setBackground(cover);
        if (glass) {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0x33FFFFFF, 0x14FFC83D});
            g.setCornerRadius(density * 16);
            g.setStroke((int) density, 0x44FFD54F);
            holder.itemView.setBackground(g);
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_card);
        }

        int size = (int) (48 * density);
        Bitmap picture = Thumbs.forSong(ctx, song, size);
        if (picture != null) {
            holder.vCover.setImageBitmap(picture);
        } else {
            holder.vCover.setImageDrawable(null);
        }

        // tick box while selecting, otherwise the remove (X) and play buttons
        holder.cbPick.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.cbPick.setChecked(selected.contains(position));
        holder.btnRemove.setVisibility((removable && !selectionMode) ? View.VISIBLE : View.GONE);
        holder.btnPlay.setVisibility(selectionMode ? View.GONE : View.VISIBLE);

        holder.btnDrag.setVisibility((reorderable && !selectionMode) ? View.VISIBLE : View.GONE);
        holder.btnDrag.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && touchHelper != null) {
                touchHelper.startDrag(holder);
            }
            return false;
        });

        // the song that is playing: cyan title, pause icon
        boolean current = String.valueOf(song.resId).equals(currentMediaId);
        holder.tvTitle.setTextColor(ContextCompat.getColor(ctx,
                current ? R.color.accent_cyan_light : R.color.text_primary));
        holder.btnPlay.setImageResource(
                (current && isPlaying) ? R.drawable.ic_pause : R.drawable.ic_play);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onRowClick(pos);
        });
        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onRowLongClick(pos);
            return true;
        });
        holder.btnPlay.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onPlayClick(pos);
        });
        holder.btnRemove.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onRemoveClick(pos);
        });
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    static class RowViewHolder extends RecyclerView.ViewHolder {
        ImageView vCover, btnPlay, btnRemove, btnDrag;
        TextView tvTitle, tvSub;
        CheckBox cbPick;

        RowViewHolder(@NonNull View itemView) {
            super(itemView);
            vCover = itemView.findViewById(R.id.vCover);
            btnPlay = itemView.findViewById(R.id.btnPlay);
            btnRemove = itemView.findViewById(R.id.btnRemove);
            btnDrag = itemView.findViewById(R.id.btnDrag);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSub = itemView.findViewById(R.id.tvSub);
            cbPick = itemView.findViewById(R.id.cbPick);
        }
    }
}