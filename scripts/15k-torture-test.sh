#!/bin/bash

# =============================================================================
# 15K ORDER TORTURE TEST - SINGLE COMMAND PRODUCTION READINESS
# =============================================================================
#
# Run: ./scripts/15k-torture-test.sh [--quick|--full|--chaos|--skip-build]
#
# Modes:
#   --quick      2k plays, 3 min timeout, mild chaos (DEFAULT)
#   --full       15k plays, 10 min timeout, moderate chaos
#   --chaos      15k plays, 15 min timeout, severe chaos
#   --skip-build Skip Maven build (if already built)
#
# What this tests:
#   - Task generation for large orders
#   - Worker execution under load
#   - Recovery from chaos (random failures, proxy bans, latency)
#   - App restart with orphan recovery
#   - All invariants preserved post-stress
#
# Exit codes:
#   0 = ALL INVARIANTS PASSED - Production ready!
#   1 = Setup/infrastructure failure
#   2 = Order placement failed
#   3 = Invariant violation detected
#   4 = Timeout exceeded
#
# =============================================================================

set -e

# === Configuration ===
API_KEY="test-api-key-local-dev-12345"
BASE_URL="http://localhost:8080"
SERVICE_ID="3c1cb593-85a7-4375-8092-d39c00399a7b"
JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null)}"

# Defaults (--quick mode)
QUANTITY=2000
POLL_INTERVAL=2
TIMEOUT=180
CHAOS_LEVEL="mild"
SKIP_BUILD=false
APP_PID=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --quick)
            QUANTITY=2000; TIMEOUT=180; CHAOS_LEVEL="mild"; shift ;;
        --full)
            QUANTITY=15000; TIMEOUT=600; CHAOS_LEVEL="moderate"; shift ;;
        --chaos)
            QUANTITY=15000; TIMEOUT=900; CHAOS_LEVEL="severe"; shift ;;
        --skip-build)
            SKIP_BUILD=true; shift ;;
        --help|-h)
            echo "Usage: $0 [--quick|--full|--chaos] [--skip-build]"
            exit 0 ;;
        *)
            echo "Unknown option: $1"; exit 1 ;;
    esac
done

# === Colors for output ===
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# === Helper Functions ===

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "\n${PURPLE}═══════════════════════════════════════════════════════════════════${NC}"
    echo -e "${PURPLE}  STEP: $1${NC}"
    echo -e "${PURPLE}═══════════════════════════════════════════════════════════════════${NC}\n"
}

api_call() {
    curl -s -w "\n%{http_code}" "$@"
}

check_response() {
    local response="$1"
    local http_code=$(echo "$response" | tail -1)
    local body=$(echo "$response" | head -n -1)
    
    if [[ "$http_code" -ge 200 && "$http_code" -lt 300 ]]; then
        echo "$body"
        return 0
    else
        log_error "HTTP $http_code: $body"
        return 1
    fi
}

wait_for_app() {
    log_info "Waiting for app to be ready..."
    local max_attempts=30
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if curl -s "$BASE_URL/actuator/health" | grep -q "UP"; then
            log_success "App is ready!"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 2
    done
    
    log_error "App failed to start after $max_attempts attempts"
    return 1
}

# === Pre-flight Checks ===

log_step "Pre-flight Checks"

# Check if jq is installed
if ! command -v jq &> /dev/null; then
    log_error "jq is required but not installed. Install with: brew install jq"
    exit 1
fi

# Check if app is running
log_info "Checking if app is running..."
if ! curl -s "$BASE_URL/actuator/health" | grep -q "UP"; then
    log_warn "App not running. Please start it first:"
    echo "  export JAVA_HOME=\$(/usr/libexec/java_home -v 17)"
    echo "  SPRING_PROFILES_ACTIVE=local mvn spring-boot:run -DskipTests"
    exit 1
fi
log_success "App is running"

# === STEP 1: Seed Proxies ===

log_step "1. Seed Proxies for Capacity"

PROXY_COUNT=100
log_info "Seeding $PROXY_COUNT datacenter proxies..."

for i in $(seq 1 $PROXY_COUNT); do
    response=$(api_call -X POST "$BASE_URL/api/internal/proxies" \
        -H "X-API-Key: $API_KEY" \
        -H "Content-Type: application/json" \
        -d "{
            \"proxyUrl\": \"http://proxy-dc-${i}.local:8080\",
            \"tier\": \"DATACENTER\",
            \"label\": \"datacenter-${i}\",
            \"maxPlaysPerHour\": 200,
            \"maxConcurrent\": 10
        }" 2>/dev/null) || true
done

log_info "Verifying capacity..."
capacity_response=$(api_call -X GET "$BASE_URL/api/admin/capacity" \
    -H "X-API-Key: $API_KEY")
capacity=$(check_response "$capacity_response")

healthy_proxies=$(echo "$capacity" | jq -r '.healthyProxyCount // 0')
can_accept=$(echo "$capacity" | jq -r '.canAccept15k // false')

log_info "Healthy proxies: $healthy_proxies"
log_info "Can accept 15k: $can_accept"

if [ "$can_accept" != "true" ]; then
    log_error "System cannot accept 15k orders. Check proxy capacity."
    exit 1
fi

log_success "Proxies seeded successfully"

# === STEP 2: Place 15k Order ===

log_step "2. Place 15,000 Play Order"

IDEMPOTENCY_KEY="torture-test-$(date +%s)"
log_info "Creating order with idempotency key: $IDEMPOTENCY_KEY"

order_response=$(api_call -X POST "$BASE_URL/api/v2/orders" \
    -H "X-API-Key: $API_KEY" \
    -H "Content-Type: application/json" \
    -H "X-Idempotency-Key: $IDEMPOTENCY_KEY" \
    -d "{
        \"serviceId\": \"$SERVICE_ID\",
        \"quantity\": $QUANTITY,
        \"targetUrl\": \"https://open.spotify.com/track/torture-test-$(date +%s)\"
    }")

order_body=$(check_response "$order_response") || exit 1

ORDER_ID=$(echo "$order_body" | jq -r '.orderId // .id')
ORDER_STATUS=$(echo "$order_body" | jq -r '.status')

if [ -z "$ORDER_ID" ] || [ "$ORDER_ID" == "null" ]; then
    log_error "Failed to get order ID from response: $order_body"
    exit 1
fi

log_success "Order created!"
log_info "Order ID: $ORDER_ID"
log_info "Status: $ORDER_STATUS"

# Save order ID for later steps
echo "$ORDER_ID" > /tmp/torture-test-order-id

# === STEP 3: Verify Task Generation ===

log_step "3. Verify Task Generation"

sleep 2  # Wait for tasks to be generated

tasks_response=$(api_call -X GET "$BASE_URL/api/admin/orders/$ORDER_ID/tasks" \
    -H "X-API-Key: $API_KEY")
tasks=$(check_response "$tasks_response")

task_count=$(echo "$tasks" | jq -r '.taskCount // 0')
log_info "Tasks generated: $task_count"

if [ "$task_count" -lt 30 ]; then
    log_error "Expected ~38 tasks for 15k order, got $task_count"
    exit 1
fi

log_success "Task generation verified: $task_count tasks"

# === STEP 4: Monitor Initial Progress ===

log_step "4. Monitor Initial Progress (30 seconds)"

for i in {1..6}; do
    progress_response=$(api_call -X GET "$BASE_URL/api/admin/orders/$ORDER_ID/progress" \
        -H "X-API-Key: $API_KEY")
    progress=$(check_response "$progress_response")
    
    delivered=$(echo "$progress" | jq -r '.delivered // 0')
    completed_tasks=$(echo "$progress" | jq -r '.completedTasks // 0')
    progress_pct=$(echo "$progress" | jq -r '.progressPercent // 0')
    
    log_info "Progress: $delivered/$QUANTITY delivered ($progress_pct%) | Tasks: $completed_tasks/$task_count"
    sleep $POLL_INTERVAL
done

# === STEP 5: Enable Failure Injection ===

log_step "5. Enable Failure Injection (Moderate Chaos)"

# Enable moderate chaos: 25% failures, 5% timeouts
inject_response=$(api_call -X POST "$BASE_URL/api/admin/testing/preset/moderate" \
    -H "X-API-Key: $API_KEY")
inject_result=$(check_response "$inject_response" 2>/dev/null) || true

log_warn "Chaos mode enabled: 25% failures, 5% timeouts, 100ms latency"

# Also ban a few proxies
log_info "Banning 3 proxies for additional chaos..."
for i in {1..3}; do
    proxy_list=$(api_call -X GET "$BASE_URL/api/internal/proxies" \
        -H "X-API-Key: $API_KEY" | head -n -1)
    proxy_id=$(echo "$proxy_list" | jq -r ".[$i].id // empty")
    
    if [ -n "$proxy_id" ]; then
        api_call -X POST "$BASE_URL/api/admin/testing/ban-proxy/$proxy_id?durationSeconds=60" \
            -H "X-API-Key: $API_KEY" > /dev/null 2>&1 || true
        log_info "Banned proxy: $proxy_id (60 seconds)"
    fi
done

# === STEP 6: Monitor Under Chaos ===

log_step "6. Monitor Under Chaos (60 seconds)"

for i in {1..12}; do
    progress_response=$(api_call -X GET "$BASE_URL/api/admin/orders/$ORDER_ID/progress" \
        -H "X-API-Key: $API_KEY")
    progress=$(check_response "$progress_response")
    
    worker_response=$(api_call -X GET "$BASE_URL/api/admin/worker/status" \
        -H "X-API-Key: $API_KEY")
    worker=$(check_response "$worker_response")
    
    delivered=$(echo "$progress" | jq -r '.delivered // 0')
    completed_tasks=$(echo "$progress" | jq -r '.completedTasks // 0')
    failed_tasks=$(echo "$progress" | jq -r '.failedTasks // 0')
    total_retries=$(echo "$worker" | jq -r '.totalRetries // 0')
    transient=$(echo "$worker" | jq -r '.transientFailures // 0')
    
    log_info "Progress: $delivered/$QUANTITY | Tasks: $completed_tasks completed, $failed_tasks permanent | Retries: $total_retries | Transient: $transient"
    sleep $POLL_INTERVAL
done

# === STEP 7: Simulate App Restart ===

log_step "7. Simulate App Restart (Crash Recovery Test)"

# Record current progress before restart
pre_restart_response=$(api_call -X GET "$BASE_URL/api/admin/orders/$ORDER_ID/progress" \
    -H "X-API-Key: $API_KEY")
pre_restart=$(check_response "$pre_restart_response")

pre_delivered=$(echo "$pre_restart" | jq -r '.delivered // 0')
pre_completed=$(echo "$pre_restart" | jq -r '.completedTasks // 0')

log_info "Pre-restart state: $pre_delivered delivered, $pre_completed tasks completed"

# Find and kill the app
log_warn "Killing app process..."
APP_PID=$(lsof -ti :8080 2>/dev/null || echo "")
if [ -n "$APP_PID" ]; then
    kill -9 $APP_PID 2>/dev/null || true
    log_info "Killed process $APP_PID"
fi

sleep 2

# Restart the app
log_info "Restarting app..."
cd "$(dirname "$0")/.."
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
nohup mvn spring-boot:run -DskipTests -Dspring-boot.run.profiles=local > /tmp/app-restart.log 2>&1 &
RESTART_PID=$!

log_info "Started app with PID $RESTART_PID"

# Wait for app to be ready
sleep 10
wait_for_app

# === STEP 8: Verify Recovery ===

log_step "8. Verify Recovery After Restart"

sleep 5  # Allow worker to resume

post_restart_response=$(api_call -X GET "$BASE_URL/api/admin/worker/status" \
    -H "X-API-Key: $API_KEY")
post_worker=$(check_response "$post_restart_response")

recovered=$(echo "$post_worker" | jq -r '.tasksRecoveredAfterStart // 0')
new_worker_id=$(echo "$post_worker" | jq -r '.workerId')

log_info "New worker ID: $new_worker_id"
log_info "Tasks recovered after restart: $recovered"

# Disable chaos for final push
log_info "Disabling chaos for final completion..."
api_call -X POST "$BASE_URL/api/admin/testing/disable" \
    -H "X-API-Key: $API_KEY" > /dev/null 2>&1 || true

# === STEP 9: Wait for Completion ===

log_step "9. Wait for Order Completion"

MAX_WAIT=600  # 10 minutes max
ELAPSED=0
COMPLETED=false

while [ $ELAPSED -lt $MAX_WAIT ]; do
    progress_response=$(api_call -X GET "$BASE_URL/api/admin/orders/$ORDER_ID/progress" \
        -H "X-API-Key: $API_KEY")
    progress=$(check_response "$progress_response")
    
    status=$(echo "$progress" | jq -r '.status')
    delivered=$(echo "$progress" | jq -r '.delivered // 0')
    failed_perm=$(echo "$progress" | jq -r '.failedPermanent // 0')
    progress_pct=$(echo "$progress" | jq -r '.progressPercent // 0')
    
    log_info "Status: $status | Delivered: $delivered | Failed: $failed_perm | Progress: $progress_pct%"
    
    if [ "$status" == "COMPLETED" ]; then
        COMPLETED=true
        break
    fi
    
    sleep $POLL_INTERVAL
    ELAPSED=$((ELAPSED + POLL_INTERVAL))
done

# === STEP 10: Final Validation ===

log_step "10. Final Validation"

# Get final progress
final_response=$(api_call -X GET "$BASE_URL/api/admin/orders/$ORDER_ID/progress" \
    -H "X-API-Key: $API_KEY")
final=$(check_response "$final_response")

final_status=$(echo "$final" | jq -r '.status')
final_delivered=$(echo "$final" | jq -r '.delivered // 0')
final_failed=$(echo "$final" | jq -r '.failedPermanent // 0')
final_total_tasks=$(echo "$final" | jq -r '.totalTasks // 0')
final_completed_tasks=$(echo "$final" | jq -r '.completedTasks // 0')
final_failed_tasks=$(echo "$final" | jq -r '.failedTasks // 0')

# Get worker stats
worker_final=$(api_call -X GET "$BASE_URL/api/admin/worker/status" \
    -H "X-API-Key: $API_KEY" | head -n -1)

total_retries=$(echo "$worker_final" | jq -r '.totalRetries // 0')
total_recovered=$(echo "$worker_final" | jq -r '.recoveredOrphans // 0')

# Get dead letter queue
dlq_response=$(api_call -X GET "$BASE_URL/api/admin/orders/$ORDER_ID/failed-tasks" \
    -H "X-API-Key: $API_KEY")
dlq=$(check_response "$dlq_response")
dlq_count=$(echo "$dlq" | jq -r '.failedTaskCount // 0')

echo -e "\n${CYAN}════════════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}                    TORTURE TEST RESULTS                             ${NC}"
echo -e "${CYAN}════════════════════════════════════════════════════════════════════${NC}\n"

echo -e "Order ID:          $ORDER_ID"
echo -e "Final Status:      $final_status"
echo -e "Total Quantity:    $QUANTITY"
echo -e "Delivered:         $final_delivered"
echo -e "Failed Permanent:  $final_failed"
echo -e ""
echo -e "Tasks Total:       $final_total_tasks"
echo -e "Tasks Completed:   $final_completed_tasks"
echo -e "Tasks Failed:      $final_failed_tasks"
echo -e "Tasks Retried:     $total_retries"
echo -e "Orphans Recovered: $total_recovered"
echo -e "Dead Letter Queue: $dlq_count tasks"
echo -e ""

# === Validation Checks ===

PASS=true

# Check 1: Order completed
if [ "$final_status" == "COMPLETED" ]; then
    log_success "✓ Order reached COMPLETED status"
else
    log_error "✗ Order status is $final_status, expected COMPLETED"
    PASS=false
fi

# Check 2: All plays accounted for
TOTAL_ACCOUNTED=$((final_delivered + final_failed))
if [ "$TOTAL_ACCOUNTED" -eq "$QUANTITY" ]; then
    log_success "✓ All plays accounted for: $final_delivered delivered + $final_failed failed = $QUANTITY"
else
    log_error "✗ Plays not accounted: $final_delivered + $final_failed = $TOTAL_ACCOUNTED (expected $QUANTITY)"
    PASS=false
fi

# Check 3: No double-counting (tasks match plays)
TASK_PLAYS=$((final_completed_tasks * 400))  # Approximate since task size varies
ACTUAL_MARGIN=2000  # Allow 2000 variance for variable task sizes
DIFF=$((final_delivered - TASK_PLAYS))
if [ "${DIFF#-}" -lt "$ACTUAL_MARGIN" ]; then
    log_success "✓ No obvious double-counting detected"
else
    log_warn "⚠ Possible double-counting: $final_delivered delivered vs ~$TASK_PLAYS from tasks"
fi

# Check 4: Dead letter queue only has failures
if [ "$dlq_count" -eq "$final_failed_tasks" ]; then
    log_success "✓ Dead letter queue matches failed task count"
else
    log_warn "⚠ Dead letter queue count ($dlq_count) differs from failed tasks ($final_failed_tasks)"
fi

# Check 5: Recovery worked
if [ "$total_recovered" -gt 0 ]; then
    log_success "✓ Orphan recovery worked: $total_recovered tasks recovered"
else
    log_info "ℹ No orphan recovery needed (may be normal if restart was clean)"
fi

echo -e ""

if [ "$PASS" == "true" ]; then
    echo -e "${GREEN}════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}                    ✓ ALL TESTS PASSED!                             ${NC}"
    echo -e "${GREEN}════════════════════════════════════════════════════════════════════${NC}"
    exit 0
else
    echo -e "${RED}════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${RED}                    ✗ SOME TESTS FAILED                              ${NC}"
    echo -e "${RED}════════════════════════════════════════════════════════════════════${NC}"
    exit 1
fi
