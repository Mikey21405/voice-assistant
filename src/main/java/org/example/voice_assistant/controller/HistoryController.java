package org.example.voice_assistant.controller;



import org.example.voice_assistant.entity.ConversationHistory;
import org.example.voice_assistant.service.ConversationHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final ConversationHistoryService historyService;

    @GetMapping("/assistant/{assistantId}")
    public List<ConversationHistory> getByAssistantId(@PathVariable Long assistantId) {
        log.info("查询对话历史：assistantId={}", assistantId);
        return historyService.getHistoryByAssistantId(assistantId);
    }

    @GetMapping("/session/{sessionId}")
    public List<ConversationHistory> getBySessionId(@PathVariable String sessionId) {
        log.info("查询对话历史：sessionId={}", sessionId);
        return historyService.getHistoryBySessionId(sessionId);
    }

    @GetMapping("/recent/{assistantId}")
    public List<ConversationHistory> getRecent(@PathVariable Long assistantId,
                                               @RequestParam(defaultValue = "10") int limit) {
        log.info("查询最近对话历史：assistantId={}, limit={}", assistantId, limit);
        return historyService.getRecentHistory(assistantId, limit);
    }

    @DeleteMapping("/assistant/{assistantId}")
    public void clearByAssistantId(@PathVariable Long assistantId) {
        log.info("清空对话历史：assistantId={}", assistantId);
        historyService.clearHistory(assistantId);
    }
}
