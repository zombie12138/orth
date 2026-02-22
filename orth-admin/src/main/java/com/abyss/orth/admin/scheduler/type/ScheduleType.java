package com.abyss.orth.admin.scheduler.type;

import java.util.Date;

import com.abyss.orth.admin.model.JobInfo;

/**
 * Abstract strategy for calculating next job trigger times in the Orth scheduler.
 *
 * <p>This is the base class for the Strategy pattern implementation of schedule calculation. Each
 * concrete subclass implements a different scheduling algorithm (cron, fixed-rate, none, etc.).
 *
 * <p>The primary responsibility is to calculate when a job should next execute based on:
 *
 * <ul>
 *   <li>The job's schedule configuration (cron expression, interval, etc.)
 *   <li>A reference time (typically the current time or last trigger time)
 * </ul>
 *
 * <p>Schedule types are registered in {@link ScheduleTypeEnum} and selected at job configuration
 * time. The scheduler invokes this method to:
 *
 * <ul>
 *   <li>Initialize next trigger time when a job starts
 *   <li>Refresh next trigger time after each execution
 *   <li>Update trigger time after schedule configuration changes
 * </ul>
 *
 * <p>Implementation guidelines:
 *
 * <ul>
 *   <li>Return null for manual-only jobs (no automatic scheduling)
 *   <li>Validate schedule configuration and throw exceptions for invalid settings
 *   <li>Ensure thread-safety; implementations may be shared across multiple threads
 *   <li>Handle edge cases: DST transitions, leap seconds, invalid dates
 * </ul>
 *
 * @author xuxueli 2020-10-29
 */
public abstract class ScheduleType {

    /**
     * Calculates the next trigger time for a job.
     *
     * <p>This method is called by the scheduler to determine when a job should next execute. The
     * calculation is based on the job's schedule configuration and a reference time.
     *
     * @param jobInfo the job configuration containing schedule settings
     * @param fromTime the reference time to calculate from (typically current time or last trigger
     *     time)
     * @return the next trigger time, or null if the job should not be scheduled automatically
     * @throws Exception if the schedule configuration is invalid or calculation fails
     */
    public abstract Date generateNextTriggerTime(JobInfo jobInfo, Date fromTime) throws Exception;
}
