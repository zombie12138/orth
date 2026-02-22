package com.abyss.orth.core.openapi.model;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Job termination request.
 *
 * <p>The admin sends this request to executors to forcefully terminate a running job. The executor
 * interrupts the job handler thread and cleans up resources.
 *
 * <p>Kill mechanism:
 *
 * <ol>
 *   <li>Admin calls /kill endpoint with job ID
 *   <li>Executor looks up running {@link com.abyss.orth.core.thread.JobThread} by job ID
 *   <li>Executor interrupts thread and waits for termination (with timeout)
 *   <li>Executor returns success/failure response
 * </ol>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Manual job termination from admin UI
 *   <li>Timeout enforcement (executor-side timeout handler)
 *   <li>Emergency shutdown of runaway jobs
 * </ul>
 *
 * @author xuxueli 2020-04-11 22:27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KillRequest implements Serializable {
    private static final long serialVersionUID = 42L;

    /** Job ID to terminate */
    private int jobId;
}
