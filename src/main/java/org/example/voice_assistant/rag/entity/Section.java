package org.example.voice_assistant.rag.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 按MarkDown标题切分的章节
public class Section {
    private String title;
    private String content;
    private int startIndex;
}
