package com.xxl.job.admin.scheduler.misfire.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.admin.scheduler.misfire.MisfireHandler;

/**
 * Misfire strategy that skips missed executions in the Orth scheduler.
 *
 * <p>When a job misfire is detected, this strategy simply logs the event and takes no corrective
 * action. The missed execution is skipped, and the scheduler will wait for the next regularly
 * scheduled trigger time.
 *
 * <p>Use this strategy when:
 *
 * <ul>
 *   <li>Timeliness is critical - late execution is worse than no execution
 *   <li>Jobs are idempotent and missing one execution is acceptable
 *   <li>Executing late would cause data inconsistency or conflicts
 * </ul>
 *
 * <p>Examples of appropriate use cases:
 *
 * <ul>
 *   <li>Real-time data snapshots (e.g., market close prices)
 *   <li>Time-sensitive notifications
 *   <li>Periodic cache refreshes where stale data is acceptable
 * </ul>
 *
 * @author xuxueli 2020-10-29
 */
public class MisfireDoNothing extends MisfireHandler {
    private static final Logger logger = LoggerFactory.getLogger(MisfireDoNothing.class);

    /**
     * Logs the misfire event without triggering compensatory execution.
     *
     * <p>This method simply records that a misfire occurred. The missed execution is abandoned, and
     * the job will resume at its next regularly scheduled time.
     *
     * @param jobId the ID of the job that misfired
     */
    @Override
    public void handle(int jobId) {
        logger.warn(
                "Orth scheduler misfire (DO_NOTHING): skipping missed execution for job {}", jobId);
    }
}
