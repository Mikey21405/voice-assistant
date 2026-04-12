package org.example.voice_assistant.handler;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.dto.WebSocketMessage;
import org.example.voice_assistant.llm.LLMClient;
import org.example.voice_assistant.llm.LLMClientFactory;
import org.example.voice_assistant.websocket.SessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
public class AsrFinalCommandHandler extends BaseCommandHandler{

    private final LLMClientFactory llmClientFactory;

    public AsrFinalCommandHandler(SessionManager sessionManager, LLMClientFactory llmClientFactory) {
        super(sessionManager);
        this.llmClientFactory = llmClientFactory;
    }

    @Override
    public String getCommand() {
        return WebSocketMessage.Command.ASR_FINAL.name();
    }

    @Override
    public void handle(WebSocketSession session, WebSocketMessage message) {
        log.info("处理asr_final信号，session={}",session.getId());

        String text = message.getPayload() != null
                ? message.getPayload().getString("text")
                : null;

        log.info("识别到的asr信息为：{}",text);
        String llmAnswer = callLLM(text);
        log.info("大模型回复：{}",llmAnswer);

        JSONObject answerPayload = new JSONObject();
        answerPayload.put("text",llmAnswer);

        WebSocketMessage frontMessage = WebSocketMessage.builder()
                .command(WebSocketMessage.Command.ANSWER.name())
                .requestId(message.getRequestId())
                .payload(answerPayload)
                .sessionId(session.getId())
                .build();

        log.info("前端收到的消息：sessionId={},requestId={},command={},payload={}"
                , frontMessage.getSessionId(),frontMessage.getRequestId(),frontMessage.getCommand(),frontMessage.getPayload());

        sendToFrontend(session,frontMessage);

        JSONObject ttsPayload = new JSONObject();
        ttsPayload.put("text", llmAnswer);

        WebSocketMessage ttsMessage = WebSocketMessage.builder()
                .command(WebSocketMessage.Command.TTS.name())
                .requestId(message.getRequestId())
                .payload(ttsPayload)
                .sessionId(session.getId())
                .build();

        log.info("pbx收到的消息：sessionId={},requestId={},command={},payload={}"
                , ttsMessage.getSessionId(),ttsMessage.getRequestId(),ttsMessage.getCommand(),ttsMessage.getPayload());
        sendToPbx(session, ttsMessage);
    }

    private String callLLM(String asrText) {
        try {
            LLMClient llmClient = llmClientFactory.getDefaultClient();
            String systemPrompt = "你是一个语音助手的后端，负责理解用户输入并给出简洁友好的回复。回复要口语化，适合语音合成。";
            return llmClient.chat(asrText,systemPrompt);
        } catch (Exception e) {
            log.error("调用大模型失败");
            return "调用大模型失败";
        }
    }
}
