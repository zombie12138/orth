package com.abyss.orth.admin.scheduler.misfire;

/**
 * Abstract strategy for handling job execution misfires in the Orth scheduler.
 *
 * <p>A misfire occurs when a job's scheduled trigger time is delayed beyond the acceptable
 * threshold (typically 5+ seconds). This can happen due to:
 *
 * <ul>
 *   <li>Scheduler downtime or restart
 *   <li>System overload or resource exhaustion
 *   <li>Thread pool saturation (all worker threads busy)
 *   <li>Database lock contention during schedule processing
 * </ul>
 *
 * <p>The misfire handler is invoked by the scheduler to determine how to respond to these delayed
 * executions. Different strategies provide different behaviors:
 *
 * <ul>
 *   <li>Skip the missed execution and wait for next scheduled time
 *   <li>Execute immediately to compensate for the miss
 *   <li>Execute multiple times to catch up (not currently implemented)
 * </ul>
 *
 * <p>Misfire detection logic:
 *
 * <pre>
 * Current time - Scheduled time > Misfire threshold (5 seconds)
 * </pre>
 *
 * <p>Handler implementations should be lightweight and non-blocking. Heavy processing should be
 * delegated to background threads.
 *
 * @author xuxueli 2020-10-29
 * @see MisfireStrategyEnum
 */
public abstract class MisfireHandler {

    /**
     * Handles a misfire event for the specified job.
     *
     * <p>This method is called by the scheduler when a job's trigger time has been missed beyond
     * the acceptable threshold. The implementation determines whether to trigger compensatory
     * execution, skip the missed time, or take other action.
     *
     * <p>Implementations should:
     *
     * <ul>
     *   <li>Complete quickly to avoid blocking the scheduler thread
     *   <li>Log misfire events for monitoring and debugging
     *   <li>Handle failures gracefully (e.g., if trigger fails)
     * </ul>
     *
     * @param jobId the ID of the job that experienced a misfire
     */
    public abstract void handle(int jobId);
}
