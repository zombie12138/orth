# Batch Trigger with Logical Schedule Time

## Core Concept

The **batch trigger** feature enables manual triggering of multiple job instances within a specified time range, each with a **logical schedule time**. This is essential for backfilling missed executions, testing scheduled jobs, and reprocessing historical data.

**Key Distinction**:
- **Trigger Time** = Actual execution time (when job runs)
- **Schedule Time** = Logical scheduled time (when job was supposed to run)

This separation allows jobs to know their intended execution context, critical for time-sensitive data processing.

## Architecture Overview

```mermaid
flowchart TB
    subgraph UI["Admin Console"]
        TM["Trigger Modal"]
        MODE["Mode Selection:<br/>Immediate / Scheduled"]
        RANGE["Time Range Input<br/>(Start/End)"]
        PREVIEW["Preview API<br/>Show estimated instances"]
    end

    subgraph Backend["Batch Trigger Engine"]
        CALC["Schedule Calculator<br/>Generate trigger times"]
        BATCH["Batch Trigger Loop<br/>Create multiple instances"]
        LOG["Log Creation<br/>with schedule_time"]
    end

    subgraph Executor["Job Execution"]
        CTX["XxlJobContext<br/>scheduleTime field"]
        ENV["Environment Variables<br/>ORTH_SCHEDULE_TIME"]
        HANDLER["Job Handler<br/>Access via XxlJobHelper"]
    end

    TM -->|"Select scheduled"| MODE
    MODE -->|"Enter range"| RANGE
    RANGE -->|"Preview"| PREVIEW
    PREVIEW -->|"Confirm"| CALC

    CALC -->|"Generate times"| BATCH
    BATCH -->|"For each time"| LOG
    LOG -->|"Trigger"| CTX
    CTX -->|"Pass to"| ENV
    ENV -->|"Access via"| HANDLER
```

## Trigger Mode Comparison

### Immediate Trigger (Existing)

```mermaid
sequenceDiagram
    participant User
    participant UI
    participant API
    participant Trigger
    participant Executor

    User->>UI: Click "Trigger"
    UI->>API: POST /jobinfo/trigger<br/>id=123
    API->>Trigger: triggerJob(123, params)

    Note over Trigger: schedule_time = NULL<br/>trigger_time = now()

    Trigger->>Executor: Execute immediately
    Executor-->>User: Result
```

### Scheduled Trigger (New)

```mermaid
sequenceDiagram
    participant User
    participant UI
    participant Preview as Preview API
    participant Batch as Batch Trigger
    participant DB as xxl_job_log
    participant Executor

    User->>UI: Select "Scheduled" mode
    User->>UI: Enter time range<br/>(2026-02-01 00:00 to 02-01 23:59)

    UI->>Preview: POST /jobinfo/previewTriggerBatch<br/>startTime, endTime
    Preview->>Preview: Calculate schedule times<br/>based on CRON/FIX_RATE
    Preview-->>UI: [00:00, 01:00, 02:00, ..., 23:00]<br/>(24 instances)

    User->>UI: Confirm batch trigger

    UI->>Batch: POST /jobinfo/triggerBatch<br/>startTime, endTime

    loop For each schedule time
        Batch->>DB: Insert log entry<br/>schedule_time = calculated<br/>trigger_time = now()
        Batch->>Executor: Trigger with params +<br/>scheduleTime
    end

    Batch-->>UI: Success (24 instances created)
```

## Database Schema

### New Column: `schedule_time`

Added to `xxl_job_log`:

| Trigger Type | trigger_time | schedule_time | Interpretation |
|-------------|--------------|---------------|----------------|
| Immediate | 2026-02-10 15:30:00 | NULL | Manual trigger right now |
| Scheduled | 2026-02-10 15:30:00 | 2026-02-01 00:00:00 | Backfill for Feb 1 midnight |
| Normal CRON | 2026-02-10 02:00:00 | 2026-02-10 02:00:00 | Automatic scheduled trigger |

## Schedule Time Calculation

The engine generates schedule times from a time range based on the job's schedule type:

| Schedule Type | Algorithm | Example |
|--------------|-----------|---------|
| CRON | Parse expression, enumerate matching times in range | `0 0 * * * ?` over 24h → 24 instances |
| FIX_RATE | Add interval repeatedly from start time | 3600s interval over 6h → 6 instances |
| FIX_DELAY | Single instance at start time only | Unpredictable schedule (depends on prior completion) |
| NONE | Rejected — batch trigger not supported | Manual-only jobs have no schedule to calculate |

**Safety cap**: Maximum 100 instances per batch trigger. See `JobInfoController.java`.

## API Summary

| Method | Path | Description |
|--------|------|-------------|
| POST | `/jobinfo/trigger` | Immediate trigger (existing) |
| POST | `/jobinfo/triggerBatch` | Batch trigger with schedule times |
| POST | `/jobinfo/previewTriggerBatch` | Preview calculated schedule times |

The preview endpoint returns a list of calculated schedule times for the given range, enabling the UI to show instance count before confirmation.

## Context Propagation

Schedule time flows from admin to executor through the full stack:

| Layer | Mechanism | Access |
|-------|-----------|--------|
| Admin trigger | Set on `XxlJobLog.scheduleTime` | — |
| RPC call | Passed as `TriggerParam.scheduleTime` | — |
| Java handlers | `XxlJobContext.scheduleTime` field | `XxlJobHelper.getScheduleTime()` |
| Script handlers | Environment variable | `$ORTH_SCHEDULE_TIME` |

### Environment Variables (Script Jobs)

Set by `ScriptJobHandler.java` before script execution:

| Variable | Type | Description |
|----------|------|-------------|
| `ORTH_JOB_ID` | int | Job ID from xxl_job_info |
| `ORTH_JOB_PARAM` | string | Executor parameters |
| `ORTH_LOG_ID` | long | Log entry ID |
| `ORTH_SCHEDULE_TIME` | datetime | Logical schedule time (NULL for immediate) |
| `ORTH_TRIGGER_TIME` | datetime | Actual trigger time |
| `ORTH_SHARD_INDEX` | int | Shard index (for broadcast jobs) |
| `ORTH_SHARD_TOTAL` | int | Total shard count |

## Safety Mechanisms

| Mechanism | Rule | Rationale |
|-----------|------|-----------|
| Instance count cap | Max 100 per batch | Prevent resource exhaustion |
| Time range validation | `startTime < endTime`, max 7 days | Prevent overly broad ranges |
| Schedule type check | Reject NONE type | No schedule to calculate |
| Permission check | `JobGroupPermissionUtil` | Standard access control |

## Use Cases

- **Backfilling**: System was down on Feb 1 — batch trigger the hourly job for that day (24 instances), each knowing its logical hour via `getScheduleTime()`.
- **Testing time-dependent logic**: Trigger a monthly report job across Jan-Dec to verify each month's output.
- **Parallel historical reprocessing**: Create 30 instances (1 per day) for last month, each processing its assigned day.

## Performance Considerations

| Aspect | Current | Notes |
|--------|---------|-------|
| Trigger latency | ~10ms/instance (sequential) | 100 instances ≈ 1 second |
| Log table impact | 100 rows per batch | Negligible; consider cleanup policies |
| Executor load | All instances trigger simultaneously | Limited by thread pool (200 fast + 100 slow) |

## Critical Analysis

### Strengths

1. **Historical Processing**: Enables backfilling without manual timestamp passing
2. **Time Awareness**: Jobs know their logical context (`schedule_time` vs `trigger_time`)
3. **Preview Safety**: Users verify instance count before triggering
4. **Flexible Range**: Supports CRON, FIX_RATE, FIX_DELAY schedule types
5. **Environment Variables**: Script jobs access schedule_time without code changes

### Weaknesses

1. **Sequential Triggering**: 100 instances take ~1 second to trigger
2. **No Progress Tracking**: Cannot monitor batch trigger progress
3. **Fixed Cap**: 100-instance limit may be insufficient for some use cases

### Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Executor overload from simultaneous triggers | Add optional staggered triggering with delay |
| Time zone confusion in UI | Add timezone selector to trigger modal |
| Accidental mass trigger | Preview + confirmation dialog with instance count |

### Future Enhancements

- **Asynchronous batch triggering**: Return immediately with a `batchId`, poll for progress
- **Progress tracking**: `GET /batchTriggerProgress?batchId=X` returning total/completed/failed
- **Custom schedule times**: Allow manual specification of arbitrary times instead of calculated range
- **Conditional triggering**: Skip instances that already have successful logs
