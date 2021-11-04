package com.mvanniekerk.akka.compute.compute.synth;

import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.compute.ComputeCore;
import com.mvanniekerk.akka.compute.vertex.Core;

import java.util.ArrayList;
import java.util.List;

public class NoteEqAggregator extends ComputeCore {

    private long frameNr;
    private final List<double[]> soundBuffers = new ArrayList<>();

    public NoteEqAggregator(Core consumer) {
        super(consumer);
    }

    @Override
    public void receive(JsonNode message) {
        var soundBuffer = convert(message, SoundBuffer.class);
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
                .reduce(SoundUtil::sumArray)
                .orElseGet(() -> SoundUtil.silent(SoundUtil.MSG_INTERVAL_MS));
        var bufferResult = SoundUtil.multArray(soundBuffers.size(), bufferSum);
        send(new SoundBuffer(frameNr, bufferResult));
        soundBuffers.clear();
    }
}
