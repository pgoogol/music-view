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
 * Provider zgodny z API OpenAI ({@code POST /v1/chat/completions}) —
 * pokrywa OpenAI, OpenRouter, Groq, Mistral i serwery lokalne.
 * Bean tworzony w {@link LlmClientConfig}, nie przez skanowanie.
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final RestClient restClient;
    private final LlmProperties properties;
    private final ApiCallGuard guard;

    public OpenAiCompatibleLlmClient(RestClient.Builder restClientBuilder, LlmProperties properties) {

        this.restClient = restClientBuilder.clone()
            .baseUrl(properties.resolvedBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
            .build();
        this.properties = properties;
        this.guard = ApiCallGuard.of("llm-openai", properties.requestsPerSecond());
    }

    @Override
    public LlmCompletion complete(LlmPrompt prompt) {

        Objects.requireNonNull(prompt, "prompt");
        ChatRequest request = new ChatRequest(
            properties.model(),
            List.of(new ChatMessage("system", prompt.system()), new ChatMessage("user", prompt.user())),
            properties.temperature(),
            prompt.maxTokensOr(properties.maxTokens()));
        ChatResponse response = guard.execute(() -> {
            try {
                return restClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
            } catch (HttpClientErrorException.TooManyRequests ex) {
                throw new RateLimitedException("LLM_RATE_LIMITED",
                    "Provider LLM ograniczył liczbę zapytań", retryAfter(ex));
            } catch (HttpClientErrorException ex) {
                throw LlmErrors.rejected(ex);
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                throw new ExternalServiceException("LLM_UNAVAILABLE", "Provider LLM niedostępny", ex);
            }
        });
        return toCompletion(response);
    }

    private LlmCompletion toCompletion(ChatResponse response) {

        String content = Optional.ofNullable(response)
            .map(ChatResponse::choices)
            .orElse(List.of())
            .stream()
            .findFirst()
            .map(Choice::message)
            .map(ChatMessage::content)
            .orElseThrow(() -> new ExternalServiceException("LLM_RESPONSE_EMPTY",
                "Provider LLM zwrócił pustą odpowiedź"));
        Usage usage = Objects.requireNonNullElse(response.usage(), new Usage(0, 0));
        return new LlmCompletion(content, usage.promptTokens(), usage.completionTokens());
    }

    private Duration retryAfter(HttpClientErrorException.TooManyRequests ex) {

        return Optional.ofNullable(ex.getResponseHeaders())
            .map(headers -> headers.getFirst(HttpHeaders.RETRY_AFTER))
            .map(Long::parseLong)
            .map(Duration::ofSeconds)
            .orElse(null);
    }

    private record ChatMessage(String role, String content) {

    }

    private record ChatRequest(String model,
                               List<ChatMessage> messages,
                               double temperature,
                               @JsonProperty("max_tokens") int maxTokens) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatResponse(List<Choice> choices, Usage usage) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(ChatMessage message) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(@JsonProperty("prompt_tokens") long promptTokens,
                         @JsonProperty("completion_tokens") long completionTokens) {

    }
}
