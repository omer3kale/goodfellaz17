# Issues Tracker - GoodFellaz17

**Generated:** January 31, 2026
**Total Issues:** 7 categories with 55+ individual problems
**Status:** Open for resolution

## Quick Stats

| Category | Count | Severity | Type | Effort |
|----------|-------|----------|------|--------|
| [001] Unused Imports | 12 | Low | Code Quality | 5 min |
| [002] Null Type Safety | 20+ | Medium | Runtime Safety | 30 min |
| [003] Unchecked Type Casts | 2 | Low-Medium | Type Safety | 15 min |
| [004] Unused Methods | 3 | Low | Code Quality | 15 min |
| [005] Resource Leaks | 2 | High | Resource Mgmt | 20 min |
| [006] Unused Field | 1 | Low | Code Quality | 5 min |
| [007] Deprecated Method | 1 | Low-Medium | API Compatibility | 10 min |

**Total Estimated Effort:** ~100 minutes (1.5-2 hours)

---

## Issues by Priority

### CRITICAL (Fix First)
- [005] Resource Leaks - Can cause test flakiness and CI failures

### HIGH (Fix Soon)
- [002] Null Type Safety - Potential NullPointerException at runtime

### MEDIUM (Fix This Week)
- [003] Unchecked Type Casts - Potential ClassCastException at runtime
- [007] Deprecated Method - Will break in future versions

### LOW (Code Cleanup)
- [001] Unused Imports - IDE noise and code cleanliness
- [004] Unused Methods - Dead code
- [006] Unused Field - Dead code

---

## Issues by File

### AdminOrderProgressController.java
- [001] Unused import: TaskStatus (line 8)
- [002] Null type safety on orderId (lines 77, 95, 120, 220)
- [006] Unused logger field (line 39)

### CheckoutController.java
- [001] Unused imports: Swagger annotations (lines 8-10)

### CustomerDashboardController.java
- [001] Unused import: ApiKeyEntity (line 3)
- [002] Null type safety on orderId (line 105)
- [002] Null type safety on serviceId (line 221)

### CustomErrorHandler.java
- [002] Null type safety on MediaType (lines 51, 57)
- [002] Null type safety on Map value (line 58)

### GeneratedDeviceNodeRepository.java
- [001] Unused import: Instant (line 10)

### GoodFellaz17IntegrationTest.java
- [004] Unused method: assertQuantityConserved() (line 637)
- [004] Unused method: assertRefundCorrect() (line 642)
- [004] Unused method: assertBalanceConserved() (line 649)
- [004] Unused import: HybridProxyRouterV2 (line 14)
- [005] Resource leak: PostgreSQLContainer (line 62)

### HybridProxyRouter.java
- [002] Null type safety in merge operations (lines 94, 154) - 4 params

### IntegrationTestBase.java
- [005] Resource leak: PostgreSQLContainer (line 21)

### OrderResponse.java
- [007] Deprecated method: getCost() (line 68)

### OrderExecutionService.java
- [002] Null type safety (lines 288, 294, 420, 426) - 4 issues

### OrderOrchestratorTest.java
- [001] Unused import: Instant (line 18)
- [002] Null type safety on orderRepository.save() (lines 94, 140, 231) - 3 issues

### PublicApiController.java
- [001] Unused import: Instant (line 20)
- [002] Null type safety on orderId (line 251)

### R2dbcTransactionConfig.java
- [002] Null type safety on ConnectionFactory (line 25)
- [002] Null type safety on ReactiveTransactionManager (line 40)

### Scope.java
- [003] Unchecked type cast (line 62)
- [003] Unchecked type cast (line 91)

### ServiceEntity.java
- [001] Unused import: List (line 16)

### CapacityAdminController.java
- [001] Unused imports: CapacitySnapshot, CanAcceptResult (lines 4-5)

### CapacityService.java
- [001] Unused imports: OrderEntity, Flux (lines 3, 14)
- [002] Null type safety in reduce (line 191) - 2 params

---

## Issue Details

Click on issue number to view detailed resolution guide:

### [001 - Unused Imports](001-unused-imports.md)
12 unused import statements in 10 files. Safe to remove, IDE auto-fix available.

### [002 - Null Type Safety](002-null-type-safety.md)
20+ instances where nullable types passed to @NonNull parameters. Requires null-checks or Optional wrapping.

### [003 - Unchecked Type Casts](003-unchecked-type-casts.md)
2 unsafe type casts in Scope.java symbol table. Need error handling or type bounds.

### [004 - Unused Methods](004-unused-methods.md)
3 test helper methods never called. Either delete or create tests that use them.

### [005 - Resource Leaks](005-resource-leaks.md)
2 test containers not properly closed. Use @Container/@Testcontainers annotations.

### [006 - Unused Field](006-unused-field.md)
1 logger field unused in AdminOrderProgressController. Add @Slf4j and use log, or remove.

### [007 - Deprecated Method](007-deprecated-method.md)
1 call to deprecated getCost() method in OrderResponse. Find and use replacement method.

---

## Resolution Workflow

### Step 1: Quick Wins (15 minutes)
```bash
# Issue 001: Unused imports - Use IDE bulk fix
# Issue 006: Unused field - Add @Slf4j

# Compile check
mvn clean compile -DskipTests
```

### Step 2: Type Safety (30 minutes)
```bash
# Issue 002: Null type safety - Add null-checks
# Issue 003: Unchecked casts - Add error handling

mvn clean compile -DskipTests
```

### Step 3: Resource Management (20 minutes)
```bash
# Issue 005: Resource leaks - Add @Container

mvn clean test
```

### Step 4: Code Quality (15 minutes)
```bash
# Issue 004: Unused methods - Delete or create tests
# Issue 007: Deprecated method - Find replacement

mvn clean compile -DskipTests
```

### Final Verification
```bash
mvn clean compile test -DskipTests
# Expected: 0 warnings, all tests pass
```

---

## Tackling Strategy

### Recommended Order (By Impact)
1. **Issue 005** - Resource Leaks (HIGH severity, quick fix)
2. **Issue 002** - Null Type Safety (Many occurrences, important)
3. **Issue 001** - Unused Imports (Quick win, satisfying)
4. **Issue 003** - Unchecked Casts (Type safety, important)
5. **Issue 007** - Deprecated Method (Future-proofing)
6. **Issue 006** - Unused Field (Code quality)
7. **Issue 004** - Unused Methods (Refactoring opportunity)

### Recommended Order (By Effort)
1. **Issue 001** - 5 min
2. **Issue 006** - 5 min
3. **Issue 004** - 15 min
4. **Issue 007** - 10 min
5. **Issue 003** - 15 min
6. **Issue 005** - 20 min
7. **Issue 002** - 30 min

---

## Commands Reference

```bash
# Check current state
mvn clean compile -DskipTests

# Watch for specific issues
mvn compile -X 2>&1 | grep "warning\|deprecated"

# Run all tests
mvn clean test

# Quick compile (no cleanup)
mvn compile

# Full build with tests
mvn clean install
```

---

## Notes for Next Session

- Each issue has its own detailed markdown file
- Files are in `/ISSUES/` directory
- Follow the checklist in each issue file
- Use the provided code snippets for fixes
- Test after each fix to ensure no regressions
- Commit each issue fix separately for clean git history

---

## Tracking

| Issue | Status | Started | Completed | Notes |
|-------|--------|---------|-----------|-------|
| 001 | Not Started | - | - | - |
| 002 | Not Started | - | - | - |
| 003 | Not Started | - | - | - |
| 004 | Not Started | - | - | - |
| 005 | Not Started | - | - | - |
| 006 | Not Started | - | - | - |
| 007 | Not Started | - | - | - |

Update this table as you complete each issue.

---

**Last Updated:** January 31, 2026 - 4:50 PM
**Created By:** Copilot Analysis
**Status:** Ready for Review
