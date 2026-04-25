package org.example.voice_assistant.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.entity.ConversationHistory;
import org.springframework.stereotype.Service;

import java.util.List;


public interface ConversationHistoryService {

    /**
     * 保存对话历史（简化版，传参数）
     * @param assistantId 助手 ID
     * @param sessionId 会话 ID
     * @param callId 通话 ID
     * @param userMessage 用户消息
     * @param assistantMessage 助手消息
     */
    void saveHistory(Long assistantId, String sessionId, String callId,
                     String userMessage, String assistantMessage);

    /**
     * 保存对话历史（完整版，传对象）
     * @param history 对话历史对象
     */
    void saveHistory(ConversationHistory history);

    /**
     * 根据助手 ID 查询历史
     * @param assistantId 助手 ID
     * @return 对话历史列表
     */
    List<ConversationHistory> getHistoryByAssistantId(Long assistantId);

    /**
     * 根据会话 ID 查询历史
     * @param sessionId 会话 ID
     * @return 对话历史列表
     */
    List<ConversationHistory> getHistoryBySessionId(String sessionId);

    /**
     * 查询最近的对话历史
     * @param assistantId 助手 ID
     * @param limit 限制条数
     * @return 对话历史列表
     */
    List<ConversationHistory> getRecentHistory(Long assistantId, int limit);

    /**
     * 清空对话历史
     * @param assistantId 助手 ID
     */
    void clearHistory(Long assistantId);

    /**
     * 构建历史对话 prompt
     * @param assistantId 助手 ID
     * @param maxRounds 最大轮数
     * @return 历史对话 prompt
     */
    String buildHistoryPrompt(Long assistantId, int maxRounds);
}
