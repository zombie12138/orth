package com.xxl.job.admin.scheduler.type.strategy;

import java.util.Date;

import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.scheduler.exception.XxlJobException;
import com.xxl.job.admin.scheduler.type.ScheduleType;

/**
 * Fixed-rate scheduling strategy for the Orth scheduler.
 *
 * <p>This strategy schedules jobs at fixed time intervals specified in seconds. Unlike fixed-delay
 * scheduling (which starts counting after job completion), fixed-rate scheduling triggers at
 * regular intervals regardless of execution duration.
 *
 * <p>Schedule configuration format: Integer value representing seconds between triggers
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code 60} - Every 60 seconds (1 minute)
 *   <li>{@code 300} - Every 300 seconds (5 minutes)
 *   <li>{@code 3600} - Every 3600 seconds (1 hour)
 * </ul>
 *
 * <p>Use fixed-rate scheduling for:
 *
 * <ul>
 *   <li>Simple periodic jobs without complex calendar logic
 *   <li>High-frequency monitoring or polling tasks
 *   <li>Jobs where exact interval timing is critical
 * </ul>
 *
 * <p><b>Note:</b> If job execution takes longer than the interval, jobs will queue up. Consider
 * using appropriate thread pool sizing and misfire strategies to handle this scenario.
 *
 * @author xuxueli 2020-10-29
 */
public class FixRateScheduleType extends ScheduleType {

    private static final long MILLIS_PER_SECOND = 1000L;

    /**
     * Calculates the next trigger time by adding the fixed interval to the reference time.
     *
     * <p>The interval is stored in {@link XxlJobInfo#getScheduleConf()} as a string representation
     * of seconds. This method parses the interval and adds it to the reference time.
     *
     * @param jobInfo the job configuration containing the interval in seconds
     * @param fromTime the reference time to calculate from
     * @return the next trigger time (fromTime + interval)
     * @throws XxlJobException if the schedule configuration is not a valid positive integer
     */
    @Override
    public Date generateNextTriggerTime(XxlJobInfo jobInfo, Date fromTime) {
        try {
            long intervalSeconds = Long.parseLong(jobInfo.getScheduleConf());

            if (intervalSeconds <= 0) {
                throw new XxlJobException(
                        "Fixed-rate interval must be positive: " + intervalSeconds);
            }

            long nextTriggerMillis = fromTime.getTime() + (intervalSeconds * MILLIS_PER_SECOND);
            return new Date(nextTriggerMillis);

        } catch (NumberFormatException e) {
            throw new XxlJobException(
                    "Invalid fixed-rate schedule configuration: " + jobInfo.getScheduleConf(), e);
        }
    }
}
