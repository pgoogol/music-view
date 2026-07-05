package com.pgoogol.enrichment.llm;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.pgoogol.common.ExternalServiceException;
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
import static org.assertj.core.api.Assertions.catchThrowable;

@WireMockTest
class OpenAiCompatibleLlmClientTest {

    private static final String RESPONSE_JSON = """
        {
          "choices": [{"message": {"role": "assistant", "content": "[{\\"ok\\": true}]"}}],
          "usage": {"prompt_tokens": 700, "completion_tokens": 550}
        }
        """;

    @Test
    void complete_whenProviderResponds_returnsContentAndUsage(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(post(urlPathEqualTo("/v1/chat/completions")).willReturn(okJson(RESPONSE_JSON)));
        LlmClient client = new OpenAiCompatibleLlmClient(WireMockRestClients.builder(), properties(wireMock));

        // when
        LlmCompletion completion = client.complete(new LlmPrompt("system-prompt", "user-prompt"));

        // then
        assertThat(completion).isEqualTo(new LlmCompletion("[{\"ok\": true}]", 700, 550));
        verify(postRequestedFor(urlPathEqualTo("/v1/chat/completions"))
            .withHeader("Authorization", equalTo("Bearer test-key"))
            .withRequestBody(equalToJson("""
                {
                  "model": "test-model",
                  "messages": [
                    {"role": "system", "content": "system-prompt"},
                    {"role": "user", "content": "user-prompt"}
                  ],
                  "temperature": 0.2,
                  "max_tokens": 2048
                }
                """)));
    }

    @Test
    void complete_whenNoChoices_throwsExternalServiceException(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(post(urlPathEqualTo("/v1/chat/completions"))
            .willReturn(okJson("{\"choices\": [], \"usage\": null}")));
        LlmClient client = new OpenAiCompatibleLlmClient(WireMockRestClients.builder(), properties(wireMock));

        // when
        Throwable thrown = catchThrowable(() -> client.complete(new LlmPrompt("s", "u")));

        // then
        assertThat(thrown).isInstanceOf(ExternalServiceException.class)
            .hasMessageContaining("pustą odpowiedź");
    }

    private LlmProperties properties(WireMockRuntimeInfo wireMock) {
        return new LlmProperties("openai", wireMock.getHttpBaseUrl(), "test-key", "test-model",
            "v1", 5, 100, 2048, 0.2);
    }
}
