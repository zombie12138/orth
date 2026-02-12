package com.xxl.job.admin.scheduler.type.strategy;

import java.util.Date;

import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.scheduler.type.ScheduleType;

/**
 * Manual-only scheduling strategy for the Orth scheduler.
 *
 * <p>This strategy disables automatic scheduling for a job. Jobs configured with this schedule type
 * can only be triggered manually through:
 *
 * <ul>
 *   <li>Admin console UI (manual trigger button)
 *   <li>RESTful API calls (external triggers)
 *   <li>Parent job completion (child job triggers)
 * </ul>
 *
 * <p>Use cases for manual-only scheduling:
 *
 * <ul>
 *   <li>Ad-hoc data processing or batch operations
 *   <li>Jobs that should only run on-demand based on external events
 *   <li>Maintenance or diagnostic jobs
 *   <li>Jobs pending schedule configuration
 * </ul>
 *
 * @author xuxueli 2020-10-29
 */
public class NoneScheduleType extends ScheduleType {

    /**
     * Returns null to indicate no automatic scheduling.
     *
     * <p>By returning null, the scheduler will not add this job to the time-ring or pre-read queue.
     * The job remains in the system but requires explicit manual triggers.
     *
     * @param jobInfo the job configuration (unused for this strategy)
     * @param fromTime the reference time (unused for this strategy)
     * @return always null, indicating no automatic trigger time
     */
    @Override
    public Date generateNextTriggerTime(XxlJobInfo jobInfo, Date fromTime) {
        // No automatic scheduling; manual trigger only
        return null;
    }
}
