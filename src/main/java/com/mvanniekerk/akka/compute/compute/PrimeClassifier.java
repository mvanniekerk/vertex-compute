package com.mvanniekerk.akka.compute.compute;

import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.vertex.Core;

public class PrimeClassifier extends ComputeCore {

    public record PrimeMessage(long number) {}

    public PrimeClassifier(Core consumer) {
        super(consumer);
        log("Starting the prime classifier");
    }

    @Override
    public void receive(JsonNode message) {
        var numberNode = message.get("number");
        if (numberNode != null && numberNode.isLong()) {
            var number = numberNode.asLong();
            if (isPrime(number)) {
                send(new PrimeMessage(number));
            }
        }
    }

    private boolean isPrime(long number) {
        if (number < 2) {
            return false;
        }
        for (long i = 2; i < number; i++) {
            if (number % i == 0) {
                return false;
            }
        }
        return true;
    }

}
