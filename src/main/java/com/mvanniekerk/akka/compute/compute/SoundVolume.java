package com.mvanniekerk.akka.compute.compute;

import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.vertex.Core;

public class SoundVolume extends ComputeCore {

    private final double volume;

    public SoundVolume(Core consumer, String[] args) {
        super(consumer);

        String volumeArg = args.length > 0 ? args[0] : "1";
        volume = Double.parseDouble(volumeArg);
    }

    @Override
    public void receive(JsonNode message) {
        var soundBuffer = convert(message, NoteSynthesizer.SoundBuffer.class);
        var outArr = multArray(volume, soundBuffer.buffer());
        send(new NoteSynthesizer.SoundBuffer(soundBuffer.frameNr(), outArr));
    }

    private static double[] multArray(double left, double[] right) {
        var output = new double[right.length];
        for (int i = 0; i < right.length; i++) {
            output[i] = right[i] * left;
        }
        return output;
    }
}
