package com.goodfellaz17.presentation.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Dashboard Page Controller.
 * 
 * Serves HTML pages for customer dashboard and admin panel.
 * Redirects with proper paths for static file serving.
 */
@Controller
public class DashboardPageController {

    /**
     * Customer dashboard page.
     * URL: /customer?key=demo_abc123
     */
    @GetMapping("/customer")
    public String customerDashboard(@RequestParam(required = false) String key) {
        // Forward to static HTML (key is handled by JS)
        return "forward:/customer.html";
    }

    /**
     * Admin panel page.
     * URL: /admin
     */
    @GetMapping("/admin")
    public String adminPanel() {
        return "forward:/admin.html";
    }

    /**
     * Root redirect to landing page.
     */
    @GetMapping("/")
    public String home() {
        return "forward:/index.html";
    }
}
