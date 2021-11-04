package com.mvanniekerk.akka.compute.compute;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvanniekerk.akka.compute.vertex.Core;

public class SoundVolume extends ComputeCore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final double volume;

    public SoundVolume(Core consumer, String[] args) {
        super(consumer);

        String volumeArg = args.length > 0 ? args[0] : "1";
        volume = Double.parseDouble(volumeArg);
    }

    @Override
    public void receive(JsonNode message) {
        try {
            var soundBuffer = OBJECT_MAPPER.treeToValue(message, NoteSynthesizer.SoundBuffer.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return SoundVolume.class.getSimpleName();
    }

    private static double[] multArray(double left, double[] right) {
        var output = new double[right.length];
        for (int i = 0; i < right.length; i++) {
            output[i] = right[i] * left;
        }
        return output;
    }
}
