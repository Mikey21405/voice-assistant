package org.example.voice_assistant.rag.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 最终存储到向量数据库的切片
public class DocumentChunk {
    /**
     * 存储的文本
     */
    private String content;

    /**
     * 文本的起始位置
     */
    private int startIndex;

    /**
     * 文本的结束位置
     */
    private int endIndex;

    /**
     * 分片序号
     * 默认从0开始
     */
    private int chunkIndex;

    /**
     * 标题
     */
    private String title;

    public DocumentChunk (String content, int startIndex, int endIndex, int chunkIndex) {
        this.content = content;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.chunkIndex = chunkIndex;
    }

    @Override
    public String toString() {
        return "DocumentChunk{" +
                "contentLength='" + (content != null ? content.length() : 0) + '\'' +
                ", startIndex=" + startIndex +
                ", endIndex=" + endIndex +
                ", chunkIndex=" + chunkIndex +
                ", title='" + title + '\'' +
                '}';
    }
}
