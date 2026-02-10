# Job Configuration Import/Export

## Core Concept

The **import/export** feature enables **portable job configuration management** through JSON serialization. Users can export job definitions (single or batch), share them across environments, back them up, or modify them externally before re-importing.

**Key Capability**: Configuration as code - treat job definitions as version-controlled, transferable artifacts.

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
        DB[(MySQL<br/>xxl_job_info)]
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

## Export Feature

### Single Job Export

**Endpoint**: `GET /jobinfo/export?id=123`

**Response**: JSON object

```json
{
  "jobGroup": 1,
  "jobDesc": "Data Collection Job",
  "author": "admin",
  "alarmEmail": "admin@example.com",
  "scheduleType": "CRON",
  "scheduleConf": "0 0 2 * * ?",
  "glueType": "GLUE_PYTHON",
  "glueSource": "#!/usr/bin/env python3\nprint('Hello World')",
  "executorHandler": "",
  "executorParam": "region=A",
  "executorRouteStrategy": "FIRST",
  "childJobId": "",
  "misfireStrategy": "DO_NOTHING",
  "executorBlockStrategy": "SERIAL_EXECUTION",
  "executorTimeout": 0,
  "executorFailRetryCount": 0
}
```

### Batch Export

**Endpoint**: `GET /jobinfo/export?ids[]=123&ids[]=124&ids[]=125`

**Response**: JSON array

```json
[
  {
    "jobGroup": 1,
    "jobDesc": "Data Collection Job - Region A",
    "author": "admin",
    "scheduleConf": "0 0 2 * * ?",
    ...
  },
  {
    "jobGroup": 1,
    "jobDesc": "Data Collection Job - Region B",
    "author": "admin",
    "scheduleConf": "0 0 3 * * ?",
    ...
  },
  {
    "jobGroup": 1,
    "jobDesc": "Data Collection Job - Region C",
    "author": "admin",
    "scheduleConf": "0 0 4 * * ?",
    ...
  }
]
```

### Excluded Fields

Export **omits** runtime/identity fields that shouldn't transfer:

| Field | Reason for Exclusion |
|-------|---------------------|
| `id` | Auto-generated on import |
| `addTime` | Set to current time on import |
| `updateTime` | Set to current time on import |
| `glueUpdatetime` | Set to current time on import |
| `triggerStatus` | Default to 0 (STOP) |
| `triggerLastTime` | Reset on import |
| `triggerNextTime` | Recalculated on import |

**Rationale**: Exported configs should be environment-agnostic and time-independent.

### Permission Handling

**Controller Logic**:

```java
@RequestMapping("/export")
@ResponseBody
public Response<String> export(
    @RequestParam(required = false) Integer id,
    @RequestParam(required = false) Integer[] ids,
    HttpServletRequest request) {

    LoginInfo loginInfo = XxlSsoHelper.getLoginInfo(request);

    // Single export
    if (id != null) {
        XxlJobInfo jobInfo = xxlJobInfoMapper.loadById(id);
        if (jobInfo == null) {
            return Response.ofFail("Job not found");
        }
        if (!JobGroupPermissionUtil.hasPermission(loginInfo, jobInfo.getJobGroup())) {
            return Response.ofFail("Permission denied");
        }

        String json = buildExportData(jobInfo);
        return Response.ofSuccess(json);
    }

    // Batch export
    if (ids != null && ids.length > 0) {
        List<String> exportList = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Integer jobId : ids) {
            XxlJobInfo jobInfo = xxlJobInfoMapper.loadById(jobId);
            if (jobInfo == null) {
                errors.add("Job " + jobId + " not found");
                continue;
            }
            if (!JobGroupPermissionUtil.hasPermission(loginInfo, jobInfo.getJobGroup())) {
                errors.add("Job " + jobId + " permission denied");
                continue;
            }

            exportList.add(buildExportData(jobInfo));
        }

        // Graceful degradation: export authorized jobs, skip unauthorized
        String json = "[" + String.join(",", exportList) + "]";
        return Response.ofSuccess(json);
    }

    return Response.ofFail("No job ID specified");
}
```

**Graceful Degradation**: If user selects 10 jobs but only has permission for 7, export succeeds with 7 jobs (no error).

## Import Feature

### Single Job Import

**Endpoint**: `POST /jobinfo/import`

**Request**: JSON object

```json
{
  "jobGroup": 1,
  "jobDesc": "Imported Job",
  "author": "admin",
  "scheduleType": "CRON",
  "scheduleConf": "0 0 2 * * ?",
  ...
}
```

**Response**:
```json
{
  "code": 200,
  "msg": "success",
  "data": "Job imported successfully with ID: 126"
}
```

### Batch Import

**Endpoint**: `POST /jobinfo/import`

**Request**: JSON array

```json
[
  { "jobGroup": 1, "jobDesc": "Job 1", ... },
  { "jobGroup": 1, "jobDesc": "Job 2", ... },
  { "jobGroup": 1, "jobDesc": "Job 3", ... }
]
```

**Response**:
```json
{
  "code": 200,
  "msg": "success",
  "data": "Imported 3/3 jobs successfully."
}
```

### Partial Failure Handling

**Scenario**: Import 5 jobs, 2 fail validation

**Response**:
```json
{
  "code": 200,
  "msg": "success",
  "data": "Imported 3/5 jobs successfully. Failures: Job 2: Invalid CRON expression; Job 4: Job group not found"
}
```

**Design Decision**: Return success (200) with detailed message, not failure (500), because some imports succeeded.

### Auto-Detection Logic

**Controller Implementation**:

```java
@RequestMapping("/import")
@ResponseBody
public Response<String> importJobs(
    @RequestBody String jsonContent,
    HttpServletRequest request) {

    LoginInfo loginInfo = XxlSsoHelper.getLoginInfo(request);

    try {
        // Parse JSON (detect type)
        Object parsed = new Gson().fromJson(jsonContent, Object.class);

        if (parsed instanceof List) {
            // Batch import
            List<Map<String, Object>> jobList = (List<Map<String, Object>>) parsed;
            return importBatch(jobList, loginInfo);

        } else if (parsed instanceof Map) {
            // Single import
            Map<String, Object> jobData = (Map<String, Object>) parsed;
            return importSingle(jobData, loginInfo);

        } else {
            return Response.ofFail("Invalid JSON format");
        }

    } catch (JsonSyntaxException e) {
        return Response.ofFail("JSON parsing failed: " + e.getMessage());
    }
}
```

**Type Detection**: Use `instanceof` to distinguish object vs array, then route to appropriate handler.

## Data Transformation

### Export Data Builder

**Location**: `JobInfoController.java`

```java
private String buildExportData(XxlJobInfo jobInfo) {
    Map<String, Object> exportData = new LinkedHashMap<>();

    // Include configuration fields
    exportData.put("jobGroup", jobInfo.getJobGroup());
    exportData.put("jobDesc", jobInfo.getJobDesc());
    exportData.put("author", jobInfo.getAuthor());
    exportData.put("alarmEmail", jobInfo.getAlarmEmail());

    exportData.put("scheduleType", jobInfo.getScheduleType());
    exportData.put("scheduleConf", jobInfo.getScheduleConf());

    exportData.put("glueType", jobInfo.getGlueType());
    exportData.put("glueSource", jobInfo.getGlueSource());
    exportData.put("executorHandler", jobInfo.getExecutorHandler());
    exportData.put("executorParam", jobInfo.getExecutorParam());

    exportData.put("executorRouteStrategy", jobInfo.getExecutorRouteStrategy());
    exportData.put("childJobId", jobInfo.getChildJobId());
    exportData.put("misfireStrategy", jobInfo.getMisfireStrategy());
    exportData.put("executorBlockStrategy", jobInfo.getExecutorBlockStrategy());
    exportData.put("executorTimeout", jobInfo.getExecutorTimeout());
    exportData.put("executorFailRetryCount", jobInfo.getExecutorFailRetryCount());

    // Serialize to JSON
    return new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()
        .toJson(exportData);
}
```

**Key Features**:
- `LinkedHashMap` for consistent field ordering
- `GsonBuilder.setPrettyPrinting()` for readability
- `disableHtmlEscaping()` to avoid `\u` escapes in code

### Import Data Parser

**Location**: `XxlJobServiceImpl.java`

```java
private Response<String> importSingle(Map<String, Object> jobData, LoginInfo loginInfo) {
    try {
        // Parse fields
        Integer jobGroup = getInteger(jobData, "jobGroup");
        String jobDesc = getString(jobData, "jobDesc");
        String scheduleType = getString(jobData, "scheduleType");
        String scheduleConf = getString(jobData, "scheduleConf");
        // ... more fields

        // Validate required fields
        if (jobGroup == null || jobDesc == null || scheduleType == null) {
            return Response.ofFail("Missing required fields: jobGroup, jobDesc, scheduleType");
        }

        // Check permission
        if (!JobGroupPermissionUtil.hasPermission(loginInfo, jobGroup)) {
            return Response.ofFail("Permission denied for job group: " + jobGroup);
        }

        // Create job object
        XxlJobInfo jobInfo = new XxlJobInfo();
        jobInfo.setJobGroup(jobGroup);
        jobInfo.setJobDesc(jobDesc);
        // ... set all fields

        // Set defaults for omitted runtime fields
        jobInfo.setTriggerStatus(0); // STOP
        jobInfo.setAddTime(new Date());
        jobInfo.setUpdateTime(new Date());
        jobInfo.setGlueUpdatetime(new Date());

        // Insert
        xxlJobInfoMapper.insert(jobInfo);

        return Response.ofSuccess("Job imported successfully with ID: " + jobInfo.getId());

    } catch (Exception e) {
        return Response.ofFail("Import failed: " + e.getMessage());
    }
}
```

**Error Handling**: Each import wrapped in try-catch for isolation (one failure doesn't abort batch).

## Frontend Integration

### Export Modal

**HTML** (`job.list.ftl`):

```html
<div class="modal fade" id="jobExportModal" tabindex="-1" role="dialog">
    <div class="modal-dialog modal-lg">
        <div class="modal-content">
            <div class="modal-header">
                <h4 class="modal-title">${I18n.jobinfo_export_title}</h4>
            </div>
            <div class="modal-body">
                <textarea id="exportJsonContent" class="form-control" readonly
                          style="height: 400px; font-family: monospace; font-size: 12px;"></textarea>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-primary" id="copyExportBtn">
                    <i class="fa fa-copy"></i> ${I18n.jobinfo_export_copy_success}
                </button>
                <button type="button" class="btn btn-default" data-dismiss="modal">${I18n.system_close}</button>
            </div>
        </div>
    </div>
</div>
```

**JavaScript**:

```javascript
$("#data_operation").on('click', '.job_export', function() {
    var rows = $.adminTable.table.bootstrapTable('getSelections');

    if (rows.length === 0) {
        layer.msg(I18n.system_please_choose + I18n.system_data);
        return;
    }

    // Collect job IDs
    var ids = rows.map(function(row) { return row.id; });

    // Prepare request (single or batch)
    var requestData = ids.length === 1 ? { id: ids[0] } : { 'ids[]': ids };

    // Call export API
    $.ajax({
        type: 'GET',
        url: base_url + '/jobinfo/export',
        data: requestData,
        traditional: true, // Required for array parameters
        dataType: 'json',
        success: function(data) {
            if (data.code == 200) {
                $('#exportJsonContent').val(data.data);
                $('#jobExportModal').modal('show');
            } else {
                layer.open({
                    title: I18n.system_tips,
                    content: (data.msg || I18n.system_fail)
                });
            }
        }
    });
});

// Copy to clipboard
$('#copyExportBtn').on('click', function() {
    var content = $('#exportJsonContent').val();
    var $temp = $('<textarea>');
    $('body').append($temp);
    $temp.val(content).select();
    document.execCommand('copy');
    $temp.remove();

    layer.msg(I18n.jobinfo_export_copy_success);
});
```

### Import Modal

**HTML**:

```html
<div class="modal fade" id="jobImportModal" tabindex="-1" role="dialog">
    <div class="modal-dialog modal-lg">
        <div class="modal-content">
            <div class="modal-header">
                <h4 class="modal-title">${I18n.jobinfo_import_title}</h4>
            </div>
            <div class="modal-body">
                <textarea id="importJsonContent" class="form-control"
                          placeholder="${I18n.jobinfo_import_placeholder}"
                          style="height: 400px; font-family: monospace; font-size: 12px;"></textarea>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-primary" id="confirmImportBtn">${I18n.jobinfo_opt_import}</button>
                <button type="button" class="btn btn-default" data-dismiss="modal">${I18n.system_cancel}</button>
            </div>
        </div>
    </div>
</div>
```

**JavaScript**:

```javascript
$('#confirmImportBtn').on('click', function() {
    var jsonContent = $('#importJsonContent').val();
    if (!jsonContent || jsonContent.trim() === '') {
        layer.msg(I18n.system_please_input + 'JSON');
        return;
    }

    // Validate JSON format
    try {
        var parsed = JSON.parse(jsonContent);
        if (typeof parsed !== 'object' || parsed === null) {
            throw new Error('Must be JSON object or array');
        }
    } catch (e) {
        layer.msg(I18n.jobinfo_import_json_invalid);
        return;
    }

    // Call import API
    $.ajax({
        type: 'POST',
        url: base_url + '/jobinfo/import',
        data: jsonContent,
        contentType: 'application/json',
        dataType: 'json',
        success: function(data) {
            if (data.code == 200) {
                $('#jobImportModal').modal('hide');
                layer.open({
                    title: I18n.system_tips,
                    content: data.data || I18n.system_opt_suc,
                    yes: function(index) {
                        layer.close(index);
                        $.adminTable.table.bootstrapTable('refresh');
                    }
                });
            } else {
                layer.open({
                    title: I18n.system_tips,
                    content: (data.msg || I18n.system_fail)
                });
            }
        }
    });
});
```

## Use Cases

### 1. Environment Migration

**Scenario**: Migrate 50 jobs from development to production.

**Steps**:
1. **Dev**: Select all jobs → Export → Copy JSON
2. **Prod**: Import → Paste JSON → Confirm
3. All 50 jobs created with dev configurations
4. Modify `jobGroup` to match prod groups if needed

**Benefit**: Avoid manual recreation, reduce human error

### 2. Job Template Sharing

**Scenario**: Share a complex data collection job template with team.

**Steps**:
1. Export job as JSON
2. Commit JSON to Git repository
3. Team members import from Git
4. Customize parameters per use case

**Benefit**: Standardized job templates, version-controlled configs

### 3. Backup and Restore

**Scenario**: Backup all jobs before major system upgrade.

**Steps**:
1. Select all jobs → Export → Save to file
2. Perform system upgrade
3. If rollback needed: Import from backup file

**Benefit**: Disaster recovery, quick restoration

### 4. Cross-Instance Cloning

**Scenario**: Create 10 similar jobs with slight parameter variations.

**Steps**:
1. Export base job
2. Use script to generate 10 JSON variations
3. Import batch (JSON array)

**Benefit**: Programmatic job generation

### 5. Configuration Review

**Scenario**: Audit job configurations for security/compliance.

**Steps**:
1. Export all jobs to JSON
2. Parse JSON with external tools (e.g., `jq`)
3. Check for insecure patterns (e.g., hardcoded passwords)

**Benefit**: Automated compliance checking

## Internationalization

### New i18n Keys

**English** (`message_en.properties`):
```properties
jobinfo_export_title=Export Job Configuration
jobinfo_export_copy_success=Copy to Clipboard
jobinfo_import_title=Import Job Configuration
jobinfo_import_placeholder=Paste JSON object or array here...
jobinfo_import_json_invalid=Invalid JSON format
jobinfo_opt_export=Export
jobinfo_opt_import=Import
```

**Chinese Simplified** (`message_zh_CN.properties`):
```properties
jobinfo_export_title=导出任务配置
jobinfo_export_copy_success=复制到剪贴板
jobinfo_import_title=导入任务配置
jobinfo_import_placeholder=在此粘贴 JSON 对象或数组...
jobinfo_import_json_invalid=无效的 JSON 格式
jobinfo_opt_export=导出
jobinfo_opt_import=导入
```

## Security Considerations

### 1. Permission Enforcement

**Export**: Check `JobGroupPermissionUtil` before exposing job data

```java
if (!JobGroupPermissionUtil.hasPermission(loginInfo, jobInfo.getJobGroup())) {
    errors.add("Permission denied for job " + jobInfo.getId());
    continue; // Skip unauthorized job
}
```

**Import**: Check permissions before creating job

```java
if (!JobGroupPermissionUtil.hasPermission(loginInfo, jobGroup)) {
    return Response.ofFail("Permission denied for job group: " + jobGroup);
}
```

### 2. Code Injection Prevention

**Risk**: Malicious `glueSource` in imported JSON

**Mitigation**:
- Imported jobs default to `triggerStatus=0` (STOP)
- Require manual start after review
- No automatic execution of imported code

### 3. Data Leakage

**Risk**: Export may expose sensitive data in `executorParam` or `glueSource`

**Mitigation**:
- Warn users before export: "Configuration may contain sensitive data"
- Option to redact fields (future enhancement)
- Encourage environment variables over hardcoded secrets

### 4. Payload Size Limits

**Risk**: Large batch import (e.g., 10,000 jobs) causes memory exhaustion

**Current**: No explicit limit

**Recommendation**: Add request size limit (e.g., 10 MB) in Spring config

```yaml
spring:
  servlet:
    multipart:
      max-request-size: 10MB
```

## Performance Considerations

### Export Performance

**Single Job**: ~5ms (database query + JSON serialization)

**Batch Export (100 jobs)**: ~500ms
- 100 database queries: ~400ms
- JSON serialization: ~100ms

**Optimization**: Batch query with `WHERE id IN (...)`

```java
List<XxlJobInfo> jobs = xxlJobInfoMapper.loadByIds(ids);
```

### Import Performance

**Single Job**: ~10ms (parsing + validation + insert)

**Batch Import (100 jobs)**: ~1 second
- 100 sequential inserts: ~800ms
- Validation overhead: ~200ms

**Optimization**: JDBC batch insert

```java
jdbcTemplate.batchUpdate(
    "INSERT INTO xxl_job_info (...) VALUES (...)",
    jobBatch
);
```

## Error Scenarios

### Export Errors

| Error | Cause | Response |
|-------|-------|----------|
| Job not found | Invalid ID | Skip with warning (batch), error (single) |
| Permission denied | User lacks access | Skip with warning (batch), error (single) |
| No jobs selected | Empty selection | Error message |

### Import Errors

| Error | Cause | Response |
|-------|-------|----------|
| JSON syntax error | Malformed JSON | Fail entire import |
| Missing required field | Incomplete job data | Skip job in batch |
| Invalid jobGroup | Non-existent group | Skip job in batch |
| Permission denied | User can't access group | Skip job in batch |
| Database error | Constraint violation | Skip job in batch |

## Comparison with Alternatives

### 1. Manual Recreation

| Aspect | Import/Export | Manual Recreation |
|--------|---------------|-------------------|
| Speed | Instant (copy/paste) | Slow (form filling) |
| Error Rate | Low (automated) | High (human error) |
| Scalability | 100 jobs in seconds | Impractical for >10 jobs |
| Auditability | Version-controlled JSON | No audit trail |

### 2. Database Backup

| Aspect | Import/Export | Database Backup |
|--------|---------------|-----------------|
| Granularity | Per-job | All-or-nothing |
| Portability | JSON (universal) | SQL dump (DB-specific) |
| Editability | Easy (text editor) | Hard (SQL syntax) |
| Restoration | Selective | Full restore required |

### 3. API-Based Migration

| Aspect | Import/Export | Custom API Scripts |
|--------|---------------|-------------------|
| Complexity | Low (built-in UI) | High (script development) |
| Maintenance | None | Scripts need updates |
| User Skill | Basic (copy/paste) | Advanced (programming) |
| Flexibility | Moderate | High (full control) |

## Future Enhancements

### 1. Field Redaction

Add option to exclude sensitive fields from export:

```json
{
  "includeGlueSource": false,
  "includeExecutorParam": false
}
```

Export would replace these with `"***REDACTED***"`.

### 2. Import Validation Preview

Before actual import, show a preview table:

| Field | Value | Status |
|-------|-------|--------|
| jobDesc | "Data Collection" | ✓ Valid |
| jobGroup | 999 | ✗ Group not found |
| scheduleConf | "invalid" | ✗ Invalid CRON |

User can fix issues before committing import.

### 3. Bulk Edit via Export

Workflow:
1. Export 100 jobs
2. Use external tool (e.g., `jq`) to bulk-edit JSON
3. Re-import modified jobs
4. System updates existing jobs (match by `jobDesc` or tag)

**Benefit**: Programmatic configuration management

### 4. Template Library

Built-in template marketplace:
- Public templates for common use cases
- Search by category (data collection, reporting, etc.)
- One-click import from library

### 5. Git Integration

Automatic commit of job configs to Git on save:
- Job changes trigger Git commits
- Full audit trail in version control
- Easy rollback to previous configs

## Integration with Other Features

### SuperTask Pattern

**Export SuperTask + SubTasks**:
```json
[
  {
    "jobDesc": "SuperTask",
    "superTaskId": null,
    "glueSource": "#!/usr/bin/env python3\n..."
  },
  {
    "jobDesc": "SubTask 1",
    "superTaskId": 123,  // Reference to SuperTask
    "executorParam": "region=A"
  },
  {
    "jobDesc": "SubTask 2",
    "superTaskId": 123,
    "executorParam": "region=B"
  }
]
```

**Import Challenge**: `superTaskId` refers to old job ID, need to resolve to new ID

**Solution**: Import SuperTask first, capture new ID, update SubTask references

### Batch Trigger

Export doesn't include trigger history (`xxl_job_log`), only job definitions.

**Future**: Add option to export job definition + recent execution history for debugging.

## Conclusion

The import/export feature provides essential capabilities for job configuration portability, backup, and programmatic management. By supporting both single and batch operations with JSON serialization, it enables efficient job migration across environments and facilitates configuration-as-code workflows.

**Recommended For**:
- Environment migration (dev → staging → prod)
- Backup and disaster recovery
- Template sharing across teams
- Bulk job configuration changes

**Not Recommended For**:
- Real-time synchronization (use database replication)
- Large-scale migrations (>1000 jobs, use database tools)
- Exporting execution history (only definitions exported)
