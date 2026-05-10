package org.example.voice_assistant.llm;

import org.example.voice_assistant.llm.dto.QwenChatRequest;
import org.example.voice_assistant.llm.dto.QwenChatResponse;

import java.util.List;
import java.util.function.Consumer;

public interface LLMClient {

    String chat(String prompt, String systemPrompt);

    String getClientType();

    void chatStream(String prompt, String systemPrompt,
                    Consumer<String> onToken, Consumer<String> onComplete,
                    Consumer<Exception> onError);

    QwenChatResponse chatWithTools(String prompt, String systemPrompt,
                                   List<QwenChatRequest.ToolDef> tools);

    String chatWithMessages(List<QwenChatRequest.Message> messages,
                            List<QwenChatRequest.ToolDef> tools);

    void chatWithMessagesStream(List<QwenChatRequest.Message> messages,
                                List<QwenChatRequest.ToolDef> tools,
                                Consumer<String> onToken,
                                Consumer<String> onComplete,
                                Consumer<Exception> onError);
}
