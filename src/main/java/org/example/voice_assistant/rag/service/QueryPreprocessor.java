package org.example.voice_assistant.rag.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.llm.LLMClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class QueryPreprocessor {

    @Autowired
    private LLMClient llmClient;

    /**
     * 预处理用户查询，将口语化问题转换为适合检索的标准化查询列表。
     * 要求 LLM 返回 JSON 格式，避免正则解析的不确定性。
     *
     * @param originalQuery 用户原始输入
     * @param historyPrompt 对话历史提示词（可为 null）
     * @return 优化后的查询变体列表，解析失败时返回仅包含原始查询的单元素列表
     */
    public List<String> preprocessQuery(String originalQuery, String historyPrompt) {
        try {
            log.info("开始查询预处理，原始查询: {}", originalQuery);

            String systemPrompt = buildPreprocessPrompt(historyPrompt);
            String llmResponse = llmClient.chat(originalQuery, systemPrompt);

            log.info("LLM 预处理响应: {}", llmResponse);

            List<String> queries = parseJsonResponse(llmResponse, originalQuery);

            log.info("查询预处理完成，生成 {} 个变体: {}", queries.size(), queries);
            return queries;

        } catch (Exception e) {
            log.error("查询预处理失败，使用原始查询", e);
            return List.of(originalQuery);
        }
    }

    /**
     * 构建查询预处理的提示词，要求 LLM 返回严格的 JSON 格式。
     */
    private String buildPreprocessPrompt(String historyPrompt) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个查询优化专家。你的任务是将用户的口语化问题转换为适合向量检索的标准化查询列表。\n\n");

        prompt.append("【优化规则】\n");
        prompt.append("1. 去除口语化表达、语气词、重复词\n");
        prompt.append("2. 将模糊的指代（如「上次」「那个」「它」）结合对话历史替换为具体内容\n");
        prompt.append("3. 保留核心问题意图，提取关键词\n");
        prompt.append("4. 生成1-3个简洁、明确的查询变体\n\n");

        prompt.append("【输出格式 — 必须严格遵守】\n");
        prompt.append("你必须只返回一个 JSON 对象，格式如下，不要包含任何其他文字：\n");
        prompt.append("{\"queries\": [\"查询变体1\", \"查询变体2\", \"查询变体3\"]}\n\n");

        prompt.append("【示例】\n");
        prompt.append("用户: \"上次的方案是什么\"\n");
        prompt.append("输出: {\"queries\": [\"方案内容\", \"项目方案\", \"上次的项目方案\"]}\n\n");

        prompt.append("用户: \"那个文档里有啥信息\"\n");
        prompt.append("输出: {\"queries\": [\"文档内容\", \"相关信息\", \"文档中的信息\"]}\n\n");

        prompt.append("用户: \"告诉我关于这个的详情\"\n");
        prompt.append("输出: {\"queries\": [\"详细信息\", \"详情内容\", \"具体信息\"]}\n\n");

        if (historyPrompt != null && !historyPrompt.isEmpty()) {
            prompt.append("【对话历史】\n");
            prompt.append(historyPrompt).append("\n\n");
        }

        prompt.append("【硬性要求】\n");
        prompt.append("- 只返回 JSON，不要加任何解释、说明或 markdown 代码块标记\n");
        prompt.append("- queries 数组包含 1-3 个元素\n");
        prompt.append("- 如果是简单明确的问题，可以只返回 1 个查询");
        // 不要添加 markdown 标记
        return prompt.toString();
    }

    /**
     * 解析 LLM 返回的 JSON，提取查询列表。
     * 支持多种容错情况：
     * - 纯 JSON: {"queries": [...]}
     * - JSON 被 markdown 代码块包裹: ```json {...} ```
     * - LLM 返回了非 JSON 文本（降级为按行分割）
     *
     * @param llmResponse   LLM 原始响应
     * @param originalQuery 原始查询，作为兜底
     * @return 解析出的查询列表
     */
    private List<String> parseJsonResponse(String llmResponse, String originalQuery) {
        if (llmResponse == null || llmResponse.trim().isEmpty()) {
            return List.of(originalQuery);
        }

        String jsonStr = extractJson(llmResponse.trim());

        try {
            JSONObject json = JSON.parseObject(jsonStr);
            JSONArray queriesArray = json.getJSONArray("queries");

            if (queriesArray == null || queriesArray.isEmpty()) {
                log.warn("JSON 中 queries 数组为空，降级使用原始查询");
                return List.of(originalQuery);
            }

            List<String> queries = new ArrayList<>();
            for (int i = 0; i < queriesArray.size(); i++) {
                String q = queriesArray.getString(i);
                if (q != null && !q.trim().isEmpty()) {
                    queries.add(q.trim());
                }
            }

            if (queries.isEmpty()) {
                log.warn("解析后无有效查询，降级使用原始查询");
                return List.of(originalQuery);
            }

            return queries;

        } catch (Exception e) {
            log.warn("JSON 解析失败: {}，降级按行分割", e.getMessage());
            return fallbackLineParse(llmResponse, originalQuery);
        }
    }

    /**
     * 从 LLM 响应中提取 JSON 字符串。
     * 处理 LLM 可能在 JSON 外包裹 markdown 代码块或额外文字的情况。
     */
    private String extractJson(String response) {
        // 情况1: 被 ```json ... ``` 包裹
        int jsonStart = response.indexOf("```json");
        if (jsonStart != -1) {
            int contentStart = response.indexOf('\n', jsonStart);
            if (contentStart == -1) contentStart = jsonStart + 7;
            else contentStart = contentStart + 1;
            int jsonEnd = response.indexOf("```", contentStart);
            if (jsonEnd != -1) {
                return response.substring(contentStart, jsonEnd).trim();
            }
        }

        // 情况2: 被 ``` ... ``` 包裹（无语言标记）
        int tripleStart = response.indexOf("```");
        if (tripleStart != -1) {
            int contentStart = response.indexOf('\n', tripleStart);
            if (contentStart == -1) contentStart = tripleStart + 3;
            else contentStart = contentStart + 1;
            int tripleEnd = response.indexOf("```", contentStart);
            if (tripleEnd != -1) {
                return response.substring(contentStart, tripleEnd).trim();
            }
        }

        // 情况3: 尝试找到第一个 { 到最后一个 } 的内容
        int braceStart = response.indexOf('{');
        int braceEnd = response.lastIndexOf('}');
        if (braceStart != -1 && braceEnd != -1 && braceEnd > braceStart) {
            return response.substring(braceStart, braceEnd + 1).trim();
        }

        // 情况4: 直接返回原始文本
        return response;
    }

    /**
     * 当 JSON 解析完全失败时的降级方案：按行分割。
     */
    private List<String> fallbackLineParse(String text, String originalQuery) {
        List<String> queries = new ArrayList<>();
        for (String line : text.split("\n")) {
            line = line.trim();
            if (!line.isEmpty()
                    && !line.startsWith("{")
                    && !line.startsWith("}")
                    && !line.startsWith("```")
                    && !line.startsWith("原始")
                    && !line.startsWith("优化")
                    && !line.startsWith("【")) {
                queries.add(line);
            }
        }
        if (queries.isEmpty()) {
            queries.add(originalQuery);
        }
        return queries;
    }
}
