package com.mvanniekerk.akka.compute.compute;

import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.vertex.Core;

public class Logger extends ComputeCore {

    public Logger(Core consumer) {
        super(consumer);
    }

    @Override
    public void receive(JsonNode message) {
       log(message.toString());
       send(message);
    }

}
