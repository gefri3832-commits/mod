package com.multitool.clock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Определяет, какие будильники сработали с прошлой проверки. Устойчив к смене системного времени:
 * если время ушло назад, окно проверки сбрасывается; если вперёд или проверка запоздала (сон,
 * зависание), срабатывают только сигналы не старше {@link #MAX_LATE_SECONDS}, остальные пропускаются
 * и не воспроизводятся «задним числом».
 */
public final class AlarmScheduler {
    public static final long MAX_LATE_SECONDS = 60;

    private final List<Alarm> alarms;
    private Instant lastCheck;

    public AlarmScheduler(List<Alarm> alarms, Instant start) {
        this.alarms = alarms;
        this.lastCheck = start;
    }

    /**
     * При запуске: однократные будильники с датой в прошлом выключаются (пропущены, пока игра была закрыта),
     * устаревший «отложенный» сигнал сбрасывается.
     */
    public void expireMissed(Instant now) {
        for (Alarm a : alarms) {
            if (a.snoozeUntil != null && !a.snoozeUntil.isAfter(now)) {
                a.snoozeUntil = null;
            }
            if (a.enabled && !a.repeating() && a.date != null) {
                Instant t = a.nextFire(Instant.EPOCH);
                if (t == null || !t.isAfter(now)) {
                    a.enabled = false;
                }
            }
        }
        lastCheck = now;
    }

    /** @return будильники, сработавшие в интервале (прошлая проверка, now] */
    public List<Alarm> tick(Instant now) {
        Instant from = lastCheck;
        if (now.isBefore(from)) {
            from = now;
        }
        Instant floor = now.minusSeconds(MAX_LATE_SECONDS);
        if (from.isBefore(floor)) {
            from = floor;
        }
        List<Alarm> fired = new ArrayList<>();
        for (Alarm a : alarms) {
            if (a.snoozeUntil != null) {
                Instant s = a.snoozeUntil;
                if (!s.isAfter(now)) {
                    a.snoozeUntil = null;
                    if (!s.isBefore(floor)) {
                        fired.add(a);
                    }
                    continue;
                }
            }
            if (!a.enabled) {
                continue;
            }
            Instant t = a.nextFire(from);
            if (t != null && !t.isAfter(now)) {
                fired.add(a);
                if (!a.repeating()) {
                    a.enabled = false;
                }
            }
        }
        lastCheck = now;
        return fired;
    }
}
