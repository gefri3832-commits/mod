package com.multitool.ui;

import com.multitool.audio.Beeper;
import com.multitool.config.ModConfig;
import com.multitool.overlay.Gfx;
import com.multitool.overlay.HitList;
import com.multitool.overlay.OverlayWindow;
import com.multitool.overlay.Ui;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.function.IntConsumer;

/** Окно настроек мода. Каждый элемент меняет реальную настройку, которая сразу сохраняется и применяется. */
public final class SettingsWindow extends OverlayWindow {
    public static final String ID = "settings";

    private static final String[] TABS = {"Interface", "Clock", "Controls"};
    private static final int ROW = 20;
    private static final int TAB_H = 16;

    private final ModConfig cfg = ModConfig.INSTANCE;
    private final HitList hits = new HitList();
    private int tab;

    public SettingsWindow() {
        super(ID, "Settings");
        this.x = 590;
        this.y = 40;
        this.w = 250;
        this.h = 250;
        this.minW = 200;
        this.minH = 150;
    }

    @Override
    protected int compactContentHeight() {
        return 12;
    }

    @Override
    protected void renderContent(DrawContext c, TextRenderer tr, int mx, int my, boolean interactive) {
        hits.clear();
        Ui ui = new Ui(c, tr, mx, my, interactive, hits);
        if (compact) {
            ui.text(Gfx.fit(tr, (cfg.lightTheme ? "Light" : "Dark") + " theme, opacity " + cfg.opacity + "%", cw - 4),
                    cx + 2, cy + 1, Gfx.DIM);
            return;
        }
        int tw = (cw - 6) / 3;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            ui.button(cx + i * (tw + 3), cy, tw, TAB_H - 2, TABS[i], tab == i, () -> tab = idx);
        }
        int y = cy + TAB_H + 6;
        switch (tab) {
            case 0 -> renderInterface(ui, y);
            case 1 -> renderClock(ui, y);
            default -> renderControls(ui, y);
        }
    }

    private void renderInterface(Ui ui, int y) {
        row(ui, y, "Theme");
        ui.button(cx + cw - 70, y, 70, 14, cfg.lightTheme ? "Light" : "Dark", false, () -> {
            cfg.lightTheme = !cfg.lightTheme;
            cfg.changed();
        });
        y += ROW;
        row(ui, y, "Opacity");
        stepper(ui, y, cfg.opacity + "%", 5, 10, d -> {
            cfg.opacity += d;
            cfg.changed();
        });
        y += ROW;
        row(ui, y, "Accent");
        for (int i = 0; i < ModConfig.ACCENT_COLORS.length; i++) {
            final int idx = i;
            int sx = cx + cw - (ModConfig.ACCENT_COLORS.length - i) * 16;
            boolean sel = cfg.accent == i;
            if (sel) {
                Gfx.rrect(ui.c, sx - 1, y - 1, sx + 14, y + 15, 4, Gfx.TEXT);
            }
            Gfx.rrect(ui.c, sx, y, sx + 13, y + 14, 3, ModConfig.ACCENT_COLORS[i]);
            ui.hits.add(sx, y, 13, 14, b -> {
                if (b == 0) {
                    cfg.accent = idx;
                    cfg.changed();
                }
            });
        }
        y += ROW;
        row(ui, y, "Corner radius");
        stepper(ui, y, String.valueOf(cfg.cornerRadius), 1, 2, d -> {
            cfg.cornerRadius += d;
            cfg.changed();
        });
        y += ROW;
        row(ui, y, "Shadows");
        toggle(ui, y, cfg.shadows, () -> {
            cfg.shadows = !cfg.shadows;
            cfg.changed();
        });
        y += ROW;
        row(ui, y, "Window borders");
        toggle(ui, y, cfg.borders, () -> {
            cfg.borders = !cfg.borders;
            cfg.changed();
        });
        y += ROW + 2;
        ui.button(cx, y, cw, 14, "Reset appearance", false, cfg::resetAppearance);
    }

    private void renderClock(Ui ui, int y) {
        row(ui, y, "24-hour time");
        toggle(ui, y, cfg.clock24h, () -> {
            cfg.clock24h = !cfg.clock24h;
            cfg.changed();
        });
        y += ROW;
        row(ui, y, "Show seconds");
        toggle(ui, y, cfg.showSeconds, () -> {
            cfg.showSeconds = !cfg.showSeconds;
            cfg.changed();
        });
        y += ROW;
        row(ui, y, "Show date");
        toggle(ui, y, cfg.showDate, () -> {
            cfg.showDate = !cfg.showDate;
            cfg.changed();
        });
        y += ROW;
        row(ui, y, "Alert volume");
        stepper(ui, y, cfg.volume + "%", 10, 10, d -> {
            cfg.volume += d;
            cfg.changed();
        });
        y += ROW;
        row(ui, y, "Alert sound");
        ui.button(cx + cw - 124, y, 14, 14, "<", false, () -> cycleSound(-1));
        ui.center(ModConfig.SOUND_NAMES[cfg.sound], cx + cw - 124 + 14 + 21, y + 3, Gfx.TEXT);
        ui.button(cx + cw - 124 + 14 + 42, y, 14, 14, ">", false, () -> cycleSound(1));
        ui.button(cx + cw - 50, y, 50, 14, "Test", false, () -> Beeper.play(cfg.sound, cfg.volume));
        y += ROW;
        row(ui, y, "Timer popup (s)");
        stepper(ui, y, String.valueOf(cfg.toastSeconds), 1, 5, d -> {
            cfg.toastSeconds += d;
            cfg.changed();
        });
        y += ROW + 2;
        ui.button(cx, y, cw, 14, "Reset clock settings", false, cfg::resetClock);
        if (Beeper.isUnavailable()) {
            ui.text("Audio output is unavailable", cx + 2, y + 18, Gfx.ERROR);
        }
    }

    private void renderControls(Ui ui, int y) {
        String[][] keys = {
                {"Open MultiTool", "Right Shift"},
                {"Show/hide pinned windows", "\\"},
                {"Dismiss alarm and timer alerts", "End"}
        };
        ui.text("Default keys", cx + 2, y, Gfx.DIM);
        y += 14;
        for (String[] k : keys) {
            ui.text(Gfx.fit(ui.tr, k[0], cw - 70), cx + 2, y, Gfx.TEXT);
            ui.right(k[1], cx + cw - 2, y, Gfx.ACCENT);
            y += 12;
        }
        y += 6;
        ui.text(Gfx.fit(ui.tr, "Change keys in:", cw - 4), cx + 2, y, Gfx.DIM);
        y += 12;
        ui.text(Gfx.fit(ui.tr, "Options > Controls > Key Binds", cw - 4), cx + 2, y, Gfx.TEXT);
        y += 12;
        ui.text(Gfx.fit(ui.tr, "> MultiTool Overlay", cw - 4), cx + 2, y, Gfx.TEXT);
        y += 18;
        ui.text(Gfx.fit(ui.tr, "Esc closes the window layer; pinned", cw - 4), cx + 2, y, Gfx.DIM);
        y += 12;
        ui.text(Gfx.fit(ui.tr, "windows stay visible over the game.", cw - 4), cx + 2, y, Gfx.DIM);
    }

    private void row(Ui ui, int y, String label) {
        ui.text(Gfx.fit(ui.tr, label, cw - 130), cx + 2, y + 3, Gfx.TEXT);
    }

    private void toggle(Ui ui, int y, boolean on, Runnable action) {
        ui.button(cx + cw - 44, y, 44, 14, on ? "On" : "Off", on, action);
    }

    private void stepper(Ui ui, int y, String text, int small, int big, IntConsumer delta) {
        int sw = 84;
        int sx = cx + cw - sw;
        ui.button(sx, y, 14, 14, "-", false, b -> delta.accept(-(b == 0 ? small : big)));
        ui.button(sx + sw - 14, y, 14, 14, "+", false, b -> delta.accept(b == 0 ? small : big));
        ui.center(text, sx + sw / 2, y + 3, Gfx.TEXT);
    }

    private void cycleSound(int dir) {
        int n = ModConfig.SOUND_NAMES.length;
        cfg.sound = ((cfg.sound + dir) % n + n) % n;
        cfg.changed();
        Beeper.play(cfg.sound, cfg.volume);
    }

    @Override
    public boolean contentClicked(double mx, double my, int button) {
        return hits.click(mx, my, button);
    }
}
