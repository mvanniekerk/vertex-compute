package com.mvanniekerk.akka.compute.control;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.util.Aggregator;
import com.mvanniekerk.akka.compute.vertex.*;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class Control extends AbstractBehavior<Control.Message> {
    public interface Message {}

    // HTTP requests
    public record GetStateRequest(ActorRef<SystemDescription> replyTo) implements Message {}
    public record CreateVertex(ActorRef<VertexReply> replyTo, String name, String code) implements Message {}
    public record VertexReply(String status, VertexDescription description) {}
    public record LoadCode(ActorRef<VertexDescription> replyTo, String id, String code) implements Message {}
    public record LoadName(ActorRef<VertexDescription> replyTo, String id, String code) implements Message {}
    public record LinkVertices(ActorRef<LinkReply> replyTo, String from, String to) implements Message {}
    public record LinkReply(String status, String id) {}

    // Vertex responses
    private record VertexDescribeAggregator(ActorRef<SystemDescription> replyTo,
                                            List<VertexDescription> vertices) implements Message {}

    // WS push messages
    public record RegisterWebSocket(String sessionId, ActorRef<CoreLog.LogMessage> socket) implements Message {}
    public record WrappedLog(CoreLog.LogMessage message) implements Message {}
    public record LogSubscribe(String session, String id) implements Message {}

    // TODO: receive HTTP should be split to own actor, set up direct link and get out of the way
    public record ReceiveHttp(String id, JsonNode body) implements Message {}

    // FIELDS

    private final Map<String, ActorRef<VertexMessage>> verticesById = new HashMap<>();
    private final Map<String, SystemDescription.Edge> edgesById = new HashMap<>();
    private final ActorRef<CoreLog.LogMessage> logMessageAdapter;

    private final Map<String, String> subscriptionsBySessionId = new HashMap<>();
    private final Map<String, ActorRef<CoreLog.LogMessage>> socketsBySessionId = new HashMap<>();
    private final NameGenerator nameGenerator = new NameGenerator();

    // CODE

    public static Behavior<Message> create() {
        return Behaviors.setup(Control::new);
    }

    private Control(ActorContext<Message> context) {
        super(context);
        logMessageAdapter = getContext().messageAdapter(CoreLog.LogMessage.class, WrappedLog::new);
    }

    @Override
    public Receive<Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(GetStateRequest.class, msg -> {
                    ArrayList<ActorRef<VertexMessage>> vertices = new ArrayList<>(verticesById.values());
                    getContext().spawnAnonymous(Aggregator.create(
                            VertexDescription.class,
                            replyTo -> vertices.forEach(vertex -> vertex.tell(new CoreControl.Describe(replyTo.narrow()))),
                            vertices.size(),
                            getContext().getSelf(),
                            replies -> new VertexDescribeAggregator(msg.replyTo(), replies),
                            Duration.of(5, ChronoUnit.SECONDS)));
                    return this;
                })
                .onMessage(VertexDescribeAggregator.class, msg -> {
                    SystemDescription description = new SystemDescription(msg.vertices(), new ArrayList<>(edgesById.values()));
                    msg.replyTo().tell(description);
                    return this;
                })
                .onMessage(CreateVertex.class, msg -> {
                    String id = UUID.randomUUID().toString();
                    String name;
                    if (msg.name == null || msg.name.isBlank()) {
                        name = nameGenerator.generateName();
                    } else {
                        name = msg.name;
                    }
                    ActorRef<VertexMessage> vert = getContext().spawn(Core.create(id, name, msg.code), id);
                    verticesById.put(id, vert);
                    msg.replyTo.tell(new VertexReply("Success", new VertexDescription(id, name, msg.code)));
                    return this;
                })
                .onMessage(ReceiveHttp.class, msg -> {
                    ActorRef<VertexMessage> vertActor = verticesById.get(msg.id);
                    vertActor.tell(new CoreConsumer.Message(msg.body));
                    return this;
                })
                .onMessage(LoadCode.class, msg -> {
                    ActorRef<VertexMessage> vertActor = verticesById.get(msg.id);
                    vertActor.tell(new CoreControl.LoadCode(msg.replyTo, msg.code));
                    return this;
                })
                .onMessage(LoadName.class, msg -> {
                    var vertex = verticesById.get(msg.id);
                    vertex.tell(new CoreControl.LoadName(msg.replyTo, msg.code));
                    return this;
                })
                .onMessage(LinkVertices.class, msg -> {
                    String id = UUID.randomUUID().toString();
                    ActorRef<VertexMessage> source = verticesById.get(msg.from);
                    ActorRef<VertexMessage> target = verticesById.get(msg.to);
                    source.tell(new CoreControl.Connect(id, target));
                    edgesById.put(id, new SystemDescription.Edge(id, msg.from, msg.to));
                    msg.replyTo.tell(new LinkReply("Success", id));
                    return this;
                })

                .onMessage(LogSubscribe.class, msg -> {
                    var subscription = subscriptionsBySessionId.get(msg.session);
                    if (subscription != null) {
                        var oldVertex = verticesById.get(subscription);
                        oldVertex.tell(new CoreLog.UnsubscribeLog());
                    }
                    if (msg.id != null) {
                        subscriptionsBySessionId.put(msg.session, msg.id);
                        ActorRef<VertexMessage> newVertex = verticesById.get(msg.id);
                        newVertex.tell(new CoreLog.SubscribeLog(logMessageAdapter));
                    }
                    return this;
                })
                .onMessage(WrappedLog.class, msg -> {
                    CoreLog.LogMessage message = msg.message;
                    var id = message.computeId();
                    subscriptionsBySessionId.entrySet().stream()
                            .filter(entry -> entry.getValue().equals(id))
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .map(socketsBySessionId::get)
                            .ifPresent(socket -> socket.tell(message));
                    return this;
                })
                .onMessage(RegisterWebSocket.class, msg -> {
                    getContext().getLog().info("Register ws: {}", msg);
                    socketsBySessionId.put(msg.sessionId, msg.socket);
                    return this;
                })
                .build();
    }
}
