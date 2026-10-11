package com.multitool.browser;

/** Состояние одной вкладки. Пустой url означает стартовую страницу. */
public final class BrowserTab {
    public String url = "";
    public String title = "New tab";
    public boolean loading;
    public boolean canGoBack;
    public boolean canGoForward;
    public String error = "";

    public boolean isStartPage() {
        return url.isEmpty();
    }
}
