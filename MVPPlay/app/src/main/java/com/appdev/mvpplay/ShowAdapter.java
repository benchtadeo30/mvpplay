package com.appdev.mvpplay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ShowAdapter extends RecyclerView.Adapter<ShowAdapter.ShowViewHolder> {

    public interface Listener {
        void onShowClick(PodcastShows.Show show);
    }

    private final List<PodcastShows.Show> shows;
    private final Listener listener;

    public ShowAdapter(List<PodcastShows.Show> shows, Listener listener) {
        this.shows = shows;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ShowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_show, parent, false);
        return new ShowViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShowViewHolder holder, int position) {
        PodcastShows.Show show = shows.get(position);
        Context ctx = holder.itemView.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        Song first = show.episodes.get(0);

        int count = show.episodes.size();
        holder.tvName.setText(show.name);
        holder.tvName.setSelected(true);
        holder.tvMeta.setText((count == 1 ? "1 episode" : count + " episodes")
                + "  •  " + Math.max(1L, show.totalMs() / 60000) + " min");

        // the cover of the first episode: your picture, a video frame, or the gradient
        GradientDrawable cover = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{first.colorStart, first.colorEnd});
        cover.setCornerRadius(density * 14);
        holder.ivCover.setBackground(cover);

        int size = (int) (80 * density);
        holder.ivCover.setImageBitmap(Thumbs.forSong(ctx, first, size));

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onShowClick(shows.get(pos));
        });
    }

    @Override
    public int getItemCount() {
        return shows.size();
    }

    static class ShowViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvName, tvMeta;

        ShowViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.ivShowCover);
            tvName = itemView.findViewById(R.id.tvShowName);
            tvMeta = itemView.findViewById(R.id.tvShowMeta);
        }
    }
}