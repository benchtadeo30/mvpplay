package com.appdev.mvpplay;

import android.content.Context;
import android.graphics.Bitmap;
import android.content.res.ColorStateList;
import android.graphics.Color;
import androidx.core.widget.ImageViewCompat;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder> {

    public interface Listener {
        void onEpisodeClick(int position);   // tap on the row: play it and open Now Playing
        void onPlayClick(int position);      // round button: play or pause only

        // the ⋮ button (only shown when a screen turns it on)
        default void onMoreClick(int position) {
        }
        // the heart button (only shown when a screen turns it on)
        default void onFavoriteClick(int position) {
        }
    }

    private final List<Song> episodes;
    private final Listener listener;
    private String currentMediaId = null;
    private boolean isPlaying = false;
    private boolean showMore = false;
    private boolean showHeart = false;

    public void setShowHeart(boolean show) {
        showHeart = show;
        notifyDataSetChanged();
    }

    public void setShowMore(boolean show) {
        showMore = show;
        notifyDataSetChanged();
    }

    public EpisodeAdapter(List<Song> episodes, Listener listener) {
        this.episodes = episodes;
        this.listener = listener;
    }

    public void setPlaybackState(String mediaId, boolean playing) {
        this.currentMediaId = mediaId;
        this.isPlaying = playing;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EpisodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_episode, parent, false);
        return new EpisodeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EpisodeViewHolder holder, int position) {
        Song episode = episodes.get(position);
        Context ctx = holder.itemView.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        int number = PodcastShows.episodeNumber(ctx, episode);
        long minutes = episode.durationMs / 60000;
        holder.tvNumber.setText(number > 0 ? "EP " + number : "EPISODE");
        holder.tvMeta.setText((episode.hasVideo ? "VIDEO  •  " : "")
                + (minutes < 1 ? "<1 min" : minutes + " min"));
        holder.tvTitle.setText(episode.title);
        holder.tvTitle.setSelected(true);

        // the description from strings.xml (the string named like the file)
        String file = "";
        try {
            file = ctx.getResources().getResourceEntryName(episode.resId);
        } catch (Exception ignored) {
        }
        int stringId = ctx.getResources().getIdentifier(file, "string", ctx.getPackageName());
        if (episode.isCustom()) {
            // your own episode: the description you wrote
            holder.tvDesc.setText(episode.plays);
            holder.tvDesc.setVisibility(episode.plays.isEmpty() ? View.GONE : View.VISIBLE);
        } else if (stringId != 0) {
            holder.tvDesc.setText(ctx.getString(stringId));
            holder.tvDesc.setVisibility(View.VISIBLE);
        } else {
            holder.tvDesc.setVisibility(View.GONE);
        }

        // cover
        GradientDrawable cover = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{episode.colorStart, episode.colorEnd});
        cover.setCornerRadius(density * 12);
        holder.ivCover.setBackground(cover);
        int size = (int) (64 * density);
        holder.ivCover.setImageBitmap(Thumbs.forSong(ctx, episode, size));

        // the episode that is playing: cyan title and a pause icon
        boolean current = String.valueOf(episode.resId).equals(currentMediaId);
        holder.tvTitle.setTextColor(ContextCompat.getColor(ctx,
                current ? R.color.accent_cyan_light : R.color.text_primary));
        holder.btnPlay.setImageResource(
                (current && isPlaying) ? R.drawable.ic_pause : R.drawable.ic_play);

        holder.btnFav.setVisibility(showHeart ? View.VISIBLE : View.GONE);
        boolean liked = FavoritesStore.isFavorite(ctx, episode.resId);
        holder.btnFav.setImageResource(liked ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        ImageViewCompat.setImageTintList(holder.btnFav, ColorStateList.valueOf(
                liked ? Color.parseColor("#F472B6")
                        : ContextCompat.getColor(ctx, R.color.text_secondary)));
        holder.btnFav.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onFavoriteClick(pos);
        });

        holder.btnMore.setVisibility(showMore ? View.VISIBLE : View.GONE);
        holder.btnMore.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onMoreClick(pos);
        });

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onEpisodeClick(pos);
        });
        holder.btnPlay.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onPlayClick(pos);
        });
    }

    @Override
    public int getItemCount() {
        return episodes.size();
    }

    static class EpisodeViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover, btnPlay, btnMore, btnFav;
        TextView tvNumber, tvMeta, tvTitle, tvDesc;

        EpisodeViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.ivEpCover);
            btnPlay = itemView.findViewById(R.id.btnEpPlay);
            btnMore = itemView.findViewById(R.id.btnEpMore);
            btnFav = itemView.findViewById(R.id.btnEpFav);
            tvNumber = itemView.findViewById(R.id.tvEpNumber);
            tvMeta = itemView.findViewById(R.id.tvEpMeta);
            tvTitle = itemView.findViewById(R.id.tvEpTitle);
            tvDesc = itemView.findViewById(R.id.tvEpDesc);
        }
    }
}