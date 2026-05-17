package com.example.evimind.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
public class DeepSeekExchangeFilterFunction implements ExchangeFilterFunction {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        if (request.url().getHost() != null && request.url().getHost().contains("deepseek") && request.method() == HttpMethod.POST) {
            // Spring WebClient request body is hard to intercept and modify synchronously.
            // But Spring AI provides a way to customize request bodies, or we can use a custom request interceptor.
            // Since modifying WebClient bodies requires intercepting Publisher streams, it's non-trivial.
            // Is there an easier way?
            // Actually, we can use a ClientHttpRequestInterceptor if Spring AI is using RestClient for streams? No, Spring AI uses WebClient for streams.
            // Wait, we can intercept it by wrapping the ClientRequest.
            // Let's implement it carefully.
            // It's actually easier to wrap the BodyInserter, but ClientRequest doesn't expose it easily.
        }
        return next.exchange(request);
    }
}
