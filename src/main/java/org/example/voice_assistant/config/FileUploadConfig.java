package org.example.voice_assistant.config;

import lombok.Data;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadConfig {

    private String path;

    private List<String> allowedExtensions;

    public boolean isAllowedExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        return allowedExtensions.stream().anyMatch(allowed -> lowerName.endsWith("." + allowed));
    }
}
