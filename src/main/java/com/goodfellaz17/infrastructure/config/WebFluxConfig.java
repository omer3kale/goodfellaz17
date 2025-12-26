package com.goodfellaz17.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux Configuration.
 * 
 * Configures static resource handling for the reactive web stack.
 */
@Configuration
@EnableWebFlux
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve static files from /static folder
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
        
        // Assets folder
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/");
        
        // CSS folder
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");
        
        // JS folder
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");
    }
}
