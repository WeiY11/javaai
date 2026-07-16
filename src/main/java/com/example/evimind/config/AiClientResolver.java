package com.example.evimind.config;

import java.util.Map;
import java.util.Optional;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.util.StringUtils;

/** Resolves configured AI clients without silently changing an explicit provider choice. */
public final class AiClientResolver {

  private static final String PREFERRED_PROVIDER = "deepseek";

  private AiClientResolver() {}

  public static ChatClient resolve(Map<String, ChatClient> chatClients, String requestedProvider) {
    if (chatClients == null || chatClients.isEmpty()) {
      return null;
    }
    if (StringUtils.hasText(requestedProvider)) {
      return chatClients.get(requestedProvider.trim());
    }
    return resolveProviderName(chatClients, null).map(chatClients::get).orElse(null);
  }

  public static Optional<String> resolveProviderName(
      Map<String, ChatClient> chatClients, String requestedProvider) {
    if (chatClients == null || chatClients.isEmpty()) {
      return Optional.empty();
    }
    if (StringUtils.hasText(requestedProvider)) {
      String provider = requestedProvider.trim();
      return chatClients.containsKey(provider) && chatClients.get(provider) != null
          ? Optional.of(provider)
          : Optional.empty();
    }
    if (chatClients.get(PREFERRED_PROVIDER) != null) {
      return Optional.of(PREFERRED_PROVIDER);
    }
    return chatClients.entrySet().stream()
        .filter(entry -> StringUtils.hasText(entry.getKey()) && entry.getValue() != null)
        .map(Map.Entry::getKey)
        .sorted()
        .findFirst();
  }
}
