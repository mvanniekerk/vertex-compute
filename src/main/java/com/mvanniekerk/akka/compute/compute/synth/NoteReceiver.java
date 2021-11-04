package com.mvanniekerk.akka.compute.compute.synth;

import com.fasterxml.jackson.databind.JsonNode;
import com.mvanniekerk.akka.compute.compute.ComputeCore;
import com.mvanniekerk.akka.compute.vertex.Core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NoteReceiver extends ComputeCore {
    private record NoteAction(String action, int midiNumber) {}
    private record Note(int midiNumber, long startFrame, long endFrame, boolean isPressed) {}
    private record Instruction(int midiNumber, double startVolume, double endVolume) {}

    private final Map<Integer, Note> notesByMidi = new HashMap<>();

    private long frameNr = 0;

    private int attack = 3;
    private int decay = 2;
    private double sustain = 0.5;
    private int release = 5;

    public NoteReceiver(Core consumer, String[] args) {
        super(consumer);

        schedulePeriodic("notes", Duration.ofMillis(SoundUtil.MSG_INTERVAL_MS), () -> {
            var instructions = notesByMidi.values().stream().map(this::getInstruction).collect(Collectors.toList());
            send(instructions);
            notesByMidi.values().removeIf(note -> !note.isPressed && frameNr - note.endFrame >= release - 1);
            frameNr++;
        });
    }

    private Instruction getInstruction(Note note) {
        double startVolume;
        double endVolume;
        var framesActive = frameNr - note.startFrame;
        if (framesActive < attack && note.isPressed) {
            startVolume = 1.0 * framesActive / attack;
            endVolume = 1.0 * (framesActive + 1) / attack;
        } else if (framesActive < attack + decay && note.isPressed) {
            startVolume = 1.0 - 1.0 * (framesActive - attack) / decay * sustain;
            endVolume = 1.0 - 1.0 * (framesActive - attack + 1) / decay * sustain;
        } else if (note.isPressed) {
            startVolume = sustain;
            endVolume = sustain;
        } else {
            var framesReleased = frameNr - note.endFrame;
            startVolume = sustain - 1.0 * framesReleased / release * sustain;
            endVolume = sustain - 1.0 * (framesReleased + 1) / release * sustain;
        }
        return new Instruction(note.midiNumber, startVolume, endVolume);
    }

    @Override
    public void receive(JsonNode message) {
        var noteAction = convert(message, NoteAction.class);
        if (noteAction.action.equals("play")) {
            notesByMidi.put(noteAction.midiNumber, new Note(noteAction.midiNumber, frameNr, -1, true));
        } else {
            var note = notesByMidi.get(noteAction.midiNumber);
            var updated = new Note(note.midiNumber, note.startFrame, frameNr, false);
            notesByMidi.put(note.midiNumber, updated);
        }
    }
}
