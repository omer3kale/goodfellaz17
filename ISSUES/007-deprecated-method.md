# Issue 007: Deprecated Method Usage

**Severity:** Low-Medium (Deprecation Warning)
**Type:** Compiler Warning
**Count:** 1 occurrence
**Impact:** May be removed in future versions

## File Affected

### OrderResponse.java

**Location:** `src/main/java/com/goodfellaz17/application/dto/generated/OrderResponse.java`

**Line 68:**
```java
entity.getCost(),
```

**Issue:**
- `OrderEntity.getCost()` method is marked as `@Deprecated`
- Indicates this method may be removed in a future version
- Code should migrate to the replacement method

## Analysis

### What is OrderEntity.getCost()?

The `getCost()` method is likely a field getter in OrderEntity:

```java
@Entity
public class OrderEntity {
    private BigDecimal cost;

    @Deprecated(since = "1.1.0", forRemoval = true)
    public BigDecimal getCost() {
        return cost;
    }

    // New method (if replacement exists)
    public BigDecimal getTotalCost() {
        return cost;
    }
}
```

## Solutions

### Solution 1: Use Replacement Method (If Available)

```java
// BEFORE (OrderResponse.java line 68)
entity.getCost(),

// AFTER - Use alternative method if it exists
entity.getTotalCost(),
// or
entity.calculateCost(),
// or
entity.getPrice(),
```

**Steps:**
1. Check OrderEntity class for alternative methods
2. Look for methods with similar names:
   - `getTotalCost()`
   - `getPrice()`
   - `getAmount()`
   - `calculateCost()`
3. Use the recommended replacement

### Solution 2: Suppress Warning (Temporary)

```java
@SuppressWarnings("deprecation")
public class OrderResponse {
    // ...
}
```

**Only if:**
- No replacement method exists
- Plan to refactor when replacement becomes available

### Solution 3: Calculate Cost Differently

```java
// BEFORE
entity.getCost()

// AFTER - Compute from other fields if available
entity.getQuantity() * entity.getUnitPrice()
```

## Implementation Steps

1. **Check OrderEntity source:**
   ```bash
   grep -n "getCost\|getTotalCost\|getPrice\|getAmount" \
     src/main/java/com/goodfellaz17/domain/model/generated/OrderEntity.java
   ```

2. **Identify replacement method:**
   - Look for `@Deprecated` javadoc or annotation
   - Find the recommended alternative

3. **Update OrderResponse.java line 68:**
   ```java
   // Replace getCost() call with appropriate alternative
   ```

4. **Verify compilation:**
   ```bash
   mvn clean compile -DskipTests
   # Should show: 0 deprecation warnings for this file
   ```

## Context

OrderResponse is a DTO (Data Transfer Object) that converts OrderEntity to API response:

```java
@Data
@AllArgsConstructor
public class OrderResponse {
    private UUID id;
    private String trackId;
    private Integer quantity;
    private BigDecimal cost;  // Deprecated source
    private String status;
    private LocalDateTime createdAt;

    // Factory method that converts entity to response
    public static OrderResponse from(OrderEntity entity) {
        return new OrderResponse(
            entity.getId(),
            entity.getTrackId(),
            entity.getQuantity(),
            entity.getCost(),  // LINE 68 - DEPRECATED
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }
}
```

## Decision Tree

```
┌─ Is there a replacement method in OrderEntity?
│
├─ YES:
│  └─ Replace getCost() with replacement
│     └─ Run: mvn clean compile
│
└─ NO:
   ├─ Is cost field needed in API response?
   │
   ├─ YES:
   │  └─ Calculate from other fields (qty × unitPrice)
   │
   └─ NO:
      └─ Remove cost field from OrderResponse DTO
```

## Checklist

- [ ] Examine OrderEntity class for `getCost()` deprecation message
- [ ] Find replacement method name (if available)
- [ ] Update OrderResponse.java line 68 with replacement
- [ ] Compile: `mvn clean compile -DskipTests`
- [ ] Verify: No deprecation warnings remain
- [ ] Test: Ensure API still returns expected response

## Priority

**Low** - Warning doesn't affect functionality, but should be fixed before replacement is removed

## Prevention

For future code:
- Always check `@Deprecated` annotations
- Use IDE warnings to catch deprecations early
- Configure Maven to fail on deprecation warnings in CI:
  ```xml
  <compilerArgument>-Werror</compilerArgument>
  <compilerArgument>-Xlint:deprecation</compilerArgument>
  ```

## Related

- OrderEntity class definition
- OrderResponse DTO conversions
- API contract documentation
