# API and RPC Architecture

## Core Concept

Orth implements **bidirectional HTTP/JSON RPC** between Admin and Executors, enabling cross-language support. Admin exposes REST APIs for callbacks/registration, while Executors run embedded Netty servers for job triggering.

## Communication Architecture

```mermaid
flowchart TB
    subgraph Admin["Admin Server"]
        AC["REST Controllers<br/>/api/callback<br/>/api/registry"]
        Client["HTTP Client<br/>(Dynamic Proxy)"]
    end
    
    subgraph Executor["Executor"]
        NS["Netty Server<br/>/run, /kill, /log, /beat"]
        EC["HTTP Client<br/>(Multi-admin)"]
    end
    
    AC <-->|"Callbacks<br/>Heartbeats"| EC
    Client <-->|"Triggers<br/>Commands"| NS
    
    style AC fill:#e1f5ff
    style NS fill:#ffe1e1
```

## Admin → Executor (Job Triggering)

```mermaid
sequenceDiagram
    participant Admin
    participant Proxy as Dynamic Proxy
    participant Executor as Executor Netty
    
    Admin->>Proxy: executorBiz.run(trigger)
    Note over Proxy: HTTP POST /run<br/>JSON serialization<br/>Token header
    
    Proxy->>Executor: HTTP Request
    Executor->>Executor: Validate token
    Executor->>Executor: Route to handler
    Executor-->>Proxy: Response (success/fail)
    Proxy-->>Admin: Java object
```

**Key Endpoints:**

| Endpoint | Purpose | Request |
|----------|---------|---------|
| `/run` | Trigger job execution | TriggerRequest (job ID, params, timeout) |
| `/kill` | Terminate running job | KillRequest (job ID) |
| `/idleBeat` | Check if job is idle | IdleBeatRequest (job ID) |
| `/log` | Fetch execution logs | LogRequest (log ID, line offset) |
| `/beat` | Health check | Empty |

## Executor → Admin (Callbacks & Registration)

```mermaid
sequenceDiagram
    participant Executor
    participant Admin1 as Admin 1
    participant Admin2 as Admin 2
    
    Note over Executor: Job completes
    Executor->>Admin1: POST /api/callback
    
    alt Success
        Admin1-->>Executor: 200 OK
    else Failure
        Executor->>Admin2: POST /api/callback (failover)
        
        alt Success
            Admin2-->>Executor: 200 OK
        else All failed
            Executor->>Executor: Write to retry file
        end
    end
```

**Admin Endpoints:**

| Endpoint | Purpose |
|----------|---------|
| `/api/callback` | Report job results |
| `/api/registry` | Heartbeat registration |
| `/api/registryRemove` | Deregister on shutdown |

## Authentication Flow

```mermaid
flowchart LR
    Request["HTTP Request"] --> Header["Orth-ACCESS-TOKEN<br/>in header"]
    
    Header --> Validate{Token matches<br/>configured value?}
    
    Validate -->|Yes| Allow["Process request"]
    Validate -->|No| Reject["Return error:<br/>The access token is wrong"]
```

**Security Model:**
- Shared secret token
- Transmitted in every request header
- No encryption (recommend HTTPS in production)

## Routing Strategy Selection

```mermaid
flowchart TD
    Trigger["Job Trigger"] --> Strategy{Routing Strategy}
    
    Strategy -->|FIRST| S1["Use first<br/>available executor"]
    Strategy -->|ROUND| S2["Round-robin<br/>across executors"]
    Strategy -->|RANDOM| S3["Random<br/>selection"]
    Strategy -->|CONSISTENT_HASH| S4["Hash by job ID<br/>sticky routing"]
    Strategy -->|FAILOVER| S5["Try until<br/>one succeeds"]
    Strategy -->|SHARDING_BROADCAST| S6["Broadcast to ALL<br/>with shard params"]
    
    subgraph Result["Executor Selection"]
        E1["Executor 1"]
        E2["Executor 2"]
        EN["Executor N"]
    end
    
    S1 --> Result
    S2 --> Result
    S3 --> Result
    S4 --> Result
    S5 --> Result
    S6 --> Result
```

**Sharding Example:**
- Total executors: 3
- Executor 1 receives: `shardIndex=0, shardTotal=3`
- Executor 2 receives: `shardIndex=1, shardTotal=3`
- Executor 3 receives: `shardIndex=2, shardTotal=3`

## Block Strategy Handling

```mermaid
stateDiagram-v2
    [*] --> NewTrigger: Trigger arrives
    
    NewTrigger --> CheckStrategy: Check block strategy
    
    state CheckStrategy {
        [*] --> SERIAL: SERIAL_EXECUTION
        [*] --> DISCARD: DISCARD_LATER
        [*] --> COVER: COVER_EARLY
        
        SERIAL --> Queue: Add to queue
        Queue --> Execute: Process sequentially
        
        DISCARD --> CheckBusy: Is job running?
        CheckBusy --> Reject: Yes - reject trigger
        CheckBusy --> Queue: No - add to queue
        
        COVER --> Kill: Kill existing thread
        Kill --> CreateNew: Start new thread
    }
```

**Use Cases:**
- **SERIAL**: Prevent concurrent execution (e.g., database migrations)
- **DISCARD**: Skip if busy (e.g., real-time data fetch)
- **COVER**: Always use latest (e.g., cache warming)

## Executor Registration

```mermaid
sequenceDiagram
    participant Executor
    participant Admin
    participant Registry as Registry Table
    
    Note over Executor: Startup
    Executor->>Admin: POST /api/registry<br/>(every 30s)
    Admin->>Registry: UPSERT (app, address, timestamp)
    
    loop Health Monitor (Admin side)
        Admin->>Registry: Find records > 90s old
        Admin->>Registry: DELETE stale entries
    end
    
    Note over Executor: Shutdown
    Executor->>Admin: POST /api/registryRemove
    Admin->>Registry: DELETE (app, address)
```

**Key Thresholds:**
- Heartbeat interval: 30 seconds
- Dead timeout: 90 seconds (3× heartbeat)

## Request/Response Model

```mermaid
classDiagram
    class TriggerRequest {
        +jobId
        +executorHandler
        +executorParams
        +executorTimeout
        +logId
        +scheduleTime (nullable)
        +shardIndex/shardTotal
    }
    
    class Response~T~ {
        +code (200=success, 500=fail)
        +msg (error message)
        +data (payload)
    }
    
    class CallbackRequest {
        +logId
        +handleCode
        +handleMsg
    }
    
    TriggerRequest --> Response : sent via /run
    CallbackRequest --> Response : sent via /api/callback
```

## Netty Server Architecture

```mermaid
flowchart TB
    subgraph NettyServer["Embedded Netty Server"]
        Boss["Boss Thread Group<br/>Accept connections"]
        Worker["Worker Thread Group<br/>Handle I/O"]
        Biz["Business Thread Pool<br/>0-200 threads<br/>Queue: 2000"]
        Handler["Request Handler"]
    end
    
    Client["Admin Client"] --> Boss
    Boss --> Worker
    Worker --> Biz
    Biz --> Handler
    
    Handler -->|"/run"| Execute["Execute job"]
    Handler -->|"/kill"| Kill["Kill job"]
    Handler -->|"/log"| Fetch["Fetch logs"]
```

**Configuration:**
- Thread pool: 0-200 threads (creates on demand)
- Queue capacity: 2000 requests
- Idle timeout: 90 seconds
- Max body size: 5 MB

## Design Strengths

1. **Cross-Language Support**: HTTP/JSON works with any language
2. **Fault Tolerance**: Multi-admin failover, automatic retry
3. **Flexible Routing**: Multiple strategies for different use cases
4. **Non-Blocking I/O**: Netty handles high concurrency efficiently

## Design Limitations

1. **No Circuit Breaker**: Dead executors cause timeout on every trigger
2. **No Rate Limiting**: Vulnerable to flood attacks
3. **Plaintext Token**: Authentication token not encrypted
4. **No API Versioning**: Breaking changes require coordinated deployment
