package com.appdev.mvpplay;

import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

// one playlist per row (used by the Playlists tab and by Search)
public class PlaylistRowAdapter extends RecyclerView.Adapter<PlaylistRowAdapter.RowViewHolder> {

    public interface Listener {
        void onPlaylistClick(Playlist playlist);
    }

    private final List<Playlist> playlists;
    private final Listener listener;

    public PlaylistRowAdapter(List<Playlist> playlists, Listener listener) {
        this.playlists = playlists;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist_row, parent, false);
        return new RowViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RowViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);
        Context ctx = holder.itemView.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        holder.tvName.setText(playlist.name);
        holder.tvName.setSelected(true);   // a TextView only scrolls while it is "selected"
        holder.tvSub.setText(playlist.subtitle);
        holder.tvMeta.setText(playlist.stats());
        holder.tvTag.setText(playlist.tag);

        GradientDrawable cover = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{playlist.colorStart, playlist.colorEnd});
        cover.setCornerRadius(density * 12);
        holder.ivCover.setBackground(cover);

        // the picture the user picked comes first, then the first song's thumbnail
        String coverPath = PlaylistCoverStore.path(ctx, PlaylistCoverStore.keyFor(playlist));
        android.graphics.Bitmap customCover = coverPath != null
                ? Thumbs.file(coverPath, (int) (56 * density)) : null;
        int thumbId = PlaylistCoverStore.defaultCover(playlist);
        if (thumbId == 0 && !playlist.songs.isEmpty()) {
            thumbId = Thumbs.thumbForRaw(ctx, playlist.songs.get(0).resId);
        }
        if (customCover != null) {
            holder.ivCover.setImageBitmap(customCover);
        } else if (thumbId != 0) {
            holder.ivCover.setImageBitmap(Thumbs.get(ctx, thumbId, (int) (56 * density)));
        } else {
            holder.ivCover.setImageDrawable(null);
        }

        styleRow(holder, playlist, ctx);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onPlaylistClick(playlists.get(pos));
        });
    }

    // gold look for the special playlist; every other row gets the normal look back
    // (rows are reused while scrolling, so both cases are set every time)
    private void styleRow(RowViewHolder h, Playlist playlist, Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;
        if (playlist.special) {
            h.itemView.setBackground(GoldStyle.background(ctx, 16));
            h.tvName.setTextColor(GoldStyle.TEXT_DARK);
            h.tvSub.setTextColor(GoldStyle.TEXT_DARK_SOFT);
            h.tvMeta.setTextColor(GoldStyle.TEXT_DARK_SOFT);
            h.tvTag.setBackground(GoldStyle.tagBackground(ctx));
            h.tvTag.setTextColor(GoldStyle.TEXT_LIGHT_GOLD);
            ImageViewCompat.setImageTintList(h.ivChevron,
                    ColorStateList.valueOf(GoldStyle.TEXT_DARK));
            h.shine.setCorner(16 * d);
            h.shine.setVisibility(View.VISIBLE);
        } else {
            h.itemView.setBackgroundResource(R.drawable.bg_card);
            h.tvName.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
            h.tvSub.setTextColor(ContextCompat.getColor(ctx, R.color.text_hint));
            h.tvMeta.setTextColor(ContextCompat.getColor(ctx, R.color.text_hint));
            h.tvTag.setBackgroundResource(R.drawable.bg_tag_lavender);
            h.tvTag.setTextColor(ContextCompat.getColor(ctx, R.color.accent_lavender));
            ImageViewCompat.setImageTintList(h.ivChevron, ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.text_hint)));
            h.shine.setVisibility(View.GONE);
        }
        // the tag keeps the same padding in both looks
        int padH = (int) (8 * d);
        int padV = (int) (3 * d);
        h.tvTag.setPadding(padH, padV, padH, padV);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    static class RowViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover, ivChevron;
        ShineView shine;
        TextView tvName, tvSub, tvMeta, tvTag;

        RowViewHolder(@NonNull View itemView) {
            super(itemView);
            ivChevron = itemView.findViewById(R.id.ivChevron);
            shine = itemView.findViewById(R.id.shine);
            ivCover = itemView.findViewById(R.id.ivCover);
            tvName = itemView.findViewById(R.id.tvName);
            tvSub = itemView.findViewById(R.id.tvSub);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            tvTag = itemView.findViewById(R.id.tvTag);
        }
    }
}