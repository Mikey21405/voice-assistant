package org.example.voice_assistant.rag.service;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class FileReaderService {
        public String readFile(String filePath) throws Exception{
            Path path = Paths.get(filePath);  // 创建文件路径对象
            return Files.readString(path);   // 读取文件内容并返回
        }
}
