# Issue 003: Unchecked Type Casts

**Severity:** Low-Medium (Type Safety)
**Type:** Compiler Warning
**Count:** 2 occurrences
**Impact:** Potential ClassCastException at runtime

## Files Affected

### Scope.java (Symbol Table)

**Location:** `src/main/java/com/goodfellaz17/symboltable/Scope.java`

#### Problem 1: Line 62
```java
return Optional.ofNullable((S) localSymbols.get(key));
```

**Issue:**
- `localSymbols.get(key)` returns `Symbol` (can be any subtype)
- Unchecked cast to generic type `S` without verification
- `Symbol` → `S` conversion is not type-safe

**Risk:** If actual type doesn't match `S`, ClassCastException at runtime

#### Problem 2: Line 91
```java
.map(s -> (S) s)
```

**Issue:**
- Same pattern in stream mapping
- Unchecked cast without type verification
- Can fail at runtime if symbol type doesn't match expected type

## Fix Strategy

### Option A: Add Type Parameter Constraint (Recommended)

```java
// BEFORE
public Optional<S> lookup(String key) {
    return Optional.ofNullable((S) localSymbols.get(key));
}

// AFTER
public Optional<S> lookup(String key) {
    Symbol symbol = localSymbols.get(key);
    if (symbol == null) {
        return Optional.empty();
    }
    try {
        return Optional.of((S) symbol);
    } catch (ClassCastException e) {
        log.warn("Symbol type mismatch for key: {}", key, e);
        return Optional.empty();
    }
}
```

### Option B: Suppress Warning (If Cast is Verified)

```java
@SuppressWarnings("unchecked")
public Optional<S> lookup(String key) {
    return Optional.ofNullable((S) localSymbols.get(key));
}
```

### Option C: Runtime Type Checking

```java
public Optional<S> lookup(String key) {
    Object obj = localSymbols.get(key);
    if (obj != null && expectedType.isInstance(obj)) {
        return Optional.of((S) obj);
    }
    return Optional.empty();
}
```

## Implementation Plan

1. **Add error handling to Line 62:**
   - Wrap cast in try-catch
   - Return empty Optional on ClassCastException
   - Log warning with context

2. **Add error handling to Line 91:**
   - Similar pattern in map operation
   - Consider using flatMap instead of map

3. **Alternative: Refactor Symbol Hierarchy**
   - If Symbol subclasses are known at compile-time
   - Can use bounded wildcards: `<S extends Symbol>`
   - Compiler can verify type safety

## Testing

After fix, add tests:

```java
@Test
public void testLookupWithWrongType() {
    // Verify that wrong type returns empty
    Optional<String> result = scope.lookup("key");
    assertTrue(result.isEmpty());
}

@Test
public void testLookupWithCorrectType() {
    // Verify correct type is returned
    Optional<Symbol> result = scope.lookup("key");
    assertTrue(result.isPresent());
}
```

## Checklist

- [ ] Scope.java line 62 - Add error handling for cast
- [ ] Scope.java line 91 - Add error handling for cast
- [ ] Add unit tests for type mismatch cases
- [ ] Document expected Symbol subtypes
- [ ] Verify no functional changes after fix

## Priority

**Low** - These warnings indicate type-safe usage in most cases, but proper error handling is recommended for robustness.

## Related Files

- `OrderInvariantValidator.java` - Uses Scope for symbol lookups
- Symbol-related domain classes - May need review of type hierarchy
