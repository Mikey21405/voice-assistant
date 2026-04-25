package org.example.voice_assistant.llm;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.config.RestTemplateConfig;
import org.example.voice_assistant.llm.dto.QwenChatRequest;
import org.example.voice_assistant.llm.dto.QwenChatResponse;
import org.example.voice_assistant.llm.dto.QwenChatStreamResponse;
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

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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

            if(response == null) {
                throw new RuntimeException("Qwen API returned empty response");
            }

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


    @Override
    public void chatStream(String prompt, String systemPrompt,
                           Consumer<String> onToken,
                           Consumer<String> onComplete,
                           Consumer<Exception> onError) {
        log.info("Qwen chat stream: systemPrompt={}",systemPrompt);

        // HTTP连接对象（最底层实现）
        HttpURLConnection connection = null;
        try {

            // 创建URL对象
            URL url = new URL(apiUrl);
            // 打开连接
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setDoOutput(true);

            // 构建请求体
            QwenChatRequest request = buildRequest(prompt, systemPrompt);
            request.setStream(true);
            String jsonBody = JSON.toJSONString(request);

            // 发送请求体
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 400) {
                InputStream errorStream = connection.getErrorStream();
                String errorMessage = "";
                if (errorStream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        errorMessage = sb.toString();
                    }
                }
                log.error("Qwen API client error: status={}, response={}", responseCode, errorMessage);
                if (onError != null) {
                    onError.accept(new RuntimeException("Qwen API client error: status=" + responseCode + ", response=" + errorMessage));
                }
                return;
            }

            StringBuilder fullResponse = new StringBuilder();

            try (InputStream inputStream = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6);
                        if ("[DONE]".equals(data)) {
                            log.info("Stream complete");
                            if (onComplete != null) {
                                onComplete.accept(fullResponse.toString());
                            }
                            break;
                        }

                        try {
                            QwenChatStreamResponse streamResponse = JSON.parseObject(data, QwenChatStreamResponse.class);
                            if (streamResponse != null && streamResponse.getChoices() != null && !streamResponse.getChoices().isEmpty()) {
                                QwenChatStreamResponse.Choice choice = streamResponse.getChoices().get(0);
                                if (choice.getDelta() != null && choice.getDelta().getContent() != null) {
                                    String content = choice.getDelta().getContent();
                                    fullResponse.append(content);
                                    if (onToken != null) {
                                        onToken.accept(content);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse stream data: {}", data, e);
                        }
                    }
                }
            }

            log.info("Qwen stream full response: {}", fullResponse);

        } catch (Exception e) {
            log.error("Qwen API unexpected error", e);
            if (onError != null) {
                onError.accept(e);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
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
