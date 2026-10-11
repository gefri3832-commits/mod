package com.multitool.clock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/** Секундомер на монотонном времени с кругами. */
public final class Stopwatch {
    public record Lap(int number, long lapMs, long totalMs) {
    }

    private static final long NANOS_PER_MS = 1_000_000L;
    private static final int MAX_LAPS = 99;

    private final LongSupplier nanos;
    private boolean running;
    private long accumulatedNanos;
    private long startNanos;
    private long lastLapTotalMs;
    private final List<Lap> laps = new ArrayList<>();

    public Stopwatch(LongSupplier nanos) {
        this.nanos = nanos;
    }

    public boolean running() {
        return running;
    }

    public void start() {
        if (!running) {
            startNanos = nanos.getAsLong();
            running = true;
        }
    }

    public void pause() {
        if (running) {
            accumulatedNanos += nanos.getAsLong() - startNanos;
            running = false;
        }
    }

    public void toggle() {
        if (running) {
            pause();
        } else {
            start();
        }
    }

    public void reset() {
        running = false;
        accumulatedNanos = 0;
        lastLapTotalMs = 0;
        laps.clear();
    }

    public long elapsedMs() {
        long n = accumulatedNanos + (running ? nanos.getAsLong() - startNanos : 0);
        return n / NANOS_PER_MS;
    }

    /** Фиксирует круг; работает только пока секундомер идёт. */
    public boolean lap() {
        if (!running || laps.size() >= MAX_LAPS) {
            return false;
        }
        long total = elapsedMs();
        laps.add(0, new Lap(laps.size() + 1, total - lastLapTotalMs, total));
        lastLapTotalMs = total;
        return true;
    }

    /** Круги, последний — первым. */
    public List<Lap> laps() {
        return List.copyOf(laps);
    }

    /** Текст для буфера обмена: итог и список кругов по порядку. */
    public String copyText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Total ").append(TimeFormats.stopwatch(elapsedMs()));
        for (int i = laps.size() - 1; i >= 0; i--) {
            Lap l = laps.get(i);
            sb.append('\n').append("Lap ").append(l.number()).append(": ").append(TimeFormats.stopwatch(l.lapMs()))
                    .append(" (").append(TimeFormats.stopwatch(l.totalMs())).append(')');
        }
        return sb.toString();
    }
}
