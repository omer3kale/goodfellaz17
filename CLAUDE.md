# CLAUDE.md - Self-Audit Checklist

**Purpose:** Document quality gates and testing methodology for thesis evidence.

## Code Quality Checkpoints

### Security
- [ ] No hardcoded secrets (API keys, passwords, DB credentials)
- [ ] No SQL injection vectors (all R2DBC queries parameterized)
- [ ] Input validation on all public endpoints
- [ ] Exception handling doesn't leak sensitive info
- [ ] Semgrep OWASP scan passes

### Type Safety (Java 23)
- [ ] No raw types or unchecked casts
- [ ] All Mono/Flux properly typed
- [ ] No null pointer exceptions (Optional used correctly)
- [ ] Compilation clean (no warnings)

### Testing & Quality Gates

#### 1. Test Pyramid (Required)

I maintain a three-layer test pyramid for this codebase:

1. **Unit tests (fast, isolated)**
   - 8+ tests for `OrderOrchestrator` and core domain logic
   - Use real domain objects, mock only external boundaries (repos, clients)
   - Cover: happy paths, edge cases, error handling, state transitions, metrics, and reactive behavior

2. **Integration tests (real DB, real HTTP)**
   - Use Testcontainers with PostgreSQL (or real Omen PostgreSQL in CI)
   - Validate: `POST /api/orders/create` writes correct rows, task decomposition, metrics aggregation
   - No mock data: real R2DBC calls, real schema, real JSON over HTTP

3. **Chaos / Load tests (production realism)**
   - 500+ concurrent order creations against a running instance
   - Capture: success rate, throughput (orders/sec), avg and P99 latency
   - Used mainly for thesis evidence and capacity planning, not for every commit

Claude must help me keep this pyramid coherent and up-to-date, not invent extra layers.

#### 2. No Mock Data Philosophy

- Unit tests may mock repositories and external systems, **but test data must always be realistic** (e.g., real-looking track IDs, account IDs, quantities).
- Integration and chaos tests must **never** rely on hardcoded "fake world" assumptions like toy schemas or magic flags; they must operate against the real schema and invariants.
- When proposing new tests, Claude should:
  - Start from real business rules and invariants.
  - Reuse existing entities, value objects, and factory methods.
  - Avoid brittle tests that mirror implementation details instead of observable behavior.

#### 3. What Claude Should Do

When I ask for help with tests, Claude should:

- Propose **one concrete test at a time**, starting from the most business-critical case.
- Show the **intent** of the test first (1–2 sentences), then the code.
- Prefer improving or extending existing test classes instead of creating new ones.
- Always make tests:
  - Fast (suitable for CI)
  - Deterministic (no flakiness, no time-based randomness)
  - Readable (clear Arrange–Act–Assert structure)

#### 4. Test Prioritization

When you say "write tests" or "implement next", Claude should prioritize in this order:

1. **Fix broken unit tests** (always first; >2 sec failures or assertion errors block everything)
2. **Extend unit test coverage** (add missing edge cases or business scenarios)
3. **Add integration test golden path** (validate POST/GET real DB interaction only if unit tests green)
4. **Run chaos test** (only after unit + integration stable; capture metrics for thesis)

If you say "move on" or "next scenario", Claude assumes the current layer is solid and focuses on the next layer, not re-design.

#### 5. Invariant-Aware Proposals

When proposing changes (new features, tests, adapters, or services), Claude must:

1. **State the relevant domain invariants** (as bullet points) that this change assumes or affects.
2. **Explain how the change preserves or strengthens** those invariants.
3. **If an invariant would be weakened or broken**, stop and ask for confirmation before proceeding.

Example:
- **Invariant:** For each Order: `quantity == tasks.size`, `status ∈ {PENDING, ACTIVE, COMPLETED, FAILED}`
- **Invariant:** An ACTIVE order must have at least one task in PENDING or EXECUTING state
- **Proposed Change:** Add retry logic to `TaskExecutionService`
- **Invariant Impact:** Task count stays constant (no new tasks created on retry); final Order status still matches tasks; invariants **strengthened** (retry reduces final FAILED orders)

This mirrors MontiCore's context conditions (CoCos): each code change carries a local well-formedness check.

### Reactive Correctness
- [ ] No blocking calls in reactive methods (no .block())
- [ ] Mono/Flux lazy evaluation verified
- [ ] Proper subscription handling (no premature eval)
- [ ] Database operations non-blocking (R2DBC)

### Database (R2DBC)
- [ ] All JPA annotations removed
- [ ] @Table/@Column from org.springframework.data.relational
- [ ] UUID generation before insert
- [ ] Relationships via FK fields (no @OneToMany)
- [ ] Transactions explicit with Mono/Flux operators

## Pre-Commit Gates

```yaml
- Trailing whitespace (corrected)
- End-of-file newlines (corrected)
- YAML validation
- Large files rejected (>5MB)
- Secrets detection (gitleaks)
- Semgrep OWASP-Top-Ten scan
```

## Thesis Evidence Collected

### Methodology
- Three-layer testing strategy
- R2DBC reactive patterns
- Error handling approaches
- Scalability validation

### Results
- Test coverage: 100% orchestrator
- Success rate under load: ~95%
- Latency: [avg] ms / [P99] ms
- Throughput: [X] orders/sec

### Appendices
- Unit test source + output
- Integration test source + output
- Chaos test results
- Pre-commit configuration

## Last Updated

```
Date: 2026-01-26
Time: 22:49
Commits: OrderOrchestratorTest + Security Gates
Status: All quality gates passing
```
