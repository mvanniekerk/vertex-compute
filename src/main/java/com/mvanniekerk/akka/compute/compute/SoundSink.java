package com.mvanniekerk.akka.compute.compute;

import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.vertex.Core;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.text.MessageFormat;
import java.time.Duration;

public class SoundSink extends ComputeCore {
    private static final int SAMPLE_RATE = 44100;

    private final Player player;
    private int time = 0;

    public SoundSink(Core consumer) {
        super(consumer);
        try {
            player = new Player(SAMPLE_RATE);
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
        var playerThread = new Thread(player);

        var msgIntervalMs = 50;
        scheduleOnce("start", Duration.ofMillis(2 * msgIntervalMs), () -> {
            playerThread.start();
            log("Started the dedicated sound thread...");
        });

        schedulePeriodic("a4", Duration.ofMillis(msgIntervalMs), () -> {
            var note = 5;
            var sinWaveBuffer = createSinWaveBuffer(calculateFrequency(note), msgIntervalMs, time);
            var sinVolume = multArray(0.2, sinWaveBuffer);
            player.writeToBuffer(asBytes(sinVolume));
            log(MessageFormat.format("Wrote a4 for {0} ms, time {1}", msgIntervalMs, time));
            time = time + msgIntervalMs;
        });
    }

    @Override
    public void receive(JsonNode message) {

    }

    @Override
    public String getName() {
        return SoundSink.class.getSimpleName();
    }

    public static double calculateFrequency(int noteValue) {
        final var a = Math.pow(2, 1. / 12);
        return 440 * Math.pow(a, noteValue);
    }

    public static byte[] asBytes(double[] in) {
        var output = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            output[i] = (byte) (in[i] * 127f);
        }
        return output;
    }

    public static double[] multArray(double left, double[] right) {
        var output = new double[right.length];
        for (int i = 0; i < right.length; i++) {
            output[i] = right[i] * left;
        }
        return output;
    }

    public static double[] createSinWaveBuffer(double freq, int ms, double start) {
        int samples = (ms * SAMPLE_RATE) / 1000;
        double[] output = new double[samples];
        var period = SAMPLE_RATE / freq;
        var offset = start / 1000.0 * SAMPLE_RATE;
        for (int i = 0; i < samples; i++) {
            output[i] = Math.sin(2.0 * Math.PI * (i + offset) / period);
        }

        return output;
    }

    private static class Player implements Runnable {
        private final int bufferSize;
        private boolean isRunning = true;
        private final SourceDataLine line;

        public void writeToBuffer(byte[] soundBuffer) {
            int left = bufferSize - line.available();
            if (left == 0) {
                System.out.println("Warning!! No samples left...");
            }
            line.write(soundBuffer, 0, soundBuffer.length);
        }

        public Player(int bufferSize) throws LineUnavailableException {
            final AudioFormat af = new AudioFormat(SAMPLE_RATE, 8, 1, true, true);
            line = AudioSystem.getSourceDataLine(af);
            this.bufferSize = bufferSize;
            line.open(af, bufferSize);
        }

        public void run() {
            line.start();
            while (isRunning) {
                line.drain();
                System.out.println("Ran out of data to drain");
            }
            line.close();
        }

        public void stop() {
            isRunning = false;
        }
    }
}
