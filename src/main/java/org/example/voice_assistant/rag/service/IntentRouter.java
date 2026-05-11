package org.example.voice_assistant.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.llm.LLMClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 意图路由器 — RAG 三层漏斗的第一层。
 * 在不查向量库的前提下，快速判断用户查询是否需要知识检索。
 *
 * 策略：规则匹配先行（0 token，覆盖80%场景），命中不了的走 LLM 轻量分类。
 */
@Slf4j
@Service
public class IntentRouter {

    @Autowired
    private LLMClient llmClient;

    // ==================== 规则匹配 ====================

    private static final Set<String> CHAT_GREETINGS = Set.of(
            "你好", "嗨", "hello", "hi", "hey", "在吗", "早上好", "下午好", "晚上好",
            "晚安", "再见", "拜拜", "bye", "goodbye"
    );

    private static final Set<String> CHAT_THANKS = Set.of(
            "谢谢", "感谢", "多谢", "thanks", "thank you", "3q"
    );

    private static final Set<String> CHAT_SMALL_TALK = Set.of(
            "你是谁", "你叫什么", "你能做什么", "你会什么", "你有什么功能",
            "讲个笑话", "说个笑话", "笑话",
            "今天天气", "天气怎么样", "天气如何",
            "你吃了吗", "你怎么样"
    );

    private static final Pattern KNOWLEDGE_PATTERN = Pattern.compile(
            ".*(?:查|搜索|找|检索|知识库|文档|合同|会议|纪要|记录|规定|制度|流程|"
                    + "是什么|什么是|怎么|如何|解释|说明|定义|什么是|告诉我|帮我查|"
                    + "帮我找|帮我搜索).*"
    );

    private static final Pattern COMMAND_PATTERN = Pattern.compile(
            ".*(?:打开|关闭|设置|切换|启用|禁用|开启|停止|开始|暂停|重启).*"
    );

    // ==================== LLM 分类 Prompt ====================

    private static final String INTENT_CLASSIFY_PROMPT = buildClassifyPrompt();

    private static String buildClassifyPrompt() {
        StringBuilder p = new StringBuilder();
        p.append("判断用户意图，只回复一个字母：A、B 或 C。不要回复任何其他内容。\n\n");
        p.append("A = 需要查询知识库/文档来回答（如：合同条款是什么、查一下会议纪要、这个规定怎么说的）\n");
        p.append("B = 闲聊/问候/简单对话（如：你好、谢谢、讲个笑话、你是谁）\n");
        p.append("C = 要求执行操作/命令（如：打开灯、设置温度、切换模式）\n\n");
        p.append("示例：\n");
        p.append("用户：\"你好\" → B\n");
        p.append("用户：\"帮我查一下合同条款\" → A\n");
        p.append("用户：\"打开空调\" → C\n");
        p.append("用户：\"今天天气怎么样\" → B\n");
        p.append("用户：\"上次的会议纪要里关于预算的内容\" → A");
        return p.toString();
    }

    // ==================== 公共方法 ====================

    /**
     * 分析用户查询意图。
     * 先走规则匹配（0 token 开销），命中不了再走 LLM 分类。
     *
     * @param query 用户原始输入
     * @return 意图分类结果
     */
    public QueryIntent classify(String query) {
        if (query == null || query.trim().isEmpty()) {
            return QueryIntent.CHAT;
        }

        String q = query.trim();

        // 第一层：规则快速匹配
        QueryIntent ruleResult = ruleMatch(q);
        if (ruleResult != null) {
            log.info("意图路由（规则命中）: {} → {}", q, ruleResult);
            return ruleResult;
        }

        // 第二层：LLM 轻量分类
        QueryIntent llmResult = llmClassify(q);
        log.info("意图路由（LLM分类）: {} → {}", q, llmResult);
        return llmResult;
    }

    // ==================== 私有方法 ====================

    /**
     * 规则匹配。返回 null 表示规则无法确定，需要 LLM 分类。
     */
    private QueryIntent ruleMatch(String query) {
        // 精确匹配闲聊关键词
        if (CHAT_GREETINGS.contains(query) || CHAT_THANKS.contains(query) || CHAT_SMALL_TALK.contains(query)) {
            return QueryIntent.CHAT;
        }

        // 命令模式匹配
        if (COMMAND_PATTERN.matcher(query).matches()) {
            return QueryIntent.COMMAND;
        }

        // 知识查询模式匹配（较强的信号）
        if (KNOWLEDGE_PATTERN.matcher(query).matches()) {
            return QueryIntent.KNOWLEDGE;
        }

        // 规则无法确定
        return null;
    }

    /**
     * LLM 轻量分类。prompt 极短（约150 token），追求低延迟。
     * 失败时降级为 CHAT（保守策略，避免无意义向量检索）。
     */
    private QueryIntent llmClassify(String query) {
        try {
            String result = llmClient.chat(query, INTENT_CLASSIFY_PROMPT);
            if (result == null) return QueryIntent.CHAT;

            String trimmed = result.trim().toUpperCase();
            if (trimmed.contains("A")) return QueryIntent.KNOWLEDGE;
            if (trimmed.contains("C")) return QueryIntent.COMMAND;
            // B 或无法识别 → 保守降级为闲聊
            return QueryIntent.CHAT;
        } catch (Exception e) {
            log.warn("LLM 意图分类失败，降级为 CHAT: {}", e.getMessage());
            return QueryIntent.CHAT;
        }
    }
}
