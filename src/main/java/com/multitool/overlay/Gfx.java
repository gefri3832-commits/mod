package com.multitool.overlay;

import com.multitool.config.ModConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Общие цвета и простые примитивы рисования. Цвета в формате ARGB (альфа обязательна).
 * Цвета не константы: {@link #refresh()} пересчитывает их из настроек (тема, прозрачность, акцент).
 */
public final class Gfx {
    public static int BG;
    public static int TITLE;
    public static int ACCENT;
    public static int ACCENT_HOVER;
    public static int TEXT;
    public static int DIM;
    public static int ERROR;
    public static int OK;
    public static int BTN;
    public static int BTN_HOVER;
    public static int BTN_PRESS;
    public static int BTN_OFF;
    public static int DISPLAY;
    public static int OP;
    public static int MENU;
    public static int BORDER;

    static {
        refresh();
    }

    private Gfx() {
    }

    /** Пересчитывает палитру из {@link ModConfig#INSTANCE}. */
    public static void refresh() {
        ModConfig cfg = ModConfig.INSTANCE;
        int a = Math.round(cfg.opacity * 255f / 100f) << 24;
        ACCENT = cfg.accentColor();
        ACCENT_HOVER = lighten(ACCENT, 0.18f);
        if (cfg.lightTheme) {
            BG = a | 0xF2F3F5;
            TITLE = a | 0xE1E3E8;
            TEXT = 0xFF1F2328;
            DIM = 0xFF5F6672;
            ERROR = 0xFFD12F2F;
            OK = 0xFF1E8E4E;
            BTN = 0xFFD4D7DE;
            BTN_HOVER = 0xFFC4C8D1;
            BTN_PRESS = 0xFFAEB3BF;
            BTN_OFF = 0xFFE6E8EC;
            DISPLAY = 0xFFFFFFFF;
            OP = 0xFF2F6BDB;
            MENU = 0xDDEDEEF1;
            BORDER = 0xFF9AA0AA;
        } else {
            BG = a | 0x202228;
            TITLE = a | 0x2A2D35;
            TEXT = 0xFFE8EAED;
            DIM = 0xFF9AA0AA;
            ERROR = 0xFFFF6B6B;
            OK = 0xFF5BD68F;
            BTN = 0xFF33363F;
            BTN_HOVER = 0xFF42464F;
            BTN_PRESS = 0xFF5A5F6B;
            BTN_OFF = 0xFF2A2C33;
            DISPLAY = 0xFF181A1F;
            OP = 0xFF9CC2FF;
            MENU = 0xDD16181C;
            BORDER = 0xFF4A4E58;
        }
    }

    /** Осветляет цвет, сохраняя альфу. */
    public static int lighten(int argb, float amount) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r += Math.round((255 - r) * amount);
        g += Math.round((255 - g) * amount);
        b += Math.round((255 - b) * amount);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Прямоугольник со скруглёнными углами радиуса r. */
    public static void rrect(DrawContext c, int x1, int y1, int x2, int y2, int r, int color) {
        int r2 = Math.max(0, Math.min(r, Math.min(x2 - x1, y2 - y1) / 2));
        rrect2(c, x1, y1, x2, y2, r2, r2, color);
    }

    /**
     * Прямоугольник с разными радиусами верхних (rt) и нижних (rb) углов. Пиксели не перекрываются,
     * поэтому полупрозрачные цвета не дают швов.
     */
    public static void rrect2(DrawContext c, int x1, int y1, int x2, int y2, int rt, int rb, int color) {
        int w = x2 - x1;
        int h = y2 - y1;
        if (w <= 0 || h <= 0) {
            return;
        }
        rt = Math.max(0, Math.min(rt, Math.min(w / 2, h)));
        rb = Math.max(0, Math.min(rb, Math.min(w / 2, h - rt)));
        c.fill(x1, y1 + rt, x2, y2 - rb, color);
        for (int i = 0; i < rt; i++) {
            int dx = cornerInset(rt, i);
            c.fill(x1 + dx, y1 + i, x2 - dx, y1 + i + 1, color);
        }
        for (int i = 0; i < rb; i++) {
            int dx = cornerInset(rb, i);
            c.fill(x1 + dx, y2 - i - 1, x2 - dx, y2 - i, color);
        }
    }

    private static int cornerInset(int r, int row) {
        double dy = r - row - 0.5;
        return (int) Math.round(r - Math.sqrt(r * r - dy * dy));
    }

    public static void text(DrawContext c, TextRenderer tr, String s, int x, int y, int color) {
        c.drawTextWithShadow(tr, Text.literal(s), x, y, color);
    }

    public static void centered(DrawContext c, TextRenderer tr, String s, int cx, int y, int color) {
        c.drawTextWithShadow(tr, Text.literal(s), cx - tr.getWidth(s) / 2, y, color);
    }

    public static void rightAligned(DrawContext c, TextRenderer tr, String s, int rx, int y, int color) {
        c.drawTextWithShadow(tr, Text.literal(s), rx - tr.getWidth(s), y, color);
    }

    /** Обрезает строку слева, пока она не поместится в maxWidth. */
    public static String tail(TextRenderer tr, String s, int maxWidth) {
        while (s.length() > 1 && tr.getWidth(s) > maxWidth) {
            s = s.substring(1);
        }
        return s;
    }

    /** Обрезает строку справа (с «..»), пока она не поместится в maxWidth. */
    public static String fit(TextRenderer tr, String s, int maxWidth) {
        if (tr.getWidth(s) <= maxWidth) {
            return s;
        }
        while (s.length() > 1 && tr.getWidth(s + "..") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }

    public static boolean inside(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
}
