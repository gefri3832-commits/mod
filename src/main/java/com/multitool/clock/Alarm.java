package com.multitool.clock;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Будильник. Работает по реальному времени и выбранному поясу. Без дней недели срабатывает один раз
 * (в указанную дату либо в ближайшее наступление времени); с днями недели — повторяется.
 */
public final class Alarm {
    public String name;
    public LocalTime time;
    /** Дата для однократного будильника; игнорируется, если выбраны дни недели. */
    public LocalDate date;
    public final Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    /** Пояс: пустая строка — системный. */
    public String zone = ClockZones.SYSTEM;
    public boolean enabled = true;
    public int sound;
    public int volume;
    /** Время, когда отложенный сигнал сработает снова; null — не отложен. */
    public Instant snoozeUntil;

    public Alarm(String name, LocalTime time, int sound, int volume) {
        this.name = name;
        this.time = time;
        this.sound = sound;
        this.volume = volume;
    }

    public ZoneId zoneId() {
        return ClockZones.resolve(zone);
    }

    public boolean repeating() {
        return !days.isEmpty();
    }

    /** Ближайший момент срабатывания строго после {@code after}, либо null, если больше не сработает. */
    public Instant nextFire(Instant after) {
        ZoneId z = zoneId();
        LocalDate today = after.atZone(z).toLocalDate();
        if (days.isEmpty()) {
            if (date != null) {
                Instant t = at(date, z);
                return t.isAfter(after) ? t : null;
            }
            for (int i = 0; i <= 2; i++) {
                Instant t = at(today.plusDays(i), z);
                if (t.isAfter(after)) {
                    return t;
                }
            }
            return null;
        }
        for (int i = 0; i <= 8; i++) {
            LocalDate d = today.plusDays(i);
            if (days.contains(d.getDayOfWeek())) {
                Instant t = at(d, z);
                if (t.isAfter(after)) {
                    return t;
                }
            }
        }
        return null;
    }

    /** Момент времени для даты; при «пропавшем» часе перехода на летнее время Java сдвигает его вперёд. */
    private Instant at(LocalDate d, ZoneId z) {
        return ZonedDateTime.of(d, time, z).toInstant();
    }
}
