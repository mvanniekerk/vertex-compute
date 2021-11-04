package com.mvanniekerk.akka.compute.compute;

import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.vertex.Core;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static com.mvanniekerk.akka.compute.compute.SoundSink.SAMPLE_RATE;

public class NoteSynthesizer extends ComputeCore {
    private record NoteAction(String action, int midiNumber) {}
    record SoundBuffer(long frameNr, double[] buffer) {}

    public static final int MSG_INTERVAL_MS = 50;

    private final Set<Integer> attackNotes = new HashSet<>();
    private final Set<Integer> releaseNotes = new HashSet<>();
    private final Set<Integer> activeNotes = new HashSet<>();
    private double frequencyMultiplier = 1;
    private int frameNr = 0;

    public NoteSynthesizer(Core consumer, String[] args) {
        super(consumer);

        if (args.length > 0) {
            frequencyMultiplier = Double.parseDouble(args[0]);
        }

        schedulePeriodic("soundGen", Duration.ofMillis(MSG_INTERVAL_MS), () -> {
            var soundBuff = activeNotes.stream()
                    .map(note -> createSinWaveBuffer(calculateFrequency(note), MSG_INTERVAL_MS, frameNr))
                    .reduce(NoteSynthesizer::sumArray)
                    .orElseGet(() -> silent(MSG_INTERVAL_MS));

            var attackBuff = attackNotes.stream()
                    .map(note -> createSinWaveBuffer(calculateFrequency(note), MSG_INTERVAL_MS, frameNr))
                    .reduce(NoteSynthesizer::sumArray)
                    .orElseGet(() -> silent(MSG_INTERVAL_MS));
            var attack = multArray(linearUp(MSG_INTERVAL_MS), attackBuff);

            var releaseBuff = releaseNotes.stream()
                    .map(note -> createSinWaveBuffer(calculateFrequency(note), MSG_INTERVAL_MS, frameNr))
                    .reduce(NoteSynthesizer::sumArray)
                    .orElseGet(() -> silent(MSG_INTERVAL_MS));
            var release = multArray(linearDown(MSG_INTERVAL_MS), releaseBuff);

            var sounds = Stream.of(soundBuff, attack, release)
                    .reduce(NoteSynthesizer::sumArray).orElseThrow();

            var noteCount = activeNotes.size() + attackNotes.size() + releaseNotes.size();
            var sinVolume = multArray(1.0 / noteCount, sounds);
            send(new SoundBuffer(frameNr, sinVolume));

            activeNotes.addAll(attackNotes);
            attackNotes.clear();
            releaseNotes.clear();
            frameNr++;
        });
    }

    @Override
    public void receive(JsonNode message) {
        var noteAction = convert(message, NoteAction.class);
        if (noteAction.action.equals("play")) {
            attackNotes.add(noteAction.midiNumber);
        } else { // stop
            attackNotes.remove(noteAction.midiNumber);
            activeNotes.remove(noteAction.midiNumber);
            releaseNotes.add(noteAction.midiNumber);
        }
    }

    private static double calculateFrequency(int noteValue) {
        final var a = Math.pow(2, 1. / 12);
        return 440 * Math.pow(a, noteValue - 48);
    }

    private static double[] sumArray(double[] left, double[] right) {
        double[] result = new double[left.length];
        for (int i = 0; i < left.length; i++) {
            result[i] = left[i] + right[i];
        }
        return result;
    }

    private static double[] multArray(double left, double[] right) {
        var output = new double[right.length];
        for (int i = 0; i < right.length; i++) {
            output[i] = right[i] * left;
        }
        return output;
    }

    private static double[] multArray(double[] left, double[] right) {
        var output = new double[left.length];
        for (int i = 0; i < left.length; i++) {
            output[i] = right[i] * left[i];
        }
        return output;
    }

    private static double[] linearUp(int ms) {
        int samples = (ms * SAMPLE_RATE) / 1000;
        var output = new double[samples];
        for (int i = 0; i < output.length; i++) {
            output[i] = 1.0 * i / samples;
        }
        return output;
    }

    private static double[] linearDown(int ms) {
        int samples = (ms * SAMPLE_RATE) / 1000;
        var output = new double[samples];
        for (int i = 0; i < output.length; i++) {
            output[i] = 1 - 1.0 * i / samples;
        }
        return output;
    }

    private static double[] silent(int ms) {
        int samples = (ms * SAMPLE_RATE) / 1000;
        return new double[samples];
    }

    private double[] createSinWaveBuffer(double freq, int ms, int frameNr) {
        int samples = (ms * SAMPLE_RATE) / 1000;
        double[] output = new double[samples];
        var period = SAMPLE_RATE / freq;
        var offset = frameNr * ms / 1000.0 * SAMPLE_RATE;
        for (int i = 0; i < samples; i++) {
            output[i] = Math.sin(2.0 * Math.PI * frequencyMultiplier * (i + offset) / period);
        }

        return output;
    }
}
