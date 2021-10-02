package com.mvanniekerk.akka.compute.control;

import com.mvanniekerk.akka.compute.vertex.VertexDescription;

import java.util.List;

public record SystemDescription(List<VertexDescription> vertices, List<Edge> edges) {
    public record Edge(String id, String from, String to) {}
}
