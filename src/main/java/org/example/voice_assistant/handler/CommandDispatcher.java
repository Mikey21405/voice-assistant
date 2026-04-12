package org.example.voice_assistant.handler;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.dto.WebSocketMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommandDispatcher {

    private final List<CommandHandler> commandHandlerList;
    private final Map<String,CommandHandler> handlerMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for(CommandHandler handler:commandHandlerList) {
            String command = handler.getCommand();
            handlerMap.put(command,handler);
            log.info("注册CommandHandler:command={},handler={}",command,handler.getClass().getSimpleName());
        }
    }

    public void dispatch(WebSocketSession session, WebSocketMessage message) {
        String command = message.getCommand();
        log.info("处理消息，command={},session={}",command,session.getId());

        CommandHandler handler = handlerMap.get(command);
        if(handler != null) {
            handler.handle(session,message);
        }else {
            log.warn("未找到该信息类型的Handler");
            handleUnknownCommand(session, message);
        }
    }

    public void handleUnknownCommand(WebSocketSession session,WebSocketMessage message) {
        WebSocketMessage errorResponse = WebSocketMessage.builder()
                .command(WebSocketMessage.Command.ERROR.name())
                .requestId(message.getRequestId())
                .payload(JSON.parseObject("{\"message\":\"Unknown command:" + message.getCommand() + "\"}"))
                .sessionId(session.getId())
                .build();

        try {
            session.sendMessage(new TextMessage(JSON.toJSONString(errorResponse)));
        } catch (IOException e) {
            log.error("发送错误响应失败：",e);
        }
    }
}
