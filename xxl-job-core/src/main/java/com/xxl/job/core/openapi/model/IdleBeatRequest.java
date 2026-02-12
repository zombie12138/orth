package com.xxl.job.core.openapi.model;

import java.io.Serializable;

/**
 * Idle beat request for checking if a job is currently running.
 *
 * <p>The admin uses this endpoint to check executor capacity before triggering a new job instance.
 * The executor returns success if the job is NOT currently running (idle), or failure if it's busy.
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Pre-trigger capacity check for SERIAL execution mode
 *   <li>Load-based routing decisions (BUSYOVER strategy)
 *   <li>Preventing concurrent executions of the same job
 * </ul>
 *
 * @author xuxueli 2020-04-11 22:27
 */
public class IdleBeatRequest implements Serializable {
    private static final long serialVersionUID = 42L;

    /** Job ID to check idle status for */
    private int jobId;

    /** Default constructor for JSON deserialization */
    public IdleBeatRequest() {}

    /**
     * Constructs an idle beat request.
     *
     * @param jobId job ID to check
     */
    public IdleBeatRequest(int jobId) {
        this.jobId = jobId;
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }
}
