package org.example.voice_assistant.rag.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.geometry.partitioning.Embedding;
import org.example.voice_assistant.constant.MilvusConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class VectorSearchService {

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private EmbeddingService embeddingService;

    public List<SearchResult> searchSimilarDocuments(String query, int topK) {
        try {
            log.info("开始搜索相似文档，查询：{},topK：{}",query,topK);

            // 1. 将查询文本向量化
            List<Float> queryVector = embeddingService.generateQueryVector(query);
            log.info("查询向量：{}",queryVector);

            // 2. 构建搜索参数
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withVectorFieldName("vector")
                    .withVectors(Collections.singletonList(queryVector))
                    .withTopK(topK)
                    .withMetricType(MetricType.L2)
                    .withOutFields(List.of("id","content","metadata"))
                    .withParams("{\"nprobe\": 10}")
                    .build();

            // 3. 执行搜索
            R<SearchResults> searchResponse = milvusClient.search(searchParam);

            if(searchResponse.getStatus() != 0) {
                throw new RuntimeException("向量搜索失败：" + searchResponse.getMessage());
            }

            // 4. 解析搜索结果
            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
            List<SearchResult> results = new ArrayList<>();

            for(int i = 0;i<wrapper.getRowRecords(0).size();i++) {
                SearchResult result = new SearchResult();
                result.setId((String) wrapper.getIDScore(0).get(i).get("id"));
                result.setContent((String) wrapper.getFieldData("content", 0).get(i));
                result.setScore(wrapper.getIDScore(0).get(i).getScore());

                // 解析 metadata
                Object metadataObj = wrapper.getFieldData("metadata", 0).get(i);
                if (metadataObj != null) {
                    result.setMetadata(metadataObj.toString());
                }
                results.add(result);

            }
            log.info("搜索完成, 找到 {} 个相似文档", results.size());
            return results;
        } catch (Exception e) {
            log.error("搜索相似文档失败", e);
            throw new RuntimeException("搜索失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用多个查询变体进行搜索，去重后返回结果
     */
    public List<SearchResult> searchWithMultipleQueries(List<String> queries, int topK) {
        try {
            log.info("开始使用多个查询变体搜索，查询数量：{}，topK：{}", queries.size(), topK);

            List<SearchResult> allResults = new ArrayList<>();

            // 对每个查询进行搜索
            for (String query : queries) {
                try {
                    List<SearchResult> results = searchSimilarDocuments(query, topK);
                    allResults.addAll(results);
                } catch (Exception e) {
                    log.warn("查询变体搜索失败：{}", query, e);
                }
            }

            // 去重（按id去重）
            List<SearchResult> deduplicatedResults = deduplicateResults(allResults);

            // 按分数排序
            deduplicatedResults.sort((a, b) -> Float.compare(a.getScore(), b.getScore()));

            // 只保留topK个结果
            if (deduplicatedResults.size() > topK) {
                deduplicatedResults = deduplicatedResults.subList(0, topK);
            }

            log.info("多查询搜索完成，找到 {} 个唯一文档", deduplicatedResults.size());
            return deduplicatedResults;
        } catch (Exception e) {
            log.error("多查询搜索失败", e);
            throw new RuntimeException("多查询搜索失败: " + e.getMessage(), e);
        }
    }

    /**
     * 去重搜索结果
     */
    private List<SearchResult> deduplicateResults(List<SearchResult> results) {
        List<SearchResult> uniqueResults = new ArrayList<>();
        List<String> seenIds = new ArrayList<>();

        for (SearchResult result : results) {
            if (!seenIds.contains(result.getId())) {
                seenIds.add(result.getId());
                uniqueResults.add(result);
            }
        }

        return uniqueResults;
    }

    /**
     * 搜索结果类
     */
    @Setter
    @Getter
    public static class SearchResult {
        private String id;
        private String content;
        private float score;
        private String metadata;

    }
}
