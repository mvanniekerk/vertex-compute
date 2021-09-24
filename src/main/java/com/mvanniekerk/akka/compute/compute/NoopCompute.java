package com.mvanniekerk.akka.compute.compute;

import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.vertex.Core;

public class NoopCompute extends ComputeCore {

    public NoopCompute(Core consumer) {
        super(consumer);
    }

    @Override
    public void receive(JsonNode message) {
        // NOOP
    }

    @Override
    public String getName() {
        return NoopCompute.class.getSimpleName();
    }
}
