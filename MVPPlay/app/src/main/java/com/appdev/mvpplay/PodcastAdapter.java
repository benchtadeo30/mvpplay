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

// For a podcast "Song": title = episode, artist = show name, plays = "Host: ..." (or empty)
public class PodcastAdapter extends RecyclerView.Adapter<PodcastAdapter.PodcastViewHolder> {

    public interface Listener {
        void onEpisodeClick(int position);   // tap on the card: play it and open Now Playing
        void onListenClick(int position);    // tap on the LISTEN / WATCH row
    }

    private final List<Song> episodes;
    private final Listener listener;

    private String currentMediaId = null;
    private boolean isPlaying = false;

    public PodcastAdapter(List<Song> episodes, Listener listener) {
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
    public PodcastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_podcast, parent, false);
        return new PodcastViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PodcastViewHolder holder, int position) {
        Song episode = episodes.get(position);
        Context ctx = holder.itemView.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        holder.tvShow.setText(episode.artist);
        holder.tvShow.setSelected(true);
        holder.tvHost.setText(episode.plays);
        holder.tvHost.setVisibility(episode.plays.isEmpty() ? View.GONE : View.VISIBLE);
        holder.tvEpisode.setText("\u201C" + episode.title + "\u201D");
        holder.tvEpisode.setSelected(true);

        long minutes = episode.durationMs / 60000;
        holder.tvMinutes.setText(minutes < 1 ? "<1 min" : minutes + " min");

        // video episodes get their own tag and icon
        holder.tvTag.setText(episode.hasVideo ? "VIDEO" : "EPISODE");
        holder.ivKind.setImageResource(episode.hasVideo ? R.drawable.ic_videocam : R.drawable.ic_mic);

        // cover: your picture first, then a frame from the video, then the gradient
        GradientDrawable cover = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{episode.colorStart, episode.colorEnd});
        cover.setCornerRadius(density * 14);
        holder.vCover.setBackground(cover);

        int size = (int) (80 * density);
        Bitmap picture = Thumbs.forSong(ctx, episode, size);
        if (picture != null) {
            holder.vCover.setImageBitmap(picture);
        } else {
            holder.vCover.setImageDrawable(null);
        }

        // the playing episode: the row shows PAUSE
        boolean current = String.valueOf(episode.resId).equals(currentMediaId);
        boolean showPause = current && isPlaying;
        holder.tvListen.setText(showPause ? "PAUSE" : (episode.hasVideo ? "WATCH" : "LISTEN"));
        holder.ivListen.setImageResource(showPause ? R.drawable.ic_pause : R.drawable.ic_play_circle);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onEpisodeClick(pos);
        });
        holder.rowListen.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onListenClick(pos);
        });
    }

    @Override
    public int getItemCount() {
        return episodes.size();
    }

    static class PodcastViewHolder extends RecyclerView.ViewHolder {
        ImageView vCover, ivListen, ivKind;
        TextView tvShow, tvHost, tvEpisode, tvMinutes, tvListen, tvTag;
        View rowListen;

        PodcastViewHolder(@NonNull View itemView) {
            super(itemView);
            vCover = itemView.findViewById(R.id.vCover);
            ivListen = itemView.findViewById(R.id.ivListen);
            ivKind = itemView.findViewById(R.id.ivKind);
            tvShow = itemView.findViewById(R.id.tvShow);
            tvHost = itemView.findViewById(R.id.tvHost);
            tvEpisode = itemView.findViewById(R.id.tvEpisode);
            tvMinutes = itemView.findViewById(R.id.tvMinutes);
            tvListen = itemView.findViewById(R.id.tvListen);
            tvTag = itemView.findViewById(R.id.tvTag);
            rowListen = itemView.findViewById(R.id.rowListen);
        }
    }
}