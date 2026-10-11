package com.multitool.calc;

import com.multitool.MultiToolClient;
import com.multitool.overlay.Gfx;
import com.multitool.overlay.OverlayWindow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Окно калькулятора: дисплей, кнопки, история, компактный режим, ввод с клавиатуры. */
public final class CalculatorWindow extends OverlayWindow {
    public static final String ID = "calculator";

    private static final int DISPLAY_H = 36;
    private static final int GAP = 3;
    private static final int ROWS = 6;
    private static final int COLS = 4;
    private static final String[] KEYS = {
            "C", "(", ")", "<",
            "7", "8", "9", "/",
            "4", "5", "6", "*",
            "1", "2", "3", "-",
            "0", ".", "%", "+",
            "±", "Copy", "Hist", "="
    };

    private final CalculatorModel model = new CalculatorModel();
    private final Path historyFile;
    private boolean historyView;
    private String pressedLabel = "";
    private long pressedAt;
    private long copiedAt;

    public CalculatorWindow(Path historyFile) {
        super(ID, "Calculator");
        this.historyFile = historyFile;
        this.w = 220;
        this.h = 300;
        this.minW = 120;
        this.minH = 154;
        model.loadHistory(historyFile);
    }

    @Override
    protected int compactContentHeight() {
        return DISPLAY_H;
    }

    @Override
    protected void renderContent(DrawContext c, TextRenderer tr, int mx, int my, boolean interactive) {
        // дисплей
        Gfx.rrect(c, cx, cy, cx + cw, cy + DISPLAY_H, 4, Gfx.DISPLAY);
        String expr = Gfx.tail(tr, model.expression(), cw - 10);
        Gfx.rightAligned(c, tr, expr, cx + cw - 5, cy + 5, Gfx.DIM);
        String res = model.preview();
        boolean err = !model.error().isEmpty();
        if (System.currentTimeMillis() - copiedAt < 900) {
            res = "Copied";
        }
        Gfx.rightAligned(c, tr, Gfx.tail(tr, res, cw - 10), cx + cw - 5, cy + 20, err ? Gfx.ERROR : Gfx.TEXT);

        if (compact) {
            return;
        }
        if (historyView) {
            renderHistory(c, tr, mx, my, interactive);
            return;
        }
        for (int i = 0; i < KEYS.length; i++) {
            int[] r = cell(i);
            String label = KEYS[i];
            boolean hover = interactive && Gfx.inside(mx, my, r[0], r[1], r[2], r[3]);
            boolean flash = label.equals(pressedLabel) && System.nanoTime() - pressedAt < 150_000_000L;
            boolean eq = label.equals("=");
            int bg = eq ? (flash ? Gfx.lighten(Gfx.ACCENT, 0.35f) : hover ? Gfx.ACCENT_HOVER : Gfx.ACCENT)
                    : flash ? Gfx.BTN_PRESS : hover ? Gfx.BTN_HOVER : Gfx.BTN;
            Gfx.rrect(c, r[0], r[1], r[0] + r[2], r[1] + r[3], 4, bg);
            int col = eq ? 0xFFFFFFFF : isOperator(label) ? Gfx.OP : Gfx.TEXT;
            Gfx.centered(c, tr, label, r[0] + r[2] / 2, r[1] + (r[3] - 8) / 2, col);
        }
    }

    private void renderHistory(DrawContext c, TextRenderer tr, int mx, int my, boolean interactive) {
        int top = cy + DISPLAY_H + GAP;
        int bw = (cw - GAP) / 2;
        drawBtn(c, tr, cx, top, bw, 14, "Back", mx, my, interactive);
        drawBtn(c, tr, cx + bw + GAP, top, bw, 14, "Clear", mx, my, interactive);
        List<CalculatorModel.Entry> list = model.history();
        int y = top + 14 + GAP + 2;
        if (list.isEmpty()) {
            Gfx.text(c, tr, "History is empty", cx + 2, y + 2, Gfx.DIM);
            return;
        }
        for (int i = 0; i < list.size() && y + 12 <= cy + ch; i++) {
            CalculatorModel.Entry e = list.get(i);
            boolean hover = interactive && Gfx.inside(mx, my, cx, y, cw, 12);
            if (hover) {
                Gfx.rrect(c, cx, y, cx + cw, y + 12, 3, Gfx.BTN_HOVER);
            }
            String line = Gfx.tail(tr, e.expression() + " = " + e.result(), cw - 6);
            Gfx.text(c, tr, line, cx + 3, y + 2, Gfx.TEXT);
            y += 12;
        }
    }

    private void drawBtn(DrawContext c, TextRenderer tr, int x, int y, int bw, int bh, String label, int mx, int my,
                         boolean interactive) {
        boolean hover = interactive && Gfx.inside(mx, my, x, y, bw, bh);
        Gfx.rrect(c, x, y, x + bw, y + bh, 4, hover ? Gfx.BTN_HOVER : Gfx.BTN);
        Gfx.centered(c, tr, label, x + bw / 2, y + (bh - 8) / 2, Gfx.TEXT);
    }

    private int[] cell(int idx) {
        int ky = cy + DISPLAY_H + GAP;
        int kh = ch - DISPLAY_H - GAP;
        int cellH = Math.max(12, (kh - GAP * (ROWS - 1)) / ROWS);
        int cellW = Math.max(14, (cw - GAP * (COLS - 1)) / COLS);
        int col = idx % COLS;
        int row = idx / COLS;
        return new int[]{cx + col * (cellW + GAP), ky + row * (cellH + GAP), cellW, cellH};
    }

    private static boolean isOperator(String l) {
        return l.equals("/") || l.equals("*") || l.equals("-") || l.equals("+") || l.equals("%");
    }

    @Override
    public boolean contentClicked(double mx, double my, int button) {
        if (button != 0 || compact) {
            return false;
        }
        if (historyView) {
            int top = cy + DISPLAY_H + GAP;
            int bw = (cw - GAP) / 2;
            if (Gfx.inside(mx, my, cx, top, bw, 14)) {
                historyView = false;
                return true;
            }
            if (Gfx.inside(mx, my, cx + bw + GAP, top, bw, 14)) {
                model.clearHistory();
                persist();
                return true;
            }
            int y = top + 14 + GAP + 2;
            List<CalculatorModel.Entry> list = model.history();
            for (int i = 0; i < list.size() && y + 12 <= cy + ch; i++, y += 12) {
                if (Gfx.inside(mx, my, cx, y, cw, 12)) {
                    model.recall(i);
                    historyView = false;
                    return true;
                }
            }
            return true;
        }
        for (int i = 0; i < KEYS.length; i++) {
            int[] r = cell(i);
            if (Gfx.inside(mx, my, r[0], r[1], r[2], r[3])) {
                press(KEYS[i]);
                return true;
            }
        }
        return false;
    }

    private void press(String label) {
        pressedLabel = label;
        pressedAt = System.nanoTime();
        switch (label) {
            case "C" -> model.clear();
            case "<" -> model.backspace();
            case "±" -> model.negate();
            case "=" -> {
                model.evaluate();
                persist();
            }
            case "Copy" -> copy();
            case "Hist" -> historyView = true;
            default -> model.input(label);
        }
    }

    private void copy() {
        String t = model.copyText();
        if (!t.isEmpty()) {
            MinecraftClient.getInstance().keyboard.setClipboard(t);
            copiedAt = System.currentTimeMillis();
        }
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            copy();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            String clip = MinecraftClient.getInstance().keyboard.getClipboard();
            if (clip != null) {
                model.input(clip.length() > 200 ? clip.substring(0, 200) : clip);
            }
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                pressedLabel = "=";
                pressedAt = System.nanoTime();
                model.evaluate();
                persist();
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                model.backspace();
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                model.clear();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(String text) {
        model.input(text);
        return true;
    }

    @Override
    public void onClose() {
        persist();
    }

    public void persist() {
        try {
            model.saveHistory(historyFile);
        } catch (IOException e) {
            MultiToolClient.LOGGER.warn("Не удалось сохранить историю калькулятора: {}", e.getMessage());
        }
    }
}
