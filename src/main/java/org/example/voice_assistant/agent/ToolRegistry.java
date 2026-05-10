package org.example.voice_assistant.agent;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ToolRegistry {

    private final List<Tool> tools;
    private final Map<String, Tool> toolMap = new ConcurrentHashMap<>();

    public ToolRegistry(List<Tool> tools) {
        this.tools = tools;
    }

    @PostConstruct
    public void init() {
        for (Tool tool : tools) {
            toolMap.put(tool.getName(), tool);
            log.info("Tool registered: {}", tool.getName());
        }
        log.info("ToolRegistry initialized with {} tools", toolMap.size());
    }

    public Tool getTool(String name) {
        Tool tool = toolMap.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        return tool;
    }

    public List<Tool> getAllTools() {
        if(toolMap.isEmpty()) {
            log.warn("No tools registered");
        }
        return new ArrayList<>(toolMap.values());
    }

    public boolean hasTools() {
        return !toolMap.isEmpty();
    }
}
