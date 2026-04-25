package org.example.voice_assistant.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.voice_assistant.entity.ConversationHistory;
import org.hibernate.query.sqm.tree.expression.Conversion;

import java.util.List;

@Mapper
public interface ConversationHistoryMapper{

    int insert(ConversationHistory conversionHistory);

    List<ConversationHistory> selectByAssistantId(@Param("assistantId") Long assistantId);

    List<ConversationHistory> selectBySessionId(@Param("sessionId") String sessionId);

    List<ConversationHistory> selectByCallId(@Param("callId") String callId);

    List<ConversationHistory> selectRecentByAssistantId(@Param("assistantId") Long assistantId,
                                                        @Param("limit") int limit);

    int deleteByAssistantId(@Param("assistantId") Long assistantId);

}
