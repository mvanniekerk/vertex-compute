package com.mvanniekerk.akka.compute.compute;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mvanniekerk.akka.compute.vertex.Core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;

public class NumberSource extends ComputeCore {

    private record NumberMessage(long number, ZonedDateTime sentTime) {}

    private long number = 0;

    public NumberSource(Core consumer) {
        super(consumer);
        schedulePeriodic("numbers", Duration.ofSeconds(1), () -> {
            ZonedDateTime now = ZonedDateTime.now();
            log("Generated number " + number);
            send(new NumberMessage(number++, now));
        });
    }

    @Override
    public void receive(JsonNode message) {
        // NOOP
    }

    @Override
    public String getName() {
        return NumberSource.class.getSimpleName();
    }
}
