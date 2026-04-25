package org.example.voice_assistant.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.voice_assistant.entity.Assistant;

import java.util.List;

@Mapper
public interface AssistantMapper {

    int insert(Assistant assistant);
    int update(Assistant assistant);
    int delete(@Param("id") Long id);
    Assistant selectById(Long id);
    Assistant selectByName(String name);
    List<Assistant> selectAll();
}
