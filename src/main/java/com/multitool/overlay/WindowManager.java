package com.multitool.overlay;

import com.multitool.MultiToolClient;
import com.multitool.config.ModConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Хранит окна в порядке наложения (последнее — сверху), рисует их и сохраняет положение. */
public final class WindowManager {
    public enum Part { NONE, CLOSE, PIN, COMPACT, TITLE, RESIZE, CONTENT }

    public static final WindowManager INSTANCE = new WindowManager();
    private static final int BTN = 16;
    private static final int GRIP = 10;
    /** Ширина зоны захвата края и размер зоны захвата угла (в пикселях GUI). */
    private static final int EDGE = 4;
    private static final int CORNER = 10;

    public static final int L = 1;
    public static final int R = 2;
    public static final int T = 4;
    public static final int B = 8;

    private final List<OverlayWindow> windows = new ArrayList<>();
    private Path file;
    public boolean hudVisible = true;

    private WindowManager() {
    }

    public void register(OverlayWindow w) {
        windows.add(w);
    }

    public OverlayWindow get(String id) {
        for (OverlayWindow w : windows) {
            if (w.id.equals(id)) {
                return w;
            }
        }
        return null;
    }

    /** Окно, получающее клавиатуру: верхнее открытое. */
    public OverlayWindow focused() {
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).open) {
                return windows.get(i);
            }
        }
        return null;
    }

    public void open(String id) {
        OverlayWindow w = get(id);
        if (w != null) {
            w.open = true;
            bringToFront(w);
        }
    }

    public void close(OverlayWindow w) {
        w.open = false;
        w.onClose();
    }

    public void toggle(String id) {
        OverlayWindow w = get(id);
        if (w == null) {
            return;
        }
        if (w.open) {
            close(w);
        } else {
            open(id);
        }
    }

    public void bringToFront(OverlayWindow w) {
        windows.remove(w);
        windows.add(w);
    }

    public void closeAll() {
        for (OverlayWindow w : windows) {
            w.onClose();
        }
    }

    /** Экран MultiTool закрыт: окна должны прекратить ввод текста. */
    public void screenClosed() {
        for (OverlayWindow w : windows) {
            w.onBlur();
        }
    }

    /** Рисует окна. menuOpen=true: все открытые окна, интерактивно; иначе только закреплённые. */
    public void render(DrawContext c, TextRenderer tr, int sw, int sh, int mx, int my, boolean menuOpen) {
        if (!menuOpen && !hudVisible) {
            return;
        }
        OverlayWindow top = focused();
        for (OverlayWindow w : new ArrayList<>(windows)) {
            if (!w.open || (!menuOpen && !w.pinned)) {
                continue;
            }
            clamp(w, sw, sh);
            drawFrame(c, tr, w, menuOpen, mx, my, w == top);
            c.enableScissor(w.x + 1, w.y + OverlayWindow.TITLE_H, w.x + w.w - 1, w.y + w.effectiveHeight() - 1);
            w.render(c, tr, menuOpen ? mx : -1000, menuOpen ? my : -1000, menuOpen);
            c.disableScissor();
        }
    }

    /** Не даёт окну выйти за границы экрана и уменьшает его, если экран стал меньше. */
    public void clamp(OverlayWindow w, int sw, int sh) {
        w.w = Math.max(Math.min(w.minW, sw), Math.min(w.w, sw));
        int eh = w.effectiveHeight();
        if (!w.compact) {
            w.h = Math.max(Math.min(w.minH, sh), Math.min(w.h, sh));
            eh = w.h;
        }
        w.x = Math.max(0, Math.min(w.x, sw - w.w));
        w.y = Math.max(0, Math.min(w.y, sh - eh));
    }

    private void drawFrame(DrawContext c, TextRenderer tr, OverlayWindow w, boolean interactive, int mx, int my,
                           boolean focused) {
        int eh = w.effectiveHeight();
        ModConfig cfg = ModConfig.INSTANCE;
        int r = cfg.cornerRadius;
        if (cfg.shadows) {
            Gfx.rrect(c, w.x - 2, w.y - 1, w.x + w.w + 2, w.y + eh + 3, r + 2, 0x33000000);
            Gfx.rrect(c, w.x - 1, w.y, w.x + w.w + 1, w.y + eh + 1, r + 1, 0x55000000);
        }
        Gfx.rrect2(c, w.x, w.y, w.x + w.w, w.y + OverlayWindow.TITLE_H, r, 0, Gfx.TITLE);
        Gfx.rrect2(c, w.x, w.y + OverlayWindow.TITLE_H, w.x + w.w, w.y + eh, 0, r, Gfx.BG);
        if (cfg.borders) {
            c.fill(w.x + r, w.y, w.x + w.w - r, w.y + 1, Gfx.BORDER);
            c.fill(w.x + r, w.y + eh - 1, w.x + w.w - r, w.y + eh, Gfx.BORDER);
            c.fill(w.x, w.y + r, w.x + 1, w.y + eh - r, Gfx.BORDER);
            c.fill(w.x + w.w - 1, w.y + r, w.x + w.w, w.y + eh - r, Gfx.BORDER);
        }
        if (focused && interactive) {
            c.fill(w.x + r, w.y, w.x + w.w - r, w.y + 1, Gfx.ACCENT);
        }
        Gfx.text(c, tr, w.title, w.x + 8, w.y + 5, Gfx.TEXT);

        int by = w.y + 1;
        int closeX = w.x + w.w - BTN - 2;
        int pinX = closeX - BTN;
        int compX = pinX - BTN;
        button(c, tr, closeX, by, "x", false, interactive, mx, my);
        button(c, tr, pinX, by, "P", w.pinned, interactive, mx, my);
        button(c, tr, compX, by, w.compact ? "+" : "-", false, interactive, mx, my);

        if (interactive) {
            int mask = edgeMask(w, mx, my);
            if (mask != 0 && !inTitleButtons(w, mx, my)) {
                drawEdgeHighlight(c, w, mask, eh);
            }
        }
        if (interactive && !w.compact) {
            int gx = w.x + w.w - GRIP;
            int gy = w.y + eh - GRIP;
            for (int i = 0; i < 3; i++) {
                int o = 3 + i * 3;
                c.fill(gx + GRIP - o - 1, gy + GRIP - 2, gx + GRIP - o, gy + GRIP - 1, Gfx.DIM);
                c.fill(gx + GRIP - 2, gy + GRIP - o - 1, gx + GRIP - 1, gy + GRIP - o, Gfx.DIM);
            }
        }
    }

    private void drawEdgeHighlight(DrawContext c, OverlayWindow w, int mask, int eh) {
        int x2 = w.x + w.w;
        int y2 = w.y + eh;
        int a = Gfx.ACCENT;
        if ((mask & L) != 0) {
            c.fill(w.x, (mask & T) != 0 ? w.y : w.y + CORNER, w.x + 2, (mask & B) != 0 ? y2 : y2 - CORNER, a);
        }
        if ((mask & R) != 0) {
            c.fill(x2 - 2, (mask & T) != 0 ? w.y : w.y + CORNER, x2, (mask & B) != 0 ? y2 : y2 - CORNER, a);
        }
        if ((mask & T) != 0) {
            c.fill((mask & L) != 0 ? w.x : w.x + CORNER, w.y, (mask & R) != 0 ? x2 : x2 - CORNER, w.y + 2, a);
        }
        if ((mask & B) != 0) {
            c.fill((mask & L) != 0 ? w.x : w.x + CORNER, y2 - 2, (mask & R) != 0 ? x2 : x2 - CORNER, y2, a);
        }
    }

    private void button(DrawContext c, TextRenderer tr, int x, int y, String label, boolean active, boolean interactive,
                        int mx, int my) {
        boolean hover = interactive && Gfx.inside(mx, my, x, y, BTN, OverlayWindow.TITLE_H - 2);
        if (active) {
            Gfx.rrect(c, x + 1, y + 1, x + BTN - 1, y + OverlayWindow.TITLE_H - 3, 3, Gfx.ACCENT);
        } else if (hover) {
            Gfx.rrect(c, x + 1, y + 1, x + BTN - 1, y + OverlayWindow.TITLE_H - 3, 3, Gfx.BTN_HOVER);
        }
        Gfx.centered(c, tr, label, x + BTN / 2, y + 4, active ? 0xFFFFFFFF : Gfx.DIM);
    }

    public void mouseMoved(double mx, double my) {
        for (OverlayWindow w : new ArrayList<>(windows)) {
            if (w.open) {
                w.mouseMoved(mx, my);
            }
        }
    }

    /** Верхнее окно под курсором (только среди открытых) или null. */
    public OverlayWindow windowAt(double mx, double my) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            OverlayWindow w = windows.get(i);
            if (w.open && Gfx.inside(mx, my, w.x, w.y, w.w, w.effectiveHeight())) {
                return w;
            }
        }
        return null;
    }

    /** Маска краёв под курсором (L|R|T|B); в компактном режиме доступна только ширина. */
    public int edgeMask(OverlayWindow w, double mx, double my) {
        int eh = w.effectiveHeight();
        if (mx < w.x - 1 || mx > w.x + w.w + 1 || my < w.y - 1 || my > w.y + eh + 1) {
            return 0;
        }
        int m = 0;
        boolean nearL = mx < w.x + EDGE;
        boolean nearR = mx >= w.x + w.w - EDGE;
        boolean nearT = my < w.y + EDGE;
        boolean nearB = my >= w.y + eh - EDGE;
        // в углах зона шире: захватить угол проще, чем край
        if (mx < w.x + CORNER) {
            nearL = nearL || (my < w.y + CORNER || my >= w.y + eh - CORNER);
        }
        if (mx >= w.x + w.w - CORNER) {
            nearR = nearR || (my < w.y + CORNER || my >= w.y + eh - CORNER);
        }
        if (my < w.y + CORNER) {
            nearT = nearT || (mx < w.x + CORNER || mx >= w.x + w.w - CORNER);
        }
        if (my >= w.y + eh - CORNER) {
            nearB = nearB || (mx < w.x + CORNER || mx >= w.x + w.w - CORNER);
        }
        if (nearL) {
            m |= L;
        }
        if (nearR) {
            m |= R;
        }
        if (nearT) {
            m |= T;
        }
        if (nearB) {
            m |= B;
        }
        if (w.compact) {
            m &= (L | R);
        }
        return m;
    }

    /** Кнопки заголовка (свернуть, закрепить, закрыть) имеют приоритет над верхним краем. */
    private boolean inTitleButtons(OverlayWindow w, double mx, double my) {
        int compX = w.x + w.w - BTN - 2 - BTN - BTN;
        return my >= w.y + EDGE && my < w.y + OverlayWindow.TITLE_H && mx >= compX && mx < w.x + w.w - CORNER;
    }

    public Part partAt(OverlayWindow w, double mx, double my) {
        if (edgeMask(w, mx, my) != 0 && !inTitleButtons(w, mx, my)) {
            return Part.RESIZE;
        }
        if (my < w.y + OverlayWindow.TITLE_H) {
            int closeX = w.x + w.w - BTN - 2;
            int pinX = closeX - BTN;
            int compX = pinX - BTN;
            if (mx >= closeX) {
                return Part.CLOSE;
            }
            if (mx >= pinX) {
                return Part.PIN;
            }
            if (mx >= compX) {
                return Part.COMPACT;
            }
            return Part.TITLE;
        }
        return Part.CONTENT;
    }

    /**
     * Меняет размер окна, перетаскивая края из маски mask. Противоположные края остаются на месте,
     * окно не уменьшается меньше минимума и не выходит за границы экрана.
     */
    public void resize(OverlayWindow w, int mask, int sx, int sy, int sw0, int sh0, double dx, double dy,
                       int screenW, int screenH) {
        int left = sx;
        int top = sy;
        int right = sx + sw0;
        int bottom = sy + sh0;
        int minW = Math.min(w.minW, screenW);
        int minH = Math.min(w.minH, screenH);
        if ((mask & L) != 0) {
            left = (int) Math.round(Math.max(0, Math.min(sx + dx, right - minW)));
        }
        if ((mask & R) != 0) {
            right = (int) Math.round(Math.min(screenW, Math.max(sx + sw0 + dx, left + minW)));
        }
        if (!w.compact) {
            if ((mask & T) != 0) {
                top = (int) Math.round(Math.max(0, Math.min(sy + dy, bottom - minH)));
            }
            if ((mask & B) != 0) {
                bottom = (int) Math.round(Math.min(screenH, Math.max(sy + sh0 + dy, top + minH)));
            }
        }
        w.x = left;
        w.y = top;
        w.w = right - left;
        if (!w.compact) {
            w.h = bottom - top;
        }
    }

    public void toggleCompact(OverlayWindow w) {
        w.compact = !w.compact;
    }

    // ---- сохранение ----

    public void setFile(Path file) {
        this.file = file;
    }

    public void load() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException | IllegalArgumentException e) {
            MultiToolClient.LOGGER.warn("Не удалось прочитать {}: {}", file, e.getMessage());
            return;
        }
        hudVisible = Boolean.parseBoolean(p.getProperty("hudVisible", "true"));
        for (OverlayWindow w : windows) {
            w.x = intProp(p, w.id + ".x", w.x);
            w.y = intProp(p, w.id + ".y", w.y);
            w.w = Math.max(w.minW, Math.min(4000, intProp(p, w.id + ".w", w.w)));
            w.h = Math.max(w.minH, Math.min(4000, intProp(p, w.id + ".h", w.h)));
            w.open = Boolean.parseBoolean(p.getProperty(w.id + ".open", "false"));
            w.pinned = Boolean.parseBoolean(p.getProperty(w.id + ".pinned", "false"));
            w.compact = Boolean.parseBoolean(p.getProperty(w.id + ".compact", "false"));
        }
    }

    public void save() {
        if (file == null) {
            return;
        }
        Properties p = new Properties();
        p.setProperty("hudVisible", String.valueOf(hudVisible));
        for (OverlayWindow w : windows) {
            p.setProperty(w.id + ".x", String.valueOf(w.x));
            p.setProperty(w.id + ".y", String.valueOf(w.y));
            p.setProperty(w.id + ".w", String.valueOf(w.w));
            p.setProperty(w.id + ".h", String.valueOf(w.h));
            p.setProperty(w.id + ".open", String.valueOf(w.open));
            p.setProperty(w.id + ".pinned", String.valueOf(w.pinned));
            p.setProperty(w.id + ".compact", String.valueOf(w.compact));
        }
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "MultiTool Overlay: windows");
            }
        } catch (IOException e) {
            MultiToolClient.LOGGER.warn("Не удалось сохранить {}: {}", file, e.getMessage());
        }
    }

    private static int intProp(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
