package org.example.voice_assistant.controller;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.collection.HasCollectionParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/milvus")
public class MilvusTestController {


    @Autowired
    private MilvusServiceClient milvusServiceClient;

    @GetMapping("/status")
    public String getStatus() {
        try {
            // 测试连接是否正常
            boolean isConnected = milvusServiceClient.hasCollection(
                    HasCollectionParam.newBuilder().withCollectionName("test_collection").build()
            ).getData();

            return "Milvus 连接正常，服务运行中";
        } catch (Exception e) {
            return "Milvus 连接异常: " + e.getMessage();
        }
    }
}
