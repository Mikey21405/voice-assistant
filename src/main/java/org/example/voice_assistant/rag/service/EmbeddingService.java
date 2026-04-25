package org.example.voice_assistant.rag.service;

import com.alibaba.dashscope.embeddings.*;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class EmbeddingService {

    @Value("${dashscope.api.key}")
    private String apiKey;

    @Value("${dashscope.embedding.model}")
    private String model;

    private TextEmbedding textEmbedding;

    @PostConstruct
    public void init() {
        // 验证API Key
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("API Key 未正确配置！当前值：{}",apiKey);
            throw new IllegalStateException("请设置环境变量 API Key");
        }

        // 打印API Key 前缀用于调试
        String mashedKey = apiKey.length() > 8 ?
                apiKey.substring(0,8) + "..." + apiKey.substring(apiKey.length() - 4) :
                "***";
        log.info("DashScope API Key 前缀：{}",mashedKey);

        // 设置全局API Key
        Constants.apiKey = apiKey;

        // 验证 API Key是否设置成功
        if(Constants.apiKey == null || Constants.apiKey.isEmpty()) {
            log.error("COnstants.apiKey 设置失败！");
            throw new IllegalStateException("API Key 设置到Constants 失败");
        }

        log.info("Constants.apiKey 已设置：{}",Constants.apiKey.substring(0,Math.min(8, Constants.apiKey.length())) + "...");

        // 创建 TextEmbedding 实例
        textEmbedding = new TextEmbedding();

        log.info("阿里云 DashScope Embedding 服务初始化完成，模型为{}",model);

    }

    /**
     * 生成向量嵌入
     * 调用阿里云 DashScope Text Embedding API
     *
     * @param content
     * @return 向量嵌入
     */
    public List<Float> generateEmbedding(String content) {
        try {
            if(content == null || content.trim().isEmpty()) {
                log.warn("内容为空，无法生成向量");
                throw new IllegalStateException("内容不能为空");
            }

            log.debug("开始生成向量嵌入，内容长度：{} 字符",content.length());

            // 验证 API Key
            if(Constants.apiKey == null || Constants.apiKey.isEmpty()) {
                log.warn("API Key 为空，进行重新设置");
                Constants.apiKey = apiKey;
            }

            log.debug("调用 API 前 Constants.apiKey:{}",Constants.apiKey != null ? Constants.apiKey.substring(0, Math.min(8, Constants.apiKey.length())) + "..." : "null");

            // 构建请求参数
            TextEmbeddingParam param = TextEmbeddingParam
                    .builder()
                    .model(model)
                    .texts(Collections.singletonList(content))
                    .build();

            // 调用 API
            TextEmbeddingResult result = textEmbedding.call(param);

            // 检查结果
            List<Float> floatEmbedding = getFloats(result);

            log.info("成功生成向量嵌入，内容长度:{}字符，向量维度:{}",content.length(),floatEmbedding.size());

            return floatEmbedding;
        } catch (NoApiKeyException e) {
            log.error("API Key 未设置或无效", e);
            throw new RuntimeException("API Key 未设置，请配置 dashscope.api.key", e);
        } catch (Exception e) {
            log.error("生成向量嵌入失败, 内容长度: {}", content != null ? content.length() : 0, e);
            throw new RuntimeException("生成向量嵌入失败: " + e.getMessage(), e);
        }

    }

    // 解析结果获取向量文本
    @NotNull
    private static List<Float> getFloats(TextEmbeddingResult result) {
        if(result == null || result.getOutput() == null || result.getOutput().getEmbeddings() == null) {
            throw new RuntimeException("DashScope API 返回空结果");
        }

        TextEmbeddingOutput output = result.getOutput();
        List<TextEmbeddingResultItem> embeddings = output.getEmbeddings();

        if(embeddings.isEmpty()) {
            throw new RuntimeException("DashScope API 返回空向量列表");
        }

        // 获取第一个文本向量
        List<Double> embeddingDoubles = embeddings.get(0).getEmbedding();

        // 转换为List<Float>
        List<Float> floatEmbedding = new ArrayList<>(embeddingDoubles.size());
        for(Double value : embeddingDoubles) {
            floatEmbedding.add(value.floatValue());
        }
        return floatEmbedding;
    }

    /**
     * 批量生成向量嵌入
     *
     * @param contents 文本内容列表
     * @return 向量嵌入列表
     */
    public List<List<Float>> generateEmbeddings(List<String> contents) {
        try {
            if (contents == null || contents.isEmpty()) {
                log.warn("内容列表为空，无法生成向量");
                return Collections.emptyList();
            }

            log.info("开始批量生成向量嵌入, 数量: {}", contents.size());

            // 确保 API Key 已设置
            if (Constants.apiKey == null || Constants.apiKey.isEmpty()) {
                log.warn("检测到 Constants.apiKey 为空，重新设置");
                Constants.apiKey = apiKey;
            }

            // 构建请求参数 - 批量输入
            TextEmbeddingParam param = TextEmbeddingParam
                    .builder()
                    .model(model)
                    .texts(contents)
                    .build();

            // 调用 API
            TextEmbeddingResult result = textEmbedding.call(param);

            // 检查结果
            if (result == null || result.getOutput() == null || result.getOutput().getEmbeddings() == null) {
                throw new RuntimeException("批量 DashScope API 返回空结果");
            }

            List<TextEmbeddingResultItem> embeddingItems = result.getOutput().getEmbeddings();

            if (embeddingItems.isEmpty()) {
                throw new RuntimeException("批量 DashScope API 返回空向量列表");
            }

            // 转换结果
            List<List<Float>> embeddings = new ArrayList<>();
            for (TextEmbeddingResultItem item : embeddingItems) {
                List<Double> embeddingDoubles = item.getEmbedding();
                List<Float> embedding = new ArrayList<>(embeddingDoubles.size());
                for (Double value : embeddingDoubles) {
                    embedding.add(value.floatValue());
                }
                embeddings.add(embedding);
            }

            log.info("成功批量生成向量嵌入, 数量: {}, 维度: {}",
                    embeddings.size(),
                    embeddings.isEmpty() ? 0 : embeddings.get(0).size());

            return embeddings;
        }catch (NoApiKeyException e) {
            log.error("批量调用时 API Key 未设置或无效", e);
            throw new RuntimeException("API Key 未设置，请配置 dashscope.api.key", e);
        } catch (Exception e) {
            log.error("批量生成向量嵌入失败", e);
            throw new RuntimeException("批量生成向量嵌入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成查询向量
     *
     * @param query
     * @return
     */
    public List<Float> generateQueryVector(String query) {
        return generateEmbedding(query);
    }

    /**
     * 计算两个向量的余弦相似度
     *
     * @param vector1 向量1
     * @param vector2 向量2
     * @return 余弦相似度 [-1, 1]
     */
    public float calculateSimilarity(List<Float> vector1, List<Float> vector2) {
        if(vector1.size() != vector2.size()) {
            throw new IllegalStateException("向量维度不匹配");
        }

        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;

        for(int i = 0; i < vector1.size(); i++) {
            dotProduct += vector1.get(i) * vector2.get(i);
            norm1 += vector1.get(i) * vector1.get(i);
            norm2 += vector2.get(i) * vector2.get(i);
        }
        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

}
