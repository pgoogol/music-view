package com.pgoogol.enrichment.llm;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import com.pgoogol.WireMockRestClients;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class AnthropicLlmClientTest {

    private static final String RESPONSE_JSON = """
        {
          "content": [{"type": "text", "text": "[{\\"ok\\": true}]"}],
          "usage": {"input_tokens": 800, "output_tokens": 450}
        }
        """;

    @Test
    void complete_whenProviderResponds_returnsContentAndUsage(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(okJson(RESPONSE_JSON)));
        LlmProperties properties = new LlmProperties("anthropic", wireMock.getHttpBaseUrl(),
            "test-key", "test-model", "v1", 5, 100, 1024, 0.1, 500, null);
        LlmClient client = new AnthropicLlmClient(WireMockRestClients.builder(), properties);

        // when
        LlmCompletion completion = client.complete(new LlmPrompt("system-prompt", "user-prompt"));

        // then
        assertThat(completion).isEqualTo(new LlmCompletion("[{\"ok\": true}]", 800, 450));
        verify(postRequestedFor(urlPathEqualTo("/v1/messages"))
            .withHeader("x-api-key", equalTo("test-key"))
            .withHeader("anthropic-version", equalTo("2023-06-01"))
            .withRequestBody(equalToJson("""
                {
                  "model": "test-model",
                  "system": "system-prompt",
                  "messages": [{"role": "user", "content": "user-prompt"}],
                  "max_tokens": 1024,
                  "temperature": 0.1
                }
                """)));
    }
}
