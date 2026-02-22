# Registry and Service Discovery Architecture

## Core Concept

Orth implements a **heartbeat-based service registry** where Executors actively register themselves with Admin servers every 30 seconds. Admin maintains a registry table and automatically removes stale entries after 90 seconds (3× heartbeat interval), enabling dynamic executor discovery.

## High-Level Architecture

```mermaid
flowchart TB
    subgraph ExecutorCluster["Executor Cluster"]
        E1["Executor 1<br/>app: data-collector"]
        E2["Executor 2<br/>app: data-collector"]
        E3["Executor 3<br/>app: report-generator"]
    end
    
    subgraph AdminCluster["Admin Cluster"]
        A1["Admin Server 1"]
        A2["Admin Server 2"]
    end
    
    subgraph RegistryStorage["Service Registry"]
        RT[(orth_job_registry<br/>• app_name<br/>• address<br/>• update_time)]
        
        JG[(orth_job_group<br/>• app_name<br/>• address_list)]
    end
    
    E1 -->|"Heartbeat every 30s"| A1
    E2 -->|"Heartbeat every 30s"| A1
    E3 -->|"Heartbeat every 30s"| A2
    
    A1 -->|"UPSERT"| RT
    A2 -->|"UPSERT"| RT
    
    RT -->|"Query by app_name"| JG
    
    subgraph Cleanup["Auto Cleanup"]
        Monitor["Admin Monitor<br/>Delete if update_time > 90s"]
    end
    
    RT -.->|"Stale detection"| Monitor
    Monitor -.->|"DELETE"| RT
```

## Executor Registration Flow

```mermaid
sequenceDiagram
    participant Executor
    participant RegistryThread as ExecutorRegistryThread
    participant Admin1 as Admin Server 1
    participant Admin2 as Admin Server 2
    participant DB as Registry Table
    
    Note over Executor: Startup
    Executor->>RegistryThread: start(appname, address)
    
    loop Every 30 seconds
        RegistryThread->>RegistryThread: Build RegistryRequest<br/>• type: EXECUTOR<br/>• key: appname<br/>• value: address<br/>• timestamp: now
        
        RegistryThread->>Admin1: POST /api/registry
        
        alt Admin1 success
            Admin1->>DB: INSERT ... ON DUPLICATE KEY UPDATE<br/>update_time = now
            Admin1-->>RegistryThread: 200 OK
            Note over RegistryThread: Wait 30s for next beat
        else Admin1 failure
            RegistryThread->>Admin2: POST /api/registry (failover)
            
            alt Admin2 success
                Admin2->>DB: INSERT ... ON DUPLICATE KEY UPDATE
                Admin2-->>RegistryThread: 200 OK
            else All admins failed
                Note over RegistryThread: Log error, retry in 30s
            end
        end
    end
    
    Note over Executor: Shutdown
    Executor->>RegistryThread: toStop()
    RegistryThread->>Admin1: POST /api/registryRemove
    Admin1->>DB: DELETE WHERE app_name AND address
```

## Registry Table Schema

```mermaid
erDiagram
    orth_job_registry {
        int id PK
        varchar registry_group "Always: EXECUTOR"
        varchar registry_key "Executor app_name"
        varchar registry_value "Executor address URL"
        datetime update_time "Last heartbeat time"
        UNIQUE idx_g_k_v "Composite unique key"
    }
    
    orth_job_group {
        int id PK
        varchar app_name "Links to registry_key"
        int address_type "0=auto, 1=manual"
        text address_list "Cached from registry"
    }
```

**Key Design:** Composite unique key `(registry_group, registry_key, registry_value)` ensures one row per executor instance.

## Admin-Side Discovery

```mermaid
sequenceDiagram
    participant Monitor as JobRegistryHelper
    participant Registry as Registry Table
    participant GroupMapper as Job Group Mapper
    participant Trigger as Job Trigger
    
    loop Every 30 seconds
        Monitor->>Registry: SELECT * FROM orth_job_registry<br/>WHERE registry_group = 'EXECUTOR'
        Registry-->>Monitor: All active executors
        
        Monitor->>Monitor: Group by app_name<br/>(registry_key)
        
        loop For each app_name
            Monitor->>GroupMapper: Find orth_job_group<br/>WHERE app_name = ?
            
            alt Address type = AUTO
                Monitor->>GroupMapper: UPDATE address_list<br/>with active executors
            else Address type = MANUAL
                Note over Monitor: Skip, use manual config
            end
        end
        
        Monitor->>Registry: DELETE FROM orth_job_registry<br/>WHERE update_time < (now - 90s)
        Note over Monitor: Remove stale registrations
    end
    
    Note over Trigger: Job trigger time
    Trigger->>GroupMapper: Load executor group
    GroupMapper-->>Trigger: address_list (from registry)
    Trigger->>Trigger: Route to executor<br/>using routing strategy
```

## Heartbeat Timing

```mermaid
timeline
    title Executor Heartbeat Lifecycle
    
    section Healthy State
        T=0s : Executor starts
        T=0s : First heartbeat sent
        T=30s : Heartbeat #2
        T=60s : Heartbeat #3
        T=90s : Heartbeat #4
    
    section Executor Crash
        T=95s : Last heartbeat was at 90s
        T=120s : No heartbeat for 30s
        T=150s : No heartbeat for 60s
        T=180s : 90s passed - Admin removes entry
    
    section Recovery
        T=200s : Executor restarts
        T=200s : New heartbeat sent
        T=200s : Re-registered immediately
```

**Key Thresholds:**
- **Heartbeat interval**: 30 seconds
- **Dead timeout**: 90 seconds (3× heartbeat)
- **Detection frequency**: Every 30 seconds (admin monitor)

## Multi-Admin Failover

```mermaid
flowchart TD
    Executor["Executor"] --> Attempt1["Try Admin 1"]
    
    Attempt1 --> Check1{Success?}
    Check1 -->|Yes| Done["Registration complete"]
    Check1 -->|No| Attempt2["Try Admin 2"]
    
    Attempt2 --> Check2{Success?}
    Check2 -->|Yes| Done
    Check2 -->|No| Attempt3["Try Admin 3...N"]
    
    Attempt3 --> Check3{Any success?}
    Check3 -->|Yes| Done
    Check3 -->|No| LogError["Log error<br/>Retry in 30s"]
    
    LogError --> Attempt1
```

**Resilience:** Executor registers with ANY available admin server.

## Address Type Modes

```mermaid
flowchart LR
    subgraph AutoMode["Auto Discovery (address_type=0)"]
        direction TB
        A1["Executors register<br/>via heartbeat"]
        A2["Admin updates<br/>address_list automatically"]
        A3["Dynamic: executors<br/>can join/leave"]
    end
    
    subgraph ManualMode["Manual Configuration (address_type=1)"]
        direction TB
        M1["Admin manually<br/>configures address_list"]
        M2["Heartbeats ignored<br/>for address_list"]
        M3["Static: predefined<br/>executor addresses"]
    end
    
    JobGroup["Job Group"] -->|"Choose mode"| AutoMode
    JobGroup -->|"Choose mode"| ManualMode
```

**Use Cases:**
- **Auto Mode**: Dynamic cloud environments, auto-scaling
- **Manual Mode**: Fixed infrastructure, specific IP requirements

## Registry Cleanup Process

```mermaid
flowchart TD
    Start["JobRegistryHelper<br/>runs every 30s"] --> Query["Query registry table"]
    
    Query --> Calculate["Calculate threshold:<br/>now - 90 seconds"]
    
    Calculate --> Find["Find records WHERE<br/>update_time < threshold"]
    
    Find --> Check{Stale records found?}
    
    Check -->|Yes| Delete["DELETE stale records"]
    Check -->|No| Wait["No action"]
    
    Delete --> Log["Log removed executors"]
    Wait --> Sleep["Sleep 30 seconds"]
    Log --> Sleep
    Sleep --> Query
```

## Executor Discovery for Job Triggers

```mermaid
sequenceDiagram
    participant Trigger as Job Trigger
    participant Group as Job Group
    participant Registry as Registry Cache
    participant Strategy as Routing Strategy
    participant Executor as Selected Executor
    
    Trigger->>Group: Load by job.group_id
    Group-->>Trigger: Group config + address_list
    
    alt address_list is empty
        Trigger-->>Trigger: Return fail: no executors available
    else address_list has executors
        Trigger->>Strategy: route(trigger, address_list)
        
        alt FIRST
            Strategy-->>Trigger: First address
        else ROUND_ROBIN
            Strategy-->>Trigger: Next in rotation
        else RANDOM
            Strategy-->>Trigger: Random selection
        else CONSISTENT_HASH
            Strategy-->>Trigger: Hash(jobId) → address
        else FAILOVER
            Strategy-->>Trigger: Try each until success
        else SHARDING_BROADCAST
            Strategy-->>Trigger: All addresses (with shard params)
        end
        
        Trigger->>Executor: HTTP POST /run
    end
```

## State Transitions

```mermaid
stateDiagram-v2
    [*] --> Unregistered: Executor starts
    
    Unregistered --> Registering: First heartbeat sent
    
    Registering --> Active: Admin confirms
    
    state Active {
        [*] --> Healthy
        Healthy --> Healthy: Heartbeat every 30s
    }
    
    Active --> MissedBeat: No heartbeat
    
    state MissedBeat {
        [*] --> Grace30s: 30s since last beat
        Grace30s --> Grace60s: 60s since last beat
        Grace60s --> Grace90s: 90s since last beat
    }
    
    MissedBeat --> Active: Heartbeat resumes
    MissedBeat --> Removed: 90s timeout reached
    
    Removed --> Registering: Executor recovers
    
    Active --> Deregistering: Shutdown signal
    Deregistering --> [*]: Explicit deregister
```

## Key Components

| Component | Location | Responsibility |
|-----------|----------|----------------|
| **ExecutorRegistryThread** | Executor side | Send heartbeats every 30s |
| **JobRegistryHelper** | Admin side | Process registrations, cleanup stale |
| **orth_job_registry** | Database | Store active executor addresses |
| **orth_job_group** | Database | Cache executor list per app |
| **OpenApiController** | Admin API | Handle `/api/registry` endpoint |

## Registration Request Format

```mermaid
classDiagram
    class RegistryRequest {
        +String registryGroup = "EXECUTOR"
        +String registryKey (app_name)
        +String registryValue (address URL)
    }
    
    note for RegistryRequest "Sent every 30s from each executor<br/>UPSERT updates timestamp"
```

## Admin Registry Monitor Thread

```mermaid
flowchart LR
    subgraph Responsibilities["JobRegistryHelper Responsibilities"]
        R1["1. Process heartbeats<br/>(UPSERT registry)"]
        R2["2. Update job groups<br/>(refresh address_list)"]
        R3["3. Cleanup stale entries<br/>(DELETE > 90s)"]
        R4["4. Monitor health<br/>(detect executor failures)"]
    end
    
    Thread["Monitor Thread<br/>Daemon, runs every 30s"] --> Responsibilities
```

## Design Strengths

1. **Simple & Reliable**: Heartbeat-based, no complex consensus
2. **Fault Tolerant**: Multi-admin failover, automatic cleanup
3. **Dynamic Discovery**: Executors can join/leave freely
4. **Zero Configuration**: Auto-discovery mode requires no manual setup

## Design Limitations

1. **90-Second Detection Lag**: Dead executors remain visible for up to 90s
2. **Database Dependency**: Registry unavailable if DB is down
3. **No Health Checks**: Only presence detection, not actual health
4. **Polling-Based**: 30s granularity, not real-time updates

## Operational Metrics

| Metric | Value | Purpose |
|--------|-------|---------|
| Heartbeat interval | 30 seconds | Balance between freshness and load |
| Dead timeout | 90 seconds | 3× heartbeat for network tolerance |
| Registry cleanup cycle | 30 seconds | Match heartbeat frequency |
| UPSERT operation | Idempotent | Same request = same result |
| Failover targets | Multiple admins | High availability |

## Failure Scenarios

### Scenario 1: Executor Network Partition

```mermaid
flowchart TD
    Normal["Normal: Executor sending heartbeats"] --> Partition["Network partition occurs"]
    Partition --> NoHeartbeat["No heartbeat reaches Admin"]
    NoHeartbeat --> Wait30["Wait 30s"]
    Wait30 --> Wait60["Wait 60s"]
    Wait60 --> Wait90["Wait 90s"]
    Wait90 --> Remove["Admin removes from registry"]
    Remove --> NoTriggers["No more triggers sent to this executor"]
    
    Partition -.->|"Network recovers"| Recover["Heartbeat succeeds"]
    Recover --> Reregister["Immediately re-registered"]
    Reregister --> Normal
```

### Scenario 2: Admin Cluster Failure

```mermaid
flowchart TD
    Executor["Executor tries Admin 1"] --> Failed1["Connection failed"]
    Failed1 --> Try2["Try Admin 2"]
    Try2 --> Success["Admin 2 accepts"]
    Success --> Continue["Continue with Admin 2"]
    
    style Failed1 fill:#ffcccc
    style Success fill:#ccffcc
```

### Scenario 3: Database Unavailable

```mermaid
flowchart TD
    Heartbeat["Executor sends heartbeat"] --> Admin["Admin receives"]
    Admin --> DB["Try to UPSERT registry"]
    DB --> Error["Database error"]
    Error --> ReturnFail["Return error to executor"]
    ReturnFail --> ExecutorRetry["Executor retries in 30s"]
    
    Error -.->|"DB recovers"| Success["UPSERT succeeds"]
    Success --> Normal["Normal operation resumes"]
```

## Best Practices

1. **Configure Multiple Admins**: Use comma-separated addresses for failover
2. **Monitor Registry Size**: Alert if registry grows unboundedly
3. **Set Proper Network Timeouts**: Avoid long blocking on dead admins
4. **Use Auto Discovery**: Simplifies operations in dynamic environments
5. **Log Registration Failures**: Track patterns of connectivity issues
