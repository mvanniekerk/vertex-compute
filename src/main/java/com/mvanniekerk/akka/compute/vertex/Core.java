package com.mvanniekerk.akka.compute.vertex;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.compute.Compiler;
import com.mvanniekerk.akka.compute.compute.ComputeCore;
import com.mvanniekerk.akka.compute.compute.NoopCompute;
import com.mvanniekerk.akka.compute.compute.StaticClassNameCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Core extends AbstractBehavior<VertexMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Core.class);

    private final Compiler compiler = new StaticClassNameCompiler();
    private final String id;
    private final String name;

    private String code;
    private ComputeCore process;
    private Map<String, ActorRef<? super CoreConsumer>> targetsById = new HashMap<>();

    private Core(ActorContext<VertexMessage> context, String id, String name, String code) {
        super(context);
        this.id = id;
        this.name = name;
        this.code = code;
        process = new NoopCompute(this);
    }

    public static Behavior<VertexMessage> create(String id, String name, String code) {
        return Behaviors.setup(context -> new Core(context, id, name, code));
    }

    public void send(JsonNode message) {
        targetsById.forEach((id, target) -> target.tell(new CoreConsumer.Message(message)));
    }

    @Override
    public Receive<VertexMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(CoreConsumer.Message.class, msg -> {
                    JsonNode body = msg.body();
                    LOGGER.info("Received ({}): {}", name, body);
                    if (process != null) {
                        process.receive(body);
                    }
                    return this;
                })
                .onMessage(CoreControl.Describe.class, msg -> {
                    var description = new VertexDescription(id, name, code);
                    msg.replyTo().tell(description);
                    return this;
                })
                .onMessage(CoreControl.ShowCode.class, msg -> {
                    msg.replyTo().tell(code);
                    return this;
                })
                .onMessage(CoreControl.LoadCode.class, msg -> {
                    code = msg.code();
                    process = compiler.compile(code).apply(this);
                    return this;
                })
                .onMessage(CoreControl.Connect.class, msg -> {
                    targetsById.put(msg.id(), msg.target());
                    return this;
                })
                .build();
    }
}
