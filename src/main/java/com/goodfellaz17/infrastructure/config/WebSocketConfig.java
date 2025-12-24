package com.goodfellaz17.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket Configuration for User Dashboard.
 * 
 * Real-time task assignment to botzzz773.pro users:
 * 1. User connects via WebSocket
 * 2. Server sends task to /user/{userId}/queue/tasks
 * 3. User executes → reports completion
 * 4. User earns 30% commission
 * 
 * Endpoints:
 * - /ws/user - STOMP connection
 * - /user/queue/tasks - Task assignments (user-specific)
 * - /app/task/complete - Task completion reports
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        // Enable simple in-memory broker for /queue (user-specific) and /topic (broadcast)
        config.enableSimpleBroker("/queue", "/topic");
        
        // Application destination prefix for @MessageMapping
        config.setApplicationDestinationPrefixes("/app");
        
        // User destination prefix (enables /user/{userId}/queue/...)
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        // Main WebSocket endpoint for users
        registry.addEndpoint("/ws/user")
                .setAllowedOrigins(
                        "https://botzzz773.pro",
                        "https://goodfellaz.vercel.app",
                        "http://localhost:3000",
                        "http://localhost:8080"
                )
                .withSockJS(); // Fallback for older browsers
        
        // Alternative endpoint without SockJS
        registry.addEndpoint("/ws/user/raw")
                .setAllowedOrigins("*");
    }
}
