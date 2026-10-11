package com.multitool.clock;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Форматирование времени для окон часов. Без зависимостей от Minecraft. */
public final class TimeFormats {
    private TimeFormats() {
    }

    public static String time(ZonedDateTime t, boolean h24, boolean seconds) {
        String pattern = h24 ? (seconds ? "HH:mm:ss" : "HH:mm") : (seconds ? "h:mm:ss a" : "h:mm a");
        return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).format(t);
    }

    /** Дата с днём недели, например «Sun, 11 Oct 2026». */
    public static String date(ZonedDateTime t, Locale locale) {
        return DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale).format(t);
    }

    /** Смещение пояса, например «UTC+03:00» или «UTC». */
    public static String offset(ZonedDateTime t) {
        String o = t.getOffset().getId();
        return o.equals("Z") ? "UTC" : "UTC" + o;
    }

    /** Обратный отсчёт: «H:MM:SS» либо «MM:SS». */
    public static String countdown(long ms) {
        long total = Math.max(0, ms) / 1000;
        long h = total / 3600;
        long m = (total / 60) % 60;
        long s = total % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.ROOT, "%02d:%02d", m, s);
    }

    /** Секундомер: «MM:SS.mmm» либо «H:MM:SS.mmm». */
    public static String stopwatch(long ms) {
        ms = Math.max(0, ms);
        long h = ms / 3_600_000L;
        long m = (ms / 60_000L) % 60;
        long s = (ms / 1000L) % 60;
        long milli = ms % 1000L;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d.%03d", h, m, s, milli)
                : String.format(Locale.ROOT, "%02d:%02d.%03d", m, s, milli);
    }

    public static String hm(java.time.LocalTime t) {
        return String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
    }

    public static ZonedDateTime now(ZoneId zone) {
        return ZonedDateTime.now(zone);
    }
}
