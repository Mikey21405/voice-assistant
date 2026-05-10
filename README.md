# 5.10.1

###  新建SentenceBoundaryDetector.java

```java
private static final Pattern SENTENCE_END = Pattern.compile("[。！？,.!?]");
```

- `feed(token)`: 将 token 追加到内部 `StringBuilder` buffer，用正则匹配 buffer 中最后一个句末标点位置。若找到且不在开头，截取 `[0, lastEnd)` 作为完整句子返回，剩余部分留在 buffer。若未找到返回 `null`。
- `flush()`: 返回 buffer 中剩余文本并清空，buffer 为空时返回 `null`。

###  修改 WebSocketMessage.java

在 `Command` 枚举中，`TTS` 之后新增两项：

```java
TTS_SENTENCE,      // 分句TTS（逐句发送）
TTS_STREAM_DONE,   // 分句TTS结束
```

### 修改 AsrFinalCommandHandler.java

**import 新增：** 第 7 行加入 `SentenceBoundaryDetector`

**`processWithRAG` 方法（行 125-155）：** 删除了原来 `sendTtsToPbx(session, originalMessage, fullAnswer)` 的整段发送逻辑，改为：

- 方法入口创建 `detector` + `seq` 计数器
- `onToken` 回调中：`sendStreamToken` 之后调用 `detector.feed(token)`，若返回句子则 `sendTtsSentence`
- `onComplete` 回调中：`sendStreamEnd` + 保存历史 之后调用 `detector.flush()`，若有剩余文本则 `sendTtsSentence`，最后 `sendTtsStreamDone`

**`processWithoutRAG` 方法（行 184–207）：** 同上，在 `agent.runStream` 的 `onToken` / `onComplete` 中嵌入相同的分句检测逻辑。

**删除方法：** 原来的 `sendTtsToPbx`（一次发送全文给 PBX，走 `TTS` 命令）

**新增方法：**

- `sendTtsSentence(session, originalMessage, sentence, sequence)` — 构建 `TTS_SENTENCE` 消息，payload 含 `text` 和 `sequence`
- `sendTtsStreamDone(session, originalMessage)` — 构建 `TTS_STREAM_DONE` 消息，payload 含空 `text`

# 5.10.2

### 新建

**WebClientConfig.java** — 替代 RestTemplateConfig

- Reactor Netty `ConnectionProvider`，连接池 200 连接、空闲 60s 回收、生命周期 300s
- `readTimeout` 120s（适配 LLM 长响应）、`writeTimeout` 30s、`connectTimeout` 10s

### 删除

**RestTemplateConfig.java** — 移除

### 重写

**QwenClient.java**

- 构造函数注入 `WebClient` 替代 `@RequiredArgsConstructor` + `RestTemplate`
- 3 个同步方法 (`chat` / `chatWithTools` / `chatWithMessages`) 统一走 `postForResponse()` — `webClient.post()...bodyToMono().block()`
- 流式方法 `executeStreamRequest()` — `webClient.post().accept(TEXT_EVENT_STREAM)...bodyToFlux(String.class).blockLast()`，用 `doOnNext` 逐 chunk 回调
- 新增 `processSseChunk()` 处理跨 chunk 不完整 SSE 行（`lineBuffer` 累加 → 找到 `\n` → 按行解析 `data:` 前缀 → fastjson2 解析 delta.content）
- 异常类型：`HttpClientErrorException` / `ResourceAccessException` → `WebClientResponseException` / `WebClientRequestException`

**WeatherTool.java**

- `RestTemplate restTemplate` → `WebClient webClient`
- `restTemplate.exchange(uri, GET, entity, String.class).getBody()` → `webClient.get().uri(uri).header(...).retrieve().bodyToMono(String.class).block()`
- 移除 `HttpEntity`、`HttpHeaders`、`ResponseEntity` 等模板式代码

### 依赖

**pom.xml** — 新增 `spring-boot-starter-webflux`（仅用 WebClient，不影响现有 Tomcat servlet 容器）