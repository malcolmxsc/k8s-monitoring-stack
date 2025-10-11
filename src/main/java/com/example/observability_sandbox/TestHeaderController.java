package com.example.observability_sandbox;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class TestHeaderController {
    
    @GetMapping("/test-headers")
    public ResponseEntity<Map<String, String>> testHeaders(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Region", required = false) String region,
            @RequestHeader(value = "X-Model", required = false) String model
    ) {
        Map<String, String> result = new HashMap<>();
        result.put("X-User-Id", userId != null ? userId : "null");
        result.put("X-Region", region != null ? region : "null");
        result.put("X-Model", model != null ? model : "null");
        return ResponseEntity.ok(result);
    }
}
