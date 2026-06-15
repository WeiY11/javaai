package com.example.evimind.config;

import lombok.Data;

public class DeepSeekContextHolder {

  private static final ThreadLocal<DeepSeekContext> CONTEXT = new ThreadLocal<>();

  @Data
  public static class DeepSeekContext {
    private Boolean thinking;
    private String reasoningEffort;

    public DeepSeekContext(Boolean thinking, String reasoningEffort) {
      this.thinking = thinking;
      this.reasoningEffort = reasoningEffort;
    }
  }

  public static void setContext(Boolean thinking, String reasoningEffort) {
    CONTEXT.set(new DeepSeekContext(thinking, reasoningEffort));
  }

  public static DeepSeekContext getContext() {
    return CONTEXT.get();
  }

  public static void clearContext() {
    CONTEXT.remove();
  }
}
