package com.mvanniekerk.akka.compute.compute.synth;

import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.compute.ComputeCore;
import com.mvanniekerk.akka.compute.vertex.Core;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static com.mvanniekerk.akka.compute.compute.synth.SoundSink.SAMPLE_RATE;

public class NoteSynthesizer extends ComputeCore {
    private record NoteAction(String action, int midiNumber) {}

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

        schedulePeriodic("soundGen", Duration.ofMillis(SoundUtil.MSG_INTERVAL_MS), () -> {
            var soundBuff = activeNotes.stream()
                    .map(note -> createSinWaveBuffer(SoundUtil.calculateFrequency(note), SoundUtil.MSG_INTERVAL_MS, frameNr))
                    .reduce(SoundUtil::sumArray)
                    .orElseGet(() -> SoundUtil.silent(SoundUtil.MSG_INTERVAL_MS));

            var attackBuff = attackNotes.stream()
                    .map(note -> createSinWaveBuffer(SoundUtil.calculateFrequency(note), SoundUtil.MSG_INTERVAL_MS, frameNr))
                    .reduce(SoundUtil::sumArray)
                    .orElseGet(() -> SoundUtil.silent(SoundUtil.MSG_INTERVAL_MS));
            var attack = SoundUtil.multArray(SoundUtil.linearUp(SoundUtil.MSG_INTERVAL_MS), attackBuff);

            var releaseBuff = releaseNotes.stream()
                    .map(note -> createSinWaveBuffer(SoundUtil.calculateFrequency(note), SoundUtil.MSG_INTERVAL_MS, frameNr))
                    .reduce(SoundUtil::sumArray)
                    .orElseGet(() -> SoundUtil.silent(SoundUtil.MSG_INTERVAL_MS));
            var release = SoundUtil.multArray(SoundUtil.linearDown(SoundUtil.MSG_INTERVAL_MS), releaseBuff);

            var sounds = Stream.of(soundBuff, attack, release)
                    .reduce(SoundUtil::sumArray).orElseThrow();

            var noteCount = activeNotes.size() + attackNotes.size() + releaseNotes.size();
            var sinVolume = SoundUtil.multArray(1.0 / noteCount, sounds);
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

    double[] createSinWaveBuffer(double freq, int ms, int frameNr) {
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
