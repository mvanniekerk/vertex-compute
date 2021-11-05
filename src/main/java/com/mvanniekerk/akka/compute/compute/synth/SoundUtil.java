package com.mvanniekerk.akka.compute.compute.synth;

import java.util.Arrays;

public class SoundUtil {

    public static final int MSG_INTERVAL_MS = 20;
    static final int SAMPLE_RATE = 44100;

    private SoundUtil() {
    }

    static double calculateFrequency(int noteValue) {
        final var a = Math.pow(2, 1. / 12);
        return 440 * Math.pow(a, noteValue - 48);
    }

    static double[] sumArray(double[] left, double[] right) {
        double[] result = new double[left.length];
        for (int i = 0; i < left.length; i++) {
            result[i] = left[i] + right[i];
        }
        return result;
    }

    static double[] multArray(double left, double[] right) {
        var output = new double[right.length];
        for (int i = 0; i < right.length; i++) {
            output[i] = right[i] * left;
        }
        return output;
    }

    static double[] multArray(double[] left, double[] right) {
        var output = new double[left.length];
        for (int i = 0; i < left.length; i++) {
            output[i] = right[i] * left[i];
        }
        return output;
    }

    static double[] linear(int ms, double start, double end) {
        int samples = (ms * SAMPLE_RATE) / 1000;
        var output = new double[samples];
        for (int i = 0; i < output.length; i++) {
            output[i] = start + (end - start) * i / samples;
        }
        return output;
    }

    static double[] silent(int ms) {
        int samples = (ms * SAMPLE_RATE) / 1000;
        var doubles = new double[samples];
        Arrays.fill(doubles, 0);
        return doubles;
    }
}
