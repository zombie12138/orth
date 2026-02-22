package com.abyss.orth.admin.model;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * Job execution log entity for tracking trigger and execution lifecycle.
 *
 * <p>Records comprehensive execution details including trigger info, executor response, and alarm
 * status for monitoring and debugging purposes.
 */
@Getter
@Setter
public class JobLog {

    /** Alarm status: Default (not processed) */
    public static final int ALARM_STATUS_DEFAULT = 0;

    /** Alarm status: No alarm needed */
    public static final int ALARM_STATUS_NONE = -1;

    /** Alarm status: Lock failed (alarm is being sent by another thread) */
    public static final int ALARM_STATUS_LOCK_FAIL = 1;

    /** Alarm status: Successfully sent */
    public static final int ALARM_STATUS_SUCCESS = 2;

    /** Alarm status: Failed to send */
    public static final int ALARM_STATUS_FAIL = 3;

    private long id;

    // Job reference
    private int jobGroup; // Executor group ID
    private int jobId; // Job ID

    // Execution configuration
    private String executorAddress; // Selected executor address
    private String executorHandler; // Job handler name
    private String executorParam; // Job parameters
    private String executorShardingParam; // Sharding parameters (index/total)
    private int executorFailRetryCount; // Retry count on failure

    // Trigger information
    private Date triggerTime; // Actual trigger time

    /** Theoretical schedule time (null for manual/API triggers) */
    private Date scheduleTime;

    private int triggerCode; // Trigger result code
    private String triggerMsg; // Trigger result message

    // Execution result from executor
    private Date handleTime; // Execution completion time
    private int handleCode; // Execution result code
    private String handleMsg; // Execution result message

    // Alarm status
    private int alarmStatus; // Alarm processing status (see ALARM_STATUS_* constants)
}
