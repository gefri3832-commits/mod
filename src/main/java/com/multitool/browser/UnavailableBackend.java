package com.multitool.browser;

/** Заглушка на случай, когда движок не установлен: честно сообщает причину и ничего не загружает. */
public final class UnavailableBackend implements BrowserBackend {
    private final String reason;

    public UnavailableBackend(String reason) {
        this.reason = reason;
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public String statusMessage() {
        return reason;
    }

    @Override
    public void navigate(BrowserTab tab, String url) {
        tab.error = reason;
    }

    @Override
    public void back(BrowserTab tab) {
    }

    @Override
    public void forward(BrowserTab tab) {
    }

    @Override
    public void reload(BrowserTab tab) {
    }

    @Override
    public void stop(BrowserTab tab) {
    }

    @Override
    public void closeTab(BrowserTab tab) {
    }

    @Override
    public void shutdown() {
    }
}
