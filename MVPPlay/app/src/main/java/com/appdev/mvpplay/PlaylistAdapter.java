package com.appdev.mvpplay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import android.content.res.ColorStateList;
import androidx.core.widget.ImageViewCompat;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> {

    public interface Listener {
        void onPlaylistClick(int position);   // tap on the card: play it and open Now Playing
        void onPlayClick(int position);       // tap on the round button: play or pause only
    }

    private final List<Playlist> playlists;
    private final Listener listener;

    private String activeName = null;   // the playlist that is playing right now
    private boolean isPlaying = false;

    public PlaylistAdapter(List<Playlist> playlists, Listener listener) {
        this.playlists = playlists;
        this.listener = listener;
    }

    public void setPlaybackState(String activePlaylistName, boolean playing) {
        this.activeName = activePlaylistName;
        this.isPlaying = playing;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);
        Context ctx = holder.itemView.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        holder.tvName.setText(playlist.name);
        holder.tvName.setSelected(true);
        holder.tvSub.setText(playlist.subtitle);
        holder.tvTag.setText(playlist.tag);
        holder.tvMeta.setText(playlist.stats());

        // gradient cover, with the first song's picture on top if it has one
        GradientDrawable cover = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{playlist.colorStart, playlist.colorEnd});
        cover.setCornerRadius(density * 14);
        holder.ivCover.setBackground(cover);

        // the picture the user picked comes first, then the first song's picture
        String coverPath = PlaylistCoverStore.path(ctx, PlaylistCoverStore.keyFor(playlist));
        Bitmap picture = coverPath != null
                ? Thumbs.file(coverPath, (int) (152 * density)) : null;
        if (picture == null) {
            int builtIn = PlaylistCoverStore.defaultCover(playlist);
            if (builtIn != 0) picture = Thumbs.get(ctx, builtIn, (int) (152 * density));
        }
        if (picture == null && !playlist.songs.isEmpty()) {
            picture = Thumbs.forSong(ctx, playlist.songs.get(0), (int) (152 * density));
        }
        if (picture != null) {
            holder.ivCover.setImageBitmap(picture);
        } else {
            holder.ivCover.setImageDrawable(null);
        }

        // the playing playlist: cyan name and a pause icon
        boolean current = playlist.name.equals(activeName);
        holder.tvName.setTextColor(ContextCompat.getColor(ctx,
                current ? R.color.accent_cyan_light : R.color.text_primary));
        holder.btnPlay.setBackgroundResource(position % 2 == 0
                ? R.drawable.bg_circle_purple : R.drawable.bg_circle_cyan);
        holder.btnPlay.setImageResource(
                (current && isPlaying) ? R.drawable.ic_pause : R.drawable.ic_play);

        styleCard(holder, playlist, ctx);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onPlaylistClick(pos);
        });
        holder.btnPlay.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onPlayClick(pos);
        });
    }

    // gold look for the special playlist; every other card gets the normal look back
    // (cards are reused while scrolling, so both cases are set every time)
    private void styleCard(PlaylistViewHolder h, Playlist playlist, Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;
        if (playlist.special) {
            h.itemView.setBackground(GoldStyle.background(ctx, 16));
            h.tvName.setTextColor(GoldStyle.TEXT_DARK);
            h.tvSub.setTextColor(GoldStyle.TEXT_DARK_SOFT);
            h.tvMeta.setTextColor(GoldStyle.TEXT_DARK_SOFT);
            ImageViewCompat.setImageTintList(h.ivMetaIcon,
                    ColorStateList.valueOf(GoldStyle.TEXT_DARK_SOFT));
            h.tvTag.setBackground(GoldStyle.tagBackground(ctx));
            h.tvTag.setTextColor(GoldStyle.TEXT_LIGHT_GOLD);

            GradientDrawable play = new GradientDrawable();
            play.setShape(GradientDrawable.OVAL);
            play.setColor(0xE63A2600);
            h.btnPlay.setBackground(play);
            ImageViewCompat.setImageTintList(h.btnPlay,
                    ColorStateList.valueOf(GoldStyle.TEXT_LIGHT_GOLD));

            h.shine.setCorner(16 * d);
            h.shine.setVisibility(View.VISIBLE);
        } else {
            h.itemView.setBackgroundResource(R.drawable.bg_card);
            h.tvSub.setTextColor(ContextCompat.getColor(ctx, R.color.text_hint));
            h.tvMeta.setTextColor(ContextCompat.getColor(ctx, R.color.text_hint));
            ImageViewCompat.setImageTintList(h.ivMetaIcon, ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.text_hint)));
            h.tvTag.setBackgroundResource(R.drawable.bg_eq_badge);
            h.tvTag.setTextColor(ContextCompat.getColor(ctx, R.color.accent_lavender));
            ImageViewCompat.setImageTintList(h.btnPlay, ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.on_accent)));
            h.shine.setVisibility(View.GONE);
        }
        // the tag keeps the same padding in both looks
        int padH = (int) (8 * d);
        int padV = (int) (4 * d);
        h.tvTag.setPadding(padH, padV, padH, padV);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover, btnPlay, ivMetaIcon;
        ShineView shine;
        TextView tvName, tvSub, tvTag, tvMeta;

        PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMetaIcon = itemView.findViewById(R.id.ivMetaIcon);
            shine = itemView.findViewById(R.id.shine);
            ivCover = itemView.findViewById(R.id.ivCover);
            btnPlay = itemView.findViewById(R.id.btnPlay);
            tvName = itemView.findViewById(R.id.tvName);
            tvSub = itemView.findViewById(R.id.tvSub);
            tvTag = itemView.findViewById(R.id.tvTag);
            tvMeta = itemView.findViewById(R.id.tvMeta);
        }
    }
}
