package com.mvanniekerk.akka.compute.compute;

import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.vertex.Core;

import java.time.Duration;
import java.time.ZonedDateTime;

public class NumberSource extends ComputeCore {
    private record NumberMessage(long number, ZonedDateTime sentTime) {}

    private long number = 0;
    private double rate = 1.0;

    public NumberSource(Core consumer, String[] args) {
        super(consumer);
        if (args.length == 1) {
            rate = Double.parseDouble(args[0]);
        }
        schedulePeriodic("numbers", Duration.ofMillis((long) (1000 * rate)), () -> {
            ZonedDateTime now = ZonedDateTime.now();
            log("Generated number " + number);
            send(new NumberMessage(number++, now));
        });
    }

    @Override
    public void receive(JsonNode message) {
        // NOOP
    }

}
