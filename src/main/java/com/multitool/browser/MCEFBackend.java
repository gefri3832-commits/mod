package com.multitool.browser;

import com.multitool.MultiToolClient;
import com.multitool.overlay.Gfx;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.util.Identifier;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Встроенный Chromium через MCEF Modern. Движок стартует только после первого действия пользователя
 * (переход по адресу), потому что при первом запуске MCEF скачивает JCEF. Все вызовы Minecraft-стороны
 * выполняются в главном потоке; обработчики CEF только переносят изменения в главный поток.
 */
public final class MCEFBackend implements BrowserBackend {
    private enum State { IDLE, STARTING, READY, FAILED }

    private static final double ZOOM_STEP = Math.log(1.2);

    private static final class Page {
        final MCEFBrowser browser;
        final CefBrowser cef;
        final BrowserSurface surface;
        int vw;
        int vh;
        double appliedZoom = -1;

        Page(MCEFBrowser browser, BrowserSurface surface) {
            this.browser = browser;
            this.cef = browser.getCefBrowser();
            this.surface = surface;
        }
    }

    private final Map<BrowserTab, Page> pages = new IdentityHashMap<>();
    private final Map<BrowserTab, Boolean> failed = new IdentityHashMap<>();
    private final Map<CefBrowser, BrowserTab> owners = new ConcurrentHashMap<>();
    private volatile State state = State.IDLE;
    private volatile String failure = "";
    private MCEFApi api;
    private MCEFApi.Initialization init;
    private boolean handlersInstalled;
    private Listener listener;
    private double zoom = 1.0;
    private int counter;

    // ---- состояние движка ----

    @Override
    public boolean isReady() {
        return state == State.READY;
    }

    @Override
    public String statusMessage() {
        return switch (state) {
            case IDLE -> "Chromium has not been started yet. Enter an address to start it. "
                    + "The first start downloads the browser engine (JCEF), which can take a while.";
            case STARTING -> {
                MCEFApi.Initialization i = init;
                if (i == null) {
                    yield "Starting Chromium...";
                }
                float p = i.getPercentage();
                yield "Starting Chromium: " + i.getStage().name().toLowerCase()
                        + (p >= 0 ? " " + Math.round(p) + "%" : "") + ". Please wait.";
            }
            case FAILED -> "Chromium failed to start: " + failure + ". Restart the game to try again.";
            case READY -> "";
        };
    }

    private void start() {
        if (state != State.IDLE) {
            return;
        }
        state = State.STARTING;
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            init = MCEFApi.initialize();
            init.getFuture().whenComplete((a, e) -> mc.execute(() -> {
                if (e != null || a == null) {
                    Throwable root = e;
                    while (root != null && root.getCause() != null) {
                        root = root.getCause();
                    }
                    failure = root == null ? "unknown error" : String.valueOf(root.getMessage());
                    state = State.FAILED;
                    MultiToolClient.LOGGER.error("MCEF initialization failed", e);
                } else {
                    api = a;
                    state = State.READY;
                }
            }));
        } catch (Throwable t) {
            failure = String.valueOf(t.getMessage());
            state = State.FAILED;
            MultiToolClient.LOGGER.error("MCEF initialization failed", t);
        }
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // ---- навигация ----

    @Override
    public void navigate(BrowserTab tab, String url) {
        if (!UrlUtil.isSafe(url)) {
            tab.error = "Unsupported or invalid address (only http/https)";
            return;
        }
        failed.remove(tab);
        start();
        Page p = pages.get(tab);
        if (p != null) {
            p.cef.loadURL(url);
        }
        // иначе страница будет создана при ближайшей отрисовке, когда движок запустится
    }

    @Override
    public void back(BrowserTab tab) {
        Page p = pages.get(tab);
        if (p != null) {
            p.cef.goBack();
        }
    }

    @Override
    public void forward(BrowserTab tab) {
        Page p = pages.get(tab);
        if (p != null) {
            p.cef.goForward();
        }
    }

    @Override
    public void reload(BrowserTab tab) {
        failed.remove(tab);
        tab.error = "";
        Page p = pages.get(tab);
        if (p != null) {
            p.cef.reload();
        }
    }

    @Override
    public void stop(BrowserTab tab) {
        Page p = pages.get(tab);
        if (p != null) {
            p.cef.stopLoad();
        }
    }

    // ---- страницы ----

    private static int guiScale() {
        return Math.max(1, (int) MinecraftClient.getInstance().getWindow().getScaleFactor());
    }

    private Page create(BrowserTab tab, int w, int h) {
        if (failed.containsKey(tab) || !UrlUtil.isSafe(tab.url)) {
            return null;
        }
        try {
            int s = guiScale();
            MCEFBrowser b = api.createBrowser(tab.url, false);
            b.resize(w * s, h * s);
            Page p = new Page(b, new BrowserSurface(Identifier.of(MultiToolClient.MOD_ID, "browser/tab_" + counter++)));
            p.vw = w * s;
            p.vh = h * s;
            pages.put(tab, p);
            owners.put(p.cef, tab);
            installHandlers(p.cef.getClient());
            return p;
        } catch (Throwable t) {
            failed.put(tab, Boolean.TRUE);
            tab.error = "Could not create browser: " + t.getMessage();
            MultiToolClient.LOGGER.error("Failed to create MCEF browser", t);
            return null;
        }
    }

    @Override
    public boolean renderPage(DrawContext c, BrowserTab tab, int x, int y, int w, int h) {
        if (state != State.READY || w < 8 || h < 8) {
            return false;
        }
        Page p = pages.get(tab);
        if (p == null) {
            p = create(tab, w, h);
            if (p == null) {
                return false;
            }
        }
        int s = guiScale();
        if (p.vw != w * s || p.vh != h * s) {
            p.vw = w * s;
            p.vh = h * s;
            p.browser.resize(p.vw, p.vh);
        }
        // Окно рендерится в реальных пикселях (w*s), поэтому масштаб 1.0 = обычный 100% браузера:
        // сайт видит ширину окна в настоящих пикселях и верстается как на компьютере, а не как на телефоне.
        double wantZoom = zoom;
        if (p.appliedZoom != wantZoom) {
            p.cef.setZoomLevel(Math.log(wantZoom) / ZOOM_STEP);
            p.appliedZoom = wantZoom;
        }
        tab.canGoBack = p.cef.canGoBack();
        tab.canGoForward = p.cef.canGoForward();
        tab.loading = p.cef.isLoading();
        if (!p.surface.draw(c, p.browser, x, y, w, h)) {
            Gfx.rrect(c, x, y, x + w, y + h, 4, Gfx.DISPLAY);
        }
        return true;
    }

    @Override
    public void closeTab(BrowserTab tab) {
        failed.remove(tab);
        Page p = pages.remove(tab);
        if (p != null) {
            dispose(p);
        }
    }

    private void dispose(Page p) {
        owners.remove(p.cef);
        try {
            p.surface.release();
            p.browser.close();
        } catch (Throwable t) {
            MultiToolClient.LOGGER.warn("Failed to close browser: {}", t.toString());
        }
    }

    @Override
    public void shutdown() {
        for (Page p : new ArrayList<>(pages.values())) {
            dispose(p);
        }
        pages.clear();
        // сам CefApp закрывает MCEF Modern при остановке клиента
    }

    // ---- ввод ----

    @Override
    public void mouseMoved(BrowserTab tab, double lx, double ly) {
        Page p = pages.get(tab);
        if (p != null) {
            int s = guiScale();
            p.browser.onMouseMoved((int) (lx * s), (int) (ly * s));
        }
    }

    @Override
    public void mouseButton(BrowserTab tab, double lx, double ly, int button, boolean doubled, boolean down) {
        Page p = pages.get(tab);
        if (p == null) {
            return;
        }
        int s = guiScale();
        Click click = new Click(lx * s, ly * s, new MouseInput(button, 0));
        if (down) {
            p.browser.setFocus(true);
            p.browser.onMouseClicked(click, doubled);
        } else {
            p.browser.onMouseReleased(click);
        }
    }

    @Override
    public void scroll(BrowserTab tab, double lx, double ly, double amount) {
        Page p = pages.get(tab);
        if (p != null) {
            int s = guiScale();
            // в AWT/MCEF положительное значение — прокрутка вниз
            p.browser.onMouseScrolled((int) (lx * s), (int) (ly * s), -amount);
        }
    }

    @Override
    public void key(BrowserTab tab, int key, int scancode, int modifiers, boolean down) {
        Page p = pages.get(tab);
        if (p == null) {
            return;
        }
        KeyInput input = new KeyInput(key, scancode, modifiers);
        if (down) {
            p.browser.onKeyPressed(input);
        } else {
            p.browser.onKeyReleased(input);
        }
    }

    @Override
    public void typed(BrowserTab tab, int codePoint) {
        Page p = pages.get(tab);
        if (p != null) {
            p.browser.onCharTyped(new CharInput(codePoint, 0));
        }
    }

    @Override
    public void setFocus(BrowserTab tab, boolean focused) {
        Page p = pages.get(tab);
        if (p != null) {
            p.browser.setFocus(focused);
        }
    }

    @Override
    public void setZoom(double factor) {
        this.zoom = Math.max(0.25, Math.min(5.0, factor));
    }

    @Override
    public double zoom() {
        return zoom;
    }

    // ---- события CEF (приходят не из главного потока) ----

    private void onMain(Runnable r) {
        MinecraftClient.getInstance().execute(r);
    }

    private void installHandlers(CefClient client) {
        if (handlersInstalled || client == null) {
            return;
        }
        handlersInstalled = true;
        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
                BrowserTab t = owners.get(browser);
                if (t == null || frame == null || !frame.isMain() || !UrlUtil.isSafe(url)) {
                    return;
                }
                onMain(() -> {
                    t.url = url;
                    t.error = "";
                    Page p = pages.get(t);
                    if (p != null) {
                        p.appliedZoom = -1;
                    }
                    if (listener != null) {
                        listener.onNavigated(t, url, t.title);
                    }
                });
            }

            @Override
            public void onTitleChange(CefBrowser browser, String title) {
                BrowserTab t = owners.get(browser);
                if (t == null || title == null || title.isBlank()) {
                    return;
                }
                String clean = title.length() > 120 ? title.substring(0, 120) : title;
                onMain(() -> {
                    t.title = clean;
                    if (listener != null && !t.url.isEmpty()) {
                        listener.onNavigated(t, t.url, clean);
                    }
                });
            }
        });
        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadError(CefBrowser browser, CefFrame frame, CefLoadHandler.ErrorCode errorCode,
                                    String errorText, String failedUrl) {
                BrowserTab t = owners.get(browser);
                if (t == null || frame == null || !frame.isMain() || errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED) {
                    return;
                }
                String msg = (errorText == null || errorText.isBlank() ? errorCode.name() : errorText)
                        + " - " + failedUrl;
                onMain(() -> t.error = msg);
            }
        });
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String targetUrl, String targetFrameName) {
                // новые окна не создаём: ссылка открывается в этой же вкладке
                BrowserTab t = owners.get(browser);
                if (t != null && UrlUtil.isSafe(targetUrl)) {
                    onMain(() -> {
                        Page p = pages.get(t);
                        if (p != null) {
                            p.cef.loadURL(targetUrl);
                        }
                    });
                }
                return true;
            }
        });
    }
}
