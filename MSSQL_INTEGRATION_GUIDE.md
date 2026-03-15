# MSSQL Integration Guide

**Date:** March 13, 2026 | **Status:** Full MSSQL R2DBC integration complete

## ⚡ Quick Start (Local MSSQL)

### 1. Start MSSQL Server in Docker
```bash
docker-compose -f docker-compose.mssql.yml up -d
```

### 2. Verify MSSQL is running
```bash
docker-compose -f docker-compose.mssql.yml logs mssql
# Look for: [INFO] SQL Server is now ready for client connections
```

### 3. Run Spring Boot with MSSQL profile
```bash
mvn spring-boot:run -Dspring-boot.run.arguments='--spring.profiles.active=mssql'
```

Or via environment variable:
```bash
export SPRING_PROFILES_ACTIVE=mssql
mvn spring-boot:run
```

### 4. Verify connection
- Check logs for: "Successfully created R2DBC connection pool to mssql://localhost:1433"
- Hit health endpoint: `curl http://localhost:8080/actuator/health`
- Should show: `"db": "UP"`

---

## 📦 What Changed

### pom.xml
- ✅ Replaced `r2dbc-postgresql` with `r2dbc-mssql` (v1.0.1.RELEASE)
- ✅ Added `mssql-jdbc` (v12.4.2.jre17) for JDBC support
- ✅ Replaced testcontainers-postgresql with testcontainers-mssqlserver
- ✅ Kept `r2dbc-spi` and `r2dbc-pool` (unchanged)

### R2dbcConfig.java
- ✅ Updated default URL to MSSQL format: `r2dbc:mssql://localhost:1433;database=goodfellaz17`
- ✅ Added MSSQL URL parsing logic (`mssql://host:port;database=name`)
- ✅ Kept PostgreSQL parsing for backward compatibility
- ✅ Kept H2 parsing for in-memory tests

### New Files
- ✅ **application-mssql.yml** — MSSQL R2DBC + connection pool config
- ✅ **docker-compose.mssql.yml** — Local MSSQL 2022 server setup

---

## 🔧 Configuration Profiles

### Local Development (default — `mssql`)
```bash
spring.profiles.active=mssql
spring.r2dbc.url=r2dbc:mssql://localhost:1433;database=goodfellaz17
spring.r2dbc.username=sa
spring.r2dbc.password=YourStrong@Passw0rd
```

### Production (Azure SQL — `mssql-prod`)
```bash
spring.profiles.active=mssql-prod
export MSSQL_R2DBC_URL="r2dbc:mssql://[srvname].database.windows.net:1433;database=[dbname];encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net"
export MSSQL_USERNAME="[adminuser]"
export MSSQL_PASSWORD="[strongpass]"
```

---

## 🗄️ MSSQL R2DBC URL Formats

### Local/Docker
```
r2dbc:mssql://localhost:1433;database=goodfellaz17
r2dbc:mssql://host:port;database=dbname
```

### Azure SQL Database (production)
```
r2dbc:mssql://myserver.database.windows.net:1433;database=mydb;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net
```

### With authentication in URL (alternative)
```
r2dbc:mssql://user:password@host:port;database=dbname
```

---

## 🧪 Integration Tests

### Testcontainers + MSSQL
Flyway migrations now run against testcontainers-mssqlserver automatically in integration tests.

**Example (OrderControllerIntegrationTest):**
```java
@Testcontainers
class OrderControllerIntegrationTest {
    
    @Container
    static MSSQLServerContainer<?> mssql = new MSSQLServerContainer<>()
        .withDatabaseName("goodfellaz17_test");
    
    @DynamicPropertySource
    static void r2dbcProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", 
            () -> "r2dbc:mssql://" + mssql.getHost() + ":" 
                + mssql.getMappedPort(1433) 
                + ";database=goodfellaz17_test");
        registry.add("spring.r2dbc.username", () -> mssql.getUsername());
        registry.add("spring.r2dbc.password", () -> mssql.getPassword());
    }
}
```

---

## 🚀 Migration Strategy

### Existing Flyway migrations (V1-V14)
These are **PostgreSQL-specific** and need rewriting for MSSQL. Do this in phases:

1. **Phase 1 (Done):** V15 — New MSSQL-native core tables (orders, order_tasks, execution_results)
2. **Phase 2 (Next):** V16+ — Reference tables (tenants, services, accounts, proxy_nodes, etc.) in MSSQL syntax
3. **Phase 3 (Future):** Port or drop old V1-V14 migrations; rebase on V15+

### Running migrations
```bash
# Start MSSQL
docker-compose -f docker-compose.mssql.yml up -d

# Spring Boot auto-runs Flyway on startup (with application-mssql.yml)
mvn spring-boot:run -Dspring-boot.run.arguments='--spring.profiles.active=mssql'
```

---

## 📊 Verification Queries

### Check MSSQL connection
```sql
SELECT @@VERSION;  -- Returns MSSQL version
SELECT DB_NAME();  -- Should return "goodfellaz17"
```

### Check tables (after V15 migration)
```sql
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = 'dbo' AND TABLE_TYPE = 'BASE TABLE';
```

Expected output:
- `orders`
- `order_tasks`
- `execution_results`

### Check indexes
```sql
SELECT TABLE_NAME, INDEX_NAME 
FROM INFORMATION_SCHEMA.INDEXES 
WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME IN ('orders', 'order_tasks', 'execution_results');
```

---

## 🔐 Security Notes

### Local Development
- Default SA password: `YourStrong@Passw0rd`
- Only use this locally (docker-compose)
- Never commit real passwords to git

### Production (Azure SQL)
- Use Azure Key Vault or environment variables
- Connection via encrypted TLS (trustServerCertificate=false)
- Monitor connection patterns in Azure portal

---

## ⚠️ Known Limitations / TODO

- [ ] Replicate V1-V14 migrations in MSSQL syntax (FTL-B Phase 2)
- [ ] Update all @Query SQL to use `@P0`, `@P1` instead of `$1`, `$2` (PostgreSQL style)
- [ ] Validate stored procedures (V13 PL/pgSQL) don't exist in V15+ (no T-SQL equivalents yet)
- [ ] Update OrderControllerIntegrationTest to use MSSQLServerContainer
- [ ] Load test: 10k orders, 100k tasks on MSSQL

---

## 🆘 Troubleshooting

### "Cannot find r2dbc-mssql driver"
```
Error: io.r2dbc.spi.NoSuchDriverException: Could not find driver for r2dbc:mssql://...
```
✅ **Solution:** Ensure pom.xml has both `r2dbc-mssql` AND `mssql-jdbc` dependencies

### "Connection refused on port 1433"
```
Error: com.microsoft.sqlserver.jdbc.SQLServerException: Connection refused
```
✅ **Solution:** Start MSSQL first: `docker-compose -f docker-compose.mssql.yml up -d`

### "Login failed for user 'sa'"
```
Error: com.microsoft.sqlserver.jdbc.SQLServerException: Login failed for user 'sa'
```
✅ **Solution:** Check password in `application-mssql.yml` matches `docker-compose.mssql.yml` (both should be `YourStrong@Passw0rd`)

### Flyway migration fails with "Invalid SQL"
✅ **Solution:** V1-V14 use PostgreSQL syntax; use only V15+ migrations on MSSQL. See "Migration Strategy" above.

---

## 📈 Next Steps

1. **Start MSSQL:** `docker-compose -f docker-compose.mssql.yml up -d`
2. **Run app:** `mvn spring-boot:run -Dspring-boot.run.arguments='--spring.profiles.active=mssql'`
3. **Verify tables:** Check V15 migration created tables (see verification queries above)
4. **Add reference tables:** V16+ migrations for tenants, services, accounts, etc. (FTL-B Phase 2)

---

**Authored by:** FTL-B MSSQL Integration | **Last Updated:** March 13, 2026
