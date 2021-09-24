package com.mvanniekerk.akka.compute.vertex;

import akka.actor.typed.ActorRef;

public interface CoreControl extends VertexMessage {

    record Describe(ActorRef<VertexDescription> replyTo) implements CoreControl {}
    record LoadCode(String code) implements CoreControl {}
    record ShowCode(ActorRef<String> replyTo) implements CoreControl {}
    record Connect(String id, ActorRef<? super CoreConsumer> target) implements CoreControl {}
}
