package com.mvanniekerk.akka.compute.compute.synth;

import static com.mvanniekerk.akka.compute.compute.synth.SoundSink.SAMPLE_RATE;

public class SoundUtil {

    public static final int MSG_INTERVAL_MS = 50;

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

    static double[] linearUp(int ms) {
        int samples = (ms * SAMPLE_RATE) / 1000;
        var output = new double[samples];
        for (int i = 0; i < output.length; i++) {
            output[i] = 1.0 * i / samples;
        }
        return output;
    }

    static double[] linearDown(int ms) {
        int samples = (ms * SAMPLE_RATE) / 1000;
        var output = new double[samples];
        for (int i = 0; i < output.length; i++) {
            output[i] = 1 - 1.0 * i / samples;
        }
        return output;
    }

    static double[] silent(int ms) {
        int samples = (ms * SAMPLE_RATE) / 1000;
        return new double[samples];
    }
}
