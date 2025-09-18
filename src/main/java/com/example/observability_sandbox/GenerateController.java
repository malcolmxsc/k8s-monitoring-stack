package com.example.observability_sandbox;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GenerateController {

  private final LlmService llm;

  public GenerateController(LlmService llm) {
    this.llm = llm; // Spring injects the service bean
  }

  @PostMapping(path = "/generate", consumes = "application/json", produces = "application/json")
  public GenerateResponse generate(@RequestBody(required = false) PromptRequest req) {
    // If body is missing or prompt is blank, default to "hello"
    String prompt = (req != null && req.prompt() != null && !req.prompt().isBlank())
        ? req.prompt()
        : "hello";

    // Delegate to the service — this produces spans, tags, and metrics
    return llm.generate(prompt);
  }
}

// Simple request wrapper for JSON body
record PromptRequest(String prompt) {}
