package org.example.voice_assistant.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.entity.ConversationHistory;
import org.example.voice_assistant.mapper.ConversationHistoryMapper;
import org.example.voice_assistant.service.ConversationHistoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ConversationHistoryImpl implements ConversationHistoryService {

    private final ConversationHistoryMapper mapper;

    @Override
    public void saveHistory(Long assistantId, String sessionId, String callId, String userMessage, String assistantMessage) {
        ConversationHistory history = ConversationHistory.builder()
                .assistantId(assistantId)
                .sessionId(sessionId)
                .callId(callId)
                .userMessage(userMessage)
                .assistantMessage(assistantMessage)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        mapper.insert(history);
        log.info("保存对话历史：assistantId={}, userMessage={}, assistantMessage={}",
                assistantId, userMessage, assistantMessage);
    }

    @Override
    public void saveHistory(ConversationHistory history) {
        if (history.getCreatedAt() == null) {
            history.setCreatedAt(LocalDateTime.now());
        }
        if (history.getUpdatedAt() == null) {
            history.setUpdatedAt(LocalDateTime.now());
        }

        mapper.insert(history);
        log.info("保存对话历史：assistantId={}, userMessage={}, assistantMessage={}",
                history.getAssistantId(), history.getUserMessage(), history.getAssistantMessage());
    }

    @Override
    public List<ConversationHistory> getHistoryByAssistantId(Long assistantId) {
        return mapper.selectByAssistantId(assistantId);

    }

    @Override
    public List<ConversationHistory> getHistoryBySessionId(String sessionId) {
        return mapper.selectBySessionId(sessionId);
    }

    @Override
    public List<ConversationHistory> getRecentHistory(Long assistantId, int limit) {
        List<ConversationHistory> histories = mapper.selectRecentByAssistantId(assistantId, limit);
        histories.sort((h1, h2) -> h1.getCreatedAt().compareTo(h2.getCreatedAt()));
        return histories;
    }

    @Override
    public void clearHistory(Long assistantId) {
        mapper.deleteByAssistantId(assistantId);
        log.info("清空对话历史：assistantId={}", assistantId);
    }

    @Override
    public String buildHistoryPrompt(Long assistantId, int maxRounds) {
        List<ConversationHistory> histories = getRecentHistory(assistantId, maxRounds);

        if (histories.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("以下是之前的对话历史：\n");

        for (ConversationHistory history : histories) {
            sb.append("用户：").append(history.getUserMessage()).append("\n");
            sb.append("助手：").append(history.getAssistantMessage()).append("\n");
        }

        sb.append("以上是之前的对话，请基于以上历史继续对话。\n");

        return sb.toString();
    }
}
