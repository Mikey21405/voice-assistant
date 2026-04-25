package org.example.voice_assistant.llm;

import java.util.function.Consumer;

public interface LLMClient {

    String chat(String prompt,String systemPrompt);

    String getClientType();

    /**
     * 流式发送请求到大模型
     * @param prompt
     * @param systemPrompt
     * @param onToken
     * @param onComplete
     * @param onError
     */
    void chatStream(String prompt, String systemPrompt,
                    Consumer<String> onToken,Consumer<String> onComplete,
                    Consumer<Exception> onError);
}
