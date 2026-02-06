package org.springframework.messaging.simp;

/**
 * Minimal local stub of Spring's SimpMessagingTemplate.
 *
 * Only exists so the arbitrage module compiles without bringing in the full
 * Spring WebSocket/STOMP stack. All methods are no-op.
 *
 * If you later add the real Spring WebSocket/STOMP configuration,
 * delete this class so the real SimpMessagingTemplate from spring-messaging
 * is used instead.
 */
public class SimpMessagingTemplate {

    public SimpMessagingTemplate() {
        // no-op
    }

    /**
     * Send a message to a specific user.
     *
     * Signature mirrors the real Spring method we use in the pool:
     * convertAndSendToUser(String user, String destination, Object payload)
     */
    public void convertAndSendToUser(String user, String destination, Object payload) {
        // no-op: in this project we don't actually use the WebSocket channel yet
    }
}
