#!/usr/bin/env bash
# =============================================================================
# GOODFELLAZ17 - Application User Password Management
# =============================================================================
# Sets the password for the spotifybot_app database user.
# This script does NOT create the user; that's handled by V11__Create_App_User.sql
#
# Usage: ./infra/setup-app-user.sh 'YourStrongPasswordHere'
#
# After running:
#   1. Update application-local-selfhosted.yml with the same password
#   2. Or set SPOTIFYBOT_APP_PASSWORD environment variable
#   3. Restart the Spring Boot application
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Configuration
# -----------------------------------------------------------------------------
CONTAINER_NAME="goodfellaz17-postgres"
DB_NAME="spotifybot"
DB_ADMIN_USER="spotifybot_admin"
APP_USER="spotifybot_app"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# -----------------------------------------------------------------------------
# Usage
# -----------------------------------------------------------------------------
usage() {
    echo "Usage: $0 <new_password>"
    echo ""
    echo "Sets the password for the ${APP_USER} database role."
    echo ""
    echo "Arguments:"
    echo "  new_password    The new password for ${APP_USER}"
    echo "                  Must be at least 12 characters"
    echo ""
    echo "Example:"
    echo "  $0 'MyStr0ngP@ssw0rd2026!'"
    echo ""
    echo "After running:"
    echo "  1. Update src/main/resources/application-local-selfhosted.yml"
    echo "  2. Or set environment variable: export SPOTIFYBOT_APP_PASSWORD='...'"
    echo "  3. Restart the application"
    exit 1
}

# -----------------------------------------------------------------------------
# Validation
# -----------------------------------------------------------------------------
validate_input() {
    if [[ $# -lt 1 ]]; then
        echo -e "${RED}Error: No password provided${NC}"
        echo ""
        usage
    fi

    local password="$1"

    # Check minimum length
    if [[ ${#password} -lt 12 ]]; then
        echo -e "${RED}Error: Password must be at least 12 characters${NC}"
        exit 1
    fi

    # Check for weak patterns
    if [[ "${password}" == "password" ]] || [[ "${password}" == "CHANGE_ME_IMMEDIATELY" ]]; then
        echo -e "${RED}Error: Password is too weak${NC}"
        exit 1
    fi
}

# -----------------------------------------------------------------------------
# Pre-flight Checks
# -----------------------------------------------------------------------------
preflight_checks() {
    echo "Running preflight checks..."

    # Check Docker
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}Error: Docker is not installed${NC}"
        exit 1
    fi

    # Check container is running
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        echo -e "${RED}Error: Container '${CONTAINER_NAME}' is not running${NC}"
        echo ""
        echo "Start the database first:"
        echo "  docker compose -f infra/docker-compose.db.yml up -d"
        exit 1
    fi

    # Check if role exists
    local role_exists
    role_exists=$(docker exec "${CONTAINER_NAME}" psql -U "${DB_ADMIN_USER}" -d "${DB_NAME}" \
        -t -c "SELECT 1 FROM pg_roles WHERE rolname = '${APP_USER}';" 2>/dev/null | tr -d ' ')

    if [[ "${role_exists}" != "1" ]]; then
        echo -e "${RED}Error: Role '${APP_USER}' does not exist${NC}"
        echo ""
        echo "Run the migration first:"
        echo "  docker exec -i ${CONTAINER_NAME} psql -U ${DB_ADMIN_USER} -d ${DB_NAME} \\"
        echo "    < src/main/resources/db/migration/V11__Create_App_User.sql"
        exit 1
    fi

    echo -e "${GREEN}✓ Preflight checks passed${NC}"
}

# -----------------------------------------------------------------------------
# Set Password
# -----------------------------------------------------------------------------
set_password() {
    local password="$1"

    echo ""
    echo "Setting password for ${APP_USER}..."

    # Escape single quotes in password for SQL
    local escaped_password="${password//\'/\'\'}"

    if docker exec "${CONTAINER_NAME}" psql -U "${DB_ADMIN_USER}" -d "${DB_NAME}" \
        -c "ALTER ROLE ${APP_USER} WITH PASSWORD '${escaped_password}';" 2>/dev/null; then
        echo -e "${GREEN}✓ Password updated successfully${NC}"
    else
        echo -e "${RED}Error: Failed to update password${NC}"
        exit 1
    fi
}

# -----------------------------------------------------------------------------
# Post-update Instructions
# -----------------------------------------------------------------------------
print_instructions() {
    echo ""
    echo -e "${YELLOW}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${YELLOW}║                    NEXT STEPS                                ║${NC}"
    echo -e "${YELLOW}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo "1. Update the Spring profile with the new password:"
    echo ""
    echo "   Edit: src/main/resources/application-local-selfhosted.yml"
    echo ""
    echo "   spring:"
    echo "     r2dbc:"
    echo "       url: r2dbc:postgresql://localhost:5432/spotifybot"
    echo "       username: spotifybot_app"
    echo "       password: <YOUR_NEW_PASSWORD>"
    echo ""
    echo "   OR use environment variable:"
    echo ""
    echo "   export SPOTIFYBOT_APP_PASSWORD='<YOUR_NEW_PASSWORD>'"
    echo ""
    echo "2. Restart the application:"
    echo ""
    echo "   SPRING_PROFILES_ACTIVE=local-selfhosted mvn spring-boot:run"
    echo ""
    echo "3. Verify connection:"
    echo ""
    echo "   curl http://localhost:8080/actuator/health"
    echo ""
    echo -e "${GREEN}Password rotation complete!${NC}"
}

# -----------------------------------------------------------------------------
# Test Connection
# -----------------------------------------------------------------------------
test_connection() {
    local password="$1"

    echo ""
    echo "Testing connection with new credentials..."

    # Escape for PGPASSWORD
    export PGPASSWORD="${password}"

    if docker exec -e PGPASSWORD="${password}" "${CONTAINER_NAME}" \
        psql -U "${APP_USER}" -d "${DB_NAME}" -c "SELECT 1;" &>/dev/null; then
        echo -e "${GREEN}✓ Connection test successful${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠ Connection test failed (this may be expected if pg_hba.conf requires adjustment)${NC}"
        return 1
    fi
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------
main() {
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║       GOODFELLAZ17 - App User Password Management            ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""

    validate_input "$@"
    preflight_checks
    set_password "$1"
    test_connection "$1" || true
    print_instructions
}

main "$@"
