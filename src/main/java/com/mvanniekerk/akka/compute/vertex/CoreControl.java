package com.mvanniekerk.akka.compute.vertex;

import akka.actor.typed.ActorRef;

public interface CoreControl extends VertexMessage {

    record LoadCode(String code) implements CoreControl {
    }

    record ShowCode(ActorRef<String> replyTo) implements CoreControl {
    }
}
