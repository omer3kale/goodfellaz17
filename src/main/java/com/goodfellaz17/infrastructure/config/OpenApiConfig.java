package com.goodfellaz17.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger Configuration.
 * 
 * Provides interactive API documentation at /docs
 * Auto-generates from controller annotations.
 */
@Configuration
public class OpenApiConfig {

    @Value("${app.base-url:https://goodfellaz17.onrender.com}")
    private String baseUrl;

    @Bean
    public OpenAPI goodfellazOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("GOODFELLAZ17 API")
                .description("""
                    ## SMM Panel Provider API
                    
                    Full-featured API for Spotify play boosting services.
                    
                    ### Quick Start
                    1. Create an API key: `POST /api/key/create?email=you@email.com`
                    2. Add funds: `POST /api/checkout/create-session` or `/api/crypto/create-charge`
                    3. Place orders: Use dashboard at `/customer?key=YOUR_KEY`
                    4. Track status: `GET /api/v2/status?key=YOUR_KEY`
                    
                    ### Authentication
                    All API calls require an API key passed as `?key=` query parameter.
                    
                    ### Rate Limits
                    - 100 requests/minute per API key
                    - Webhook endpoints are not rate limited
                    
                    ### Support
                    Contact: support@goodfellaz17.com
                    """)
                .version("2.0.0")
                .contact(new Contact()
                    .name("GOODFELLAZ17 Support")
                    .email("support@goodfellaz17.com")
                    .url("https://goodfellaz17.onrender.com"))
                .license(new License()
                    .name("Proprietary")
                    .url("https://goodfellaz17.onrender.com/terms")))
            .servers(List.of(
                new Server()
                    .url(baseUrl)
                    .description("Production Server"),
                new Server()
                    .url("http://localhost:8080")
                    .description("Local Development")))
            .components(new Components()
                .addSecuritySchemes("apiKey", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.QUERY)
                    .name("key")
                    .description("API key for authentication (botzzz_xxx)")));
    }
}
