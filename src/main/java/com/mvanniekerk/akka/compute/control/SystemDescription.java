package com.mvanniekerk.akka.compute.control;

import com.mvanniekerk.akka.compute.control.graph.Edge;
import com.mvanniekerk.akka.compute.vertex.VertexDescription;

import java.util.List;

public record SystemDescription(List<VertexDescription> vertices, List<Edge> edges) {}
