package org.example.voice_assistant.llm;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.llm.dto.QwenChatRequest;
import org.example.voice_assistant.llm.dto.QwenChatResponse;
import org.example.voice_assistant.llm.dto.QwenChatStreamResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component("qwen")
public class QwenClient implements LLMClient {

    private final WebClient webClient;

    @Value("${dashscope.model}")
    private String model;

    @Value("${dashscope.api.key}")
    private String apiKey;

    @Value("${dashscope.api.url}")
    private String apiUrl;

    public QwenClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public String chat(String prompt, String systemPrompt) {
        log.info("Qwen chat: model={}, prompt={}, systemPrompt={}", model, prompt, systemPrompt);

        try {
            QwenChatRequest request = buildRequest(prompt, systemPrompt);
            QwenChatResponse response = postForResponse(request);
            String result = extractResponse(response);
            log.info("Qwen response: {}", result);
            return result;
        } catch (WebClientResponseException e) {
            log.error("Qwen API client error: status={}, response={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Qwen API client error: " + e.getMessage(), e);
        } catch (WebClientRequestException e) {
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
        log.info("Qwen chat stream: systemPrompt={}", systemPrompt);

        QwenChatRequest request = buildRequest(prompt, systemPrompt);
        request.setStream(true);

        executeStreamRequest(request, onToken, onComplete, onError);
    }

    @Override
    public QwenChatResponse chatWithTools(String prompt, String systemPrompt,
                                          List<QwenChatRequest.ToolDef> tools) {
        log.info("Qwen chat with tools: model={}, tools={}", model, tools != null ? tools.size() : 0);

        try {
            QwenChatRequest request = buildRequestWithTools(prompt, systemPrompt, tools);
            QwenChatResponse response = postForResponse(request);
            log.info("Qwen chatWithTools response: finish_reason={}",
                    response.getChoices() != null && !response.getChoices().isEmpty()
                            ? response.getChoices().get(0).getFinish_reason() : "none");
            return response;
        } catch (WebClientResponseException e) {
            log.error("Qwen API client error: status={}, response={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Qwen API client error: " + e.getMessage(), e);
        } catch (WebClientRequestException e) {
            log.error("Qwen API connection error", e);
            throw new RuntimeException("Qwen API connection error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Qwen API unexpected error", e);
            throw new RuntimeException("Qwen API error: " + e.getMessage(), e);
        }
    }

    @Override
    public String chatWithMessages(List<QwenChatRequest.Message> messages,
                                   List<QwenChatRequest.ToolDef> tools) {
        log.info("Qwen chatWithMessages: messageCount={}, tools={}",
                messages.size(), tools != null ? tools.size() : 0);

        try {
            QwenChatRequest request = QwenChatRequest.builder()
                    .model(model)
                    .messages(messages)
                    .temperature(0.7)
                    .tools(tools)
                    .tool_choice("auto")
                    .enable_thinking(false)
                    .build();

            QwenChatResponse response = postForResponse(request);
            String result = extractResponse(response);
            log.info("Qwen chatWithMessages response: {}", result);
            return result;
        } catch (WebClientResponseException e) {
            log.error("Qwen API client error: status={}, response={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Qwen API client error: " + e.getMessage(), e);
        } catch (WebClientRequestException e) {
            log.error("Qwen API connection error", e);
            throw new RuntimeException("Qwen API connection error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Qwen API unexpected error", e);
            throw new RuntimeException("Qwen API error: " + e.getMessage(), e);
        }
    }

    @Override
    public void chatWithMessagesStream(List<QwenChatRequest.Message> messages,
                                       List<QwenChatRequest.ToolDef> tools,
                                       Consumer<String> onToken,
                                       Consumer<String> onComplete,
                                       Consumer<Exception> onError) {
        log.info("Qwen chatWithMessagesStream: messageCount={}, tools={}",
                messages.size(), tools != null ? tools.size() : 0);

        QwenChatRequest request = QwenChatRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(0.7)
                .tools(tools)
                .tool_choice("auto")
                .stream(true)
                .enable_thinking(false)
                .build();

        executeStreamRequest(request, onToken, onComplete, onError);
    }

    /**
     * 同步 POST 请求，返回解析后的 QwenChatResponse
     */
    private QwenChatResponse postForResponse(QwenChatRequest request) {
        QwenChatResponse response = webClient.post()
                .uri(apiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(QwenChatResponse.class)
                .block();

        if (response == null) {
            throw new RuntimeException("Qwen API returned empty response");
        }
        return response;
    }

    /**
     * 流式 SSE 请求，逐 token 回调。blockLast() 保证同步阻塞语义。
     */
    private void executeStreamRequest(QwenChatRequest request,
                                      Consumer<String> onToken,
                                      Consumer<String> onComplete,
                                      Consumer<Exception> onError) {
        StringBuilder fullResponse = new StringBuilder();
        StringBuilder lineBuffer = new StringBuilder();

        try {
            webClient.post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnNext(chunk -> processSseChunk(chunk, lineBuffer, fullResponse, onToken))
                    .blockLast();

            log.info("Stream full response: {}", fullResponse);
            if (onComplete != null) {
                onComplete.accept(fullResponse.toString());
            }
        } catch (WebClientResponseException e) {
            log.error("Qwen API client error: status={}, response={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            if (onError != null) {
                onError.accept(new RuntimeException("Qwen API client error: status=" + e.getStatusCode()));
            }
        } catch (Exception e) {
            log.error("Qwen API unexpected error", e);
            if (onError != null) {
                onError.accept(e);
            }
        }
    }

    /**
     * 处理流式 chunk，兼容 NDJSON 和 SSE data: 两种格式。
     * 每个 chunk 可能是一个完整 JSON 行，也可能是多行拼接。
     */
    private void processSseChunk(String chunk, StringBuilder lineBuffer,
                                  StringBuilder fullResponse, Consumer<String> onToken) {
        lineBuffer.append(chunk);
        String text = lineBuffer.toString();

        int lastNewline = text.lastIndexOf('\n');
        String toProcess;
        if (lastNewline >= 0) {
            toProcess = text.substring(0, lastNewline);
            lineBuffer.setLength(0);
            if (lastNewline + 1 < text.length()) {
                lineBuffer.append(text.substring(lastNewline + 1));
            }
        } else {
            // 没有换行，尝试把整个 buffer 当作一个完整 JSON 解析
            toProcess = text;
            lineBuffer.setLength(0);
        }

        for (String line : toProcess.split("\n")) {
            String jsonStr = extractJson(line);
            if (jsonStr == null || "[DONE]".equals(jsonStr)) {
                if ("[DONE]".equals(jsonStr)) {
                    log.info("Stream complete");
                }
                continue;
            }

            try {
                QwenChatStreamResponse streamResponse =
                        JSON.parseObject(jsonStr, QwenChatStreamResponse.class);
                if (streamResponse != null
                        && streamResponse.getChoices() != null
                        && !streamResponse.getChoices().isEmpty()) {
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
                // 解析失败可能是跨 chunk 的不完整 JSON，放回 buffer 等下个 chunk
                lineBuffer.append(line).append('\n');
                log.debug("Failed to parse stream line, buffering for next chunk");
            }
        }
    }

    /**
     * 从一行文本中提取 JSON 字符串。
     * 支持 NDJSON 格式（整行即 JSON）和 SSE 格式（data: {...}）。
     */
    private String extractJson(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        // NDJSON：整行就是 JSON
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        // SSE：data: {...}
        if (trimmed.startsWith("data:")) {
            String data = trimmed.substring(5).trim();
            if ("[DONE]".equals(data)) {
                return "[DONE]";
            }
            if (data.startsWith("{")) {
                return data;
            }
        }
        return null;
    }

    private QwenChatRequest buildRequestWithTools(String prompt, String systemPrompt,
                                                   List<QwenChatRequest.ToolDef> tools) {
        QwenChatRequest request = buildRequest(prompt, systemPrompt);
        request.setTools(tools);
        request.setTool_choice("auto");
        return request;
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
                .enable_thinking(false)
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
