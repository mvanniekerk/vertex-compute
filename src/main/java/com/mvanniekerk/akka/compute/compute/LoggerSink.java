package com.mvanniekerk.akka.compute.compute;

import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.vertex.Core;

public class LoggerSink extends ComputeCore {

    public LoggerSink(Core consumer) {
        super(consumer);
    }

    @Override
    public void receive(JsonNode message) {
       log(message.toString());
    }

    @Override
    public String getName() {
        return LoggerSink.class.getSimpleName();
    }
}
