package org.example.voice_assistant.client;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.*;
import io.milvus.param.collection.CollectionSchemaParam;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.config.MilvusProperties;
import org.example.voice_assistant.constant.MilvusConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Milvus 客户端工厂类
 * 负责创建和初始化 Milvus 客户端连接
 */
@Slf4j
@Component
public class MilvusClientFactory {

    @Autowired
    private MilvusProperties milvusProperties;

    /**
     * 创建并初始化 Milvus 客户端
     * @return
     */
    public MilvusServiceClient createClient() {
        MilvusServiceClient client = null;
        try {
            // 1. 连接到Milvus
            log.info("正在连接到 Milvus：{}:{}",milvusProperties.getHost(),milvusProperties.getPort());
            client = connectToMilvus();
            log.info("成功连接到 Milvus");

            // 2. 检查并创建 biz collection
            if (!collectionExists(client, MilvusConstants.MILVUS_COLLECTION_NAME)) {
                log.info("正在创建 collection：{}", MilvusConstants.MILVUS_COLLECTION_NAME);
                createBizCollection(client);
                log.info("成功创建 collection：{}", MilvusConstants.MILVUS_COLLECTION_NAME);

                // 创建索引
                createIndexes(client);
                log.info("成功创建索引");
            }else {
                log.info("collection {} 已经存在", MilvusConstants.MILVUS_COLLECTION_NAME);
            }

            return client;
        } catch (Exception e) {
            log.error("创建 Milvus 客户端失败", e);
            if (client != null) {
                client.close();
            }
            throw new RuntimeException("创建 Milvus 客户端失败: " + e.getMessage(), e);
        }

    }

    /**
     * 连接到 Milvus
     */
    private MilvusServiceClient connectToMilvus() {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(milvusProperties.getHost())
                .withPort(milvusProperties.getPort())
                .withConnectTimeout(milvusProperties.getTimeout(), TimeUnit.MILLISECONDS);

        // 如果配置了用户名和密码
        if (milvusProperties.getUsername() != null && !milvusProperties.getUsername().isEmpty()) {
            builder.withAuthorization(milvusProperties.getUsername(), milvusProperties.getPassword());
        }

        return new MilvusServiceClient(builder.build());
    }

    /**
     * 检查 collection 是否存在
     */
    private boolean collectionExists(MilvusServiceClient client, String collectionName) {
        R<Boolean> response = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());

        if (response.getStatus() != 0) {
            throw new RuntimeException("检查 collection 失败: " + response.getMessage());
        }

        return response.getData();
    }

    /**
     * 创建向量集合，本质就是"创建数据库表"
     *
     * @param client
     */
    private void createBizCollection(MilvusServiceClient client) {
        // 定义字段
        FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.VarChar)
                .withPrimaryKey(true)
                .withMaxLength(MilvusConstants.ID_MAX_LENGTH)
                .build();

        FieldType vectorField = FieldType.newBuilder()
                .withName("vector")
                .withDataType(DataType.FloatVector)  // 改为 FloatVector
                .withDimension(MilvusConstants.VECTOR_DIM)
                .build();

        FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(MilvusConstants.CONTENT_MAX_LENGTH)
                .build();

        FieldType metadataField = FieldType.newBuilder()
                .withName("metadata")
                .withDataType(DataType.JSON)
                .build();

        // 创建 collection schema,表结构
        CollectionSchemaParam schema = CollectionSchemaParam.newBuilder()
                .withEnableDynamicField(false)
                .addFieldType(idField)
                .addFieldType(vectorField)
                .addFieldType(contentField)
                .addFieldType(metadataField)
                .build();

        // 创建 collection，建表
        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withDescription("Business knowledge collection")
                .withSchema(schema)
                .withShardsNum(MilvusConstants.DEFAULT_SHARD_NUMBER)
                .build();

        R<RpcStatus> response = client.createCollection(createParam);
        if(response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("创建 collection 失败: " + response.getMessage());
        }
    }

    /**
     * 为 collection 创建索引
     */
    private void createIndexes(MilvusServiceClient client) {
        // 为 vector 字段创建索引（FloatVector 使用 IVF_FLAT 和 L2 距离）
        CreateIndexParam vectorIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withFieldName("vector")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)  // L2 距离（欧氏距离）
                .withExtraParam("{\"nlist\":128}")
                .withSyncMode(Boolean.FALSE)
                .build();

        R<RpcStatus> response = client.createIndex(vectorIndexParam);
        if (response.getStatus() != 0) {
            throw new RuntimeException("创建 vector 索引失败: " + response.getMessage());
        }

        log.info("成功为 vector 字段创建索引");
    }

    /**
     * 删除指定的 collection（表）
     * @param client Milvus客户端
     * @param collectionName 要删除的collection名称
     * @return 删除结果
     */
    public boolean dropCollection(MilvusServiceClient client, String collectionName) {
        try {
            log.info("开始删除 collection: {}", collectionName);
            
            // 检查collection是否存在
            if (!collectionExists(client, collectionName)) {
                log.warn("collection {} 不存在，无需删除", collectionName);
                return true;
            }
            
            // 删除collection
            R<RpcStatus> response = client.dropCollection(
                io.milvus.param.collection.DropCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build()
            );
            
            if (response.getStatus() != 0) {
                log.error("删除 collection {} 失败: {}", collectionName, response.getMessage());
                return false;
            }
            
            log.info("成功删除 collection: {}", collectionName);
            return true;
            
        } catch (Exception e) {
            log.error("删除 collection {} 时发生异常", collectionName, e);
            return false;
        }
    }

    /**
     * 删除默认的 biz collection
     * @param client Milvus客户端
     * @return 删除结果
     */
    public boolean dropBizCollection(MilvusServiceClient client) {
        return dropCollection(client, MilvusConstants.MILVUS_COLLECTION_NAME);
    }

}
