package com.example.evimind;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

public class CheckOptions {
  @Test
  public void test() throws ClassNotFoundException {
    Class<?> clazz =
        Class.forName("org.springframework.ai.openai.api.OpenAiApi$ChatCompletionRequest");
    for (Method m : clazz.getMethods()) {
      System.out.println("Method: " + m.getName());
    }
    for (Field f : clazz.getDeclaredFields()) {
      System.out.println("Field: " + f.getName());
    }
  }
}
