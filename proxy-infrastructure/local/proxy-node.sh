#!/bin/bash
# Phase 3a: Simple HTTP proxy node for local development
# 
# This stub will be replaced with a real implementation.
# For now, it demonstrates the contract:
# - Starts on specified port (default 9090)
# - Responds to /health with status JSON
# - Logs all requests to requests.log
#
# Usage: ./proxy-node.sh [port]
# Example: ./proxy-node.sh 9090

PORT=${1:-9090}
LOG_DIR="$(dirname "$0")"
LOG_FILE="$LOG_DIR/requests.log"

echo "=========================================="
echo " GoodFellaz17 Local Proxy Node"
echo " Port: $PORT"
echo " Log:  $LOG_FILE"
echo "=========================================="
echo ""
echo "Contract:"
echo "  GET  /health  → {\"status\": \"ONLINE\", \"nodeId\": \"...\"}"
echo "  POST /execute → Accept task, route to Spotify, return result"
echo ""
echo "Starting..."

# Check if Python is available for simple HTTP server
if command -v python3 &> /dev/null; then
    echo "Using Python HTTP server..."
    
    # Create a simple Python server that logs requests
    python3 << 'PYEOF'
import http.server
import json
import sys
import os
from datetime import datetime
from uuid import uuid4

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 9090
NODE_ID = str(uuid4())[:8]
LOG_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "requests.log")

class ProxyHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # Suppress default logging to stderr
        pass
    
    def log_task(self, task_data, status_code, result):
        """Log task execution details to requests.log"""
        with open(LOG_FILE, 'a') as f:
            entry = {
                "timestamp": datetime.now().isoformat(),
                "nodeId": NODE_ID,
                "method": self.command,
                "path": self.path,
                "status": status_code,
                "task": task_data,
                "result": result
            }
            f.write(json.dumps(entry) + "\n")
    
    def do_GET(self):
        if self.path == '/health':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = {"status": "ONLINE", "nodeId": NODE_ID, "port": PORT}
            self.wfile.write(json.dumps(response).encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def do_POST(self):
        if self.path == '/execute':
            content_length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(content_length)
            
            # Parse the task
            try:
                task = json.loads(body.decode()) if body else {}
            except json.JSONDecodeError:
                task = {"raw": body.decode()}
            
            # TODO: Actually execute the Spotify request
            # For now, simulate success with task echo
            result = {
                "success": True,
                "nodeId": NODE_ID,
                "taskId": task.get("taskId", "unknown"),
                "plays": task.get("plays", 0),
                "message": "Task executed (stub)"
            }
            
            # Log the task with full details
            self.log_task(task, 200, result)
            print(f"[ProxyNode {NODE_ID}] EXECUTE: taskId={task.get('taskId')} plays={task.get('plays')} → SUCCESS")
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(result).encode())
        else:
            self.send_response(404)
            self.end_headers()

print(f"[ProxyNode {NODE_ID}] Listening on port {PORT}")
print(f"[ProxyNode {NODE_ID}] Health: http://localhost:{PORT}/health")
http.server.HTTPServer(('', PORT), ProxyHandler).serve_forever()
PYEOF

else
    echo "ERROR: Python3 not found. Install Python or use Node.js implementation."
    exit 1
fi
