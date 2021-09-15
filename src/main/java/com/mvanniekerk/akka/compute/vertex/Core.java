package com.mvanniekerk.akka.compute.vertex;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class Core extends AbstractBehavior<VertexMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Core.class);

    private final Compiler compiler = new SimplisticCompiler();
    private final String name;

    private String code = "pass";
    private Function<JsonNode, JsonNode> process = node -> node;
    private ActorRef<? super CoreConsumer> target;

    private Core(ActorContext<VertexMessage> context, String name) {
        super(context);
        this.name = name;
    }

    public static Behavior<VertexMessage> create(String name) {
        return Behaviors.setup(context -> new Core(context, name));
    }

    @Override
    public Receive<VertexMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(CoreConsumer.Message.class, msg -> {
                    JsonNode body = msg.body();
                    LOGGER.info("Received ({}): {}", name, body);
                    if (target != null) {
                        JsonNode out = process.apply(body);
                        target.tell(new CoreConsumer.Message(out));
                    }
                    return this;
                })
                .onMessage(CoreControl.ShowCode.class, msg -> {
                    msg.replyTo().tell(code);
                    return this;
                })
                .onMessage(CoreControl.LoadCode.class, msg -> {
                    code = msg.code();
                    process = compiler.compile(code);
                    return this;
                })
                .onMessage(CoreControl.Connect.class, msg -> {
                    target = msg.target();
                    return this;
                })
                .build();
    }
}
