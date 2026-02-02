# Issue 005: Resource Leaks

**Severity:** High (Potential Resource Exhaustion)
**Type:** Compiler Warning
**Count:** 2 occurrences
**Impact:** Database connections, file handles not closed

## Files Affected

### IntegrationTestBase.java

**Location:** `src/test/java/com/goodfellaz17/order/integration/IntegrationTestBase.java`

**Line 21:**
```java
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
```

**Issue:**
- `PostgreSQLContainer` implements `Closeable`
- Assigned to static field but never explicitly closed
- Testcontainers manages lifecycle, but explicit closure recommended

**Impact:**
- Database connections may not be properly released
- Resource pool exhaustion if tests run repeatedly
- Potential port binding issues on subsequent test runs

### GoodFellaz17IntegrationTest.java

**Location:** `src/test/java/com/goodfellaz/integration/GoodFellaz17IntegrationTest.java`

**Line 62:**
```java
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
```

**Same Issue:** PostgreSQLContainer not explicitly closed

## Root Cause

Both test classes use Testcontainers without proper lifecycle management:

```java
// PROBLEMATIC PATTERN
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
// No cleanup in @AfterAll or shutdown hook
```

## Solutions

### Solution 1: Use @ClassRule / @Container Annotation (Recommended for JUnit 4)

```java
// For JUnit 4
@ClassRule
public static final PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>("postgres:16-alpine")
    .withDatabaseName("test_db")
    .withUsername("test_user")
    .withPassword("test_pass");
```

### Solution 2: Use @TestcontainersTest Annotation (JUnit 5+)

```java
@Testcontainers
@SpringBootTest
public class IntegrationTestBase {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("test_db");
}
```

### Solution 3: Manual Lifecycle Management

```java
@BeforeAll
static void startDatabase() {
    postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    postgres.start();
}

@AfterAll
static void stopDatabase() {
    if (postgres != null && postgres.isRunning()) {
        postgres.stop();
    }
}
```

### Solution 4: Try-with-Resources (For Local Testing)

```java
try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
    postgres.start();
    // Run tests
} // Auto-closes
```

## Implementation Steps

### Step 1: Add Testcontainers Annotation Support

Check pom.xml for dependency:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
```

### Step 2: Update IntegrationTestBase.java

```java
@Testcontainers
public abstract class IntegrationTestBase {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("test_db")
        .withUsername("test_user")
        .withPassword("test_pass")
        .withInitScript("test-schema.sql");

    @BeforeAll
    static void setup() {
        // Database is auto-started by @Container
    }
}
```

### Step 3: Update GoodFellaz17IntegrationTest.java

```java
@Testcontainers
@SpringBootTest
public class GoodFellaz17IntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
        .withDatabaseName("goodfellaz17_test")
        .withInitScript("test-init.sql");

    // Test methods...
}
```

### Step 4: Add Graceful Shutdown

```java
@BeforeEach
void beforeEach() {
    // Ensure container is running
    if (!postgres.isRunning()) {
        postgres.start();
    }
}

@AfterAll
static void cleanup() {
    if (postgres != null && postgres.isRunning()) {
        postgres.stop();
    }
}
```

## Verification

After fix:
```bash
# Run tests multiple times - should not fail on port binding
mvn clean test -DskipITs

# Check for resource leaks
mvn clean test -Dtest=IntegrationTestBase

# Monitor Docker containers
docker ps | grep postgres
# Should show 0 hanging containers after tests complete
```

## Best Practices

1. **Always Use @Container Annotation**
   - Automatic lifecycle management
   - Proper shutdown on test failure
   - Thread-safe

2. **Use DockerImageName**
   - For version flexibility
   - Better error messages

3. **Configure Database**
   - Set database name
   - Set credentials explicitly
   - Add init scripts for schema

4. **Add Timeout**
   ```java
   @Container
   static PostgreSQLContainer<?> postgres =
       new PostgreSQLContainer<>("postgres:16-alpine")
       .withStartupTimeout(Duration.ofSeconds(60));
   ```

## Checklist

- [ ] Add @Testcontainers to IntegrationTestBase
- [ ] Add @Container annotation to postgres field
- [ ] Remove manual lifecycle management (if any)
- [ ] Add database configuration (name, user, password)
- [ ] Update GoodFellaz17IntegrationTest similarly
- [ ] Run tests locally: `mvn test`
- [ ] Verify Docker cleanup: `docker ps | grep postgres`
- [ ] Check for resource leaks in CI logs

## Priority

**High** - Resource leaks can cause test flakiness and CI failures

## Related

- Testcontainers Documentation: https://www.testcontainers.org/
- JUnit 5 Integration: https://www.testcontainers.org/test_framework_integration/junit_5/
