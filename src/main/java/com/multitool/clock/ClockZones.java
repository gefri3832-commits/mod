package com.multitool.clock;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * Список часовых поясов для выбора и разбор сохранённых значений. Пустая строка означает
 * «часовой пояс системы». Правила перехода на летнее время берутся из базы часовых поясов Java.
 */
public final class ClockZones {
    public static final String SYSTEM = "";

    /** Пояса для выбора: первым идёт системный. */
    public static final String[] PRESETS = {
            SYSTEM, "UTC", "Europe/London", "Europe/Berlin", "Europe/Moscow", "Europe/Istanbul",
            "Asia/Dubai", "Asia/Kolkata", "Asia/Bangkok", "Asia/Shanghai", "Asia/Tokyo", "Asia/Seoul",
            "Australia/Sydney", "Pacific/Auckland", "America/Sao_Paulo", "America/New_York",
            "America/Chicago", "America/Denver", "America/Los_Angeles", "America/Anchorage",
            "Pacific/Honolulu", "Africa/Cairo", "Africa/Johannesburg"
    };

    private ClockZones() {
    }

    /** Пояс по сохранённой строке; неизвестное значение превращается в пояс системы. */
    public static ZoneId resolve(String zone) {
        if (zone == null || zone.isEmpty()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            return ZoneId.systemDefault();
        }
    }

    public static boolean isValid(String zone) {
        if (zone == null) {
            return false;
        }
        if (zone.isEmpty()) {
            return true;
        }
        try {
            ZoneId.of(zone);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    /** Следующий (dir=1) или предыдущий (dir=-1) пояс из списка по кругу. */
    public static String cycle(String current, int dir) {
        int idx = 0;
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i].equals(current)) {
                idx = i;
                break;
            }
        }
        int n = PRESETS.length;
        return PRESETS[((idx + dir) % n + n) % n];
    }

    /** Имя по умолчанию: город из идентификатора пояса. */
    public static String defaultName(String zone) {
        if (zone == null || zone.isEmpty()) {
            return "Local";
        }
        int slash = zone.lastIndexOf('/');
        return (slash >= 0 ? zone.substring(slash + 1) : zone).replace('_', ' ');
    }

    /** Подпись пояса для интерфейса: «System (Europe/Moscow)» либо идентификатор. */
    public static String label(String zone) {
        return (zone == null || zone.isEmpty()) ? "System (" + ZoneId.systemDefault().getId() + ")" : zone;
    }
}
