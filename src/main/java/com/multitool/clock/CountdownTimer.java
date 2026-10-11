package com.multitool.clock;

import java.util.function.LongSupplier;

/**
 * Таймер обратного отсчёта на монотонном времени (System.nanoTime). Не зависит от FPS, TPS и
 * системных часов: перевод времени назад или вперёд его не затрагивает.
 */
public final class CountdownTimer {
    public enum State { IDLE, RUNNING, PAUSED, FINISHED }

    public static final long MIN_MS = 1_000L;
    public static final long MAX_MS = 99L * 3_600_000L + 59L * 60_000L + 59_000L;
    private static final long NANOS_PER_MS = 1_000_000L;

    private final LongSupplier nanos;
    public String name;
    private long durationMs;
    private State state = State.IDLE;
    private long remainingAtPauseMs;
    private long endNanos;

    public CountdownTimer(String name, long durationMs, LongSupplier nanos) {
        this.name = name;
        this.nanos = nanos;
        this.durationMs = clampDuration(durationMs);
    }

    public static long clampDuration(long ms) {
        return Math.max(MIN_MS, Math.min(MAX_MS, ms));
    }

    public State state() {
        return state;
    }

    public long durationMs() {
        return durationMs;
    }

    /** Меняет длительность; допустимо только когда таймер не идёт. */
    public boolean setDuration(long ms) {
        if (state == State.RUNNING || state == State.PAUSED) {
            return false;
        }
        durationMs = clampDuration(ms);
        state = State.IDLE;
        return true;
    }

    /** Запуск с начала, либо продолжение, если таймер стоит на паузе. */
    public void start() {
        if (state == State.PAUSED) {
            resume();
            return;
        }
        if (state == State.RUNNING) {
            return;
        }
        remainingAtPauseMs = durationMs;
        endNanos = nanos.getAsLong() + durationMs * NANOS_PER_MS;
        state = State.RUNNING;
    }

    public void pause() {
        if (state == State.RUNNING) {
            remainingAtPauseMs = remainingMs();
            state = State.PAUSED;
        }
    }

    public void resume() {
        if (state == State.PAUSED) {
            endNanos = nanos.getAsLong() + remainingAtPauseMs * NANOS_PER_MS;
            state = State.RUNNING;
        }
    }

    public void reset() {
        state = State.IDLE;
    }

    /** Оставшееся время, мс (округляется вверх, чтобы «00:01» показывалось до самого конца). */
    public long remainingMs() {
        switch (state) {
            case RUNNING -> {
                long diff = endNanos - nanos.getAsLong();
                if (diff <= 0) {
                    return 0;
                }
                return (diff + NANOS_PER_MS - 1) / NANOS_PER_MS;
            }
            case PAUSED -> {
                return remainingAtPauseMs;
            }
            case FINISHED -> {
                return 0;
            }
            default -> {
                return durationMs;
            }
        }
    }

    /** Доля пройденного времени 0..1. */
    public double progress() {
        if (state == State.IDLE) {
            return 0;
        }
        return 1.0 - (double) remainingMs() / durationMs;
    }

    /** @return true один раз — в момент, когда таймер дошёл до нуля */
    public boolean tick() {
        if (state == State.RUNNING && endNanos - nanos.getAsLong() <= 0) {
            state = State.FINISHED;
            return true;
        }
        return false;
    }
}
