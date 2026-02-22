# Job Configuration Import/Export

## Core Concept

The **import/export** feature enables **portable job configuration management** through JSON serialization. Users can export job definitions (single or batch), share them across environments, back them up, or modify them externally before re-importing.

**Key Capability**: Configuration as code — treat job definitions as version-controlled, transferable artifacts.

## Architecture Overview

```mermaid
flowchart TB
    subgraph UI["Job Management UI"]
        SELECT["Select Jobs<br/>(1 or many)"]
        EXPORT["Export Button"]
        IMPORT["Import Button"]
    end

    subgraph Backend["Configuration API"]
        EPERM["Permission Check"]
        EBUILD["Build Export Data<br/>(exclude runtime fields)"]
        EJSON["Serialize to JSON"]

        IPARSE["Parse JSON<br/>(detect object/array)"]
        IVALID["Validate Fields"]
        ICREATE["Create Job(s)"]
    end

    subgraph Storage["Persistent Store"]
        DB[(MySQL<br/>orth_job_info)]
        FILE["JSON File<br/>(clipboard/disk)"]
    end

    SELECT -->|"Click export"| EXPORT
    EXPORT -->|"GET /jobinfo/export"| EPERM
    EPERM -->|"Authorized"| EBUILD
    EBUILD -->|"Serialize"| EJSON
    EJSON -->|"Display"| FILE

    FILE -->|"Paste JSON"| IMPORT
    IMPORT -->|"POST /jobinfo/import"| IPARSE
    IPARSE -->|"Single/Batch"| IVALID
    IVALID -->|"Insert"| ICREATE
    ICREATE -->|"Save"| DB
```

## API Summary

| Method | Path | Description |
|--------|------|-------------|
| GET | `/jobinfo/export?id=123` | Export single job as JSON object |
| GET | `/jobinfo/export?ids[]=123&ids[]=124` | Export multiple jobs as JSON array |
| POST | `/jobinfo/import` | Import single (JSON object) or batch (JSON array) |

## Export Feature

Export serializes job configuration fields to JSON via `JobInfoController.java`, using `LinkedHashMap` for consistent field ordering and `GsonBuilder.setPrettyPrinting()` for readability.

### Excluded Fields

Export omits runtime/identity fields that shouldn't transfer across environments:

| Field | Reason for Exclusion |
|-------|---------------------|
| `id` | Auto-generated on import |
| `addTime` | Set to current time on import |
| `updateTime` | Set to current time on import |
| `glueUpdatetime` | Set to current time on import |
| `triggerStatus` | Default to 0 (STOP) |
| `triggerLastTime` | Reset on import |
| `triggerNextTime` | Recalculated on import |

### Batch Export Behavior

When exporting multiple jobs, each is independently permission-checked. Unauthorized jobs are silently skipped — if the user selects 10 jobs but only has access to 7, export succeeds with 7 jobs (graceful degradation).

## Import Feature

The import endpoint (`POST /jobinfo/import`) auto-detects single vs batch import by checking whether the parsed JSON is an object or array. See `JobInfoController.java`.

### Import Behavior

- **Runtime fields reset**: `triggerStatus=0` (STOP), timestamps set to current time
- **Required fields**: `jobGroup`, `jobDesc`, `scheduleType`
- **Permission check**: User must have access to the target `jobGroup`
- **Partial failure**: In batch import, individual failures are skipped with error messages. The response reports `"Imported 3/5 jobs successfully"` with failure details.

### Design Decision

Batch import returns HTTP 200 even with partial failures, since some imports succeeded. This avoids discarding successful results due to individual validation errors.

## Frontend Integration

| Component | Location | Description |
|-----------|----------|-------------|
| Export button | `job.list.ftl` toolbar | Exports selected jobs, opens modal with JSON + copy button |
| Import button | `job.list.ftl` toolbar | Opens modal with textarea for pasting JSON |
| Export modal | `jobExportModal` | Read-only textarea with copy-to-clipboard |
| Import modal | `jobImportModal` | Editable textarea with client-side JSON validation |

Both modals live in `job.list.ftl`. The import modal validates JSON syntax client-side before submitting.

## Security Considerations

- **Permission enforcement**: Both export and import check `JobGroupPermissionUtil` per job/group
- **Code injection prevention**: Imported jobs default to `triggerStatus=0` (STOP), requiring manual start after review
- **Data leakage**: Export may expose sensitive data in `executorParam` or `glueSource` — users should use environment variables over hardcoded secrets
- **Payload size**: No explicit limit currently; recommend adding Spring `max-request-size` config

## Error Scenarios

| Phase | Error | Response |
|-------|-------|----------|
| Export | Job not found | Skip (batch) or error (single) |
| Export | Permission denied | Skip (batch) or error (single) |
| Export | No jobs selected | Error message |
| Import | JSON syntax error | Fail entire import |
| Import | Missing required field | Skip job in batch |
| Import | Invalid jobGroup | Skip job in batch |
| Import | Permission denied | Skip job in batch |
| Import | Database constraint violation | Skip job in batch |

## Use Cases

- **Environment migration**: Export from dev, import to prod — avoid manual recreation of 50+ jobs
- **Template sharing**: Commit exported JSON to Git, team members import and customize
- **Backup/restore**: Export all jobs before upgrade, re-import if rollback needed
- **Programmatic cloning**: Export 1 job, script 10 JSON variations with `jq`, batch import
- **Configuration audit**: Export all jobs, parse JSON externally for security/compliance checks

## Performance Considerations

| Operation | Cost | Optimization |
|-----------|------|-------------|
| Single export | ~5ms | — |
| Batch export (100 jobs) | ~500ms | Use `WHERE id IN (...)` batch query |
| Single import | ~10ms | — |
| Batch import (100 jobs) | ~1s | Use JDBC batch insert |

## Critical Analysis

### Strengths

1. **Portability**: JSON format is universal and human-readable
2. **Batch Support**: Single and multi-job operations in one endpoint
3. **Graceful Degradation**: Partial export/import succeeds where possible
4. **Safety**: Imported jobs start in STOP state, preventing accidental execution

### Weaknesses

1. **No ID Mapping**: SuperTask `superTaskId` references break across environments
2. **No Deduplication**: Re-importing creates duplicates (no upsert by name)
3. **No Size Limit**: Large batch imports could exhaust memory

### Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Sensitive data in exported JSON | Use env vars instead of hardcoded secrets; add field redaction option |
| SuperTask references break on import | Import SuperTask first, capture new ID, update SubTask references |
| Memory exhaustion from large imports | Add Spring `max-request-size` config |

### Future Enhancements

- **Field redaction**: Option to exclude `glueSource` / `executorParam` from export
- **Import preview**: Validation table showing field status before committing
- **Bulk edit workflow**: Export → external edit with `jq` → re-import with upsert matching
- **Git integration**: Auto-commit job configs on save for audit trail
