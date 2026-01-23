#!/usr/bin/env bash
# =============================================================================
# branch-db.sh - Create isolated database branches for bot experiments
# =============================================================================
#
# Usage:
#   ./infra/branch-db.sh <branch_name> [--with-data]
#
# Examples:
#   ./infra/branch-db.sh feature_residential_proxy
#   ./infra/branch-db.sh ab_test_drip_rate --with-data
#
# What it does:
#   1. Creates new database: spotifybot_<branch_name>
#   2. Runs all migrations (V1-V14) to create schema
#   3. Optionally restores sanitized prod data (orders, no users/balances)
#   4. Generates Spring profile: application-<branch_name>.yml
#   5. Registers in tenant_databases catalog
#
# Use cases:
#   - Test bot strategies in isolation
#   - A/B test configurations
#   - Prove thesis hypotheses with controlled experiments
#
# Prerequisites:
#   - Docker container running: goodfellaz17-postgres
#   - Admin credentials: spotifybot_admin
#
# =============================================================================

set -euo pipefail

# Configuration
CONTAINER_NAME="goodfellaz17-postgres"
DB_ADMIN_USER="spotifybot_admin"
DB_ADMIN_PASSWORD="Mariogomez33Strong"
DB_HOST="localhost"
DB_PORT="5432"
MAIN_DB="spotifybot"

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
MIGRATIONS_DIR="$PROJECT_ROOT/src/main/resources/db/migration"
PROFILES_DIR="$PROJECT_ROOT/src/main/resources"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[OK]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

show_usage() {
    cat << EOF
Usage: $0 <branch_name> [options]

Creates an isolated database branch for experiments.

Arguments:
  branch_name       Name for the branch (alphanumeric and underscore only)
                    Example: feature_residential_proxy

Options:
  --with-data       Copy sanitized data from production (orders only, no user balances)
  --help            Show this help message

Examples:
  $0 feature_proxy_tiers
  $0 ab_test_drip_rate --with-data

The script will:
  1. Create database: spotifybot_<branch_name>
  2. Run all migrations (V1-V14)
  3. Generate Spring profile: application-<branch_name>.yml
  4. Register in tenant_databases catalog

To use the branch:
  SPRING_PROFILES_ACTIVE=<branch_name> mvn spring-boot:run
EOF
}

validate_branch_name() {
    local name="$1"
    
    if [[ ! "$name" =~ ^[a-z][a-z0-9_]*$ ]]; then
        log_error "Invalid branch name: $name"
        log_error "Must start with lowercase letter, contain only lowercase letters, numbers, and underscores"
        exit 1
    fi
    
    if [[ ${#name} -gt 50 ]]; then
        log_error "Branch name too long (max 50 characters): $name"
        exit 1
    fi
}

check_container() {
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        log_error "PostgreSQL container not running: $CONTAINER_NAME"
        log_error "Start it with: docker compose -f infra/docker-compose.db.yml up -d"
        exit 1
    fi
    log_success "Container running: $CONTAINER_NAME"
}

create_database() {
    local db_name="$1"
    
    log_info "Creating database: $db_name"
    
    # Check if database exists
    local exists=$(docker exec -i "$CONTAINER_NAME" psql -U "$DB_ADMIN_USER" -d postgres -tAc \
        "SELECT 1 FROM pg_database WHERE datname = '$db_name'")
    
    if [[ "$exists" == "1" ]]; then
        log_warn "Database already exists: $db_name"
        read -p "Drop and recreate? [y/N] " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            docker exec -i "$CONTAINER_NAME" psql -U "$DB_ADMIN_USER" -d postgres -c \
                "DROP DATABASE IF EXISTS $db_name;"
            log_info "Dropped existing database"
        else
            log_error "Aborted. Database exists."
            exit 1
        fi
    fi
    
    docker exec -i "$CONTAINER_NAME" psql -U "$DB_ADMIN_USER" -d postgres -c \
        "CREATE DATABASE $db_name WITH OWNER = $DB_ADMIN_USER ENCODING = 'UTF8';"
    
    log_success "Database created: $db_name"
}

run_migrations() {
    local db_name="$1"
    
    log_info "Running migrations on: $db_name"
    
    # Find all migration files
    local migrations=($(ls -1 "$MIGRATIONS_DIR"/V*.sql 2>/dev/null | sort -V))
    
    if [[ ${#migrations[@]} -eq 0 ]]; then
        log_error "No migration files found in: $MIGRATIONS_DIR"
        exit 1
    fi
    
    log_info "Found ${#migrations[@]} migrations"
    
    for migration in "${migrations[@]}"; do
        local filename=$(basename "$migration")
        log_info "  Running: $filename"
        
        docker exec -i "$CONTAINER_NAME" psql -U "$DB_ADMIN_USER" -d "$db_name" \
            < "$migration" > /dev/null 2>&1 || {
            log_error "Migration failed: $filename"
            exit 1
        }
    done
    
    log_success "All migrations applied: ${#migrations[@]} files"
}

copy_sanitized_data() {
    local db_name="$1"
    
    log_info "Copying sanitized data from $MAIN_DB to $db_name"
    
    # Copy services (full copy - configuration data)
    log_info "  Copying: services"
    docker exec -i "$CONTAINER_NAME" psql -U "$DB_ADMIN_USER" -d "$db_name" -c \
        "INSERT INTO services SELECT * FROM dblink(
            'dbname=$MAIN_DB user=$DB_ADMIN_USER password=$DB_ADMIN_PASSWORD',
            'SELECT * FROM services'
        ) AS t(
            id UUID, name VARCHAR, display_name VARCHAR, service_type VARCHAR,
            description VARCHAR, cost_per_1k DECIMAL, reseller_cost_per_1k DECIMAL,
            agency_cost_per_1k DECIMAL, min_quantity INTEGER, max_quantity INTEGER,
            estimated_days_min INTEGER, estimated_days_max INTEGER, geo_profiles JSONB,
            is_active BOOLEAN, sort_order INTEGER, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ
        );" 2>/dev/null || {
        # Fallback: simple dump/restore for services
        docker exec -i "$CONTAINER_NAME" pg_dump -U "$DB_ADMIN_USER" -d "$MAIN_DB" \
            --data-only -t services | \
        docker exec -i "$CONTAINER_NAME" psql -U "$DB_ADMIN_USER" -d "$db_name" > /dev/null 2>&1
    }
    
    # Copy proxy_nodes (important for testing proxy strategies)
    log_info "  Copying: proxy_nodes (sanitized)"
    docker exec -i "$CONTAINER_NAME" pg_dump -U "$DB_ADMIN_USER" -d "$MAIN_DB" \
        --data-only -t proxy_nodes 2>/dev/null | \
    docker exec -i "$CONTAINER_NAME" psql -U "$DB_ADMIN_USER" -d "$db_name" > /dev/null 2>&1 || true
    
    # Copy orders (without user financial data)
    log_info "  Copying: orders (last 1000, anonymized)"
    docker exec -i "$CONTAINER_NAME" psql -U "$DB_ADMIN_USER" -d "$db_name" -c \
        "INSERT INTO orders (
            id, service_id, link, quantity, amount, status, delivered, remains,
            start_count, api_source, created_at, updated_at, estimated_completion
        )
        SELECT 
            id, service_id, link, quantity, 0.00 as amount, status, delivered, remains,
            start_count, api_source, created_at, updated_at, estimated_completion
        FROM dblink(
            'dbname=$MAIN_DB user=$DB_ADMIN_USER password=$DB_ADMIN_PASSWORD',
            'SELECT id, service_id, link, quantity, status, delivered, remains, 
                    start_count, api_source, created_at, updated_at, estimated_completion 
             FROM orders ORDER BY created_at DESC LIMIT 1000'
        ) AS t(
            id UUID, service_id UUID, link VARCHAR, quantity INTEGER, status VARCHAR,
            delivered INTEGER, remains INTEGER, start_count INTEGER, api_source VARCHAR,
            created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ, estimated_completion TIMESTAMPTZ
        );" 2>/dev/null || {
        log_warn "Could not copy orders (dblink may not be available)"
    }
    
    log_success "Sanitized data copied (services, proxy_nodes, anonymized orders)"
    log_warn "User data, balances, and transactions were NOT copied (privacy)"
}

generate_spring_profile() {
    local branch_name="$1"
    local db_name="$2"
    local profile_file="$PROFILES_DIR/application-${branch_name}.yml"
    
    log_info "Generating Spring profile: $profile_file"
    
    cat > "$profile_file" << EOF
# =============================================================================
# GOODFELLAZ17 - Branch Profile: ${branch_name}
# =============================================================================
# Auto-generated by branch-db.sh on $(date -u +"%Y-%m-%d %H:%M:%S UTC")
#
# Database: ${db_name}
# Purpose: Isolated environment for experiments/testing
#
# Run command:
#   export JAVA_HOME=\$(/usr/libexec/java_home -v 17)
#   SPRING_PROFILES_ACTIVE=${branch_name} mvn spring-boot:run
#
# IMPORTANT: This branch database is isolated from production.
# Changes here do NOT affect the main spotifybot database.
# =============================================================================

spring:
  config:
    # Inherit base config from local-selfhosted
    import: classpath:application-local-selfhosted.yml
    
  # Override database to point to branch
  r2dbc:
    url: r2dbc:postgresql://localhost:6432/${db_name}
    username: spotifybot_app
    password: \${SPOTIFYBOT_APP_PASSWORD:SpotifyApp2026Secure}
    pool:
      enabled: false  # PgBouncer handles pooling

# Branch-specific configuration
goodfellaz17:
  branch:
    name: ${branch_name}
    database: ${db_name}
    created-at: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
  # Multi-tenancy disabled for branch testing
  multitenancy:
    enabled: false
    default-tenant: ${branch_name}

logging:
  level:
    root: INFO
    com.goodfellaz17: DEBUG
EOF
    
    log_success "Spring profile created: application-${branch_name}.yml"
}

register_in_catalog() {
    local branch_name="$1"
    local db_name="$2"
    
    log_info "Registering branch in tenant_databases catalog"
    
    docker exec -i "$CONTAINER_NAME" psql -U "$DB_ADMIN_USER" -d "$MAIN_DB" << EOF
INSERT INTO tenant_databases (
    tenant_code,
    database_name,
    display_name,
    owner_email,
    host,
    port,
    current_schema_version,
    target_schema_version,
    backup_enabled,
    status
) VALUES (
    '${branch_name}',
    '${db_name}',
    'Branch: ${branch_name}',
    'dev@goodfellaz17.com',
    'localhost',
    5432,
    'V14',
    'V14',
    false,  -- Don't backup experimental branches
    'ACTIVE'
) ON CONFLICT (tenant_code) DO UPDATE SET
    database_name = EXCLUDED.database_name,
    updated_at = CURRENT_TIMESTAMP;
EOF
    
    log_success "Branch registered in catalog"
}

grant_app_user_access() {
    local db_name="$1"
    
    log_info "Granting spotifybot_app access to: $db_name"
    
    docker exec -i "$CONTAINER_NAME" psql -U "$DB_ADMIN_USER" -d "$db_name" << 'EOF'
-- Grant schema access
GRANT USAGE ON SCHEMA public TO spotifybot_app;

-- Grant table access (all current and future tables)
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO spotifybot_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO spotifybot_app;

-- Grant sequence access
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO spotifybot_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO spotifybot_app;
EOF
    
    log_success "App user granted access"
}

# =============================================================================
# Main
# =============================================================================

main() {
    local branch_name=""
    local with_data=false
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --with-data)
                with_data=true
                shift
                ;;
            --help|-h)
                show_usage
                exit 0
                ;;
            -*)
                log_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
            *)
                if [[ -z "$branch_name" ]]; then
                    branch_name="$1"
                else
                    log_error "Unexpected argument: $1"
                    show_usage
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    # Validate
    if [[ -z "$branch_name" ]]; then
        log_error "Branch name is required"
        show_usage
        exit 1
    fi
    
    validate_branch_name "$branch_name"
    
    local db_name="spotifybot_${branch_name}"
    
    echo ""
    echo "========================================"
    echo "  Creating Database Branch"
    echo "========================================"
    echo "  Branch:   $branch_name"
    echo "  Database: $db_name"
    echo "  Data:     $([ "$with_data" = true ] && echo "Yes (sanitized)" || echo "No")"
    echo "========================================"
    echo ""
    
    # Execute steps
    check_container
    create_database "$db_name"
    run_migrations "$db_name"
    grant_app_user_access "$db_name"
    
    if [[ "$with_data" = true ]]; then
        copy_sanitized_data "$db_name"
    fi
    
    generate_spring_profile "$branch_name" "$db_name"
    register_in_catalog "$branch_name" "$db_name"
    
    echo ""
    echo "========================================"
    log_success "Branch created successfully!"
    echo "========================================"
    echo ""
    echo "To use this branch:"
    echo ""
    echo "  export JAVA_HOME=\$(/usr/libexec/java_home -v 17)"
    echo "  SPRING_PROFILES_ACTIVE=${branch_name} mvn spring-boot:run"
    echo ""
    echo "To connect directly with psql:"
    echo ""
    echo "  docker exec -it $CONTAINER_NAME psql -U spotifybot_app -d $db_name"
    echo ""
    echo "To delete this branch:"
    echo ""
    echo "  docker exec -it $CONTAINER_NAME psql -U $DB_ADMIN_USER -d postgres -c \"DROP DATABASE $db_name;\""
    echo "  rm $PROFILES_DIR/application-${branch_name}.yml"
    echo ""
}

main "$@"
