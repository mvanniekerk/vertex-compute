package com.mvanniekerk.akka.compute.vertex;

import java.util.function.Function;

public class CompilerSimplistic implements Compiler {
    @Override
    public Function<String, String> compile(String code) {
        if (code.equals("split")) {
            return text -> {
                return "";
            };
        }
        return text -> text;
    }
}
