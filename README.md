# Orth

A distributed task scheduling framework with centralized scheduling and distributed execution.

[![License: GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0.html)
[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-green.svg)](https://spring.io/projects/spring-boot)

[中文文档](README.zh-CN.md)

## Introduction

Orth is a fork of [XXL-JOB](https://github.com/xuxueli/xxl-job) by [xuxueli](https://github.com/xuxueli), rebuilt as part of the **Abyss** project. It is optimized for high-performance Python batch data collection and low-latency streaming task scheduling.

Key differences from upstream XXL-JOB:

- Modern React 19 + TypeScript + Ant Design 5 admin UI (replaces server-rendered JSP)
- JWT authentication with role-based access control
- SuperTask pattern for batch job management
- Batch trigger with logical schedule time support
- Job import/export (JSON)
- Concurrent execution block strategy
- OpenAPI-first design
- Full English codebase and documentation

## Key Features

### Scheduling Engine

- **Time-ring algorithm** — second-precision scheduling with 60-slot ring buffer and 5-second pre-read
- **Schedule types** — CRON expressions, fixed-rate intervals, API-only triggers
- **Adaptive thread pools** — fast pool (200 threads) and slow pool (100 threads) with automatic migration based on timeout history
- **Distributed lock** — `SELECT ... FOR UPDATE` ensures single-scheduler consistency in clusters
- **Misfire strategies** — skip (`DO_NOTHING`) or fire immediately (`FIRE_ONCE_NOW`)

### Execution

- **9+ routing strategies** — FIRST, LAST, ROUND, RANDOM, CONSISTENT_HASH, LFU, LRU, FAILOVER, BUSYOVER
- **Sharding broadcast** — execute on all executors with shard index/total parameters
- **4 block strategies** — SERIAL, DISCARD_LATER, COVER_EARLY, CONCURRENT
- **Job types** — Bean, GLUE (Groovy), Script (Shell/Python/Node.js/PHP/PowerShell), HTTP, Command
- **Timeout and retry** — configurable per-job with execution log tracking

### Operations

- **SuperTask pattern** — define a template job, then batch-copy sub-tasks with parameter variations
- **Batch trigger** — trigger jobs with explicit schedule time ranges for backfilling
- **Import/export** — JSON-based job configuration portability
- **Rolling logs** — real-time log streaming from executors via Netty
- **GLUE web IDE** — edit Groovy job source code directly in the admin console

### Admin Console

- **React 19 + TypeScript + Ant Design 5** — modern SPA with fast builds via Vite
- **JWT authentication** — stateless auth with role-based access control (admin/normal)
- **Dark mode** — system-aware theme switching
- **Internationalization** — English and Chinese with i18next
- **Mobile responsive** — adaptive layouts for all screen sizes
- **Dashboard** — job execution charts and scheduler status overview

### Infrastructure

- **Heartbeat discovery** — executors register every 30 seconds, stale after 90 seconds
- **Docker Compose** — full-stack deployment with admin, UI (Nginx), and worker containers
- **OpenAPI** — RESTful admin API with comprehensive endpoint coverage

## Architecture

```
                    ┌─────────────────────────────────────────┐
                    │              Admin Server                │
  ┌──────────┐     │  ┌───────────┐  ┌──────────────────────┐ │     ┌──────────────┐
  │  Admin UI │────▶│  │  REST API │  │  Scheduling Engine   │ │────▶│  Executor 1   │
  │ (React)  │◀────│  │  (Spring) │  │  (Time-Ring + Pools) │ │◀────│  (Netty RPC)  │
  └──────────┘     │  └───────────┘  └──────────────────────┘ │     └──────────────┘
                    │       │                    │              │     ┌──────────────┐
                    │       ▼                    ▼              │────▶│  Executor 2   │
                    │  ┌─────────┐      ┌──────────────┐       │◀────│  (Netty RPC)  │
                    │  │  MySQL  │      │   Registry    │       │     └──────────────┘
                    │  │   (DB)  │      │  (Heartbeat)  │       │          ...
                    │  └─────────┘      └──────────────┘       │     ┌──────────────┐
                    └─────────────────────────────────────────┘────▶│  Executor N   │
                                                                ◀────│  (Netty RPC)  │
                                                                     └──────────────┘
```

For detailed architecture documentation, see the [`arch/`](arch/) directory:

| Doc | Topic |
|-----|-------|
| [01 — Registry & Discovery](arch/01-registry-and-discovery.md) | Heartbeat protocol, auto-registration, failure detection |
| [02 — API & RPC](arch/02-api-and-rpc.md) | REST API design, Netty RPC, serialization |
| [03 — Scheduling & Misfire](arch/03-scheduling-and-misfire.md) | Time-ring algorithm, pre-read, misfire handling |
| [04 — Executor Implementation](arch/04-executor-implementation.md) | Job thread lifecycle, handler types, block strategies |
| [05 — Log Management](arch/05-log-management.md) | Rolling logs, log streaming, retention |
| [06 — Database Layer](arch/06-database-layer.md) | Schema design, distributed locking, query patterns |
| [07 — Critical Analysis](arch/07-critical-analysis.md) | Bottlenecks, failure modes, improvement areas |
| [08 — SuperTask Pattern](arch/08-supertask-pattern.md) | Template jobs, batch copy, parameter variations |
| [09 — Batch Trigger](arch/09-batch-trigger-schedule-time.md) | Schedule-time triggers, backfilling |
| [10 — Import/Export](arch/10-import-export-configuration.md) | JSON-based job configuration portability |
| [11 — Frontend Architecture](arch/11-frontend-architecture.md) | React UI design, state management, i18n |

## Quick Start

### Prerequisites

- Docker and Docker Compose
- MySQL 8.0+ (or use an existing instance)
- Java 17+ and Maven 3.8+ (for building from source)

### Docker Compose

1. Clone the repository:

   ```bash
   git clone https://github.com/zombie12138/Orth.git
   cd Orth
   ```

2. Configure `.env` (edit MySQL connection and access token as needed):

   ```bash
   cp .env.example .env   # or edit .env directly
   ```

3. Build and start:

   ```bash
   mvn clean package -DskipTests
   docker-compose up -d
   ```

4. Access the admin console at `http://localhost:18081/orth-admin`
   - Default credentials: `admin` / `123456`

### Build from Source

```bash
# Build all modules
mvn clean install

# Run admin standalone (requires MySQL)
cd orth-admin && mvn spring-boot:run

# Run UI dev server
cd orth-ui && pnpm install && pnpm dev
```

## Configuration

Key environment variables (configured in `.env`):

| Variable | Default | Description |
|----------|---------|-------------|
| `MYSQL_HOST` | `172.17.0.1` | MySQL host address |
| `MYSQL_PORT` | `3306` | MySQL port |
| `MYSQL_DB` | `orth_job` | Database name |
| `MYSQL_USER` | `orth` | Database user |
| `ORTH_JOB_ACCESS_TOKEN` | `orth-secret-token` | Shared token between admin and executors |
| `ADMIN_HTTP_PORT` | `18080` | Admin server port (host) |
| `UI_HTTP_PORT` | `18081` | UI Nginx port (host) |
| `ADMIN_DEBUG_PORT` | `15005` | Admin JVM debug port |

## Module Structure

```
orth/
├── orth-core/                     # Core library (executor framework, OpenAPI, handlers)
├── orth-admin/                    # Scheduling center (Spring Boot)
├── orth-ui/                       # Admin console (React 19 + TypeScript + Ant Design 5)
├── orth-executor-samples/
│   ├── orth-executor-sample-springboot/       # Standard Spring Boot executor
│   ├── orth-executor-sample-springboot-ai/    # AI integration samples (Ollama, Dify)
│   └── orth-executor-sample-frameless/        # Standalone executor (no framework)
├── arch/                          # Architecture documentation (11 docs)
├── docker-compose.yml             # Full-stack deployment
└── .env                           # Environment configuration
```

## Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Backend | Java | 17 |
| Framework | Spring Boot | 3.5.8 |
| ORM | MyBatis | 3.0.5 |
| RPC | Netty | 4.2.7 |
| Scripting | Groovy | 5.0.2 |
| Frontend | React + TypeScript | 19.0 |
| UI Library | Ant Design | 5.23 |
| Build Tool | Vite | 6.1 |
| Database | MySQL | 8.0+ |
| Serialization | Gson | 2.13 |

## Contributing

Contributions are welcome. Please open an issue to discuss significant changes before submitting a pull request.

- [Issue Tracker](https://github.com/zombie12138/Orth/issues)

## License & Attribution

Orth is licensed under the [GNU General Public License v3.0](LICENSE).

Orth is a fork of [XXL-JOB](https://github.com/xuxueli/xxl-job) by [xuxueli](https://github.com/xuxueli), originally licensed under GPLv3. See [NOTICE](NOTICE) for full attribution and modification details.
