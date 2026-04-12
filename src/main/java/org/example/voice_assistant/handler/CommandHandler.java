package org.example.voice_assistant.handler;


import org.example.voice_assistant.dto.WebSocketMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public interface CommandHandler{

    String getCommand();

    void handle(WebSocketSession session, WebSocketMessage message);
}
