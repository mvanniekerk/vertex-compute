package com.mvanniekerk.akka.compute.compute;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mvanniekerk.akka.compute.vertex.Core;

import java.time.Duration;

public abstract class ComputeCore {
    // TODO: You are recreating an actor, consider just using an actor!!

    private final Core core;
    private final ObjectMapper objectMapper;

    public abstract void receive(JsonNode message);

    public ComputeCore(Core consumer) {
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        this.core = consumer;
    }

    public final <T> T convert(JsonNode message, Class<T> type) {
        try {
            return objectMapper.treeToValue(message, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public final void send(JsonNode message) {
        core.send(message);
    }

    public final void send(Object message) {
        core.send(objectMapper.valueToTree(message));
    }

    public final void log(String message) {
        core.log(message);
    }

    public final void schedulePeriodic(String key, Duration interval, Runnable runnable) {
        core.schedulePeriodic(key, interval, runnable);
    }

    public final void scheduleOnce(String key, Duration delay, Runnable runnable) {
        core.scheduleOnce(key, delay, runnable);
    }

    public final void stopPeriodic(String key) {
        core.stopPeriodic(key);
    }
}
