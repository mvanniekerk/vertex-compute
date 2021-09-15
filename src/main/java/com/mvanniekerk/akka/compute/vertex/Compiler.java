package com.mvanniekerk.akka.compute.vertex;

import java.util.function.Function;

public interface Compiler {
    Function<String, String> compile(String code);
}
