# SuperTask Template-Instance Pattern

## Core Concept

The **SuperTask pattern** implements a template-instance architecture where a parent job (SuperTask) defines executable code while child jobs (SubTasks) provide varying parameters. This enables batch creation of similar jobs that share code but differ in configuration.

**Key Principle**: Code inheritance at trigger time — SubTasks dynamically inherit their SuperTask's code when executed, eliminating code duplication and enabling centralized updates.

## Architecture Overview

```mermaid
flowchart TB
    subgraph Admin["Admin Console"]
        UI["Job Management UI"]
        API["Batch Copy API<br/>/jobinfo/batchCopy"]
    end

    subgraph Database["MySQL Storage"]
        ST["SuperTask<br/>(super_task_id = NULL)<br/>Contains: code + config"]
        SUB1["SubTask 1<br/>(super_task_id = SuperTask.id)<br/>Contains: params only"]
        SUB2["SubTask 2<br/>(super_task_id = SuperTask.id)<br/>Contains: params only"]
        SUB3["SubTask 3<br/>(super_task_id = SuperTask.id)<br/>Contains: params only"]

        ST -->|"references"| SUB1
        ST -->|"references"| SUB2
        ST -->|"references"| SUB3
    end

    subgraph Trigger["Trigger Time"]
        TRIG["Job Trigger"]
        INHERIT["Code Inheritance<br/>JobTrigger.java"]
        EXEC["Execute with<br/>SubTask params"]
    end

    UI -->|"Fork SuperJob"| API
    API -->|"Create batch"| SUB1
    API -->|"Create batch"| SUB2
    API -->|"Create batch"| SUB3

    SUB1 -->|"Scheduled"| TRIG
    TRIG -->|"Resolve super_task_id"| INHERIT
    INHERIT -->|"Copy code from SuperTask"| EXEC
```

## Database Schema

### New Column: `super_task_id`

Added to `orth_job_info`:

| Value | Meaning |
|-------|---------|
| `NULL` | Standalone job or SuperTask |
| Non-NULL | SubTask — inherits code from referenced SuperTask |

Indexed for fast lookups during trigger. Foreign key enables cascade delete protection.

## Template-Instance Workflow

```mermaid
sequenceDiagram
    participant User
    participant UI as Job List UI
    participant API as Batch Copy API
    participant DB as orth_job_info
    participant Trigger as Job Trigger
    participant Executor

    User->>UI: Click "Fork SuperJob"
    UI->>User: Show JSON editor with template
    Note over UI,User: Template has 3 sample SubTasks<br/>with different params

    User->>UI: Edit JSON (add/modify SubTasks)
    User->>UI: Click "Create SubTasks"

    UI->>API: POST /jobinfo/batchCopy
    Note over API: mode: "advanced"<br/>templateJobId: 123<br/>tasks: [...]

    API->>DB: Query SuperTask (id=123)
    DB-->>API: SuperTask config

    loop For each SubTask config
        API->>DB: INSERT new job<br/>super_task_id=123<br/>executor_param=custom
        Note over DB: SubTask inherits:<br/>- job_group<br/>- glue_type<br/>- executor_handler<br/>- schedule_type<br/>- route_strategy<br/>BUT NOT: glue_source, executor_param
    end

    API-->>UI: BatchCopyResult<br/>(successCount, failCount)
    UI-->>User: Show success message

    Note over Trigger: Later: SubTask scheduled

    Trigger->>DB: Load SubTask
    DB-->>Trigger: SubTask with super_task_id=123

    alt SubTask has super_task_id
        Trigger->>DB: Load SuperTask (id=123)
        DB-->>Trigger: SuperTask code
        Trigger->>Trigger: Inherit glue_source<br/>from SuperTask
    end

    Trigger->>Executor: Trigger with<br/>SuperTask code +<br/>SubTask params
    Executor-->>Trigger: Execution result
```

## Batch Copy API

**POST** `/jobinfo/batchCopy`

| Feature | Simple Mode | Advanced Mode |
|---------|-------------|---------------|
| **Input** | Array of strings (params) | Array of SubTaskConfig objects |
| **Name Generation** | Template-based `{origin}-{index}` | Explicit per SubTask |
| **Customization** | `executor_param` only | Full per-SubTask config (desc, schedule, params) |
| **Use Case** | Quick parameterized duplication | Complex batch with different schedules |

Returns `BatchCopyResult` with `successCount`, `failCount`, and `createdJobIds`.

## Code Inheritance Mechanism

At trigger time, `JobTrigger.java:trigger()` resolves the SuperTask chain:

1. Load SubTask from `orth_job_info`
2. If `super_task_id` is set, load the referenced SuperTask
3. Copy `glue_source` and `glue_updatetime` from SuperTask into the SubTask's in-memory object
4. Trigger with inherited code + SubTask-specific params

**Inherited from SuperTask**: `glue_source`, `glue_updatetime`

**Kept from SubTask**: `executor_param`, `job_desc`, `schedule_conf`, `alarm_email`

## Frontend Integration

### Job List UI

| Component | Location | Description |
|-----------|----------|-------------|
| Fork SuperJob button | `job.list.ftl` toolbar | Opens JSON editor with template for batch SubTask creation |
| SuperTask dropdown | Add/Update modals | Select parent SuperTask from same executor group (AJAX-loaded) |
| Edit SuperTask Code link | Update modal | Opens GLUE IDE for the SuperTask in a new tab |
| SuperTask column | Job list table | Badge display — blue for SubTasks (shows parent name), gray for standalone |
| SuperTask search filter | Search row | Searches both job name and SuperTask name via SQL JOIN |

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/jobinfo/batchCopy` | Create SubTasks from SuperTask template |
| GET | `/jobinfo/getSuperTaskList` | List potential SuperTasks in a job group |

### SuperTask Tag Display

The job list uses a LEFT JOIN (`JobInfoMapper.xml:pageList`) to resolve `super_task_name` from the parent. A transient `superTaskName` field on `JobInfo` carries this to the UI.

| Badge Color | Meaning | Tooltip |
|-------------|---------|---------|
| Blue (`label-primary`) | SubTask | "SubTask of: [SuperTask Name]" |
| Gray (`label-default`) | Standalone | "Standalone Job" |

## Cascade Delete Protection

SuperTasks with active SubTasks cannot be deleted. `JobServiceImpl.java:delete()` checks `countSubTasks(id)` before proceeding. Users must delete all SubTasks first.

A database-level foreign key constraint provides additional safety.

## Use Cases

- **Multi-Region Data Collection**: 1 SuperTask with scraping logic, N SubTasks with `region=XX` params. Update code once, all regions use it.
- **Batch Report Generation**: 1 report template SuperTask, 100 SubTasks with `department_id=X` and different email recipients.
- **A/B Testing**: 1 algorithm SuperTask, SubTasks with `variant=A/B/control` params.

## Performance Considerations

| Operation | Cost | Notes |
|-----------|------|-------|
| SuperTask lookup at trigger | ~1-2ms | Indexed PK query, cacheable |
| Batch creation (100 SubTasks) | ~500ms | Sequential INSERTs; could optimize with JDBC batch (~50ms) |

## Critical Analysis

### Strengths

1. **Code Reusability**: Single source of truth for shared logic
2. **Maintenance Efficiency**: Update once, affects all SubTasks
3. **Parameterization**: Easy to create variations with different configs
4. **Scalability**: Supports thousands of SubTasks per SuperTask
5. **Flexibility**: SubTasks can override schedule, email, description

### Weaknesses

1. **Runtime Dependency**: SubTask execution depends on SuperTask existence
2. **Code Versioning**: No built-in version control for code changes
3. **Testing Complexity**: SuperTask changes affect all SubTasks simultaneously
4. **Query Overhead**: Additional database query per SubTask trigger

### Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Breaking changes propagate to all SubTasks | Add `super_task_version` column for version pinning |
| Accidental SuperTask deletion orphans SubTasks | Cascade delete protection (service + DB FK) |
| Large `glue_source` duplicated in memory | Application-layer caching (Redis or MyBatis L2) |

### Future Enhancements

- **SuperTask versioning**: SubTasks lock to a specific code version
- **Dry-run mode**: Simulate batch copy without creating jobs (`?dryRun=true`)
- **Cascading config updates**: Propagate SuperTask field changes to SubTasks
