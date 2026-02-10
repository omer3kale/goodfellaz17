# CLAUDE.md: Development Contract for goodfellaz17

This file is the single source of truth for how Claude should help with this codebase.

---

## Testing & Quality Gates

### 1. Test Pyramid (Required)

I maintain a three-layer test pyramid:

1. **Unit tests (fast, isolated)**
   - 8+ tests for `OrderOrchestrator` and core domain logic
   - Use real domain objects, mock only external boundaries (repos, clients)
   - Cover: happy paths, edge cases, error handling, state transitions, metrics, reactive behavior

2. **Integration tests (real DB, real HTTP)**
   - Use Testcontainers with PostgreSQL (or real HP Omen PostgreSQL in CI)
   - Validate: `POST /api/orders/create` writes correct rows, task decomposition, metrics aggregation
   - No mock data: real R2DBC calls, real schema, real JSON over HTTP

3. **Chaos / Load tests (production realism)**
   - 500+ concurrent order creations against a running instance
   - Capture: success rate, throughput (orders/sec), avg and P99 latency
   - Used mainly for thesis evidence and capacity planning, not for every commit

**Claude must keep this pyramid coherent and up-to-date, not invent extra layers.**

---

### 2. No Mock Data Philosophy

- Unit tests may mock repositories and external systems, **but test data must always be realistic** (e.g., real-looking track IDs, account IDs, quantities).
- Integration and chaos tests must **never** rely on hardcoded "fake world" assumptions like toy schemas or magic flags; they must operate against the real schema and invariants.
- When proposing new tests, Claude should:
  - Start from real business rules and invariants.
  - Reuse existing entities, value objects, and factory methods.
  - Avoid brittle tests that mirror implementation details instead of observable behavior.

---

### 3. Invariant-Aware Proposals (MontiCore CoCo Discipline)

When proposing changes (new features, tests, adapters), Claude must:

1. **State the domain invariants** relevant to that change (as bullet points).
2. **Explain how the change preserves or strengthens them.**
3. **If an invariant would be weakened or broken, stop and ask for confirmation** before proceeding.

Example:

```
Invariant: "For each Order: quantity == tasks.size"
Proposal: "Add retry logic to TaskExecutionService"
Impact: Invariant still holds (task count doesn't change); no stale tasks left PENDING.
```

---

### 4. What Claude Should Do

When I ask for help with tests, Claude should:

- Propose **one concrete test at a time**, starting from the most business-critical case.
- Show the **intent** of the test first (1–2 sentences), then the code.
- Prefer improving or extending existing test classes instead of creating new ones.
- Always make tests:
  - Fast (suitable for CI)
  - Deterministic (no flakiness, no time-based randomness)
  - Readable (clear Arrange–Act–Assert structure)

When I ask to "move on" or "implement next", Claude should assume the pyramid is in place and focus on the next missing layer or scenario, not re-design the whole strategy.

---

## Architecture & Design

### Hexagonal Ports/Adapters Pattern

The codebase follows domain-driven design with clear boundaries:

- **Domain Layer** (pure Java, zero framework dependencies)
  - `Order`, `OrderTask` entities with clear invariants
  - `OrderOrchestrator` service (business logic)
  - `SpotifyPlayPort` interface (abstraction for external play execution)
  - DTOs: `SpotifyPlayCommand`, `PlayResult`

- **Service Layer** (Spring, Reactive)
  - `TaskExecutionService` (orchestrates task lifecycle PENDING → EXECUTING → COMPLETED/FAILED)
  - Validates preconditions, builds commands, handles errors + retries

- **Adapter Layer** (swappable implementations)
  - `LocalPlayAdapter` (dummy 90% success rate for local dev)
  - Later: `RealProxySpotifyAdapter` (real Spotify/proxy calls)
  - **Zero changes to domain when swapping adapters**

- **REST Layer**
  - `OrderController` with endpoints:
    - `POST /api/orders/create` (create order + decompose into tasks)
    - `GET /api/orders/{id}` (fetch order + tasks)
    - `POST /api/orders/{id}/tasks/{taskId}/execute` (manual task execution)
    - `GET /api/orders/metrics` (aggregated order/task metrics)

---

## Current Status (Session 2 Complete)

✅ **Adapter layer fully implemented:**
- SpotifyPlayPort + DTOs
- TaskExecutionService (PENDING → EXECUTING → COMPLETED/FAILED lifecycle)
- LocalPlayAdapter (dummy 90% success implementation)
- REST endpoint for manual task execution
- 3 unit tests (TaskExecutionServiceTest) - all passing
- 1 smoke test validating full chain

✅ **Testing framework complete:**
- 8 OrderOrchestratorTest tests (unit)
- 3 TaskExecutionServiceTest tests (service layer)
- 1 smoke test (full chain)
- All pre-commit gates active (semgrep, gitleaks, formatting)

✅ **Git history clean:**
- 6 commits this session
- All gates pass
- Ready for production development

---

## Next Steps (Choose One Path)

### Path A: Automatic Background Executor (Scheduler)
**Time:** 1-2 hours | **Complexity:** Medium

Wire TaskExecutionService into a scheduled worker that:
- Continuously polls DB for PENDING tasks (e.g., every 100ms)
- Executes each task concurrently (threadpool, not blocking)
- Updates task status automatically
- Result: Orders complete without manual REST trigger

**Use case:** Production readiness (simulates real async worker)

---

### Path B: Real Spotify/Proxy Integration
**Time:** 2-3 hours | **Complexity:** High

1. Create `RealProxySpotifyAdapter implements SpotifyPlayPort`
2. Swap bean configuration (no code changes to domain)
3. Extract real `trackId` from Order instead of placeholder
4. Point to real Spotify/proxy infrastructure
5. Test with manual REST endpoint or scheduler

**Use case:** Thesis validation (proves actual Spotify integration works)

---

### Path C: Chaos Test of New Endpoint
**Time:** 1-2 hours | **Complexity:** Low

Modify existing chaos test to:
- `POST /api/orders/create` (generate real orders)
- Immediately execute each task's endpoint
- Measure real latency of full chain
- Capture success rate, throughput, P99 metrics

**Use case:** Thesis results (latency under concurrent load)

---

### Recommended Sequence
1. **Path A** (scheduler) → Enables production-like execution
2. **Path B** (real adapter) → Validates Spotify connectivity
3. **Path C** (chaos endpoint) → Measures performance under load
4. **Thesis:** Use metrics from A+C for results section

---

## Thesis Integration

**Chapter 3 (Methodology):**
- Reference CLAUDE.md for invariant-aware architecture discipline
- Use adapter layer code as proof of hexagonal ports/adapters pattern
- Cite unit + smoke tests as validation strategy

**Chapter 4 (Results):**
- Scheduler latency metrics (avg, P99)
- Chaos test results (throughput, success rate)
- Endpoint performance under concurrent load

**Appendices:**
- OrderOrchestrator test source
- TaskExecutionService test source
- Smoke test output
- Pre-commit gate configuration

---

## Key Constraints

- **No mock data** in tests (realistic payloads always)
- **One test at a time** (propose, not bulk changes)
- **Invariants first** (state them before proposing changes)
- **Minimal markdown at root** (only README.md and CLAUDE.md)
- **Clean git history** (descriptive commits, no WIP)

---

## Documentation Structure

All markdown files organized in `gf17-docs/`:

```
gf17-docs/
├── ADAPTER_LAYER_COMPLETE.md         (session summary)
├── SAFETY_MODULE_DEPLOYMENT.md       (deployment guide)
├── SAFETY_QUICK_START.md             (quick start)
├── THESIS_EVIDENCE.md                (thesis context)
├── docs/                              (architecture & guides)
│   ├── architecture/                 (C4 diagrams, sequence flows)
│   ├── buyer/                        (API docs, whitelabel)
│   ├── thesis/                       (chapter content)
│   └── *.md                          (implementation guides)
├── infra/                            (deployment & infrastructure)
│   ├── BACKUP_STRATEGY.md
│   ├── DB_CONNECTION_PLAYBOOK.md
│   ├── SELFHOSTED_DEPLOYMENT.md
│   └── *.md                          (security, roles, runbooks)
└── proxy-infrastructure/              (proxy service docs)
    └── README.md
```

**IDE Benefits:**
- Root clean (only `README.md`, `CLAUDE.md`, code)
- All docs organized and discoverable
- No IDE noise from scattered markdown files

---

**Last Updated:** 2026-01-26 23:35 UTC
