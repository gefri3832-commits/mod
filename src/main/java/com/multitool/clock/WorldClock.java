package com.multitool.clock;

/** Одни из отображаемых часов: название и пояс (пустая строка — пояс системы). */
public final class WorldClock {
    public String name;
    public String zone;

    public WorldClock(String name, String zone) {
        this.name = name;
        this.zone = zone;
    }
}
