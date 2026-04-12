package org.example.voice_assistant.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String index() {
        return "Hello, Spring Boot!";
    }

    @GetMapping("/hello")
    public String hello() {
        return "欢迎使用Spring Boot项目！";
    }
}
