package com.mvanniekerk.akka.compute;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.server.*;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorSink;
import akka.stream.typed.javadsl.ActorSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mvanniekerk.akka.compute.control.Control;
import com.mvanniekerk.akka.compute.control.SystemDescription;
import com.mvanniekerk.akka.compute.control.WebSocketMessage;
import com.mvanniekerk.akka.compute.vertex.VertexDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import static akka.http.javadsl.server.PathMatchers.separateOnSlashes;
import static ch.megard.akka.http.cors.javadsl.CorsDirectives.cors;
import static ch.megard.akka.http.cors.javadsl.CorsDirectives.corsRejectionHandler;
import static com.mvanniekerk.akka.compute.util.HttpObjectMapper.genericJsonUnmarshaller;

public class HttpServerVector extends AllDirectives {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVector.class);

    public static final Duration TIMEOUT = Duration.ofSeconds(3);
    private final ActorSystem<Control.Message> system;
    private final ActorRef<Control.Message> control;

    public HttpServerVector(ActorSystem<Control.Message> system) {
        this.system = system;
        this.control = system;
    }

    public static void main(String[] args) throws IOException {
        ActorSystem<Control.Message> system = ActorSystem.create(Control.create(), "control");

        Http http = Http.get(system);

        HttpServerVector app = new HttpServerVector(system);

        CompletionStage<ServerBinding> binding = http.newServerAt("localhost", 8080)
                .bind(app.createRoute());

        System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
        System.in.read(); // let it run until user presses return

        binding
                .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
                .thenAccept(unbound -> system.terminate()); // and shutdown when done
    }

    private record CreateVertex(String name, String code) {}
    private record DeleteVertex(String id, String name) {}
    private record LoadCode(String code) {}
    private record LoadName(String name) {}
    private record Link(String source, String target) {}

    /*
    /graph - GET the graph
    /graph - POST load a graph

    /graph/vertex - POST create a vertex (returns ID)
    /graph/vertex/{id} - DELETE a vertex
    /graph/vertex/{id}/code - POST update the source code
    /graph/vertex/{id}/name - POST update the name
    /graph/edge - POST create an edge

    /send/{name} - POST a message to a vertex
    /sendws - open a WS connection for sending messages directly to vertices
    /ws - open a WS connection for subscribing to log messages and metrics
     */

    private Route createRoute() {
        Route route = concat(
                path(separateOnSlashes("graph/vertex"), () -> post(this::createVertexRoute)),
                path("graph", () -> concat(get(this::getGraphRoute), post(this::postGraphRoute))),
                pathPrefix(separateOnSlashes("graph/vertex"), () -> concat(
                        delete(this::deleteVertexRoute),
                        pathPrefix(id -> concat(
                                path("code", () -> post(() -> loadCodeRoute(id))),
                                path("name", () -> post(() -> loadNameRoute(id)))
                        ))
                )),
                path(separateOnSlashes("graph/edge"), () -> post(this::linkRoute)),
                pathPrefix("send", this::sendRoute),
                path("sendws", this::sendWsRoute),
                path("ws", this::wsRoute)
        );

        final RejectionHandler rejectionHandler = corsRejectionHandler().withFallback(RejectionHandler.defaultHandler());

        final ExceptionHandler exceptionHandler = ExceptionHandler.newBuilder()
                .match(NoSuchElementException.class, ex -> complete(StatusCodes.NOT_FOUND, ex.getMessage(), Jackson.marshaller()))
                .build();

        // Combining the two handlers only for convenience
        final Function<Supplier<Route>, Route> handleErrors = inner -> Directives.allOf(
                s -> handleExceptions(exceptionHandler, s),
                s -> handleRejections(rejectionHandler, s),
                inner
        );

        return handleErrors.apply(() -> cors(() -> handleErrors.apply(() -> route)));
    }

    private Route sendWsRoute() {
        Source<Message, NotUsed> source = Source.never();
        var flow = Flow.fromSinkAndSource(createSendMessageSink(), source);

        return handleWebSocketMessages(flow);
    }

    private Sink<Message, NotUsed> createSendMessageSink() {
        Sink<Control.Message, NotUsed> actorSink = ActorSink.actorRef(control,
                new Control.ReceiveMsg(null, null),
                error -> new Control.ReceiveMsg("fail", null));

        var objectMapper = new ObjectMapper();
        return Flow.of(Message.class)
                .map(msg -> msg.asTextMessage().getStrictText())
                .<Control.Message>map(text -> objectMapper.readValue(text, Control.ReceiveMsg.class))
                .toMat(actorSink, Keep.right());
    }

    private Route wsRoute() {
        return handleWebSocketMessages(createControlWebsocketFlow());
    }

    private Flow<Message, Message, NotUsed> createControlWebsocketFlow() {
        var sessionId = UUID.randomUUID().toString();
        var source = createLogMessageActorSource(sessionId);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        Sink<Message, NotUsed> sink = createLogMessageSink(sessionId);

        var flow = Flow.fromSinkAndSource(sink, source);

        return flow.map(objectMapper::writeValueAsString)
                .map((akka.japi.function.Function<String, Message>) TextMessage::create);
    }

    private Sink<Message, NotUsed> createLogMessageSink(String sessionId) {
        Sink<Control.Message, NotUsed> actorSink = ActorSink.actorRef(control,
                new Control.LogSubscribe(sessionId, null),
                error -> new Control.LogSubscribe(sessionId, null));

        return Flow.of(Message.class)
                .map(msg -> msg.asTextMessage().getStrictText())
                .<Control.Message>map(text -> new Control.LogSubscribe(sessionId, text))
                .toMat(actorSink, Keep.right());
    }

    private Source<WebSocketMessage, NotUsed> createLogMessageActorSource(String sessionId) {
        Source<WebSocketMessage, ActorRef<WebSocketMessage>> source = ActorSource.actorRef(
                msg -> false,
                msg -> Optional.empty(),
                500,
                OverflowStrategy.dropTail());
        var actorRefAndSource = source.preMaterialize(system);
        control.tell(new Control.RegisterControlWebSocket(sessionId, actorRefAndSource.first()));
        return actorRefAndSource.second();
    }

    private Route linkRoute() {
        return post(() -> entity(Jackson.unmarshaller(Link.class), body -> {
            CompletionStage<Control.LinkReply> reply = AskPattern.ask(
                    control,
                    replyTo -> new Control.LinkVertices(replyTo, body.source, body.target),
                    TIMEOUT,
                    system.scheduler());
            return onSuccess(reply, id -> complete(StatusCodes.OK, id, Jackson.marshaller()));
        }));
    }

    private Route loadCodeRoute(String id) {
        return entity(Jackson.unmarshaller(LoadCode.class), code -> {
            CompletionStage<VertexDescription> reply = AskPattern.ask(
                    control,
                    replyTo -> new Control.LoadCode(replyTo, id, code.code),
                    TIMEOUT,
                    system.scheduler());
            return onSuccess(reply, content -> complete(StatusCodes.OK, content, Jackson.marshaller()));
        });
    }

    private Route loadNameRoute(String id) {
        return entity(Jackson.unmarshaller(LoadName.class), code -> {
            CompletionStage<VertexDescription> reply = AskPattern.ask(
                    control,
                    replyTo -> new Control.LoadName(replyTo, id, code.name),
                    TIMEOUT,
                    system.scheduler());
            return onSuccess(reply, content -> complete(StatusCodes.OK, content, Jackson.marshaller()));
        });
    }

    private Route sendRoute() {
        return post(() -> path(name -> entity(genericJsonUnmarshaller(), body -> {
            control.tell(new Control.ReceiveMsg(name, body));
            return complete(StatusCodes.OK, "message sent", Jackson.marshaller());
        })));
    }

    private Route createVertexRoute() {
        return entity(Jackson.unmarshaller(CreateVertex.class), body -> {
            CompletionStage<Control.VertexReply> reply = AskPattern.ask(
                    control,
                    replyTo -> new Control.CreateVertex(replyTo, body.name, body.code),
                    TIMEOUT,
                    system.scheduler());
            return onSuccess(reply, id -> complete(StatusCodes.OK, id, Jackson.marshaller()));
        });
    }

    private Route deleteVertexRoute() {
        return path(id -> {
            control.tell(new Control.DeleteVertex(id));
            return complete(StatusCodes.OK, "vertex deleted", Jackson.marshaller());
        });
    }

    private Route postGraphRoute() {
        return post(() -> entity(Jackson.unmarshaller(SystemDescription.class), body -> {
            control.tell(new Control.LoadStateRequest(body));
            CompletionStage<SystemDescription> reply = AskPattern.ask(
                    control,
                    Control.GetStateRequest::new,
                    TIMEOUT,
                    system.scheduler());
            return onSuccess(reply, description -> complete(StatusCodes.OK, description, Jackson.marshaller()));
        }));
    }

    private Route getGraphRoute() {
        CompletionStage<SystemDescription> reply = AskPattern.ask(
                control,
                Control.GetStateRequest::new,
                TIMEOUT,
                system.scheduler());
        return onSuccess(reply, description -> complete(StatusCodes.OK, description, Jackson.marshaller()));
    }
}
