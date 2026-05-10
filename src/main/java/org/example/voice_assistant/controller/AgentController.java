package org.example.voice_assistant.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice_assistant.agent.Agent;
import org.example.voice_assistant.agent.Tool;
import org.example.voice_assistant.agent.ToolRegistry;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final Agent agent;
    private final ToolRegistry toolRegistry;

    @GetMapping("/tools")
    public Map<String, Object> getTools() {
        List<Tool> tools = toolRegistry.getAllTools();
        List<Map<String, Object>> toolList = new ArrayList<>();
        for (Tool tool : tools) {
            Map<String, Object> toolInfo = new LinkedHashMap<>();
            toolInfo.put("name", tool.getName());
            toolInfo.put("description", tool.getDescription());
            toolInfo.put("parameters", tool.getParametersSchema());
            toolList.add(toolInfo);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("count", toolList.size());
        result.put("tools", toolList);
        return result;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        String systemPrompt = request.getOrDefault("systemPrompt", null);

        if (prompt == null || prompt.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "prompt不能为空");
            return error;
        }

        long startTime = System.currentTimeMillis();
        String response = agent.run(prompt, systemPrompt);
        long cost = System.currentTimeMillis() - startTime;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("response", response);
        result.put("costMs", cost);
        return result;
    }
}
