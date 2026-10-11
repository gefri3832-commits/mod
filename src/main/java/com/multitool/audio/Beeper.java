package com.multitool.audio;

import com.multitool.MultiToolClient;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Сигналы таймеров и будильников. Звуки синтезируются в коде и воспроизводятся через javax.sound
 * в отдельном потоке, поэтому игровой поток не блокируется. Громкость задаётся масштабом амплитуды.
 */
public final class Beeper {
    public static final int SAMPLE_RATE = 44100;
    public static final int SOUND_COUNT = 3;

    // ноты: {частота Гц (0 — пауза), длительность мс}
    private static final int[][][] PATTERNS = {
            {{784, 350}, {1047, 650}},                                   // Bell
            {{1000, 130}, {0, 90}, {1000, 130}, {0, 90}, {1000, 130}},   // Beep
            {{523, 220}, {659, 220}, {784, 220}, {1047, 520}}            // Chime
    };
    private static final double[] DECAY = {5.0, 0.0, 3.5};

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MultiTool-Audio");
        t.setDaemon(true);
        return t;
    });
    private static volatile boolean unavailable;

    private Beeper() {
    }

    /** Синтезирует сигнал: 16 бит, моно, little-endian. Громкость 0..100 %. */
    public static byte[] pcm(int sound, int volumePercent) {
        int s = Math.max(0, Math.min(SOUND_COUNT - 1, sound));
        double gain = 0.6 * Math.max(0, Math.min(100, volumePercent)) / 100.0;
        int total = 0;
        for (int[] n : PATTERNS[s]) {
            total += (int) ((long) SAMPLE_RATE * n[1] / 1000);
        }
        byte[] out = new byte[total * 2];
        int pos = 0;
        for (int[] n : PATTERNS[s]) {
            int len = (int) ((long) SAMPLE_RATE * n[1] / 1000);
            double f = n[0];
            int fade = SAMPLE_RATE / 200; // 5 мс
            for (int i = 0; i < len; i++) {
                double v = 0;
                if (f > 0) {
                    double t = (double) i / SAMPLE_RATE;
                    double env = Math.exp(-DECAY[s] * t);
                    env *= Math.min(1.0, (double) i / fade);
                    env *= Math.min(1.0, (double) (len - i) / fade);
                    double ph = 2 * Math.PI * f * t;
                    v = (Math.sin(ph) + 0.3 * Math.sin(2 * ph)) / 1.3 * env * gain;
                }
                short sample = (short) Math.round(v * Short.MAX_VALUE);
                out[pos++] = (byte) (sample & 0xFF);
                out[pos++] = (byte) ((sample >> 8) & 0xFF);
            }
        }
        return out;
    }

    /** Длительность сигнала, мс. */
    public static int durationMs(int sound) {
        int s = Math.max(0, Math.min(SOUND_COUNT - 1, sound));
        int total = 0;
        for (int[] n : PATTERNS[s]) {
            total += n[1];
        }
        return total;
    }

    /** Воспроизводит сигнал в фоне. Если звук недоступен (нет аудиоустройства), молча ничего не делает. */
    public static void play(int sound, int volumePercent) {
        if (unavailable || volumePercent <= 0) {
            return;
        }
        byte[] data = pcm(sound, volumePercent);
        EXEC.execute(() -> {
            try {
                AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                Clip clip = AudioSystem.getClip();
                clip.addLineListener(e -> {
                    if (e.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });
                clip.open(fmt, data, 0, data.length);
                clip.start();
            } catch (Throwable e) {
                unavailable = true;
                MultiToolClient.LOGGER.warn("Звук сигналов недоступен: {}", e.toString());
            }
        });
    }

    public static boolean isUnavailable() {
        return unavailable;
    }

    public static void shutdown() {
        EXEC.shutdownNow();
    }
}
