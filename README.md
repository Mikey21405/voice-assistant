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

# 5.11.1

## 修改清单

| 文件                        | 操作     | 说明                                          |
| --------------------------- | -------- | --------------------------------------------- |
| QueryIntent.java            | **新建** | 三分类枚举：KNOWLEDGE / CHAT / COMMAND        |
| IntentRouter.java           | **新建** | 意图路由器，规则+LLM 两级分类                 |
| RAGConfig.java              | **修改** | 新增 `autoRoute`、`minScore` 两个策略配置项   |
| application.properties      | **修改** | 新增 `rag.auto-route`、`rag.min-score` 默认值 |
| RAGQAService.java           | **修改** | 新增 `hasRelevantResults()` 相关性阈值过滤    |
| AsrFinalCommandHandler.java | **修改** | 注入 IntentRouter，三层漏斗决策取代一刀切     |

------

## 完整流程：用户一句话从输入到回答的全路径



```
用户说："帮我查一下合同条款"
  │
  ▼
┌─────────────────────────────────────────────────┐
│  AsrFinalCommandHandler.processWithRAGFramework │
│                                                  │
│  ① 第一层：RAG 总开关                             │
│     ragConfig.isEnabled() == false → 普通对话     │
│                                                  │
│  ② 第二层：意图路由 (IntentRouter.classify)        │
│     ├─ 规则匹配：先走关键词/正则，命中 → 直接返回   │
│     │   "帮我查" → KNOWLEDGE_PATTERN 命中          │
│     │   返回 QueryIntent.KNOWLEDGE                 │
│     │                                             │
│     └─ 规则未命中 → LLM 轻量分类（~150 token）     │
│         prompt："判断意图，只回复一个字母 A/B/C"    │
│                                                  │
│  ③ 按意图分流                                     │
│     KNOWLEDGE → processWithRAG()                  │
│     CHAT      → processWithoutRAG()               │
│     COMMAND   → processWithoutRAG()（可扩展）       │
└──────────────────┬──────────────────────────────┘
                   ▼
┌─────────────────────────────────────────────────┐
│  RAGQAService.ragAnswerStream()                  │
│                                                  │
│  ④ 查询预处理 (QueryPreprocessor)                 │
│     用户输入 → LLM 返回 JSON → 解析为查询列表      │
│     "帮我查一下合同条款"                            │
│     → {"queries": ["合同条款","合同内容","合同规定"]}│
│                                                  │
│  ⑤ 多查询向量检索                                  │
│     searchWithMultipleQueries(queries, topK)      │
│     → 返回相似文档列表（含相似度分数）              │
│                                                  │
│  ⑥ 第三层：相关性阈值过滤 (hasRelevantResults)      │
│     最高相似度 < minScore(0.55) → "无相关文档"降级  │
│     最高相似度 ≥ 0.55 → 文档有效，进入 Prompt 构建  │
│                                                  │
│  ⑦ Prompt 构建 → LLM 流式生成 → TTS 逐句播放       │
└─────────────────────────────────────────────────┘
```

------

## 各方法的实现讲解

### 1. `IntentRouter.classify(String query)` — 意图分类入口



```java
public QueryIntent classify(String query) {
    // 第一步：规则快速匹配（0 token，0 延迟）
    QueryIntent ruleResult = ruleMatch(q);
    if (ruleResult != null) return ruleResult;

    // 第二步：LLM 轻量分类（只在规则无法判断时触发）
    return llmClassify(q);
}
```

**设计要点**：规则优先、LLM 兜底。为什么这样做？

- 规则匹配对着"你好""谢谢""帮我查"这类明确信号，准确率接近 100%，且不消耗 Token
- LLM 分类用极短 prompt（约 150 tokens），比一次完整的 RAG 检索便宜得多
- 闲聊被规则拦截后，完全不会触发后续的查询预处理和向量检索，节省大量开销

### 2. `IntentRouter.ruleMatch(String query)` — 规则匹配

三个维度：

- **闲聊白名单**：精确匹配问候语、感谢语、常见闲聊
- **命令正则**：匹配"打开/关闭/设置"等操作动词
- **知识查询正则**：匹配"查/搜索/合同/会议/规定/怎么/如何"等信号词

返回 `null` 表示"我判断不了"，交给 LLM。

### 3. `IntentRouter.llmClassify(String query)` — LLM 分类



```java
private QueryIntent llmClassify(String query) {
    String result = llmClient.chat(query, INTENT_CLASSIFY_PROMPT);
    // 解析 LLM 回复的第一个字母 A/B/C
    if (result.contains("A")) return KNOWLEDGE;
    if (result.contains("C")) return COMMAND;
    return CHAT;  // 默认保守降级
}
```

**容错策略**：LLM 调用失败 → 降级为 `CHAT`。这比降级为 `KNOWLEDGE` 更安全——因为闲聊走 RAG 只会浪费资源，但知识查询不走 RAG 会导致回答质量差。不过 LLM 分类失败的几率很低，且规则已覆盖了高置信度场景。

### 4. `RAGQAService.hasRelevantResults(List<SearchResult>)` — 相关性阈值



```java
private boolean hasRelevantResults(List<SearchResult> results) {
    if (results == null || results.isEmpty()) return false;
    float maxScore = 0f;
    for (SearchResult r : results) maxScore = Math.max(maxScore, r.getScore());
    return maxScore >= ragConfig.getMinScore();  // 默认 0.55
}
```

**为什么需要这个**：向量数据库会为任何查询返回"最相似"的文档，哪怕内容完全不相关。比如用户问"今天天气怎么样"，向量库中最近的文档可能是某个合同条款（相似度 0.32），如果不过滤直接喂给 LLM，反而产生幻觉。阈值 0.55 是一个保守的起点，可以根据你实际数据的分数分布调优。

### 5. `AsrFinalCommandHandler.processWithRAGFramework()` — 决策中心

现在是三层漏斗：



```
总开关(false) → 普通对话（快速退出）
    ↓
autoRoute(true) → IntentRouter 判断意图
    ├─ CHAT    → 普通对话（跳过所有 RAG 开销）
    ├─ COMMAND → 普通对话（预留给工具调用扩展）
    └─ KNOWLEDGE → 进 RAG 管道
autoRoute(false) → 兼容模式，全部走 RAG
```

------

## 关键设计决策

| 决策点               | 选择         | 原因                                   |
| -------------------- | ------------ | -------------------------------------- |
| 意图分类先规则后 LLM | 规则优先     | 80% 场景规则可覆盖，0 Token 开销       |
| LLM 分类失败降级     | 降级为 CHAT  | 保守策略，避免无效 RAG 开销            |
| 相关性阈值默认值     | 0.55         | 经验值，需要根据实际数据调参           |
| autoRoute 开关       | 可关闭       | 保留向后兼容，关闭后行为与原来完全一致 |
| COMMAND 意图处理     | 暂走普通对话 | 预留扩展点，以后可接入工具调用         |