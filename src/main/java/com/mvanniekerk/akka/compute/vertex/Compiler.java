package com.mvanniekerk.akka.compute.vertex;

import com.mvanniekerk.akka.compute.compute.ComputeCore;

import java.util.function.Function;

public interface Compiler {
    Function<Core, ComputeCore> compile(String code);
}
