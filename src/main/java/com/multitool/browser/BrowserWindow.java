package com.multitool.browser;

import com.multitool.MultiToolClient;
import com.multitool.overlay.Gfx;
import com.multitool.overlay.HitList;
import com.multitool.overlay.OverlayWindow;
import com.multitool.overlay.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Окно браузера: вкладки, панель адреса, стартовая страница с редактируемыми быстрыми ссылками,
 * избранное и история. Отображение страниц делегируется {@link BrowserBackend}; если движок
 * недоступен, окно прямо сообщает об этом и ничего не загружает.
 */
public final class BrowserWindow extends OverlayWindow {
    public static final String ID = "browser";

    private enum View { PAGE, FAVORITES, HISTORY }

    private enum Edit { NONE, URL, QUICK_TITLE, QUICK_URL }

    private static final int ROW = 14;
    private static final int BTN = 16;
    private static final int GAP = 2;

    private final BrowserModel model = new BrowserModel();
    private final Path file;
    private final HitList hits = new HitList();
    private BrowserBackend backend;

    private View view = View.PAGE;
    private Edit edit = Edit.NONE;
    private boolean quickEditMode;
    private int quickIndex = -1;
    private final StringBuilder urlText = new StringBuilder();
    private final StringBuilder quickTitle = new StringBuilder();
    private final StringBuilder quickUrl = new StringBuilder();
    /** Сохранять ли историю посещений (будет подключено к настройкам браузера). */
    public boolean saveHistory = true;

    // область страницы (экранные координаты) и состояние ввода в страницу
    private boolean pageShown;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private boolean pageFocused;
    private boolean pagePressed;

    public BrowserWindow(Path file, BrowserBackend backend) {
        super(ID, "Browser");
        this.file = file;
        this.backend = backend;
        backend.setListener(this::onNavigated);
        this.x = 320;
        this.y = 40;
        this.w = 360;
        this.h = 280;
        this.minW = 200;
        this.minH = 100;
        model.load(file);
    }

    public void setBackend(BrowserBackend backend) {
        this.backend = backend;
        backend.setListener(this::onNavigated);
    }

    /** Навигация внутри страницы (ссылки, редиректы, смена заголовка): история и сохранение. */
    private void onNavigated(BrowserTab tab, String url, String title) {
        model.addHistory(title, url, saveHistory);
        if (tab == model.activeTab() && edit != Edit.URL) {
            urlText.setLength(0);
            urlText.append(url);
        }
        persist();
    }

    public BrowserModel model() {
        return model;
    }

    @Override
    protected int compactContentHeight() {
        return ROW;
    }

    @Override
    protected void renderContent(DrawContext c, TextRenderer tr, int mx, int my, boolean interactive) {
        hits.clear();
        Ui ui = new Ui(c, tr, mx, my, interactive, hits);
        BrowserTab tab = model.activeTab();
        if (compact) {
            String t = tab.isStartPage() ? "Start page" : tab.title;
            ui.text(Gfx.fit(tr, t + (tab.loading ? " (loading)" : ""), cw - 4), cx + 2, cy + 2, Gfx.TEXT);
            return;
        }
        int y = cy;
        renderTabs(ui, y);
        y += ROW + 4;
        renderToolbar(ui, y, tab);
        y += BTN + 4;
        int bodyH = cy + ch - y;
        if (bodyH < 20) {
            return;
        }
        switch (view) {
            case FAVORITES -> renderLinks(ui, y, bodyH, "Favorites", model.favorites(), false);
            case HISTORY -> renderLinks(ui, y, bodyH, "History", reversed(model.history()), true);
            default -> {
                if (tab.isStartPage()) {
                    renderStart(ui, y, bodyH);
                } else {
                    renderPage(ui, y, bodyH, tab);
                }
            }
        }
    }

    // ---- вкладки и панель инструментов ----

    private void renderTabs(Ui ui, int y) {
        List<BrowserTab> tabs = model.tabs();
        int n = tabs.size();
        int avail = cw - ROW - GAP;
        int tw = Math.max(24, Math.min(100, (avail - (n - 1) * GAP) / n));
        for (int i = 0; i < n; i++) {
            final int idx = i;
            BrowserTab t = tabs.get(i);
            int tx = cx + i * (tw + GAP);
            if (tx + tw > cx + avail) {
                break;
            }
            boolean active = i == model.activeIndex();
            String label = t.isStartPage() ? "New tab" : t.title;
            int closeW = active && tw >= 48 ? 10 : 0;
            ui.button(tx, y, tw, ROW, Gfx.fit(ui.tr, label, tw - 6 - closeW), active, b -> {
                if (b == 0) {
                    selectTab(idx);
                } else if (b == 1) {
                    closeTab(idx);
                }
            });
            if (closeW > 0) {
                ui.text("x", tx + tw - 9, y + 3, 0xFFFFFFFF);
                ui.hits.add(tx + tw - 11, y, 11, ROW, b -> {
                    if (b == 0) {
                        closeTab(idx);
                    }
                });
            }
        }
        int plusX = cx + cw - ROW;
        ui.button(plusX, y, ROW, ROW, "+", false, this::openTab);
    }

    private void renderToolbar(Ui ui, int y, BrowserTab tab) {
        boolean ready = backend.isReady();
        int x = cx;
        toolButton(ui, x, y, "<", ready && tab.canGoBack, () -> backend.back(tab));
        x += BTN + GAP;
        toolButton(ui, x, y, ">", ready && tab.canGoForward, () -> backend.forward(tab));
        x += BTN + GAP;
        boolean canReload = ready && !tab.isStartPage();
        toolButton(ui, x, y, tab.loading ? "X" : "R", canReload, () -> {
            if (tab.loading) {
                backend.stop(tab);
            } else {
                backend.reload(tab);
            }
        });
        x += BTN + GAP;
        toolButton(ui, x, y, "H", true, this::home);
        x += BTN + GAP;

        int starX = cx + cw - BTN;
        int fieldW = starX - GAP - x;
        String shown = edit == Edit.URL ? urlText.toString() : tab.url;
        ui.field(x, y, fieldW, BTN, shown, edit == Edit.URL, () -> {
            edit = Edit.URL;
            urlText.setLength(0);
            urlText.append(tab.url);
        });
        boolean fav = !tab.isStartPage() && model.isFavorite(tab.url);
        if (tab.isStartPage()) {
            toolButton(ui, starX, y, "*", false, () -> {
            });
        } else {
            ui.button(starX, y, BTN, BTN, "*", fav, () -> {
                model.toggleFavorite(tab.title, tab.url);
                persist();
            });
        }
    }

    private void toolButton(Ui ui, int x, int y, String label, boolean enabled, Runnable action) {
        if (enabled) {
            ui.button(x, y, BTN, BTN, label, false, action);
        } else {
            Gfx.rrect(ui.c, x, y, x + BTN, y + BTN, 4, Gfx.BTN_OFF);
            Gfx.centered(ui.c, ui.tr, label, x + BTN / 2, y + (BTN - 8) / 2, Gfx.DIM);
        }
    }

    // ---- стартовая страница ----

    private void renderStart(Ui ui, int y, int bodyH) {
        int bottom = y + bodyH;
        ui.text("Quick links", cx + 2, y + 1, Gfx.DIM);
        int ly = y + 12;
        List<Link> quick = model.quickLinks();
        int rowH = 18;
        for (int i = 0; i < quick.size(); i++) {
            final int idx = i;
            Link l = quick.get(i);
            if (ly + rowH > bottom - 20) {
                break;
            }
            int bw = quickEditMode ? cw - 20 : cw;
            ui.button(cx, ly, bw, rowH - 2, Gfx.fit(ui.tr, l.title() + "  " + UrlUtil.hostOf(l.url()), bw - 6),
                    quickEditMode && quickIndex == i, () -> {
                        if (quickEditMode) {
                            beginQuickEdit(idx);
                        } else {
                            go(l.url());
                        }
                    });
            if (quickEditMode) {
                ui.button(cx + cw - 18, ly, 18, rowH - 2, "x", false, () -> {
                    model.removeQuickLink(idx);
                    quickIndex = -1;
                    edit = Edit.NONE;
                    persist();
                });
            }
            ly += rowH;
        }
        if (quickEditMode) {
            int ey = Math.min(ly, bottom - 20 - 2 * BTN - 2 * GAP);
            if (edit != Edit.URL) {
                ui.field(cx, ey, cw, BTN, quickTitle.toString(), edit == Edit.QUICK_TITLE, () -> edit = Edit.QUICK_TITLE);
                ui.field(cx, ey + BTN + GAP, cw, BTN, quickUrl.toString(), edit == Edit.QUICK_URL, () -> edit = Edit.QUICK_URL);
                if (quickTitle.length() == 0 && edit != Edit.QUICK_TITLE) {
                    ui.text("title", cx + 4, ey + 4, Gfx.DIM);
                }
                if (quickUrl.length() == 0 && edit != Edit.QUICK_URL) {
                    ui.text("https://...", cx + 4, ey + BTN + GAP + 4, Gfx.DIM);
                }
            }
        }
        // нижний ряд кнопок
        int by = bottom - 16;
        int n = quickEditMode ? 5 : 4;
        int bw = (cw - (n - 1) * GAP) / n;
        int bx = cx;
        ui.button(bx, by, bw, 16, "Fav", false, () -> view = View.FAVORITES);
        bx += bw + GAP;
        ui.button(bx, by, bw, 16, "History", false, () -> view = View.HISTORY);
        bx += bw + GAP;
        ui.button(bx, by, bw, 16, quickEditMode ? "Done" : "Edit", quickEditMode, () -> {
            quickEditMode = !quickEditMode;
            if (!quickEditMode) {
                edit = Edit.NONE;
                quickIndex = -1;
            }
        });
        bx += bw + GAP;
        ui.button(bx, by, bw, 16, UrlUtil.ENGINE_NAMES[model.searchEngine], false, () -> {
            model.searchEngine = (model.searchEngine + 1) % UrlUtil.ENGINE_NAMES.length;
            persist();
        });
        if (quickEditMode) {
            bx += bw + GAP;
            ui.button(bx, by, bw, 16, "Save", false, this::commitQuickEdit);
        }
    }

    private void beginQuickEdit(int idx) {
        quickIndex = idx;
        Link l = model.quickLinks().get(idx);
        quickTitle.setLength(0);
        quickTitle.append(l.title());
        quickUrl.setLength(0);
        quickUrl.append(l.url());
        edit = Edit.QUICK_TITLE;
    }

    private void commitQuickEdit() {
        String url = quickUrl.toString().trim();
        if (UrlUtil.resolve(url, model.searchEngine) == null || !UrlUtil.isSafe(url)) {
            model.activeTab().error = "Quick link needs a full http(s) address";
            return;
        }
        boolean ok = quickIndex >= 0
                ? model.setQuickLink(quickIndex, quickTitle.toString(), url)
                : model.addQuickLink(quickTitle.toString(), url);
        if (ok) {
            model.activeTab().error = "";
            edit = Edit.NONE;
            quickIndex = -1;
            quickTitle.setLength(0);
            quickUrl.setLength(0);
            persist();
        } else {
            model.activeTab().error = "Cannot save quick link (limit reached?)";
        }
    }

    // ---- избранное и история ----

    private static List<Link> reversed(List<Link> src) {
        List<Link> r = new java.util.ArrayList<>(src);
        java.util.Collections.reverse(r);
        return r;
    }

    private void renderLinks(Ui ui, int y, int bodyH, String title, List<Link> links, boolean isHistory) {
        int bottom = y + bodyH;
        ui.text(title + " (" + links.size() + ")", cx + 2, y + 1, Gfx.DIM);
        int ly = y + 12;
        int rowH = 16;
        for (Link l : links) {
            if (ly + rowH > bottom - 20) {
                break;
            }
            ui.button(cx, ly, cw, rowH - 2, Gfx.fit(ui.tr, l.title() + "  " + UrlUtil.hostOf(l.url()), cw - 6), false,
                    () -> go(l.url()));
            ly += rowH;
        }
        if (links.isEmpty()) {
            ui.text("Nothing here yet", cx + 2, ly + 2, Gfx.DIM);
        }
        int by = bottom - 16;
        int half = (cw - GAP) / 2;
        ui.button(cx, by, half, 16, "Back", false, () -> view = View.PAGE);
        if (isHistory) {
            ui.button(cx + half + GAP, by, half, 16, "Clear history", false, () -> {
                model.clearHistory();
                persist();
            });
        }
    }

    // ---- страница ----

    private void renderPage(Ui ui, int y, int bodyH, BrowserTab tab) {
        pageShown = false;
        if (backend.isReady() && backend.renderPage(ui.c, tab, cx, y, cw, bodyH)) {
            pageShown = true;
            px = cx;
            py = y;
            pw = cw;
            ph = bodyH;
            if (tab.loading) {
                ui.c.fill(cx, y, cx + cw, y + 2, Gfx.ACCENT);
            }
            if (!tab.error.isEmpty()) {
                Gfx.rrect(ui.c, cx + 4, y + bodyH - 16, cx + cw - 4, y + bodyH - 4, 4, 0xDD000000);
                ui.text(Gfx.fit(ui.tr, tab.error, cw - 16), cx + 8, y + bodyH - 14, Gfx.ERROR);
            }
            return;
        }
        Gfx.rrect(ui.c, cx, y, cx + cw, y + bodyH, 4, Gfx.DISPLAY);
        int ty = y + 8;
        ui.center(Gfx.fit(ui.tr, tab.title, cw - 12), cx + cw / 2, ty, Gfx.TEXT);
        ui.center(Gfx.fit(ui.tr, tab.url, cw - 12), cx + cw / 2, ty + 12, Gfx.DIM);
        if (!backend.isReady()) {
            ui.center(Gfx.fit(ui.tr, "Chromium engine is not ready", cw - 12), cx + cw / 2, ty + 30, Gfx.ERROR);
            String msg = backend.statusMessage();
            int line = 0;
            for (String part : wrap(ui.tr, msg, cw - 16)) {
                if (ty + 44 + line * 10 > y + bodyH - 8) {
                    break;
                }
                ui.center(part, cx + cw / 2, ty + 44 + line * 10, Gfx.DIM);
                line++;
            }
        } else if (!tab.error.isEmpty()) {
            ui.center(Gfx.fit(ui.tr, tab.error, cw - 12), cx + cw / 2, ty + 30, Gfx.ERROR);
        }
    }

    private static List<String> wrap(TextRenderer tr, String text, int width) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = cur.length() == 0 ? word : cur + " " + word;
            if (tr.getWidth(next) > width && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(next);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    // ---- действия ----

    private void go(String url) {
        BrowserTab t = model.activeTab();
        t.url = url;
        t.error = "";
        t.title = UrlUtil.hostOf(url);
        model.addHistory(t.title, url, saveHistory);
        pageFocused = false;
        backend.navigate(t, url);
        urlText.setLength(0);
        urlText.append(url);
        view = View.PAGE;
        edit = Edit.NONE;
        persist();
    }

    private void submitUrl() {
        String raw = urlText.toString();
        if (raw.isBlank()) {
            edit = Edit.NONE;
            return;
        }
        String url = UrlUtil.resolve(raw, model.searchEngine);
        if (url == null) {
            model.activeTab().error = "Unsupported or invalid address (only http/https)";
            return;
        }
        go(url);
    }

    private void home() {
        BrowserTab t = model.activeTab();
        t.url = "";
        t.title = "New tab";
        t.error = "";
        view = View.PAGE;
        edit = Edit.NONE;
    }

    private void openTab() {
        if (model.newTab() == null) {
            model.activeTab().error = "Tab limit reached";
            return;
        }
        view = View.PAGE;
        edit = Edit.NONE;
    }

    private void selectTab(int i) {
        if (pageFocused) {
            pageFocused = false;
            backend.setFocus(model.activeTab(), false);
        }
        model.select(i);
        view = View.PAGE;
        edit = Edit.NONE;
    }

    private void closeTab(int i) {
        BrowserTab closed = model.closeTab(i);
        if (closed != null) {
            backend.closeTab(closed);
        }
        edit = Edit.NONE;
    }

    private void persist() {
        try {
            model.save(file);
        } catch (IOException e) {
            MultiToolClient.LOGGER.warn("Не удалось сохранить данные браузера: {}", e.getMessage());
        }
    }

    // ---- ввод ----

    private StringBuilder current() {
        return switch (edit) {
            case URL -> urlText;
            case QUICK_TITLE -> quickTitle;
            case QUICK_URL -> quickUrl;
            default -> null;
        };
    }

    private boolean inPage(double mx, double my) {
        return pageShown && view == View.PAGE && Gfx.inside(mx, my, px, py, pw, ph);
    }

    @Override
    public boolean contentClicked(double mx, double my, int button, boolean doubled) {
        BrowserTab tab = model.activeTab();
        if (inPage(mx, my)) {
            edit = Edit.NONE;
            pageFocused = true;
            pagePressed = true;
            backend.setFocus(tab, true);
            backend.mouseButton(tab, mx - px, my - py, button, doubled, true);
            return true;
        }
        if (pageFocused) {
            pageFocused = false;
            backend.setFocus(tab, false);
        }
        return contentClicked(mx, my, button);
    }

    @Override
    public boolean contentClicked(double mx, double my, int button) {
        boolean hit = hits.click(mx, my, button);
        if (!hit && button == 0) {
            edit = Edit.NONE;
        }
        return hit;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (pageShown && view == View.PAGE) {
            backend.mouseMoved(model.activeTab(), mx - px, my - py);
        }
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!pagePressed) {
            return false;
        }
        pagePressed = false;
        backend.mouseButton(model.activeTab(), mx - px, my - py, button, false, false);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!inPage(mx, my)) {
            return false;
        }
        backend.scroll(model.activeTab(), mx - px, my - py, amount);
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scancode, int modifiers) {
        if (!pageFocused) {
            return false;
        }
        backend.key(model.activeTab(), key, scancode, modifiers, false);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_L) {
            BrowserTab t = model.activeTab();
            edit = Edit.URL;
            urlText.setLength(0);
            urlText.append(t.url);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_T) {
            openTab();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_W) {
            closeTab(model.activeIndex());
            return true;
        }
        if (pageFocused) {
            if (ctrl && (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD)) {
                backend.setZoom(backend.zoom() * 1.1);
                return true;
            }
            if (ctrl && (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT)) {
                backend.setZoom(backend.zoom() / 1.1);
                return true;
            }
            if (ctrl && key == GLFW.GLFW_KEY_0) {
                backend.setZoom(1.0);
                return true;
            }
            backend.key(model.activeTab(), key, scancode, modifiers, true);
            return true;
        }
        StringBuilder sb = current();
        if (sb == null) {
            return false;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            String clip = MinecraftClient.getInstance().keyboard.getClipboard();
            if (clip != null) {
                append(sb, clip.replace("\n", "").replace("\r", ""));
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_A) {
            sb.setLength(0);
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (edit == Edit.URL) {
                    submitUrl();
                } else if (edit == Edit.QUICK_TITLE) {
                    edit = Edit.QUICK_URL;
                } else {
                    commitQuickEdit();
                }
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (sb.length() > 0) {
                    sb.setLength(sb.length() - 1);
                }
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (edit == Edit.QUICK_TITLE) {
                    edit = Edit.QUICK_URL;
                } else if (edit == Edit.QUICK_URL) {
                    edit = Edit.QUICK_TITLE;
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(String text) {
        if (pageFocused) {
            text.codePoints().forEach(cp -> backend.typed(model.activeTab(), cp));
            return true;
        }
        StringBuilder sb = current();
        if (sb == null) {
            return false;
        }
        append(sb, text);
        return true;
    }

    private static void append(StringBuilder sb, String s) {
        for (int i = 0; i < s.length() && sb.length() < UrlUtil.MAX_LENGTH; i++) {
            char ch = s.charAt(i);
            if (!Character.isISOControl(ch)) {
                sb.append(ch);
            }
        }
    }

    @Override
    public void onBlur() {
        edit = Edit.NONE;
        if (pageFocused || pagePressed) {
            pageFocused = false;
            pagePressed = false;
            backend.setFocus(model.activeTab(), false);
        }
    }

    @Override
    public void onClose() {
        edit = Edit.NONE;
        persist();
    }

    /** Освобождает вкладки и движок (выход из игры). */
    public void shutdown() {
        persist();
        for (BrowserTab t : model.tabs()) {
            backend.closeTab(t);
        }
        backend.shutdown();
    }
}
