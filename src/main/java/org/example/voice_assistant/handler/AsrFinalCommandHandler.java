package org.example.voice_assistant.handler;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.config.RAGConfig;
import org.example.voice_assistant.dto.WebSocketMessage;
import org.example.voice_assistant.entity.Assistant;
import org.example.voice_assistant.llm.LLMClient;
import org.example.voice_assistant.llm.LLMClientFactory;
import org.example.voice_assistant.rag.service.RAGQAService;
import org.example.voice_assistant.service.AssistantService;
import org.example.voice_assistant.service.ConversationHistoryService;
import org.example.voice_assistant.websocket.SessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.net.http.WebSocket;


@Slf4j
@Component
public class AsrFinalCommandHandler extends BaseCommandHandler{

    private final LLMClientFactory llmClientFactory;
    private final ConversationHistoryService conversationHistoryService;
    private final AssistantService assistantService;
    private final RAGQAService ragQAService;
    private final RAGConfig ragConfig;

    public AsrFinalCommandHandler(SessionManager sessionManager
            , LLMClientFactory llmClientFactory
            , ConversationHistoryService conversationHistoryService
            , AssistantService assistantService
            , RAGQAService ragQAService
            , RAGConfig ragConfig) {
        super(sessionManager);
        this.llmClientFactory = llmClientFactory;
        this.conversationHistoryService = conversationHistoryService;
        this.assistantService = assistantService;
        this.ragQAService = ragQAService;
        this.ragConfig = ragConfig;
    }

    @Override
    public String getCommand() {
        return WebSocketMessage.Command.ASR_FINAL.name();
    }

    @Override
    public void handle(WebSocketSession session, WebSocketMessage message) {
        log.info("📍【框架步骤 1】ASR接收，session={}", session.getId());

        String text = message.getPayload() != null
                ? message.getPayload().getString("text")
                : null;
        String callId = message.getPayload() != null
                ? message.getPayload().getString("callId")
                : null;
        Long assistantId = message.getAssistantId();

        log.info("🎤 ASR识别文本：{}", text);

        // 按照框架开始处理
        processWithRAGFramework(session, message, assistantId, text, callId);
    }

    /**
     * 【完整框架实现】
     * 1. ASR接收 ✓
     * 2. RAG检索
     * 3. Prompt构建
     * 4. LLM调用
     * 5. 返回结果
     */
    private void processWithRAGFramework(WebSocketSession session, WebSocketMessage originalMessage,
                                          Long assistantId, String asrText, String callId) {
        try {
            if (assistantId == null) {
                log.warn("⚠️ 该助手不存在");
                return;
            }

            // 获取助手配置
            Assistant assistant = assistantService.getAssistantById(assistantId);
            
            // 检查应用级别的RAG总开关
            boolean useRag = ragConfig.isEnabled();
            
            log.info("🤖 RAG总开关: enabled={}, topK={}", useRag, ragConfig.getTopK());

            // 获取历史对话
            int maxRounds = assistantService.getMaxRounds(assistantId);
            String historyPrompt = conversationHistoryService.buildHistoryPrompt(assistantId, maxRounds);
            String baseSystemPrompt = assistant != null ? assistant.getSystemPrompt() : null;

            if (useRag) {
                log.info("🔍 【启用RAG模式】开始完整RAG流程");
                processWithRAG(session, originalMessage, assistantId, asrText, callId, 
                              assistant, historyPrompt, baseSystemPrompt);
            } else {
                log.info("💬 【普通对话模式】使用传统LLM对话");
                processWithoutRAG(session, originalMessage, assistantId, asrText, callId, 
                                 historyPrompt, baseSystemPrompt);
            }

        } catch (Exception e) {
            log.error("❌ 处理失败", e);
            String errorMsg = "抱歉，我暂时无法理解您的意思，请稍后再试。";
            sendStreamEnd(session, originalMessage, errorMsg);
        }
    }

    /**
     * 【RAG模式】按照框架完整流程（真正的流式！）
     */
    private void processWithRAG(WebSocketSession session, WebSocketMessage originalMessage,
                                Long assistantId, String asrText, String callId,
                                Assistant assistant, String historyPrompt, String baseSystemPrompt) {
        try {
            log.info("🔄 ======== 开始完整RAG流程（流式）========");

            // 获取RAG配置（从应用级别配置）
            int topK = ragConfig.getTopK();

            // ============================================
            // 【框架步骤 2-5】调用RAGQAService完成（流式！）
            // ============================================
            ragQAService.ragAnswerStream(
                asrText,
                topK,
                historyPrompt,
                baseSystemPrompt,
                // onToken - 流式接收token
                token -> {
                    sendStreamToken(session, originalMessage, token);
                },
                // onComplete - 完成时
                fullAnswer -> {
                    log.info("✅ ======== RAG流程完成 ========");
                    log.info("📝 最终回答：{}", fullAnswer);
                    sendStreamEnd(session, originalMessage, fullAnswer);
                    conversationHistoryService.saveHistory(assistantId, session.getId(), callId, asrText, fullAnswer);
                    sendTtsToPbx(session, originalMessage, fullAnswer);
                },
                // onError - 出错时
                e -> {
                    log.error("❌ RAG流程失败，降级到普通对话", e);
                    processWithoutRAG(session, originalMessage, assistantId, asrText, callId, 
                                     historyPrompt, baseSystemPrompt);
                }
            );

        } catch (Exception e) {
            log.error("❌ RAG流程异常，降级到普通对话", e);
            processWithoutRAG(session, originalMessage, assistantId, asrText, callId, 
                             historyPrompt, baseSystemPrompt);
        }
    }

    /**
     * 【普通对话模式】不使用RAG
     */
    private void processWithoutRAG(WebSocketSession session, WebSocketMessage originalMessage,
                                   Long assistantId, String asrText, String callId,
                                   String historyPrompt, String baseSystemPrompt) {
        try {
            LLMClient llmClient = llmClientFactory.getDefaultClient();
            log.info("📤 调用大模型：{}", llmClient.getClientType());

            String systemPrompt = assistantService.getSystemPromptWithHistory(assistantId, historyPrompt);
            log.info("📝 系统提示词：{}", systemPrompt);

            StringBuilder fullResponse = new StringBuilder();
            llmClient.chatStream(
                    asrText,
                    systemPrompt,
                    token -> {
                        fullResponse.append(token);
                        sendStreamToken(session, originalMessage, token);
                    },
                    fullText -> {
                        log.info("✅ 大模型回复完成");
                        sendStreamEnd(session, originalMessage, fullText);
                        conversationHistoryService.saveHistory(assistantId, session.getId(), callId, asrText, fullText);
                        sendTtsToPbx(session, originalMessage, fullText);
                    },
                    e -> {
                        log.error("❌ 调用大模型失败", e);
                        String errorMsg = "抱歉，我暂时无法理解您的意思，请稍后再试。";
                        sendStreamEnd(session, originalMessage, errorMsg);
                    });
        } catch (Exception e) {
            log.error("❌ 普通对话失败", e);
            String errorMsg = "抱歉，我暂时无法理解您的意思，请稍后再试。";
            sendStreamEnd(session, originalMessage, errorMsg);
        }
    }



    private void sendStreamToken(WebSocketSession session,WebSocketMessage originalMessage,String token) {
        JSONObject payload = new JSONObject();
        payload.put("text",token);

        WebSocketMessage message = WebSocketMessage.builder()
                .command(WebSocketMessage.Command.ANSWER_STREAM.name())
                .requestId(originalMessage.getRequestId())
                .payload(payload)
                .sessionId(session.getId())
                .build();

        sendToFrontend(session,message);
    }

    private void sendStreamEnd(WebSocketSession session,WebSocketMessage originalMessage,String fullText) {
        JSONObject payload = new JSONObject();
        payload.put("text",fullText);

        WebSocketMessage message = WebSocketMessage.builder()
                .command(WebSocketMessage.Command.ANSWER_STREAM_DONE.name())
                .requestId(originalMessage.getRequestId())
                .payload(payload)
                .sessionId(session.getId())
                .build();

        sendToFrontend(session,message);
    }

    private void sendTtsToPbx(WebSocketSession session,WebSocketMessage originalMessage,String fullText) {
        JSONObject payload = new JSONObject();
        payload.put("text",fullText);

        WebSocketMessage message = WebSocketMessage.builder()
                .command(WebSocketMessage.Command.TTS.name())
                .requestId(originalMessage.getRequestId())
                .payload(payload)
                .sessionId(session.getId())
                .build();

        sendToPbx(session,message);
    }
}
