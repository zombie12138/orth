# Log Management Architecture

## Core Concept

Orth separates **metadata** (stored in database) from **detailed logs** (stored in files), enabling high-throughput logging with queryable history. Executors report results asynchronously via callbacks with file-based retry for reliability.

## Dual Storage Architecture

```mermaid
flowchart LR
    subgraph LogStorage["Log Storage Strategy"]
        direction TB
        DB["Database<br/>Metadata only<br/>• Job ID<br/>• Status codes<br/>• Timestamps<br/>• Error summary"]
        
        FS["File System<br/>Detailed logs<br/>• Full output<br/>• Stack traces<br/>• Progress updates"]
    end
    
    subgraph Benefits["Design Benefits"]
        B1["Fast queries<br/>on metadata"]
        B2["High-volume<br/>log writes"]
        B3["Independent<br/>retention policies"]
    end
    
    DB --> B1
    FS --> B2
    DB --> B3
    FS --> B3
```

## Log Status Reference

A log record's status is derived from two database fields: `trigger_code` and `handle_code`.

| Status | `trigger_code` | `handle_code` | Scenario | Terminal? |
|--------|---------------|---------------|----------|-----------|
| **Init** | `0` | `0` | Log record just inserted, scheduler hasn't sent trigger request yet | No |
| **Pending** | `200` | `0` | Trigger succeeded (Admin→Executor HTTP returned 200), awaiting execution result | No |
| **Success** | `200` | `200` | Trigger succeeded + executor callback reported success | Yes |
| **Timeout** | `200` | `502` | Trigger succeeded, but execution timed out (JobThread detected `executorTimeout` exceeded) | Yes |
| **Trigger Failed** | `≠0, ≠200` (usually `500`) | any | Trigger phase failed: no available executor, routing failure, network error, missing handler, etc. | Yes |
| **Failed** | `200` | `≠0, ≠200, ≠502` (usually `500`) | Trigger succeeded, but executor callback reported failure (handler threw exception, returned FAIL, etc.) | Yes |

### Status Transitions

```
Init ──trigger──→ Trigger Failed          (terminal)
  │
  └──trigger ok──→ Pending ──callback──→ Success   (terminal)
                       │            ├──→ Failed    (terminal)
                       │            └──→ Timeout   (terminal)
                       │
                       └──no callback > 10min──→ Failed (lost job detection)
```

### UI Representation

The frontend merges these two codes into a single **Status** column with a clickable `<Tag>`:
- **Tag label**: `{status} {triggerCode}/{handleCode}` (e.g., `Success 200/200`)
- **Tag color**: green (Success), processing/blue (Pending), red (Failed/Trigger Failed), orange (Timeout), default/gray (Init)
- **Click**: Opens a popover showing the raw `trigger_msg` and `handle_msg` from the database for debugging

## Log Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Init: Log record inserted

    Init --> TriggerFailed: Trigger phase fails
    Init --> Pending: Trigger succeeds (trigger_code=200)

    Pending --> Success: Callback handle_code=200
    Pending --> Failed: Callback handle_code=500
    Pending --> Timeout: Callback handle_code=502
    Pending --> Failed: No callback > 10min (lost job)

    TriggerFailed --> [*]
    Success --> [*]
    Failed --> [*]
    Timeout --> [*]
```

## Callback Flow

```mermaid
sequenceDiagram
    participant Executor
    participant Queue as Callback Queue
    participant Retry as Retry Files
    participant Admin
    
    Executor->>Queue: Push result
    
    loop Background Processing
        Queue->>Queue: Batch callbacks
        Queue->>Admin: HTTP POST /api/callback
        
        alt Success
            Admin-->>Queue: 200 OK
            Note over Queue: Remove from queue
        else All admins failed
            Queue->>Retry: Write to disk
            Note over Retry: Persistent for retry
        end
    end
    
    loop Every 30 seconds
        Retry->>Retry: Scan files
        Retry->>Admin: Retry failed callbacks
        
        alt Success
            Admin-->>Retry: 200 OK
            Retry->>Retry: Delete file
        end
    end
```

## Lost Job Detection

```mermaid
flowchart TD
    Monitor["Monitor Thread<br/>(runs every 60s)"] --> Query["Find jobs:<br/>• Still 'running' status<br/>• No callback > 10 minutes<br/>• Executor offline"]
    
    Query --> Check{Jobs found?}
    
    Check -->|No| Wait["Sleep 60s"]
    Check -->|Yes| Mark["Mark as FAILED<br/>handle_code = 500<br/>msg = 'Result lost'"]
    
    Mark --> Next{More jobs?}
    Next -->|Yes| Mark
    Next -->|No| Wait
    
    Wait --> Query
```

**Design Rationale:** Handles executor crashes that prevent callback delivery.

## Alarm Status Flow

```mermaid
stateDiagram-v2
    [*] --> Default: Log created
    Default --> Locked: Alarm monitor acquires
    
    state Locked {
        [*] --> Processing
        Processing: alarm_status = -1
        Processing: Prevents concurrent alarm
    }
    
    Locked --> NoAlarm: Job not found
    Locked --> AlarmSuccess: Alarm sent successfully
    Locked --> AlarmFailed: Alarm failed
    
    note right of Locked
        Optimistic lock:
        UPDATE ... WHERE alarm_status = 0
        Ensures single processing
    end note
```

## Message Truncation Strategy

```mermaid
flowchart LR
    Handler["Job Handler<br/>Unlimited output"] -->|"Truncate at 50k chars"| Executor["Executor<br/>Callback"]
    
    Executor -->|"Network transfer"| Admin["Admin Server<br/>Callback processor"]
    
    Admin -->|"Truncate at 15k chars"| DB["Database<br/>TEXT field (64KB limit)"]
    
    style Handler fill:#e1f5ff
    style DB fill:#ffe1e1
```

**Trade-off:** Prevents database overflow but may lose critical debug info.

## Key Components

| Component | Responsibility | Key Behavior |
|-----------|---------------|--------------|
| **Admin: JobCompleteHelper** | Process callbacks | Thread pool (2-20 threads)<br/>Deduplicates by log ID |
| **Executor: TriggerCallbackThread** | Send results | Batches callbacks<br/>Retries on failure |
| **Executor: JobThread** | Generate logs | Writes to files<br/>Truncates messages |
| **Admin: Monitor Thread** | Detect lost jobs | Scans every 60s<br/>10-minute threshold |

## Retry Mechanism

```mermaid
flowchart TD
    subgraph NormalFlow["Normal Callback Flow"]
        CB1["Executor sends callback"]
        CB2["Admin receives and processes"]
        CB3["Database updated"]
    end
    
    subgraph RetryFlow["Failure & Retry Flow"]
        F1["Network timeout or error"]
        F2["Write to local file:<br/>callbacklog/orth-callback-{md5}.log"]
        F3["Retry thread scans files"]
        F4["Resend to admin"]
        F5{Success?}
        F6["Delete file"]
        
        F1 --> F2 --> F3 --> F4 --> F5
        F5 -->|Yes| F6
        F5 -->|No| F3
    end
    
    CB1 --> CB2 --> CB3
    CB2 -.->|Failure| F1
```

**Reliability:** Survives executor restarts, admin downtime.

## File Organization

```mermaid
flowchart TB
    subgraph ExecutorFS["Executor File System"]
        LogRoot["logPath/"]
        
        subgraph Dates["Date Directories"]
            D1["2026-01-30/"]
            D2["2026-01-29/"]
        end
        
        subgraph LogFiles["Log Files"]
            L1["12345.log"]
            L2["12346.log"]
            L3["Each file = one execution"]
        end
        
        LogRoot --> Dates
        Dates --> LogFiles
    end
    
    subgraph Retrieval["Log Retrieval"]
        UI["Web UI polls every 3s"]
        API["Admin calls Executor /log"]
        Read["Read from file system"]
    end
    
    UI --> API --> Read --> LogFiles
```

## Design Strengths

1. **High Throughput**: File writes don't block database
2. **Queryable History**: Database enables fast status queries
3. **Fault Tolerant**: File-based retry handles network failures
4. **Independent Scaling**: Log files on executor, metadata on admin

## Design Limitations

1. **10-Minute Lost Job Latency**: Too slow for fast jobs
2. **Race Condition Risk**: Duplicate callback not fully prevented
3. **No Streaming**: Polling-based log retrieval, not real-time
4. **Unbounded Callback Queue**: OOM risk if admin unreachable
