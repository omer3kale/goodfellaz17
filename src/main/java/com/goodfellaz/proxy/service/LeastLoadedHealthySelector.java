package com.goodfellaz.proxy.service;

import com.goodfellaz.proxy.model.ProxyNode;
import com.goodfellaz.proxy.model.ProxyStatus;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class LeastLoadedHealthySelector implements ProxySelector {

    @Override
    public ProxyNode select(List<ProxyNode> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new NoAvailableProxyException("No proxies available");
        }

        // Prefer HEALTHY
        List<ProxyNode> healthy = candidates.stream()
                .filter(p -> p.getStatus() == ProxyStatus.HEALTHY)
                .sorted(Comparator.comparingInt(ProxyNode::getCurrentLoad))
                .collect(Collectors.toList());

        if (!healthy.isEmpty()) {
            return healthy.get(0);
        }

        // Fallback to DEGRADED
        List<ProxyNode> degraded = candidates.stream()
                .filter(p -> p.getStatus() == ProxyStatus.DEGRADED)
                .sorted(Comparator.comparingInt(ProxyNode::getCurrentLoad))
                .collect(Collectors.toList());

        if (!degraded.isEmpty()) {
            return degraded.get(0);
        }

        throw new NoAvailableProxyException("No HEALTHY or DEGRADED proxies available");
    }
}
