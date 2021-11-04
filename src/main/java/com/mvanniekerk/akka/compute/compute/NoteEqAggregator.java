package com.mvanniekerk.akka.compute.compute;

import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.vertex.Core;

import java.util.ArrayList;
import java.util.List;

import static com.mvanniekerk.akka.compute.compute.SoundSink.SAMPLE_RATE;

public class NoteEqAggregator extends ComputeCore {

    private long frameNr;
    private List<double[]> soundBuffers = new ArrayList<>();

    public NoteEqAggregator(Core consumer) {
        super(consumer);
    }

    @Override
    public void receive(JsonNode message) {
        var soundBuffer = convert(message, NoteSynthesizer.SoundBuffer.class);
        if (soundBuffer.frameNr() != frameNr) {
            log("Flushing frame " + frameNr);
            flush();
            soundBuffers.add(soundBuffer.buffer());
            frameNr = soundBuffer.frameNr();
        } else {
            soundBuffers.add(soundBuffer.buffer());
        }
    }

    public void flush() {
        var bufferSum = soundBuffers.stream()
                .reduce(NoteEqAggregator::sumArray)
                .orElseGet(() -> silent(NoteSynthesizer.MSG_INTERVAL_MS));
        var bufferResult = multArray(soundBuffers.size(), bufferSum);
        send(new NoteSynthesizer.SoundBuffer(frameNr, bufferResult));
        soundBuffers.clear();
    }

    private static double[] multArray(double left, double[] right) {
        var output = new double[right.length];
        for (int i = 0; i < right.length; i++) {
            output[i] = right[i] * left;
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

    private static double[] silent(int ms) {
        int samples = (ms * SAMPLE_RATE) / 1000;
        return new double[samples];
    }
}
