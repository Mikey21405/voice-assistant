package org.example.voice_assistant.websocket;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.dto.WebSocketMessage;
import org.example.voice_assistant.handler.CommandDispatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
public class WebSocketHandler extends TextWebSocketHandler {
    private final SessionManager sessionManager;
    private final CommandDispatcher commandDispatcher;
    private final String type;

    public WebSocketHandler(SessionManager sessionManager, CommandDispatcher commandDispatcher,String type) {
        this.sessionManager = sessionManager;
        this.commandDispatcher = commandDispatcher;
        this.type = type;
    }

    public WebSocketHandler() {
        this.sessionManager = null;
        this.commandDispatcher = null;
        this.type = null;
    }


    // 建立连接时调用
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        log.info("{} WebSocket 连接成功：{}",this.type,session.getId());
        if ("frontend".equals(type)) {
            if (sessionManager != null) {
                sessionManager.addFrontendSession(session);
            }
        } else if ("pbx".equals(type)) {
            if (sessionManager != null) {
                sessionManager.addPbxSession(session);
            }
        }
        super.afterConnectionEstablished(session);
    }

    // 接收消息时调用
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("收到消息,sessionid={},payload={}",session.getId(),payload);
        try {
            WebSocketMessage webSocketMessage = JSON.parseObject(payload,WebSocketMessage.class);
            webSocketMessage.setSessionId(session.getId());
            commandDispatcher.dispatch(session,webSocketMessage);
        } catch (Exception e) {
            log.error("解析消息失败, payload={}", payload, e);
            sendError(session, "Invalid message format");
        }

    }

    // 连接关闭时调用
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket 连接关闭，sessionId={},status={}",session.getId(),status);

        super.afterConnectionClosed(session,status);
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            WebSocketMessage errorResponse = WebSocketMessage.builder()
                    .command(WebSocketMessage.Command.ERROR.name())
                    .payload(JSON.parseObject("{\"message\":\"" + errorMessage + "\"}"))
                    .build();
            session.sendMessage(new TextMessage(JSON.toJSONString(errorResponse)));
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
    }
}
