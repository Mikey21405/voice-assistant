package org.example.voice_assistant.config;

import io.milvus.client.MilvusServiceClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.client.MilvusClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MilvusConfig {

    @Autowired
    private MilvusClientFactory milvusClientFactory;

    private MilvusServiceClient milvusClient;

    /**
     * 创建 MilvusServiceClient Bean
     *
     * @return MilvusServiceClient
     */
    @Bean
    public MilvusServiceClient milvusServiceClient() {
        log.info("正在初始化 Milvus 客户端...");
        milvusClient = milvusClientFactory.createClient();
        log.info("Milvus 客户端初始化完成");
        return milvusClient;
    }

    @PreDestroy
    public void cleanup() {
        if (milvusClient != null) {
            log.info("正在清理 Milvus 客户端...");
            milvusClient.close();
            log.info("Milvus 客户端清理完成");
        }
    }
}
