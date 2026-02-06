#!/bin/sh
set -e

# Default port if not set
PORT="${PORT:-8080}"

# Run the JVM as PID 1 with proper signal forwarding
exec java $JAVA_OPTS -Dserver.port="$PORT" -jar /app/app.jar
