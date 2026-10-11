package com.multitool.browser;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Безопасная обработка адресов из строки браузера. Разрешены только http и https:
 * javascript:, file:, data: и прочие схемы отклоняются.
 */
public final class UrlUtil {
    public static final String[] ENGINE_NAMES = {"Google", "DuckDuckGo", "Bing"};
    private static final String[] ENGINE_TEMPLATES = {
            "https://www.google.com/search?q=%s",
            "https://duckduckgo.com/?q=%s",
            "https://www.bing.com/search?q=%s"
    };
    public static final int MAX_LENGTH = 2048;

    private UrlUtil() {
    }

    public static String searchUrl(int engine, String query) {
        int e = Math.max(0, Math.min(ENGINE_TEMPLATES.length - 1, engine));
        return ENGINE_TEMPLATES[e].formatted(URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
    }

    /**
     * Превращает ввод пользователя в адрес для загрузки: готовый http(s)-адрес, домен без схемы
     * (добавляется https://) или поисковый запрос. Возвращает null, если ввод пустой или небезопасный.
     */
    public static String resolve(String input, int engine) {
        if (input == null) {
            return null;
        }
        String s = input.trim();
        if (s.isEmpty() || s.length() > MAX_LENGTH || hasControlChars(s)) {
            return null;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return isSafe(s) ? s : null;
        }
        if (hasScheme(s)) {
            return null;
        }
        if (looksLikeHost(s)) {
            String url = "https://" + s;
            return isSafe(url) ? url : null;
        }
        return searchUrl(engine, s);
    }

    /** true, если адрес — http(s) с непустым хостом и без логина/пароля в адресе. */
    public static boolean isSafe(String url) {
        if (url == null || url.length() > MAX_LENGTH) {
            return false;
        }
        try {
            URI u = new URI(url);
            String scheme = u.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && u.getHost() != null && !u.getHost().isEmpty() && u.getUserInfo() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean hasControlChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isISOControl(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** Есть ли в начале «схема:» (буквы, затем двоеточие), но это не host:port. */
    private static boolean hasScheme(String s) {
        int colon = s.indexOf(':');
        if (colon <= 0 || s.indexOf(' ') >= 0 && s.indexOf(' ') < colon) {
            return false;
        }
        for (int i = 0; i < colon; i++) {
            char ch = s.charAt(i);
            if (!(Character.isLetter(ch) || (i > 0 && (Character.isDigit(ch) || ch == '+' || ch == '-' || ch == '.')))) {
                return false;
            }
        }
        // «localhost:8080» и «example.com:80» — это хост с портом, а не схема
        String rest = s.substring(colon + 1);
        boolean portOnly = !rest.isEmpty() && Character.isDigit(rest.charAt(0));
        return !portOnly;
    }

    private static boolean looksLikeHost(String s) {
        if (s.indexOf(' ') >= 0) {
            return false;
        }
        String host = s;
        int cut = host.length();
        for (char ch : new char[]{'/', '?', '#'}) {
            int i = host.indexOf(ch);
            if (i >= 0) {
                cut = Math.min(cut, i);
            }
        }
        host = host.substring(0, cut);
        int colon = host.lastIndexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        return host.equalsIgnoreCase("localhost") || (host.contains(".") && !host.startsWith(".") && !host.endsWith("."));
    }

    /** Короткое имя сайта для вкладки: хост без «www.». */
    public static String hostOf(String url) {
        try {
            String h = new URI(url).getHost();
            if (h == null) {
                return url;
            }
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (URISyntaxException e) {
            return url;
        }
    }
}
