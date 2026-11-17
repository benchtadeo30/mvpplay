package com.appdev.mvpplay;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// groups the podcast episodes into shows: "podcast_show__ep2_title" = show + episode 2
public class PodcastShows {

    public static class Show {
        public final String name;
        public final List<Song> episodes;

        Show(String name, List<Song> episodes) {
            this.name = name;
            this.episodes = episodes;
        }

        public long totalMs() {
            long total = 0;
            for (Song s : episodes) total += s.durationMs;
            return total;
        }
    }

    private static final Pattern EPISODE_NUMBER = Pattern.compile("^ep(\\d+)_");

    // the number in "ep2_title" after the double underscore (0 = the file has no number)
    public static int episodeNumberOf(Context context, int resId) {
        if (CustomPodcastStore.isEpisode(resId)) {
            return CustomPodcastStore.episodeNumber(context, resId);
        }
        try {
            String file = context.getResources().getResourceEntryName(resId);
            int at = file.indexOf("__");
            if (at < 0) return 0;
            Matcher matcher = EPISODE_NUMBER.matcher(file.substring(at + 2));
            if (matcher.find()) return Integer.parseInt(matcher.group(1));
        } catch (Exception ignored) {
        }
        return 0;
    }

    public static int episodeNumber(Context context, Song episode) {
        return episodeNumberOf(context, episode.resId);
    }

    // one Show per show name; its episodes are ordered by number
    public static List<Show> group(final Context context, List<Song> episodes) {
        Map<String, List<Song>> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Song s : episodes) {
            List<Song> list = groups.get(s.artist);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(s.artist, list);
            }
            list.add(s);
        }

        List<Show> shows = new ArrayList<>();
        for (Map.Entry<String, List<Song>> entry : groups.entrySet()) {
            List<Song> list = entry.getValue();
            Collections.sort(list, (a, b) -> {
                int na = episodeNumber(context, a);
                int nb = episodeNumber(context, b);
                if (na == 0) na = Integer.MAX_VALUE;   // episodes without a number go last
                if (nb == 0) nb = Integer.MAX_VALUE;
                if (na != nb) return Integer.compare(na, nb);
                return a.title.compareToIgnoreCase(b.title);
            });
            shows.add(new Show(entry.getKey(), list));
        }
        SortHelper.sortGroups(context, shows, s -> s.name, Show::totalMs);
        return shows;
    }
}