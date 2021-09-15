package com.mvanniekerk.akka.compute.vertex;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.function.Function;

public interface Compiler {
    Function<JsonNode, JsonNode> compile(String code);
}
