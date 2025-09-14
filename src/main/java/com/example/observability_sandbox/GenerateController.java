package com.example.observability_sandbox;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController

public class GenerateController {
    @PostMapping(path = "/generate", consumes = "application/json", produces = "application/json")
    public Map<String, Object> generate(@RequestBody Map<String, Object> body) throws InterruptedException {
        // Simulate some processing time
        Thread.sleep(150);
        String prompt = String.valueOf(body.getOrDefault("prompt", "hello"));
        return Map.of("response", "You said: " + prompt, "latencyMs", 150);
    }
}