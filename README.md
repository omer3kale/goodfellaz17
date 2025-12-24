# GOODFELLAZ17 Provider

> **RWTH MATSE Research Project** - Clean Architecture SMM Panel API with Spring Boot 3.5, Supabase persistence, and Python stealth executor.

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                   CLEAN ARCHITECTURE                        │
├─────────────────────────────────────────────────────────────┤
│  Presentation    │  REST API v2 (SMM Panel Spec)            │
├──────────────────┼──────────────────────────────────────────┤
│  Application     │  OrderService, BotOrchestrator           │
├──────────────────┼──────────────────────────────────────────┤
│  Domain          │  Order Aggregate, BotTask, DripSchedule  │
├──────────────────┼──────────────────────────────────────────┤
│  Infrastructure  │  Python Stealth, Proxy Pool, Supabase    │
└─────────────────────────────────────────────────────────────┘
```

## 📁 Project Structure

```
goodfellaz17-provider/
├── src/main/java/com/goodfellaz17/
│   ├── domain/
│   │   ├── model/           # Entities & Value Objects
│   │   │   ├── Order.java           # Aggregate Root
│   │   │   ├── BotTask.java         # Execution unit
│   │   │   ├── DripSchedule.java    # Anti-detection timing
│   │   │   ├── SpotifyTrackId.java  # Track identifier VO
│   │   │   └── ...
│   │   └── port/            # Domain Interfaces
│   │       ├── OrderRepositoryPort.java
│   │       ├── BotExecutorPort.java
│   │       ├── ProxyPoolPort.java
│   │       └── AccountFarmPort.java
│   │
│   ├── application/
│   │   ├── service/         # Use Cases
│   │   │   ├── OrderService.java
│   │   │   └── BotOrchestratorService.java
│   │   ├── command/         # Input DTOs
│   │   └── response/        # Output DTOs
│   │
│   ├── infrastructure/
│   │   ├── persistence/     # Database Adapters
│   │   ├── bot/             # Chrome Automation
│   │   ├── proxy/           # Residential Proxy Pool
│   │   ├── account/         # Premium Account Farm
│   │   └── config/          # Spring Configuration
│   │
│   └── presentation/
│       ├── api/             # REST Controllers
│       └── dto/             # API Request/Response
│
├── docs/architecture/       # PlantUML Diagrams
├── scripts/                 # Database migrations
├── docker-compose.yml       # Local dev environment
└── pom.xml                  # Maven dependencies
```

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+
- Docker & Docker Compose (for local dev)

### Development Setup

```bash
# Clone repository
git clone <repository-url>
cd spotify-bot-provider

# Start infrastructure (PostgreSQL, Selenium Grid)
docker-compose up -d postgres selenium-hub chrome

# Run application
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# API available at http://localhost:8080/api/v2
```

### Docker Deployment

```bash
# Build and run everything
docker-compose up --build

# Scale Chrome workers
docker-compose up --scale chrome=10
```

## 📡 API Endpoints (SMM Panel v2 Spec)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v2/add` | Place new order |
| GET | `/api/v2/status` | Get order status |
| POST | `/api/v2/status` | Batch status check |
| GET | `/api/v2/services` | List available services |
| POST | `/api/v2/cancel` | Cancel order |
| GET | `/api/v2/balance` | Check API balance |
| GET | `/api/v2/stats` | Execution statistics |

### Example: Place Order

```bash
curl -X POST http://localhost:8080/api/v2/add \
  -H "Content-Type: application/json" \
  -d '{
    "key": "your-api-key",
    "action": "add",
    "service": 4,
    "link": "https://open.spotify.com/track/4iV5W9uYEdYUVa79Axb7Rh",
    "quantity": 100000
  }'

# Response
{
  "success": true,
  "order": "550e8400-e29b-41d4-a716-446655440000"
}
```

## 🎯 Key Business Rules

### Domain Invariants

| Rule | Value | Purpose |
|------|-------|---------|
| Max Hourly Spike | 5% | Avoid Chartmetric detection |
| Min Session Duration | 35 seconds | Royalty eligibility |
| Max Order Size | 10M plays | Risk management |
| Account Daily Limit | 500 plays | Account longevity |

### Drip Schedule (VIP Tier Example)

```
1M plays over 24 hours
├── 5% hourly max = 50,000 plays/hour
├── 6 batches/hour = 8,333 plays/batch  
├── 10-minute intervals
└── Random variance ±5 seconds per session
```

## 🔧 Configuration

### Environment Variables

```bash
# Database (Supabase)
DB_HOST=localhost
DB_PORT=5432
DB_NAME=spotifybot
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Bot Configuration
BOT_MAX_CONCURRENT=100
PROXY_PROVIDER=BRIGHTDATA  # MOCK, BRIGHTDATA, OXYLABS
PROXY_API_KEY=your-api-key
ACCOUNT_FARM_SIZE=1000
```

## 📊 Architecture Diagrams

Located in `docs/architecture/`:

- **C4-Context.puml** - System context (external actors)
- **C4-Container.puml** - Container diagram (internal services)
- **DomainModel.puml** - Domain entities and relationships
- **Sequence-OrderFlow.puml** - Order execution flow
- **Deployment.puml** - Production deployment topology

Generate diagrams using PlantUML VS Code extension or online at [plantuml.com](https://www.plantuml.com/plantuml).

## 📚 Research Context

This project analyzes the software architecture of SMM panels like **StreamingMafia.com** for academic research on:

1. **Streaming Fraud Mechanisms** - How bot services simulate organic plays
2. **Detection Evasion** - Drip scheduling, proxy rotation, account farming
3. **Money Laundering Patterns** - Pandemic-era scandals (Xatar, Mero)
4. **Royalty System Exploitation** - 35-second threshold gaming

### Related Work
- Chartmetric bot detection algorithms
- Spotify's fraud detection systems
- GEMA/BMI royalty distribution analysis

## 🧪 Testing

```bash
# Unit tests (Domain layer)
./mvnw test -Dtest="**/domain/**"

# Integration tests
./mvnw test -Dtest="**/integration/**"

# Full test suite
./mvnw verify
```

## 📄 License

Academic research use only. Not for commercial deployment.

---

**RWTH Aachen University** - MATSE Program  
**Author**: Research Student  
**Date**: December 2024
