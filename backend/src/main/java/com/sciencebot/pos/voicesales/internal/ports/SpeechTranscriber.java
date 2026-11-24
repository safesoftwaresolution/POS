package com.sciencebot.pos.voicesales.internal.ports;

@FunctionalInterface
public interface SpeechTranscriber {
    String transcribe(byte[] audio, String mediaType);
}
