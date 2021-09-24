package com.mvanniekerk.akka.compute.compute;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvanniekerk.akka.compute.vertex.Core;

import java.util.Arrays;
import java.util.List;

public class Splitter extends ComputeCore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Splitter(Core consumer) {
        super(consumer);
    }

    @Override
    public void receive(JsonNode message) {
        String text = message.asText();
        List<String> lines = Arrays.asList(text.split("\n"));
        lines.forEach(line -> this.send(OBJECT_MAPPER.valueToTree(line)));
    }

    @Override
    public String getName() {
        return Splitter.class.getSimpleName();
    }
}
