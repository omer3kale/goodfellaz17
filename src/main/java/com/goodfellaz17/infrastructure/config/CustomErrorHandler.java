package com.goodfellaz17.infrastructure.config;

import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Custom Error Handler for WebFlux.
 * 
 * Serves custom 404 page with botzzz773.pro design.
 */
@Component
@Order(-2) // Higher priority than default
public class CustomErrorHandler extends AbstractErrorWebExceptionHandler {

    public CustomErrorHandler(ErrorAttributes errorAttributes,
                              WebProperties webProperties,
                              ApplicationContext applicationContext,
                              ServerCodecConfigurer configurer) {
        super(errorAttributes, webProperties.getResources(), applicationContext);
        this.setMessageWriters(configurer.getWriters());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        Map<String, Object> errorAttributes = getErrorAttributes(request, ErrorAttributeOptions.defaults());
        int statusCode = (int) errorAttributes.getOrDefault("status", 500);
        
        if (statusCode == 404) {
            // Serve custom 404 HTML
            Resource errorPage = new ClassPathResource("static/error/404.html");
            return ServerResponse.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_HTML)
                    .body(BodyInserters.fromResource(errorPage));
        }
        
        // For other errors, return JSON
        return ServerResponse.status(HttpStatus.valueOf(statusCode))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "error", errorAttributes.getOrDefault("error", "Unknown Error"),
                        "message", errorAttributes.getOrDefault("message", "An error occurred"),
                        "status", statusCode,
                        "path", request.path()
                ));
    }
}
