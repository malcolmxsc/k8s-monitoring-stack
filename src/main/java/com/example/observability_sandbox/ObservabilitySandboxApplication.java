package com.example.observability_sandbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.example.observability_sandbox.evaluation.EvaluationProperties;

@SpringBootApplication
@EnableConfigurationProperties(EvaluationProperties.class)
public class ObservabilitySandboxApplication {

	public static void main(String[] args) {
		SpringApplication.run(ObservabilitySandboxApplication.class, args);
	}

}
