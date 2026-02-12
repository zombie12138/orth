package com.xxl.job.core.openapi.model;

import java.io.Serializable;

/**
 * Job execution callback request.
 *
 * <p>Executors send this request to admin when a job execution completes (success or failure). The
 * admin updates the execution log record in the database with the handle result.
 *
 * <p>Callback flow:
 *
 * <ol>
 *   <li>Executor receives trigger request
 *   <li>Executor executes job handler
 *   <li>Executor collects execution result (exit code, error message, duration)
 *   <li>Executor sends callback request to admin
 *   <li>Admin updates {@code xxl_job_log.handle_code} and {@code handle_msg}
 * </ol>
 *
 * @author xuxueli 2017-03-02
 */
public class CallbackRequest implements Serializable {
    private static final long serialVersionUID = 42L;

    /** Log ID (identifies the execution instance) */
    private long logId;

    /** Log date (timestamp when log was created, used for log file path) */
    private long logDateTim;

    /** Handle result code (200=success, 500=failure) */
    private int handleCode;

    /** Handle result message (empty on success, error message on failure) */
    private String handleMsg;

    /** Default constructor for JSON deserialization */
    public CallbackRequest() {}

    /**
     * Constructs a callback request with all fields.
     *
     * @param logId log ID
     * @param logDateTim log creation timestamp
     * @param handleCode handle result code (200=success, 500=failure)
     * @param handleMsg handle result message
     */
    public CallbackRequest(long logId, long logDateTim, int handleCode, String handleMsg) {
        this.logId = logId;
        this.logDateTim = logDateTim;
        this.handleCode = handleCode;
        this.handleMsg = handleMsg;
    }

    public long getLogId() {
        return logId;
    }

    public void setLogId(long logId) {
        this.logId = logId;
    }

    public long getLogDateTim() {
        return logDateTim;
    }

    public void setLogDateTim(long logDateTim) {
        this.logDateTim = logDateTim;
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

    @Override
    public String toString() {
        return "CallbackRequest{"
                + "logId="
                + logId
                + ", logDateTim="
                + logDateTim
                + ", handleCode="
                + handleCode
                + ", handleMsg='"
                + handleMsg
                + '\''
                + '}';
    }
}
