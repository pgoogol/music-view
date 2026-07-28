package com.pgoogol;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Builder RestClient dla testów WireMock: JDK HttpClient przypięty do HTTP/1.1.
 * Domyślny klient próbuje upgrade'u h2c na czystym HTTP, a Jetty WireMocka
 * resetuje wtedy strumień przy POST z JSON-em (RST_STREAM); po HTTPS/ALPN
 * (produkcja) problem nie występuje.
 */
public final class WireMockRestClients {

    private WireMockRestClients() {

    }

    public static RestClient.Builder builder() {

        HttpClient http11Client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        return RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(http11Client));
    }
}
