package com.multitool.browser;

import net.minecraft.client.gui.DrawContext;

/**
 * Граница между окном браузера и конкретным движком отображения (Chromium через MCEF).
 * Окно и модель вкладок ничего не знают о движке: подключается любая реализация.
 * Координаты мыши в методах ввода локальные: относительно левого верхнего угла области страницы,
 * в пикселях GUI Minecraft.
 */
public interface BrowserBackend {
    /** Слушатель событий навигации внутри страницы (клик по ссылке, редирект, смена заголовка). */
    interface Listener {
        void onNavigated(BrowserTab tab, String url, String title);
    }

    /** Движок готов принимать команды. */
    boolean isReady();

    /** Пояснение для пользователя, если движок недоступен или ещё загружается. */
    String statusMessage();

    void navigate(BrowserTab tab, String url);

    void back(BrowserTab tab);

    void forward(BrowserTab tab);

    void reload(BrowserTab tab);

    void stop(BrowserTab tab);

    /** Освободить ресурсы вкладки (закрытие вкладки или окна). */
    void closeTab(BrowserTab tab);

    void shutdown();

    default void setListener(Listener listener) {
    }

    /**
     * Рисует страницу вкладки в прямоугольнике. Возвращает true, если область страницы занята
     * движком (даже если кадр ещё не готов); false — окно рисует своё сообщение.
     */
    default boolean renderPage(DrawContext c, BrowserTab tab, int x, int y, int w, int h) {
        return false;
    }

    default void mouseMoved(BrowserTab tab, double lx, double ly) {
    }

    default void mouseButton(BrowserTab tab, double lx, double ly, int button, boolean doubled, boolean down) {
    }

    /** amount > 0 — прокрутка вверх (как в Minecraft). */
    default void scroll(BrowserTab tab, double lx, double ly, double amount) {
    }

    default void key(BrowserTab tab, int key, int scancode, int modifiers, boolean down) {
    }

    default void typed(BrowserTab tab, int codePoint) {
    }

    default void setFocus(BrowserTab tab, boolean focused) {
    }

    /** Масштаб страниц (1.0 = 100%). */
    default void setZoom(double factor) {
    }

    default double zoom() {
        return 1.0;
    }
}
