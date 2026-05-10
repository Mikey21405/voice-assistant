package org.example.voice_assistant.agent;

import java.util.Map;

public interface Tool {

    String getName();

    String getDescription();

    Map<String, Object> getParametersSchema();

    String execute(Map<String, Object> arguments);
}
