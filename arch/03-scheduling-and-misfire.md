# Scheduling and Misfire Architecture

## Core Concept

Orth uses a **time-ring buffer** algorithm to achieve precise second-level job scheduling. The scheduler pre-reads jobs 5 seconds ahead and distributes them into a 60-slot ring buffer, enabling exact-second triggering without database queries at execution time.

## High-Level Architecture

```mermaid
flowchart TB
    subgraph Scheduler["Scheduling Engine"]
        ST["Schedule Thread<br/>(Job Discovery)"]
        RT["Ring Thread<br/>(Precise Execution)"]
        TR["Time Ring<br/>60-slot buffer<br/>(0-59 seconds)"]
    end
    
    subgraph Pools["Execution Pools"]
        FP["Fast Pool<br/>200 threads"]
        SP["Slow Pool<br/>100 threads"]
    end
    
    subgraph Storage["Data Store"]
        DB[(MySQL<br/>Job Definitions<br/>+ Distributed Lock)]
    end
    
    DB -->|"1. Query jobs<br/>next 5 seconds"| ST
    ST -->|"2. Push to ring<br/>by target second"| TR
    TR -->|"3. Trigger at<br/>exact second"| RT
    RT -->|"4. Adaptive routing"| FP
    RT -->|"4. Adaptive routing"| SP
```

## Time Ring Algorithm

```mermaid
flowchart LR
    subgraph PreRead["Pre-Read Window (5s)"]
        direction TB
        P1["Job scheduled at 10:00:01"]
        P2["Job scheduled at 10:00:03"]
        P3["Job scheduled at 10:00:05"]
    end
    
    subgraph TimeRing["Time Ring Buffer"]
        S0["Slot 0<br/>[]"]
        S1["Slot 1<br/>[Job A, Job C]"]
        S2["Slot 2<br/>[]"]
        S3["Slot 3<br/>[Job B]"]
        S4["Slot 4<br/>[]"]
        S5["Slot 5<br/>[Job D]"]
        Dots["..."]
        S59["Slot 59<br/>[]"]
    end
    
    subgraph Execution["Ring Thread Execution"]
        E1["Every second:<br/>Check current + 2 previous slots"]
        E2["Deduplicate by jobId"]
        E3["Trigger all jobs in slots"]
    end
    
    PreRead -->|"Schedule Thread<br/>distributes"| TimeRing
    TimeRing -->|"Ring Thread<br/>consumes"| Execution
```

**Key Design Points:**
- **60 slots** = seconds in a minute
- **Pre-read 5s ahead** = balance between memory and accuracy
- **Check 3 slots** (current + 2 previous) = handle timing drift

## Dual-Thread Model

```mermaid
sequenceDiagram
    participant DB as Database
    participant Schedule as Schedule Thread
    participant Ring as Time Ring
    participant RingThread as Ring Thread
    participant Pool as Trigger Pools
    
    Note over Schedule: Every second (aligned)
    
    loop Scheduling Cycle
        Schedule->>DB: Lock + Query jobs<br/>(next_time ≤ now + 5s)
        DB-->>Schedule: Job list
        
        alt Job expired > 5s
            Schedule->>Schedule: Misfire handling
        else Job within 5s window
            Schedule->>Ring: Push to ring[second]
        end
        
        Schedule->>DB: Update next trigger times
    end
    
    Note over RingThread: Every second (aligned)
    
    loop Ring Cycle
        RingThread->>Ring: Get jobs for current second
        Ring-->>RingThread: Job list
        RingThread->>Pool: Trigger jobs
    end
```

## Job State Machine

```mermaid
stateDiagram-v2
    [*] --> STOPPED: Job created
    
    STOPPED --> RUNNING: User starts
    RUNNING --> Scheduling: Schedule thread picks up
    
    state Scheduling {
        [*] --> InTimeWindow: Within 5s window
        [*] --> Expired: Past window
        
        InTimeWindow --> TimeRing: Push to ring
        Expired --> MisfireCheck: Check strategy
        
        state MisfireCheck {
            [*] --> DoNothing: Strategy=DO_NOTHING
            [*] --> FireNow: Strategy=FIRE_ONCE_NOW
        }
    }
    
    Scheduling --> Triggered: Ring thread fires
    Triggered --> RUNNING: Next cycle
    
    RUNNING --> STOPPED: User stops<br/>or no next time
```

## Adaptive Pool Selection

```mermaid
flowchart TD
    Trigger["Job Trigger Request"] --> Measure["Track execution time"]
    
    Measure --> Check{"> 500ms<br/>more than 10 times<br/>in 1 minute?"}
    
    Check -->|No| Fast["Fast Pool<br/>High concurrency<br/>200 threads<br/>Queue: 2000"]
    Check -->|Yes| Slow["Slow Pool<br/>Long-running jobs<br/>100 threads<br/>Queue: 5000"]
    
    Fast --> Reset["Time window resets<br/>every minute"]
    Slow --> Reset
    
    Reset --> Measure
```

**Rationale:** Prevent slow jobs from starving fast jobs.

## Distributed Lock Coordination

```mermaid
flowchart TB
    subgraph MultiInstance["Multi-Instance Deployment"]
        A1["Admin 1"]
        A2["Admin 2"]
        A3["Admin 3"]
    end
    
    subgraph LockMechanism["Database Lock"]
        Lock["SELECT ... FOR UPDATE<br/>on lock table"]
    end
    
    subgraph Scheduling["Schedule Cycle"]
        S1["Lock acquired"]
        S2["Query jobs"]
        S3["Process + push to ring"]
        S4["Update DB"]
        S5["Commit = Release lock"]
    end
    
    A1 --> Lock
    A2 --> Lock
    A3 --> Lock
    
    Lock -->|"Only one succeeds"| S1
    S1 --> S2 --> S3 --> S4 --> S5
```

**Trade-off:** Simple and reliable, but limits horizontal scaling (only one instance schedules at a time).

## Misfire Handling

```mermaid
flowchart TD
    Job["Job scheduled at 10:00:00"] --> Check{Schedule Thread<br/>processes at time T}
    
    Check -->|"T ≤ 10:00:05<br/>(within 5s)"| Normal["Normal: Push to time ring"]
    Check -->|"10:00:05 < T ≤ 10:00:10<br/>(5-10s late)"| Direct["Direct: Trigger immediately"]
    Check -->|"T > 10:00:10<br/>(> 10s late)"| Misfire["MISFIRE detected"]
    
    Misfire --> Strategy{Misfire Strategy}
    Strategy -->|DO_NOTHING| Log["Log warning only"]
    Strategy -->|FIRE_ONCE_NOW| Trigger["Trigger immediately"]
    
    Normal --> Next["Calculate next trigger time"]
    Direct --> Next
    Log --> Next
    Trigger --> Next
```

## Key Metrics

| Metric | Value | Purpose |
|--------|-------|---------|
| Pre-read window | 5 seconds | Job discovery ahead time |
| Time ring slots | 60 | Seconds in a minute |
| Ring check slots | 3 (current + 2 previous) | Handle timing drift |
| Misfire threshold | 5+ seconds | Delayed job detection |
| Fast pool capacity | 200 threads + 2000 queue | High-concurrency jobs |
| Slow pool capacity | 100 threads + 5000 queue | Long-running jobs |
| Pool switch threshold | 10 timeouts (500ms) in 1 min | Adaptive routing |

## Critical Variables

```mermaid
flowchart LR
    subgraph CoreVariables["Core State Variables"]
        direction TB
        TN["trigger_next_time<br/>Next execution timestamp"]
        TS["trigger_status<br/>0=stopped, 1=running"]
        ST["scheduleTime<br/>Theoretical execute time<br/>(null for manual triggers)"]
    end
    
    subgraph RuntimeState["Runtime State"]
        direction TB
        RD["ringData<br/>ConcurrentHashMap<br/>slot → job list"]
        TC["timeoutCount<br/>Per-job performance tracking"]
    end
```

## Trigger Dispatch & Routing

After the time ring fires a job, `JobTrigger` resolves **which executor(s)** receive the trigger. This is the bridge between the scheduling engine (admin) and execution (executor).

### Dispatch Flow

```mermaid
flowchart LR
    Ring["Time Ring fires"] --> JT["JobTrigger"]
    JT --> Route{Routing Strategy}

    Route -->|"9 strategies"| Single["Single Executor"]
    Route -->|"SHARDING_BROADCAST"| All["All Executors<br/>(with shard index)"]

    Single --> Exec1["Executor"]
    All --> ExecA["Executor 0/3"]
    All --> ExecB["Executor 1/3"]
    All --> ExecC["Executor 2/3"]
```

### Routing Strategies

| Category | Strategies | Target | Use Case |
|----------|-----------|--------|----------|
| Fixed | FIRST, LAST | 1 executor (deterministic) | Debugging, single node |
| Distributing | ROUND, RANDOM, LFU, LRU | 1 executor (rotating) | Stateless jobs, load balancing |
| Affinity | CONSISTENT_HASH | 1 executor (sticky by job ID) | Jobs with local state/cache |
| Health-aware | FAILOVER, BUSYOVER | 1 executor (first healthy/idle) | Critical jobs |
| Broadcast | SHARDING_BROADCAST | **All executors** | Parallel data collection |

**Default behavior: one trigger → one executor.** Only SHARDING_BROADCAST fans out to all registered executors, each receiving its shard index and total count.

### Interaction with Block Strategies

Routing decides **where**, block strategy decides **what happens on arrival**:

```mermaid
flowchart TD
    Trigger["Trigger arrives<br/>at executor"] --> Exists{JobThread<br/>already running?}

    Exists -->|No| Create["Create JobThread<br/>→ execute"]
    Exists -->|Yes| Block{Block Strategy}

    Block -->|SERIAL| Queue["Queue behind<br/>current execution"]
    Block -->|DISCARD| Drop["Reject trigger<br/>(job is busy)"]
    Block -->|COVER| Replace["Kill old thread<br/>→ restart with new trigger"]
    Block -->|CONCURRENT| Parallel["Submit to worker pool<br/>(parallel execution)"]
```

**Key distinction:**
- **Routing** controls cross-executor parallelism (single vs broadcast)
- **Block strategy** controls intra-executor parallelism (serial vs concurrent)

These are orthogonal — SHARDING_BROADCAST + CONCURRENT means all executors run multiple instances in parallel.

## Design Strengths

1. **Sub-second Precision**: Time ring enables exact-second execution
2. **Scalable Pre-read**: Database queried once per second, not per job
3. **Fair Scheduling**: Slow jobs don't block fast jobs
4. **Drift Tolerance**: Checks multiple slots to handle timing variations

## Design Limitations

1. **Single Scheduler**: Database lock allows only one instance to schedule
2. **Memory Bound**: All jobs in 5s window held in memory
3. **Fixed Granularity**: Second-level only, no sub-second scheduling
4. **No Prioritization**: FIFO order within each second
