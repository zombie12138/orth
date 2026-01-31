# Distributed Job Scheduling Framework - Deep Architecture Analysis Prompt

## Context

You are a senior AI Infrastructure Architect and System Analysis Expert specializing in distributed systems, job scheduling frameworks, and high-concurrency architectures.

## Target Framework

**Framework Name:** [e.g., PowerJob, ElasticJob, Quartz Cluster, etc.]

**Project Repository:** [GitHub/GitLab URL]

**Version:** [Specific version to analyze]

## Analysis Objectives

Conduct a comprehensive architectural analysis of the target distributed job scheduling framework, focusing on:

1. **Service Registry & Discovery**
2. **API & RPC Communication**
3. **Scheduling & Misfire Handling**
4. **Executor Implementation**
5. **Log Management & Callback**
6. **Database Layer & Persistence**
7. **Critical Analysis (Flaws & Bottlenecks)**

---

## Analysis Requirements

### 1. Output Format

- Generate **7 separate Markdown files** in an `arch/` directory
- Number them sequentially: `01-` through `07-`
- Use **Mermaid diagrams** as the primary content, with text as supplementary explanation
- Keep the analysis at a **high-level overview** (not code/SQL granularity)

### 2. Content Guidelines

#### **Overview Level (NOT Deep Code)**
- Focus on **conceptual architecture**, not line-by-line code analysis
- Use **schematic diagrams** (e.g., time-wheel visualization, thread models)
- Show **key state transitions** and **critical variables**
- Avoid detailed SQL queries or specific code snippets
- Emphasize **data flow**, **component interactions**, and **lifecycle management**

#### **Professional Terminology**
- Describe each module's **core responsibilities**, **tech stack**, and **implementation mechanisms**
- Use industry-standard terms (e.g., "time-ring buffer", "pessimistic locking", "MVCC", "sharding broadcast")

#### **Mermaid Diagrams - Key Focus Areas**
Create detailed Mermaid diagrams for:
- Thread management and lifecycle
- Distributed coordination and locking
- Task/job state machines
- Log lifecycle and callback flow
- Request/response patterns
- Service discovery and heartbeat
- Time-wheel scheduling visualization
- Database transaction boundaries

---

## Detailed Module Breakdown

### File 01: Registry & Discovery

**File Name:** `01-registry-and-discovery.md`

**Content Requirements:**
1. **Heartbeat Mechanism**
   - Registration flow (Executor → Admin/Scheduler)
   - Heartbeat interval and timeout settings
   - Auto-cleanup of stale entries

2. **Service Discovery**
   - How Admin/Scheduler discovers available executors
   - Failover and high availability
   - Multi-scheduler coordination (if applicable)

3. **Address Management**
   - Static address vs. dynamic discovery
   - Executor grouping strategies

4. **Mermaid Diagrams:**
   - Registration flow sequence
   - Heartbeat timing diagram
   - Discovery and failover state machine
   - Registry table schema (if DB-based)

**Key Variables/Fields to Highlight:**
- Registration timestamp
- Last heartbeat time
- Executor address/port
- App name/group

---

### File 02: API & RPC Communication

**File Name:** `02-api-and-rpc.md`

**Content Requirements:**
1. **Communication Patterns**
   - Admin → Executor (trigger job)
   - Executor → Admin (callback, heartbeat)
   - Protocol: HTTP/gRPC/custom

2. **Core Endpoints**
   - Job trigger API
   - Callback API
   - Registration/heartbeat API
   - Admin query API

3. **Authentication & Security**
   - Token-based auth
   - Access key validation
   - IP whitelist (if any)

4. **Routing Strategies**
   - Round-robin, random, consistent hashing
   - Sharding broadcast
   - Failover and retry

5. **Block Strategies** (Executor-side)
   - Serial execution
   - Discard later
   - Cover early

6. **Mermaid Diagrams:**
   - Bidirectional RPC flow
   - Routing strategy decision tree
   - Authentication flow
   - Block strategy state machine

**Key Components to Highlight:**
- HTTP client/server implementation (Netty, OkHttp, etc.)
- Dynamic proxy usage
- Request/response models
- Timeout and retry configuration

---

### File 03: Scheduling & Misfire

**File Name:** `03-scheduling-and-misfire.md`

**Content Requirements:**
1. **Scheduling Algorithm**
   - Time-wheel (ring buffer) or cron-based
   - Pre-read strategy (e.g., fetch next 5s tasks)
   - Thread model (single/dual-thread scheduler)

2. **Job State Machine**
   - States: Pending, Running, Success, Failed, Stopped
   - Transition conditions

3. **Misfire Handling**
   - Detection mechanism
   - Strategies: DO_NOTHING, FIRE_ONCE_NOW, etc.

4. **Distributed Coordination**
   - Pessimistic lock (SELECT FOR UPDATE)
   - Optimistic lock (version field)
   - Leader election (if applicable)

5. **Thread Pool Management**
   - Fast/slow trigger pools
   - Adaptive switching
   - Queue overflow handling

6. **Mermaid Diagrams:**
   - Time-wheel schematic (fetch → ring → dispatch)
   - Dual-thread model (Schedule Thread + Ring Thread)
   - Job state machine
   - Misfire detection and handling flow
   - Distributed lock acquisition/release

**Key Variables to Highlight:**
- Pre-read window (e.g., 5000ms)
- Ring data structure
- Trigger timestamp vs. schedule timestamp
- Misfire threshold

---

### File 04: Executor Implementation

**File Name:** `04-executor-implementation.md`

**Content Requirements:**
1. **Thread Model**
   - Thread-per-job vs. thread pool
   - Job queue management
   - Idle thread cleanup

2. **Job Context**
   - Thread-local context (job ID, params, shard index)
   - Context propagation to child threads

3. **Handler Types**
   - Method handler (Spring Bean)
   - Script handler (Shell, Python, etc.)
   - Glue handler (dynamic code)

4. **Script Execution**
   - Process creation
   - Environment variables
   - Output redirection

5. **Block Strategy Enforcement**
   - Serial: queue tasks
   - Discard: reject new tasks
   - Cover: interrupt current task

6. **Callback Mechanism**
   - Async callback queue
   - Retry on failure (file-based)
   - Batch callback optimization

7. **Mermaid Diagrams:**
   - JobThread lifecycle (init → run → idle → cleanup)
   - Context creation and propagation
   - Handler type decision flow
   - Script execution process
   - Callback push and retry flow

**Key Components to Highlight:**
- Job trigger queue
- InheritableThreadLocal usage
- Script environment variables
- Callback file storage format

---

### File 05: Log Management

**File Name:** `05-log-management.md`

**Content Requirements:**
1. **Dual Storage Architecture**
   - MySQL: metadata (id, status, timestamps)
   - File system: detailed logs (stdout/stderr)

2. **Log Lifecycle**
   - Creation: at job trigger
   - Update: during execution (handle code, msg)
   - Finalization: on callback (status, duration)

3. **Callback Processing**
   - Async callback thread pool
   - Batch processing
   - Failure retry with exponential backoff

4. **Lost Job Detection**
   - Timeout-based scanning
   - Recovery mechanism

5. **Alarm Status**
   - Failure alarm trigger
   - Retry control (avoid spam)

6. **Message Truncation**
   - Length limits (e.g., 15000 chars)
   - Truncation markers

7. **Mermaid Diagrams:**
   - Log lifecycle (create → update → finalize)
   - Dual storage architecture
   - Callback flow (executor → admin)
   - Lost job detection timing
   - Alarm retry logic

**Key Fields to Highlight:**
- trigger_time, schedule_time, handle_time
- trigger_code, handle_code
- executor_address, executor_handler
- alarm_status

---

### File 06: Database Layer

**File Name:** `06-database-layer.md`

**Content Requirements:**
1. **Core Schema**
   - Job config table
   - Job log table
   - Executor registry table
   - Distributed lock table

2. **Data Volume & Scale**
   - Supported job/log volume
   - ID generation strategy (auto-increment, snowflake, etc.)

3. **Transaction Patterns**
   - Job scheduling transaction boundary
   - Log update atomicity
   - Lock table usage

4. **Index Strategy**
   - Query patterns
   - Composite indexes
   - Missing indexes (if any)

5. **Query Patterns**
   - Pre-read scheduling query
   - Log pagination
   - Executor registry query

6. **Mermaid Diagrams:**
   - Core table relationships (ER diagram)
   - Transaction boundaries in scheduling flow
   - Index usage in key queries
   - Lock table state machine

**Key Tables to Highlight:**
- Job config table structure
- Job log table structure
- Registry table structure
- Lock table structure

---

### File 07: Critical Analysis (Hardcore Critique)

**File Name:** `07-critical-analysis.md`

**Content Requirements:**

**Goal:** Identify **flaws**, **performance bottlenecks**, **scalability issues**, and **security vulnerabilities** across all modules with the highest Code Review standards. Do NOT be polite—be brutally honest.

#### **For Each Module (Registry, API, Scheduling, Executor, Log, DB):**

1. **Flaws & Design Issues**
   - Architectural defects
   - Race conditions
   - Edge case mishandling

2. **Performance Bottlenecks**
   - Single points of contention
   - Inefficient algorithms
   - Memory leaks
   - Thread starvation

3. **Scalability Problems**
   - Single-node limitations
   - Database hot spots
   - Lock contention at scale

4. **Security Vulnerabilities**
   - Authentication weaknesses
   - Data loss risks
   - Injection vulnerabilities

5. **Operational Risks**
   - Silent failures
   - Poor observability
   - Recovery difficulties

#### **Severity Rating:**
- 🔴 **Critical:** Data loss, security breach, system crash
- 🟠 **High:** Significant performance degradation, scale limit
- 🟡 **Medium:** Suboptimal design, maintainability issue
- 🟢 **Low:** Code style, minor optimization

#### **Format:**
For each issue:
```
### [Module] - [Issue Title]

**Severity:** [Critical/High/Medium/Low]

**Problem:**
[Describe the specific flaw]

**Impact:**
[What breaks, how bad, when it happens]

**Root Cause:**
[Why this design/code is problematic]

**Suggested Fix:**
[High-level solution approach]
```

**Mermaid Diagrams:**
- Bottleneck visualization (e.g., lock contention points)
- Failure scenario flow
- Scale limit diagrams

---

## Output Language

**English** (professional technical writing)

---

## Example Usage

```bash
# Step 1: Clone target framework
git clone https://github.com/PowerJob/PowerJob.git
cd PowerJob

# Step 2: Create arch directory
mkdir -p arch

# Step 3: Use this prompt with AI (Claude, GPT-4, etc.)
# Paste this entire prompt and specify:
# - Framework name: PowerJob
# - Version: v4.3.0 (or latest)
# - Repository: https://github.com/PowerJob/PowerJob
```

---

## Key Differences from Code Review

- **Granularity:** Overview level, not line-by-line
- **Focus:** Architecture, data flow, state machines
- **Diagrams:** Heavy use of Mermaid for visualization
- **Critique:** Separate file for systematic flaw analysis

---

## Success Criteria

✅ All 7 files generated with consistent naming and structure  
✅ Mermaid diagrams are the primary content (text is supplementary)  
✅ No detailed code snippets or SQL queries  
✅ High-level conceptual clarity  
✅ Critical analysis is comprehensive and severity-rated  
✅ Professional terminology throughout  
✅ Can be used as onboarding material for new team members  

---

## Notes

- If the target framework has unique features (e.g., DAG workflows, MapReduce support, workflow engine), add an **08-advanced-features.md** file
- Adjust the file order based on the framework's core design (e.g., if it's workflow-first, put workflow before scheduling)
- Compare with XXL-JOB/Orth architecture where applicable to highlight differences

---

## Revision History

- **v1.0** (2026-01-30): Initial template based on Orth (XXL-JOB fork) analysis
