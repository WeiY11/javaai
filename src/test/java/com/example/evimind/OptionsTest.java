package com.example.evimind;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;

public class OptionsTest {
  @Test
  public void test() {
    for (Method m : OpenAiChatOptions.Builder.class.getMethods()) {
      System.out.println("Method: " + m.getName());
    }
  }
}
