package org.example.voice_assistant.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.dto.ChatRequest;
import org.example.voice_assistant.llm.LLMClientFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Slf4j
@Controller
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LLMController {

    private final LLMClientFactory llmClientFactory;

    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        log.info("LLM chat request,model={},prompt={}",request.getModel(),request.getPrompt());
        return llmClientFactory.getClient(request.getModel()).chat(request.getPrompt(),request.getSystemPrompt());
    }

    @PostMapping("/chat/default")
    public String chatDefault(@RequestBody ChatRequest request) {
        log.info("LLM chat default request: prompt={}", request.getPrompt());

        return llmClientFactory.getDefaultClient()
                .chat(request.getPrompt(), request.getSystemPrompt());
    }
}
