# Issue 001: Unused Imports

**Severity:** Low (Code Quality)
**Type:** Compiler Warning
**Count:** 12 occurrences
**Impact:** Code cleanliness, IDE noise

## Files Affected

### AdminOrderProgressController.java
- Line 8: `import com.goodfellaz17.domain.model.generated.TaskStatus;`
  - **Status:** UNUSED - Can be safely removed

### CheckoutController.java
- Line 8: `import io.swagger.v3.oas.annotations.Operation;`
- Line 9: `import io.swagger.v3.oas.annotations.Parameter;`
- Line 10: `import io.swagger.v3.oas.annotations.responses.ApiResponse;`
  - **Status:** UNUSED - Swagger annotations not used

### CustomerDashboardController.java
- Line 3: `import com.goodfellaz17.infrastructure.persistence.entity.ApiKeyEntity;`
  - **Status:** UNUSED - Can be safely removed

### PublicApiController.java
- Line 20: `import java.time.Instant;`
  - **Status:** UNUSED - Instant type not used

### GeneratedDeviceNodeRepository.java
- Line 10: `import java.time.Instant;`
  - **Status:** UNUSED - Instant type not used

### ServiceEntity.java
- Line 16: `import java.util.List;`
  - **Status:** UNUSED - List type not used

### OrderOrchestratorTest.java
- Line 18: `import java.time.Instant;`
  - **Status:** UNUSED - Instant type not used

### CapacityAdminController.java
- Line 4: `import com.goodfellaz17.application.service.CapacityService.CapacitySnapshot;`
- Line 5: `import com.goodfellaz17.application.service.CapacityService.CanAcceptResult;`
  - **Status:** UNUSED - Inner classes not imported

### CapacityService.java
- Line 3: `import com.goodfellaz17.domain.model.generated.OrderEntity;`
- Line 14: `import reactor.core.publisher.Flux;`
  - **Status:** UNUSED - OrderEntity and Flux not used

### GoodFellaz17IntegrationTest.java
- Line 14: `import com.goodfellaz17.infrastructure.proxy.generated.HybridProxyRouterV2;`
  - **Status:** UNUSED - HybridProxyRouterV2 not imported

## Fix Strategy

**Method 1: IDE Auto-Fix**
- Most IDEs can remove unused imports automatically
- VS Code: Right-click → "Remove unused imports"
- IntelliJ: Code → Optimize Imports

**Method 2: Maven Plugin**
- Add maven-enforcer-plugin to catch unused imports in CI

## Quick Fix Commands

```bash
# Check all unused imports in each file
mvn compile -DskipTests

# Then manually remove or use IDE quickfix
```

## Priority
- **Quick Win:** Yes - Can be fixed in 5 minutes
- **No Behavior Change:** Yes - Safe to remove
- **Automated:** Can be fixed with IDE tools

## Checklist
- [ ] AdminOrderProgressController.java - Remove TaskStatus import
- [ ] CheckoutController.java - Remove Swagger imports
- [ ] CustomerDashboardController.java - Remove ApiKeyEntity import
- [ ] PublicApiController.java - Remove Instant import
- [ ] GeneratedDeviceNodeRepository.java - Remove Instant import
- [ ] ServiceEntity.java - Remove List import
- [ ] OrderOrchestratorTest.java - Remove Instant import
- [ ] CapacityAdminController.java - Remove CapacitySnapshot/CanAcceptResult imports
- [ ] CapacityService.java - Remove OrderEntity/Flux imports
- [ ] GoodFellaz17IntegrationTest.java - Remove HybridProxyRouterV2 import
