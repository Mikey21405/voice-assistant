package org.example.voice_assistant.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.llm.LLMClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class QueryPreprocessor {

    @Autowired
    private LLMClient llmClient;

    /**
     * 预处理用户查询，将口语化问题转换为适合检索的标准化查询
     */
    public String preprocessQuery(String originalQuery, String historyPrompt) {
        try {
            log.info("🔧 开始查询预处理，原始查询: {}", originalQuery);

            // 构建预处理提示词
            String systemPrompt = buildPreprocessPrompt(historyPrompt);
            
            // 调用LLM进行查询优化
            String optimizedQuery = llmClient.chat(originalQuery, systemPrompt);
            
            log.info("✅ 查询预处理完成，优化后: {}", optimizedQuery);
            
            // 如果优化失败，返回原始查询
            if (optimizedQuery == null || optimizedQuery.trim().isEmpty()) {
                return originalQuery;
            }
            
            return optimizedQuery;
        } catch (Exception e) {
            log.error("❌ 查询预处理失败，使用原始查询", e);
            return originalQuery;
        }
    }

    /**
     * 构建查询预处理的提示词
     */
    private String buildPreprocessPrompt(String historyPrompt) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("你是一个查询优化专家。你的任务是将用户的口语化问题转换为适合向量检索的标准化查询。\n\n");
        
        prompt.append("【优化规则】\n");
        prompt.append("1. 去除口语化表达、语气词、重复词\n");
        prompt.append("2. 将模糊的指代（如\"上次\"、\"那个\"、\"它\"）结合对话历史替换为具体内容\n");
        prompt.append("3. 保留核心问题意图，提取关键词\n");
        prompt.append("4. 生成1-3个简洁、明确的查询变体，用换行分隔\n");
        prompt.append("5. 直接返回优化后的查询，不要解释\n\n");
        
        prompt.append("【示例】\n");
        prompt.append("原始: \"上次的方案是什么\"\n");
        prompt.append("优化: \"方案内容\\n项目方案\\n上次的项目方案\"\n\n");
        
        prompt.append("原始: \"那个文档里有啥信息\"\n");
        prompt.append("优化: \"文档内容\\n相关信息\\n文档中的信息\"\n\n");
        
        prompt.append("原始: \"告诉我关于这个的详情\"\n");
        prompt.append("优化: \"详细信息\\n详情内容\\n具体信息\"\n\n");
        
        if (historyPrompt != null && !historyPrompt.isEmpty()) {
            prompt.append("【对话历史】\n");
            prompt.append(historyPrompt).append("\n\n");
        }
        
        prompt.append("【要求】\n");
        prompt.append("- 只返回优化后的查询，每行一个\n");
        prompt.append("- 不要添加任何解释或说明\n");
        prompt.append("- 如果是简单明确的问题，可以直接返回，或生成2个简单变体\n");
        
        return prompt.toString();
    }
}
