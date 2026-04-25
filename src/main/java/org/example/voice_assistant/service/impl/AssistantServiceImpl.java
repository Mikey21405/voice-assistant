package org.example.voice_assistant.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.entity.Assistant;
import org.example.voice_assistant.mapper.AssistantMapper;
import org.example.voice_assistant.service.AssistantService;
import org.example.voice_assistant.service.ConversationHistoryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AssistantServiceImpl implements AssistantService {

    private final AssistantMapper mapper;

    private final ConversationHistoryService conversationHistoryService;

    @Override
    public Assistant createAssistant(Assistant assistant) {
        mapper.insert(assistant);
        log.info("创建助手：助手id={},name={}", assistant.getId(),assistant.getName());
        return assistant;
    }

    @Override
    public Assistant updateAssistant(Assistant assistant) {
        mapper.update(assistant);
        log.info("更新助手：助手id={},name={}", assistant.getId(),assistant.getName());
        return assistant;
    }

    @Override
    public void deleteAssistant(Long id) {
        mapper.delete(id);
        log.info("删除助手：助手id={}", id);
        conversationHistoryService.clearHistory(id);
    }

    @Override
    public Assistant getAssistantById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public Assistant getAssistantByName(String name) {
        return mapper.selectByName(name);
    }

    @Override
    public List<Assistant> getAllAssistants() {
        return mapper.selectAll();
    }

    @Override
    public String getSystemPromptWithHistory(Long assistantId, String historyPrompt) {
        Assistant assistant = mapper.selectById(assistantId);
        if(assistant == null) {
            log.warn("助手不存在：id={}",assistantId);
        }

        String systemPrompt = assistant.getSystemPrompt();
        if (systemPrompt == null) {
            if(assistant.getDescription() != null) {
                systemPrompt = assistant.getDescription();
            }else {
                systemPrompt = "你是一个智能助手，请帮助用户回答问题，如果遇到能力范围之外的问题，直接说不会，不要乱回答。";
            }
        }
        if (!historyPrompt.isEmpty()) {
            systemPrompt = systemPrompt + "\n" + historyPrompt;
        }
        return systemPrompt;
    }

    @Override
    public int getMaxRounds(Long assistantId) {
        Assistant assistant = mapper.selectById(assistantId);
        if(assistant == null || assistant.getMaxHistoryRounds() == null) {
            if (assistant != null) {
                assistant.setMaxHistoryRounds(5);
                mapper.update(assistant);
            }
            return 5; // 默认5轮
        }
        return assistant.getMaxHistoryRounds();
    }
}
