package com.pgoogol.enrichment.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pgoogol.common.ExternalServiceException;
import com.pgoogol.common.RateLimitedException;
import com.pgoogol.common.ratelimit.ApiCallGuard;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Provider Anthropic ({@code POST /v1/messages}).
 * Bean tworzony w {@link LlmClientConfig}, nie przez skanowanie.
 */
public class AnthropicLlmClient implements LlmClient {

    private static final String API_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final LlmProperties properties;
    private final ApiCallGuard guard;

    public AnthropicLlmClient(RestClient.Builder restClientBuilder, LlmProperties properties) {

        this.restClient = restClientBuilder.clone()
            .baseUrl(properties.resolvedBaseUrl())
            .defaultHeader("x-api-key", properties.apiKey())
            .defaultHeader("anthropic-version", API_VERSION)
            .build();
        this.properties = properties;
        this.guard = ApiCallGuard.of("llm-anthropic", properties.requestsPerSecond());
    }

    @Override
    public LlmCompletion complete(LlmPrompt prompt) {

        Objects.requireNonNull(prompt, "prompt");
        MessagesRequest request = new MessagesRequest(
            properties.model(),
            prompt.system(),
            List.of(new Message("user", prompt.user())),
            properties.maxTokens(),
            properties.temperature());
        MessagesResponse response = guard.execute(() -> {
            try {
                return restClient.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MessagesResponse.class);
            } catch (HttpClientErrorException.TooManyRequests ex) {
                throw new RateLimitedException("LLM_RATE_LIMITED",
                    "Provider LLM ograniczył liczbę zapytań", retryAfter(ex));
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                throw new ExternalServiceException("LLM_UNAVAILABLE", "Provider LLM niedostępny", ex);
            }
        });
        return toCompletion(response);
    }

    private LlmCompletion toCompletion(MessagesResponse response) {

        String content = Optional.ofNullable(response)
            .map(MessagesResponse::content)
            .orElse(List.of())
            .stream()
            .filter(block -> Objects.equals(block.type(), "text"))
            .findFirst()
            .map(ContentBlock::text)
            .orElseThrow(() -> new ExternalServiceException("LLM_RESPONSE_EMPTY",
                "Provider LLM zwrócił pustą odpowiedź"));
        Usage usage = Objects.requireNonNullElse(response.usage(), new Usage(0, 0));
        return new LlmCompletion(content, usage.inputTokens(), usage.outputTokens());
    }

    private Duration retryAfter(HttpClientErrorException.TooManyRequests ex) {

        return Optional.ofNullable(ex.getResponseHeaders())
            .map(headers -> headers.getFirst(HttpHeaders.RETRY_AFTER))
            .map(Long::parseLong)
            .map(Duration::ofSeconds)
            .orElse(null);
    }

    private record Message(String role, String content) {

    }

    private record MessagesRequest(String model,
                                   String system,
                                   List<Message> messages,
                                   @JsonProperty("max_tokens") int maxTokens,
                                   double temperature) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MessagesResponse(List<ContentBlock> content, Usage usage) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContentBlock(String type, String text) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(@JsonProperty("input_tokens") long inputTokens,
                         @JsonProperty("output_tokens") long outputTokens) {

    }
}
