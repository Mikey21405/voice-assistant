package org.example.voice_assistant.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.entity.Assistant;
import org.example.voice_assistant.service.AssistantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Assistant assistant) {
        try {
            log.info("创建助手：name={}", assistant.getName());
            Assistant created = assistantService.createAssistant(assistant);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", created);
            response.put("message", "助手创建成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建助手失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "创建失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> update(@RequestBody Assistant assistant) {
        try {
            log.info("更新助手：id={}, name={}", assistant.getId(), assistant.getName());
            Assistant updated = assistantService.updateAssistant(assistant);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", updated);
            response.put("message", "助手更新成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("更新助手失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "更新失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        try {
            log.info("删除助手：id={}", id);
            assistantService.deleteAssistant(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "助手删除成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除助手失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        try {
            log.info("查询助手：id={}", id);
            Assistant assistant = assistantService.getAssistantById(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", assistant);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("查询助手失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<Map<String, Object>> getByName(@PathVariable String name) {
        try {
            log.info("查询助手：name={}", name);
            Assistant assistant = assistantService.getAssistantByName(name);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", assistant);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("查询助手失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll() {
        try {
            log.info("查询所有助手");
            List<Assistant> assistants = assistantService.getAllAssistants();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", assistants);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("查询助手列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}
