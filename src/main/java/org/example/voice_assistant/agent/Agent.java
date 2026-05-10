package org.example.voice_assistant.agent;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.llm.LLMClient;
import org.example.voice_assistant.llm.LLMClientFactory;
import org.example.voice_assistant.llm.dto.QwenChatRequest;
import org.example.voice_assistant.llm.dto.QwenChatResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class Agent {

    private static final String TOOL_USAGE_POLICY =
            "\n\n【工具使用规则】你有一些可用的工具（functions）。重要规则：\n"
            + "1. 如果用户的问题与这些工具的功能无关，你必须直接使用自己的知识回答，不要调用任何工具。\n"
            + "2. 工具调用为空是完全可以接受的，不要强行使用工具。\n"
            + "3. 只有在用户的问题明确需要某个工具的功能时，才调用该工具。";

    private final LLMClientFactory llmClientFactory;
    private final ToolRegistry toolRegistry;

    public String run(String userInput, String systemPrompt) {
        log.info("Agent run: userInput={}", userInput);

        LLMClient llmClient = llmClientFactory.getDefaultClient();

        List<QwenChatRequest.ToolDef> toolDefs = buildToolDefs();
        log.info("Agent using {} tools", toolDefs.size());

        String systemPromptWithTools = buildSystemPromptWithToolPolicy(systemPrompt, toolDefs);

        QwenChatResponse response = llmClient.chatWithTools(userInput, systemPromptWithTools, toolDefs);

        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return llmClient.chat(userInput, systemPrompt);
        }

        QwenChatResponse.Choice choice = response.getChoices().get(0);
        QwenChatResponse.Message message = choice.getMessage();

        if (hasToolCalls(message)) {
            log.info("Agent detected tool_calls, executing tools...");
            return executeToolCallLoop(llmClient, userInput, systemPrompt, message, toolDefs);
        }

        if (message.getContent() != null) {
            return message.getContent();
        }

        return "抱歉，我无法处理您的请求。";
    }

    public void runStream(String userInput, String systemPrompt,
                          Consumer<String> onToken,
                          Consumer<String> onComplete,
                          Consumer<Exception> onError) {
        log.info("Agent runStream: userInput={}", userInput);

        LLMClient llmClient = llmClientFactory.getDefaultClient();

        List<QwenChatRequest.ToolDef> toolDefs = buildToolDefs();

        if (toolDefs == null || toolDefs.isEmpty()) {
            log.info("Agent no tools registered, direct chatStream");
            llmClient.chatStream(userInput, systemPrompt, onToken, onComplete, onError);
            return;
        }

        log.info("Agent using {} tools, checking for tool_calls...", toolDefs.size());

        QwenChatResponse response;
        try {
            String systemPromptWithTools = buildSystemPromptWithToolPolicy(systemPrompt, toolDefs);
            response = llmClient.chatWithTools(userInput, systemPromptWithTools, toolDefs);
        } catch (Exception e) {
            log.error("Agent chatWithTools failed, fallback to chatStream", e);
            llmClient.chatStream(userInput, systemPrompt, onToken, onComplete, onError);
            return;
        }

        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            llmClient.chatStream(userInput, systemPrompt, onToken, onComplete, onError);
            return;
        }

        QwenChatResponse.Choice choice = response.getChoices().get(0);
        QwenChatResponse.Message message = choice.getMessage();

        if (hasToolCalls(message)) {
            log.info("Agent detected tool_calls, executing tools then streaming...");
            executeToolCallLoopStream(llmClient, userInput, systemPrompt,
                    message, toolDefs, onToken, onComplete, onError);
            return;
        }

        if (message.getContent() != null) {
            log.info("Agent no tool_calls, LLM answered directly, using chatStream for streaming output");
            llmClient.chatStream(userInput, systemPrompt, onToken, onComplete, onError);
            return;
        }

        onError.accept(new RuntimeException("Agent: LLM returned empty response"));
    }

    private boolean hasToolCalls(QwenChatResponse.Message message) {
        return message != null
                && message.getTool_calls() != null
                && !message.getTool_calls().isEmpty();
    }

    /**
     * 执行工具调用循环：根据 assistant 的 tool_calls 执行工具，并将结果作为新的消息追加到对话中，继续调用 LLM 直到没有 tool_calls
     */
    private String executeToolCallLoop(LLMClient llmClient, String userInput,
                                       String systemPrompt,
                                       QwenChatResponse.Message assistantMessage,
                                       List<QwenChatRequest.ToolDef> toolDefs) {
        List<QwenChatRequest.Message> messages = executeToolsAndBuildMessages(
                userInput, systemPrompt, assistantMessage);
        return llmClient.chatWithMessages(messages, toolDefs);
    }

    private void executeToolCallLoopStream(LLMClient llmClient, String userInput,
                                           String systemPrompt,
                                           QwenChatResponse.Message assistantMessage,
                                           List<QwenChatRequest.ToolDef> toolDefs,
                                           Consumer<String> onToken,
                                           Consumer<String> onComplete,
                                           Consumer<Exception> onError) {
        List<QwenChatRequest.Message> messages = executeToolsAndBuildMessages(
                userInput, systemPrompt, assistantMessage);
        llmClient.chatWithMessagesStream(messages, toolDefs, onToken, onComplete, onError);
    }

    /**
     * 根据 assistant 的 tool_calls 执行工具，并返回新的消息列表
     */
    private List<QwenChatRequest.Message> executeToolsAndBuildMessages(
            String userInput, String systemPrompt,
            QwenChatResponse.Message assistantMessage) {
        List<QwenChatRequest.Message> messages = buildMultiTurnMessages(
                userInput, systemPrompt, assistantMessage);

        for (QwenChatResponse.ToolCall toolCall : assistantMessage.getTool_calls()) {
            QwenChatResponse.FunctionCall functionCall = toolCall.getFunction();
            if (functionCall == null) {
                log.warn("ToolCall without function: id={}", toolCall.getId());
                continue;
            }

            String toolName = functionCall.getName();
            String toolResult;

            try {
                Tool tool = toolRegistry.getTool(toolName);
                Map<String, Object> arguments = JSON.parseObject(functionCall.getArguments());
                log.info("Executing tool: {} with args: {}", toolName, arguments);
                toolResult = tool.execute(arguments);
                log.info("Tool result: {}", toolResult);
            } catch (Exception e) {
                log.error("Tool execution failed: {}", toolName, e);
                toolResult = "工具执行失败: " + e.getMessage();
            }

            QwenChatRequest.Message toolMessage = QwenChatRequest.Message.builder()
                    .role("tool")
                    .tool_call_id(toolCall.getId())
                    .content(toolResult)
                    .build();
            messages.add(toolMessage);
        }

        return messages;
    }

    /**
     * 构建多轮对话消息，用户输入 + 上一轮 assistant 的 tool_calls重新组装成下一轮 LLM 请求的 messages
     *
     * @param userInput
     * @param systemPrompt
     * @param assistantMessage
     * @return
     */
    private List<QwenChatRequest.Message> buildMultiTurnMessages(
            String userInput, String systemPrompt,
            QwenChatResponse.Message assistantMessage) {

        List<QwenChatRequest.Message> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(QwenChatRequest.Message.builder()
                    .role("system")
                    .content(systemPrompt)
                    .build());
        }

        messages.add(QwenChatRequest.Message.builder()
                .role("user")
                .content(userInput)
                .build());

        List<QwenChatRequest.ToolCall> toolCalls = new ArrayList<>();
        if (assistantMessage.getTool_calls() != null) {
            for (QwenChatResponse.ToolCall tc : assistantMessage.getTool_calls()) {
                toolCalls.add(QwenChatRequest.ToolCall.builder()
                        .id(tc.getId())
                        .type(tc.getType())
                        .function(QwenChatRequest.FunctionCall.builder()
                                .name(tc.getFunction() != null ? tc.getFunction().getName() : null)
                                .arguments(tc.getFunction() != null ? tc.getFunction().getArguments() : null)
                                .build())
                        .build());
            }
        }

        // 添加上一轮的 tool_calls 作为新的 assistant 消息，供下一轮 LLM 调用时参考
        messages.add(QwenChatRequest.Message.builder()
                .role("assistant")
                .tool_calls(toolCalls)
                .build());

        return messages;
    }

    private String buildSystemPromptWithToolPolicy(String systemPrompt, List<QwenChatRequest.ToolDef> toolDefs) {
        if (toolDefs == null || toolDefs.isEmpty()) {
            return systemPrompt;
        }
        String base = systemPrompt != null ? systemPrompt : "";
        return base + TOOL_USAGE_POLICY;
    }

    private List<QwenChatRequest.ToolDef> buildToolDefs() {
        if (!toolRegistry.hasTools()) {
            return null;
        }
        List<QwenChatRequest.ToolDef> toolDefs = new ArrayList<>();
        for(Tool tool : toolRegistry.getAllTools()) {
            QwenChatRequest.ToolDef toolDef = QwenChatRequest.ToolDef.builder()
                    .type("function")
                    .function(QwenChatRequest.FunctionDef.builder()
                            .name(tool.getName())
                            .description(tool.getDescription())
                            .parameters(tool.getParametersSchema())
                            .build())
                    .build();
            toolDefs.add(toolDef);
        }
        return toolDefs;
    }
}
