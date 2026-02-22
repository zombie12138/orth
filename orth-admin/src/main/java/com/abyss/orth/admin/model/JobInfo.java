package com.abyss.orth.admin.model;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * Job configuration entity representing a scheduled task.
 *
 * <p>Defines complete job metadata including scheduling rules, execution strategy, and SuperTask
 * relationships for template-instance pattern.
 */
@Getter
@Setter
public class JobInfo {

    private int id; // Primary key

    private int jobGroup; // Executor group ID
    private String jobDesc; // Job description

    private Date addTime; // Creation timestamp
    private Date updateTime; // Last update timestamp

    private String author; // Owner/responsible person
    private String alarmEmail; // Alert email addresses (comma-separated)

    // Scheduling configuration
    private String scheduleType; // Schedule type (see ScheduleTypeEnum)
    private String scheduleConf; // Schedule config (format depends on scheduleType)
    private String misfireStrategy; // Misfire handling strategy (see MisfireStrategyEnum)

    // Execution configuration
    private String executorRouteStrategy; // Routing strategy (see ExecutorRouteStrategyEnum)
    private String executorHandler; // Job handler name
    private String executorParam; // Job parameters
    private String executorBlockStrategy; // Block handling strategy (see ExecutorBlockStrategyEnum)
    private int executorTimeout; // Execution timeout in seconds
    private int executorFailRetryCount; // Retry count on failure

    // GLUE (dynamic code) configuration
    private String glueType; // GLUE type (see GlueTypeEnum)
    private String glueSource; // GLUE source code
    private String glueRemark; // GLUE version remark
    private Date glueUpdatetime; // GLUE last update time

    // Job dependencies
    private String childJobId; // Child job IDs (comma-separated)

    // SuperTask pattern (template-instance relationship)
    private Integer superTaskId; // SuperTask template ID (NULL = standalone or template)

    // Transient field: SuperTask name for display (not persisted to database)
    private String superTaskName;

    // Scheduling state
    private int triggerStatus; // Current status (see TriggerStatus)
    private long triggerLastTime; // Last trigger timestamp (milliseconds)
    private long triggerNextTime; // Next trigger timestamp (milliseconds)
}
