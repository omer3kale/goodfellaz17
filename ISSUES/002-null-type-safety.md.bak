# Issue 002: Null Type Safety Warnings

**Severity:** Medium (Potential Runtime Issues)
**Type:** Compiler Warning (Unchecked Conversion)
**Count:** 20+ occurrences
**Impact:** Potential NullPointerException at runtime

## Overview

These warnings indicate that nullable types are being passed to `@NonNull` parameters without proper null-checks.

## Files Affected by Category

### 1. UUID findById() Calls (6 occurrences)

**Problem:** `UUID` (can be null) → `@NonNull UUID` parameter

Files affected:
- AdminOrderProgressController.java
  - Line 77: `orderRepository.findById(orderId)`
  - Line 95: `orderRepository.findById(orderId)`
  - Line 120: `orderRepository.findById(orderId)`
  - Line 220: `orderRepository.findById(orderId)`

- CustomerDashboardController.java
  - Line 105: `orderRepository.findById(orderId)`

- PublicApiController.java
  - Line 251: `orderRepository.findById(orderId)`

**Root Cause:**
- `orderId` is extracted from HTTP request and could theoretically be null
- Repository expects `@NonNull UUID`

**Fix Strategy:**
```java
// BEFORE
return orderRepository.findById(orderId)

// AFTER
if (orderId == null) {
    return Mono.error(new IllegalArgumentException("Order ID required"));
}
return orderRepository.findById(orderId);
```

### 2. MediaType Safety Issues (2 occurrences)

**Files:** CustomErrorHandler.java
- Line 51: `.contentType(MediaType.TEXT_HTML)`
- Line 57: `.contentType(MediaType.APPLICATION_JSON)`

**Problem:** `MediaType` (nullable) → `@NonNull MediaType`

**Fix:**
```java
// Ensure MediaType is not null before passing
.contentType(MediaType.APPLICATION_JSON != null ? MediaType.APPLICATION_JSON : MediaType.TEXT_PLAIN)
```

### 3. Map/Collection Safety Issues (2 occurrences)

**Files:** CustomErrorHandler.java (line 58), HybridProxyRouter.java

**Problem:** Map/Collection values passed to unchecked methods

**Fix:** Add null-checks before passing collections

### 4. Integer/Long Merge Operations (4 occurrences)

**Files:** HybridProxyRouter.java, CapacityService.java

**Problem:** Null types in merge operations

Example:
```java
// Line 94 in HybridProxyRouter.java
operationCounts.merge(request.operation(), 1L, Long::sum);
// Parameter 1 and 2 need null-checks
```

**Fix:**
```java
Long currentCount = operationCounts.getOrDefault(request.operation(), 0L);
operationCounts.put(request.operation(), currentCount + 1L);
```

### 5. Repository Type Mismatches (4 occurrences)

**Files:** OrderExecutionService.java, OrderOrchestratorTest.java

**Problem:** Wrong type passed to findById

Example:
- Line 420: `userRepository.findById(userId)` - UUID expected
- Line 426: `serviceRepository.findById(serviceId)` - UUID expected

**Fix:** Ensure types match repository signature

### 6. Transaction Safety (2 occurrences)

**Files:**
- R2dbcTransactionConfig.java (line 25, 40)

**Problem:** ConnectionFactory/ReactiveTransactionManager null safety

**Fix:** Add null-checks in bean configuration

## Fix Priority

1. **High** (Actual NPE Risk):
   - UUID findById() calls
   - Repository type mismatches

2. **Medium** (Configuration Safety):
   - Transaction config safety
   - MediaType safety

3. **Low** (Edge Cases):
   - Merge operation safety

## Implementation Approach

### Option A: Suppress Warnings (Not Recommended)
```java
@SuppressWarnings("null")
public void method() { }
```

### Option B: Add Null-Checks (Recommended)
```java
if (parameter == null) {
    throw new IllegalArgumentException("Parameter cannot be null");
}
// Safe to use now
```

### Option C: Use Optional
```java
Optional.ofNullable(orderId)
    .flatMap(id -> orderRepository.findById(id))
    .orElseThrow(() -> new IllegalArgumentException("Order not found"));
```

## Checklist

### AdminOrderProgressController
- [ ] Line 77 - Add null-check for orderId
- [ ] Line 95 - Add null-check for orderId
- [ ] Line 120 - Add null-check for orderId
- [ ] Line 220 - Add null-check for orderId

### CustomErrorHandler
- [ ] Line 51 - Ensure MediaType.TEXT_HTML is not null
- [ ] Line 57 - Ensure MediaType.APPLICATION_JSON is not null
- [ ] Line 58 - Add null-check for Map value

### HybridProxyRouter
- [ ] Line 94 (2 params) - Fix merge operation null safety
- [ ] Line 154 (2 params) - Fix merge operation null safety

### CapacityService
- [ ] Line 191 (2 params) - Fix reduce operation null safety

### Other Controllers/Services
- [ ] CustomerDashboardController line 105
- [ ] CustomerDashboardController line 221
- [ ] PublicApiController line 251
- [ ] OrderExecutionService lines 288, 294, 420, 426
- [ ] R2dbcTransactionConfig lines 25, 40
- [ ] OrderOrchestratorTest lines 94, 140, 231

## Testing

After fixes:
```bash
mvn clean compile -DskipTests
# Should report: 0 null type safety warnings
```
