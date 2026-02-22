package com.abyss.orth.core.openapi.model;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
 *   <li>Admin updates {@code orth_job_log.handle_code} and {@code handle_msg}
 * </ol>
 *
 * @author xuxueli 2017-03-02
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
