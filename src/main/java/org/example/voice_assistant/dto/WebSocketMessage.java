package org.example.voice_assistant.dto;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data  // 生成所有getter/setter；自动生成toString()；自动生成@RequestArgsConstructor
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {

    private String command;

    private String requestId;

    private JSONObject payload;

    private Long assistantId;

    private String sessionId;

    public enum Command {
        ASR_FINAL, // 语音识别完成
        ASR_PARTIAL, // 语音识别部分结果
        ANSWER, // 回答
        ANSWER_STREAM, // 流式回答（逐token）
        ANSWER_STREAM_DONE, // 流式回答完成
        TTS, // 文本转语音
        INVITE, // 建立通话
        HANGUP, // 通话结束
        ERROR, // 错误
        PING, // 心跳
        PONG,
        ECHO // 测试
    }
}
