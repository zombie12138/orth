package com.xxl.job.admin.scheduler.misfire.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.admin.scheduler.config.XxlJobAdminBootstrap;
import com.xxl.job.admin.scheduler.misfire.MisfireHandler;
import com.xxl.job.admin.scheduler.trigger.TriggerTypeEnum;

/**
 * Misfire strategy that executes missed jobs immediately in the Orth scheduler.
 *
 * <p>When a job misfire is detected, this strategy triggers immediate compensatory execution. The
 * job runs as soon as possible to make up for the missed scheduled time, then resumes normal
 * scheduling afterward.
 *
 * <p>Use this strategy when:
 *
 * <ul>
 *   <li>Data consistency requires every scheduled execution to complete
 *   <li>Jobs are idempotent and can run slightly out of order
 *   <li>Late execution is better than skipping
 * </ul>
 *
 * <p>Examples of appropriate use cases:
 *
 * <ul>
 *   <li>ETL batch processing pipelines
 *   <li>Data synchronization jobs
 *   <li>Periodic backups or archival tasks
 *   <li>Cumulative metrics calculation
 * </ul>
 *
 * <p><b>Important:</b> The compensatory execution uses {@code scheduleTime = null} to indicate it's
 * a misfire recovery rather than a regular scheduled trigger. Job handlers can check this to adjust
 * behavior if needed.
 *
 * @author xuxueli 2020-10-29
 */
public class MisfireFireOnceNow extends MisfireHandler {
    private static final Logger logger = LoggerFactory.getLogger(MisfireFireOnceNow.class);

    /**
     * Triggers immediate execution to compensate for the missed schedule.
     *
     * <p>This method submits a trigger request with:
     *
     * <ul>
     *   <li>Trigger type: {@link TriggerTypeEnum#MISFIRE}
     *   <li>Schedule time: {@code null} (indicates misfire recovery)
     *   <li>Fail retry count: -1 (no automatic retry)
     * </ul>
     *
     * <p>The trigger is submitted to the thread pool asynchronously. If the pool is saturated, the
     * trigger may be queued or rejected according to the pool's policy.
     *
     * @param jobId the ID of the job that misfired
     */
    @Override
    public void handle(int jobId) {
        // Trigger immediate execution with MISFIRE type and null scheduleTime
        XxlJobAdminBootstrap.getInstance()
                .getJobTriggerPoolHelper()
                .trigger(jobId, TriggerTypeEnum.MISFIRE, -1, null, null, null, null);

        logger.warn(
                "Orth scheduler misfire (FIRE_ONCE_NOW): triggering immediate "
                        + "compensatory execution for job {}",
                jobId);
    }
}
