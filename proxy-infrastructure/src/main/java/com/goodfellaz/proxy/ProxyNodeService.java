package com.goodfellaz.proxy;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proxy Node Service
 * 
 * Manages lifecycle and routing of self-hosted proxy nodes.
 * Each node runs on a separate port and is registered in PostgreSQL.
 * 
 * Contract (matches proxy_network_erd):
 * - /health endpoint reports ONLINE, DEGRADED, or OFFLINE
 * - Each node accepts HTTP requests and routes via configured upstream
 * - Failures are logged and reported back to main service
 * 
 * Design Constraints:
 * - MUST NOT modify order invariants (INV-1 through INV-6)
 * - MUST NOT change how delivered/failed_permanent/remains are calculated
 * - Health transitions governed by existing ProxyHealthRules (Phase 1)
 * 
 * @see com.goodfellaz.proxy.service.ProxyHealthRules
 * @see com.goodfellaz.proxy.model.ProxyNode
 */
@Service
public class ProxyNodeService {
    
    private final Map<String, ProxyNodeInfo> activeNodes = new ConcurrentHashMap<>();
    
    /**
     * Start a local proxy node on the specified port.
     * Phase 3a: Development on MacBook M1
     * 
     * @param port Port number (default: 9090)
     * @return Node ID for tracking
     */
    public String startLocalNode(int port) {
        String nodeId = UUID.randomUUID().toString();
        System.out.println("[ProxyNode] Starting node " + nodeId + " on port " + port);
        
        ProxyNodeInfo info = new ProxyNodeInfo(nodeId, "localhost", port, "ONLINE");
        activeNodes.put(nodeId, info);
        
        // TODO Phase 3a: Implement HTTP server
        // - Accept requests on /execute endpoint
        // - Route to Spotify API via residential IP
        // - Report results back to OrderDeliveryWorker
        
        return nodeId;
    }
    
    /**
     * Query health status of a specific node.
     * Matches ProxyStatus enum: ONLINE, DEGRADED, BANNED, OFFLINE
     */
    public String getHealth(String nodeId) {
        ProxyNodeInfo info = activeNodes.get(nodeId);
        if (info == null) {
            return "OFFLINE";
        }
        return info.status();
    }
    
    /**
     * List all active nodes with their current status.
     * Used by HybridProxyRouterV2 for load balancing decisions.
     */
    public List<ProxyNodeInfo> listActiveNodes() {
        return new ArrayList<>(activeNodes.values());
    }
    
    /**
     * Update node health status (called by ProxyHealthRules).
     * Transitions: ONLINE → DEGRADED → BANNED
     */
    public void updateNodeStatus(String nodeId, String newStatus) {
        ProxyNodeInfo existing = activeNodes.get(nodeId);
        if (existing != null) {
            activeNodes.put(nodeId, new ProxyNodeInfo(
                existing.nodeId(),
                existing.host(),
                existing.port(),
                newStatus
            ));
            System.out.println("[ProxyNode] Node " + nodeId + " status: " + newStatus);
        }
    }
    
    /**
     * Shutdown a proxy node gracefully.
     */
    public void stopNode(String nodeId) {
        activeNodes.remove(nodeId);
        System.out.println("[ProxyNode] Stopped node " + nodeId);
    }
    
    /**
     * Immutable record for proxy node info.
     * Mirrors ProxyNode entity but for runtime tracking.
     */
    public record ProxyNodeInfo(
        String nodeId,
        String host,
        int port,
        String status
    ) {}
}
