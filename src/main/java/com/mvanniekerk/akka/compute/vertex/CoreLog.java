package com.mvanniekerk.akka.compute.vertex;

import akka.actor.typed.ActorRef;

import java.time.ZonedDateTime;

public interface CoreLog extends VertexMessage {

    record SubscribeLog(ActorRef<LogMessage> subscriber) implements CoreLog {}
    record UnsubscribeLog() implements CoreLog {}

    record LogMessage(ZonedDateTime timestamp, String computeId, String message) {}
}
