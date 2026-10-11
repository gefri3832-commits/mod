package com.multitool.ui;

import com.multitool.browser.BrowserWindow;
import com.multitool.calc.CalculatorWindow;
import com.multitool.clock.ClockService;
import com.multitool.clock.ClockWindow;
import com.multitool.clock.Toasts;
import com.multitool.overlay.Gfx;
import com.multitool.overlay.OverlayWindow;
import com.multitool.overlay.WindowManager;
import com.multitool.overlay.WindowManager.Part;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * Режим взаимодействия: главное меню и все открытые окна. Пока экран открыт, ввод идёт окнам;
 * Esc возвращает управление игре, закреплённые окна остаются на экране.
 */
public final class MultiToolScreen extends Screen {
    private enum Drag { NONE, MOVE, RESIZE }

    private static final String[] TOOLS = {"Calculator", "Clock", "Browser", "Settings"};
    private static final String[] TOOL_IDS = {CalculatorWindow.ID, ClockWindow.ID, BrowserWindow.ID, SettingsWindow.ID};
    private static final int BTN_W = 78;
    private static final int BTN_H = 20;
    private static final int GAP = 6;

    private final WindowManager wm = WindowManager.INSTANCE;
    private Drag drag = Drag.NONE;
    private OverlayWindow dragWin;
    private double grabX;
    private double grabY;
    private int resizeMask;
    private double pressX;
    private double pressY;
    private int startX;
    private int startY;
    private int startW;
    private int startH;

    public MultiToolScreen() {
        super(Text.literal("MultiTool Overlay"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int menuX() {
        int total = TOOLS.length * BTN_W + (TOOLS.length - 1) * GAP;
        return (this.width - total) / 2;
    }

    private int menuY() {
        return this.height - BTN_H - 14;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        wm.render(context, this.textRenderer, this.width, this.height, mouseX, mouseY, true);
        Toasts.render(context, this.textRenderer, this.width, mouseX, mouseY, true, ClockService.INSTANCE);

        int mx0 = menuX();
        int my0 = menuY();
        int total = TOOLS.length * BTN_W + (TOOLS.length - 1) * GAP;
        Gfx.rrect(context, mx0 - 8, my0 - 8, mx0 + total + 8, my0 + BTN_H + 8, 8, Gfx.MENU);
        for (int i = 0; i < TOOLS.length; i++) {
            int bx = mx0 + i * (BTN_W + GAP);
            boolean available = TOOL_IDS[i] != null;
            OverlayWindow w = available ? wm.get(TOOL_IDS[i]) : null;
            boolean isOpen = w != null && w.open;
            boolean hover = available && Gfx.inside(mouseX, mouseY, bx, my0, BTN_W, BTN_H);
            int bg = !available ? Gfx.BTN_OFF : isOpen ? Gfx.ACCENT : hover ? Gfx.BTN_HOVER : Gfx.BTN;
            Gfx.rrect(context, bx, my0, bx + BTN_W, my0 + BTN_H, 5, bg);
            Gfx.centered(context, this.textRenderer, TOOLS[i], bx + BTN_W / 2, my0 + 6,
                    !available ? Gfx.DIM : isOpen ? 0xFFFFFFFF : Gfx.TEXT);
        }
        Gfx.centered(context, this.textRenderer,
                "Drag edges/corners to resize. P - pin window, Esc - back to game",
                this.width / 2, my0 - 22, Gfx.DIM);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        int button = click.button();

        if (Toasts.click(mx, my, button)) {
            return true;
        }
        if (button == 0) {
            int mx0 = menuX();
            int my0 = menuY();
            for (int i = 0; i < TOOLS.length; i++) {
                int bx = mx0 + i * (BTN_W + GAP);
                if (Gfx.inside(mx, my, bx, my0, BTN_W, BTN_H)) {
                    if (TOOL_IDS[i] != null) {
                        wm.toggle(TOOL_IDS[i]);
                        wm.save();
                    }
                    return true;
                }
            }
        }

        OverlayWindow w = wm.windowAt(mx, my);
        if (w == null) {
            return super.mouseClicked(click, doubled);
        }
        wm.bringToFront(w);
        if (button != 0) {
            return w.contentClicked(mx, my, button, doubled);
        }
        Part part = wm.partAt(w, mx, my);
        switch (part) {
            case CLOSE -> {
                wm.close(w);
                wm.save();
            }
            case PIN -> {
                w.pinned = !w.pinned;
                wm.save();
            }
            case COMPACT -> {
                wm.toggleCompact(w);
                wm.save();
            }
            case TITLE -> {
                drag = Drag.MOVE;
                dragWin = w;
                grabX = mx - w.x;
                grabY = my - w.y;
            }
            case RESIZE -> {
                drag = Drag.RESIZE;
                dragWin = w;
                resizeMask = wm.edgeMask(w, mx, my);
                pressX = mx;
                pressY = my;
                startX = w.x;
                startY = w.y;
                startW = w.w;
                startH = w.effectiveHeight();
            }
            case CONTENT -> w.contentClicked(mx, my, button, doubled);
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (drag == Drag.NONE || dragWin == null) {
            wm.mouseMoved(click.x(), click.y());
            return super.mouseDragged(click, offsetX, offsetY);
        }
        if (drag == Drag.MOVE) {
            dragWin.x = (int) Math.round(click.x() - grabX);
            dragWin.y = (int) Math.round(click.y() - grabY);
        } else {
            wm.resize(dragWin, resizeMask, startX, startY, startW, startH, click.x() - pressX, click.y() - pressY,
                    this.width, this.height);
        }
        wm.clamp(dragWin, this.width, this.height);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (drag != Drag.NONE) {
            drag = Drag.NONE;
            dragWin = null;
            wm.save();
            return true;
        }
        OverlayWindow f = wm.focused();
        if (f != null && f.mouseReleased(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        wm.mouseMoved(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        OverlayWindow w = wm.windowAt(mouseX, mouseY);
        if (w != null && w.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        OverlayWindow f = wm.focused();
        if (f != null && f.keyReleased(input.key(), input.scancode(), input.modifiers())) {
            return true;
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        OverlayWindow f = wm.focused();
        if (f != null && input.key() != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                && f.keyPressed(input.key(), input.scancode(), input.modifiers())) {
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        OverlayWindow f = wm.focused();
        if (f != null && f.charTyped(input.asString())) {
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void removed() {
        wm.screenClosed();
        wm.save();
        super.removed();
    }
}
