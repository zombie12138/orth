package com.xxl.job.core.openapi;

import com.xxl.job.core.openapi.model.*;
import com.xxl.tool.response.Response;

/**
 * Executor RPC interface for admin-to-executor communication.
 *
 * <p>Admin scheduler uses this interface to: 1. Trigger job executions 2. Kill running jobs 3.
 * Check executor health and availability 4. Retrieve job execution logs
 *
 * <p>Implementation: {@link com.xxl.job.core.openapi.impl.ExecutorBizImpl} exposed via embedded
 * Netty server.
 */
public interface ExecutorBiz {

    /**
     * Health check endpoint.
     *
     * <p>Simple heartbeat to verify executor is alive. Always returns success if executor is
     * running.
     *
     * @return success response
     */
    Response<String> beat();

    /**
     * Idle check for specific job.
     *
     * <p>Used by routing strategies (e.g., BUSYOVER) to find idle executors. Returns success only
     * if the job thread is not running and has no pending triggers.
     *
     * @param idleBeatRequest request with job ID to check
     * @return success if idle, failure if job is running or has queued triggers
     */
    Response<String> idleBeat(IdleBeatRequest idleBeatRequest);

    /**
     * Triggers a job execution.
     *
     * <p>Main endpoint for job scheduling. Creates/reuses job thread, handles block strategies
     * (SERIAL_EXECUTION, DISCARD_LATER, COVER_EARLY), and queues the trigger for execution.
     *
     * @param triggerRequest trigger parameters (job ID, parameters, glue source, etc.)
     * @return success if queued, failure if handler not found or block strategy rejects
     */
    Response<String> run(TriggerRequest triggerRequest);

    /**
     * Kills a running job.
     *
     * <p>Stops the job thread immediately and removes it from the thread pool. The job will not
     * complete normally and callback will indicate it was killed.
     *
     * @param killRequest request with job ID to kill
     * @return success response (always succeeds, even if job not running)
     */
    Response<String> kill(KillRequest killRequest);

    /**
     * Retrieves job execution logs.
     *
     * <p>Returns log file content starting from specified line number. Used by admin console to
     * display real-time logs.
     *
     * @param logRequest request with log ID, date, and starting line number
     * @return log result with content and metadata
     */
    Response<LogResult> log(LogRequest logRequest);
}
