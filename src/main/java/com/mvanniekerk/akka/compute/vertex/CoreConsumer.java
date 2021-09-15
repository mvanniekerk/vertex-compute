package com.mvanniekerk.akka.compute.vertex;

import com.fasterxml.jackson.databind.JsonNode;

public interface CoreConsumer extends VertexMessage {
    record Message(JsonNode body) implements CoreConsumer {
    }
}
