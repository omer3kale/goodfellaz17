#!/bin/bash
# Container-native database access helper for GoodFellaz17
# Eliminates Colima port forwarding issues by using docker exec directly
# Usage:
#   source scripts/dbpsql.sh
#   dbpsql -c "SELECT COUNT(*) FROM orders;"
#   dbpsql < /tmp/query.sql
#   dbconn "SELECT 1"  # alias for convenience

set -euo pipefail

# Absolute path handling for Colima socket
COLIMA_SOCKET="${HOME}/.colima/default/docker.sock"
CONTAINER_NAME="goodfellaz17-postgres"
DB_USER="goodfellaz17"
DB_NAME="goodfellaz17"

dbpsql() {
  if [ ! -S "$COLIMA_SOCKET" ]; then
    echo "Error: Colima socket not found at $COLIMA_SOCKET"
    echo "Make sure Colima is running: colima start"
    return 1
  fi

  # Pass all arguments directly to psql inside the container
  # This includes -c for commands, <, and all standard psql flags
  docker exec -i "$CONTAINER_NAME" \
    psql -U "$DB_USER" -d "$DB_NAME" "$@"
}

# Convenience alias for one-liners
dbconn() {
  dbpsql -c "$@"
}

# Check container is running
dbhealth() {
  if [ ! -S "$COLIMA_SOCKET" ]; then
    echo "Error: Colima socket not found"
    return 1
  fi

  echo "=== Container Status ==="
  docker ps --filter "name=$CONTAINER_NAME" --format "table {{.Names}}\t{{.Status}}" || echo "Container not found"

  echo ""
  echo "=== Database Health Check ==="
  dbpsql -c "SELECT 'Database is healthy' as status, now() as timestamp;"
}

# Export functions for use in shell
export -f dbpsql
export -f dbconn
export -f dbhealth
