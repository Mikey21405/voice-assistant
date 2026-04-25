package org.example.voice_assistant.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.llm.LLMClient;
import org.example.voice_assistant.rag.service.VectorSearchService.SearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
public class RAGQAService {

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private LLMClient llmClient;

    /**
     * 【框架步骤 2-5】完整的RAG流程（非流式）
     * 2. RAG检索
     * 3. Prompt构建
     * 4. LLM调用
     * 5. 返回结果
     */
    public String ragAnswer(String query, int topK, String historyPrompt, String baseSystemPrompt) {
        try {
            log.info("🔍 【框架步骤 2】开始RAG检索，查询: {}, topK: {}", query, topK);

            // ============================================
            // 【框架步骤 2】RAG检索
            // ============================================
            List<SearchResult> searchResults = vectorSearchService.searchSimilarDocuments(query, topK);
            
            if (searchResults.isEmpty()) {
                log.warn("⚠️ 未检索到相关文档，使用普通对话");
                String systemPrompt = buildBasicSystemPrompt(historyPrompt, baseSystemPrompt);
                return llmClient.chat(query, systemPrompt);
            }

            log.info("✅ 【框架步骤 2】检索到 {} 个相关文档", searchResults.size());

            // ============================================
            // 【框架步骤 3】Prompt构建
            // ============================================
            String context = buildContext(searchResults);
            String systemPrompt = buildSystemPromptWithContext(context, historyPrompt, baseSystemPrompt);
            log.info("✅ 【框架步骤 3】Prompt构建完成，长度: {}", systemPrompt.length());

            // ============================================
            // 【框架步骤 4】LLM调用
            // ============================================
            String answer = llmClient.chat(query, systemPrompt);
            log.info("✅ 【框架步骤 4】LLM调用完成");

            // ============================================
            // 【框架步骤 5】返回结果
            // ============================================
            log.info("✅ 【框架步骤 5】RAG问答流程完成");
            return answer;

        } catch (Exception e) {
            log.error("❌ RAG问答失败，降级到普通对话", e);
            try {
                String systemPrompt = buildBasicSystemPrompt(historyPrompt, baseSystemPrompt);
                return llmClient.chat(query, systemPrompt);
            } catch (Exception ex) {
                log.error("❌ 普通对话也失败", ex);
                return "抱歉，我暂时无法理解您的意思，请稍后再试。";
            }
        }
    }

    /**
     * 【框架步骤 2-5】完整的RAG流程（流式！）
     * 直接支持流式输出，不需要模拟
     */
    public void ragAnswerStream(String query, int topK, String historyPrompt, String baseSystemPrompt,
                                Consumer<String> onToken, Consumer<String> onComplete, Consumer<Exception> onError) {
        try {
            log.info("🔍 【框架步骤 2】开始RAG检索（流式），查询: {}, topK: {}", query, topK);

            // ============================================
            // 【框架步骤 2】RAG检索
            // ============================================
            List<SearchResult> searchResults = vectorSearchService.searchSimilarDocuments(query, topK);
            
            String systemPrompt;
            if (searchResults.isEmpty()) {
                log.warn("⚠️ 未检索到相关文档，使用普通对话");
                systemPrompt = buildBasicSystemPrompt(historyPrompt, baseSystemPrompt);
            } else {
                log.info("✅ 【框架步骤 2】检索到 {} 个相关文档", searchResults.size());
                // ============================================
                // 【框架步骤 3】Prompt构建
                // ============================================
                String context = buildContext(searchResults);
                systemPrompt = buildSystemPromptWithContext(context, historyPrompt, baseSystemPrompt);
                log.info("✅ 【框架步骤 3】Prompt构建完成，长度: {}", systemPrompt.length());
            }

            // ============================================
            // 【框架步骤 4】LLM调用（流式）
            // ============================================
            log.info("🚀 【框架步骤 4】开始流式LLM调用");
            
            llmClient.chatStream(query, systemPrompt, onToken, onComplete, onError);

        } catch (Exception e) {
            log.error("❌ RAG问答失败，降级到普通对话", e);
            try {
                String systemPrompt = buildBasicSystemPrompt(historyPrompt, baseSystemPrompt);
                llmClient.chatStream(query, systemPrompt, onToken, onComplete, onError);
            } catch (Exception ex) {
                log.error("❌ 普通对话也失败", ex);
                if (onError != null) {
                    onError.accept(ex);
                }
            }
        }
    }

    /**
     * 构建检索文档上下文
     */
    private String buildContext(List<SearchResult> searchResults) {
        StringBuilder context = new StringBuilder();
        context.append("\n\n【检索到的相关文档信息】\n\n");
        
        for (int i = 0; i < searchResults.size(); i++) {
            SearchResult result = searchResults.get(i);
            context.append(String.format("📄 文档 %d (相似度: %.2f):\n", i + 1, result.getScore()));
            context.append(result.getContent()).append("\n\n");
        }
        
        return context.toString();
    }

    /**
     * 构建带RAG上下文的系统提示词
     */
    private String buildSystemPromptWithContext(String context, String historyPrompt, String baseSystemPrompt) {
        StringBuilder prompt = new StringBuilder();
        
        if (baseSystemPrompt != null && !baseSystemPrompt.isEmpty()) {
            prompt.append(baseSystemPrompt).append("\n\n");
        } else {
            prompt.append("你是一个智能语音助手，请根据以下检索到的文档信息来回答用户的问题。回复要简洁、口语化，适合语音合成播放。\n\n");
        }
        
        prompt.append("【重要提示】\n");
        prompt.append("1. 请优先基于检索到的文档信息回答问题\n");
        prompt.append("2. 如果文档中没有相关信息，请诚实说明，不要编造\n");
        prompt.append("3. 回答要简洁、口语化，适合语音播放\n");
        prompt.append("4. 可以结合对话历史理解上下文\n\n");
        
        if (historyPrompt != null && !historyPrompt.isEmpty()) {
            prompt.append("【对话历史】\n");
            prompt.append(historyPrompt).append("\n\n");
        }
        
        prompt.append(context);
        
        return prompt.toString();
    }

    /**
     * 构建不带RAG的普通对话提示词
     */
    private String buildBasicSystemPrompt(String historyPrompt, String baseSystemPrompt) {
        StringBuilder prompt = new StringBuilder();
        
        if (baseSystemPrompt != null && !baseSystemPrompt.isEmpty()) {
            prompt.append(baseSystemPrompt).append("\n\n");
        } else {
            prompt.append("你是一个语音助手的后端，负责理解用户输入并给出简洁友好的回复。回复要口语化，适合语音合成。\n\n");
        }
        
        if (historyPrompt != null && !historyPrompt.isEmpty()) {
            prompt.append("【对话历史】\n");
            prompt.append(historyPrompt).append("\n\n");
        }
        
        return prompt.toString();
    }
}
