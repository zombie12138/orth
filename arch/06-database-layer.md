# Database Layer Architecture

## Core Schema

```mermaid
erDiagram
    xxl_job_group ||--o{ xxl_job_info : contains
    xxl_job_info ||--o{ xxl_job_log : generates
    xxl_job_group ||--o{ xxl_job_registry : registered
    
    xxl_job_group {
        int id PK
        varchar app_name
        varchar title
        int address_type
        text address_list
    }
    
    xxl_job_info {
        int id PK
        int job_group FK
        varchar schedule_type
        varchar schedule_conf
        varchar executor_handler
        bigint trigger_next_time
        int trigger_status
    }
    
    xxl_job_log {
        bigint id PK
        int job_id FK
        datetime trigger_time
        datetime schedule_time
        int trigger_code
        int handle_code
        int alarm_status
    }
    
    xxl_job_registry {
        int id PK
        varchar registry_group
        varchar registry_key
        varchar registry_value
        datetime update_time
    }
```

## Data Volume Characteristics

| Table | Volume | Growth Rate |
|-------|--------|-------------|
| `xxl_job_group` | Low (~10s) | Static |
| `xxl_job_info` | Medium (~1000s) | Slow |
| **`xxl_job_log`** | **High (millions)** | **Fast** |
| `xxl_job_registry` | Low (~100s) | Moderate |

**Key Design:** `xxl_job_log` uses `bigint` ID to support high-volume logging.

## Transaction Patterns

### Schedule Cycle (Explicit Transaction)

```mermaid
sequenceDiagram
    participant Thread as Schedule Thread
    participant TX as Transaction
    participant DB as Database
    
    Thread->>TX: BEGIN
    Thread->>DB: SELECT ... FOR UPDATE<br/>(Distributed Lock)
    Thread->>DB: SELECT jobs WHERE<br/>next_time ≤ now + 5s
    
    loop Process jobs
        Thread->>Thread: Update next_time in memory
    end
    
    Thread->>DB: UPDATE job trigger times
    Thread->>TX: COMMIT
    Note over TX,DB: Lock released
```

**Purpose:** Ensure atomic scheduling across distributed admin instances.

### Service Layer (Auto-Commit)

Most operations rely on MyBatis auto-commit:
- Single INSERT/UPDATE/DELETE statements
- No explicit transaction boundaries
- Trade-off: Simpler code but less safety for multi-step operations

## Index Strategy

### Critical Indexes

```mermaid
flowchart LR
    subgraph LogTable["xxl_job_log (high volume)"]
        I1["I_trigger_time<br/>Time range queries"]
        I2["I_handle_code<br/>Status filtering"]
        I3["I_jobid_jobgroup<br/>Job filtering"]
    end
    
    subgraph Queries["Common Query Patterns"]
        Q1["Pagination by time"]
        Q2["Find failed jobs"]
        Q3["Job-specific logs"]
    end
    
    Q1 --> I1
    Q2 --> I2
    Q3 --> I3
```

### Missing Indexes (Performance Issues)

```mermaid
flowchart TD
    subgraph Issues["Performance Bottlenecks"]
        P1["scheduleJobQuery:<br/>Full scan on trigger_status"]
        P2["findFailJobLogIds:<br/>Full scan on alarm_status"]
        P3["Slow pagination:<br/>Large OFFSET values"]
    end
    
    subgraph Solutions["Recommended Indexes"]
        S1["INDEX (trigger_status, trigger_next_time)"]
        S2["INDEX (alarm_status)"]
        S3["Cursor-based pagination"]
    end
    
    P1 -.->|"Add"| S1
    P2 -.->|"Add"| S2
    P3 -.->|"Replace"| S3
```

## Query Patterns

### Schedule Query (Critical Path)

```mermaid
flowchart LR
    Input["Input:<br/>maxNextTime<br/>pageSize"] --> Query["SELECT * FROM xxl_job_info<br/>WHERE trigger_status = 1<br/>AND trigger_next_time ≤ ?<br/>ORDER BY id<br/>LIMIT ?"]
    
    Query --> Issue["⚠️ Missing composite index<br/>Full table scan"]
    
    Issue -.->|"Solution"| Fix["INDEX (trigger_status,<br/>trigger_next_time)"]
```

**Impact:** Executed every second by scheduler.

### Lost Job Detection

```mermaid
flowchart LR
    Query["SELECT logs<br/>WHERE handle_code = 0<br/>AND trigger_time ≤ (now - 10min)"] --> Join["LEFT JOIN registry<br/>ON executor_address"]
    
    Join --> Filter["WHERE registry.id IS NULL<br/>(executor offline)"]
    
    Filter --> Result["Lost jobs to mark as failed"]
```

**Design:** Anti-join pattern finds logs from unregistered executors.

## Distributed Lock Mechanism

```mermaid
flowchart TD
    subgraph LockTable["xxl_job_lock Table"]
        LockRow["Single row:<br/>lock_name = 'schedule_lock'"]
    end
    
    Admin1["Admin 1"] -->|"SELECT FOR UPDATE"| LockRow
    Admin2["Admin 2"] -->|"BLOCKED"| LockRow
    Admin3["Admin 3"] -->|"BLOCKED"| LockRow
    
    LockRow -->|"COMMIT releases"| Admin1
    
    style Admin1 fill:#90EE90
    style Admin2 fill:#FFB6C6
    style Admin3 fill:#FFB6C6
```

**Characteristics:**
- Pessimistic lock (row-level)
- Transaction-scoped
- Simple but limits horizontal scaling

## Data Lifecycle

### Log Retention

```mermaid
flowchart LR
    Create["Log created"] -->|"Retention period<br/>(default 30 days)"| Cleanup["Cleanup job"]
    
    Cleanup --> Query["Find old logs"]
    Query --> Batch["Delete in batches"]
    Batch --> Check{More to delete?}
    Check -->|Yes| Query
    Check -->|No| Done["Complete"]
```

### Registry Cleanup

```mermaid
flowchart LR
    Heartbeat["Executor sends heartbeat<br/>every 30s"] --> Update["UPSERT registry record<br/>with current timestamp"]
    
    Update --> Monitor["Admin monitor checks<br/>every cycle"]
    
    Monitor --> Check{Record > 90s old?}
    Check -->|Yes| Delete["DELETE from registry"]
    Check -->|No| Keep["Keep active"]
```

## Design Strengths

1. **Optimized for Log Volume**: Bigint IDs, strategic indexes
2. **Simple Locking**: Database-native distributed coordination
3. **Flexible Queries**: MyBatis dynamic SQL for complex filtering
4. **Denormalized for Performance**: Reduces JOINs in hot paths

## Design Limitations

1. **Single-Instance Scheduling**: Lock bottleneck
2. **Offset Pagination**: Degrades with large offsets
3. **Missing Indexes**: Some queries scan full tables
4. **N+1 Query Patterns**: Loop-based loading instead of batch
