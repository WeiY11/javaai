package com.example.javaai;

import com.example.javaai.config.AiProperties;
import com.example.javaai.config.AnalysisProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;

@SpringBootApplication(exclude = {OpenAiAutoConfiguration.class})
@EnableConfigurationProperties({AiProperties.class, AnalysisProperties.class})
public class JavaaiApplication {

	public static void main(String[] args) {
		SpringApplication.run(JavaaiApplication.class, args);
	}

}
