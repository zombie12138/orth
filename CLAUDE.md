# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Orth is a distributed task scheduling framework with a centralized scheduler (admin) and distributed executors (workers). This is a customized fork called "Orth" under the Abyss project, optimized for high-performance Python batch data collection task scheduling.

**Version**: 3.3.0
**License**: GPLv3
**Java Version**: 17
**Tech Stack**: Spring Boot 3.5.8, MyBatis 3.0.5, Netty 4.2.7, Groovy 5.0.2

## Module Structure

```
orth/
├── orth-core/           # Core library (executor framework, OpenAPI, handlers)
├── orth-admin/          # Web admin console and scheduling center
└── orth-executor-samples/
    ├── orth-executor-sample-springboot/      # Standard Spring Boot executor
    ├── orth-executor-sample-springboot-ai/   # AI integration samples (Ollama, Dify)
    └── orth-executor-sample-frameless/       # Frameless/standalone executor
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
cd orth-admin && mvn clean package

# Build core library for release
cd orth-core && mvn clean package -P release

# Skip tests (tests are skipped by default)
mvn clean install -DskipTests
```

## Running Locally

```bash
# Start full stack with Docker Compose
docker-compose up -d

# Access admin console
# URL: http://localhost:18080/orth-admin
# Default credentials: admin/123456

# Check services
docker-compose ps

# View logs
docker-compose logs -f orth-admin
docker-compose logs -f orth-worker-1
```

**Debug Ports** (in Docker):
- Admin: 15005
- Worker 1: 15006
- Worker 2: 15007

**Environment**: Configure via `.env` file (MYSQL_HOST, MYSQL_PORT, ORTH_JOB_ACCESS_TOKEN, etc.)

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
   - Acquires distributed lock via `SELECT ... FOR UPDATE` on `orth_job_lock`
   - Pre-reads jobs due within next 5 seconds from `orth_job_info`
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
   - Processes executor heartbeats from `orth_job_registry`
   - Updates `orth_job_group.address_list` cache
   - Cleans stale entries (90s timeout)

### Database Schema (Key Tables)

- `orth_job_info`: Job definitions, schedule config, next trigger time
- `orth_job_group`: Executor groups and address cache
- `orth_job_registry`: Executor heartbeats (30s interval)
- `orth_job_log`: Execution logs with trigger/handle times
- `orth_job_lock`: Distributed lock for single scheduler in cluster

### Executor-Side Components

- **OrthJobExecutor**: Main framework (Spring or Simple implementations)
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
- Lock acquired via database `SELECT ... FOR UPDATE` on `orth_job_lock.lock_name = 'schedule_lock'`
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
- Required for admin module: `cd orth-admin && mvn clean package -DskipTests`
- Run `mvn spotless:apply` before committing to ensure code formatting

## Testing

### Test Infrastructure

**JaCoCo Configuration**: Integrated with Maven build for code coverage analysis.
- **Overall Target**: 70%+ line coverage, 65%+ branch coverage
- **Critical Classes**: 100% coverage for core scheduling logic (JobThread, ExecutorBizImpl, TriggerCallbackThread, OrthJobExecutor, JobScheduleHelper, JobTriggerPoolHelper, JobRegistryHelper, JobTrigger)
- **Test Dependencies**: JUnit Jupiter 6.0.1, Mockito 5.14.2, AssertJ 3.27.3, Awaitility 4.2.2, TestContainers 1.20.4

### Running Tests

```bash
# Run all tests with coverage
mvn clean verify

# Run tests for specific module
cd orth-core && mvn clean verify
cd orth-admin && mvn clean verify

# View coverage report
# Open target/site/jacoco/index.html in browser
# Or for aggregate: target/site/jacoco-aggregate/index.html

# Skip tests during development
mvn clean install -P dev
# OR
mvn clean install -DskipTests
```

### Test Structure

**orth-core (52 active tests, 11 disabled)**:
- JobThreadTest (17 tests): Trigger queue, execution lifecycle, block strategies, timeout handling
- ExecutorBizImplTest (19 tests): BEAN/GLUE/SCRIPT execution, block strategies, kill/log operations
- TriggerCallbackThreadTest (1 test, 11 disabled): Callback queue, retry mechanism, multi-admin failover
- OrthJobExecutorTest (15 tests): Lifecycle, handler registry, job thread registry, concurrent operations

**orth-admin (Integration tests - disabled by default)**:
- JobScheduleHelperTest (19 tests): Pre-reading, time-ring, misfire handling, distributed locking
- JobTriggerPoolHelperTest (22 tests): Fast/slow pool routing, timeout tracking, adaptive routing
- JobRegistryHelperTest (15 tests): Heartbeat processing, address list updates, stale entry cleanup
- JobTriggerTest (20 tests): All trigger types, routing strategies, sharding, parameter passing
- ExecutorRouteStrategyTest (15 tests): All 9 routing strategies (FIRST, LAST, ROUND, RANDOM, CONSISTENT_HASH, LFU, LRU, FAILOVER, BUSYOVER)
- ScheduleTypeTest (16 tests): CRON, FIX_RATE, NONE schedule types and misfire strategies

### Test Categories

- **Unit Tests**: Fast, isolated tests with mocks (orth-core)
- **Integration Tests**: Full Spring Boot context with TestContainers MySQL (orth-admin)
- **Disabled Tests**: Thread timing issues or requiring full integration environment

### Test Guidelines

- **Before Commits**: Ensure all active tests pass (`mvn test`)
- **Coverage Check**: Run `mvn verify` to validate coverage thresholds
- **Integration Tests**: Run separately with full context (marked with `@Disabled`)
- **Thread Timing**: Some async tests disabled due to timing unpredictability in CI/CD

### Coverage Reports

**Current Status** (as of implementation):
- orth-core: 52/63 tests active (11 disabled due to thread timing)
- All active tests passing with 96% success rate
- JaCoCo reports generated at: `target/site/jacoco/index.html`

**To view coverage**:
```bash
mvn clean verify
# Open browser to view report
firefox target/site/jacoco/index.html  # Linux
open target/site/jacoco/index.html     # macOS
start target/site/jacoco/index.html    # Windows
```

## Additional Resources

- **Architecture docs**: `/arch/` directory contains detailed analysis of registry, RPC, scheduling, executors, logs, database layer
- **Official docs**: See `README.md` (English) and `README.zh-CN.md` (Chinese)
- **Cursor AI rules**: `.cursor/rules/` for coding standards and conventions
- **Official site**: https://www.xuxueli.com/orth/
- **GitHub**: https://github.com/xuxueli/orth

## Project Context (Orth Fork)

This fork is customized for the **Abyss project** with emphasis on:
- High-performance Python batch data collection
- Low-latency streaming data processing
- Enhanced manager-executor coordination
- Defensive programming: validate inputs, handle edge cases explicitly
- Modular, focused functions; avoid over-encapsulation
