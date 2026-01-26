# CLAUDE.md - Self-Review & Security Gate

## Purpose
Every code generation task must pass these checks before completion.
Claude reads this automatically; it's a contract with the codebase.

---

## Pre-Generation Checklist

Before I write ANY code:
- [ ] Is the architectural pattern clear? (no ad-hoc hacks)
- [ ] Does this integrate cleanly with existing code? (no duplicate logic)
- [ ] Are there test cases I should include? (not "write code", think "write verifiable code")

---

## Post-Generation Security Audit

After I generate code, I MUST run these checks:

### 1. Secret Scanning
```bash
# ❌ FAIL if found:
# - Hardcoded passwords, API keys, tokens
# - AWS credentials, Spotify tokens, database passwords
# - Private keys, JWTs, bearer tokens
# - Database connection strings with credentials
```

**Check method:**
- Grep for patterns: `password=`, `api_key=`, `token=`, `secret=`, `AWS_`, `DATABASE_URL=`
- Any found = immediate failure, fix required

### 2. Input Validation
```bash
# ❌ FAIL if any of these exist without validation:
# - @RequestParam / @PathVariable directly used in queries
# - String concatenation in SQL (even "safe" JDBC)
# - File paths constructed from user input
# - Process execution with user-provided commands
# - Regular expressions from untrusted sources
```

**Check method:**
- For each `@RequestParam` / `@RequestBody`, verify:
  - Length validation exists
  - Type validation exists (not just casting)
  - Range validation for numbers
  - Whitelist validation for enums/choices
  - No SQL concatenation (use parameterized queries only)

### 3. Type Safety
```bash
# ❌ FAIL if:
# - Method returns Object without casting to correct type
# - Null checks missing on critical paths
# - Optional.get() called without isPresent() check
# - Collections cast without type safety
# - Mono/Flux chain without error handling
```

**Check method:**
```bash
mvn clean compile 2>&1 | grep -i "unchecked\|rawtypes\|null"
# If warnings appear in security-sensitive code → FAIL
```

### 4. Reactive Contract
```bash
# ❌ FAIL if (for WebFlux code):
# - Any .block() calls in orchestrators/services
# - Mono/Flux doesn't propagate to controller level
# - @Transactional used without reactive transaction manager
# - Blocking I/O in reactive context (network calls without proper scheduling)
```

**Check method:**
- Grep for `.block()` outside of testing
- Grep for `Thread.sleep()` in production code
- Verify all service methods return Mono/Flux, not concrete types

### 5. R2DBC Compatibility
```bash
# ❌ FAIL if:
# - Entity uses @OneToMany / @ManyToOne relationships (JPA, not R2DBC)
# - Entity uses @Transient on persistence-critical fields
# - Repository methods return non-reactive types (Mono/Flux required)
# - SQL queries are hand-written (use repository methods)
```

**Check method:**
- Scan entities for JPA annotations
- Verify repositories extend R2dbcRepository, not CrudRepository
- Check all queries use parameterized values

### 6. Error Handling
```bash
# ❌ FAIL if:
# - Exception caught but not logged or rethrown
# - catch(Exception e) {} silently swallows errors
# - .onErrorResume() doesn't return sensible fallback
# - Stack traces leaked to user responses
```

**Check method:**
- Every catch block has log statement
- Every onErrorResume() returns appropriate Mono/Flux
- Error responses don't include internal stack traces

### 7. Thread Safety
```bash
# ❌ FAIL if:
# - Shared mutable state (Map, List) without synchronization
# - Non-atomic compound operations
# - Race condition possible between check and use
```

**Check method:**
- Grep for `new HashMap()`, `new ArrayList()` in shared context
- Use ConcurrentHashMap, Collections.synchronizedList() in orchestrators

### 8. Test Coverage (Critical Paths Only)
```bash
# ⚠️ WARNING (not fail, but required for shipping):
# - createOrder() flow untested
# - recordTaskDelivery() untested
# - Error handling paths untested
```

**Check method:**
- Unit tests exist for:
  - Happy path (order created, tasks generated)
  - Error path (invalid input, DB failure)
  - Edge cases (null, empty, max values)

---

## Spotify Safety Guardrails

For ANY code touching `/api/play/safe`:

```bash
# ✅ VERIFY:
# - Every play request validated against 12 factors
# - No bypass of SpotifySafetyValidator
# - No direct Spotify API calls outside /api/play/safe
# - Third-party services explicitly blocked
```

---

## Database Schema Consistency

For ANY code touching database entities:

```bash
# ✅ VERIFY:
# - Entity fields match database column definitions
# - No JPA @OneToMany in R2DBC entities
# - All @Transient fields are non-persistent
# - Migration scripts exist for schema changes
```

---

## Performance & Scalability Checks

For code handling concurrent requests:

```bash
# ✅ VERIFY:
# - No N+1 queries (for each order, do 1 query, not N)
# - No unbounded loops or recursive calls
# - Connection pooling configured (max 100 connections)
# - Reactive streams don't buffer infinite data
```

---

## Pre-Commit Validation

Before code can merge:

```bash
# Must pass ALL:
mvn clean compile              # Compilation + warnings
mvn test                       # Unit tests
mvn checkstyle:check           # Code style (camelCase, naming)
gitleaks detect                # Secrets scanning
semgrep scan --json            # OWASP Top 10
bandit -r src/                 # Python-style vulns
ruff check src/                # Linting
mypy src/ --strict             # Type checking (if ported to TypeScript)
```

---

## Escalation: When to Fail

**HARD FAIL (blocking, must fix before ANY use):**
- Hardcoded secrets
- SQL injection vectors
- Unvalidated user input in critical paths
- Blocking calls in reactive context
- Entity mapping mismatches (JPA in R2DBC context)

**SOFT FAIL (document, must acknowledge before shipping):**
- Missing test cases for edge cases
- Type warnings (unchecked casts)
- Performance concerns (N+1 queries)
- Accessibility issues (if applicable)

---

## How Claude Uses This

Every response with code generation:

1. **I read this file first** (contract established)
2. **I generate code** against these standards
3. **I run self-audits** (grep, type checks, test validation)
4. **I report failures** (be explicit: "FAIL: Hardcoded API key in line 42")
5. **I fix before output** (no code reaches you with known bugs)

If I can't verify, I say so explicitly: "Cannot verify reactive contract without running compile; paste the error and I'll fix."

---

## Example: Self-Audit in Action

**Scenario: I generated OrderOrchestrator.java**

I should report:
```
✅ Secret scan: PASS (no hardcoded keys)
✅ Input validation: PASS (OrderRequest validated in controller)
✅ Type safety: PASS (no raw types, null checks present)
✅ Reactive contract: PASS (all methods return Mono/Flux)
✅ R2DBC compatibility: PASS (uses R2dbcRepository, no JPA)
✅ Error handling: PASS (onErrorResume with logging)
✅ Thread safety: PASS (uses ConcurrentHashMap for metrics)
⚠️  Test coverage: WARNING (no unit tests yet, add to PR)

Status: APPROVED for merge
```

If ANY check fails, I don't submit the code.

---

## For You (Code Review)

You still review PR descriptions. I'll state:
- What changed
- Why (architectural reason)
- Self-audit results (✅ or ⚠️)
- Any risks I identified
- Recommended additional tests

You decide if you trust it. But you're not manually reviewing 500 lines—the checklist caught the obvious bugs.

---

## Updating This File

As new security concerns emerge:
- Add new checks to this file
- I automatically see and apply them
- Example: "Add check for residential proxy rotation (don't use same IP twice in N seconds)"

This is a living contract. Update it as the project evolves.
