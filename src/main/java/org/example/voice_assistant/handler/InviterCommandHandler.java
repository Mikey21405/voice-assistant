package org.example.voice_assistant.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.dto.WebSocketMessage;
import org.example.voice_assistant.websocket.SessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
public class InviterCommandHandler extends BaseCommandHandler{

    public InviterCommandHandler(SessionManager sessionManager) {
        super(sessionManager);
    }

    @Override
    public String getCommand() {
        return WebSocketMessage.Command.INVITE.name();
    }

    @Override
    public void handle(WebSocketSession session, WebSocketMessage message) {
        log.info("处理invite命令,sessionId={},sessionType={}",session.getId(),sessionManager.isFrontendSession(session.getId())?"frontend":"PBX");
        String pairedSessionId = getPairedSessionId(session,message);

        if(pairedSessionId != null) {
            sessionManager.pairSessions(session.getId(),pairedSessionId);
            log.info("会话配对：session={},pairedSessionId={}",session.getId(),pairedSessionId);
        }

        JSONObject payload = new JSONObject();
        payload.put("status","ok");
        payload.put("message","connect to pbx establish");
        payload.put("sessionId",session.getId());
        log.info("模拟生成pbx的answer信号");
        WebSocketMessage response = WebSocketMessage.builder()
                .command(WebSocketMessage.Command.ANSWER.name())
                .requestId(message.getRequestId())
                .payload(payload)
                .assistantId(message.getAssistantId())
                .sessionId(session.getId())
                .build();

        sendMessage(session,response);

    }
}
