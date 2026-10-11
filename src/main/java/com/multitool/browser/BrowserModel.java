package com.multitool.browser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Данные браузера без привязки к Minecraft: вкладки, история, избранное и быстрые ссылки
 * стартовой страницы. Сохраняется в текстовый файл (UTF-8, поля через табуляцию).
 */
public final class BrowserModel {
    public static final int MAX_TABS = 12;
    public static final int MAX_HISTORY = 500;
    public static final int MAX_FAVORITES = 100;
    public static final int MAX_QUICK = 8;

    private final List<BrowserTab> tabs = new ArrayList<>();
    private final List<Link> history = new ArrayList<>();
    private final List<Link> favorites = new ArrayList<>();
    private final List<Link> quick = new ArrayList<>();
    private int active;
    /** Индекс поисковой системы в {@link UrlUtil#ENGINE_NAMES}. */
    public int searchEngine;

    public BrowserModel() {
        resetQuickLinks();
        tabs.add(new BrowserTab());
    }

    public void resetQuickLinks() {
        quick.clear();
        quick.add(new Link("Google", "https://www.google.com"));
        quick.add(new Link("YouTube", "https://www.youtube.com"));
        quick.add(new Link("Minecraft Wiki", "https://minecraft.wiki"));
        quick.add(new Link("Modrinth", "https://modrinth.com"));
        quick.add(new Link("CurseForge", "https://www.curseforge.com/minecraft"));
    }

    // ---- вкладки ----

    public List<BrowserTab> tabs() {
        return tabs;
    }

    public int activeIndex() {
        return active;
    }

    public BrowserTab activeTab() {
        return tabs.get(active);
    }

    /** Открывает новую вкладку и делает её активной; null, если достигнут лимит. */
    public BrowserTab newTab() {
        if (tabs.size() >= MAX_TABS) {
            return null;
        }
        BrowserTab t = new BrowserTab();
        tabs.add(t);
        active = tabs.size() - 1;
        return t;
    }

    public void select(int index) {
        if (index >= 0 && index < tabs.size()) {
            active = index;
        }
    }

    /** Закрывает вкладку и возвращает её; последняя вкладка не закрывается, а сбрасывается на стартовую. */
    public BrowserTab closeTab(int index) {
        if (index < 0 || index >= tabs.size()) {
            return null;
        }
        BrowserTab closed = tabs.get(index);
        if (tabs.size() == 1) {
            tabs.set(0, new BrowserTab());
            active = 0;
            return closed;
        }
        tabs.remove(index);
        if (active > index || active >= tabs.size()) {
            active = Math.max(0, active - 1);
        }
        return closed;
    }

    // ---- история ----

    public List<Link> history() {
        return history;
    }

    /** Добавляет посещение; повторная запись подряд того же адреса обновляет заголовок. */
    public void addHistory(String title, String url, boolean enabled) {
        if (!enabled || !UrlUtil.isSafe(url)) {
            return;
        }
        String t = title == null || title.isBlank() ? UrlUtil.hostOf(url) : title.trim();
        if (!history.isEmpty() && history.get(history.size() - 1).url().equals(url)) {
            history.set(history.size() - 1, new Link(t, url));
            return;
        }
        history.add(new Link(t, url));
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }

    public void clearHistory() {
        history.clear();
    }

    // ---- избранное ----

    public List<Link> favorites() {
        return favorites;
    }

    public boolean isFavorite(String url) {
        return indexOfFavorite(url) >= 0;
    }

    /** Добавляет или убирает адрес из избранного; возвращает true, если теперь он в избранном. */
    public boolean toggleFavorite(String title, String url) {
        int i = indexOfFavorite(url);
        if (i >= 0) {
            favorites.remove(i);
            return false;
        }
        if (!UrlUtil.isSafe(url) || favorites.size() >= MAX_FAVORITES) {
            return false;
        }
        favorites.add(new Link(title == null || title.isBlank() ? UrlUtil.hostOf(url) : title.trim(), url));
        return true;
    }

    private int indexOfFavorite(String url) {
        for (int i = 0; i < favorites.size(); i++) {
            if (favorites.get(i).url().equals(url)) {
                return i;
            }
        }
        return -1;
    }

    // ---- быстрые ссылки ----

    public List<Link> quickLinks() {
        return quick;
    }

    /** Заменяет быструю ссылку; возвращает false, если адрес небезопасен. */
    public boolean setQuickLink(int index, String title, String url) {
        if (index < 0 || index >= quick.size() || !UrlUtil.isSafe(url)) {
            return false;
        }
        String t = title == null || title.isBlank() ? UrlUtil.hostOf(url) : title.trim();
        quick.set(index, new Link(t, url));
        return true;
    }

    public boolean addQuickLink(String title, String url) {
        if (quick.size() >= MAX_QUICK || !UrlUtil.isSafe(url)) {
            return false;
        }
        quick.add(new Link(title == null || title.isBlank() ? UrlUtil.hostOf(url) : title.trim(), url));
        return true;
    }

    public void removeQuickLink(int index) {
        if (index >= 0 && index < quick.size()) {
            quick.remove(index);
        }
    }

    // ---- сохранение ----

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "");
    }

    private static String unesc(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public void save(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("S\t" + searchEngine + "\t-");
        for (Link l : quick) {
            lines.add("Q\t" + esc(l.title()) + "\t" + esc(l.url()));
        }
        for (Link l : favorites) {
            lines.add("F\t" + esc(l.title()) + "\t" + esc(l.url()));
        }
        for (Link l : history) {
            lines.add("H\t" + esc(l.title()) + "\t" + esc(l.url()));
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /** Читает файл; повреждённые и небезопасные строки пропускаются. Файл без быстрых ссылок оставляет стандартные. */
    public void load(Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return;
        }
        List<Link> q = new ArrayList<>();
        List<Link> f = new ArrayList<>();
        List<Link> h = new ArrayList<>();
        for (String line : lines) {
            String[] p = line.split("\t", -1);
            if (p.length == 3 && p[0].equals("S")) {
                try {
                    searchEngine = Math.max(0, Math.min(UrlUtil.ENGINE_NAMES.length - 1, Integer.parseInt(p[1].trim())));
                } catch (NumberFormatException ignored) {
                    searchEngine = 0;
                }
                continue;
            }
            if (p.length != 3 || !UrlUtil.isSafe(unesc(p[2]))) {
                continue;
            }
            Link l = new Link(unesc(p[1]), unesc(p[2]));
            switch (p[0]) {
                case "Q" -> {
                    if (q.size() < MAX_QUICK) {
                        q.add(l);
                    }
                }
                case "F" -> {
                    if (f.size() < MAX_FAVORITES) {
                        f.add(l);
                    }
                }
                case "H" -> h.add(l);
                default -> {
                }
            }
        }
        if (!q.isEmpty()) {
            quick.clear();
            quick.addAll(q);
        }
        favorites.clear();
        favorites.addAll(f);
        history.clear();
        history.addAll(h.size() > MAX_HISTORY ? h.subList(h.size() - MAX_HISTORY, h.size()) : h);
    }
}
