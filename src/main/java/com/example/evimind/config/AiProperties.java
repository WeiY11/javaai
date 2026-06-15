package com.example.evimind.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "custom.ai")
public class AiProperties {

  private Map<String, ProviderConfig> providers;

  public Map<String, ProviderConfig> getProviders() {
    return providers;
  }

  public void setProviders(Map<String, ProviderConfig> providers) {
    this.providers = providers;
  }

  public static class ProviderConfig {
    private String baseUrl;
    private String apiKey;
    private String model;
    private Double temperature;

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public Double getTemperature() {
      return temperature;
    }

    public void setTemperature(Double temperature) {
      this.temperature = temperature;
    }
  }
}
