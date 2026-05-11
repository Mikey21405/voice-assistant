package org.example.voice_assistant.handler;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.agent.Agent;
import org.example.voice_assistant.config.RAGConfig;
import org.example.voice_assistant.tts.SentenceBoundaryDetector;
import org.example.voice_assistant.dto.WebSocketMessage;
import org.example.voice_assistant.entity.Assistant;
import org.example.voice_assistant.rag.service.IntentRouter;
import org.example.voice_assistant.rag.service.QueryIntent;
import org.example.voice_assistant.rag.service.RAGQAService;
import org.example.voice_assistant.service.AssistantService;
import org.example.voice_assistant.service.ConversationHistoryService;
import org.example.voice_assistant.websocket.SessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;




@Slf4j
@Component
public class AsrFinalCommandHandler extends BaseCommandHandler{

    private final Agent agent;
    private final ConversationHistoryService conversationHistoryService;
    private final AssistantService assistantService;
    private final RAGQAService ragQAService;
    private final RAGConfig ragConfig;
    private final IntentRouter intentRouter;

    public AsrFinalCommandHandler(SessionManager sessionManager
            , Agent agent
            , ConversationHistoryService conversationHistoryService
            , AssistantService assistantService
            , RAGQAService ragQAService
            , RAGConfig ragConfig
            , IntentRouter intentRouter) {
        super(sessionManager);
        this.agent = agent;
        this.conversationHistoryService = conversationHistoryService;
        this.assistantService = assistantService;
        this.ragQAService = ragQAService;
        this.ragConfig = ragConfig;
        this.intentRouter = intentRouter;
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

            // ============================================
            // 第一层：RAG 总开关
            // ============================================
            if (!useRag) {
                log.info("【普通对话模式】RAG 总开关关闭");
                processWithoutRAG(session, originalMessage, assistantId, asrText, callId,
                                 historyPrompt, baseSystemPrompt);
                return;
            }

            // ============================================
            // 第二层：意图路由（自动判断是否需要检索）
            // ============================================
            if (ragConfig.getAutoRoute()) {
                QueryIntent intent = intentRouter.classify(asrText);

                switch (intent) {
                    case KNOWLEDGE:
                        log.info("【RAG 模式】意图={}，进入完整 RAG 流程", intent);
                        processWithRAG(session, originalMessage, assistantId, asrText, callId,
                                      historyPrompt, baseSystemPrompt);
                        return;
                    case CHAT:
                        log.info("【普通对话模式】意图={}，跳过 RAG", intent);
                        processWithoutRAG(session, originalMessage, assistantId, asrText, callId,
                                         historyPrompt, baseSystemPrompt);
                        return;
                    case COMMAND:
                        log.info("【普通对话模式】意图=COMMAND，当前暂用 LLM 处理");
                        processWithoutRAG(session, originalMessage, assistantId, asrText, callId,
                                         historyPrompt, baseSystemPrompt);
                        return;
                }
            }

            // 兜底：autoRoute=false 时全部走 RAG（保持向后兼容）
            log.info("【RAG 模式】autoRoute=false，全部走 RAG 流程");
            processWithRAG(session, originalMessage, assistantId, asrText, callId,
                          historyPrompt, baseSystemPrompt);

        } catch (Exception e) {
            log.error("❌ 处理失败", e);
            String errorMsg = "抱歉，我暂时无法理解您的意思，请稍后再试。";
            sendStreamEnd(session, originalMessage, errorMsg);
        }
    }

    /**
     * 【RAG模式】按照框架完整流程（真正地流式！）
     */
    private void processWithRAG(WebSocketSession session, WebSocketMessage originalMessage,
                                Long assistantId, String asrText, String callId,
                                String historyPrompt, String baseSystemPrompt) {
        try {
            log.info("🔄 ======== 开始完整RAG流程（流式）========");

            // 获取RAG配置（从应用级别配置）
            int topK = ragConfig.getTopK();

            // 分句流式TTS：token累加 → 检测句子边界 → 立即发送给PBX
            final SentenceBoundaryDetector detector = new SentenceBoundaryDetector();
            final int[] seq = {0};

            // ============================================
            // 【框架步骤 2-5】调用RAGQAService完成（流式！）
            // ============================================
            ragQAService.ragAnswerStream(
                asrText,
                topK,
                historyPrompt,
                baseSystemPrompt,
                // onToken - 流式接收token，检测句子边界
                token -> {
                    sendStreamToken(session, originalMessage, token);
                    String sentence = detector.feed(token);
                    if (sentence != null) {
                        sendTtsSentence(session, originalMessage, sentence, seq[0]++);
                    }
                },
                // onComplete - 完成时
                fullAnswer -> {
                    log.info("✅ ======== RAG流程完成 ========");
                    log.info("📝 最终回答：{}", fullAnswer);
                    sendStreamEnd(session, originalMessage, fullAnswer);
                    conversationHistoryService.saveHistory(assistantId, session.getId(), callId, asrText, fullAnswer);
                    String remaining = detector.flush();
                    if (remaining != null) {
                        sendTtsSentence(session, originalMessage, remaining, seq[0]++);
                    }
                    sendTtsStreamDone(session, originalMessage);
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
            log.info("📤 Agent 流式处理");

            String systemPrompt = assistantService.getSystemPromptWithHistory(assistantId, historyPrompt);
            log.info("📝 系统提示词：{}", systemPrompt);

            final SentenceBoundaryDetector detector = new SentenceBoundaryDetector();
            final int[] seq = {0};
            StringBuilder fullResponse = new StringBuilder();
            agent.runStream(
                    asrText,
                    systemPrompt,
                    token -> {
                        fullResponse.append(token);
                        sendStreamToken(session, originalMessage, token);
                        String sentence = detector.feed(token);
                        if (sentence != null) {
                            sendTtsSentence(session, originalMessage, sentence, seq[0]++);
                        }
                    },
                    fullText -> {
                        log.info("✅ Agent 回复完成");
                        sendStreamEnd(session, originalMessage, fullText);
                        conversationHistoryService.saveHistory(assistantId, session.getId(), callId, asrText, fullText);
                        String remaining = detector.flush();
                        if (remaining != null) {
                            sendTtsSentence(session, originalMessage, remaining, seq[0]++);
                        }
                        sendTtsStreamDone(session, originalMessage);
                    },
                    e -> {
                        log.error("❌ Agent 调用失败", e);
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

    private void sendTtsSentence(WebSocketSession session, WebSocketMessage originalMessage,
                                  String sentence, int sequence) {
        JSONObject payload = new JSONObject();
        payload.put("text", sentence);
        payload.put("sequence", sequence);

        WebSocketMessage message = WebSocketMessage.builder()
                .command(WebSocketMessage.Command.TTS_SENTENCE.name())
                .requestId(originalMessage.getRequestId())
                .payload(payload)
                .sessionId(session.getId())
                .build();

        sendToPbx(session, message);
    }

    private void sendTtsStreamDone(WebSocketSession session, WebSocketMessage originalMessage) {
        JSONObject payload = new JSONObject();
        payload.put("text", "");

        WebSocketMessage message = WebSocketMessage.builder()
                .command(WebSocketMessage.Command.TTS_STREAM_DONE.name())
                .requestId(originalMessage.getRequestId())
                .payload(payload)
                .sessionId(session.getId())
                .build();

        sendToPbx(session, message);
    }
}
