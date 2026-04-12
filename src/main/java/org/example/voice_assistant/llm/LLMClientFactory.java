package org.example.voice_assistant.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//大模型工厂类，统一管理多个LLM大模型，并根据type决定调用哪个大模型
@Component
@RequiredArgsConstructor
public class LLMClientFactory {

    private final Map<String,LLMClient> llmClients;

    public LLMClient getClient(String type) {
        LLMClient client = llmClients.get(type);
        if (client == null) {
            throw new IllegalArgumentException("Unknown LLM client type: " + type);
        }
        return client;
    }

    public LLMClient getDefaultClient() {
        return llmClients.get("qwen");
    }
}
