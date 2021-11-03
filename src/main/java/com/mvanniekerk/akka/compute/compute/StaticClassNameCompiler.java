package com.mvanniekerk.akka.compute.compute;

import com.mvanniekerk.akka.compute.vertex.Core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class StaticClassNameCompiler implements Compiler {


    private final Map<String, BiFunction<Core, String[], ComputeCore>> computeBuilders;

    public StaticClassNameCompiler() {
        computeBuilders = new HashMap<>();
        computeBuilders.put(NoopCompute.class.getSimpleName(), (core, args) -> new NoopCompute(core));
        computeBuilders.put(Splitter.class.getSimpleName(), (core, args) -> new Splitter(core));
        computeBuilders.put(Logger.class.getSimpleName(), (core, args) -> new Logger(core));
        computeBuilders.put(NumberSource.class.getSimpleName(), NumberSource::new);
        computeBuilders.put(PrimeClassifier.class.getSimpleName(), (core, args) -> new PrimeClassifier(core));
        computeBuilders.put(SoundSink.class.getSimpleName(), (core, args) -> new SoundSink(core));
        computeBuilders.put(NoteSynthesizer.class.getSimpleName(), NoteSynthesizer::new);
    }

    @Override
    public Function<Core, ComputeCore> compile(String code) {
        var words = code.split(" ");
        var rest = Arrays.copyOfRange(words, 1, words.length);
        var computeBuilder = computeBuilders.get(words[0]);
        if (computeBuilder == null) {
            return NoopCompute::new;
        }
        return core -> computeBuilder.apply(core, rest);
    }
}
