package com.mvanniekerk.akka.compute.vertex;

import com.mvanniekerk.akka.compute.compute.ComputeCore;
import com.mvanniekerk.akka.compute.compute.NoopCompute;
import com.mvanniekerk.akka.compute.compute.Splitter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class StaticClassNameCompiler implements Compiler {


    private final Map<String, Function<Core, ComputeCore>> computeBuilders;

    public StaticClassNameCompiler() {
        computeBuilders = new HashMap<>();
        computeBuilders.put(NoopCompute.class.getSimpleName(), NoopCompute::new);
        computeBuilders.put(Splitter.class.getSimpleName(), Splitter::new);
    }

    @Override
    public Function<Core, ComputeCore> compile(String code) {
        Function<Core, ComputeCore> computeBuilder = computeBuilders.get(code);
        if (computeBuilder == null) {
            return NoopCompute::new;
        }
        return computeBuilder;
    }
}
