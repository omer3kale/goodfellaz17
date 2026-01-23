# Phase 3: Self-Hosted Proxy Infrastructure

## Overview

This module contains the self-hosted proxy layer for GoodFellaz17. 
Proxies run as separate processes (local or containerized) and register 
with the main PostgreSQL database.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    MacBook (Order Management)                    │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │  OrderService   │  │ HybridProxyV2   │  │ DeliveryWorker  │  │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘  │
│           │                    │                    │           │
│           └────────────────────┼────────────────────┘           │
│                                │                                │
│                    ┌───────────▼───────────┐                    │
│                    │   ProxyNodeService    │                    │
│                    └───────────┬───────────┘                    │
└────────────────────────────────┼────────────────────────────────┘
                                 │
                    ┌────────────┼────────────┐
                    │            │            │
              ┌─────▼─────┐ ┌────▼────┐ ┌─────▼─────┐
              │ Proxy :9090│ │Proxy:9091│ │Proxy:9092│
              │  (local)  │ │ (local) │ │ (Docker) │
              └───────────┘ └─────────┘ └───────────┘
```

## Design Constraints (FROZEN)

These MUST NOT be modified without updating thesis Chapters 3-5:

- **INV-1**: `delivered + failed_permanent + remains = quantity`
- **INV-2**: `delivered` is monotonically increasing
- **INV-3**: `refunded_amount = failed_permanent × unit_price`
- **INV-4**: `balance = initial - charged + refunded`
- **INV-5**: `remains >= 0`
- **INV-6**: Idempotent order submission

## Phase 3 Roadmap

### Stage 3a: Local Development (Current)
- [x] Scaffold proxy-infrastructure module
- [ ] Implement proxy-node.sh HTTP server
- [ ] Register local proxy in PostgreSQL
- [ ] Run integration tests against real proxy
- [ ] Commit: "Phase 3a: Local proxy development"

### Stage 3b: Multi-Proxy Cluster
- [ ] Spin up 3-5 proxy containers
- [ ] Implement load balancing in HybridProxyRouterV2
- [ ] Run Week 3 chaos test with real proxies
- [ ] Commit: "Phase 3b: Multi-proxy local cluster"

### Stage 3c: Distributed (HP Omen)
- [ ] Move proxy pool to HP Omen
- [ ] MacBook → Omen via REST/gRPC
- [ ] Benchmark E2E latency
- [ ] Commit: "Phase 3c: Distributed proxy infrastructure"

## Quick Start

```bash
# 1. Start a local proxy node
cd proxy-infrastructure/local
chmod +x proxy-node.sh
./proxy-node.sh 9090

# 2. Verify health
curl http://localhost:9090/health

# 3. Register in PostgreSQL (run against your dev DB)
psql -d goodfellaz -f ../config/proxy-register.sql

# 4. Run integration tests
cd ../..
mvn test -Dtest="GoodFellaz17IntegrationTest"
```

## Files

| File | Purpose |
|------|---------|
| `src/main/java/.../ProxyNodeService.java` | Java service for node lifecycle |
| `config/proxy-register.sql` | SQL to register proxy in DB |
| `local/proxy-node.sh` | Bash/Python stub for local proxy |
| `docker/` | Docker configs for containerized proxies |

## Rollback

If proxy work breaks anything:
```bash
git checkout thesis-core-freeze
```

This restores the thesis-frozen state with all 26 tests passing.
