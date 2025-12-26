package com.goodfellaz17.presentation.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Dashboard Page Controller.
 * 
 * Serves HTML pages for customer dashboard and admin panel.
 * Uses WebFlux compatible resource serving.
 */
@RestController
public class DashboardPageController {

    /**
     * Root → Checkout page.
     */
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<Resource> home() {
        return Mono.just(new ClassPathResource("static/checkout.html"));
    }

    /**
     * Checkout/onboarding page.
     */
    @GetMapping(value = "/checkout", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<Resource> checkout(@RequestParam(required = false) String key) {
        return Mono.just(new ClassPathResource("static/checkout.html"));
    }

    /**
     * Customer dashboard page.
     */
    @GetMapping(value = "/customer", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<Resource> customerDashboard(@RequestParam(required = false) String key) {
        return Mono.just(new ClassPathResource("static/customer.html"));
    }

    /**
     * Admin panel page.
     */
    @GetMapping(value = "/admin", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<Resource> adminPanel() {
        return Mono.just(new ClassPathResource("static/admin.html"));
    }

    /**
     * API documentation page.
     */
    @GetMapping(value = "/docs", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<Resource> docs() {
        // Redirect to Swagger UI
        return Mono.just(new ClassPathResource("static/checkout.html"));
    }
}
