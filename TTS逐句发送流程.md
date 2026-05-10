## TTS 逐句发送流程

### 整体架构图



```
ASR识别完成
    │
    ▼
AsrFinalCommandHandler.handle()
    │
    ├─ RAG模式 ──→ RAGQAService.ragAnswerStream()
    │                   │
    └─ 普通模式 ──→ Agent.runStream()         │
                        │                     │
                        ▼                     ▼
                  LLMClient.chatStream()  ←───┘  (QwenClient SSE)
                        │
                        ▼ (逐个token回调)
                  onToken 回调
                        │
                   ┌────┴────┐
                   │          │
              sendStreamToken   detector.feed(token)
              (发给前端展示)       │
                                   ▼
                              检测到句子边界?
                               │       │
                              YES      NO → 继续积累
                               │
                               ▼
                          sendTtsSentence(sentence, seq++)
                          (发送给PBX做语音合成播放)
```

------

### 各层详细说明

#### 第 1 层：入口 — [AsrFinalCommandHandler.java:50](vscode-webview://1cbj3sfrkenps9d6d4evrlfcalqlthn6bukobdcop779sruqghro/src/main/java/org/example/voice_assistant/handler/AsrFinalCommandHandler.java#L50)

当语音识别（ASR）完成后，`ASR_FINAL` 命令触发 `handle()`。它从消息中取出识别文本 `text` 和助手 ID，然后根据 `ragConfig.isEnabled()` 决定走 RAG 模式还是普通对话模式。

#### 第 2 层：SentenceBoundaryDetector — [SentenceBoundaryDetector.java:10](vscode-webview://1cbj3sfrkenps9d6d4evrlfcalqlthn6bukobdcop779sruqghro/src/main/java/org/example/voice_assistant/tts/SentenceBoundaryDetector.java#L10)

这是逐句拆分的**核心工具类**。它内部维护一个 `StringBuilder buffer`：

- **`feed(token)`**：把新 token 追加到 buffer → 用正则 `[。！？,.!?]` 查找最后一个句子结束标点的位置 → 如果找到，切出前面的完整句子返回，剩余部分留在 buffer 中；没找到返回 `null`
- **`flush()`**：LLM 输出结束后，把 buffer 中剩余的尾部文本（没有结束标点的）强行返回

#### 第 3 层：LLM 流式调用 — [QwenClient.java:177](vscode-webview://1cbj3sfrkenps9d6d4evrlfcalqlthn6bukobdcop779sruqghro/src/main/java/org/example/voice_assistant/llm/QwenClient.java#L177)

`QwenClient.chatStream()` 使用 Spring WebClient 的 `bodyToFlux(String.class)` 消费 SSE 流：

1. 逐 chunk 解析，兼容 SSE（`data: {...}`）和 NDJSON 格式
2. 从 `delta.content` 中提取每个 token
3. 调用 `onToken.accept(content)` 回调给上层
4. 流结束后调用 `onComplete.accept(fullResponse)`

`blockLast()` 保证方法同步阻塞，但 token 回调是实时触发的。

#### 第 4 层：句子的检测与发送 — [AsrFinalCommandHandler.java:126-163](vscode-webview://1cbj3sfrkenps9d6d4evrlfcalqlthn6bukobdcop779sruqghro/src/main/java/org/example/voice_assistant/handler/AsrFinalCommandHandler.java#L126-L163)

以 RAG 模式为例（普通模式逻辑相同），这是**核心编排逻辑**：



```java
final SentenceBoundaryDetector detector = new SentenceBoundaryDetector();
final int[] seq = {0};

ragQAService.ragAnswerStream(
    asrText, topK, historyPrompt, baseSystemPrompt,
    // onToken — 每收到一个token就触发
    token -> {
        sendStreamToken(session, originalMessage, token);  // ①发给前端展示
        String sentence = detector.feed(token);             // ②喂给检测器
        if (sentence != null) {
            sendTtsSentence(session, originalMessage, sentence, seq[0]++); // ③检测到完整句子，发PBX
        }
    },
    // onComplete — LLM流结束
    fullAnswer -> {
        sendStreamEnd(session, originalMessage, fullAnswer);              // ④通知前端流结束
        conversationHistoryService.saveHistory(...);                       // ⑤保存对话历史
        String remaining = detector.flush();                              // ⑥flush尾部残留
        if (remaining != null) {
            sendTtsSentence(session, originalMessage, remaining, seq[0]++);
        }
        sendTtsStreamDone(session, originalMessage);                      // ⑦通知PBX TTS结束
    },
    ...
);
```

#### 第 5 层：WebSocket 消息路由 — [BaseCommandHandler.java:38-52](vscode-webview://1cbj3sfrkenps9d6d4evrlfcalqlthn6bukobdcop779sruqghro/src/main/java/org/example/voice_assistant/handler/BaseCommandHandler.java#L38-L52)

发送时区分**两类接收方**：

| 方法                                 | 命令                 | 目标                 |
| ------------------------------------ | -------------------- | -------------------- |
| `sendToFrontend` → `sendStreamToken` | `ANSWER_STREAM`      | 前端页面（逐字展示） |
| `sendToFrontend` → `sendStreamEnd`   | `ANSWER_STREAM_DONE` | 前端页面（流结束）   |
| `sendToPbx` → `sendTtsSentence`      | `TTS_SENTENCE`       | PBX（逐句语音合成）  |
| `sendToPbx` → `sendTtsStreamDone`    | `TTS_STREAM_DONE`    | PBX（TTS结束）       |

路由逻辑：通过 `SessionManager` 判断当前 session 是前端还是 PBX，如果是前端则发给配对的 PBX，反之亦然。

------

### 时序总结



```
时间轴 ───────────────────────────────────────────────────────►

LLM输出:  "你好"  "，"  "今天"  "天气"  "不错"  "。"  "明天"  "也"  "会"  "很好"  [EOF]
           │      │     │      │      │      │     │      │    │     │       │
feed():    │      │     │      │      │      │→返回"你好，今天天气不错。"       │
           │      │     │      │      │      │     │      │    │     │       │
TTS发送:                                         → sendTtsSentence(seq=0)   │
前端展示:  每个token都实时推送给前端                                         │
                                                                   │       │
flush():                                                                   │→返回"明天也会很好"
TTS发送:                                                                      → sendTtsSentence(seq=1)
                                                                                  → sendTtsStreamDone
```

关键设计点：

- **token 级别**流式发给前端，用户能看到逐字出现
- **句子级别**发给 PBX 做 TTS，确保语音合成拿到完整语义单元，避免断句生硬
- `SentenceBoundaryDetector` 的中/英文标点 `[。！？,.!?]` 都算句子边界，兼顾中英文混合场景
- `flush()` 保证 LLM 输出的最后一段不完整文本（无标点结尾）也能被合成播放