package com.spotifybot.presentation.user;

import com.spotifybot.application.arbitrage.BotzzzUserProxyPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.UUID;

/**
 * WebSocket Controller for User Dashboard.
 * 
 * Handles real-time communication with botzzz773.pro users:
 * - Task completion reports
 * - User heartbeats
 * - Earnings queries
 */
@Controller
public class UserWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(UserWebSocketController.class);

    private final BotzzzUserProxyPool userPool;

    public UserWebSocketController(BotzzzUserProxyPool userPool) {
        this.userPool = userPool;
    }

    /**
     * Handle task completion from user.
     * 
     * Client sends: /app/task/complete
     */
    @MessageMapping("/task/complete")
    public void handleTaskCompletion(
            @Payload TaskCompletionMessage message,
            SimpMessageHeaderAccessor headerAccessor) {
        
        String sessionId = headerAccessor.getSessionId();
        
        log.info("Task completed: sessionId={}, userId={}, taskId={}, plays={}",
                sessionId, 
                message.userId(), message.taskId(), message.plays());
        
        userPool.completeTask(
                UUID.fromString(message.taskId()),
                message.userId(),
                message.plays()
        );
    }

    /**
     * Handle user heartbeat (keeps session alive).
     * 
     * Client sends: /app/heartbeat
     */
    @MessageMapping("/heartbeat")
    public void handleHeartbeat(
            @Payload HeartbeatMessage message,
            SimpMessageHeaderAccessor headerAccessor) {
        
        // Update last seen timestamp
        log.debug("Heartbeat received: userId={}", message.userId());
    }

    /**
     * Handle user registration via WebSocket.
     * 
     * Client sends: /app/register
     */
    @MessageMapping("/register")
    public void handleRegistration(
            @Payload UserRegistrationMessage message,
            SimpMessageHeaderAccessor headerAccessor) {
        
        String sessionId = headerAccessor.getSessionId();
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        
        String ipAddress = attrs != null ? 
                (String) attrs.getOrDefault("ip", "unknown") : "unknown";
        
        userPool.registerUser(
                sessionId,
                message.userId(),
                ipAddress,
                message.userAgent(),
                message.country()
        );
        
        log.info("User registered via WebSocket: userId={}, country={}", 
                message.userId(), message.country());
    }

    // === Message Records ===

    public record TaskCompletionMessage(
            String taskId,
            String userId,
            int plays,
            int durationSeconds,
            String error
    ) {}

    public record HeartbeatMessage(
            String userId,
            long timestamp
    ) {}

    public record UserRegistrationMessage(
            String userId,
            String userAgent,
            String country,
            boolean hasSpotifyPremium
    ) {}
}
