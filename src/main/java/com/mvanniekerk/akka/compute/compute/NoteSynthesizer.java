package com.mvanniekerk.akka.compute.compute;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvanniekerk.akka.compute.vertex.Core;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static com.mvanniekerk.akka.compute.compute.SoundSink.SAMPLE_RATE;

public class NoteSynthesizer extends ComputeCore {
    private record NoteAction(String action, int midiNumber) {}
    record SoundBuffer(long frameNr, byte[] buffer) {}

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Set<Integer> attackNotes = new HashSet<>();
    private final Set<Integer> releaseNotes = new HashSet<>();
    private final Set<Integer> activeNotes = new HashSet<>();
    private final int msgIntervalMs = 50;
    private int frameNr = 0;

    public NoteSynthesizer(Core consumer, String[] args) {
        super(consumer);

        schedulePeriodic("soundGen", Duration.ofMillis(msgIntervalMs), () -> {
            var soundBuff = activeNotes.stream()
                    .map(note -> createSinWaveBuffer(calculateFrequency(note), msgIntervalMs, frameNr))
                    .reduce(NoteSynthesizer::sumArray)
                    .orElseGet(() -> silent(msgIntervalMs));

            var attackBuff = attackNotes.stream()
                    .map(note -> createSinWaveBuffer(calculateFrequency(note), msgIntervalMs, frameNr))
                    .reduce(NoteSynthesizer::sumArray)
                    .orElseGet(() -> silent(msgIntervalMs));
            var attack = multArray(linearUp(msgIntervalMs), attackBuff);

            var releaseBuff = releaseNotes.stream()
                    .map(note -> createSinWaveBuffer(calculateFrequency(note), msgIntervalMs, frameNr))
                    .reduce(NoteSynthesizer::sumArray)
                    .orElseGet(() -> silent(msgIntervalMs));
            var release = multArray(linearDown(msgIntervalMs), releaseBuff);

            var sounds = Stream.of(soundBuff, attack, release)
                    .reduce(NoteSynthesizer::sumArray).orElseThrow();

            var noteCount = activeNotes.size() + attackNotes.size() + releaseNotes.size();
            var sinVolume = multArray(1.0 / noteCount, sounds);
            send(new SoundBuffer(frameNr, asBytes(sinVolume)));

            activeNotes.addAll(attackNotes);
            attackNotes.clear();
            releaseNotes.clear();
            frameNr++;
        });
    }

    @Override
    public void receive(JsonNode message) {
        try {
            var noteAction = OBJECT_MAPPER.treeToValue(message, NoteAction.class);
            if (noteAction.action.equals("play")) {
                attackNotes.add(noteAction.midiNumber);
            } else { // stop
                attackNotes.remove(noteAction.midiNumber);
                activeNotes.remove(noteAction.midiNumber);
                releaseNotes.add(noteAction.midiNumber);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return NoteSynthesizer.class.getSimpleName();
    }

    private static double calculateFrequency(int noteValue) {
        final var a = Math.pow(2, 1. / 12);
        return 440 * Math.pow(a, noteValue - 48);
    }

    private static byte[] asBytes(double[] in) {
        var output = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            output[i] = (byte) (in[i] * 127f);
        }
        return output;
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
            output[i] = Math.sin(2.0 * Math.PI * (i + offset) / period);
        }

        return output;
    }
}
