package com.example.evimind.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PromptTemplateManager {

  private final Map<String, String> templates = new HashMap<>();
  private final PathMatchingResourcePatternResolver resolver =
      new PathMatchingResourcePatternResolver();

  @PostConstruct
  public void loadTemplates() {
    try {
      Resource[] resources = resolver.getResources("classpath:prompts/*.st");
      for (Resource resource : resources) {
        String filename = resource.getFilename();
        if (filename != null) {
          String name = filename.replace(".st", "");
          String content = resource.getContentAsString(StandardCharsets.UTF_8);
          templates.put(name, content);
          log.info("Loaded prompt template: {}", name);
        }
      }
      log.info("Total prompt templates loaded: {}", templates.size());
    } catch (IOException e) {
      log.error("Failed to load prompt templates", e);
      throw new RuntimeException("Prompt template loading failed", e);
    }
  }

  public String render(String templateName, Map<String, Object> variables) {
    String templateContent = templates.get(templateName);
    if (templateContent == null) {
      throw new IllegalArgumentException("Template not found: " + templateName);
    }
    PromptTemplate promptTemplate = new PromptTemplate(templateContent);
    return promptTemplate.render(variables);
  }

  public String getTemplate(String templateName) {
    return templates.get(templateName);
  }

  public void validateAllTemplates() {
    for (Map.Entry<String, String> entry : templates.entrySet()) {
      try {
        new PromptTemplate(entry.getValue());
      } catch (Exception e) {
        throw new RuntimeException(
            "Invalid template '" + entry.getKey() + "': " + e.getMessage(), e);
      }
    }
    log.info("All prompt templates validated successfully");
  }
}
