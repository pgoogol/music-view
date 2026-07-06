package com.pgoogol.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: /swagger-ui.html (D13); definicja: /v3/api-docs.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI musicViewOpenApi() {

        return new OpenAPI().info(new Info()
            .title("music-view API")
            .description("Osobiste narzędzie DJ-a: katalog, biblioteka, import CSV, wzbogacanie.")
            .version("v1"));
    }
}
