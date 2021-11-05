package com.mvanniekerk.akka.compute.compute.synth;

import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.compute.ComputeCore;
import com.mvanniekerk.akka.compute.vertex.Core;

import java.util.ArrayList;

import static com.mvanniekerk.akka.compute.compute.synth.SoundUtil.MSG_INTERVAL_MS;
import static com.mvanniekerk.akka.compute.compute.synth.SoundUtil.SAMPLE_RATE;

public class NoteSynthesizer extends ComputeCore {

    private double frequencyMultiplier = 1;

    public NoteSynthesizer(Core consumer, String[] args) {
        super(consumer);

        if (args.length > 0) {
            frequencyMultiplier = Double.parseDouble(args[0]);
        }
    }

    @Override
    public void receive(JsonNode message) {
        var noteInstructions = convert(message, NoteReceiver.NoteInstructions.class);

        var result = new ArrayList<double[]>();
        for (NoteReceiver.Instruction instruction : noteInstructions.instructions()) {
            var frequency = SoundUtil.calculateFrequency(instruction.midiNumber());
            var sinWave = createSinWaveBuffer(frequency, MSG_INTERVAL_MS, noteInstructions.frameNr());
            var volume = SoundUtil.linear(MSG_INTERVAL_MS, instruction.startVolume(), instruction.endVolume());
            var volumeArray = SoundUtil.multArray(sinWave, volume);
            result.add(volumeArray);
        }
        var wave = result.stream().reduce(SoundUtil::sumArray).orElse(SoundUtil.silent(MSG_INTERVAL_MS));
        var waveNorm = SoundUtil.multArray(1.0 / noteInstructions.instructions().size(), wave);
        send(new SoundBuffer(noteInstructions.frameNr(), waveNorm));
    }

    double[] createSinWaveBuffer(double freq, int ms, long frameNr) {
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
