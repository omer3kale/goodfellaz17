#!/usr/bin/env bash
# =============================================================================
# GOODFELLAZ17 - PostgreSQL Restore Script
# =============================================================================
# Point-in-time recovery from compressed backup files.
# Replicates Neon's PITR capability for self-hosted deployments.
#
# Usage: ./infra/restore-db.sh <backup-file>
# Example: ./infra/restore-db.sh infra/backups/spotifybot_20260119_150000.sql.gz
#
# ⚠️  WARNING: This will DROP all existing data!
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Configuration
# -----------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "${SCRIPT_DIR}")"
LOG_FILE="${SCRIPT_DIR}/backup.log"
CONTAINER_NAME="goodfellaz17-postgres"
DB_NAME="spotifybot"
DB_USER="spotifybot_admin"
MIGRATIONS_DIR="${PROJECT_ROOT}/src/main/resources/db/migration"

# Colors for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# -----------------------------------------------------------------------------
# Logging Functions
# -----------------------------------------------------------------------------
log() {
    local level="$1"
    shift
    local message="$*"
    local timestamp=$(date +"%Y-%m-%d %H:%M:%S")
    echo "[${timestamp}] [${level}] ${message}" | tee -a "${LOG_FILE}"
}

log_info()  { log "INFO" "$@"; }
log_warn()  { log "WARN" "$@"; }
log_error() { log "ERROR" "$@"; }

print_banner() {
    echo -e "${YELLOW}"
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║         GOODFELLAZ17 - DATABASE RESTORE UTILITY              ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

# -----------------------------------------------------------------------------
# Validation
# -----------------------------------------------------------------------------
validate_input() {
    if [[ $# -lt 1 ]]; then
        echo -e "${RED}Error: No backup file specified${NC}"
        echo ""
        echo "Usage: $0 <backup-file>"
        echo ""
        echo "Available backups:"
        if [[ -d "${SCRIPT_DIR}/backups" ]]; then
            ls -lh "${SCRIPT_DIR}/backups"/*.sql.gz 2>/dev/null | awk '{print "  " $9 " (" $5 ")"}'
        else
            echo "  No backups found"
        fi
        exit 1
    fi
    
    BACKUP_FILE="$1"
    
    # Handle relative paths
    if [[ ! "${BACKUP_FILE}" = /* ]]; then
        BACKUP_FILE="${PROJECT_ROOT}/${BACKUP_FILE}"
    fi
    
    if [[ ! -f "${BACKUP_FILE}" ]]; then
        log_error "Backup file not found: ${BACKUP_FILE}"
        exit 1
    fi
    
    if [[ ! "${BACKUP_FILE}" == *.sql.gz ]]; then
        log_error "Invalid backup file format. Expected .sql.gz file"
        exit 1
    fi
    
    # Verify gzip integrity
    if ! gzip -t "${BACKUP_FILE}" 2>/dev/null; then
        log_error "Backup file is corrupted (gzip integrity check failed)"
        exit 1
    fi
    
    log_info "Backup file validated: ${BACKUP_FILE}"
}

# -----------------------------------------------------------------------------
# Pre-flight Checks
# -----------------------------------------------------------------------------
preflight_checks() {
    log_info "Running preflight checks..."
    
    # Check if Docker is available
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed or not in PATH"
        exit 1
    fi
    
    # Check if container is running
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        log_error "Container '${CONTAINER_NAME}' is not running"
        echo ""
        echo "Start the database first:"
        echo "  docker compose -f infra/docker-compose.db.yml up -d"
        exit 1
    fi
    
    # Check migrations directory
    if [[ ! -d "${MIGRATIONS_DIR}" ]]; then
        log_warn "Migrations directory not found: ${MIGRATIONS_DIR}"
        log_warn "Post-restore migration will be skipped"
    fi
    
    log_info "Preflight checks passed"
}

# -----------------------------------------------------------------------------
# Safety Confirmation
# -----------------------------------------------------------------------------
confirm_restore() {
    local backup_name=$(basename "${BACKUP_FILE}")
    local backup_size=$(du -h "${BACKUP_FILE}" | cut -f1)
    local backup_date=$(stat -f "%Sm" -t "%Y-%m-%d %H:%M:%S" "${BACKUP_FILE}" 2>/dev/null || stat --format="%y" "${BACKUP_FILE}" 2>/dev/null | cut -d'.' -f1)
    
    echo ""
    echo -e "${RED}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║                    ⚠️  WARNING ⚠️                              ║${NC}"
    echo -e "${RED}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo "This operation will:"
    echo "  1. DROP the entire public schema (ALL DATA WILL BE LOST)"
    echo "  2. Create a fresh public schema"
    echo "  3. Restore from backup: ${backup_name}"
    echo ""
    echo "Backup details:"
    echo "  File: ${backup_name}"
    echo "  Size: ${backup_size}"
    echo "  Date: ${backup_date}"
    echo ""
    echo "Database: ${DB_NAME}@${CONTAINER_NAME}"
    echo ""
    
    # Skip confirmation if --force flag is passed
    if [[ "${2:-}" == "--force" ]]; then
        log_warn "Force flag detected, skipping confirmation"
        return 0
    fi
    
    read -p "Type 'RESTORE' to confirm: " confirmation
    
    if [[ "${confirmation}" != "RESTORE" ]]; then
        log_info "Restore cancelled by user"
        exit 0
    fi
    
    log_info "User confirmed restore operation"
}

# -----------------------------------------------------------------------------
# Create Pre-Restore Backup
# -----------------------------------------------------------------------------
create_safety_backup() {
    log_info "Creating safety backup before restore..."
    
    local safety_backup="${SCRIPT_DIR}/backups/pre_restore_$(date +%Y%m%d_%H%M%S).sql.gz"
    
    mkdir -p "$(dirname "${safety_backup}")"
    
    if docker exec "${CONTAINER_NAME}" \
        pg_dump -U "${DB_USER}" -d "${DB_NAME}" \
        --format=plain \
        --no-owner \
        --no-acl \
        2>> "${LOG_FILE}" | gzip -9 > "${safety_backup}"; then
        
        log_info "Safety backup created: ${safety_backup}"
        echo ""
        echo -e "${GREEN}Safety backup saved to: ${safety_backup}${NC}"
        echo "If restore fails, you can recover with:"
        echo "  ./infra/restore-db.sh ${safety_backup}"
        echo ""
    else
        log_warn "Could not create safety backup (database may be empty)"
    fi
}

# -----------------------------------------------------------------------------
# Restore Database
# -----------------------------------------------------------------------------
perform_restore() {
    log_info "Starting database restore..."
    
    local start_time=$(date +%s)
    
    # Step 1: Drop and recreate public schema
    log_info "Step 1/3: Dropping existing schema..."
    docker exec "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}" \
        -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;" \
        2>> "${LOG_FILE}"
    
    # Step 2: Restore from backup
    log_info "Step 2/3: Restoring from backup..."
    if gunzip -c "${BACKUP_FILE}" | docker exec -i "${CONTAINER_NAME}" \
        psql -U "${DB_USER}" -d "${DB_NAME}" \
        --single-transaction \
        --set ON_ERROR_STOP=on \
        2>> "${LOG_FILE}"; then
        
        log_info "Backup restored successfully"
    else
        log_error "Restore FAILED"
        echo ""
        echo -e "${RED}Restore failed! Check ${LOG_FILE} for details.${NC}"
        exit 1
    fi
    
    # Step 3: Verify restore
    log_info "Step 3/3: Verifying restore..."
    local table_count
    table_count=$(docker exec "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}" \
        -t -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE';" \
        2>/dev/null | tr -d ' ')
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    log_info "Restore completed in ${duration}s"
    log_info "Tables restored: ${table_count}"
    
    echo ""
    echo -e "${GREEN}✅ Database restored successfully!${NC}"
    echo ""
    echo "Summary:"
    echo "  Tables: ${table_count}"
    echo "  Duration: ${duration}s"
    echo ""
}

# -----------------------------------------------------------------------------
# Run Migrations (Optional)
# -----------------------------------------------------------------------------
run_migrations() {
    if [[ ! -d "${MIGRATIONS_DIR}" ]]; then
        log_warn "Skipping migrations - directory not found"
        return 0
    fi
    
    echo ""
    read -p "Run migrations V1-V10 to ensure schema consistency? [y/N]: " run_mig
    
    if [[ "${run_mig}" != "y" && "${run_mig}" != "Y" ]]; then
        log_info "Skipping migrations per user request"
        return 0
    fi
    
    log_info "Running migrations to ensure schema consistency..."
    
    local migration_count=0
    local failed_count=0
    
    for migration in V1__*.sql V2__*.sql V3__*.sql V4__*.sql V5__*.sql \
                     V6__*.sql V7__*.sql V8__*.sql V9__*.sql V10__*.sql; do
        local migration_path="${MIGRATIONS_DIR}/${migration}"
        
        if [[ -f "${migration_path}" ]]; then
            log_info "Running: ${migration}"
            if docker exec -i "${CONTAINER_NAME}" \
                psql -U "${DB_USER}" -d "${DB_NAME}" \
                < "${migration_path}" 2>> "${LOG_FILE}" > /dev/null; then
                migration_count=$((migration_count + 1))
            else
                failed_count=$((failed_count + 1))
                log_warn "Migration had errors (may be expected for CREATE IF NOT EXISTS): ${migration}"
            fi
        fi
    done
    
    log_info "Migrations complete: ${migration_count} processed, ${failed_count} with warnings"
}

# -----------------------------------------------------------------------------
# Post-Restore Report
# -----------------------------------------------------------------------------
print_report() {
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "                    RESTORE COMPLETE"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
    echo "Database: ${DB_NAME}"
    echo "Container: ${CONTAINER_NAME}"
    echo ""
    echo "Tables:"
    docker exec "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}" \
        -c "\dt" 2>/dev/null | head -20
    echo ""
    echo "Row counts:"
    docker exec "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}" -t -c "
        SELECT 'users: ' || count(*) FROM users
        UNION ALL SELECT 'orders: ' || count(*) FROM orders
        UNION ALL SELECT 'services: ' || count(*) FROM services
        UNION ALL SELECT 'proxy_nodes: ' || count(*) FROM proxy_nodes;
    " 2>/dev/null
    echo ""
    log_info "Restore job completed"
}

# -----------------------------------------------------------------------------
# Main Execution
# -----------------------------------------------------------------------------
main() {
    print_banner
    
    log_info "=========================================="
    log_info "GOODFELLAZ17 Database Restore"
    log_info "=========================================="
    
    validate_input "$@"
    preflight_checks
    confirm_restore "$@"
    create_safety_backup
    perform_restore
    run_migrations
    print_report
    
    log_info "=========================================="
}

# Run main function
main "$@"
