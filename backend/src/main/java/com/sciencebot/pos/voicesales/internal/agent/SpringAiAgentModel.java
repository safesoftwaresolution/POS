package com.sciencebot.pos.voicesales.internal.agent;

import com.sciencebot.pos.voicesales.VoiceAgentRequest;
import com.sciencebot.pos.voicesales.internal.ports.AgentModel;
import com.sciencebot.pos.voicesales.internal.ports.VoiceProviderException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI implementation of {@link AgentModel}. The tool loop is written out here (not delegated to the model
 * client) so the number of rounds is bounded by configuration and the loop can be exercised with a scripted
 * {@link ChatModel}. The provider, model and temperature come from {@code spring.ai.*}; a provider that speaks
 * the OpenAI protocol (Groq by default) needs no code change to be swapped.
 *
 * <p>The {@link ChatModel} is looked up per call: with {@code spring.ai.model.chat=none} the app starts normally
 * and the agent answers "unavailable" instead of failing at boot.
 */
@Component
@ConditionalOnProperty(name = {"voice-sales.enabled", "voice-sales.agent.enabled"}, havingValue = "true")
class SpringAiAgentModel implements AgentModel {
    private final ObjectProvider<ChatModel> chatModel;
    private final ToolCallingManager toolCalling = ToolCallingManager.builder().build();
    private final int maxIterations;

    SpringAiAgentModel(ObjectProvider<ChatModel> chatModel,
                       @Value("${voice-sales.agent.max-iterations:6}") int maxIterations) {
        if (maxIterations < 1) throw new IllegalArgumentException("voice-sales.agent.max-iterations must be positive");
        this.chatModel = chatModel;
        this.maxIterations = maxIterations;
    }

    @Override
    public String run(String system, List<VoiceAgentRequest.Turn> history, String userText, Object tools) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            throw new VoiceProviderException();
        }
        ToolCallback[] callbacks = ToolCallbacks.from(tools);
        var options = optionsWithTools(model, callbacks);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(system));
        for (VoiceAgentRequest.Turn turn : history) {
            messages.add("assistant".equals(turn.role()) ? new AssistantMessage(turn.text()) : new UserMessage(turn.text()));
        }
        messages.add(new UserMessage(userText));

        Prompt prompt = new Prompt(messages, options);
        for (int round = 0; round < maxIterations; round++) {
            var response = model.call(prompt);
            if (response == null || response.getResult() == null) {
                throw new VoiceProviderException();
            }
            if (!response.hasToolCalls()) {
                String text = response.getResult().getOutput().getText();
                return text == null ? "" : text;
            }
            ToolExecutionResult result = toolCalling.executeToolCalls(prompt, response);
            prompt = new Prompt(withoutReasoning(result.conversationHistory()), options);
        }
        // The model kept asking for tools: stop here; the ticket already holds whatever the tools applied.
        return "No pude terminar eso del todo. Revisa el ticket y dime qué falta.";
    }

    /**
     * Reasoning models (gpt-oss on Groq) return their chain of thought with the assistant message, and the client
     * sends it back on the next round as {@code reasoning_content}, which the provider rejects with a 400. Only the
     * text and the tool calls are needed to continue the conversation, so the rest is dropped.
     */
    private static List<Message> withoutReasoning(List<Message> history) {
        List<Message> clean = new ArrayList<>(history.size());
        for (Message message : history) {
            if (message instanceof AssistantMessage assistant) {
                clean.add(AssistantMessage.builder()
                        .content(assistant.getText())
                        .toolCalls(assistant.getToolCalls())
                        .build());
            } else {
                clean.add(message);
            }
        }
        return clean;
    }

    /**
     * Starts from the model's own default options (model name, temperature, provider settings) and adds the tools.
     * Provider clients such as OpenAI cast the prompt options to their own type, so generic options would fail
     * at runtime; models whose defaults are not tool-capable fall back to the generic builder.
     */
    private static ToolCallingChatOptions optionsWithTools(ChatModel model, ToolCallback[] callbacks) {
        ChatOptions defaults = model.getDefaultOptions();
        if (defaults != null && defaults.mutate() instanceof ToolCallingChatOptions.Builder<?> builder) {
            return builder.toolCallbacks(callbacks).build();
        }
        return ToolCallingChatOptions.builder().toolCallbacks(callbacks).build();
    }
}
