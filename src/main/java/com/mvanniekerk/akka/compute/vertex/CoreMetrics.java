package com.mvanniekerk.akka.compute.vertex;

import akka.actor.typed.ActorRef;

public interface CoreMetrics extends VertexMessage {
    record GetMetrics(ActorRef<MetricsWithId> replyTo) implements CoreMetrics {}

    record MetricsWithId(String vertexId, Metrics metrics) {}
    record Metrics(double msgFreqPerSec, long messagesReceived, long messagesSent) {}
}
