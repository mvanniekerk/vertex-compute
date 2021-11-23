package com.mvanniekerk.akka.compute.control;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.control.graph.Vertex;
import com.mvanniekerk.akka.compute.control.graph.Vertices;
import com.mvanniekerk.akka.compute.util.Aggregator;
import com.mvanniekerk.akka.compute.vertex.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class Control extends AbstractBehavior<Control.Message> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Control.class);
    public interface Message {}

    // HTTP requests
    public record GetStateRequest(ActorRef<SystemDescription> replyTo) implements Message {}
    public record LoadStateRequest(SystemDescription system) implements Message {}
    public record CreateVertex(ActorRef<VertexReply> replyTo, String name, String code) implements Message {}
    public record DeleteVertex(String id) implements Message {}
    public record VertexReply(String status, VertexDescription description) {}
    public record LoadCode(ActorRef<VertexDescription> replyTo, String id, String code) implements Message {}
    public record LoadName(ActorRef<VertexDescription> replyTo, String id, String name) implements Message {}
    public record LinkVertices(ActorRef<LinkReply> replyTo, String from, String to) implements Message {}
    public record LinkReply(String status, String id) {}

    // WS push messages
    public record RegisterControlWebSocket(String sessionId, ActorRef<WebSocketMessage> socket) implements Message {}
    public record WrappedLog(CoreLog.LogMessage message) implements Message {}
    public record LogSubscribe(String sessionId, String id) implements Message {}

    // metrics
    public record RequestMetrics() implements Message {}
    public record VertexMetricsAggregator(Map<String, CoreMetrics.Metrics> metricsByVertexId) implements Message {}

    public record ReceiveMsg(String name, JsonNode body) implements Message {}

    public static Behavior<Message> create() {
        return Behaviors.setup(context ->
                Behaviors.withTimers(scheduler -> new Control(context, scheduler)));
    }

    private final Vertices vertices = new Vertices();
    private final Map<String, SystemDescription.Edge> edgesById = new HashMap<>();
    private final ActorRef<CoreLog.LogMessage> logMessageAdapter;

    private final Map<String, String> subscriptionsBySessionId = new HashMap<>();
    private final Map<String, ActorRef<WebSocketMessage>> socketsBySessionId = new HashMap<>();
    private final NameGenerator nameGenerator = new NameGenerator();

    private Control(ActorContext<Message> context, TimerScheduler<Message> scheduler) {
        super(context);
        logMessageAdapter = getContext().messageAdapter(CoreLog.LogMessage.class, WrappedLog::new);
        scheduler.startTimerAtFixedRate(new RequestMetrics(), Duration.ofSeconds(10));
    }

    @Override
    public Receive<Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(GetStateRequest.class, msg -> {
                    var vertexDescriptions = vertices.getAll().stream()
                            .map(Vertex::describe)
                            .collect(Collectors.toList());
                    var description = new SystemDescription(vertexDescriptions, new ArrayList<>(edgesById.values()));
                    msg.replyTo.tell(description);
                    return this;
                })
                .onMessage(LoadStateRequest.class, msg -> {
                    var system = msg.system;
                    for (VertexDescription vertex : system.vertices()) {
                        createVertex(vertex.id(), vertex.name(), vertex.code());
                    }
                    for (SystemDescription.Edge edge : system.edges()) {
                        linkVertices(edge.id(), edge.from(), edge.to());
                    }
                    return this;
                })
                .onMessage(CreateVertex.class, msg -> {
                    String id = UUID.randomUUID().toString();
                    var description = createVertex(id, msg.name, msg.code);
                    msg.replyTo.tell(new VertexReply("Success", description));
                    return this;
                })
                .onMessage(DeleteVertex.class, msg -> {
                    vertices.getAll().forEach(vertex -> vertex.getActor().tell(new CoreControl.Disconnect(msg.id)));
                    var removed = vertices.removeVertex(msg.id);
                    removed.getActor().tell(new CoreControl.Stop());
                    subscriptionsBySessionId.values().removeIf(id -> id.equals(msg.id));
                    edgesById.values().removeIf(edge -> edge.from().equals(msg.id) || edge.to().equals(msg.id));
                    return this;
                })
                .onMessage(ReceiveMsg.class, msg -> {
                    if (msg.name == null || msg.body == null) {
                        return this;
                    }
                    vertices.getVerticesByName(msg.name)
                            .forEach(vertex -> vertex.getActor().tell(new CoreConsumer.Message(msg.body)));
                    return this;
                })
                .onMessage(LoadCode.class, msg -> {
                    var vertex = vertices.getVertexDescriptionById(msg.id);
                    vertex.setCode(msg.code);
                    vertex.getActor().tell(new CoreControl.LoadCode(msg.replyTo, msg.code));
                    return this;
                })
                .onMessage(LoadName.class, msg -> {
                    var vertex = vertices.changeVertexName(msg.id, msg.name);
                    vertex.getActor().tell(new CoreControl.LoadName(msg.replyTo, msg.name));
                    return this;
                })
                .onMessage(LinkVertices.class, msg -> {
                    String id = UUID.randomUUID().toString();
                    linkVertices(id, msg.from, msg.to);
                    LOGGER.info("linked ");
                    msg.replyTo.tell(new LinkReply("Success", id));
                    return this;
                })
                .onMessage(RequestMetrics.class, msg -> {
                    var vertActors = vertices.getAll();
                    getContext().spawnAnonymous(Aggregator.create(
                            CoreMetrics.MetricsWithId.class,
                            replyTo -> vertActors.forEach(vertex -> vertex.getActor().tell(new CoreMetrics.GetMetrics(replyTo))),
                            vertActors.size(),
                            getContext().getSelf(),
                            replies -> {
                                var metrics = replies.stream()
                                        .collect(Collectors.toMap(CoreMetrics.MetricsWithId::vertexId,
                                                CoreMetrics.MetricsWithId::metrics));
                                return new VertexMetricsAggregator(metrics);
                            },
                            Duration.ofSeconds(5)));
                    return this;
                })
                .onMessage(VertexMetricsAggregator.class, msg -> {
                    var reply = new WebSocketMessage.MetricsAggregate("metrics", msg);
                    socketsBySessionId.values().forEach(socket -> socket.tell(reply));
                    return this;
                })


                .onMessage(LogSubscribe.class, msg -> {
                    var subscription = subscriptionsBySessionId.get(msg.sessionId);
                    if (subscription != null) {
                        // close previous subscription, if it exists.
                        var oldVertex = vertices.getVertexById(subscription);
                        oldVertex.tell(new CoreLog.UnsubscribeLog());
                    }
                    if (msg.id != null) {
                        subscriptionsBySessionId.put(msg.sessionId, msg.id);
                        ActorRef<VertexMessage> newVertex = vertices.getVertexById(msg.id);
                        newVertex.tell(new CoreLog.SubscribeLog(logMessageAdapter));
                    } else {
                        // socket closed, clean up session
                        subscriptionsBySessionId.remove(msg.sessionId);
                        socketsBySessionId.remove(msg.sessionId);
                    }
                    return this;
                })
                .onMessage(WrappedLog.class, msg -> {
                    CoreLog.LogMessage message = msg.message;
                    var id = message.computeId();
                    subscriptionsBySessionId.entrySet().stream()
                            .filter(entry -> entry.getValue().equals(id))
                            .map(Map.Entry::getKey)
                            .map(socketsBySessionId::get)
                            .forEach(socket -> socket.tell(new WebSocketMessage.Log("log", message)));
                    return this;
                })
                .onMessage(RegisterControlWebSocket.class, msg -> {
                    getContext().getLog().info("Register ws: {}", msg);
                    socketsBySessionId.put(msg.sessionId, msg.socket);
                    return this;
                })
                .build();
    }

    private void linkVertices(String id, String from, String to) {
        ActorRef<VertexMessage> source = vertices.getVertexById(from);
        ActorRef<VertexMessage> target = vertices.getVertexById(to);
        source.tell(new CoreControl.Connect(to, target));
        edgesById.put(id, new SystemDescription.Edge(id, from, to));
    }

    private VertexDescription createVertex(String id, String name, String code) {
        String vertName;
        if (name == null || name.isBlank()) {
            vertName = nameGenerator.generateName();
        } else {
            vertName = name;
        }
        ActorRef<VertexMessage> vert = getContext().spawn(Core.create(id, vertName, code), id);
        vertices.addVertex(new Vertex(vert, id, name, code));
        return new VertexDescription(id, vertName, code);
    }
}
