package org.example.voice_assistant.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.entity.Assistant;
import org.example.voice_assistant.service.AssistantService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    @PostMapping
    public Assistant create(@RequestBody Assistant assistant) {
        log.info("创建助手：name={}", assistant.getName());
        return assistantService.createAssistant(assistant);
    }

    @PutMapping
    public Assistant update(@RequestBody Assistant assistant) {
        log.info("更新助手：id={}, name={}", assistant.getId(), assistant.getName());
        return assistantService.updateAssistant(assistant);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        log.info("删除助手：id={}", id);
        assistantService.deleteAssistant(id);
    }

    @GetMapping("/{id}")
    public Assistant getById(@PathVariable Long id) {
        log.info("查询助手：id={}", id);
        return assistantService.getAssistantById(id);
    }

    @GetMapping("/name/{name}")
    public Assistant getByName(@PathVariable String name) {
        log.info("查询助手：name={}", name);
        return assistantService.getAssistantByName(name);
    }

    @GetMapping
    public List<Assistant> getAll() {
        log.info("查询所有助手");
        return assistantService.getAllAssistants();
    }
}
