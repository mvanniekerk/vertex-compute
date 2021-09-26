package com.mvanniekerk.akka.compute.compute;

import com.mvanniekerk.akka.compute.vertex.Core;

import java.util.function.Function;

public interface Compiler {
    Function<Core, ComputeCore> compile(String code);
}
