package com.mvanniekerk.akka.compute.compute.synth;

import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.compute.ComputeCore;
import com.mvanniekerk.akka.compute.vertex.Core;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class SoundSink extends ComputeCore {
    static final int SAMPLE_RATE = 44100;

    private final Player player;
    private boolean started = false;

    public SoundSink(Core consumer) {
        super(consumer);
        try {
            player = new Player(SAMPLE_RATE);
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void receive(JsonNode message) {
        var soundBuffer = convert(message, SoundBuffer.class);
        if (!started && soundBuffer.frameNr() > 0) {
            new Thread(player).start();
            started = true;
            log("Started the dedicated sound thread...");
        }
        player.writeToBuffer(asBytes(soundBuffer.buffer()));
    }

    private static byte[] asBytes(double[] in) {
        var output = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            output[i] = (byte) (in[i] * 127f);
        }
        return output;
    }

    private static class Player implements Runnable {
        private final int bufferSize;
        private boolean isRunning = true;
        private final SourceDataLine line;

        public void writeToBuffer(byte[] soundBuffer) {
            int left = bufferSize - line.available();
            if (left == 0) {
                System.out.println("Warning!! No samples left...");
            }
            line.write(soundBuffer, 0, soundBuffer.length);
        }

        public Player(int bufferSize) throws LineUnavailableException {
            final AudioFormat af = new AudioFormat(SAMPLE_RATE, 8, 1, true, true);
            line = AudioSystem.getSourceDataLine(af);
            this.bufferSize = bufferSize;
            line.open(af, bufferSize);
        }

        public void run() {
            line.start();
            while (isRunning) {
                line.drain();
                System.out.println("Ran out of data to drain");
            }
            line.close();
        }

        public void stop() {
            isRunning = false;
        }
    }
}
