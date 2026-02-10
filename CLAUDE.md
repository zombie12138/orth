# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

XXL-Job is a distributed task scheduling framework with a centralized scheduler (admin) and distributed executors (workers). This is a customized fork called "Orth" under the Abyss project, optimized for high-performance Python batch data collection task scheduling.

**Version**: 3.3.0
**License**: GPLv3
**Java Version**: 17
**Tech Stack**: Spring Boot 3.5.8, MyBatis 3.0.5, Netty 4.2.7, Groovy 5.0.2

## Module Structure

```
xxl-job/
├── xxl-job-core/           # Core library (executor framework, OpenAPI, handlers)
├── xxl-job-admin/          # Web admin console and scheduling center
└── xxl-job-executor-samples/
    ├── xxl-job-executor-sample-springboot/      # Standard Spring Boot executor
    ├── xxl-job-executor-sample-springboot-ai/   # AI integration samples (Ollama, Dify)
    └── xxl-job-executor-sample-frameless/       # Frameless/standalone executor
```

## Build Commands

```bash
# Build entire project
mvn clean install

# Build with code formatting check
mvn clean install spotless:check

# Auto-format code (REQUIRED before commits)
mvn spotless:apply

# Build admin WAR only
cd xxl-job-admin && mvn clean package

# Build core library for release
cd xxl-job-core && mvn clean package -P release

# Skip tests (tests are skipped by default)
mvn clean install -DskipTests
```

## Running Locally

```bash
# Start full stack with Docker Compose
docker-compose up -d

# Access admin console
# URL: http://localhost:18080/xxl-job-admin
# Default credentials: admin/123456

# Check services
docker-compose ps

# View logs
docker-compose logs -f xxl-job-admin
docker-compose logs -f xxl-job-worker-1
```

**Debug Ports** (in Docker):
- Admin: 15005
- Worker 1: 15006
- Worker 2: 15007

**Environment**: Configure via `.env` file (MYSQL_HOST, MYSQL_PORT, XXL_JOB_ACCESS_TOKEN, etc.)

## Code Style

**CRITICAL**: This project uses **AOSP Style** (Google Java Style variant) enforced by Spotless.

- **Indentation**: 4 spaces (not 2)
- **Line length**: 100 characters
- **Import order**: Static imports first, then organized by package
- **ALWAYS run `mvn spotless:apply` before committing**

### Naming Conventions

- Classes: `PascalCase` (e.g., `JobScheduleHelper`, `ExecutorRouteStrategyEnum`)
- Methods: `camelCase` (e.g., `scheduleJobQuery`, `refreshNextTriggerTime`)
- Constants: `UPPER_SNAKE_CASE` (e.g., `PRE_READ_MS`, `ADMIN_ROLE`)
- Packages: lowercase with hierarchy (e.g., `scheduler.thread`, `scheduler.route.strategy`)

### Spring Conventions

- Use `@Resource` for dependency injection (not `@Autowired`)
- Use `@Component`, `@Service`, `@Controller` appropriately
- Use `Response<T>` wrapper with `ofSuccess()` / `ofFail()` factory methods

### Design Patterns in Use

- **Strategy Pattern**: Routing strategies (`ExecutorRouteStrategyEnum`), schedule types, misfire handling
- **Helper Pattern**: Thread management classes (`JobScheduleHelper`, `JobRegistryHelper`, `JobTriggerPoolHelper`)
- **Enum Registry**: Enums with `match()` method for safe lookups with defaults

## Core Architecture

### Scheduling Flow

1. **JobScheduleHelper** (runs every second):
   - Acquires distributed lock via `SELECT ... FOR UPDATE` on `xxl_job_lock`
   - Pre-reads jobs due within next 5 seconds from `xxl_job_info`
   - Pushes jobs to **time-ring buffer** (60 slots, one per second)
   - Updates `trigger_next_time` for next execution

2. **Ring Thread** (runs every second):
   - Checks current + 2 previous slots (drift tolerance)
   - Deduplicates by job ID
   - Routes to fast/slow thread pool based on job history
   - Triggers executor via HTTP POST to `/run` endpoint

3. **JobTriggerPoolHelper**:
   - **Fast Pool**: 200 threads + 2000 queue (for quick jobs)
   - **Slow Pool**: 100 threads + 5000 queue (for long-running jobs)
   - **Adaptive routing**: Jobs with 10+ timeouts (>500ms) in 1 minute move to slow pool

4. **JobRegistryHelper** (runs every 30s):
   - Processes executor heartbeats from `xxl_job_registry`
   - Updates `xxl_job_group.address_list` cache
   - Cleans stale entries (90s timeout)

### Database Schema (Key Tables)

- `xxl_job_info`: Job definitions, schedule config, next trigger time
- `xxl_job_group`: Executor groups and address cache
- `xxl_job_registry`: Executor heartbeats (30s interval)
- `xxl_job_log`: Execution logs with trigger/handle times
- `xxl_job_lock`: Distributed lock for single scheduler in cluster

### Executor-Side Components

- **XxlJobExecutor**: Main framework (Spring or Simple implementations)
- **ExecutorRegistryThread**: Sends heartbeat every 30s to admin
- **Embedded Netty Server**: Receives triggers, handles `/run`, `/kill`, `/log` endpoints
- **Job Handlers**: Bean, Script (Shell/Python/NodeJS/PHP), GLUE (Groovy), HTTP, Command

## Git Commit Conventions

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
type(scope): subject

body (detailed description)
```

**Types**: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`

**Scopes**: `admin`, `core`, `executor`, `rpc`, `scheduler`, `docker`, `arch`, `build`

**Example**:
```
feat(scheduler): add manual batch trigger with logical schedule time

- Add batchTrigger endpoint to trigger jobs with specific schedule times
- Support startTime/endTime range with instance count estimation
- Add misfire validation for NONE schedule type

This enables backfilling missed executions for batch data collection tasks.
```

## Key Design Insights

### Time-Ring Algorithm

- **Pre-read window**: 5 seconds ahead
- **Ring slots**: 60 (one per second, 0-59)
- **Slot check window**: Current + 2 previous (handles timing drift)
- **Misfire threshold**: 5+ seconds late
- **Strategies**: `DO_NOTHING` (skip), `FIRE_ONCE_NOW` (execute immediately)

### Distributed Locking

- Single scheduler ensures only one admin processes scheduling at a time
- Lock acquired via database `SELECT ... FOR UPDATE` on `xxl_job_lock.lock_name = 'schedule_lock'`
- Lock held for duration of schedule scan (~1 second)

### Service Discovery

- **Heartbeat-based**: Executors register every 30 seconds
- **Auto-discovery**: Admin updates group address cache every 30 seconds
- **Failure detection**: 90-second timeout (3 missed heartbeats)
- **No deep health checks**: Presence detection only

### Routing Strategies

9+ strategies available via `ExecutorRouteStrategyEnum`:
- `FIRST`, `LAST`: Fixed executor selection
- `ROUND`, `RANDOM`: Load distribution
- `CONSISTENT_HASH`: Consistent hashing by job ID
- `LFU`, `LRU`: Least frequently/recently used
- `FAILOVER`: Auto-retry on failure
- `BUSYOVER`: Route to idle executor
- `SHARDING_BROADCAST`: Execute on all executors with shard index

## Language and Documentation

- **Use English** for all code, comments, and documentation
- Gradually phase out Chinese content in iterations
- Add clear comments for:
  - Distributed locks (acquisition timing, release conditions)
  - State machine transitions (conditions, side effects, failure handling)
  - Retry logic and failure recovery mechanisms

## Development Workflow

### Architecture Documentation

- **ALWAYS update architecture docs** when adding new features or refactoring
- Create/update docs in `/arch/` directory for significant changes
- Document: API design, database schema changes, UI patterns, integration flows
- Include diagrams (Mermaid), code examples, and use case scenarios

### Build Process

- **ALWAYS run `mvn clean package` before building Docker images**
- Ensures Docker containers use the latest compiled code
- Required for admin module: `cd xxl-job-admin && mvn clean package -DskipTests`
- Run `mvn spotless:apply` before committing to ensure code formatting

## Testing

Tests are skipped by default (`maven.test.skip=true` in pom.xml). Framework configured but not actively maintained.

## Additional Resources

- **Architecture docs**: `/arch/` directory contains detailed analysis of registry, RPC, scheduling, executors, logs, database layer
- **Official docs**: `/doc/` directory (Chinese and English)
- **Cursor AI rules**: `.cursor/rules/` for coding standards and conventions
- **Official site**: https://www.xuxueli.com/xxl-job/
- **GitHub**: https://github.com/xuxueli/xxl-job

## Project Context (Orth Fork)

This fork is customized for the **Abyss project** with emphasis on:
- High-performance Python batch data collection
- Low-latency streaming data processing
- Enhanced manager-executor coordination
- Defensive programming: validate inputs, handle edge cases explicitly
- Modular, focused functions; avoid over-encapsulation
