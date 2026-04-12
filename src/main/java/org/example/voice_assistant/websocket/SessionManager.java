package org.example.voice_assistant.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SessionManager {
    private final Map<String, WebSocketSession> frontendSessions = new ConcurrentHashMap<>();
    private final Map<String,WebSocketSession> pbxSessions = new ConcurrentHashMap<>();
    private final Map<String,String> sessionPairMap = new ConcurrentHashMap<>();

    public void addFrontendSession(WebSocketSession session) {
        frontendSessions.put(session.getId(), session);
        log.info("添加前端会话:sessionId={},当前前端会话数量={}",session.getId(),frontendSessions.size());
    }

    public void addPbxSession(WebSocketSession session) {
        pbxSessions.put(session.getId(), session);
        log.info("添加PBX会话:sessionId={},当前PBX会话数量={}",session.getId(),pbxSessions.size());
    }

    public void removeSession(String sessionId) {
        if(frontendSessions.containsKey(sessionId)) {
            frontendSessions.remove(sessionId);
            log.info("移除前端会话:sessionId={},当前前端会话数量={}",sessionId,frontendSessions.size());
        }
        if(pbxSessions.containsKey(sessionId)) {
            pbxSessions.remove(sessionId);
            log.info("移除PBX会话:sessionId={},当前PBX会话数量={}",sessionId,pbxSessions.size());
        }
        if(sessionPairMap.containsKey(sessionId)) {
            String pairedSessionId = sessionPairMap.get(sessionId);
            sessionPairMap.remove(sessionId);
            sessionPairMap.remove(pairedSessionId);
            log.info("移除会话配对:sessionId={},pairedSessionId={}",sessionId,pairedSessionId);
        }
    }

    public void pairSessions(String frontendSessionId, String pbxSessionId) {
        sessionPairMap.put(frontendSessionId, pbxSessionId);
        sessionPairMap.put(pbxSessionId, frontendSessionId);
        log.info("会话配对成功:frontendSessionId={},pbxSessionId={}",frontendSessionId,pbxSessionId);
    }

    public WebSocketSession getPairedSessions(String sessionId) {
        String pairedSessionId = sessionPairMap.get(sessionId);
        if(pairedSessionId == null) {
            log.warn("未找到配对会话:sessionId={}",sessionId);
            return null;
        }
        WebSocketSession pairedSession = frontendSessions.get(pairedSessionId);
        if(pairedSession == null) {
            pairedSession = pbxSessions.get(pairedSessionId);
        }
        return pairedSession;
    }

    public String getFirstPbxSessionId() {
        return pbxSessions.keySet().stream().findFirst().orElse(null);
    }

    public boolean isFrontendSession(String sessionId) {
        return frontendSessions.containsKey(sessionId);
    }

    public boolean isPbxSession(String sessionId) {
        return pbxSessions.containsKey(sessionId);
    }

    public WebSocketSession getFrontendSession(String sessionId) {
        return frontendSessions.get(sessionId);
    }

    public WebSocketSession getPbxSession(String sessionId) {
        return pbxSessions.get(sessionId);
    }
}
