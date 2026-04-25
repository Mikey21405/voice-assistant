package org.example.voice_assistant.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG全局配置
 * 应用级别的总开关，无需修改数据库
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RAGConfig {

    /**
     * RAG总开关（默认关闭）
     */
    private Boolean enabled = true;

    /**
     * RAG检索文档数量（默认5）
     */
    private Integer topK = 5;

    /**
     * 开启RAG
     */
    public void enable() {
        this.enabled = true;
        log.info("✅ RAG功能已开启");
    }

    /**
     * 关闭RAG
     */
    public void disable() {
        this.enabled = false;
        log.info("⚠️ RAG功能已关闭");
    }

    /**
     * 切换RAG开关
     * @return 切换后的状态
     */
    public Boolean toggle() {
        this.enabled = !this.enabled;
        log.info("🔄 RAG功能已{}", this.enabled ? "开启" : "关闭");
        return this.enabled;
    }

    /**
     * 检查RAG是否开启
     */
    public Boolean isEnabled() {
        return this.enabled;
    }

    /**
     * 设置topK
     */
    public void setTopK(Integer topK) {
        if (topK != null && topK > 0) {
            this.topK = topK;
            log.info("✅ RAG topK已设置为: {}", topK);
        }
    }
}
