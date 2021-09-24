package com.mvanniekerk.akka.compute.compute;

import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.vertex.Core;

public abstract class ComputeCore {
    // TODO: You are recreating an actor, consider just using an actor!!

    private final Core core;

    public abstract void receive(JsonNode message);
    public abstract String getName();

    public ComputeCore(Core consumer) {
        this.core = consumer;
    }

    public final void send(JsonNode message) {
        core.send(message);
    }
}
