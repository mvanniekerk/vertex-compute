package com.mvanniekerk.akka.compute;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.http.scaladsl.model.ErrorInfo;
import akka.http.scaladsl.model.ExceptionWithErrorInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvanniekerk.akka.compute.control.Control;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

import static com.mvanniekerk.akka.compute.HttpObjectMapper.jsonUnmarshaller;

public class HttpServerVector extends AllDirectives {

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

    private Route createRoute() {
        return concat(
                path("createVertex", () ->
                        post(() -> parameter("name", name -> {
                            control.tell(new Control.CreateVertex(name));
                            return complete(StatusCodes.OK, "vertex created");
                        }))),
                pathPrefix("send", () ->
                        post(() -> path(name -> entity(jsonUnmarshaller(), body -> {
                            control.tell(new Control.ReceiveHttp(name, body));
                            return complete(StatusCodes.OK, "message sent");
                        })))),
                pathPrefix("loadCode", () ->
                        post(() -> path(name -> entity(Unmarshaller.entityToString(), code -> {
                            control.tell(new Control.LoadCode(name, code));
                            return complete(StatusCodes.OK, "code sent");
                        })))),
                path("link", () ->
                        post(() -> parameter("source", source -> parameter("target", target -> {
                            control.tell(new Control.LinkVertices(source, target));
                            return complete(StatusCodes.OK, "vertices linked");
                        }))))
        );
    }
}
