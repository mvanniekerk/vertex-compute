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
import akka.http.javadsl.server.*;
import akka.stream.javadsl.Flow;
import com.mvanniekerk.akka.compute.control.Control;
import com.mvanniekerk.akka.compute.control.SystemDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

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
    private record LoadCode(String code) {}
    private record Link(String source, String target) {}

    private Route createRoute() {
        Route route = concat(
                path("state", this::stateRoute),
                path("createvertex", this::createVertexRoute),
                pathPrefix("send", this::sendRoute),
                pathPrefix("code", this::loadCodeRoute),
                path("link", this::linkRoute),
                pathPrefix("log", this::logRoute)
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

    private Route wsRoute() {
        return handleWebSocketMessages(wsHandler());
    }

    private Flow<Message, Message, NotUsed> wsHandler() {
        return null;
    }

    private Route logRoute() {
        return post(() -> path(id -> {
            control.tell(new Control.LogSubscribe(id));
            return complete(StatusCodes.OK, "subscribed", Jackson.marshaller());
        }));
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

    private Route loadCodeRoute() {
        return post(() -> path(id -> entity(Jackson.unmarshaller(LoadCode.class), code -> {
            CompletionStage<Control.LoadCodeReply> reply = AskPattern.ask(
                    control,
                    replyTo -> new Control.LoadCode(replyTo, id, code.code),
                    TIMEOUT,
                    system.scheduler());
            return onSuccess(reply, content -> complete(StatusCodes.OK, content, Jackson.marshaller()));
        })));
    }

    private Route sendRoute() {
        return post(() -> path(id -> entity(genericJsonUnmarshaller(), body -> {
            control.tell(new Control.ReceiveHttp(id, body));
            return complete(StatusCodes.OK, "message sent", Jackson.marshaller());
        })));
    }

    private Route createVertexRoute() {
        return post(() -> entity(Jackson.unmarshaller(CreateVertex.class), body -> {
            CompletionStage<Control.VertexReply> reply = AskPattern.ask(
                    control,
                    replyTo -> new Control.CreateVertex(replyTo, body.name, body.code),
                    TIMEOUT,
                    system.scheduler());
            return onSuccess(reply, id -> complete(StatusCodes.OK, id, Jackson.marshaller()));
        }));
    }

    private Route stateRoute() {
        return get(() -> {
            CompletionStage<SystemDescription> reply = AskPattern.ask(
                    control,
                    Control.GetStateRequest::new,
                    TIMEOUT,
                    system.scheduler());
            return onSuccess(reply, description -> complete(StatusCodes.OK, description, Jackson.marshaller()));
        });
    }
}
