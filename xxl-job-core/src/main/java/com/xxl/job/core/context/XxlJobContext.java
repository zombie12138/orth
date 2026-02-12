package com.xxl.job.core.context;

/**
 * Orth job execution context.
 *
 * <p>Provides thread-local access to job execution metadata including job ID, parameters, logging
 * info, shard info, and schedule time. This context is automatically set by the executor framework
 * before job execution and cleared after completion.
 *
 * <p>Context is inherited by child threads using {@link InheritableThreadLocal} to support
 * concurrent execution within a job.
 */
public class XxlJobContext {

    public static final int HANDLE_CODE_SUCCESS = 200;
    public static final int HANDLE_CODE_FAIL = 500;
    public static final int HANDLE_CODE_TIMEOUT = 502;

    // ---------------------- base info ----------------------

    /** job id */
    private final long jobId;

    /** job param */
    private final String jobParam;

    // ---------------------- for log ----------------------

    /** log id */
    private final long logId;

    /** log timestamp */
    private final long logDateTime;

    /** log filename */
    private final String logFileName;

    // ---------------------- for shard ----------------------

    /** shard index */
    private final int shardIndex;

    /** shard total */
    private final int shardTotal;

    // ---------------------- for schedule ----------------------

    /** Theoretical schedule time (milliseconds), null for manual/API triggers */
    private final Long scheduleTime;

    // ---------------------- for SuperTask ----------------------

    /** Super parameter for SubTask instances, null for standalone/SuperTask jobs */
    private final String superTaskParam;

    // ---------------------- for handle ----------------------

    /**
     * handleCode：The result status of job execution
     *
     * <p>200 : success 500 : fail 502 : timeout
     */
    private int handleCode;

    /** handleMsg：The simple log msg of job execution */
    private String handleMsg;

    public XxlJobContext(
            long jobId,
            String jobParam,
            long logId,
            long logDateTime,
            String logFileName,
            int shardIndex,
            int shardTotal,
            Long scheduleTime) {
        this(
                jobId,
                jobParam,
                logId,
                logDateTime,
                logFileName,
                shardIndex,
                shardTotal,
                scheduleTime,
                null);
    }

    public XxlJobContext(
            long jobId,
            String jobParam,
            long logId,
            long logDateTime,
            String logFileName,
            int shardIndex,
            int shardTotal,
            Long scheduleTime,
            String superTaskParam) {
        this.jobId = jobId;
        this.jobParam = jobParam;
        this.logId = logId;
        this.logDateTime = logDateTime;
        this.logFileName = logFileName;
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
        this.scheduleTime = scheduleTime;
        this.superTaskParam = superTaskParam;

        this.handleCode = HANDLE_CODE_SUCCESS; // default success
    }

    public long getJobId() {
        return jobId;
    }

    public String getJobParam() {
        return jobParam;
    }

    public long getLogId() {
        return logId;
    }

    public long getLogDateTime() {
        return logDateTime;
    }

    public String getLogFileName() {
        return logFileName;
    }

    public int getShardIndex() {
        return shardIndex;
    }

    public int getShardTotal() {
        return shardTotal;
    }

    /**
     * Get theoretical schedule time (milliseconds), null for manual/API triggers
     *
     * @return schedule time in milliseconds, or null
     */
    public Long getScheduleTime() {
        return scheduleTime;
    }

    /**
     * Get super parameter for SubTask instances, null for standalone/SuperTask jobs.
     *
     * @return super parameter, or null
     */
    public String getSuperTaskParam() {
        return superTaskParam;
    }

    public void setHandleCode(int handleCode) {
        this.handleCode = handleCode;
    }

    public int getHandleCode() {
        return handleCode;
    }

    public void setHandleMsg(String handleMsg) {
        this.handleMsg = handleMsg;
    }

    public String getHandleMsg() {
        return handleMsg;
    }

    // ---------------------- Thread-local Context ----------------------

    /** Thread-local context store, inherited by child threads for concurrent job execution */
    private static final InheritableThreadLocal<XxlJobContext> contextHolder =
            new InheritableThreadLocal<>();

    /**
     * Sets the job context for the current thread.
     *
     * @param xxlJobContext the context to set
     */
    public static void setXxlJobContext(XxlJobContext xxlJobContext) {
        contextHolder.set(xxlJobContext);
    }

    /**
     * Gets the job context for the current thread.
     *
     * @return the current job context, or null if not in a job execution
     */
    public static XxlJobContext getXxlJobContext() {
        return contextHolder.get();
    }
}
