package org.example.voice_assistant.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class QwenChatStreamResponse {

    private List<Choice> choices;

    private Usage usage;

    @Data
    public static class Choice {
        private Integer index;
        private String finish_reason;
        private Delta delta;
    }

    @Data
    public static class Delta {
        private String role; // 角色，只在第一个chunk中有
        private String content;
    }

    @Data
    public static class Usage {  // 只在最后一个chunk显示
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
