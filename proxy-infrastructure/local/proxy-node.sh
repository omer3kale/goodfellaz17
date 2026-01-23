#!/bin/bash
# =============================================================================
# Phase 3: Enhanced Proxy Node with Failure Injection & Play Counting
# =============================================================================
#
# Features:
#   - Stateful play counting per orderId (proves delivery)
#   - Configurable failure injection (tests retry logic)
#   - Configurable latency simulation (tests timeout handling)
#   - Stats endpoint for thesis evidence
#
# Usage:
#   ./proxy-node.sh [port] [--failure-rate=0.1] [--delay-ms=50]
#
# Examples:
#   ./proxy-node.sh 9090                          # No failures, no delay
#   ./proxy-node.sh 9090 --failure-rate=0.1       # 10% random failures
#   ./proxy-node.sh 9090 --failure-rate=0.2 --delay-ms=100  # 20% failures + 100ms latency
#
# Endpoints:
#   GET  /health  → {"status": "ONLINE", "nodeId": "...", "failureRate": 0.1}
#   POST /execute → Execute task, maybe fail, return result
#   GET  /stats   → {"totalRequests": N, "successes": M, "failures": F, "orders": {...}}
#
# Contract (unchanged from Phase 3a):
#   POST /execute accepts: {taskId, orderId, plays, trackUrl?, proxyId?}
#   Returns: {success: boolean, taskId, plays, nodeId, message, totalDelivered?}
#
# =============================================================================

# Parse arguments
PORT=${1:-9090}
FAILURE_RATE=0.0
DELAY_MS=0

for arg in "$@"; do
    case $arg in
        --failure-rate=*)
            FAILURE_RATE="${arg#*=}"
            ;;
        --delay-ms=*)
            DELAY_MS="${arg#*=}"
            ;;
    esac
done

LOG_DIR="$(dirname "$0")"
LOG_FILE="$LOG_DIR/requests.log"

echo "=========================================="
echo " GoodFellaz17 Enhanced Proxy Node"
echo "=========================================="
echo " Port:         $PORT"
echo " Failure Rate: $FAILURE_RATE (0.0 = no failures)"
echo " Delay:        ${DELAY_MS}ms"
echo " Log:          $LOG_FILE"
echo "=========================================="
echo ""
echo "Endpoints:"
echo "  GET  /health  → Node status + config"
echo "  POST /execute → Execute task (with chaos)"
echo "  GET  /stats   → Cumulative statistics"
echo ""
echo "Starting..."

# Check if Python is available
if ! command -v python3 &> /dev/null; then
    echo "ERROR: Python3 not found."
    exit 1
fi

# Start the Python server with arguments
python3 - "$PORT" "$FAILURE_RATE" "$DELAY_MS" "$LOG_FILE" << 'PYEOF'
import http.server
import json
import sys
import os
import time
import random
from datetime import datetime
from uuid import uuid4
from collections import defaultdict

# Parse arguments
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 9090
FAILURE_RATE = float(sys.argv[2]) if len(sys.argv) > 2 else 0.0
DELAY_MS = int(sys.argv[3]) if len(sys.argv) > 3 else 0
LOG_FILE = sys.argv[4] if len(sys.argv) > 4 else "requests.log"

NODE_ID = str(uuid4())[:8]

# Stateful tracking
stats = {
    "totalRequests": 0,
    "successes": 0,
    "failures": 0,
    "totalPlaysDelivered": 0,
    "orders": defaultdict(lambda: {"tasks": 0, "delivered": 0, "failed": 0})
}

class ProxyHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass  # Suppress default logging
    
    def log_request_to_file(self, task_data, status_code, result):
        """Log to requests.log in JSONL format"""
        try:
            with open(LOG_FILE, 'a') as f:
                entry = {
                    "timestamp": datetime.now().isoformat(),
                    "nodeId": NODE_ID,
                    "method": self.command,
                    "path": self.path,
                    "status": status_code,
                    "task": task_data,
                    "result": result,
                    "config": {"failureRate": FAILURE_RATE, "delayMs": DELAY_MS}
                }
                f.write(json.dumps(entry) + "\n")
        except Exception as e:
            print(f"[ERROR] Failed to log: {e}")
    
    def should_fail(self):
        """Determine if this request should fail based on failure rate"""
        return random.random() < FAILURE_RATE
    
    def apply_delay(self):
        """Apply configured latency"""
        if DELAY_MS > 0:
            time.sleep(DELAY_MS / 1000.0)
    
    def do_GET(self):
        if self.path == '/health':
            self.send_json(200, {
                "status": "ONLINE",
                "nodeId": NODE_ID,
                "port": PORT,
                "failureRate": FAILURE_RATE,
                "delayMs": DELAY_MS
            })
        elif self.path == '/stats':
            # Convert defaultdict to regular dict for JSON serialization
            stats_copy = dict(stats)
            stats_copy["orders"] = dict(stats["orders"])
            stats_copy["nodeId"] = NODE_ID
            stats_copy["uptime"] = datetime.now().isoformat()
            self.send_json(200, stats_copy)
        else:
            self.send_json(404, {"error": "Not found"})
    
    def do_POST(self):
        if self.path == '/execute':
            self.handle_execute()
        else:
            self.send_json(404, {"error": "Not found"})
    
    def handle_execute(self):
        """Handle task execution with failure injection"""
        stats["totalRequests"] += 1
        
        # Parse request body
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length)
        
        try:
            task = json.loads(body.decode()) if body else {}
        except json.JSONDecodeError:
            task = {"raw": body.decode()}
        
        task_id = task.get("taskId", "unknown")
        order_id = task.get("orderId", "unknown")
        plays = task.get("plays", 0)
        
        # Apply configured delay
        self.apply_delay()
        
        # Check for failure injection
        if self.should_fail():
            stats["failures"] += 1
            stats["orders"][order_id]["failed"] += 1
            
            result = {
                "success": False,
                "nodeId": NODE_ID,
                "taskId": task_id,
                "plays": 0,
                "message": "Simulated network timeout (chaos injection)"
            }
            
            self.log_request_to_file(task, 500, result)
            print(f"[{NODE_ID}] FAIL (injected): taskId={task_id} orderId={order_id}")
            
            # Return 200 with success=false (let Java side handle retry)
            self.send_json(200, result)
            return
        
        # Success path: increment counters
        stats["successes"] += 1
        stats["totalPlaysDelivered"] += plays
        stats["orders"][order_id]["tasks"] += 1
        stats["orders"][order_id]["delivered"] += plays
        
        result = {
            "success": True,
            "nodeId": NODE_ID,
            "taskId": task_id,
            "plays": plays,
            "message": "Task executed successfully",
            "totalDelivered": stats["orders"][order_id]["delivered"]
        }
        
        self.log_request_to_file(task, 200, result)
        print(f"[{NODE_ID}] SUCCESS: taskId={task_id} orderId={order_id} plays={plays} total={result['totalDelivered']}")
        
        self.send_json(200, result)
    
    def send_json(self, status, data):
        """Helper to send JSON response"""
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

# Start server
print(f"[{NODE_ID}] Listening on port {PORT}")
print(f"[{NODE_ID}] Failure rate: {FAILURE_RATE*100:.1f}%")
print(f"[{NODE_ID}] Delay: {DELAY_MS}ms")
print(f"[{NODE_ID}] Health: http://localhost:{PORT}/health")
print(f"[{NODE_ID}] Stats:  http://localhost:{PORT}/stats")

try:
    http.server.HTTPServer(('', PORT), ProxyHandler).serve_forever()
except KeyboardInterrupt:
    print(f"\n[{NODE_ID}] Shutting down...")
PYEOF
