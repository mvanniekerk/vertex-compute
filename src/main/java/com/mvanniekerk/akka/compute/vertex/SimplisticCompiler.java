package com.mvanniekerk.akka.compute.vertex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class SimplisticCompiler implements Compiler {
    @Override
    public Function<JsonNode, JsonNode> compile(String code) {
        if (code.equals("split")) {
            return json -> {
                String text = json.asText();
                List<String> lines = Arrays.asList(text.split("\n"));
                return new ObjectMapper().valueToTree(lines);
            };
        }
        return text -> text;
    }
}
