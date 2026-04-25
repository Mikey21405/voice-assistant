package org.example.voice_assistant.service;

import org.example.voice_assistant.entity.Assistant;

import java.util.List;

public interface AssistantService {
    /**
     * 创建一个助手
     * @param assistant
     * @return
     */
    Assistant createAssistant(Assistant assistant);

    /**
     * 更新一个助手
     * @param assistant
     * @return
     */
    Assistant updateAssistant(Assistant assistant);

    /**
     * 删除一个助手
     * @param id
     */
    void deleteAssistant(Long id);

    /**
     * 根据id获取一个助手
     * @param id
     * @return
     */
    Assistant getAssistantById(Long id);

    /**
     * 根据名称获取一个助手
     * @param name
     * @return
     */
    Assistant getAssistantByName(String name);

    /**
     * 获取所有助手
     * @return
     */
    List<Assistant> getAllAssistants();

    /**
     * 获取助手的system prompt，并加上历史记录
     * @param assistantId
     * @param historyPrompt
     * @return
     */
    String getSystemPromptWithHistory(Long assistantId,String historyPrompt);

    /**
     * 获取助手的maxRounds
     * @param assistantId
     * @return
     */
    int getMaxRounds(Long assistantId);

}
