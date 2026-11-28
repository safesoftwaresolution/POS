package com.sciencebot.pos.voicesales.internal.ports;

import com.sciencebot.pos.voicesales.VoiceAgentRequest;

import java.util.List;

/**
 * Runs the model-and-tools loop for one turn. {@code tools} is an object whose {@code @Tool} methods the model may
 * call; the implementation owns the provider, the model and the iteration bound. It must never receive store, user
 * or permission data.
 */
@FunctionalInterface
public interface AgentModel {
    String run(String system, List<VoiceAgentRequest.Turn> history, String userText, Object tools);
}
