package org.example.voice_assistant.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.example.voice_assistant.llm.dto.QwenChatRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class QwenClientDiagnosticTest {

    private static final String API_KEY = "sk-98c87527c9804e27bb5da1ceace7a433";
    private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String MODEL = "qwen3.5-flash";

    private final WebClient webClient = WebClient.create();

    @Test
    public void testNonStream() {
        System.out.println("\n========== 非流式测试 ==========");

        QwenChatRequest request = buildRequest("你好，请用一句话介绍自己", false);

        String rawResponse = webClient.post()
                .uri(API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        Assertions.assertNotNull(rawResponse, "响应不应为null");
        Assertions.assertTrue(rawResponse.contains("\"content\":\""), "响应应包含content字段");

        JSONObject json = JSON.parseObject(rawResponse);
        JSONArray choices = json.getJSONArray("choices");
        Assertions.assertNotNull(choices, "choices不应为null");
        Assertions.assertFalse(choices.isEmpty(), "choices不应为空");

        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.getJSONObject("message");
        String content = message.getString("content");

        System.out.println("非流式响应内容: " + content);
        Assertions.assertNotNull(content, "content不应为null");
        Assertions.assertFalse(content.isEmpty(), "content不应为空");
        System.out.println("✅ 非流式测试通过");
    }

    @Test
    public void testStream() {
        System.out.println("\n========== 流式测试 ==========");

        QwenChatRequest request = buildRequest("你好，请用一句话介绍自己", false);
        request.setStream(true);

        StringBuilder fullResponse = new StringBuilder();
        StringBuilder lineBuffer = new StringBuilder();
        AtomicInteger tokenCount = new AtomicInteger(0);
        AtomicInteger jsonLineCount = new AtomicInteger(0);

        webClient.post()
                .uri(API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(chunk -> processChunkTest(chunk, lineBuffer, fullResponse, tokenCount, jsonLineCount))
                .blockLast();

        System.out.println("JSON lines processed: " + jsonLineCount.get());
        System.out.println("Token count: " + tokenCount.get());
        System.out.println("Full response: " + fullResponse.toString());

        Assertions.assertTrue(jsonLineCount.get() > 0, "应至少处理一行JSON");
        Assertions.assertTrue(tokenCount.get() > 0, "应至少有一个token");
        Assertions.assertFalse(fullResponse.toString().isEmpty(), "完整响应不应为空");
        System.out.println("✅ 流式测试通过");
    }

    @Test
    public void testStreamSimulateProduction() {
        System.out.println("\n========== 模拟 QwenClient processSseChunk 测试 ==========");

        QwenChatRequest request = buildRequest("你好", false);
        request.setStream(true);

        StringBuilder fullResponse = new StringBuilder();
        StringBuilder lineBuffer = new StringBuilder();
        AtomicInteger tokenCount = new AtomicInteger(0);

        webClient.post()
                .uri(API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(chunk -> simulateProcessSseChunk(chunk, lineBuffer, fullResponse, tokenCount))
                .blockLast();

        System.out.println("Token count: " + tokenCount.get());
        System.out.println("Full response: [" + fullResponse + "]");

        Assertions.assertTrue(tokenCount.get() > 0, "应至少有一个token");
        Assertions.assertFalse(fullResponse.toString().isEmpty(), "完整响应不应为空");
        System.out.println("✅ 模拟生产代码测试通过");
    }

    // --- helpers (mirroring QwenClient logic) ---

    private void simulateProcessSseChunk(String chunk, StringBuilder lineBuffer,
                                          StringBuilder fullResponse, AtomicInteger tokenCount) {
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
            toProcess = text;
            lineBuffer.setLength(0);
        }

        for (String line : toProcess.split("\n")) {
            String jsonStr = extractJsonTest(line);
            if (jsonStr == null || "[DONE]".equals(jsonStr)) continue;

            try {
                JSONObject obj = JSON.parseObject(jsonStr);
                JSONArray choices = obj.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject delta = choice.getJSONObject("delta");
                    if (delta != null && delta.getString("content") != null) {
                        String content = delta.getString("content");
                        fullResponse.append(content);
                        tokenCount.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                lineBuffer.append(line).append('\n');
            }
        }
    }

    private String extractJsonTest(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.startsWith("{")) return trimmed;
        if (trimmed.startsWith("data:")) {
            String data = trimmed.substring(5).trim();
            if ("[DONE]".equals(data)) return "[DONE]";
            if (data.startsWith("{")) return data;
        }
        return null;
    }

    private void processChunkTest(String chunk, StringBuilder lineBuffer,
                                   StringBuilder fullResponse, AtomicInteger tokenCount,
                                   AtomicInteger jsonLineCount) {
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
            toProcess = text;
            lineBuffer.setLength(0);
        }

        for (String line : toProcess.split("\n")) {
            if (line.trim().isEmpty()) continue;
            if (!line.trim().startsWith("{")) {
                System.out.println("SKIP non-JSON: " + line.substring(0, Math.min(50, line.length())));
                continue;
            }
            jsonLineCount.incrementAndGet();
            try {
                JSONObject obj = JSON.parseObject(line.trim());
                JSONArray choices = obj.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject delta = choice.getJSONObject("delta");
                    if (delta != null && delta.getString("content") != null) {
                        String content = delta.getString("content");
                        fullResponse.append(content);
                        tokenCount.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                System.out.println("PARSE ERROR: " + e.getMessage());
            }
        }
    }

    private QwenChatRequest buildRequest(String prompt, boolean enableThinking) {
        List<QwenChatRequest.Message> messages = new ArrayList<>();
        messages.add(QwenChatRequest.Message.builder()
                .role("user")
                .content(prompt)
                .build());

        return QwenChatRequest.builder()
                .model(MODEL)
                .messages(messages)
                .temperature(0.7)
                .enable_thinking(enableThinking)
                .build();
    }
}
