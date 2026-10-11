package com.multitool.clock;

import com.multitool.config.ModConfig;
import com.multitool.overlay.Gfx;
import com.multitool.overlay.HitList;
import com.multitool.overlay.OverlayWindow;
import com.multitool.overlay.Ui;
import com.multitool.audio.Beeper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Окно Clock: часы, таймеры, секундомер и будильники. */
public final class ClockWindow extends OverlayWindow {
    public static final String ID = "clock";

    private enum Tab { CLOCK, TIMER, STOPWATCH, ALARM }

    private static final String[] TAB_NAMES = {"Clock", "Timer", "Stopwatch", "Alarm"};
    private static final int TAB_H = 16;
    private static final int LINE = 12;
    private static final int CLOCK_ROW = 42;
    private static final int TIMER_ROW = 40;
    private static final int ALARM_ROW = 22;
    private static final int ALARM_VISIBLE = 4;
    private static final long[] QUICK_MS = {30_000, 60_000, 300_000, 600_000, 1_800_000, 3_600_000};
    private static final String[] QUICK_LABELS = {"30s", "1m", "5m", "10m", "30m", "1h"};

    private final ClockService svc;
    private final ModConfig cfg;
    private final HitList hits = new HitList();

    private Tab tab = Tab.CLOCK;
    private int selAlarm = -1;
    private int alarmOffset;
    private String timerName = "";

    private String editKey;
    private StringBuilder editBuf = new StringBuilder();
    private Consumer<String> editSink;
    private long copiedAt;

    public ClockWindow(ClockService svc) {
        super(ID, "Clock");
        this.svc = svc;
        this.cfg = svc.config();
        this.x = 300;
        this.y = 40;
        this.w = 280;
        this.h = 330;
        this.minW = 220;
        this.minH = 150;
    }

    @Override
    protected int compactContentHeight() {
        return LINE * 2;
    }

    // ------------------------------------------------------------------ отрисовка

    @Override
    protected void renderContent(DrawContext c, TextRenderer tr, int mx, int my, boolean interactive) {
        hits.clear();
        Ui ui = new Ui(c, tr, mx, my, interactive, hits);
        if (compact) {
            renderCompact(ui);
            return;
        }
        int tw = (cw - 9) / 4;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            Tab t = Tab.values()[i];
            ui.button(cx + i * (tw + 3), cy, tw, TAB_H - 2, TAB_NAMES[i], tab == t, () -> {
                tab = t;
                editKey = null;
            });
        }
        int top = cy + TAB_H + 4;
        switch (tab) {
            case CLOCK -> renderClocks(ui, top);
            case TIMER -> renderTimers(ui, top);
            case STOPWATCH -> renderStopwatch(ui, top);
            case ALARM -> renderAlarms(ui, top);
        }
    }

    private void renderCompact(Ui ui) {
        WorldClock w0 = svc.worldClocks.get(0);
        ZonedDateTime t = ZonedDateTime.now(ClockZones.resolve(w0.zone));
        ui.text(TimeFormats.time(t, cfg.clock24h, cfg.showSeconds), cx + 2, cy + 1, Gfx.TEXT);
        if (cfg.showDate) {
            ui.right(TimeFormats.date(t, Locale.getDefault()), cx + cw - 2, cy + 1, Gfx.DIM);
        }
        StringBuilder st = new StringBuilder();
        for (CountdownTimer ct : svc.timers) {
            if (ct.state() == CountdownTimer.State.RUNNING || ct.state() == CountdownTimer.State.PAUSED) {
                st.append("T ").append(TimeFormats.countdown(ct.remainingMs())).append("  ");
                break;
            }
        }
        if (svc.stopwatch.running() || svc.stopwatch.elapsedMs() > 0) {
            st.append("SW ").append(TimeFormats.stopwatch(svc.stopwatch.elapsedMs()));
        }
        boolean ringing = svc.anyAlarmRinging();
        String line = ringing ? "ALARM!" : st.toString().isBlank() ? w0.name : st.toString().trim();
        ui.text(Gfx.fit(ui.tr, line, cw - 4), cx + 2, cy + 1 + LINE, ringing ? Gfx.ERROR : Gfx.DIM);
    }

    // ---- вкладка Clock ----

    private void renderClocks(Ui ui, int top) {
        int bottom = cy + ch;
        int toggleY = bottom - 14;
        int addY = toggleY - 18;
        int y = top;
        List<WorldClock> list = svc.worldClocks;
        for (int i = 0; i < list.size(); i++) {
            if (y + CLOCK_ROW > addY) {
                break;
            }
            WorldClock wc = list.get(i);
            final int idx = i;
            ZonedDateTime t = ZonedDateTime.now(ClockZones.resolve(wc.zone));
            Gfx.rrect(ui.c, cx, y, cx + cw, y + CLOCK_ROW - 3, 4, Gfx.DISPLAY);
            nameField(ui, "wc" + i, cx + 3, y + 3, cw / 2 - 6, 12, wc.name, v -> {
                wc.name = v;
                svc.markDirty();
            });
            ui.right(TimeFormats.time(t, cfg.clock24h, cfg.showSeconds), cx + cw - 5, y + 5, Gfx.TEXT);
            if (cfg.showDate) {
                ui.text(TimeFormats.date(t, Locale.getDefault()), cx + 5, y + 18, Gfx.DIM);
            }
            int by = y + 26;
            int bx = cx + cw - 3 * 14 - 5;
            ui.button(bx, by, 12, 11, "<", false, () -> svc.cycleWorldClockZone(idx, -1));
            ui.button(bx + 14, by, 12, 11, ">", false, () -> svc.cycleWorldClockZone(idx, 1));
            ui.button(bx + 28, by, 12, 11, "x", false, () -> svc.removeWorldClock(idx));
            String zl = TimeFormats.offset(t) + " " + ClockZones.label(wc.zone);
            ui.text(Gfx.fit(ui.tr, zl, bx - cx - 10), cx + 5, y + 28, Gfx.DIM);
            y += CLOCK_ROW;
        }
        if (list.size() * CLOCK_ROW > addY - top) {
            ui.text("Enlarge the window", cx + 2, addY + 3, Gfx.DIM);
        }
        if (list.size() < ClockService.MAX_CLOCKS) {
            ui.button(cx + cw - 80, addY, 80, 14, "+ Add clock", false, svc::addWorldClock);
        }
        int bw = (cw - 6) / 3;
        ui.button(cx, toggleY, bw, 14, "24h", cfg.clock24h, () -> {
            cfg.clock24h = !cfg.clock24h;
            cfg.changed();
        });
        ui.button(cx + bw + 3, toggleY, bw, 14, "Seconds", cfg.showSeconds, () -> {
            cfg.showSeconds = !cfg.showSeconds;
            cfg.changed();
        });
        ui.button(cx + 2 * (bw + 3), toggleY, bw, 14, "Date", cfg.showDate, () -> {
            cfg.showDate = !cfg.showDate;
            cfg.changed();
        });
    }

    // ---- вкладка Timer ----

    private void renderTimers(Ui ui, int top) {
        int y = top;
        ui.text("New timer", cx + 2, y, Gfx.DIM);
        ui.right("RMB: bigger step", cx + cw - 2, y, Gfx.DIM);
        y += LINE;
        int sw = (cw - 8) / 3;
        stepper(ui, cx, y, sw, String.format(Locale.ROOT, "%02d h", svc.editH), 1, 5, d -> svc.editH = clampi(svc.editH + d, 0, 99));
        stepper(ui, cx + sw + 4, y, sw, String.format(Locale.ROOT, "%02d m", svc.editM), 1, 5, d -> svc.editM = wrap(svc.editM + d, 60));
        stepper(ui, cx + 2 * (sw + 4), y, sw, String.format(Locale.ROOT, "%02d s", svc.editS), 1, 5, d -> svc.editS = wrap(svc.editS + d, 60));
        y += 18;
        int qw = (cw - 5 * 3) / 6;
        for (int i = 0; i < QUICK_MS.length; i++) {
            final long ms = QUICK_MS[i];
            ui.button(cx + i * (qw + 3), y, qw, 14, QUICK_LABELS[i], false, () -> {
                svc.editH = (int) (ms / 3_600_000L);
                svc.editM = (int) ((ms / 60_000L) % 60);
                svc.editS = (int) ((ms / 1000L) % 60);
            });
        }
        y += 18;
        String ph = timerName.isEmpty() && !"tn".equals(editKey) ? "Name (optional)" : timerName;
        boolean ed = "tn".equals(editKey);
        ui.field(cx, y, cw - 64, 14, ed ? editBuf.toString() : ph, ed, () -> startEdit("tn", timerName, v -> timerName = v));
        ui.button(cx + cw - 60, y, 60, 14, "Start", true, () -> {
            if (svc.editorMs() >= CountdownTimer.MIN_MS && svc.addTimer(timerName, svc.editorMs(), true) != null) {
                timerName = "";
                editKey = null;
            }
        });
        y += 20;
        int bottom = cy + ch;
        List<CountdownTimer> list = svc.timers;
        int shown = 0;
        for (int i = 0; i < list.size(); i++) {
            if (y + TIMER_ROW > bottom) {
                break;
            }
            renderTimerRow(ui, list.get(i), i, y);
            y += TIMER_ROW;
            shown++;
        }
        if (list.isEmpty()) {
            ui.text("No timers yet. Pick a time and press Start.", cx + 2, y + 2, Gfx.DIM);
        } else if (shown < list.size()) {
            ui.text("+" + (list.size() - shown) + " more (enlarge the window)", cx + 2, Math.min(y, bottom - 10), Gfx.DIM);
        }
    }

    private void renderTimerRow(Ui ui, CountdownTimer t, int i, int y) {
        Gfx.rrect(ui.c, cx, y, cx + cw, y + TIMER_ROW - 3, 4, Gfx.DISPLAY);
        nameField(ui, "tm" + i, cx + 3, y + 3, cw / 2 - 6, 12, t.name, v -> {
            t.name = v;
            svc.markDirty();
        });
        CountdownTimer.State st = t.state();
        int col = st == CountdownTimer.State.FINISHED ? Gfx.ERROR : st == CountdownTimer.State.PAUSED ? Gfx.DIM : Gfx.TEXT;
        ui.right(TimeFormats.countdown(t.remainingMs()), cx + cw - 5, y + 5, col);
        ui.bar(cx + 4, y + 18, cw - 8, 4, t.progress(), st == CountdownTimer.State.FINISHED ? Gfx.ERROR : Gfx.ACCENT);
        int by = y + 24;
        String main = st == CountdownTimer.State.RUNNING ? "Pause" : st == CountdownTimer.State.PAUSED ? "Resume" : "Start";
        ui.button(cx + 4, by, 56, 11, main, st != CountdownTimer.State.RUNNING, () -> {
            if (st == CountdownTimer.State.RUNNING) {
                t.pause();
            } else if (st == CountdownTimer.State.PAUSED) {
                t.resume();
            } else {
                t.start();
            }
        });
        ui.button(cx + 64, by, 44, 11, "Reset", false, t::reset);
        ui.button(cx + cw - 22, by, 18, 11, "x", false, () -> svc.removeTimer(t));
    }

    // ---- вкладка Stopwatch ----

    private void renderStopwatch(Ui ui, int top) {
        Stopwatch sw = svc.stopwatch;
        int y = top;
        Gfx.rrect(ui.c, cx, y, cx + cw, y + 32, 4, Gfx.DISPLAY);
        ui.center(TimeFormats.stopwatch(sw.elapsedMs()), cx + cw / 2, y + 12, Gfx.TEXT);
        if (System.currentTimeMillis() - copiedAt < 900) {
            ui.right("Copied", cx + cw - 4, y + 3, Gfx.OK);
        } else if (sw.running()) {
            ui.text("running", cx + 4, y + 3, Gfx.DIM);
        }
        y += 36;
        int bw = (cw - 9) / 4;
        ui.button(cx, y, bw, 14, sw.running() ? "Pause" : sw.elapsedMs() > 0 ? "Resume" : "Start", !sw.running(), sw::toggle);
        ui.button(cx + bw + 3, y, bw, 14, "Lap", false, sw::lap);
        ui.button(cx + 2 * (bw + 3), y, bw, 14, "Reset", false, sw::reset);
        ui.button(cx + 3 * (bw + 3), y, bw, 14, "Copy", false, () -> {
            if (sw.elapsedMs() > 0) {
                MinecraftClient.getInstance().keyboard.setClipboard(sw.copyText());
                copiedAt = System.currentTimeMillis();
            }
        });
        y += 20;
        List<Stopwatch.Lap> laps = sw.laps();
        if (laps.isEmpty()) {
            ui.text("Laps appear here", cx + 2, y, Gfx.DIM);
            return;
        }
        ui.text("Lap", cx + 2, y, Gfx.DIM);
        ui.text("Split", cx + 36, y, Gfx.DIM);
        ui.right("Total", cx + cw - 2, y, Gfx.DIM);
        y += LINE;
        int bottom = cy + ch;
        for (Stopwatch.Lap l : laps) {
            if (y + 10 > bottom) {
                break;
            }
            ui.text("#" + l.number(), cx + 2, y, Gfx.TEXT);
            ui.text(TimeFormats.stopwatch(l.lapMs()), cx + 36, y, Gfx.TEXT);
            ui.right(TimeFormats.stopwatch(l.totalMs()), cx + cw - 2, y, Gfx.DIM);
            y += 11;
        }
    }

    // ---- вкладка Alarm ----

    private void renderAlarms(Ui ui, int top) {
        List<Alarm> list = svc.alarms;
        if (selAlarm >= list.size()) {
            selAlarm = list.size() - 1;
        }
        int y = top;
        ui.text("Alarms (" + list.size() + ")", cx + 2, y + 2, Gfx.DIM);
        ui.button(cx + cw - 80, y, 80, 14, "+ Add alarm", false, () -> {
            Alarm a = svc.newAlarm();
            if (a != null) {
                selAlarm = svc.alarms.size() - 1;
                alarmOffset = Math.max(0, svc.alarms.size() - ALARM_VISIBLE);
            }
        });
        if (list.size() > ALARM_VISIBLE) {
            ui.button(cx + cw - 110, y, 13, 14, "^", false, () -> alarmOffset = Math.max(0, alarmOffset - 1));
            ui.button(cx + cw - 95, y, 13, 14, "v", false,
                    () -> alarmOffset = Math.min(svc.alarms.size() - ALARM_VISIBLE, alarmOffset + 1));
        }
        y += 18;
        alarmOffset = clampi(alarmOffset, 0, Math.max(0, list.size() - ALARM_VISIBLE));
        if (list.isEmpty()) {
            ui.text("No alarms. Press \"+ Add alarm\".", cx + 2, y + 2, Gfx.DIM);
            return;
        }
        for (int i = alarmOffset; i < Math.min(list.size(), alarmOffset + ALARM_VISIBLE); i++) {
            renderAlarmRow(ui, list.get(i), i, y);
            y += ALARM_ROW;
        }
        y += 4;
        if (selAlarm >= 0 && selAlarm < list.size()) {
            renderAlarmEditor(ui, list.get(selAlarm), y);
        } else {
            ui.text("Select an alarm to edit it", cx + 2, y + 2, Gfx.DIM);
        }
    }

    private void renderAlarmRow(Ui ui, Alarm a, int i, int y) {
        boolean sel = i == selAlarm;
        int h = ALARM_ROW - 2;
        Gfx.rrect(ui.c, cx, y, cx + cw, y + h, 4, sel ? Gfx.BTN : Gfx.DISPLAY);
        if (sel) {
            Gfx.rrect(ui.c, cx, y, cx + 3, y + h, 1, Gfx.ACCENT);
        }
        ui.hits.add(cx, y, cw, h, b -> {
            if (b == 0) {
                selAlarm = i;
                editKey = null;
            }
        });
        ui.button(cx + 6, y + 3, 26, 14, a.enabled ? "On" : "Off", a.enabled, () -> {
            a.enabled = !a.enabled;
            if (!a.enabled) {
                a.snoozeUntil = null;
            }
            svc.markDirty();
        });
        ui.text(TimeFormats.hm(a.time), cx + 38, y + 6, a.enabled ? Gfx.TEXT : Gfx.DIM);
        String sum = daysSummary(a);
        int sumW = ui.tr.getWidth(sum);
        ui.right(sum, cx + cw - 5, y + 6, Gfx.DIM);
        ui.text(Gfx.fit(ui.tr, a.name, cw - 38 - 36 - sumW - 14), cx + 76, y + 6, a.enabled ? Gfx.TEXT : Gfx.DIM);
    }

    private void renderAlarmEditor(Ui ui, Alarm a, int top) {
        int y = top;
        // время и название
        stepper(ui, cx, y, 56, String.format(Locale.ROOT, "%02d", a.time.getHour()), 1, 3, d -> {
            a.time = a.time.plusHours(d);
            svc.markDirty();
        });
        ui.text(":", cx + 59, y + 3, Gfx.DIM);
        stepper(ui, cx + 66, y, 56, String.format(Locale.ROOT, "%02d", a.time.getMinute()), 1, 5, d -> {
            a.time = a.time.withMinute(wrap(a.time.getMinute() + d, 60));
            svc.markDirty();
        });
        nameField(ui, "al", cx + 128, y, cw - 128, 14, a.name, v -> {
            a.name = v;
            svc.markDirty();
        });
        y += 20;
        // дни недели
        int dw = (cw - 6 * 2) / 7;
        for (int i = 0; i < 7; i++) {
            DayOfWeek d = DayOfWeek.of(i + 1);
            ui.button(cx + i * (dw + 2), y, dw, 14, d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    a.days.contains(d), () -> {
                        if (!a.days.remove(d)) {
                            a.days.add(d);
                        }
                        svc.markDirty();
                    });
        }
        y += 20;
        // дата
        ui.text("Date", cx + 2, y + 3, Gfx.DIM);
        ui.button(cx + 34, y, 14, 14, "<", false, b -> shiftDate(a, b == 0 ? -1 : -7));
        String dt = a.repeating() ? "repeats on selected days" : a.date == null ? "next occurrence" : a.date.toString();
        ui.center(Gfx.fit(ui.tr, dt, cw - 34 - 14 - 14 - 22 - 8), cx + 34 + 14 + (cw - 34 - 14 - 14 - 22) / 2, y + 3,
                a.repeating() ? Gfx.DIM : Gfx.TEXT);
        ui.button(cx + cw - 36, y, 14, 14, ">", false, b -> shiftDate(a, b == 0 ? 1 : 7));
        ui.button(cx + cw - 20, y, 20, 14, "x", false, () -> {
            a.date = null;
            svc.markDirty();
        });
        y += 20;
        // пояс
        ui.text("Zone", cx + 2, y + 3, Gfx.DIM);
        ui.button(cx + 34, y, 14, 14, "<", false, () -> cycleZone(a, -1));
        ui.center(Gfx.fit(ui.tr, ClockZones.label(a.zone), cw - 34 - 28 - 8), cx + 34 + 14 + (cw - 34 - 28) / 2, y + 3, Gfx.TEXT);
        ui.button(cx + cw - 14, y, 14, 14, ">", false, () -> cycleZone(a, 1));
        y += 20;
        // звук
        ui.text("Sound", cx + 2, y + 3, Gfx.DIM);
        ui.button(cx + 34, y, 14, 14, "<", false, () -> cycleSound(a, -1));
        ui.center(ModConfig.SOUND_NAMES[a.sound], cx + 34 + 14 + 24, y + 3, Gfx.TEXT);
        ui.button(cx + 34 + 14 + 48, y, 14, 14, ">", false, () -> cycleSound(a, 1));
        ui.button(cx + cw - 50, y, 50, 14, "Test", false, () -> Beeper.play(a.sound, a.volume));
        y += 20;
        // громкость и удаление
        ui.text("Volume", cx + 2, y + 3, Gfx.DIM);
        stepper(ui, cx + 40, y, 64, a.volume + "%", 10, 10, d -> {
            a.volume = ModConfig.clamp(a.volume + d, 0, 100);
            svc.markDirty();
        });
        ui.button(cx + cw - 50, y, 50, 14, "Delete", false, () -> {
            svc.removeAlarm(a);
            selAlarm = Math.min(selAlarm, svc.alarms.size() - 1);
            editKey = null;
        });
    }

    // ------------------------------------------------------------------ вспомогательное

    private void nameField(Ui ui, String key, int x, int y, int w, int h, String value, Consumer<String> sink) {
        boolean ed = key.equals(editKey);
        ui.field(x, y, w, h, ed ? editBuf.toString() : value, ed, () -> startEdit(key, value, sink));
    }

    private void stepper(Ui ui, int x, int y, int w, String text, int small, int big, IntConsumer delta) {
        ui.button(x, y, 14, 14, "-", false, b -> delta.accept(-(b == 0 ? small : big)));
        ui.button(x + w - 14, y, 14, 14, "+", false, b -> delta.accept(b == 0 ? small : big));
        ui.center(text, x + w / 2, y + 3, Gfx.TEXT);
    }

    private void startEdit(String key, String current, Consumer<String> sink) {
        if (key.equals(editKey)) {
            return;
        }
        editKey = key;
        editBuf = new StringBuilder(current);
        editSink = sink;
    }

    private void shiftDate(Alarm a, int days) {
        if (a.date == null) {
            a.date = LocalDate.now(a.zoneId());
        } else {
            a.date = a.date.plusDays(days);
        }
        svc.markDirty();
    }

    private void cycleZone(Alarm a, int dir) {
        a.zone = ClockZones.cycle(a.zone, dir);
        svc.markDirty();
    }

    private void cycleSound(Alarm a, int dir) {
        int n = ModConfig.SOUND_NAMES.length;
        a.sound = ((a.sound + dir) % n + n) % n;
        svc.markDirty();
        Beeper.play(a.sound, a.volume);
    }

    static String daysSummary(Alarm a) {
        if (a.days.isEmpty()) {
            return a.date == null ? "Once" : a.date.toString().substring(5);
        }
        if (a.days.size() == 7) {
            return "Daily";
        }
        StringBuilder sb = new StringBuilder();
        for (DayOfWeek d : DayOfWeek.values()) {
            if (a.days.contains(d)) {
                sb.append(sb.length() == 0 ? "" : " ").append(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), 0, 2);
            }
        }
        return sb.toString();
    }

    private static int clampi(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int wrap(int v, int mod) {
        return ((v % mod) + mod) % mod;
    }

    // ------------------------------------------------------------------ ввод

    @Override
    public boolean contentClicked(double mx, double my, int button) {
        editKey = null;
        return hits.click(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (editKey == null) {
            return false;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                editKey = null;
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (editBuf.length() > 0) {
                    editBuf.setLength(editBuf.length() - 1);
                    editSink.accept(editBuf.toString());
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(String text) {
        if (editKey == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 32 && ch != 127 && editBuf.length() < ClockService.MAX_NAME) {
                editBuf.append(ch);
            }
        }
        editSink.accept(editBuf.toString());
        return true;
    }

    @Override
    public void onBlur() {
        editKey = null;
    }

    @Override
    public void onClose() {
        editKey = null;
        svc.flush();
    }
}
