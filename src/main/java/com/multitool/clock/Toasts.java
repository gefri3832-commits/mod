package com.multitool.clock;

import com.multitool.overlay.Gfx;
import com.multitool.overlay.HitList;
import com.multitool.overlay.Ui;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Всплывающие уведомления таймеров и будильников в правом верхнем углу. Показываются поверх игры
 * всегда; кнопки (отключить, отложить) доступны только в режиме взаимодействия, когда открыт экран MultiTool.
 */
public final class Toasts {
    private static final int W = 196;
    private static final int H = 50;
    private static final int GAP = 4;
    private static final int MAX = 4;

    private static final HitList HITS = new HitList();

    private Toasts() {
    }

    public static void render(DrawContext c, TextRenderer tr, int screenW, int mx, int my, boolean interactive,
                              ClockService svc) {
        HITS.clear();
        List<ClockService.Notice> list = svc.notices;
        Ui ui = new Ui(c, tr, mx, my, interactive, HITS);
        int shown = Math.min(MAX, list.size());
        for (int i = 0; i < shown; i++) {
            ClockService.Notice n = list.get(i);
            int x = screenW - W - 8;
            int y = 8 + i * (H + GAP);
            Gfx.rrect(c, x - 1, y - 1, x + W + 1, y + H + 1, 7, 0x66000000);
            Gfx.rrect(c, x, y, x + W, y + H, 6, Gfx.BG | 0xFF000000);
            Gfx.rrect(c, x, y, x + 3, y + H, 1, n.isAlarm() ? Gfx.ERROR : Gfx.ACCENT);
            ui.text(Gfx.fit(tr, n.title, W - 16), x + 9, y + 5, Gfx.TEXT);
            ui.text(Gfx.fit(tr, n.text, W - 16), x + 9, y + 16, Gfx.DIM);
            int by = y + H - 18;
            if (!interactive) {
                ui.text("Open MultiTool to dismiss", x + 9, by + 3, Gfx.DIM);
                continue;
            }
            if (n.isAlarm()) {
                int bw = (W - 18 - 8) / 3;
                ui.button(x + 9, by, bw, 14, "Dismiss", true, () -> svc.dismiss(n));
                ui.button(x + 9 + bw + 4, by, bw, 14, "5 min", false, () -> svc.snooze(n, 5));
                ui.button(x + 9 + (bw + 4) * 2, by, bw, 14, "10 min", false, () -> svc.snooze(n, 10));
            } else {
                ui.button(x + 9, by, 60, 14, "OK", true, () -> svc.dismiss(n));
            }
        }
        if (list.size() > shown) {
            ui.text("+" + (list.size() - shown) + " more", screenW - W - 8, 8 + shown * (H + GAP), Gfx.DIM);
        }
    }

    /** Обрабатывает клик по кнопкам уведомлений; true — клик поглощён. */
    public static boolean click(double mx, double my, int button) {
        return HITS.click(mx, my, button);
    }
}
