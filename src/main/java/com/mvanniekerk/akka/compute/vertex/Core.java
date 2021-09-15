package com.mvanniekerk.akka.compute.vertex;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class Core extends AbstractBehavior<VertexMessage> {

    private String code;

    public static Behavior<VertexMessage> create() {
        return Behaviors.setup(Core::new);
    }

    private Core(ActorContext<VertexMessage> context) {
        super(context);
    }

    @Override
    public Receive<VertexMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(CoreConsumer.Message.class, msg -> {
                    System.out.println("Received: " + msg.body());
                    return this;
                })
                .onMessage(CoreControl.ShowCode.class, msg -> {
                    return this;
                })
                .onMessage(CoreControl.LoadCode.class, msg -> {
                    code = msg.code();

                    return this;
                })
                .build();
    }
}
