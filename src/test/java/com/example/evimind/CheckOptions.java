package com.example.evimind;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class CheckOptions {
    @Test
    public void test() throws ClassNotFoundException {
        Class<?> clazz = Class.forName("org.springframework.ai.openai.api.OpenAiApi$ChatCompletionRequest");
        for (Method m : clazz.getMethods()) {
            System.out.println("Method: " + m.getName());
        }
        for (Field f : clazz.getDeclaredFields()) {
            System.out.println("Field: " + f.getName());
        }
    }
}
