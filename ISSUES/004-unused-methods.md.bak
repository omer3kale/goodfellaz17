# Issue 004: Unused Methods

**Severity:** Low (Code Quality)
**Type:** Compiler Warning
**Count:** 4 occurrences
**Impact:** Dead code, maintenance confusion

## Files Affected

### GoodFellaz17IntegrationTest.java

**Location:** `src/test/java/com/goodfellaz/integration/GoodFellaz17IntegrationTest.java`

#### 1. Line 637: `assertQuantityConserved()`
```java
private void assertQuantityConserved(int ordered, int delivered, int failed, int pending) {
    // Method never called in this test class
}
```

**Purpose:** Appears to be a helper method for validating quantity conservation invariant

**Status:** UNUSED - No test method calls this

**Action:** Either delete or create test case that uses it

#### 2. Line 642: `assertRefundCorrect()`
```java
private void assertRefundCorrect(int failedCount, BigDecimal unitPrice, BigDecimal actualRefund) {
    // Method never called in this test class
}
```

**Purpose:** Helper for validating refund calculations

**Status:** UNUSED - No test method calls this

**Action:** Either delete or create test case that uses it

#### 3. Line 649: `assertBalanceConserved()`
```java
private void assertBalanceConserved(BigDecimal income, BigDecimal refund, BigDecimal netBalance) {
    // Method never called in this test class
}
```

**Purpose:** Helper for validating balance conservation

**Status:** UNUSED - No test method calls this

**Action:** Either delete or create test case that uses it

## Context

These methods appear to be test helpers from a refactoring or incomplete test suite. They represent good testing concepts but are not utilized in current tests.

## Options

### Option A: Delete (If Not Needed)
```bash
# Remove unused assertion methods
# Keep codebase clean
```

### Option B: Create Tests That Use Them
```java
@Test
public void testQuantityConservationInvariant() {
    // Create orders, execute tasks
    // Use assertQuantityConserved() to validate
}

@Test
public void testRefundCalculationCorrectness() {
    // Test refund scenarios
    // Use assertRefundCorrect()
}

@Test
public void testBalanceConservation() {
    // Test balance updates
    // Use assertBalanceConserved()
}
```

### Option C: Move to Shared Test Utilities
```java
// Move to AbstractIntegrationTestBase or TestAssertions class
// Make reusable across test suite
```

## Decision Matrix

| Option | Effort | Benefit | Risk |
|--------|--------|---------|------|
| Delete | 5 min | Clean code | Lose utility if needed later |
| Create Tests | 30 min | Validate invariants | Time investment |
| Move to Utils | 15 min | Reusable | Adds abstraction layer |

## Recommendation

**Approach:** Create tests that use these methods

**Reasoning:**
1. Methods represent important invariants (quantity, refund, balance)
2. Testing these invariants aligns with CLAUDE.md requirements
3. These are business-critical validations
4. Better to have explicit tests than utility methods

## Implementation Plan

1. Create `QuantityConservationIntegrationTest` class
   - Test quantity conservation across order lifecycle
   - Use `assertQuantityConserved()`

2. Create `RefundCalculationIntegrationTest` class
   - Test refund logic with various failure scenarios
   - Use `assertRefundCorrect()`

3. Create `BalanceConservationIntegrationTest` class
   - Test user balance updates
   - Use `assertBalanceConserved()`

## Checklist

- [ ] Review each method's implementation
- [ ] Decide: Keep/Delete/Move
- [ ] If keeping: Create test class that uses it
- [ ] If deleting: Remove from GoodFellaz17IntegrationTest
- [ ] If moving: Extract to TestAssertions utility class
- [ ] Run tests to verify no impact
- [ ] Update test coverage report

## Priority

**Low** - These don't affect compilation or functionality, but indicate incomplete test coverage.

## Related Issues

- See CLAUDE.md: Test Pyramid requirements
- Related: Issue 005 (Resource Leaks) - Same test file

## Notes

These assertion methods suggest incomplete test scenarios. Consider reviewing the test strategy for:
- Quantity conservation edge cases
- Refund calculation accuracy
- Balance tracking invariants
