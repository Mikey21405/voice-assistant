package org.example.voice_assistant.handler;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.dto.WebSocketMessage;
import org.example.voice_assistant.websocket.SessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

// 信令处理器基类，提供公共方法和属性，具体的命令处理器继承该类实现handle方法
@Slf4j
@RequiredArgsConstructor
public abstract class BaseCommandHandler implements CommandHandler {

    protected final SessionManager sessionManager;

    public void sendMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            session.sendMessage(new TextMessage(JSON.toJSONString(message)));
            log.info("发送消息,sessionId={},payload={}", session.getId(), message.getCommand());
        } catch (Exception e) {
            log.error("发送消息失败,sessionId={},error={}", session.getId(), e.getMessage());
        }
    }

    public void sendToPairedSession(WebSocketSession session, WebSocketMessage message) {
        String sessionId = session.getId();
        WebSocketSession pairedSession = sessionManager.getPairedSessions(sessionId);
        if (pairedSession != null && pairedSession.isOpen()) {
            sendMessage(pairedSession, message);
        } else {
            log.warn("未找到配对会话，无法发送消息:sessionId={}", session.getId());
        }
    }

    public void sendToFrontend(WebSocketSession session, WebSocketMessage message) {
        if (sessionManager.isFrontendSession(session.getId())) {
            sendMessage(session, message);
        } else {
            sendToPairedSession(session, message);
        }
    }

    public void sendToPbx(WebSocketSession session, WebSocketMessage message) {
        if (sessionManager.isPbxSession(session.getId())) {
            sendMessage(session, message);
        } else {
            sendToPairedSession(session, message);
        }
    }

    protected String getPairedSessionId(WebSocketSession session, WebSocketMessage message) {
        if (message.getPayload() != null) {
            String pairedSessionId = message.getPayload().getString("pairedSessionId");
            if (pairedSessionId != null && !pairedSessionId.isEmpty()) {
                return pairedSessionId;
            }
        }

        if (sessionManager.isFrontendSession(session.getId())) {

            String pbxSessionId = sessionManager.getFirstPbxSessionId();
            if (pbxSessionId != null) {
                log.info("Mock模式：自动获取pbxSessionId={}", pbxSessionId);
                return pbxSessionId;
            }
        }

        return null;
    }
}