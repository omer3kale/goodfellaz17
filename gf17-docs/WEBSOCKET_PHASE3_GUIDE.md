# Phase 3: WebSocket User Proxy Implementation Guide

> **Status:** Ready for implementation when needed
> **Current:** Stub mode (thesis demo)
> **Estimated time:** 45 minutes

## Architecture Progression

```
Phase 1 (Current): In-memory stub → Logs + empty proxies
Phase 2: STOMP WebSocket → Real user proxy pool
Phase 3: Heartbeat + Auto-healing → Production reliability
```

## Current vs Production

| Aspect | Current (`BotzzzUserProxyPool`) | Production (Phase 3) |
|--------|--------------------------------|---------------------|
| **Status** | Stub (logs + empty Mono) | STOMP WebSocket |
| `nextHealthyUser()` | `return Mono.empty()` | Queries real connected sessions |
| `sendTask()` | `log.info("Task logged")` | Broadcasts via `/topic/user-tasks` |
| `getConnectedUserCount()` | In-memory counter | `SimpUserRegistry` count |
| **Clients connect** | HTTP POST | WebSocket `ws://localhost:8080/ws/user-proxy` |
| **Heartbeat** | None | 30s auto-disconnect |
| **Scale** | Single JVM | Horizontal (Redis broker) |

---

## Step 1: Dependencies (`pom.xml`)

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

---

## Step 2: WebSocketConfig

```java
package com.goodfellaz17.infrastructure.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue/user-proxy");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/user-proxy")
            .setAllowedOrigins("*")
            .withSockJS();
    }
}
```

---

## Step 3: UserProxyWebSocketHandler

```java
package com.goodfellaz17.infrastructure.websocket;

import com.goodfellaz17.application.arbitrage.BotzzzUserProxyPool;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendToUser;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
public class UserProxyWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final BotzzzUserProxyPool proxyPool;

    public UserProxyWebSocketHandler(SimpMessagingTemplate messagingTemplate,
                                      BotzzzUserProxyPool proxyPool) {
        this.messagingTemplate = messagingTemplate;
        this.proxyPool = proxyPool;
    }

    @MessageMapping("/user-proxy/register")
    @SendToUser("/queue/register-response")
    public Mono<UserProxyRegistrationResponse> register(@Payload UserProxyRegistrationRequest request) {
        return proxyPool.registerUserProxy(request)
            .map(response -> new UserProxyRegistrationResponse("registered", response.userId()));
    }

    @MessageMapping("/user-proxy/task")
    public void sendTask(@Payload ProxyTaskRequest task) {
        proxyPool.sendTask(task);
        messagingTemplate.convertAndSend("/topic/user-tasks", task);
    }

    public record UserProxyRegistrationRequest(String userId, String proxyUrl) {}
    public record UserProxyRegistrationResponse(String status, String userId) {}
    public record ProxyTaskRequest(String taskId, String userId, String trackUrl, int quantity) {}
}
```

---

## Step 4: BotzzzUserProxyPool (Production Version)

```java
package com.goodfellaz17.application.arbitrage;

import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.infrastructure.user.UserProxy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BotzzzUserProxyPool {

    private final SimpUserRegistry userRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, UserProxySession> sessions = new ConcurrentHashMap<>();

    public BotzzzUserProxyPool(SimpUserRegistry userRegistry,
                                SimpMessagingTemplate messagingTemplate) {
        this.userRegistry = userRegistry;
        this.messagingTemplate = messagingTemplate;
    }

    public Optional<UserProxy> nextHealthyUser(GeoTarget geoTarget) {
        return sessions.values().stream()
            .filter(UserProxySession::isHealthy)
            .filter(s -> s.matchesGeo(geoTarget))
            .map(UserProxySession::getProxy)
            .findFirst();
    }

    public Mono<Void> sendTask(String userId, ProxyTaskRequest task) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/tasks", task);
        return Mono.empty();
    }

    public int getConnectedUserCount() {
        return userRegistry.getUserCount();
    }

    public record UserProxySession(String sessionId, UserProxy proxy, boolean healthy) {
        public boolean isHealthy() { return healthy; }
        public boolean matchesGeo(GeoTarget geo) { return proxy.matchesGeo(geo); }
        public UserProxy getProxy() { return proxy; }
    }

    public record ProxyTaskRequest(String taskId, String userId, String trackUrl, int quantity) {}
}
```

---

## Step 5: Connection Tracking + Heartbeat

```java
package com.goodfellaz17.infrastructure.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionTracker {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionTracker.class);

    private final Map<String, Instant> lastHeartbeat = new ConcurrentHashMap<>();

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        lastHeartbeat.put(sessionId, Instant.now());
        log.info("WebSocket connected: sessionId={}", sessionId);
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        lastHeartbeat.remove(sessionId);
        log.info("WebSocket disconnected: sessionId={}", sessionId);
    }

    @Scheduled(fixedRate = 30000) // 30s heartbeat check
    public void checkHeartbeats() {
        Instant cutoff = Instant.now().minusSeconds(60);
        int removed = 0;

        var iterator = lastHeartbeat.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isBefore(cutoff)) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            log.warn("Removed {} stale WebSocket sessions", removed);
        }
    }

    public int getActiveSessionCount() {
        return lastHeartbeat.size();
    }
}
```

---

## Client Examples

### JavaScript (SockJS + STOMP)

```javascript
const socket = new SockJS('/ws/user-proxy');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    console.log('Connected: ' + frame);

    // Register as user proxy
    stompClient.send('/app/user-proxy/register', {}, JSON.stringify({
        userId: 'user-123',
        proxyUrl: 'ws://user-proxy:8080'
    }));

    // Subscribe to tasks
    stompClient.subscribe('/user/queue/tasks', function(message) {
        const task = JSON.parse(message.body);
        console.log('Received task:', task);
        executeTask(task);
    });
});
```

### Python (stomp.py)

```python
import stomp
import json

class TaskListener(stomp.ConnectionListener):
    def on_message(self, frame):
        task = json.loads(frame.body)
        print(f"Received task: {task}")

conn = stomp.Connection([('localhost', 61613)])
conn.set_listener('', TaskListener())
conn.connect(wait=True)
conn.subscribe(destination='/user/queue/tasks', id=1)
```

---

## Verification

```bash
# 1. Start app with WebSocket
mvn spring-boot:run -Dspring.profiles.active=local

# 2. Connect via wscat
wscat -c ws://localhost:8080/ws/user-proxy

# 3. Send STOMP CONNECT frame
CONNECT
accept-version:1.2
^@

# 4. Check connected count
curl http://localhost:8080/api/proxy/user/count
# → {"connectedUserCount": 1}
```

---

## Migration Checklist

- [ ] Add `spring-boot-starter-websocket` dependency
- [ ] Create `WebSocketConfig.java`
- [ ] Create `UserProxyWebSocketHandler.java`
- [ ] Create `WebSocketSessionTracker.java`
- [ ] Update `BotzzzUserProxyPool` to use `SimpUserRegistry`
- [ ] Test with `wscat` or JavaScript client
- [ ] Deploy and verify heartbeat mechanism

**Total time: ~45 minutes**
