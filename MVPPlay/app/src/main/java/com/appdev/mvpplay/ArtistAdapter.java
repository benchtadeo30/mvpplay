package com.appdev.mvpplay;

import android.graphics.Bitmap;
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

public class ArtistAdapter extends RecyclerView.Adapter<ArtistAdapter.ArtistViewHolder> {

    // one artist and all of their songs
    public static class Artist {
        public final String name;
        public final List<Song> songs;

        public Artist(String name, List<Song> songs) {
            this.name = name;
            this.songs = songs;
        }
    }

    public interface Listener {
        void onArtistClick(Artist artist);
    }

    private final List<Artist> artists;
    private final Listener listener;

    public ArtistAdapter(List<Artist> artists, Listener listener) {
        this.artists = artists;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ArtistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_artist, parent, false);
        return new ArtistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ArtistViewHolder holder, int position) {
        Artist artist = artists.get(position);
        Context ctx = holder.itemView.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        Song first = artist.songs.get(0);

        holder.tvName.setText(artist.name);
        holder.tvCount.setText(artist.songs.size() == 1 ? "1 song" : artist.songs.size() + " songs");

        // round picture: the first song's cover, or a gradient with the first letter
        GradientDrawable circle = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{first.colorStart, first.colorEnd});
        circle.setShape(GradientDrawable.OVAL);
        holder.ivPic.setBackground(circle);

        int thumbId = Thumbs.thumbForRaw(ctx, first.resId);
        Bitmap custom = (first.thumbPath != null)
                ? Thumbs.file(first.thumbPath, (int) (52 * density)) : null;
        if (custom != null) {
            holder.ivPic.setImageBitmap(custom);
            holder.tvInitial.setVisibility(View.GONE);
        } else if (thumbId != 0) {
            holder.ivPic.setImageBitmap(Thumbs.get(ctx, thumbId, (int) (52 * density)));
            holder.tvInitial.setVisibility(View.GONE);
        } else {
            holder.ivPic.setImageDrawable(null);
            holder.tvInitial.setText(artist.name.isEmpty() ? "?" : artist.name.substring(0, 1).toUpperCase());
            holder.tvInitial.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onArtistClick(artists.get(pos));
        });
    }

    @Override
    public int getItemCount() {
        return artists.size();
    }

    static class ArtistViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPic;
        TextView tvInitial, tvName, tvCount;

        ArtistViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPic = itemView.findViewById(R.id.ivArtistPic);
            tvInitial = itemView.findViewById(R.id.tvInitial);
            tvName = itemView.findViewById(R.id.tvArtistName);
            tvCount = itemView.findViewById(R.id.tvArtistCount);
        }
    }
}