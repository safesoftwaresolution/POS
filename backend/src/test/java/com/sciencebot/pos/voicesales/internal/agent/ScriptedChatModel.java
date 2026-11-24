package com.sciencebot.pos.voicesales.internal.agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Deterministic stand-in for a provider: replays a script of replies, each one either tool calls or final text.
 * It drives the real tool loop, so tests exercise the same code path as production without network or keys.
 */
final class ScriptedChatModel implements ChatModel {
    /** A scripted step. Use {@link #calls} or {@link #say}. */
    record Step(List<AssistantMessage.ToolCall> toolCalls, String text) {}

    static Step say(String text) {
        return new Step(List.of(), text);
    }

    static Step calls(Call... calls) {
        List<AssistantMessage.ToolCall> list = new ArrayList<>();
        for (int i = 0; i < calls.length; i++) {
            list.add(new AssistantMessage.ToolCall("call-" + i, "function", calls[i].name(), calls[i].jsonArguments()));
        }
        return new Step(list, "");
    }

    record Call(String name, String jsonArguments) {}

    static Call call(String name, String jsonArguments) {
        return new Call(name, jsonArguments);
    }

    private final Function<Integer, Step> script;
    private final List<Prompt> prompts = new ArrayList<>();

    ScriptedChatModel(Step... steps) {
        this.script = index -> index < steps.length ? steps[index] : steps[steps.length - 1];
    }

    List<Prompt> prompts() {
        return prompts;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Step step = script.apply(prompts.size());
        prompts.add(prompt);
        AssistantMessage message = AssistantMessage.builder().content(step.text()).toolCalls(step.toolCalls()).build();
        return new ChatResponse(List.of(new Generation(message)));
    }
}
