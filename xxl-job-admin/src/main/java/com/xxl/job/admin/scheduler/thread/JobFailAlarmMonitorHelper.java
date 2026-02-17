package com.xxl.job.admin.scheduler.thread;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.model.XxlJobLog;
import com.xxl.job.admin.scheduler.config.XxlJobAdminBootstrap;
import com.xxl.job.admin.scheduler.trigger.TriggerTypeEnum;
import com.xxl.job.admin.util.I18nUtil;

/**
 * Job failure alarm and retry monitor.
 *
 * <p>This helper runs a background thread that periodically:
 *
 * <ol>
 *   <li>Scans for failed job executions (alarm_status = 0)
 *   <li>Acquires pessimistic lock on each failed log entry
 *   <li>Triggers retry if executor_fail_retry_count > 0
 *   <li>Sends alarm notification via configured alarm channels
 *   <li>Updates alarm_status based on outcome
 * </ol>
 *
 * <p><b>Alarm Status State Machine:</b>
 *
 * <ul>
 *   <li>0 = DEFAULT (pending processing)
 *   <li>-1 = LOCKED (currently being processed, pessimistic lock)
 *   <li>1 = NO_ALARM_NEEDED (job info not found, deleted)
 *   <li>2 = ALARM_SUCCESS (alarm sent successfully)
 *   <li>3 = ALARM_FAILED (alarm send failed)
 * </ul>
 *
 * <p><b>Thread Safety:</b> Uses database-level pessimistic locking (alarm_status 0 → -1) to ensure
 * only one thread processes each failed log entry in distributed deployments.
 *
 * @author xuxueli 2015-9-1 18:05:56
 */
public class JobFailAlarmMonitorHelper {
    private static final Logger logger = LoggerFactory.getLogger(JobFailAlarmMonitorHelper.class);

    // ---------------------- Constants ----------------------

    /** Maximum number of failed log entries to fetch per scan cycle. */
    private static final int MAX_FETCH_SIZE = 1000;

    /** Sleep interval between scan cycles (seconds). */
    private static final int SCAN_INTERVAL_SECONDS = 10;

    /** Alarm status: Default state, pending processing. */
    private static final int ALARM_STATUS_DEFAULT = 0;

    /** Alarm status: Locked for processing (pessimistic lock). */
    private static final int ALARM_STATUS_LOCKED = -1;

    /** Alarm status: No alarm needed (job deleted or not found). */
    private static final int ALARM_STATUS_NO_ALARM_NEEDED = 1;

    /** Alarm status: Alarm sent successfully. */
    private static final int ALARM_STATUS_SUCCESS = 2;

    /** Alarm status: Alarm send failed. */
    private static final int ALARM_STATUS_FAILED = 3;

    // ---------------------- Fields ----------------------

    private Thread monitorThread;
    private volatile boolean toStop = false;

    // ---------------------- Lifecycle ----------------------

    /**
     * Starts the fail alarm monitor thread.
     *
     * <p>Creates a daemon thread that continuously scans for failed job logs and processes alarms
     * and retries.
     */
    public void start() {
        monitorThread = new Thread(this::monitorFailedJobs);
        monitorThread.setDaemon(true);
        monitorThread.setName("orth-admin-JobFailAlarmMonitor");
        monitorThread.start();
    }

    /**
     * Stops the fail alarm monitor thread gracefully.
     *
     * <p>Sets stop flag, interrupts the thread, and waits for it to terminate.
     */
    public void stop() {
        toStop = true;
        monitorThread.interrupt();
        try {
            monitorThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while waiting for monitor thread to stop", e);
        }
    }

    // ---------------------- Core Monitor Logic ----------------------

    /**
     * Main monitoring loop that processes failed job logs.
     *
     * <p>Runs continuously until stopped, with configurable sleep interval between scans.
     */
    private void monitorFailedJobs() {
        logger.info("orth job fail alarm monitor started");

        while (!toStop) {
            try {
                processScanCycle();
            } catch (Throwable e) {
                if (!toStop) {
                    logger.error("orth job fail monitor error: {}", e.getMessage(), e);
                }
            }

            sleepBetweenScans();
        }

        logger.info("orth job fail alarm monitor stopped");
    }

    /**
     * Processes one scan cycle: fetches failed logs and handles each entry.
     *
     * <p>Fetches up to {@link #MAX_FETCH_SIZE} failed log entries and processes them sequentially.
     */
    private void processScanCycle() {
        List<Long> failLogIds = fetchFailedLogIds();

        if (failLogIds == null || failLogIds.isEmpty()) {
            return;
        }

        failLogIds.stream().forEach(this::processFailedLog);
    }

    /**
     * Fetches failed log IDs from database.
     *
     * @return list of log IDs with alarm_status = 0 (DEFAULT), limited to {@link #MAX_FETCH_SIZE}
     */
    private List<Long> fetchFailedLogIds() {
        return XxlJobAdminBootstrap.getInstance()
                .getXxlJobLogMapper()
                .findFailJobLogIds(MAX_FETCH_SIZE);
    }

    /**
     * Processes a single failed log entry: locks, retries, and alarms.
     *
     * <p>Steps:
     *
     * <ol>
     *   <li>Acquire pessimistic lock (alarm_status 0 → -1)
     *   <li>Load log and job info
     *   <li>Trigger retry if configured
     *   <li>Send alarm notification
     *   <li>Update final alarm status
     * </ol>
     *
     * @param failLogId the log ID to process
     */
    private void processFailedLog(long failLogId) {
        // Acquire pessimistic lock
        if (!acquireAlarmLock(failLogId)) {
            return; // Already processed by another thread
        }

        XxlJobLog log = loadJobLog(failLogId);
        XxlJobInfo info = loadJobInfo(log.getJobId());

        // Handle retry if configured
        handleFailRetry(log, info);

        // Handle alarm notification
        int finalAlarmStatus = determineAlarmStatus(info, log);
        releaseAlarmLock(failLogId, finalAlarmStatus);
    }

    // ---------------------- Locking ----------------------

    /**
     * Acquires pessimistic lock on the log entry.
     *
     * <p>Uses optimistic locking semantics: UPDATE ... WHERE alarm_status = 0 (expected) SET
     * alarm_status = -1 (locked)
     *
     * @param logId the log ID to lock
     * @return true if lock acquired successfully, false if already locked
     */
    private boolean acquireAlarmLock(long logId) {
        int rowsUpdated =
                XxlJobAdminBootstrap.getInstance()
                        .getXxlJobLogMapper()
                        .updateAlarmStatus(logId, ALARM_STATUS_DEFAULT, ALARM_STATUS_LOCKED);
        return rowsUpdated > 0;
    }

    /**
     * Releases pessimistic lock and updates final alarm status.
     *
     * <p>Updates: alarm_status = -1 (locked) → newStatus
     *
     * @param logId the log ID to unlock
     * @param newStatus final alarm status (NO_ALARM_NEEDED, SUCCESS, or FAILED)
     */
    private void releaseAlarmLock(long logId, int newStatus) {
        XxlJobAdminBootstrap.getInstance()
                .getXxlJobLogMapper()
                .updateAlarmStatus(logId, ALARM_STATUS_LOCKED, newStatus);
    }

    // ---------------------- Data Loading ----------------------

    /**
     * Loads job log from database.
     *
     * @param logId the log ID
     * @return the job log entry
     */
    private XxlJobLog loadJobLog(long logId) {
        return XxlJobAdminBootstrap.getInstance().getXxlJobLogMapper().load(logId);
    }

    /**
     * Loads job info from database.
     *
     * @param jobId the job ID
     * @return the job info, or null if not found (deleted)
     */
    private XxlJobInfo loadJobInfo(int jobId) {
        return XxlJobAdminBootstrap.getInstance().getXxlJobInfoMapper().loadById(jobId);
    }

    // ---------------------- Retry Logic ----------------------

    /**
     * Handles fail retry if configured.
     *
     * <p>Triggers retry execution if executor_fail_retry_count > 0, with decremented retry count.
     * Updates trigger message with retry marker.
     *
     * @param log the failed job log
     * @param info the job info (may be null if job deleted)
     */
    private void handleFailRetry(XxlJobLog log, XxlJobInfo info) {
        if (!shouldRetry(log)) {
            return;
        }

        int remainingRetries = log.getExecutorFailRetryCount() - 1;
        triggerRetry(log, remainingRetries);
        appendRetryMessage(log);
        updateTriggerInfo(log);
    }

    /**
     * Checks if retry should be triggered.
     *
     * @param log the failed job log
     * @return true if retry count > 0
     */
    private boolean shouldRetry(XxlJobLog log) {
        return log.getExecutorFailRetryCount() > 0;
    }

    /**
     * Triggers retry execution, preserving the original schedule time.
     *
     * @param log the failed job log
     * @param remainingRetries decremented retry count to pass to executor
     */
    private void triggerRetry(XxlJobLog log, int remainingRetries) {
        Long scheduleTime = log.getScheduleTime() != null ? log.getScheduleTime().getTime() : null;
        XxlJobAdminBootstrap.getInstance()
                .getJobTriggerPoolHelper()
                .trigger(
                        log.getJobId(),
                        TriggerTypeEnum.RETRY,
                        remainingRetries,
                        log.getExecutorShardingParam(),
                        log.getExecutorParam(),
                        null,
                        scheduleTime);
    }

    /**
     * Appends retry marker to trigger message.
     *
     * @param log the failed job log
     */
    private void appendRetryMessage(XxlJobLog log) {
        String retryMsg =
                "<br><br><span style=\"color:#00c0ef;\"> >>>>>>>>>>>"
                        + I18nUtil.getString("jobconf_trigger_type_retry")
                        + "<<<<<<<<<<< </span><br>";
        log.setTriggerMsg(log.getTriggerMsg() + retryMsg);
    }

    /**
     * Updates trigger info in database after retry.
     *
     * @param log the job log with updated trigger message
     */
    private void updateTriggerInfo(XxlJobLog log) {
        XxlJobAdminBootstrap.getInstance().getXxlJobLogMapper().updateTriggerInfo(log);
    }

    // ---------------------- Alarm Logic ----------------------

    /**
     * Determines final alarm status after sending alarm.
     *
     * <p>Decision logic:
     *
     * <ul>
     *   <li>If job info not found (deleted): NO_ALARM_NEEDED
     *   <li>If alarm sent successfully: SUCCESS
     *   <li>If alarm send failed: FAILED
     * </ul>
     *
     * @param info the job info (may be null if deleted)
     * @param log the failed job log
     * @return alarm status code (NO_ALARM_NEEDED, SUCCESS, or FAILED)
     */
    private int determineAlarmStatus(XxlJobInfo info, XxlJobLog log) {
        if (isJobDeleted(info)) {
            return ALARM_STATUS_NO_ALARM_NEEDED;
        }

        boolean alarmSent = sendAlarm(info, log);
        return alarmSent ? ALARM_STATUS_SUCCESS : ALARM_STATUS_FAILED;
    }

    /**
     * Checks if job has been deleted.
     *
     * @param info the job info
     * @return true if job not found in database
     */
    private boolean isJobDeleted(XxlJobInfo info) {
        return info == null;
    }

    /**
     * Sends alarm notification via configured alarm channels.
     *
     * @param info the job info
     * @param log the failed job log
     * @return true if alarm sent successfully, false otherwise
     */
    private boolean sendAlarm(XxlJobInfo info, XxlJobLog log) {
        return XxlJobAdminBootstrap.getInstance().getJobAlarmer().alarm(info, log);
    }

    // ---------------------- Utility ----------------------

    /**
     * Sleeps between scan cycles.
     *
     * <p>Sleeps for {@link #SCAN_INTERVAL_SECONDS} seconds, ignoring interrupts unless stopping.
     */
    private void sleepBetweenScans() {
        try {
            TimeUnit.SECONDS.sleep(SCAN_INTERVAL_SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!toStop) {
                logger.error("Monitor thread interrupted during sleep", e);
            }
        }
    }
}
