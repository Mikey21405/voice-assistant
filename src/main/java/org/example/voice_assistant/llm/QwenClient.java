package org.example.voice_assistant.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.config.RestTemplateConfig;
import org.example.voice_assistant.llm.dto.QwenChatRequest;
import org.example.voice_assistant.llm.dto.QwenChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component("qwen")
@RequiredArgsConstructor
public class QwenClient implements LLMClient{

    private final RestTemplate restTemplate;

    @Value("${dashscope.model}")
    private String model;

    @Value("${dashscope.api.key}")
    private String apiKey;

    @Value("${dashscope.api.url}")
    private String apiUrl;

    @Override
    public String chat(String prompt, String systemPrompt) {
        log.info("Qwen chat: model={}, prompt={}, systemPrompt={}", model, prompt, systemPrompt);

        try {

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // 构建请求体
            QwenChatRequest request = buildRequest(prompt,systemPrompt);

            // 封装http请求
            HttpEntity<QwenChatRequest> entity = new HttpEntity<>(request,headers);

            // 发送请求
            QwenChatResponse response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    QwenChatResponse.class
            ).getBody();


            String result = extractResponse(response);
            log.info("Qwen response: {}", result);
            return result;
        } catch (HttpClientErrorException e) {
            log.error("Qwen API client error: status={}, response={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Qwen API client error: " + e.getMessage(), e);
        } catch (ResourceAccessException e) {
            log.error("Qwen API connection error", e);
            throw new RuntimeException("Qwen API connection error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Qwen API unexpected error", e);
            throw new RuntimeException("Qwen API error: " + e.getMessage(), e);
        }


    }

    private QwenChatRequest buildRequest(String prompt, String systemPrompt) {
        List<QwenChatRequest.Message> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(QwenChatRequest.Message.builder()
                    .role("system")
                    .content(systemPrompt)
                    .build());
        }

        messages.add(QwenChatRequest.Message.builder()
                .role("user")
                .content(prompt)
                .build());

        return QwenChatRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(0.7)
                .build();
    }


    private String extractResponse(QwenChatResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new RuntimeException("Qwen API returned empty response");
        }

        QwenChatResponse.Choice choice = response.getChoices().get(0);
        if (choice.getMessage() == null || choice.getMessage().getContent() == null) {
            throw new RuntimeException("Qwen API returned empty message content");
        }

        return choice.getMessage().getContent();
    }

    @Override
    public String getClientType() {
        return "qwen";
    }
}
