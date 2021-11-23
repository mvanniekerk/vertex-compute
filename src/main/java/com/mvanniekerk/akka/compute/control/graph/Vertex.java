package com.mvanniekerk.akka.compute.control.graph;

import akka.actor.typed.ActorRef;
import com.mvanniekerk.akka.compute.vertex.VertexDescription;
import com.mvanniekerk.akka.compute.vertex.VertexMessage;

public class Vertex {
    private final String id;
    private final ActorRef<VertexMessage> actor;

    private String name;
    private String code;

    public Vertex(ActorRef<VertexMessage> vertex, String id, String name, String code) {
        this.id = id;
        this.actor = vertex;
        this.name = name;
        this.code = code;
    }

    public String getId() {
        return id;
    }

    public ActorRef<VertexMessage> getActor() {
        return actor;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public VertexDescription describe() {
        return new VertexDescription(id, name, code);
    }

    @Override
    public String toString() {
        return describe().toString();
    }
}
