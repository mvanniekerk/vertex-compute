package com.mvanniekerk.akka.compute;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.*;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import com.mvanniekerk.akka.compute.control.Control;
import com.mvanniekerk.akka.compute.control.SystemDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import static ch.megard.akka.http.cors.javadsl.CorsDirectives.cors;
import static ch.megard.akka.http.cors.javadsl.CorsDirectives.corsRejectionHandler;
import static com.mvanniekerk.akka.compute.HttpObjectMapper.genericJsonUnmarshaller;

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

    private record CreateVertex(String name) {}
    private record LoadCode(String code) {}
    private record Link(String source, String target) {}

    private Route createRoute() {
        Route route = concat(
                path("state", () ->
                        get(() -> {
                            CompletionStage<SystemDescription> reply = AskPattern.ask(
                                    control,
                                    Control.GetStateRequest::new,
                                    TIMEOUT,
                                    system.scheduler());
                            return onSuccess(reply, description -> complete(StatusCodes.OK, description, Jackson.marshaller()));
                        })),
                path("createvertex", () ->
                        post(() -> entity(Jackson.unmarshaller(CreateVertex.class), body -> {
                            CompletionStage<Control.VertexReply> reply = AskPattern.ask(
                                    control,
                                    replyTo -> new Control.CreateVertex(replyTo, body.name),
                                    TIMEOUT,
                                    system.scheduler());
                            return onSuccess(reply, id -> complete(StatusCodes.OK, id, Jackson.marshaller()));
                        }))),
                pathPrefix("send", () ->
                        post(() -> path(name -> entity(genericJsonUnmarshaller(), body -> {
                            control.tell(new Control.ReceiveHttp(name, body));
                            return complete(StatusCodes.OK, "message sent", Jackson.marshaller());
                        })))),
                pathPrefix("loadCode", () ->
                        post(() -> path(name -> entity(Jackson.unmarshaller(LoadCode.class), code -> {
                            control.tell(new Control.LoadCode(name, code.code));
                            return complete(StatusCodes.OK, "code sent", Jackson.marshaller());
                        })))),
                path("link", () ->
                        post(() -> entity(Jackson.unmarshaller(Link.class), body -> {
                            CompletionStage<Control.LinkReply> reply = AskPattern.ask(
                                    control,
                                    replyTo -> new Control.LinkVertices(replyTo, body.source, body.target),
                                    TIMEOUT,
                                    system.scheduler());
                            return onSuccess(reply, id -> complete(StatusCodes.OK, id, Jackson.marshaller()));
                        })))
        );


        // Your rejection handler
        final RejectionHandler rejectionHandler = corsRejectionHandler().withFallback(RejectionHandler.defaultHandler());

        // Your exception handler
        final ExceptionHandler exceptionHandler = ExceptionHandler.newBuilder()
                .match(NoSuchElementException.class, ex -> complete(StatusCodes.NOT_FOUND, ex.getMessage()))
                .build();

        // Combining the two handlers only for convenience
        final Function<Supplier<Route>, Route> handleErrors = inner -> Directives.allOf(
                s -> handleExceptions(exceptionHandler, s),
                s -> handleRejections(rejectionHandler, s),
                inner
        );

        return handleErrors.apply(() -> cors(() -> handleErrors.apply(() -> route)));
    }
}
