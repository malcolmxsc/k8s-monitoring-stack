package com.example.observability_sandbox;

import org.springframework.boot.SpringApplication;
import java.util.concurrent.Executor;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.example.observability_sandbox.evaluation.EvaluationProperties;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(EvaluationProperties.class)
public class ObservabilitySandboxApplication {

	public static void main(String[] args) {
		SpringApplication.run(ObservabilitySandboxApplication.class, args);
	}

	@Bean(name = "evaluationExecutor")
	public Executor evaluationExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("evaluation-");
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(1);
		executor.initialize();
		return executor;
	}

}
