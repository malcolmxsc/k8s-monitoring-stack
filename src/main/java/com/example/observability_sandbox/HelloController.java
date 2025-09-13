package com.example.observability_sandbox;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@RestController
public class HelloController {
    private final Counter helloCounter;

    public HelloController(MeterRegistry registry) {
        this.helloCounter = Counter.builder("hello_requests_total")
                .description("Total /hello requests")
                .tag("service", "observability-sandbox")
                .register(registry);
    }

    @GetMapping("/hello")
    public String hello(@RequestParam(defaultValue = "Malcolm") String name) {
        helloCounter.increment();
        return "Hello, " + name + "!";
    }
}
