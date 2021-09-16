package com.mvanniekerk.akka.compute.control;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.vertex.Core;
import com.mvanniekerk.akka.compute.vertex.CoreConsumer;
import com.mvanniekerk.akka.compute.vertex.CoreControl;
import com.mvanniekerk.akka.compute.vertex.VertexMessage;

import java.util.HashMap;
import java.util.Map;

public class Control extends AbstractBehavior<Control.Message> {
    public interface Message {}

    public record CreateVertex(String name) implements Message {}
    public record GetVertex(String name, ActorRef<ActorRef<VertexMessage>> replyTo) implements Message {}
    public record LoadCode(String name, String code) implements Message {}
    public record LinkVertices(String from, String to) implements Message {}

    // TODO: receive HTTP should be split to own actor, set up direct link and get out of the way
    public record ReceiveHttp(String name, JsonNode body) implements Message {}

    private final Map<String, ActorRef<VertexMessage>> vertices = new HashMap<>();

    public static Behavior<Message> create() {
        return Behaviors.setup(Control::new);
    }

    private Control(ActorContext<Message> context) {
        super(context);
    }

    @Override
    public Receive<Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(CreateVertex.class, msg -> {
                    ActorRef<VertexMessage> vert = getContext().spawn(Core.create(msg.name), msg.name);
                    vertices.put(msg.name, vert);
                    return this;
                })
                .onMessage(GetVertex.class, msg -> {
                    ActorRef<VertexMessage> vertActorRef = vertices.get(msg.name);
                    msg.replyTo.tell(vertActorRef);
                    return this;
                })
                .onMessage(ReceiveHttp.class, msg -> {
                    ActorRef<VertexMessage> vertActor = vertices.get(msg.name);
                    vertActor.tell(new CoreConsumer.Message(msg.body));
                    return this;
                })
                .onMessage(LoadCode.class, msg -> {
                    ActorRef<VertexMessage> vertActor = vertices.get(msg.name);
                    vertActor.tell(new CoreControl.LoadCode(msg.code));
                    return this;
                })
                .onMessage(LinkVertices.class, msg -> {
                    ActorRef<VertexMessage> source = vertices.get(msg.from);
                    ActorRef<VertexMessage> target = vertices.get(msg.to);
                    source.tell(new CoreControl.Connect(target));
                    return this;
                })
                .build();
    }
}
