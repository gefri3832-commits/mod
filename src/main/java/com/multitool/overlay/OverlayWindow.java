package com.multitool.overlay;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Базовое плавающее окно: положение, размер, закрепление, компактный режим. */
public abstract class OverlayWindow {
    public static final int TITLE_H = 18;

    public final String id;
    public final String title;
    public int x = 40;
    public int y = 40;
    public int w = 260;
    public int h = 300;
    public boolean open;
    public boolean pinned;
    public boolean compact;
    public int minW = 180;
    public int minH = 120;

    // прямоугольник содержимого, обновляется при каждой отрисовке
    protected int cx;
    protected int cy;
    protected int cw;
    protected int ch;

    protected OverlayWindow(String id, String title) {
        this.id = id;
        this.title = title;
    }

    /** Высота содержимого в компактном режиме (без заголовка). */
    protected abstract int compactContentHeight();

    protected abstract void renderContent(DrawContext c, TextRenderer tr, int mouseX, int mouseY, boolean interactive);

    public boolean contentClicked(double mx, double my, int button) {
        return false;
    }

    public boolean contentClicked(double mx, double my, int button, boolean doubled) {
        return contentClicked(mx, my, button);
    }

    public boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    /** Движение мыши (в том числе с зажатой кнопкой), координаты экранные. */
    public void mouseMoved(double mx, double my) {
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return false;
    }

    public boolean keyReleased(int key, int scancode, int modifiers) {
        return false;
    }

    public boolean keyPressed(int key, int scancode, int modifiers) {
        return false;
    }

    public boolean charTyped(String text) {
        return false;
    }

    /** Вызывается при закрытии окна и при выходе из игры. */
    public void onClose() {
    }

    /** Экран с окнами закрыт (Esc): окно должно завершить ввод текста. */
    public void onBlur() {
    }

    public int effectiveHeight() {
        return compact ? TITLE_H + compactContentHeight() + 10 : h;
    }

    final void render(DrawContext c, TextRenderer tr, int mouseX, int mouseY, boolean interactive) {
        cx = x + 6;
        cy = y + TITLE_H + 4;
        cw = w - 12;
        ch = effectiveHeight() - TITLE_H - 10;
        renderContent(c, tr, mouseX, mouseY, interactive);
    }
}
