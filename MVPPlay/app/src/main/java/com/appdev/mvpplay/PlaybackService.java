package com.appdev.mvpplay;

import android.content.Intent;
import android.app.PendingIntent;

import android.content.Context;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

@OptIn(markerClass = UnstableApi.class)
public class PlaybackService extends MediaSessionService {

    private MediaSession mediaSession;

    // remembers the song and place for "Resume the last song"
    private void saveLast() {
        if (mediaSession == null) return;
        Player p = mediaSession.getPlayer();
        MediaItem item = p.getCurrentMediaItem();
        if (item == null) return;
        try {
            String file = getResources().getResourceEntryName(Integer.parseInt(item.mediaId));
            AppSettings.saveLast(this, file, p.getCurrentPosition());
        } catch (Exception ignored) {
        }
    }

    // podcasts play at the speed chosen in Settings, songs always at normal speed
    private void applySpeed(Player player, @Nullable MediaItem item) {
        // a speed picked on Now Playing applies to everything
        float chosen = SpeedStore.get(this);
        if (chosen > 0) {
            player.setPlaybackSpeed(chosen);
            return;
        }
        boolean podcast = false;
        if (item != null) {
            try {
                int resId = Integer.parseInt(item.mediaId);
                // your own episodes count as podcasts too (they are not files in res/raw)
                podcast = CustomPodcastStore.isEpisode(resId)
                        || getResources().getResourceEntryName(resId).startsWith("podcast_");
            } catch (Exception ignored) {
            }
        }
        player.setPlaybackSpeed(podcast ? AppSettings.podcastSpeed(this) : 1f);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        // lets the waveform on Now Playing react to the real sound
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this) {
            @Override
            protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput,
                                               boolean enableAudioTrackPlaybackParams) {
                return new DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(new AudioProcessor[]{new LevelAudioProcessor()})
                        .build();
            }
        };

        ExoPlayer player = new ExoPlayer.Builder(this, renderers)
                .setAudioAttributes(attributes, true)   // pauses for calls and other apps
                .setHandleAudioBecomingNoisy(true)      // pauses when headphones are unplugged
                .setWakeMode(C.WAKE_MODE_LOCAL)         // keeps playing with the screen off
                .build();

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (!isPlaying) saveLast();
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                saveLast();
                applySpeed(player, mediaItem);
            }
        });

        // tapping the notification opens the Now Playing screen
        Intent openNowPlaying = new Intent(this, NowPlayingActivity.class);
        openNowPlaying.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent sessionActivity = PendingIntent.getActivity(this, 0, openNowPlaying,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivity)
                .setCallback(new MediaSession.Callback() {
                    @Override
                    public MediaSession.ConnectionResult onConnect(
                            MediaSession session, MediaSession.ControllerInfo controller) {
                        // allow the app's screens to use every player control
                        Player.Commands allCommands =
                                new Player.Commands.Builder().addAllCommands().build();
                        return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                                .setAvailablePlayerCommands(allCommands)
                                .build();
                    }
                })
                .build();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        saveLast();
        // user swiped the app away: stop the music, clear the queue and close the service
        if (mediaSession != null) {
            mediaSession.getPlayer().stop();
            mediaSession.getPlayer().clearMediaItems();
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        saveLast();
        if (mediaSession != null) {
            mediaSession.getPlayer().release();
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }
}