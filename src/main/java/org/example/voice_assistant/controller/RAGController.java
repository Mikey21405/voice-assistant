package org.example.voice_assistant.controller;

import io.milvus.client.MilvusServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.client.MilvusClientFactory;
import org.example.voice_assistant.config.FileUploadConfig;
import org.example.voice_assistant.config.RAGConfig;
import org.example.voice_assistant.constant.MilvusConstants;
import org.example.voice_assistant.rag.service.RAGQAService;
import org.example.voice_assistant.rag.service.VectorIndexService;
import org.example.voice_assistant.rag.service.VectorSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rag")
public class RAGController {

    @Autowired
    private VectorIndexService vectorIndexService;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private RAGQAService ragQAService;

    @Autowired
    private MilvusClientFactory milvusClientFactory;

    @Autowired
    private MilvusServiceClient milvusServiceClient;

    @Autowired
    private RAGConfig ragConfig;

    @Autowired
    private FileUploadConfig fileUploadConfig;

    /**
     * 索引指定目录下的文档
     */
    @PostMapping("/index")
    public ResponseEntity<Map<String, Object>> indexDirectory(@RequestParam(required = false) String directoryPath) {
        try {
            log.info("开始索引目录: {}", directoryPath);
            
            VectorIndexService.IndexingResult result = vectorIndexService.indexDirectory(directoryPath);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("directoryPath", result.getDirectoryPath());
            response.put("totalFiles", result.getTotalFiles());
            response.put("successCount", result.getSuccessCount());
            response.put("failCount", result.getFailCount());
            response.put("durationMs", result.getDurationMs());
            response.put("failedFiles", result.getFailedFiles());
            response.put("message", "索引完成");
            
            log.info("目录索引完成: {}", response);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("索引目录失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "索引失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 搜索相似文档
     */
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> searchSimilarDocuments(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        try {
            log.info("开始搜索相似文档，查询: {}, topK: {}", query, topK);
            
            List<VectorSearchService.SearchResult> results = vectorSearchService.searchSimilarDocuments(query, topK);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("query", query);
            response.put("topK", topK);
            response.put("results", results);
            response.put("total", results.size());
            
            log.info("搜索完成，找到 {} 个结果", results.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("搜索相似文档失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "搜索失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * RAG服务健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> healthInfo = new HashMap<>();
        
        try {
            // 测试向量搜索服务
            List<VectorSearchService.SearchResult> testResults = 
                vectorSearchService.searchSimilarDocuments("test", 1);
            healthInfo.put("vectorSearch", "OK");
            healthInfo.put("testResults", testResults.size());
        } catch (Exception e) {
            healthInfo.put("vectorSearch", "ERROR: " + e.getMessage());
        }
        
        healthInfo.put("status", "RUNNING");
        healthInfo.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(healthInfo);
    }

    /**
     * 获取RAG服务信息
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getRagInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("service", "RAG (Retrieval-Augmented Generation)");
        info.put("version", "1.0");
        info.put("features", List.of(
            "文档向量化",
            "向量搜索", 
            "文档索引",
            "相似度匹配",
            "应用级别总开关",
            "运行时配置"
        ));
        info.put("supportedFormats", List.of("txt", "md"));
        info.put("config", Map.of(
            "enabled", ragConfig.isEnabled(),
            "topK", ragConfig.getTopK()
        ));
        
        return ResponseEntity.ok(info);
    }

    /**
     * 删除Milvus collection（表）
     */
    @DeleteMapping("/collection/{collectionName}")
    public ResponseEntity<Map<String, Object>> dropCollection(@PathVariable String collectionName) {
        try {
            log.info("开始删除 collection: {}", collectionName);
            
            boolean success = milvusClientFactory.dropCollection(milvusServiceClient, collectionName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("collectionName", collectionName);
            response.put("message", success ? "删除成功" : "删除失败");
            
            log.info("删除 collection 结果: {}", response);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("删除 collection 失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("collectionName", collectionName);
            errorResponse.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 删除默认的biz collection
     */
    @DeleteMapping("/collection/biz")
    public ResponseEntity<Map<String, Object>> dropBizCollection() {
        try {
            log.info("开始删除默认的biz collection");
            
            boolean success = milvusClientFactory.dropBizCollection(milvusServiceClient);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("collectionName", MilvusConstants.MILVUS_COLLECTION_NAME);
            response.put("message", success ? "删除成功" : "删除失败");
            
            log.info("删除biz collection 结果: {}", response);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("删除biz collection 失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("collectionName", MilvusConstants.MILVUS_COLLECTION_NAME);
            errorResponse.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 重新创建biz collection（删除后重新创建）
     */
    @PostMapping("/collection/biz/recreate")
    public ResponseEntity<Map<String, Object>> recreateBizCollection() {
        try {
            log.info("开始重新创建biz collection");
            
            // 先删除现有的collection
            boolean dropSuccess = milvusClientFactory.dropBizCollection(milvusServiceClient);
            
            if (!dropSuccess) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "删除现有collection失败");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // 重新创建Milvus客户端以重新创建collection
            MilvusServiceClient newClient = milvusClientFactory.createClient();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("collectionName", MilvusConstants.MILVUS_COLLECTION_NAME);
            response.put("message", "重新创建成功");
            
            log.info("重新创建biz collection 结果: {}", response);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("重新创建biz collection 失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "重新创建失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 【完整框架】RAG问答接口
     * 1. ASR接收（通过参数）
     * 2. RAG检索
     * 3. Prompt构建
     * 4. LLM调用
     * 5. 返回结果
     */
    @PostMapping("/qa")
    public ResponseEntity<Map<String, Object>> ragAnswer(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(required = false) String historyPrompt,
            @RequestParam(required = false) String baseSystemPrompt) {
        try {
            log.info("📍【框架步骤 1】ASR接收（通过API参数）");
            log.info("🔍 开始RAG问答，查询: {}, topK: {}", query, topK);

            // ============================================
            // 【框架步骤 2-5】调用RAGQAService完成
            // ============================================
            String answer = ragQAService.ragAnswer(query, topK, historyPrompt, baseSystemPrompt);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("query", query);
            response.put("answer", answer);
            response.put("topK", topK);
            response.put("frameworkSteps", List.of("1.ASR接收", "2.RAG检索", "3.Prompt构建", "4.LLM调用", "5.返回结果"));

            log.info("✅【框架步骤 5】RAG问答完成");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ RAG问答失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "RAG问答失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 获取RAG配置
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getRagConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", ragConfig.isEnabled());
        config.put("topK", ragConfig.getTopK());
        config.put("status", ragConfig.isEnabled() ? "RAG已开启" : "RAG已关闭");
        return ResponseEntity.ok(config);
    }

    /**
     * 开启RAG
     */
    @PostMapping("/enable")
    public ResponseEntity<Map<String, Object>> enableRag() {
        ragConfig.enable();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("enabled", true);
        response.put("message", "RAG功能已开启");
        response.put("topK", ragConfig.getTopK());
        
        log.info("RAG已开启");
        return ResponseEntity.ok(response);
    }

    /**
     * 关闭RAG
     */
    @PostMapping("/disable")
    public ResponseEntity<Map<String, Object>> disableRag() {
        ragConfig.disable();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("enabled", false);
        response.put("message", "RAG功能已关闭");
        response.put("topK", ragConfig.getTopK());
        
        log.info("RAG已关闭");
        return ResponseEntity.ok(response);
    }

    /**
     * 切换RAG开关（开启变关闭，关闭变开启）
     */
    @PostMapping("/toggle")
    public ResponseEntity<Map<String, Object>> toggleRag() {
        Boolean newStatus = ragConfig.toggle();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("enabled", newStatus);
        response.put("message", newStatus ? "RAG功能已开启" : "RAG功能已关闭");
        response.put("topK", ragConfig.getTopK());
        
        log.info("RAG已切换为: {}", newStatus ? "开启" : "关闭");
        return ResponseEntity.ok(response);
    }

    /**
     * 设置RAG topK
     */
    @PostMapping("/topk")
    public ResponseEntity<Map<String, Object>> setTopK(@RequestParam Integer topK) {
        if (topK == null || topK <= 0) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "topK必须大于0");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        ragConfig.setTopK(topK);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("topK", ragConfig.getTopK());
        response.put("message", "topK设置成功");
        
        log.info("topK已设置为: {}", topK);
        return ResponseEntity.ok(response);
    }

    /**
     * 上传文件并索引到向量库
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadAndIndexFile(
            @RequestParam("file") MultipartFile file) {
        try {
            log.info("收到文件上传请求: {}", file.getOriginalFilename());
            
            // 检查文件类型
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !fileUploadConfig.isAllowedExtension(originalFilename)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "不支持的文件类型。支持类型: " + 
                    String.join(", ", fileUploadConfig.getAllowedExtensions()));
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // 确保上传目录存在
            Path uploadDir = Paths.get(fileUploadConfig.getPath()).normalize();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 保存文件
            String safeFilename = sanitizeFilename(originalFilename);
            Path filePath = uploadDir.resolve(safeFilename);
            Files.copy(file.getInputStream(), filePath);

            log.info("文件已保存: {}", filePath.toAbsolutePath());

            // 索引文件
            vectorIndexService.indexSingleFile(filePath.toAbsolutePath().toString());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("fileName", safeFilename);
            response.put("filePath", filePath.toAbsolutePath().toString());
            response.put("message", "文件上传并索引成功");
            
            log.info("文件上传和索引完成: {}", safeFilename);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("文件上传和索引失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "文件上传和索引失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 获取已上传的文件列表
     */
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listFiles() {
        try {
            Path uploadDir = Paths.get(fileUploadConfig.getPath()).normalize();
            File directory = uploadDir.toFile();

            Map<String, Object> response = new HashMap<>();
            
            if (!directory.exists() || !directory.isDirectory()) {
                response.put("success", true);
                response.put("files", new ArrayList<Map<String, Object>>());
                return ResponseEntity.ok(response);
            }

            File[] files = directory.listFiles((dir, name) ->
                    fileUploadConfig.isAllowedExtension(name)
            );

            List<Map<String, Object>> fileList = new ArrayList<>();
            if (files != null) {
                for (File file : files) {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("name", file.getName());
                    fileInfo.put("size", file.length());
                    fileInfo.put("lastModified", file.lastModified());
                    fileInfo.put("path", file.getAbsolutePath());
                    fileList.add(fileInfo);
                }
            }

            response.put("success", true);
            response.put("files", fileList);
            
            log.info("获取文件列表，共 {} 个文件", fileList.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取文件列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "获取文件列表失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/files/{fileName}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable String fileName) {
        try {
            log.info("删除文件: {}", fileName);
            
            Path uploadDir = Paths.get(fileUploadConfig.getPath()).normalize();
            Path filePath = uploadDir.resolve(fileName).normalize();

            // 安全检查
            if (!filePath.startsWith(uploadDir)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "非法的文件路径");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            File file = filePath.toFile();
            if (!file.exists()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "文件不存在");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // 删除文件
            Files.delete(filePath);

            // 同时删除向量库中的对应数据
            // 注意：这里我们需要确保在 VectorIndexService 中有删除单个文件的方法
            // 这里暂时先不删除向量，后面可以扩展
            // 或者在下次索引时，旧数据会被删除

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "文件删除成功");
            
            log.info("文件删除成功: {}", fileName);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("删除文件失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "删除文件失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 安全处理文件名，防止路径遍历攻击
     */
    private String sanitizeFilename(String filename) {
        // 移除路径分隔符
        String safeName = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        
        // 确保不以 . 或 .. 开头
        if (safeName.startsWith(".")) {
            safeName = "_" + safeName;
        }
        
        return safeName;
    }
}