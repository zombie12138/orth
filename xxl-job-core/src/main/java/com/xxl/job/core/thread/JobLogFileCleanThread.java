package com.xxl.job.core.thread;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.core.log.XxlJobFileAppender;
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

    private Thread localThread;
    private volatile boolean toStop = false;

    /**
     * Starts the log cleanup thread.
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

        localThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                while (!toStop) {
                                    try {
                                        cleanExpiredLogDirs(logRetentionDays);
                                    } catch (Throwable e) {
                                        if (!toStop) {
                                            logger.error("Error cleaning log files", e);
                                        }
                                    }

                                    try {
                                        TimeUnit.DAYS.sleep(1);
                                    } catch (Throwable e) {
                                        if (!toStop) {
                                            logger.error("Error sleeping cleanup thread", e);
                                        }
                                    }
                                }
                                logger.info(
                                        ">>>>>>>>>>> orth, executor JobLogFileCleanThread thread"
                                                + " destroy.");
                            }
                        });
        localThread.setDaemon(true);
        localThread.setName("orth, executor JobLogFileCleanThread");
        localThread.start();

        logger.info(
                "Started log cleanup thread with retention period of {} days", logRetentionDays);
    }

    /**
     * Stops the cleanup thread.
     *
     * <p>This method sets the stop flag and waits for the thread to terminate gracefully.
     */
    public void toStop() {
        toStop = true;

        if (localThread == null) {
            return;
        }

        // Interrupt and wait for termination
        localThread.interrupt();
        try {
            localThread.join();
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for cleanup thread to stop", e);
        }
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
        File logDir = new File(XxlJobFileAppender.getLogPath());
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
}
