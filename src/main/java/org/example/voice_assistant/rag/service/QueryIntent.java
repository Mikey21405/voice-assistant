package org.example.voice_assistant.rag.service;

/**
 * 用户查询意图分类。
 * KNOWLEDGE: 需要从知识库检索内容
 * CHAT:     闲聊、问候、简单对话，不需要检索
 * COMMAND:  操作指令，需要调用工具
 */
public enum QueryIntent {
    KNOWLEDGE,
    CHAT,
    COMMAND
}
