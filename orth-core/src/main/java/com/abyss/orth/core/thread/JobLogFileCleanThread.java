package com.abyss.orth.core.thread;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.abyss.orth.core.log.OrthJobFileAppender;
import com.xxl.tool.core.DateTool;
import com.xxl.tool.io.FileTool;

/**
 * Background thread for cleaning expired job log files.
 *
 * <p>This thread runs daily to delete log directories older than the configured retention period.
 * Log files are organized by date (yyyy-MM-dd format) under the log path, and entire date
 * directories are deleted when expired.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Daily cleanup schedule (sleeps 24 hours between runs)
 *   <li>Configurable retention period (minimum 3 days for safety)
 *   <li>Date-based directory deletion (deletes all logs for expired dates)
 *   <li>Graceful shutdown support
 * </ul>
 *
 * <p>Log directory structure:
 *
 * <pre>
 * {logPath}/
 *   2024-01-15/
 *     123.log
 *     456.log
 *   2024-01-16/
 *     789.log
 * </pre>
 *
 * @author xuxueli 2017-12-29 16:23:43
 */
public class JobLogFileCleanThread {
    private static final Logger logger = LoggerFactory.getLogger(JobLogFileCleanThread.class);

    /** Minimum retention days (safety threshold) */
    private static final int MIN_RETENTION_DAYS = 3;

    /** Singleton instance */
    private static final JobLogFileCleanThread instance = new JobLogFileCleanThread();

    public static JobLogFileCleanThread getInstance() {
        return instance;
    }

    private ScheduledExecutorService cleanupScheduler;

    /**
     * Starts the log cleanup scheduler.
     *
     * @param logRetentionDays number of days to retain log files (minimum 3 days)
     */
    public void start(final long logRetentionDays) {
        // Enforce minimum retention period for safety
        if (logRetentionDays < MIN_RETENTION_DAYS) {
            logger.warn(
                    "Log retention days {} is below minimum {}, cleanup disabled",
                    logRetentionDays,
                    MIN_RETENTION_DAYS);
            return;
        }

        cleanupScheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "orth-executor-JobLogFileCleanThread");
                            t.setDaemon(true);
                            return t;
                        });
        cleanupScheduler.scheduleWithFixedDelay(
                safeRunnable("log-file-cleanup", () -> cleanExpiredLogDirs(logRetentionDays)),
                0,
                1,
                TimeUnit.DAYS);

        logger.info(
                "Started log cleanup scheduler with retention period of {} days", logRetentionDays);
    }

    /**
     * Stops the cleanup scheduler.
     *
     * <p>Shuts down the scheduled executor and waits up to 5 seconds for termination.
     */
    public void toStop() {
        if (cleanupScheduler == null) {
            return;
        }

        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info(">>>>>>>>>>> orth, executor JobLogFileCleanThread thread destroy.");
    }

    /**
     * Cleans expired log directories.
     *
     * <p>Process:
     *
     * <ol>
     *   <li>Lists all directories under log path
     *   <li>Parses directory name as date (yyyy-MM-dd format)
     *   <li>Computes expiration date (logRetentionDays ago)
     *   <li>Deletes directory if it's older than expiration date
     * </ol>
     *
     * @param logRetentionDays retention period in days
     */
    private void cleanExpiredLogDirs(long logRetentionDays) {
        File logDir = new File(OrthJobFileAppender.getLogPath());
        File[] childDirs = logDir.listFiles();

        if (childDirs == null || childDirs.length == 0) {
            return;
        }

        // Get today's date at midnight for comparison
        Date todayDate = getTodayMidnight();

        // Check each directory
        for (File childFile : childDirs) {
            if (shouldDeleteLogDir(childFile, todayDate, logRetentionDays)) {
                boolean deleted = FileTool.delete(childFile);
                if (deleted) {
                    logger.info("Deleted expired log directory: {}", childFile.getName());
                } else {
                    logger.warn("Failed to delete log directory: {}", childFile.getName());
                }
            }
        }
    }

    /**
     * Checks if a log directory should be deleted.
     *
     * @param childFile directory to check
     * @param todayDate today's date at midnight
     * @param logRetentionDays retention period in days
     * @return true if directory should be deleted
     */
    private boolean shouldDeleteLogDir(File childFile, Date todayDate, long logRetentionDays) {
        // Must be a directory
        if (!childFile.isDirectory()) {
            return false;
        }

        // Must be date-formatted (contains hyphen)
        if (!childFile.getName().contains("-")) {
            return false;
        }

        // Parse creation date from directory name
        Date logFileCreateDate = parseLogDirDate(childFile.getName());
        if (logFileCreateDate == null) {
            return false;
        }

        // Check if expired
        Date expiredDate = DateTool.addDays(logFileCreateDate, (int) logRetentionDays);
        return todayDate.getTime() > expiredDate.getTime();
    }

    /**
     * Gets today's date at midnight (00:00:00.000).
     *
     * @return today's date at midnight
     */
    private Date getTodayMidnight() {
        Calendar todayCal = Calendar.getInstance();
        todayCal.set(Calendar.HOUR_OF_DAY, 0);
        todayCal.set(Calendar.MINUTE, 0);
        todayCal.set(Calendar.SECOND, 0);
        todayCal.set(Calendar.MILLISECOND, 0);
        return todayCal.getTime();
    }

    /**
     * Parses a date from a log directory name.
     *
     * @param dirName directory name (expected format: yyyy-MM-dd)
     * @return parsed date or null if parsing fails
     */
    private Date parseLogDirDate(String dirName) {
        try {
            return DateTool.parseDate(dirName);
        } catch (Exception e) {
            logger.debug("Failed to parse date from log directory name: {}", dirName);
            return null;
        }
    }

    /**
     * Wraps a runnable to catch and log exceptions, preventing {@link ScheduledExecutorService}
     * from silently cancelling future executions on uncaught exceptions.
     */
    private static Runnable safeRunnable(String taskName, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable e) {
                logger.error("Scheduled task '{}' threw exception", taskName, e);
            }
        };
    }
}
