package org.example.voice_assistant.llm;

public interface LLMClient {

    String chat(String prompt,String systemPrompt);

    String getClientType();
}
