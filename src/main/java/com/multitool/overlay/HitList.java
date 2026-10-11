package com.multitool.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Области, на которые можно нажать. Заполняется во время отрисовки, поэтому раскладка
 * не дублируется в обработчике кликов: что нарисовано, то и кликабельно.
 */
public final class HitList {
    private record Hit(int x, int y, int w, int h, IntConsumer action) {
    }

    private final List<Hit> hits = new ArrayList<>();

    public void clear() {
        hits.clear();
    }

    /** Действие получает номер кнопки мыши (0 — левая, 1 — правая). */
    public void add(int x, int y, int w, int h, IntConsumer action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    /** Нажатие обрабатывает верхняя (последняя добавленная) область под курсором. */
    public boolean click(double mx, double my, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (Gfx.inside(mx, my, h.x, h.y, h.w, h.h)) {
                h.action.accept(button);
                return true;
            }
        }
        return false;
    }

    public int size() {
        return hits.size();
    }
}
