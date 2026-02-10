# 🎵 Spotify SMM Panel - GOODFELLAZ17

[![Build](https://img.shields.io/github/actions/workflow/status/goodfellaz17/spotify-smm/ci.yml?style=flat-square)](https://github.com/goodfellaz17/spotify-smm/actions)
[![License](https://img.shields.io/badge/license-Commercial-blue?style=flat-square)](LICENSE.txt)
[![Java](https://img.shields.io/badge/Java-17+-orange?style=flat-square)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen?style=flat-square)](https://spring.io/projects/spring-boot)

> **Production-Ready SMM Panel** - 9 Spotify Services, PWA Dashboard, 56 Tests, Render 1-Click Deploy

![Demo Dashboard](assets/screenshots/dashboard.png)

## 🔥 LIVE DEMO

**Try Now:** [https://goodfellaz17.onrender.com](https://goodfellaz17.onrender.com)

```bash
# Get services
curl -X POST https://goodfellaz17.onrender.com/api/v2 -d "key=demo&action=services"

# Place order
curl -X POST https://goodfellaz17.onrender.com/api/v2 \
  -d "key=demo&action=add&service=1&link=https://open.spotify.com/track/xxx&quantity=1000"
```

## 💰 Services & Pricing

| ID | Service | Rate/1k | Min | Max |
|----|---------|---------|-----|-----|
| 1 | Spotify Plays Worldwide | $0.50 | 100 | 10M |
| 2 | Spotify Plays USA | $0.90 | 100 | 5M |
| 3 | Monthly Listeners USA | $1.90 | 500 | 1M |
| 4 | Monthly Listeners Global | $1.50 | 500 | 2M |
| 5 | Spotify Followers | $2.00 | 100 | 500K |
| 6 | Spotify Saves | $1.00 | 100 | 1M |
| 7 | Playlist Followers | $1.50 | 100 | 500K |
| 10 | Plays Drip Feed (24h) | $0.60 | 1K | 1M |
| 11 | Monthly Drip USA (30d) | $2.50 | 1K | 500K |

## 🎓 Distributed Streaming Bot (Thesis Edition)

This system provides a high-throughput, software-only Spotify streaming engine capable of delivering 15,000 streams/day for research purposes.

### Architecture Overview
- **Reactive Core:** Java 17 + Project Reactor (1k+ tasks/sec)
- **Distributed Workers:** Containerized Puppeteer/Chromium instances
- **Detection Evasion:** Behavioral randomization, proxy rotation per stream
- **Scalable:** K8s-ready with HPA support

### Running the Thesis System
This project includes a built-in benchmarking suite to verify throughput.

```bash
# Run the 1000-stream performance benchmark
mvn test -Dtest=PerformanceBenchmark

# Run core domain unit tests
mvn test -Dtest=ReactiveStreamingUnitTest
```

### Demonstration Results (Simulation Mode)
| Metric | Result |
|--------|--------|
| **Daily Capacity** | ~3.9M streams/day |
| **Throughput** | 45+ streams/sec |
| **Success Rate** | 96% - 98% |
| **Architecture** | Reactive Project Reactor |
| **Data Layer** | R2DBC (Non-blocking) |
# Start local environment (50 workers)
docker-compose up --scale streaming-worker=50

# Distribute 15,000 streams
curl -X POST http://localhost:8080/api/tasks/distribute?totalStreams=15000&trackId=spotify:track:xxx

# Verify Performance
mvn test -Dtest=PerformanceBenchmark

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│              MONTICORE SMI ARCHITECTURE                     │
├─────────────────────────────────────────────────────────────┤
│  Presentation    │  REST API v2 + PWA Dashboard             │
├──────────────────┼──────────────────────────────────────────┤
│  MontiCore SMI   │  SymbolTable, CoCos, Visitors            │
├──────────────────┼──────────────────────────────────────────┤
│  Application     │  OrderService, BotOrchestrator           │
├──────────────────┼──────────────────────────────────────────┤
│  Domain          │  Order Aggregate, BotTask, DripSchedule  │
├──────────────────┼──────────────────────────────────────────┤
│  Infrastructure  │  R2DBC PostgreSQL, Proxy Pool            │
└─────────────────────────────────────────────────────────────┘
```

## 📁 Project Structure

```
goodfellaz17-provider/
├── src/main/java/com/goodfellaz17/
│   ├── symboltable/         # MontiCore Symbol Tables
│   │   ├── SmmSymbol.java           # Service symbols
│   │   ├── SmmSymbolTableFactory.java
│   │   └── SmmScope.java            # Scoping
│   ├── cocos/               # Context Conditions
│   │   ├── OrderQuantityCoCo.java   # Min/max validation
│   │   ├── SpotifyDripRateCoCo.java # Anti-spike
│   │   └── CoCoCollector.java       # Error collection
│   ├── visitor/             # Visitor Pattern
│   │   ├── SmmVisitor.java
│   │   └── DelegatorVisitor.java
│   ├── prettyprinter/       # API Documentation
│   │   └── SmmPrettyPrinter.java
│   ├── domain/
│   │   ├── model/           # Entities & Value Objects
│   │   └── port/            # Domain Interfaces
│   ├── application/
│   │   └── service/         # Use Cases
│   ├── infrastructure/
│   │   ├── persistence/     # R2DBC Adapters
│   │   ├── bot/             # Stealth Execution
│   │   └── config/          # Spring Configuration
│   └── presentation/
│       ├── api/             # REST Controllers
│       └── dto/             # API DTOs
├── src/main/resources/static/  # PWA Dashboard
├── docs/                    # Documentation
│   ├── buyer/               # Buyer guides
│   └── architecture/        # PlantUML diagrams
├── dist/                    # Marketplace package
└── pom.xml
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
# Deploy trigger Thu Dec 25 00:18:20 CET 2025
