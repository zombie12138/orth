package com.xxl.job.admin.model;

import java.util.Date;

/**
 * Job execution log entity for tracking trigger and execution lifecycle.
 *
 * <p>Records comprehensive execution details including trigger info, executor response, and alarm
 * status for monitoring and debugging purposes.
 */
public class XxlJobLog {

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

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getJobGroup() {
        return jobGroup;
    }

    public void setJobGroup(int jobGroup) {
        this.jobGroup = jobGroup;
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public String getExecutorAddress() {
        return executorAddress;
    }

    public void setExecutorAddress(String executorAddress) {
        this.executorAddress = executorAddress;
    }

    public String getExecutorHandler() {
        return executorHandler;
    }

    public void setExecutorHandler(String executorHandler) {
        this.executorHandler = executorHandler;
    }

    public String getExecutorParam() {
        return executorParam;
    }

    public void setExecutorParam(String executorParam) {
        this.executorParam = executorParam;
    }

    public String getExecutorShardingParam() {
        return executorShardingParam;
    }

    public void setExecutorShardingParam(String executorShardingParam) {
        this.executorShardingParam = executorShardingParam;
    }

    public int getExecutorFailRetryCount() {
        return executorFailRetryCount;
    }

    public void setExecutorFailRetryCount(int executorFailRetryCount) {
        this.executorFailRetryCount = executorFailRetryCount;
    }

    public Date getTriggerTime() {
        return triggerTime;
    }

    public void setTriggerTime(Date triggerTime) {
        this.triggerTime = triggerTime;
    }

    public Date getScheduleTime() {
        return scheduleTime;
    }

    public void setScheduleTime(Date scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    public int getTriggerCode() {
        return triggerCode;
    }

    public void setTriggerCode(int triggerCode) {
        this.triggerCode = triggerCode;
    }

    public String getTriggerMsg() {
        return triggerMsg;
    }

    public void setTriggerMsg(String triggerMsg) {
        this.triggerMsg = triggerMsg;
    }

    public Date getHandleTime() {
        return handleTime;
    }

    public void setHandleTime(Date handleTime) {
        this.handleTime = handleTime;
    }

    public int getHandleCode() {
        return handleCode;
    }

    public void setHandleCode(int handleCode) {
        this.handleCode = handleCode;
    }

    public String getHandleMsg() {
        return handleMsg;
    }

    public void setHandleMsg(String handleMsg) {
        this.handleMsg = handleMsg;
    }

    public int getAlarmStatus() {
        return alarmStatus;
    }

    public void setAlarmStatus(int alarmStatus) {
        this.alarmStatus = alarmStatus;
    }
}
