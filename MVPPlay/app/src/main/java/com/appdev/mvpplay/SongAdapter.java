package com.appdev.mvpplay;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    public interface OnSongClickListener {
        void onSongClick(int position);       // tap on the row body
        void onPlayClick(int position);       // tap on the round play icon
        void onSaveClick(int position);       // tap on the Save to playlist icon
        void onSongLongClick(int position);   // long press on the row
    }

    private final List<Song> songs;
    private final OnSongClickListener listener;

    // which song is current, and whether it is playing right now
    private String currentMediaId = null;
    private boolean isPlaying = false;

    // ticking songs to make a playlist
    private boolean selectionMode = false;
    private final Set<Integer> selected = new HashSet<>();

    public SongAdapter(List<Song> songs, OnSongClickListener listener) {
        this.songs = songs;
        this.listener = listener;
    }

    // called by MainActivity whenever the player changes
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
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songs.get(position);
        Context ctx = holder.itemView.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        holder.tvTitle.setText(song.title);
        holder.tvTitle.setSelected(true);   // a TextView only scrolls while it is "selected"
        holder.tvArtist.setText(song.artist);
        holder.tvPlays.setText(song.plays);
        holder.tvDuration.setText(song.duration);

        // HI-RES badge and FLAC label only for hi-res songs
        int visibility = song.hiRes ? View.VISIBLE : View.GONE;
        holder.tvHiRes.setVisibility(visibility);
        holder.tvFormat.setVisibility(visibility);

        // hi-res songs show their play count in cyan, others in gray
        int playsColor = ContextCompat.getColor(ctx,
                song.hiRes ? R.color.accent_cyan_light : R.color.text_hint);
        holder.tvPlays.setTextColor(playsColor);
        holder.ivHeadphones.setColorFilter(playsColor);

        // cover: your picture, then a frame from the video, then the gradient
        GradientDrawable cover = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{song.colorStart, song.colorEnd});
        cover.setCornerRadius(density * 10);
        holder.vCover.setBackground(cover);

        int size = (int) (56 * density);
        Bitmap picture = null;
        if (song.thumbPath != null) {
            picture = Thumbs.file(song.thumbPath, size);
        } else {
            int thumbId = Thumbs.thumbForRaw(ctx, song.resId);
            if (thumbId != 0) {
                picture = Thumbs.get(ctx, thumbId, size);
            } else if (song.hasVideo) {
                picture = Thumbs.videoFrame(ctx, song.resId, size);
            }
        }
        if (picture != null) {
            holder.vCover.setImageBitmap(picture);
        } else {
            holder.vCover.setImageDrawable(null);
        }

        // tick box while selecting, otherwise the Save and Play icons
        holder.cbPick.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.cbPick.setChecked(selected.contains(position));
        holder.btnSave.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
        holder.btnPlay.setVisibility(selectionMode ? View.GONE : View.VISIBLE);

        // current song: cyan title, cyan button, pause icon while playing
        boolean isCurrent = String.valueOf(song.resId).equals(currentMediaId);
        holder.tvTitle.setTextColor(ContextCompat.getColor(ctx,
                isCurrent ? R.color.accent_cyan_light : R.color.text_primary));
        holder.btnPlay.setImageResource(
                (isCurrent && isPlaying) ? R.drawable.ic_pause : R.drawable.ic_play);
        ImageViewCompat.setImageTintList(holder.btnPlay, ColorStateList.valueOf(
                ContextCompat.getColor(ctx,
                        isCurrent ? R.color.accent_cyan_light : R.color.text_primary)));

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onSongClick(pos);
        });
        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onSongLongClick(pos);
            return true;
        });
        holder.btnPlay.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onPlayClick(pos);
        });
        holder.btnSave.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onSaveClick(pos);
        });
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        ImageView vCover, ivHeadphones, btnPlay, btnSave;
        TextView tvTitle, tvArtist, tvPlays, tvDuration, tvHiRes, tvFormat;
        CheckBox cbPick;

        SongViewHolder(@NonNull View itemView) {
            super(itemView);
            vCover = itemView.findViewById(R.id.vCover);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            tvPlays = itemView.findViewById(R.id.tvPlays);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            tvHiRes = itemView.findViewById(R.id.tvHiRes);
            tvFormat = itemView.findViewById(R.id.tvFormat);
            ivHeadphones = itemView.findViewById(R.id.ivHeadphones);
            btnPlay = itemView.findViewById(R.id.btnPlay);
            btnSave = itemView.findViewById(R.id.btnSave);
            cbPick = itemView.findViewById(R.id.cbPick);
        }
    }
}