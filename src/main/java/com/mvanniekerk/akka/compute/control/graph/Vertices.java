package com.mvanniekerk.akka.compute.control.graph;

import akka.actor.typed.ActorRef;
import com.mvanniekerk.akka.compute.vertex.VertexMessage;

import java.util.*;
import java.util.stream.Collectors;

public class Vertices {
    private final Map<String, Vertex> verticesById = new HashMap<>();
    private final Map<String, List<Vertex>> verticesByName = new HashMap<>();

    public void addVertex(Vertex vertex) {
        if (verticesById.containsKey(vertex.getId())) {
            throw new IllegalArgumentException("Vertex with ID " + vertex.getId() + " already exists.");
        }
        verticesById.put(vertex.getId(), vertex);
        verticesByName.computeIfAbsent(vertex.getName(), k -> new ArrayList<>()).add(vertex);
    }

    public Vertex removeVertex(String id) {
        var removed = verticesById.remove(id);
        var vertices = verticesByName.get(removed.getName());
        vertices.removeIf(vertex -> vertex.getId().equals(id));
        return removed;
    }

    public List<Vertex> getVerticesByName(String name) {
        return verticesByName.get(name);
    }

    public ActorRef<VertexMessage> getVertexById(String id) {
        return verticesById.get(id).getActor();
    }

    public Vertex getVertexDescriptionById(String id) {
        return verticesById.get(id);
    }

    public List<Vertex> getAll() {
        return new ArrayList<>(verticesById.values());
    }

    public Vertex changeVertexName(String id, String newName) {
        var updated = verticesById.get(id);
        var oldName = updated.getName();
        var vertices = verticesByName.get(oldName);
        vertices.removeIf(vertex -> vertex.getId().equals(id));
        verticesByName.computeIfAbsent(newName, k -> new ArrayList<>()).add(updated);
        updated.setName(newName);
        return updated;
    }

    public String toString() {
        return verticesById.values().stream()
                .sorted(Comparator.comparing(Vertex::getId))
                .collect(Collectors.toList())
                .toString();
    }
}
