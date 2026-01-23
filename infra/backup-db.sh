#!/usr/bin/env bash
# =============================================================================
# GOODFELLAZ17 - PostgreSQL Backup Script
# =============================================================================
# Production-grade backup with compression, rotation, and logging.
# Replicates Neon's automated backup capability for self-hosted deployments.
#
# Usage: ./infra/backup-db.sh
# Cron:  0 3 * * * /path/to/goodfellaz17/infra/backup-db.sh
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Configuration
# -----------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${SCRIPT_DIR}/backups"
LOG_FILE="${SCRIPT_DIR}/backup.log"
CONTAINER_NAME="goodfellaz17-postgres"
DB_NAME="spotifybot"
DB_USER="spotifybot_admin"
RETENTION_DAYS=30

# Timestamp for this backup
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="spotifybot_${TIMESTAMP}.sql.gz"
BACKUP_PATH="${BACKUP_DIR}/${BACKUP_FILE}"

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

# -----------------------------------------------------------------------------
# Pre-flight Checks
# -----------------------------------------------------------------------------
preflight_checks() {
    log_info "Starting backup preflight checks..."
    
    # Check if Docker is available
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed or not in PATH"
        exit 1
    fi
    
    # Check if container is running
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        log_error "Container '${CONTAINER_NAME}' is not running"
        exit 1
    fi
    
    # Check container health
    local health_status
    health_status=$(docker inspect --format='{{.State.Health.Status}}' "${CONTAINER_NAME}" 2>/dev/null || echo "unknown")
    if [[ "${health_status}" != "healthy" ]]; then
        log_warn "Container health status: ${health_status} (expected: healthy)"
    fi
    
    # Create backup directory if it doesn't exist
    if [[ ! -d "${BACKUP_DIR}" ]]; then
        mkdir -p "${BACKUP_DIR}"
        log_info "Created backup directory: ${BACKUP_DIR}"
    fi
    
    # Check disk space (require at least 1GB free)
    local free_space_kb
    free_space_kb=$(df -k "${BACKUP_DIR}" | tail -1 | awk '{print $4}')
    if [[ ${free_space_kb} -lt 1048576 ]]; then
        log_error "Insufficient disk space. Need at least 1GB free, have: $((free_space_kb / 1024))MB"
        exit 1
    fi
    
    log_info "Preflight checks passed"
}

# -----------------------------------------------------------------------------
# Backup Function
# -----------------------------------------------------------------------------
perform_backup() {
    log_info "Starting backup: ${BACKUP_FILE}"
    
    local start_time=$(date +%s)
    
    # Create backup using pg_dump with compression
    # Options:
    #   -Fc: Custom format (most flexible for restore)
    #   -Z9: Maximum compression
    #   --no-owner: Skip ownership commands (portable)
    #   --no-acl: Skip access privilege commands
    #   --clean: Include DROP commands before CREATE
    if docker exec "${CONTAINER_NAME}" \
        pg_dump -U "${DB_USER}" -d "${DB_NAME}" \
        --format=plain \
        --no-owner \
        --no-acl \
        --clean \
        --if-exists \
        2>> "${LOG_FILE}" | gzip -9 > "${BACKUP_PATH}"; then
        
        local end_time=$(date +%s)
        local duration=$((end_time - start_time))
        local backup_size=$(du -h "${BACKUP_PATH}" | cut -f1)
        
        log_info "Backup completed successfully"
        log_info "  File: ${BACKUP_PATH}"
        log_info "  Size: ${backup_size}"
        log_info "  Duration: ${duration}s"
        
        # Verify backup integrity
        if gzip -t "${BACKUP_PATH}" 2>/dev/null; then
            log_info "Backup integrity verified (gzip test passed)"
        else
            log_error "Backup integrity check FAILED"
            rm -f "${BACKUP_PATH}"
            exit 1
        fi
    else
        log_error "Backup FAILED"
        rm -f "${BACKUP_PATH}"
        exit 1
    fi
}

# -----------------------------------------------------------------------------
# Retention Cleanup
# -----------------------------------------------------------------------------
cleanup_old_backups() {
    log_info "Cleaning up backups older than ${RETENTION_DAYS} days..."
    
    local deleted_count=0
    local deleted_size=0
    
    # Find and delete old backups
    while IFS= read -r -d '' old_backup; do
        local file_size
        file_size=$(du -k "${old_backup}" | cut -f1)
        deleted_size=$((deleted_size + file_size))
        
        rm -f "${old_backup}"
        log_info "Deleted old backup: $(basename "${old_backup}")"
        deleted_count=$((deleted_count + 1))
    done < <(find "${BACKUP_DIR}" -name "spotifybot_*.sql.gz" -type f -mtime +${RETENTION_DAYS} -print0 2>/dev/null)
    
    if [[ ${deleted_count} -gt 0 ]]; then
        log_info "Cleanup complete: removed ${deleted_count} backups (freed ~$((deleted_size / 1024))MB)"
    else
        log_info "No old backups to clean up"
    fi
    
    # Report current backup inventory
    local backup_count
    backup_count=$(find "${BACKUP_DIR}" -name "spotifybot_*.sql.gz" -type f 2>/dev/null | wc -l | tr -d ' ')
    local total_size
    total_size=$(du -sh "${BACKUP_DIR}" 2>/dev/null | cut -f1)
    log_info "Backup inventory: ${backup_count} files, ${total_size} total"
}

# -----------------------------------------------------------------------------
# Main Execution
# -----------------------------------------------------------------------------
main() {
    log_info "=========================================="
    log_info "GOODFELLAZ17 Database Backup"
    log_info "=========================================="
    
    preflight_checks
    perform_backup
    cleanup_old_backups
    
    log_info "Backup job completed successfully"
    log_info "=========================================="
}

# Run main function
main "$@"
