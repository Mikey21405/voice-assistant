package org.example.voice_assistant.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 全局配置 — 应用级别的策略控制。
 *
 * 配置项说明：
 *   enabled   总开关，false 时所有请求都不走 RAG
 *   autoRoute 自动意图路由，true 时由 IntentRouter 按查询类型分流
 *   minScore  向量检索相关性阈值，低于此分数的文档视为不相关
 *   topK      检索返回的最大文档数
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RAGConfig {

    /** RAG 总开关（默认开启） */
    private Boolean enabled = true;

    /** 自动意图路由：按查询类型决定是否走 RAG */
    private Boolean autoRoute = true;

    /** 向量检索相关性阈值（0~1），低于此值的文档不被采用 */
    private Double minScore = 0.55;

    /** RAG 检索返回的最大文档数量 */
    private Integer topK = 5;

    public void enable() {
        this.enabled = true;
        log.info("RAG 功能已开启");
    }

    public void disable() {
        this.enabled = false;
        log.info("RAG 功能已关闭");
    }

    public Boolean toggle() {
        this.enabled = !this.enabled;
        log.info("RAG 功能已{}", this.enabled ? "开启" : "关闭");
        return this.enabled;
    }

    public Boolean isEnabled() {
        return this.enabled;
    }

    public void setTopK(Integer topK) {
        if (topK != null && topK > 0) {
            this.topK = topK;
            log.info("RAG topK 已设置为: {}", topK);
        }
    }

    public void setMinScore(Double minScore) {
        if (minScore != null && minScore >= 0 && minScore <= 1) {
            this.minScore = minScore;
            log.info("RAG minScore 已设置为: {}", minScore);
        }
    }
}
