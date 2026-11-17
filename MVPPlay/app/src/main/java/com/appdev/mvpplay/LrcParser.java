package com.appdev.mvpplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// reads lyrics with time tags like [01:23.45] Some line
public class LrcParser {

    public static class Line {
        public final long timeMs;
        public final String text;

        Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern TAG = Pattern.compile("^\\[[a-zA-Z]+:.*]$");

    // the synced lines, in time order (empty list = the text has no time tags)
    public static List<Line> parse(String raw) {
        List<Line> lines = new ArrayList<>();
        for (String row : raw.split("\\r?\\n")) {
            Matcher m = TIME.matcher(row);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                long ms = 0;
                String frac = m.group(3);
                if (frac != null) {
                    ms = Long.parseLong(frac);
                    if (frac.length() == 1) ms *= 100;
                    else if (frac.length() == 2) ms *= 10;
                }
                times.add(min * 60000 + sec * 1000 + ms);
                end = m.end();
            }
            if (times.isEmpty()) continue;
            String text = row.substring(end).trim();
            for (long t : times) lines.add(new Line(t, text));
        }
        Collections.sort(lines, new Comparator<Line>() {
            @Override
            public int compare(Line a, Line b) {
                return Long.compare(a.timeMs, b.timeMs);
            }
        });
        return lines;
    }

    // the plain lines (time tags and info tags like [ar:...] removed)
    public static List<String> plain(String raw) {
        List<String> out = new ArrayList<>();
        for (String row : raw.split("\\r?\\n")) {
            String t = row.trim();
            if (TAG.matcher(t).matches()) continue;
            out.add(TIME.matcher(t).replaceAll("").trim());
        }
        return out;
    }
}