package com.multitool.overlay;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.function.IntConsumer;

/** Набор простых виджетов: рисует элемент и сразу регистрирует его область клика в {@link HitList}. */
public final class Ui {
    public final DrawContext c;
    public final TextRenderer tr;
    public final int mx;
    public final int my;
    public final boolean interactive;
    public final HitList hits;

    public Ui(DrawContext c, TextRenderer tr, int mx, int my, boolean interactive, HitList hits) {
        this.c = c;
        this.tr = tr;
        this.mx = mx;
        this.my = my;
        this.interactive = interactive;
        this.hits = hits;
    }

    public boolean hover(int x, int y, int w, int h) {
        return interactive && Gfx.inside(mx, my, x, y, w, h);
    }

    public void text(String s, int x, int y, int color) {
        Gfx.text(c, tr, s, x, y, color);
    }

    public void right(String s, int rx, int y, int color) {
        Gfx.rightAligned(c, tr, s, rx, y, color);
    }

    public void center(String s, int cx, int y, int color) {
        Gfx.centered(c, tr, s, cx, y, color);
    }

    /** Кнопка; active — выделена акцентным цветом. Действие вызывается при нажатии любой кнопкой мыши. */
    public void button(int x, int y, int w, int h, String label, boolean active, IntConsumer action) {
        boolean hov = hover(x, y, w, h);
        int bg = active ? (hov ? Gfx.ACCENT_HOVER : Gfx.ACCENT) : hov ? Gfx.BTN_HOVER : Gfx.BTN;
        Gfx.rrect(c, x, y, x + w, y + h, 4, bg);
        Gfx.centered(c, tr, Gfx.fit(tr, label, w - 4), x + w / 2, y + (h - 8) / 2, active ? 0xFFFFFFFF : Gfx.TEXT);
        hits.add(x, y, w, h, action);
    }

    /** Кнопка, реагирующая только на левую кнопку мыши. */
    public void button(int x, int y, int w, int h, String label, boolean active, Runnable left) {
        button(x, y, w, h, label, active, b -> {
            if (b == 0) {
                left.run();
            }
        });
    }

    /** Поле текста: в режиме редактирования показывает курсор. */
    public void field(int x, int y, int w, int h, String value, boolean editing, Runnable onClick) {
        Gfx.rrect(c, x, y, x + w, y + h, 3, Gfx.DISPLAY);
        if (editing) {
            c.fill(x, y + h - 1, x + w, y + h, Gfx.ACCENT);
        }
        String shown = value;
        if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
            shown = value + "_";
        }
        Gfx.text(c, tr, Gfx.tail(tr, shown, w - 6), x + 3, y + (h - 8) / 2, Gfx.TEXT);
        hits.add(x, y, w, h, b -> {
            if (b == 0) {
                onClick.run();
            }
        });
    }

    /** Горизонтальная полоса прогресса, frac в диапазоне 0..1. */
    public void bar(int x, int y, int w, int h, double frac, int color) {
        Gfx.rrect(c, x, y, x + w, y + h, 2, Gfx.DISPLAY);
        int fw = (int) Math.round(w * Math.max(0, Math.min(1, frac)));
        if (fw > 0) {
            Gfx.rrect(c, x, y, x + fw, y + h, 2, color);
        }
    }
}
