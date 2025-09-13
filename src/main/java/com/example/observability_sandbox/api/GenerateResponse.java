package com.example.observability_sandbox.api;

public class GenerateResponse {

    private String completion;
    private int requestTokens;
    private int responseTokens;
    private boolean cacheHit;


    public GenerateResponse(String completion, int requestTokens, int responseTokens, boolean cacheHit) {
        this.completion = completion;
        this.requestTokens = requestTokens;
        this.responseTokens = responseTokens;
        this.cacheHit = cacheHit;
    }

    public String getCompletion() {
        return completion;
    }

    public int getRequestTokens() {
        return requestTokens;
    }

    public int getResponseTokens() {
        return responseTokens;
    }

    public boolean isCacheHit() {
        return cacheHit;
    } 
}

