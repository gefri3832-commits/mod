package com.multitool.browser;

import java.nio.file.Files;
import java.nio.file.Path;

/** Тесты браузерной логики: разбор адресов, вкладки, история, избранное, сохранение. */
public final class BrowserTests {
    private static int failed;
    private static int passed;

    private static void check(String n, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + n);
        }
    }

    public static void main(String[] args) throws Exception {
        // адреса
        check("https kept", "https://example.com/a?b=1".equals(UrlUtil.resolve("https://example.com/a?b=1", 0)));
        check("host gets https", "https://example.com".equals(UrlUtil.resolve("example.com", 0)));
        check("localhost port", "https://localhost:8080".equals(UrlUtil.resolve("localhost:8080", 0)));
        check("host with port", "https://example.com:81/x".equals(UrlUtil.resolve("example.com:81/x", 0)));
        check("search", "https://www.google.com/search?q=hello+world".equals(UrlUtil.resolve("hello world", 0)));
        check("search ddg", UrlUtil.resolve("creeper", 1).startsWith("https://duckduckgo.com/?q="));
        check("javascript blocked", UrlUtil.resolve("javascript:alert(1)", 0) == null);
        check("file blocked", UrlUtil.resolve("file:///etc/passwd", 0) == null);
        check("data blocked", UrlUtil.resolve("data:text/html,<b>x</b>", 0) == null);
        check("userinfo blocked", UrlUtil.resolve("https://user:pw@example.com", 0) == null);
        check("empty", UrlUtil.resolve("   ", 0) == null && UrlUtil.resolve(null, 0) == null);
        check("control chars", UrlUtil.resolve("exa\nmple.com", 0) == null);
        check("too long", UrlUtil.resolve("https://a.com/" + "x".repeat(3000), 0) == null);
        check("hostOf", "example.com".equals(UrlUtil.hostOf("https://www.example.com/p")));

        // вкладки
        BrowserModel m = new BrowserModel();
        check("starts with one tab", m.tabs().size() == 1 && m.activeTab().isStartPage());
        m.newTab();
        m.newTab();
        check("active is last", m.activeIndex() == 2 && m.tabs().size() == 3);
        m.closeTab(2);
        check("active after close", m.activeIndex() == 1);
        m.select(0);
        m.closeTab(1);
        check("active stays", m.activeIndex() == 0 && m.tabs().size() == 1);
        BrowserTab only = m.activeTab();
        only.url = "https://a.com";
        m.closeTab(0);
        check("last tab resets", m.tabs().size() == 1 && m.activeTab().isStartPage());
        for (int i = 0; i < 30; i++) {
            m.newTab();
        }
        check("tab limit", m.tabs().size() == BrowserModel.MAX_TABS);

        // история и избранное
        m.addHistory("A", "https://a.com", true);
        m.addHistory("A2", "https://a.com", true);
        m.addHistory("B", "https://b.com", true);
        m.addHistory("off", "https://c.com", false);
        m.addHistory("bad", "javascript:1", true);
        check("history dedupe", m.history().size() == 2 && m.history().get(0).title().equals("A2"));
        check("favorite add", m.toggleFavorite("A", "https://a.com") && m.isFavorite("https://a.com"));
        check("favorite remove", !m.toggleFavorite("A", "https://a.com") && !m.isFavorite("https://a.com"));
        check("quick defaults", m.quickLinks().size() == 5);
        check("quick edit", m.setQuickLink(0, "Bing", "https://www.bing.com") && m.quickLinks().get(0).title().equals("Bing"));
        check("quick edit unsafe", !m.setQuickLink(0, "x", "javascript:1"));

        // сохранение
        Path f = Files.createTempFile("browser", ".txt");
        m.toggleFavorite("Tab\tTitle", "https://t.com/x");
        m.save(f);
        BrowserModel m2 = new BrowserModel();
        m2.load(f);
        check("load history", m2.history().size() == 2);
        check("load favorite with tab", m2.favorites().size() == 1 && m2.favorites().get(0).title().equals("Tab\tTitle"));
        check("load quick", m2.quickLinks().get(0).title().equals("Bing"));
        Files.writeString(f, "garbage\nH\tonly\nF\tx\tjavascript:1\nH\tOk\thttps://ok.com\n");
        BrowserModel m3 = new BrowserModel();
        m3.load(f);
        check("corrupt file", m3.history().size() == 1 && m3.favorites().isEmpty() && m3.quickLinks().size() == 5);
        Files.deleteIfExists(f);

        System.out.println("browser tests: passed " + passed + ", failed " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }
}
