package com.xxl.job.core.openapi.model;

import java.io.Serializable;

/**
 * Trigger request model for job execution via RPC.
 *
 * <p>This model encapsulates all necessary information for the executor to run a job, including:
 *
 * <ul>
 *   <li>Job identification and handler routing
 *   <li>Execution parameters and block strategy
 *   <li>Logging context (log ID and timestamp)
 *   <li>GLUE script source (for dynamic script jobs)
 *   <li>Sharding/broadcast information
 *   <li>Scheduling metadata (theoretical schedule time)
 * </ul>
 *
 * <p>Lifecycle: Created by admin scheduler → Serialized over Netty → Deserialized by executor →
 * Converted to {@link com.xxl.job.core.context.XxlJobContext}
 *
 * @author xuxueli
 * @since 1.0.0
 */
public class TriggerRequest implements Serializable {
    private static final long serialVersionUID = 42L;

    // ---------------------- Job Base Info ----------------------

    /** Maximum timeout value in seconds (24 hours) */
    private static final int MAX_TIMEOUT_SECONDS = 86400;

    /** Maximum executor parameter length */
    private static final int MAX_EXECUTOR_PARAMS_LENGTH = 512;

    /** Maximum GLUE source size in bytes (64KB) */
    private static final int MAX_GLUE_SOURCE_SIZE = 65536;

    /** String preview length for toString() method */
    private static final int PREVIEW_LENGTH = 50;

    /** Job ID (primary key from xxl_job_info table) */
    private int jobId;

    // ---------------------- Execution Configuration ----------------------

    /**
     * Job handler name (bean name or script identifier).
     *
     * <p>Examples: "sampleXxlJob" (Bean), "GLUE(Shell)" (Script), "666_1234567890.py" (GLUE Python)
     */
    private String executorHandler;

    /** Job parameters passed to handler (max 512 chars, can be JSON/plain text) */
    private String executorParams;

    /**
     * Block strategy when previous instance is still running.
     *
     * <p>Values: SERIAL_EXECUTION, DISCARD_LATER, COVER_EARLY (from {@link
     * com.xxl.job.core.constant.ExecutorBlockStrategyEnum})
     */
    private String executorBlockStrategy;

    /** Execution timeout in seconds (0 = no timeout, max 86400) */
    private int executorTimeout;

    // ---------------------- Logging Context ----------------------

    /** Log ID (primary key from xxl_job_log table) */
    private long logId;

    /** Log creation timestamp (milliseconds since epoch) */
    private long logDateTime;

    // ---------------------- GLUE Script Info ----------------------

    /**
     * GLUE type code for dynamic jobs.
     *
     * <p>Values: BEAN, GLUE_GROOVY, GLUE_SHELL, GLUE_PYTHON, GLUE_PHP, GLUE_NODEJS, GLUE_POWERSHELL
     * (from {@link com.xxl.job.core.glue.GlueTypeEnum})
     */
    private String glueType;

    /**
     * GLUE script source code (only for GLUE_* types).
     *
     * <p>Max size: 64KB (enforced by xxl_job_info.job_source column)
     */
    private String glueSource;

    /** GLUE script last update timestamp (milliseconds, used for cache invalidation) */
    private long glueUpdatetime;

    // ---------------------- Sharding/Broadcast Info ----------------------

    /** Current shard index (0-based, valid when using SHARDING_BROADCAST route strategy) */
    private int broadcastIndex;

    /** Total shard count (valid when using SHARDING_BROADCAST route strategy) */
    private int broadcastTotal;

    // ---------------------- Scheduling Metadata ----------------------

    /**
     * Theoretical schedule time (milliseconds since epoch).
     *
     * <p>Null for manual/API triggers. Set for CRON/FIX_RATE schedules and batch triggers. Used for
     * idempotent data processing with logical time windows.
     *
     * <p>Example: For a job scheduled at 10:00:00 but triggered at 10:00:03 due to misfire, this
     * value is 10:00:00.
     */
    private Long scheduleTime;

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public String getExecutorHandler() {
        return executorHandler;
    }

    public void setExecutorHandler(String executorHandler) {
        this.executorHandler = executorHandler;
    }

    public String getExecutorParams() {
        return executorParams;
    }

    public void setExecutorParams(String executorParams) {
        this.executorParams = executorParams;
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

    public long getLogId() {
        return logId;
    }

    public void setLogId(long logId) {
        this.logId = logId;
    }

    public long getLogDateTime() {
        return logDateTime;
    }

    public void setLogDateTime(long logDateTime) {
        this.logDateTime = logDateTime;
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

    public long getGlueUpdatetime() {
        return glueUpdatetime;
    }

    public void setGlueUpdatetime(long glueUpdatetime) {
        this.glueUpdatetime = glueUpdatetime;
    }

    public int getBroadcastIndex() {
        return broadcastIndex;
    }

    public void setBroadcastIndex(int broadcastIndex) {
        this.broadcastIndex = broadcastIndex;
    }

    public int getBroadcastTotal() {
        return broadcastTotal;
    }

    public void setBroadcastTotal(int broadcastTotal) {
        this.broadcastTotal = broadcastTotal;
    }

    public Long getScheduleTime() {
        return scheduleTime;
    }

    public void setScheduleTime(Long scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    /**
     * Returns string representation with all fields (excluding glueSource for brevity).
     *
     * <p>glueSource is truncated to prevent log flooding for large scripts.
     *
     * @return formatted string with all trigger parameters
     */
    @Override
    public String toString() {
        String glueSourcePreview =
                (glueSource != null && glueSource.length() > PREVIEW_LENGTH)
                        ? glueSource.substring(0, PREVIEW_LENGTH)
                                + "... ("
                                + glueSource.length()
                                + " chars)"
                        : glueSource;

        return "TriggerRequest{"
                + "jobId="
                + jobId
                + ", executorHandler='"
                + executorHandler
                + '\''
                + ", executorParams='"
                + executorParams
                + '\''
                + ", executorBlockStrategy='"
                + executorBlockStrategy
                + '\''
                + ", executorTimeout="
                + executorTimeout
                + ", logId="
                + logId
                + ", logDateTime="
                + logDateTime
                + ", glueType='"
                + glueType
                + '\''
                + ", glueSource='"
                + glueSourcePreview
                + '\''
                + ", glueUpdatetime="
                + glueUpdatetime
                + ", broadcastIndex="
                + broadcastIndex
                + ", broadcastTotal="
                + broadcastTotal
                + ", scheduleTime="
                + scheduleTime
                + '}';
    }
}
