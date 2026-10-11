package com.multitool.clock;

import com.multitool.audio.Beeper;
import com.multitool.config.ModConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Тесты логики часов без Minecraft: таймер, секундомер, пояса, будильники, сохранение. */
public final class ClockTests {
    private static int failed;
    private static int passed;

    private static void check(String n, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + n);
        }
    }

    private static Instant at(String iso) {
        return Instant.parse(iso);
    }

    public static void main(String[] args) throws Exception {
        timer();
        stopwatch();
        formats();
        zones();
        alarmTimes();
        scheduler();
        service();
        persistence();
        audio();
        System.out.println("Passed: " + passed + ", failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void timer() {
        long[] t = {0};
        CountdownTimer c = new CountdownTimer("a", 5000, () -> t[0]);
        check("timer idle remaining", c.remainingMs() == 5000 && c.state() == CountdownTimer.State.IDLE);
        c.start();
        t[0] = 2_000_000_000L;
        check("timer running 3s left", c.remainingMs() == 3000);
        c.pause();
        t[0] += 10_000_000_000L;
        check("pause keeps value", c.remainingMs() == 3000 && c.state() == CountdownTimer.State.PAUSED);
        check("no finish while paused", !c.tick());
        c.resume();
        t[0] += 2_999_000_000L;
        check("not finished 1ms early", !c.tick() && c.remainingMs() == 1);
        t[0] += 1_000_000L;
        check("finish once", c.tick() && !c.tick());
        check("finished state", c.state() == CountdownTimer.State.FINISHED && c.remainingMs() == 0);
        c.reset();
        check("reset", c.state() == CountdownTimer.State.IDLE && c.remainingMs() == 5000);
        c.start();
        t[0] += 3_600_000_000_000L;
        check("huge frame gap still finishes", c.tick() && c.remainingMs() == 0);
        CountdownTimer d = new CountdownTimer("d", 5000, () -> t[0]);
        d.start();
        t[0] += 4_999_500_000L;
        check("ceil keeps 00:01 until the end", d.remainingMs() == 1);
        check("cannot change running", !d.setDuration(9000));
        d.reset();
        check("clamp low", d.setDuration(0) && d.durationMs() == CountdownTimer.MIN_MS);
        check("clamp high", d.setDuration(Long.MAX_VALUE / 4) && d.durationMs() == CountdownTimer.MAX_MS);
        d.setDuration(10_000);
        d.start();
        t[0] += 5_000_000_000L;
        check("progress half", Math.abs(d.progress() - 0.5) < 0.001);
    }

    private static void stopwatch() {
        long[] t = {0};
        Stopwatch s = new Stopwatch(() -> t[0]);
        check("lap needs running", !s.lap());
        s.start();
        t[0] = 1_500_000_000L;
        check("elapsed", s.elapsedMs() == 1500);
        check("lap1", s.lap() && s.laps().get(0).lapMs() == 1500 && s.laps().get(0).totalMs() == 1500);
        t[0] = 2_500_000_000L;
        s.lap();
        check("lap2 split", s.laps().get(0).number() == 2 && s.laps().get(0).lapMs() == 1000
                && s.laps().get(0).totalMs() == 2500);
        s.pause();
        t[0] = 10_000_000_000L;
        check("paused value", s.elapsedMs() == 2500);
        s.start();
        t[0] = 11_000_000_000L;
        check("resumed", s.elapsedMs() == 3500);
        String copy = s.copyText();
        check("copy text", copy.startsWith("Total 00:03.500") && copy.contains("Lap 1: 00:01.500")
                && copy.indexOf("Lap 1") < copy.indexOf("Lap 2"));
        s.reset();
        check("reset", s.elapsedMs() == 0 && s.laps().isEmpty() && !s.running());
        for (int i = 0; i < 120; i++) {
            s.start();
            t[0] += 1_000_000L;
            s.lap();
        }
        check("laps capped", s.laps().size() == 99);
    }

    private static void formats() {
        ZonedDateTime z = ZonedDateTime.of(2026, 10, 11, 14, 5, 9, 0, ZoneId.of("UTC"));
        check("24h sec", TimeFormats.time(z, true, true).equals("14:05:09"));
        check("24h", TimeFormats.time(z, true, false).equals("14:05"));
        check("12h", TimeFormats.time(z, false, false).equals("2:05 PM"));
        check("12h sec", TimeFormats.time(z, false, true).equals("2:05:09 PM"));
        check("date", TimeFormats.date(z, Locale.ENGLISH).equals("Sun, 11 Oct 2026"));
        check("offset utc", TimeFormats.offset(z).equals("UTC"));
        check("offset msk", TimeFormats.offset(z.withZoneSameInstant(ZoneId.of("Europe/Moscow"))).equals("UTC+03:00"));
        check("countdown", TimeFormats.countdown(3_661_000).equals("1:01:01") && TimeFormats.countdown(61_000).equals("01:01")
                && TimeFormats.countdown(-5).equals("00:00"));
        check("stopwatch", TimeFormats.stopwatch(61_001).equals("01:01.001")
                && TimeFormats.stopwatch(3_661_001).equals("1:01:01.001"));
    }

    private static void zones() {
        check("resolve known", ClockZones.resolve("Europe/Moscow").getId().equals("Europe/Moscow"));
        check("resolve bad -> system", ClockZones.resolve("Mars/Base").equals(ZoneId.systemDefault()));
        check("resolve empty -> system", ClockZones.resolve("").equals(ZoneId.systemDefault()));
        check("valid", ClockZones.isValid("") && ClockZones.isValid("Asia/Tokyo") && !ClockZones.isValid("Mars/Base")
                && !ClockZones.isValid(null));
        boolean allValid = true;
        for (String p : ClockZones.PRESETS) {
            allValid &= ClockZones.isValid(p);
        }
        check("all presets valid", allValid);
        String last = ClockZones.PRESETS[ClockZones.PRESETS.length - 1];
        check("cycle wraps fwd", ClockZones.cycle(last, 1).equals(ClockZones.PRESETS[0]));
        check("cycle wraps back", ClockZones.cycle("", -1).equals(last));
        check("default names", ClockZones.defaultName("America/New_York").equals("New York")
                && ClockZones.defaultName("UTC").equals("UTC") && ClockZones.defaultName("").equals("Local"));
        ZoneId ny = ZoneId.of("America/New_York");
        ZoneId ln = ZoneId.of("Europe/London");
        check("NY winter", TimeFormats.offset(ZonedDateTime.of(2026, 1, 15, 12, 0, 0, 0, ny)).equals("UTC-05:00"));
        check("NY summer", TimeFormats.offset(ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, ny)).equals("UTC-04:00"));
        check("London winter", TimeFormats.offset(ZonedDateTime.of(2026, 1, 15, 12, 0, 0, 0, ln)).equals("UTC"));
        check("London summer", TimeFormats.offset(ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, ln)).equals("UTC+01:00"));
    }

    private static Alarm alarm(String time, String zone) {
        Alarm a = new Alarm("t", LocalTime.parse(time), 0, 70);
        a.zone = zone;
        return a;
    }

    private static void alarmTimes() {
        Alarm a = alarm("07:30", "Europe/Moscow");
        check("once: later today", a.nextFire(at("2026-10-11T04:00:00Z")).equals(at("2026-10-11T04:30:00Z")));
        check("once: tomorrow", a.nextFire(at("2026-10-11T05:00:00Z")).equals(at("2026-10-12T04:30:00Z")));
        check("strictly after", a.nextFire(at("2026-10-11T04:30:00Z")).equals(at("2026-10-12T04:30:00Z")));
        a.date = LocalDate.of(2026, 10, 11);
        check("dated", a.nextFire(at("2026-10-11T04:00:00Z")).equals(at("2026-10-11T04:30:00Z")));
        check("dated passed", a.nextFire(at("2026-10-11T05:00:00Z")) == null);
        a.date = null;
        a.days.add(DayOfWeek.MONDAY);
        a.days.add(DayOfWeek.WEDNESDAY);
        check("weekly: next Monday", a.nextFire(at("2026-10-11T10:00:00Z")).equals(at("2026-10-12T04:30:00Z")));
        check("weekly: then Wednesday", a.nextFire(at("2026-10-12T04:30:00Z")).equals(at("2026-10-14T04:30:00Z")));
        check("weekly: wraps week", a.nextFire(at("2026-10-14T04:30:00Z")).equals(at("2026-10-19T04:30:00Z")));
        // переход на летнее время (США: 8 марта 2026 в 02:00 -> 03:00, 1 ноября в 02:00 -> 01:00)
        Alarm ny = alarm("02:30", "America/New_York");
        ny.date = LocalDate.of(2026, 3, 8);
        check("DST gap shifts forward", ny.nextFire(at("2026-03-08T00:00:00Z")).equals(at("2026-03-08T07:30:00Z")));
        Alarm fb = alarm("01:30", "America/New_York");
        fb.date = LocalDate.of(2026, 11, 1);
        check("DST overlap fires once, first", fb.nextFire(at("2026-11-01T00:00:00Z")).equals(at("2026-11-01T05:30:00Z")));
        Alarm daily = alarm("09:00", "America/New_York");
        for (DayOfWeek d : DayOfWeek.values()) {
            daily.days.add(d);
        }
        // 9:00 по Нью-Йорку до и после перехода: 14:00Z зимой и 13:00Z летом
        check("daily across DST winter", daily.nextFire(at("2026-03-07T00:00:00Z")).equals(at("2026-03-07T14:00:00Z")));
        check("daily across DST summer", daily.nextFire(at("2026-03-08T00:00:00Z")).equals(at("2026-03-08T13:00:00Z")));
    }

    private static void scheduler() {
        // однократный
        List<Alarm> list = new ArrayList<>();
        Alarm a = alarm("07:30", "Europe/Moscow");
        list.add(a);
        AlarmScheduler s = new AlarmScheduler(list, at("2026-10-11T04:29:59.500Z"));
        check("fires once", s.tick(at("2026-10-11T04:30:00.100Z")).size() == 1);
        check("once disables itself", !a.enabled);
        check("no refire", s.tick(at("2026-10-11T04:30:01Z")).isEmpty());

        // повторяющийся: раз в сутки
        Alarm d = alarm("07:30", "Europe/Moscow");
        for (DayOfWeek w : DayOfWeek.values()) {
            d.days.add(w);
        }
        List<Alarm> l2 = new ArrayList<>(List.of(d));
        AlarmScheduler s2 = new AlarmScheduler(l2, at("2026-10-11T04:29:00Z"));
        int fired = 0;
        for (Instant n = at("2026-10-11T04:29:00Z"); n.isBefore(at("2026-10-13T00:00:00Z")); n = n.plusMillis(50_000)) {
            fired += s2.tick(n).size();
        }
        check("daily fires on 11th and 12th", fired == 2);
        check("daily stays enabled", d.enabled);

        // длинная пауза: сигнал в прошлом не воспроизводится задним числом
        Alarm j = alarm("07:30", "Europe/Moscow");
        AlarmScheduler s3 = new AlarmScheduler(new ArrayList<>(List.of(j)), at("2026-10-11T03:00:00Z"));
        check("long gap skips old alarm", s3.tick(at("2026-10-11T10:00:00Z")).isEmpty());
        // небольшая задержка (до минуты) всё же срабатывает
        Alarm k = alarm("07:30", "Europe/Moscow");
        AlarmScheduler s4 = new AlarmScheduler(new ArrayList<>(List.of(k)), at("2026-10-11T04:29:00Z"));
        check("slightly late still fires", s4.tick(at("2026-10-11T04:30:30Z")).size() == 1);

        // системное время переведено назад
        Alarm m = alarm("07:30", "Europe/Moscow");
        AlarmScheduler s5 = new AlarmScheduler(new ArrayList<>(List.of(m)), at("2026-10-11T10:00:00Z"));
        check("clock moved back: nothing", s5.tick(at("2026-10-11T04:00:00Z")).isEmpty());
        check("clock moved back: still fires later", s5.tick(at("2026-10-11T04:30:00.500Z")).size() == 1);

        // отложенный сигнал
        Alarm z = alarm("07:30", "Europe/Moscow");
        z.enabled = false;
        z.snoozeUntil = at("2026-10-11T05:00:00Z");
        AlarmScheduler s6 = new AlarmScheduler(new ArrayList<>(List.of(z)), at("2026-10-11T04:59:00Z"));
        check("snooze not yet", s6.tick(at("2026-10-11T04:59:59Z")).isEmpty());
        check("snooze fires", s6.tick(at("2026-10-11T05:00:00.100Z")).size() == 1 && z.snoozeUntil == null);

        // выключенный не срабатывает
        Alarm off = alarm("07:30", "Europe/Moscow");
        off.enabled = false;
        AlarmScheduler s7 = new AlarmScheduler(new ArrayList<>(List.of(off)), at("2026-10-11T04:29:59Z"));
        check("disabled silent", s7.tick(at("2026-10-11T04:30:01Z")).isEmpty());

        // пропущенные, пока игра была закрыта
        Alarm past = alarm("07:30", "Europe/Moscow");
        past.date = LocalDate.of(2026, 10, 10);
        Alarm future = alarm("07:30", "Europe/Moscow");
        future.date = LocalDate.of(2026, 10, 20);
        Alarm sn = alarm("07:30", "Europe/Moscow");
        sn.snoozeUntil = at("2026-10-11T01:00:00Z");
        AlarmScheduler s8 = new AlarmScheduler(new ArrayList<>(List.of(past, future, sn)), Instant.EPOCH);
        s8.expireMissed(at("2026-10-11T12:00:00Z"));
        check("missed dated disabled", !past.enabled);
        check("future dated kept", future.enabled);
        check("stale snooze cleared", sn.snoozeUntil == null);
    }

    private static void service() {
        long[] n = {0};
        Instant[] now = {at("2026-10-11T04:29:00Z")};
        List<int[]> played = new ArrayList<>();
        ModConfig cfg = ModConfig.create();
        cfg.toastSeconds = 5;
        ClockService svc = new ClockService(() -> n[0], () -> now[0], cfg, (s, v) -> played.add(new int[]{s, v}));

        Alarm a = svc.newAlarm();
        a.time = LocalTime.of(7, 30);
        a.zone = "Europe/Moscow";
        a.sound = 2;
        a.volume = 40;
        svc.tick();
        check("no notice before time", svc.notices.isEmpty());
        now[0] = at("2026-10-11T04:30:00.100Z");
        n[0] += 1_000_000_000L;
        svc.tick();
        check("alarm notice", svc.notices.size() == 1 && svc.notices.get(0).isAlarm() && svc.anyAlarmRinging());
        check("alarm sound played with its own volume", played.size() == 1 && played.get(0)[0] == 2 && played.get(0)[1] == 40);
        n[0] += 500_000_000L;
        svc.tick();
        check("no immediate repeat", played.size() == 1);
        n[0] += 5_000_000_000L;
        svc.tick();
        check("repeats while ringing", played.size() == 2);
        ClockService.Notice notice = svc.notices.get(0);
        svc.snooze(notice, 5);
        check("snooze removes notice and sets time", svc.notices.isEmpty()
                && a.snoozeUntil.equals(at("2026-10-11T04:35:00.100Z")));
        now[0] = at("2026-10-11T04:35:00.200Z");
        svc.tick();
        check("rings again after snooze", svc.anyAlarmRinging());
        svc.dismiss(svc.notices.get(0));
        check("dismiss", svc.notices.isEmpty() && !a.enabled);

        // таймер
        played.clear();
        CountdownTimer t = svc.addTimer("tea", 3000, true);
        n[0] += 2_900_000_000L;
        svc.tick();
        check("timer not yet", svc.notices.isEmpty());
        n[0] += 200_000_000L;
        svc.tick();
        check("timer notice", svc.notices.size() == 1 && !svc.notices.get(0).isAlarm()
                && svc.notices.get(0).text.startsWith("tea") && played.size() == 1);
        n[0] += 6_000_000_000L;
        svc.tick();
        check("timer popup expires, alarm would not", svc.notices.isEmpty());
        check("timer limits", addMany(svc) && svc.addTimer("x", 1000, false) == null);
        svc.removeTimer(t);
        check("remove timer", !svc.timers.contains(t));

        // мировые часы
        check("world clock add/remove", svc.addWorldClock() && svc.worldClocks.size() == 2);
        svc.cycleWorldClockZone(1, 1);
        check("zone cycle renames default names", svc.worldClocks.get(1).name.equals(ClockZones.defaultName(svc.worldClocks.get(1).zone)));
        svc.removeWorldClock(1);
        svc.removeWorldClock(0);
        check("keeps at least one clock", svc.worldClocks.size() == 1);
    }

    private static boolean addMany(ClockService svc) {
        while (svc.timers.size() < ClockService.MAX_TIMERS) {
            svc.addTimer("x", 1000, false);
        }
        return true;
    }

    private static void persistence() throws Exception {
        Path dir = Files.createTempDirectory("mtclock");
        Path f = dir.resolve("sub").resolve("clock.properties");
        long[] n = {0};
        Instant[] now = {at("2026-10-11T04:00:00Z")};
        ModConfig cfg = ModConfig.create();
        ClockService a = new ClockService(() -> n[0], () -> now[0], cfg, (s, v) -> {
        });
        a.setFile(f);
        a.worldClocks.get(0).name = "Home";
        a.addWorldClock();
        a.worldClocks.get(1).zone = "Asia/Tokyo";
        a.worldClocks.get(1).name = "Tokyo";
        a.addTimer("Eggs", 600_000, false);
        Alarm al = a.newAlarm();
        al.name = "Work";
        al.time = LocalTime.of(6, 45);
        al.days.add(DayOfWeek.MONDAY);
        al.days.add(DayOfWeek.FRIDAY);
        al.zone = "Europe/Berlin";
        al.sound = 1;
        al.volume = 30;
        al.snoozeUntil = at("2026-10-11T08:00:00Z");
        Alarm once = a.newAlarm();
        once.date = LocalDate.of(2026, 10, 20);
        check("save ok", a.save() && Files.isRegularFile(f));

        ClockService b = new ClockService(() -> n[0], () -> now[0], cfg, (s, v) -> {
        });
        b.setFile(f);
        b.load();
        check("clocks restored", b.worldClocks.size() == 2 && b.worldClocks.get(0).name.equals("Home")
                && b.worldClocks.get(1).zone.equals("Asia/Tokyo"));
        check("timers restored", b.timers.size() == 1 && b.timers.get(0).name.equals("Eggs")
                && b.timers.get(0).durationMs() == 600_000);
        Alarm r = b.alarms.get(0);
        check("alarm restored", r.name.equals("Work") && r.time.equals(LocalTime.of(6, 45))
                && r.days.equals(al.days) && r.zone.equals("Europe/Berlin") && r.sound == 1 && r.volume == 30);
        check("snooze restored", r.snoozeUntil != null && r.snoozeUntil.equals(at("2026-10-11T08:00:00Z")));
        check("dated alarm restored", b.alarms.get(1).date.equals(LocalDate.of(2026, 10, 20)) && b.alarms.get(1).enabled);

        // перезапуск через неделю: однократный будильник с датой пропущен, повторяющийся жив, старая отсрочка сброшена
        now[0] = at("2026-10-27T04:00:00Z");
        ClockService c = new ClockService(() -> n[0], () -> now[0], cfg, (s, v) -> {
        });
        c.setFile(f);
        c.load();
        check("missed once-alarm disabled on load", !c.alarms.get(1).enabled);
        check("weekly alarm survives", c.alarms.get(0).enabled && c.alarms.get(0).snoozeUntil == null);

        // повреждённый файл
        Files.writeString(f, "alarms.count=3\nalarm.0.time=garbage\nalarm.1.time=08:15\nalarm.1.days=FUNDAY\n"
                + "alarm.2.time=09:00\nalarm.2.name=Ok\nalarm.2.zone=Nope/Zone\nclocks.count=x\ntimers.count=2\ntimer.0.ms=-5\n");
        ClockService d = new ClockService(() -> n[0], () -> now[0], cfg, (s, v) -> {
        });
        d.setFile(f);
        d.load();
        check("bad alarms skipped, good kept", d.alarms.size() == 1 && d.alarms.get(0).name.equals("Ok")
                && d.alarms.get(0).zone.equals(ClockZones.SYSTEM));
        check("bad timers skipped", d.timers.isEmpty());
        check("defaults kept for bad clocks", d.worldClocks.size() == 1);
        Files.write(f, new byte[]{0, 1, 2, (byte) 0xFF, '\\', 'u', 'z'});
        ClockService e = new ClockService(() -> n[0], () -> now[0], cfg, (s, v) -> {
        });
        e.setFile(f);
        e.load();
        check("binary garbage does not crash", e.worldClocks.size() == 1);
    }

    private static void audio() {
        byte[] p = Beeper.pcm(0, 70);
        check("pcm length bell", p.length == 44100 * 2);
        check("duration beep", Beeper.durationMs(1) == 570);
        byte[] silent = Beeper.pcm(1, 0);
        boolean allZero = true;
        for (byte b : silent) {
            allZero &= b == 0;
        }
        check("volume 0 is silent", allZero);
        int max = 0;
        for (int i = 0; i + 1 < p.length; i += 2) {
            max = Math.max(max, Math.abs((short) ((p[i] & 0xFF) | (p[i + 1] << 8))));
        }
        check("signal present and within gain", max > 3000 && max < 0.6 * 32767 + 1);
        check("louder is louder", maxAmp(Beeper.pcm(2, 100)) > maxAmp(Beeper.pcm(2, 30)));
        check("out of range sound clamps", Beeper.pcm(99, 50).length == Beeper.pcm(Beeper.SOUND_COUNT - 1, 50).length);
    }

    private static int maxAmp(byte[] p) {
        int max = 0;
        for (int i = 0; i + 1 < p.length; i += 2) {
            max = Math.max(max, Math.abs((short) ((p[i] & 0xFF) | (p[i + 1] << 8))));
        }
        return max;
    }
}
