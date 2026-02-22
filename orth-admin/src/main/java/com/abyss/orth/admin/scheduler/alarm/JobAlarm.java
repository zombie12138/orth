package com.abyss.orth.admin.scheduler.alarm;

import com.abyss.orth.admin.model.JobInfo;
import com.abyss.orth.admin.model.JobLog;

/**
 * Strategy interface for job failure alarm notifications in the Orth scheduler.
 *
 * <p>This interface defines the contract for alarm delivery mechanisms. Implementations provide
 * different notification channels (email, webhook, SMS, etc.) for alerting administrators about job
 * failures.
 *
 * <p>Alarm triggers:
 *
 * <ul>
 *   <li>Job trigger failures (executor unreachable, routing errors)
 *   <li>Job execution failures (handler exceptions, timeout)
 *   <li>Job completion with non-success status codes
 * </ul>
 *
 * <p>Multiple alarm implementations can be active simultaneously. The {@link JobAlarmer} component
 * discovers all JobAlarm beans and invokes them in sequence when failures occur.
 *
 * <p>Implementation guidelines:
 *
 * <ul>
 *   <li>Complete quickly to avoid blocking the alarm thread
 *   <li>Handle exceptions gracefully and return false on failure
 *   <li>Log errors for troubleshooting alarm delivery issues
 *   <li>Respect alarm email configuration (may be empty/null)
 *   <li>Consider rate limiting to avoid alarm storms
 * </ul>
 *
 * @author xuxueli 2020-01-19
 * @see JobAlarmer
 */
public interface JobAlarm {

    /**
     * Sends an alarm notification for a failed job execution.
     *
     * <p>This method is called by the alarm monitoring thread when a job failure is detected. The
     * implementation should send notifications through the appropriate channel (email, webhook,
     * etc.).
     *
     * <p>Failure scenarios include:
     *
     * <ul>
     *   <li>Trigger failure: {@code triggerCode != SUCCESS}
     *   <li>Execution failure: {@code handleCode > 0 && handleCode != SUCCESS}
     *   <li>Both trigger and execution failures
     * </ul>
     *
     * @param info the job configuration including alarm recipients
     * @param jobLog the execution log containing failure details
     * @return true if alarm was sent successfully, false otherwise
     */
    boolean doAlarm(JobInfo info, JobLog jobLog);
}
