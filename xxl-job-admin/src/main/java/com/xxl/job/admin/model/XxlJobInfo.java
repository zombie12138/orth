package com.xxl.job.admin.model;

import java.util.Date;

/**
 * Job configuration entity representing a scheduled task.
 *
 * <p>Defines complete job metadata including scheduling rules, execution strategy, and SuperTask
 * relationships for template-instance pattern.
 */
public class XxlJobInfo {

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
    private String
            superTaskParam; // Instance-specific parameter (injected as $ORTH_SUPER_TASK_PARAM)

    // Transient field: SuperTask name for display (not persisted to database)
    private String superTaskName;

    // Scheduling state
    private int triggerStatus; // Current status (see TriggerStatus)
    private long triggerLastTime; // Last trigger timestamp (milliseconds)
    private long triggerNextTime; // Next trigger timestamp (milliseconds)

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getJobGroup() {
        return jobGroup;
    }

    public void setJobGroup(int jobGroup) {
        this.jobGroup = jobGroup;
    }

    public String getJobDesc() {
        return jobDesc;
    }

    public void setJobDesc(String jobDesc) {
        this.jobDesc = jobDesc;
    }

    public Date getAddTime() {
        return addTime;
    }

    public void setAddTime(Date addTime) {
        this.addTime = addTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getAlarmEmail() {
        return alarmEmail;
    }

    public void setAlarmEmail(String alarmEmail) {
        this.alarmEmail = alarmEmail;
    }

    public String getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(String scheduleType) {
        this.scheduleType = scheduleType;
    }

    public String getScheduleConf() {
        return scheduleConf;
    }

    public void setScheduleConf(String scheduleConf) {
        this.scheduleConf = scheduleConf;
    }

    public String getMisfireStrategy() {
        return misfireStrategy;
    }

    public void setMisfireStrategy(String misfireStrategy) {
        this.misfireStrategy = misfireStrategy;
    }

    public String getExecutorRouteStrategy() {
        return executorRouteStrategy;
    }

    public void setExecutorRouteStrategy(String executorRouteStrategy) {
        this.executorRouteStrategy = executorRouteStrategy;
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

    public String getExecutorBlockStrategy() {
        return executorBlockStrategy;
    }

    public void setExecutorBlockStrategy(String executorBlockStrategy) {
        this.executorBlockStrategy = executorBlockStrategy;
    }

    public int getExecutorTimeout() {
        return executorTimeout;
    }

    public void setExecutorTimeout(int executorTimeout) {
        this.executorTimeout = executorTimeout;
    }

    public int getExecutorFailRetryCount() {
        return executorFailRetryCount;
    }

    public void setExecutorFailRetryCount(int executorFailRetryCount) {
        this.executorFailRetryCount = executorFailRetryCount;
    }

    public String getGlueType() {
        return glueType;
    }

    public void setGlueType(String glueType) {
        this.glueType = glueType;
    }

    public String getGlueSource() {
        return glueSource;
    }

    public void setGlueSource(String glueSource) {
        this.glueSource = glueSource;
    }

    public String getGlueRemark() {
        return glueRemark;
    }

    public void setGlueRemark(String glueRemark) {
        this.glueRemark = glueRemark;
    }

    public Date getGlueUpdatetime() {
        return glueUpdatetime;
    }

    public void setGlueUpdatetime(Date glueUpdatetime) {
        this.glueUpdatetime = glueUpdatetime;
    }

    public String getChildJobId() {
        return childJobId;
    }

    public void setChildJobId(String childJobId) {
        this.childJobId = childJobId;
    }

    public int getTriggerStatus() {
        return triggerStatus;
    }

    public void setTriggerStatus(int triggerStatus) {
        this.triggerStatus = triggerStatus;
    }

    public long getTriggerLastTime() {
        return triggerLastTime;
    }

    public void setTriggerLastTime(long triggerLastTime) {
        this.triggerLastTime = triggerLastTime;
    }

    public long getTriggerNextTime() {
        return triggerNextTime;
    }

    public void setTriggerNextTime(long triggerNextTime) {
        this.triggerNextTime = triggerNextTime;
    }

    public Integer getSuperTaskId() {
        return superTaskId;
    }

    public void setSuperTaskId(Integer superTaskId) {
        this.superTaskId = superTaskId;
    }

    public String getSuperTaskParam() {
        return superTaskParam;
    }

    public void setSuperTaskParam(String superTaskParam) {
        this.superTaskParam = superTaskParam;
    }

    public String getSuperTaskName() {
        return superTaskName;
    }

    public void setSuperTaskName(String superTaskName) {
        this.superTaskName = superTaskName;
    }
}
