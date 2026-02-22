# Executor Implementation Architecture

## Core Concept

The executor implements a **thread-per-job** model where each job gets a dedicated `JobThread` with a trigger queue. This ensures job isolation and enables different block strategies (serial, discard, cover).

## High-Level Architecture

```mermaid
flowchart TB
    subgraph Executor["Executor Process"]
        Server["Netty Server<br/>Receives triggers"]
        Registry["Thread Registry<br/>jobId → JobThread"]
        Handlers["Handler Repository<br/>name → IJobHandler"]
    end
    
    subgraph Threads["Job Threads"]
        JT1["JobThread 1<br/>Queue + Handler"]
        JT2["JobThread 2<br/>Queue + Handler"]
        JTN["JobThread N<br/>Queue + Handler"]
    end
    
    subgraph Output["Output"]
        Logs["File Logs<br/>/logPath/yyyy-MM-dd/"]
        Callbacks["Callbacks<br/>to Admin"]
    end
    
    Server -->|"1. Get or create"| Registry
    Registry -->|"2. Route to"| JT1
    Registry --> JT2
    Registry --> JTN
    
    JT1 -->|"Execute"| Handlers
    JT1 --> Logs
    JT1 --> Callbacks
```

## JobThread Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Created: First trigger for job ID
    
    state Created {
        [*] --> Initialize
        Initialize: Set thread name
        Initialize: Create queue
        Initialize: handler.init()
    }
    
    Created --> Running: thread.start()
    
    state Running {
        [*] --> Polling
        
        Polling --> ProcessTrigger: Trigger in queue
        Polling --> IdleCheck: No trigger (3s timeout)
        
        ProcessTrigger --> Execute: Create context
        Execute --> Callback: Push result
        Callback --> Polling
        
        IdleCheck --> Polling: idleCount ≤ 30
        IdleCheck --> SelfRemove: idleCount > 30<br/>AND queue empty
    }
    
    Running --> Stopping: toStop flag set
    
    state Stopping {
        [*] --> DrainQueue
        DrainQueue: Process remaining
        DrainQueue --> Cleanup
        Cleanup: handler.destroy()
    }
    
    Stopping --> [*]
```

**Key Points:**
- Thread starts on first trigger
- Auto-cleanup after 90+ seconds idle (30 × 3s poll)
- Graceful shutdown drains queue

## Context Propagation

```mermaid
flowchart LR
    subgraph ThreadLocal["InheritableThreadLocal"]
        CTX["OrthJobContext<br/>• jobId<br/>• jobParam<br/>• logId<br/>• scheduleTime<br/>• shardIndex/Total"]
    end
    
    subgraph Access["Access via OrthJobHelper"]
        A1["getJobParam()"]
        A2["getScheduleTime()"]
        A3["log(message)"]
        A4["handleSuccess()"]
        A5["handleFail(msg)"]
    end
    
    JobThread["JobThread<br/>Sets context"] --> ThreadLocal
    ThreadLocal --> Access
    
    subgraph ChildThreads["Inherited by Children"]
        User["User spawned threads<br/>also have access"]
    end
    
    ThreadLocal -.->|"Inherits"| ChildThreads
```

**Design Benefit:** User code can spawn threads and still access job context.

## Handler Types

```mermaid
flowchart TD
    subgraph Handlers["IJobHandler Implementations"]
        Method["MethodJobHandler<br/>@OrthJob annotated methods"]
        Script["ScriptJobHandler<br/>Python/Shell/PowerShell"]
        Glue["GlueJobHandler<br/>Groovy code"]
    end
    
    subgraph Registration["Handler Registration"]
        Spring["Spring scans @OrthJob<br/>at startup"]
        Runtime["Runtime creates<br/>Script/Glue handlers"]
    end
    
    Spring --> Method
    Runtime --> Script
    Runtime --> Glue
    
    subgraph Execution["Execution Flow"]
        E1["handler.init()"]
        E2["handler.execute()"]
        E3["handler.destroy()"]
    end
    
    Method --> Execution
    Script --> Execution
    Glue --> Execution
```

## Script Execution Flow

```mermaid
sequenceDiagram
    participant JT as JobThread
    participant SH as ScriptHandler
    participant FS as File System
    participant Proc as Script Process
    
    JT->>SH: execute()
    SH->>SH: Build environment variables<br/>ORTH_JOB_*
    
    alt Script file doesn't exist
        SH->>FS: Create script file<br/>{jobId}_{updateTime}.py
        SH->>FS: Write source code
    end
    
    SH->>Proc: Start process<br/>python script.py [params]
    
    par Capture output
        Proc->>FS: stdout → log file
        Proc->>FS: stderr → log file
    end
    
    Proc-->>SH: Exit code
    
    alt Exit code = 0
        SH->>JT: handleSuccess()
    else Exit code ≠ 0
        SH->>JT: handleFail("exit value...")
    end
```

**Environment Variables Passed:**
- `ORTH_JOB_ID`, `ORTH_JOB_PARAM`
- `ORTH_JOB_LOG_ID`
- `ORTH_JOB_SCHEDULE_TIME` (ISO 8601 or empty for manual)
- `ORTH_JOB_TRIGGER_TIME` (ISO 8601)
- `ORTH_JOB_SHARD_INDEX`, `ORTH_JOB_SHARD_TOTAL`

## Block Strategy Execution

```mermaid
flowchart TD
    Trigger["Trigger Arrives"] --> Check{Block Strategy}
    
    Check -->|SERIAL| Serial["Add to queue<br/>Execute in order"]
    Check -->|DISCARD| Discard["Reject if running<br/>or queue not empty"]
    Check -->|COVER| Cover["Kill existing thread<br/>Create new thread"]
    
    subgraph Examples["Use Case Examples"]
        Ex1["SERIAL:<br/>Database migration<br/>(sequential only)"]
        Ex2["DISCARD:<br/>Data fetch<br/>(skip if busy)"]
        Ex3["COVER:<br/>Cache warm<br/>(always latest)"]
    end
```

## Timeout Enforcement

```mermaid
sequenceDiagram
    participant JT as JobThread
    participant Future as FutureTask
    participant Worker as Worker Thread
    
    JT->>Future: Create with timeout
    JT->>Worker: Start execution
    Worker->>Worker: handler.execute()
    
    par Timeout watch
        JT->>Future: get(timeout, SECONDS)
    end
    
    alt Completes in time
        Worker-->>Future: Success
        Future-->>JT: Result
    else Timeout
        JT->>JT: handleTimeout()
        JT->>Worker: interrupt()
        Note over Worker: May not stop immediately<br/>(depends on handler)
    end
```

**Limitation:** Thread interrupt doesn't force-stop. Handler must cooperate.

## Callback Processing

```mermaid
flowchart TD
    JobComplete["Job Completes"] --> Queue["Push to<br/>Callback Queue"]
    
    Queue --> Thread["Background Thread<br/>Processes queue"]
    
    Thread --> Batch["Batch callbacks"]
    Batch --> Send["Try send to Admin 1"]
    
    Send --> Check{Success?}
    Check -->|Yes| Done["Remove from queue"]
    Check -->|No| Failover["Try Admin 2...N"]
    
    Failover --> AllFailed{All failed?}
    AllFailed -->|Yes| Persist["Write to file<br/>for retry"]
    AllFailed -->|No| Done
    
    Persist --> RetryThread["Retry Thread<br/>(every 30s)"]
    RetryThread --> Send
```

## File Organization

```mermaid
flowchart TB
    subgraph FileSystem["Executor File System"]
        Root["logPath/"]
        
        subgraph Logs["Execution Logs"]
            DateDirs["yyyy-MM-dd/"]
            LogFiles["{logId}.log<br/>One file per execution"]
        end
        
        subgraph Scripts["Script Cache"]
            ScriptDir["glueSrcPath/"]
            ScriptFiles["{jobId}_{updateTime}.py"]
        end
        
        subgraph Callbacks["Callback Retry"]
            CBDir["callbacklog/"]
            CBFiles["orth-callback-{md5}.log"]
        end
        
        Root --> Logs
        Root --> Scripts
        Root --> Callbacks
        
        Logs --> DateDirs --> LogFiles
        Scripts --> ScriptDir --> ScriptFiles
        Callbacks --> CBDir --> CBFiles
    end
```

## Idle Thread Cleanup

```mermaid
flowchart LR
    Poll["Queue poll<br/>3s timeout"] --> Check{Trigger received?}
    
    Check -->|Yes| Reset["idleCount = 0<br/>Process trigger"]
    Check -->|No| Increment["idleCount++"]
    
    Reset --> Poll
    Increment --> Threshold{idleCount > 30?}
    
    Threshold -->|No| Poll
    Threshold -->|Yes| QueueCheck{Queue empty?}
    
    QueueCheck -->|No| Poll
    QueueCheck -->|Yes| Remove["Remove thread<br/>handler.destroy()"]
```

**Calculation:** 30 cycles × 3s = 90+ seconds idle before cleanup.

## Concurrent Execution

When `executorBlockStrategy = CONCURRENT` and `executorConcurrency > 1`, a `JobThread` uses an internal `ExecutorService` thread pool. The main thread becomes a dispatcher that polls triggers from the queue and submits them to worker threads.

```mermaid
flowchart TB
    subgraph JobThread["JobThread (concurrency=3)"]
        Dispatcher["Dispatcher Thread<br/>Polls trigger queue"]
        Pool["Worker Pool<br/>(ThreadPoolExecutor)"]

        subgraph Workers["Worker Threads"]
            W1["Worker 1<br/>Context + Execute + Callback"]
            W2["Worker 2<br/>Context + Execute + Callback"]
            W3["Worker 3<br/>Context + Execute + Callback"]
        end

        Dispatcher -->|"submit"| Pool
        Pool --> W1
        Pool --> W2
        Pool --> W3
    end

    Queue["Trigger Queue"] --> Dispatcher
    W1 --> CB["Callback Queue"]
    W2 --> CB
    W3 --> CB
```

### Execution Flow

```mermaid
sequenceDiagram
    participant Admin as Admin Scheduler
    participant EB as ExecutorBizImpl
    participant JT as JobThread (Dispatcher)
    participant Pool as Worker Pool
    participant H as Handler

    Admin->>EB: run(trigger, concurrency=3)
    EB->>JT: pushTriggerQueue(trigger)

    loop Poll Loop
        JT->>JT: poll trigger from queue
        JT->>Pool: submit(trigger)
        Pool->>Pool: activeCount++
        Pool->>H: execute()
        H-->>Pool: result
        Pool->>Pool: pushCallback(trigger)
        Pool->>Pool: activeCount--
    end
```

### Behavior Details

| Aspect | Serial (concurrency=1) | Concurrent (concurrency>1) |
|--------|----------------------|---------------------------|
| Main thread role | Execute triggers inline | Dispatch to worker pool |
| `isRunningOrHasQueue()` | `running \|\| !queue.empty` | `activeCount > 0 \|\| !queue.empty` |
| Idle cleanup | `idleTimes > 30 && queue.empty` | `idleTimes > 30 && queue.empty && activeCount == 0` |
| Context (ThreadLocal) | Set by main thread | Set by each worker thread |
| Callback | Pushed in `finally` of main loop | Pushed in `finally` of each worker |
| Shutdown | `toStop` flag exits loop | `toStop` exits loop + `shutdownNow()` on pool |
| `init()/destroy()` | Called once by main thread | Called once by main thread (not per-worker) |

### Thread Safety Requirements

When using `CONCURRENT`, the job handler's `execute()` method **must be thread-safe**:
- No shared mutable state without synchronization
- `OrthJobContext` is per-thread (ThreadLocal) — safe
- `OrthJobHelper.log()` writes to per-execution log files — safe
- Handler `init()` and `destroy()` are called once (before/after all workers)

### Concurrency Change Detection

If a trigger arrives with a different `executorConcurrency` than the running `JobThread`, `ExecutorBizImpl` kills the old thread and creates a new one with the updated concurrency level. This ensures the pool size always matches the configured value.

## Key Metrics

| Metric | Value | Purpose |
|--------|-------|---------|
| Poll timeout | 3 seconds | Balance responsiveness vs CPU |
| Idle threshold | 30 cycles (90s) | Resource conservation |
| Message truncation | 50,000 chars | Prevent memory/network overload |
| Netty threads | 0-200 | Handle concurrent triggers |
| Callback retry interval | 30 seconds | Match heartbeat period |

## Design Strengths

1. **Isolation**: Each job has dedicated thread, no cross-job interference
2. **Backpressure**: Queue-based processing prevents overload
3. **Flexibility**: Supports Java methods, scripts in any language
4. **Fault Tolerance**: File-based retry survives restarts

## Design Limitations

1. **Memory Overhead**: One thread per active job
2. **No Sub-Second Scheduling**: Minimum granularity is seconds
3. **Timeout Cooperation**: Thread interrupt may not force-stop handler
4. **Unbounded Callback Queue**: OOM risk if admin unreachable for extended period
