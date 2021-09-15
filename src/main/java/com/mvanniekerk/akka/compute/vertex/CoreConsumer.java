package com.mvanniekerk.akka.compute.vertex;

public interface CoreConsumer extends VertexMessage {
    record Message(String body) implements CoreConsumer {
    }
}
