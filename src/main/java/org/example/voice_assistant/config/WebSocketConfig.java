package org.example.voice_assistant.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.handler.CommandDispatcher;
import org.example.voice_assistant.websocket.SessionManager;
import org.example.voice_assistant.websocket.WebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;


@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {


    private final SessionManager sessionManager;
    private final CommandDispatcher commandDispatcher;

    @Bean
    public WebSocketHandler frontendWebSocketHandler(){
        return new WebSocketHandler(sessionManager,commandDispatcher,"frontend");
    }

    @Bean
    public WebSocketHandler pbxWebSocketHandler(){
        return new WebSocketHandler(sessionManager,commandDispatcher,"pbx");
    }



    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(frontendWebSocketHandler(),"/ws/frontend")
                .setAllowedOrigins("*");

        registry.addHandler(pbxWebSocketHandler(),"/ws/pbx")
                .setAllowedOrigins("*");
    }
}
