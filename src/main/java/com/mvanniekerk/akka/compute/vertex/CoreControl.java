package com.mvanniekerk.akka.compute.vertex;

import akka.actor.typed.ActorRef;

public interface CoreControl extends VertexMessage {

    record LoadCode(ActorRef<VertexDescription> replyTo, String code) implements CoreControl {}
    record LoadName(ActorRef<VertexDescription> replyTo, String name) implements CoreControl {}
    record ShowCode(ActorRef<String> replyTo) implements CoreControl {}
    record Connect(String id, ActorRef<? super CoreConsumer> target) implements CoreControl {}
    record Disconnect(String id) implements CoreControl {}
    record Stop() implements CoreControl {}
}
