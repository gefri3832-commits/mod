package com.multitool.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Настройки мода. Хранятся в config/multitool/config.properties. Любое значение при чтении
 * проверяется и приводится к допустимому диапазону, поэтому испорченный файл не ломает игру.
 */
public final class ModConfig {
    public static final ModConfig INSTANCE = new ModConfig();

    public static final String[] ACCENT_NAMES = {"Blue", "Green", "Orange", "Pink", "Purple"};
    public static final int[] ACCENT_COLORS = {0xFF4C8DFF, 0xFF3DBE7A, 0xFFF28B30, 0xFFE8589B, 0xFF9B6BFF};
    public static final String[] SOUND_NAMES = {"Bell", "Beep", "Chime"};

    // ---- интерфейс ----
    public boolean lightTheme;
    public int opacity = 93;       // 40..100, %
    public int accent;             // индекс в ACCENT_COLORS
    public int cornerRadius = 6;   // 0..10
    public boolean shadows = true;
    public boolean borders;

    // ---- часы и уведомления ----
    public boolean clock24h = true;
    public boolean showSeconds = true;
    public boolean showDate = true;
    public int volume = 70;        // 0..100, %
    public int sound;              // индекс в SOUND_NAMES
    public int toastSeconds = 8;   // 3..30

    private Path file;
    private final List<Runnable> listeners = new ArrayList<>();

    ModConfig() {
    }

    /** Новый независимый экземпляр со значениями по умолчанию (для тестов). */
    public static ModConfig create() {
        return new ModConfig();
    }

    public void setFile(Path file) {
        this.file = file;
    }

    /** Слушатель вызывается после каждого изменения настроек (например, чтобы пересчитать цвета). */
    public void addListener(Runnable r) {
        listeners.add(r);
    }

    /** Приводит значения к допустимым диапазонам. */
    public void validate() {
        opacity = clamp(opacity, 40, 100);
        accent = clamp(accent, 0, ACCENT_COLORS.length - 1);
        cornerRadius = clamp(cornerRadius, 0, 10);
        volume = clamp(volume, 0, 100);
        sound = clamp(sound, 0, SOUND_NAMES.length - 1);
        toastSeconds = clamp(toastSeconds, 3, 30);
    }

    /** Вызывается после любого изменения: проверка, сохранение, уведомление слушателей. */
    public void changed() {
        validate();
        save();
        for (Runnable r : listeners) {
            r.run();
        }
    }

    public void resetAppearance() {
        ModConfig d = new ModConfig();
        lightTheme = d.lightTheme;
        opacity = d.opacity;
        accent = d.accent;
        cornerRadius = d.cornerRadius;
        shadows = d.shadows;
        borders = d.borders;
        changed();
    }

    public void resetClock() {
        ModConfig d = new ModConfig();
        clock24h = d.clock24h;
        showSeconds = d.showSeconds;
        showDate = d.showDate;
        volume = d.volume;
        sound = d.sound;
        toastSeconds = d.toastSeconds;
        changed();
    }

    public int accentColor() {
        return ACCENT_COLORS[clamp(accent, 0, ACCENT_COLORS.length - 1)];
    }

    // ---- файл ----

    public void load() {
        if (file == null || !Files.isRegularFile(file)) {
            validate();
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException | IllegalArgumentException e) {
            validate();
            return;
        }
        lightTheme = bool(p, "ui.lightTheme", lightTheme);
        opacity = num(p, "ui.opacity", opacity);
        accent = num(p, "ui.accent", accent);
        cornerRadius = num(p, "ui.cornerRadius", cornerRadius);
        shadows = bool(p, "ui.shadows", shadows);
        borders = bool(p, "ui.borders", borders);
        clock24h = bool(p, "clock.24h", clock24h);
        showSeconds = bool(p, "clock.seconds", showSeconds);
        showDate = bool(p, "clock.date", showDate);
        volume = num(p, "clock.volume", volume);
        sound = num(p, "clock.sound", sound);
        toastSeconds = num(p, "clock.toastSeconds", toastSeconds);
        validate();
    }

    /** @return true, если файл записан */
    public boolean save() {
        if (file == null) {
            return false;
        }
        Properties p = new Properties();
        p.setProperty("ui.lightTheme", String.valueOf(lightTheme));
        p.setProperty("ui.opacity", String.valueOf(opacity));
        p.setProperty("ui.accent", String.valueOf(accent));
        p.setProperty("ui.cornerRadius", String.valueOf(cornerRadius));
        p.setProperty("ui.shadows", String.valueOf(shadows));
        p.setProperty("ui.borders", String.valueOf(borders));
        p.setProperty("clock.24h", String.valueOf(clock24h));
        p.setProperty("clock.seconds", String.valueOf(showSeconds));
        p.setProperty("clock.date", String.valueOf(showDate));
        p.setProperty("clock.volume", String.valueOf(volume));
        p.setProperty("clock.sound", String.valueOf(sound));
        p.setProperty("clock.toastSeconds", String.valueOf(toastSeconds));
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "MultiTool Overlay: settings");
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        if (v == null) {
            return def;
        }
        v = v.trim();
        if (v.equalsIgnoreCase("true")) {
            return true;
        }
        if (v.equalsIgnoreCase("false")) {
            return false;
        }
        return def;
    }

    private static int num(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
