package com.multitool.config;

import java.nio.file.Files;
import java.nio.file.Path;

/** Тесты настроек: значения по умолчанию, сохранение и загрузка, защита от некорректных значений. */
public final class ConfigTests {
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
        Path dir = Files.createTempDirectory("mtcfg");
        Path f = dir.resolve("a").resolve("config.properties");

        ModConfig d = ModConfig.create();
        check("defaults", !d.lightTheme && d.opacity == 93 && d.cornerRadius == 6 && d.clock24h && d.volume == 70);

        ModConfig a = ModConfig.create();
        a.setFile(f);
        a.lightTheme = true;
        a.opacity = 60;
        a.accent = 3;
        a.cornerRadius = 2;
        a.shadows = false;
        a.borders = true;
        a.clock24h = false;
        a.showSeconds = false;
        a.showDate = false;
        a.volume = 20;
        a.sound = 2;
        a.toastSeconds = 12;
        check("save", a.save() && Files.isRegularFile(f));

        ModConfig b = ModConfig.create();
        b.setFile(f);
        b.load();
        check("roundtrip", b.lightTheme && b.opacity == 60 && b.accent == 3 && b.cornerRadius == 2 && !b.shadows
                && b.borders && !b.clock24h && !b.showSeconds && !b.showDate && b.volume == 20 && b.sound == 2
                && b.toastSeconds == 12);

        Files.writeString(f, "ui.opacity=999\nui.accent=-4\nui.cornerRadius=abc\nclock.volume=-50\nclock.sound=77\n"
                + "clock.toastSeconds=0\nui.lightTheme=maybe\nclock.24h=FALSE\nunknown.key=1\n");
        ModConfig c = ModConfig.create();
        c.setFile(f);
        c.load();
        check("opacity clamped", c.opacity == 100);
        check("accent clamped", c.accent == 0);
        check("garbage number keeps default", c.cornerRadius == 6);
        check("volume clamped", c.volume == 0);
        check("sound clamped", c.sound == ModConfig.SOUND_NAMES.length - 1);
        check("toast seconds clamped", c.toastSeconds == 3);
        check("bad boolean keeps default", !c.lightTheme);
        check("boolean case-insensitive", !c.clock24h);

        Files.write(f, new byte[]{0, (byte) 0xFF, '\\', 'u', 'x'});
        ModConfig e = ModConfig.create();
        e.setFile(f);
        e.load();
        check("binary garbage keeps defaults", e.opacity == 93 && e.cornerRadius == 6);

        ModConfig g = ModConfig.create();
        g.setFile(dir.resolve("missing").resolve("x.properties"));
        g.load();
        check("missing file ok", g.opacity == 93);

        int[] calls = {0};
        ModConfig h = ModConfig.create();
        h.setFile(dir.resolve("h.properties"));
        h.addListener(() -> calls[0]++);
        h.opacity = 5000;
        h.cornerRadius = -3;
        h.changed();
        check("changed validates, saves, notifies", h.opacity == 100 && h.cornerRadius == 0 && calls[0] == 1
                && Files.isRegularFile(dir.resolve("h.properties")));
        h.lightTheme = true;
        h.accent = 4;
        h.shadows = false;
        h.volume = 5;
        h.resetAppearance();
        check("reset appearance", !h.lightTheme && h.accent == 0 && h.shadows && h.opacity == 93 && h.cornerRadius == 6
                && h.volume == 5 && calls[0] == 2);
        h.resetClock();
        check("reset clock", h.volume == 70 && h.clock24h && calls[0] == 3);

        ModConfig nf = ModConfig.create();
        check("save without file is a no-op", !nf.save());
        check("accent color in range", ModConfig.create().accentColor() == ModConfig.ACCENT_COLORS[0]);

        System.out.println("Passed: " + passed + ", failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }
}
