package com.multitool.clock;

import com.multitool.audio.Beeper;
import com.multitool.config.ModConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Всё состояние инструмента Clock: часы, таймеры, секундомер, будильники и активные уведомления.
 * Время берётся из монотонных часов (таймеры, секундомер) и из системных часов (будильники, часы),
 * игровые тики и FPS не участвуют: {@link #tick()} лишь проверяет, не пора ли срабатывать.
 */
public final class ClockService {
    public static final int MAX_TIMERS = 8;
    public static final int MAX_ALARMS = 20;
    public static final int MAX_CLOCKS = 8;
    public static final int MAX_NAME = 20;

    private static final long SAVE_DEBOUNCE_NANOS = 2_000_000_000L;
    private static final int ALARM_REPEATS = 90;
    private static final int TIMER_REPEATS = 3;

    public static final ClockService INSTANCE =
            new ClockService(System::nanoTime, Instant::now, ModConfig.INSTANCE, Beeper::play);

    /** Активное уведомление: звучит и висит на экране, пока его не закроют (для таймеров — ограниченное время). */
    public static final class Notice {
        public final String title;
        public final String text;
        public final Alarm alarm;
        public final CountdownTimer timer;
        final long createdNanos;
        final int sound;
        final int volume;
        int repeatsLeft;
        long lastPlayNanos;

        Notice(String title, String text, Alarm alarm, CountdownTimer timer, long createdNanos, int sound, int volume,
               int repeats) {
            this.title = title;
            this.text = text;
            this.alarm = alarm;
            this.timer = timer;
            this.createdNanos = createdNanos;
            this.sound = sound;
            this.volume = volume;
            this.repeatsLeft = repeats;
            this.lastPlayNanos = createdNanos - 3_600_000_000_000L;
        }

        public boolean isAlarm() {
            return alarm != null;
        }
    }

    private final LongSupplier nanos;
    private final Supplier<Instant> clock;
    private final ModConfig cfg;
    private final BiConsumer<Integer, Integer> player;

    public final List<WorldClock> worldClocks = new ArrayList<>();
    public final List<CountdownTimer> timers = new ArrayList<>();
    public final List<Alarm> alarms = new ArrayList<>();
    public final List<Notice> notices = new ArrayList<>();
    public final Stopwatch stopwatch;
    private final AlarmScheduler scheduler;

    // редактор нового таймера
    public int editH;
    public int editM = 5;
    public int editS;

    private Path file;
    private boolean dirty;
    private long lastSaveNanos;

    public ClockService(LongSupplier nanos, Supplier<Instant> clock, ModConfig cfg,
                        BiConsumer<Integer, Integer> player) {
        this.nanos = nanos;
        this.clock = clock;
        this.cfg = cfg;
        this.player = player;
        this.stopwatch = new Stopwatch(nanos);
        this.scheduler = new AlarmScheduler(alarms, clock.get());
        worldClocks.add(new WorldClock("Local", ClockZones.SYSTEM));
    }

    public LongSupplier nanoSource() {
        return nanos;
    }

    public ModConfig config() {
        return cfg;
    }

    public void markDirty() {
        dirty = true;
    }

    // ---- часы ----

    public boolean addWorldClock() {
        if (worldClocks.size() >= MAX_CLOCKS) {
            return false;
        }
        String zone = "UTC";
        for (String z : ClockZones.PRESETS) {
            if (!z.isEmpty() && worldClocks.stream().noneMatch(w -> w.zone.equals(z))) {
                zone = z;
                break;
            }
        }
        worldClocks.add(new WorldClock(ClockZones.defaultName(zone), zone));
        markDirty();
        return true;
    }

    public void removeWorldClock(int index) {
        if (worldClocks.size() > 1 && index >= 0 && index < worldClocks.size()) {
            worldClocks.remove(index);
            markDirty();
        }
    }

    /** Меняет пояс; если имя было стандартным для прежнего пояса, оно обновляется вслед за ним. */
    public void cycleWorldClockZone(int index, int dir) {
        if (index < 0 || index >= worldClocks.size()) {
            return;
        }
        WorldClock w = worldClocks.get(index);
        boolean defaultName = w.name.equals(ClockZones.defaultName(w.zone));
        w.zone = ClockZones.cycle(w.zone, dir);
        if (defaultName) {
            w.name = ClockZones.defaultName(w.zone);
        }
        markDirty();
    }

    // ---- таймеры ----

    public CountdownTimer addTimer(String name, long ms, boolean start) {
        if (timers.size() >= MAX_TIMERS) {
            return null;
        }
        String n = (name == null || name.isBlank()) ? "Timer " + (timers.size() + 1) : name.trim();
        CountdownTimer t = new CountdownTimer(n, ms, nanos);
        timers.add(t);
        if (start) {
            t.start();
        }
        markDirty();
        return t;
    }

    public void removeTimer(CountdownTimer t) {
        timers.remove(t);
        notices.removeIf(n -> n.timer == t);
        markDirty();
    }

    public long editorMs() {
        return (editH * 3600L + editM * 60L + editS) * 1000L;
    }

    // ---- будильники ----

    public Alarm newAlarm() {
        if (alarms.size() >= MAX_ALARMS) {
            return null;
        }
        ZonedDateTime z = ZonedDateTime.ofInstant(clock.get(), ZoneId.systemDefault());
        LocalTime t = z.toLocalTime().plusHours(1).withMinute(0).withSecond(0).withNano(0);
        Alarm a = new Alarm("Alarm " + (alarms.size() + 1), t, cfg.sound, cfg.volume);
        alarms.add(a);
        markDirty();
        return a;
    }

    public void removeAlarm(Alarm a) {
        alarms.remove(a);
        notices.removeIf(n -> n.alarm == a);
        markDirty();
    }

    // ---- уведомления ----

    public void dismiss(Notice n) {
        notices.remove(n);
    }

    public void dismissAll() {
        notices.clear();
    }

    /** Откладывает сигнал будильника на указанное число минут. Для уведомлений таймера просто закрывает. */
    public void snooze(Notice n, int minutes) {
        if (n.alarm != null) {
            n.alarm.snoozeUntil = clock.get().plusSeconds(minutes * 60L);
            markDirty();
        }
        notices.remove(n);
    }

    public boolean anyAlarmRinging() {
        for (Notice n : notices) {
            if (n.isAlarm()) {
                return true;
            }
        }
        return false;
    }

    /** Проверка таймеров и будильников; вызывается каждый клиентский тик. */
    public void tick() {
        long now = nanos.getAsLong();
        for (CountdownTimer t : timers) {
            if (t.tick()) {
                String text = t.name + " (" + TimeFormats.countdown(t.durationMs()) + ")";
                notices.add(new Notice("Timer finished", text, null, t, now, cfg.sound, cfg.volume, TIMER_REPEATS));
            }
        }
        for (Alarm a : scheduler.tick(clock.get())) {
            notices.removeIf(n -> n.alarm == a);
            notices.add(new Notice(a.name.isBlank() ? "Alarm" : a.name, TimeFormats.hm(a.time), a, null, now,
                    a.sound, a.volume, ALARM_REPEATS));
            markDirty();
        }
        long toastNanos = cfg.toastSeconds * 1_000_000_000L;
        for (int i = notices.size() - 1; i >= 0; i--) {
            Notice n = notices.get(i);
            if (n.repeatsLeft > 0) {
                long interval = Math.max(1_500L, Beeper.durationMs(n.sound) + 700L) * 1_000_000L;
                if (now - n.lastPlayNanos >= interval) {
                    n.lastPlayNanos = now;
                    n.repeatsLeft--;
                    player.accept(n.sound, n.volume);
                }
            }
            if (!n.isAlarm() && now - n.createdNanos > toastNanos) {
                notices.remove(i);
            }
        }
        if (dirty && now - lastSaveNanos >= SAVE_DEBOUNCE_NANOS) {
            save();
        }
    }

    // ---- сохранение ----

    public void setFile(Path file) {
        this.file = file;
    }

    /** Загружает данные и пропускает будильники, которые прошли, пока игра была закрыта. */
    public void load() {
        if (file != null && Files.isRegularFile(file)) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
                read(p);
            } catch (IOException | RuntimeException e) {
                // повреждённый файл: остаёмся с тем, что успели прочитать
            }
        }
        scheduler.expireMissed(clock.get());
    }

    void read(Properties p) {
        List<WorldClock> wcs = new ArrayList<>();
        for (int i = 0, n = Math.min(MAX_CLOCKS, num(p, "clocks.count", 0)); i < n; i++) {
            String zone = p.getProperty("clock." + i + ".zone", "");
            if (ClockZones.isValid(zone)) {
                wcs.add(new WorldClock(name(p.getProperty("clock." + i + ".name"), ClockZones.defaultName(zone)), zone));
            }
        }
        if (!wcs.isEmpty()) {
            worldClocks.clear();
            worldClocks.addAll(wcs);
        }
        timers.clear();
        for (int i = 0, n = Math.min(MAX_TIMERS, num(p, "timers.count", 0)); i < n; i++) {
            long ms = lng(p, "timer." + i + ".ms", 0);
            if (ms >= CountdownTimer.MIN_MS) {
                timers.add(new CountdownTimer(name(p.getProperty("timer." + i + ".name"), "Timer " + (i + 1)), ms, nanos));
            }
        }
        alarms.clear();
        for (int i = 0, n = Math.min(MAX_ALARMS, num(p, "alarms.count", 0)); i < n; i++) {
            Alarm a = readAlarm(p, "alarm." + i + ".");
            if (a != null) {
                alarms.add(a);
            }
        }
    }

    private Alarm readAlarm(Properties p, String k) {
        try {
            LocalTime time = LocalTime.parse(p.getProperty(k + "time", ""));
            Alarm a = new Alarm(name(p.getProperty(k + "name"), "Alarm"), time.withSecond(0).withNano(0),
                    ModConfig.clamp(num(p, k + "sound", 0), 0, Beeper.SOUND_COUNT - 1),
                    ModConfig.clamp(num(p, k + "volume", 70), 0, 100));
            String date = p.getProperty(k + "date", "");
            a.date = date.isEmpty() ? null : LocalDate.parse(date);
            for (String d : p.getProperty(k + "days", "").split(",")) {
                if (!d.isBlank()) {
                    a.days.add(DayOfWeek.valueOf(d.trim()));
                }
            }
            String zone = p.getProperty(k + "zone", "");
            a.zone = ClockZones.isValid(zone) ? zone : ClockZones.SYSTEM;
            a.enabled = Boolean.parseBoolean(p.getProperty(k + "enabled", "true"));
            long snooze = lng(p, k + "snooze", 0);
            a.snoozeUntil = snooze > 0 ? Instant.ofEpochMilli(snooze) : null;
            return a;
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return null;
        }
    }

    Properties write() {
        Properties p = new Properties();
        p.setProperty("clocks.count", String.valueOf(worldClocks.size()));
        for (int i = 0; i < worldClocks.size(); i++) {
            p.setProperty("clock." + i + ".name", worldClocks.get(i).name);
            p.setProperty("clock." + i + ".zone", worldClocks.get(i).zone);
        }
        p.setProperty("timers.count", String.valueOf(timers.size()));
        for (int i = 0; i < timers.size(); i++) {
            p.setProperty("timer." + i + ".name", timers.get(i).name);
            p.setProperty("timer." + i + ".ms", String.valueOf(timers.get(i).durationMs()));
        }
        p.setProperty("alarms.count", String.valueOf(alarms.size()));
        for (int i = 0; i < alarms.size(); i++) {
            Alarm a = alarms.get(i);
            String k = "alarm." + i + ".";
            p.setProperty(k + "name", a.name);
            p.setProperty(k + "time", a.time.toString());
            p.setProperty(k + "date", a.date == null ? "" : a.date.toString());
            StringBuilder days = new StringBuilder();
            for (DayOfWeek d : DayOfWeek.values()) {
                if (a.days.contains(d)) {
                    days.append(days.length() == 0 ? "" : ",").append(d.name());
                }
            }
            p.setProperty(k + "days", days.toString());
            p.setProperty(k + "zone", a.zone);
            p.setProperty(k + "enabled", String.valueOf(a.enabled));
            p.setProperty(k + "sound", String.valueOf(a.sound));
            p.setProperty(k + "volume", String.valueOf(a.volume));
            p.setProperty(k + "snooze", a.snoozeUntil == null ? "" : String.valueOf(a.snoozeUntil.toEpochMilli()));
        }
        return p;
    }

    public boolean save() {
        lastSaveNanos = nanos.getAsLong();
        dirty = false;
        if (file == null) {
            return false;
        }
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                write().store(out, "MultiTool Overlay: clock");
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Записывает несохранённые изменения (закрытие окна, выход из игры). */
    public void flush() {
        if (dirty) {
            save();
        }
    }

    private static String name(String v, String def) {
        if (v == null || v.isBlank()) {
            return def;
        }
        v = v.trim();
        return v.length() > MAX_NAME ? v.substring(0, MAX_NAME) : v;
    }

    private static int num(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long lng(Properties p, String key, long def) {
        try {
            return Long.parseLong(p.getProperty(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
