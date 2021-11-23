package com.mvanniekerk.akka.compute.control;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class NameGenerator {

    private final List<String> adjectives;
    private final List<String> nouns;

    public NameGenerator() {
        adjectives = getLinesFromResource("adjectives.txt");
        nouns = getLinesFromResource("nouns.txt");
    }

    public String generateName() {
        var random = new Random();
        var nounIndex = random.nextInt(nouns.size());
        var adjectivesIndex = random.nextInt(adjectives.size());
        return adjectives.get(adjectivesIndex) + " " + nouns.get(nounIndex);
    }

    private static List<String> getLinesFromResource(String resource) {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        InputStream is = classloader.getResourceAsStream(resource);
        if (is == null) {
            return new ArrayList<>();
        }
        return new BufferedReader(new InputStreamReader(is)).lines()
                .map(word -> word.substring(0,1).toUpperCase() + word.substring(1))
                .collect(Collectors.toList());
    }
}
