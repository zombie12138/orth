package com.abyss.orth.admin.scheduler.thread;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.abyss.orth.admin.model.JobLogReport;
import com.abyss.orth.admin.scheduler.config.OrthAdminBootstrap;

/**
 * job log report helper
 *
 * @author xuxueli 2019-11-22
 */
public class JobLogReportHelper {
    private static final Logger logger = LoggerFactory.getLogger(JobLogReportHelper.class);

    private ScheduledExecutorService logReportScheduler;

    /** Tracks last log cleanup time to ensure cleanup runs at most once per day. */
    private long lastCleanLogTime = 0;

    /** Starts the log report scheduler. */
    public void start() {
        logReportScheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "orth-admin-JobLogReportHelper");
                            t.setDaemon(true);
                            return t;
                        });
        logReportScheduler.scheduleWithFixedDelay(
                safeRunnable("log-report", this::processLogReportCycle), 0, 1, TimeUnit.MINUTES);
    }

    /**
     * Processes one log report cycle: refreshes log reports for the last 3 days and cleans expired
     * logs (at most once per day).
     */
    private void processLogReportCycle() {
        // 1. log-report refresh: refresh log report in 3 days
        try {
            for (int i = 0; i < 3; i++) {

                // today
                Calendar itemDay = Calendar.getInstance();
                itemDay.add(Calendar.DAY_OF_MONTH, -i);
                itemDay.set(Calendar.HOUR_OF_DAY, 0);
                itemDay.set(Calendar.MINUTE, 0);
                itemDay.set(Calendar.SECOND, 0);
                itemDay.set(Calendar.MILLISECOND, 0);

                Date todayFrom = itemDay.getTime();

                itemDay.set(Calendar.HOUR_OF_DAY, 23);
                itemDay.set(Calendar.MINUTE, 59);
                itemDay.set(Calendar.SECOND, 59);
                itemDay.set(Calendar.MILLISECOND, 999);

                Date todayTo = itemDay.getTime();

                // refresh log-report every minute
                JobLogReport orthJobLogReport = new JobLogReport();
                orthJobLogReport.setTriggerDay(todayFrom);
                orthJobLogReport.setRunningCount(0);
                orthJobLogReport.setSuccessCount(0);
                orthJobLogReport.setFailCount(0);

                Map<String, Object> triggerCountMap =
                        OrthAdminBootstrap.getInstance()
                                .getJobLogMapper()
                                .findLogReport(todayFrom, todayTo);
                if (triggerCountMap != null && !triggerCountMap.isEmpty()) {
                    int triggerDayCount =
                            triggerCountMap.containsKey("triggerDayCount")
                                    ? Integer.parseInt(
                                            String.valueOf(triggerCountMap.get("triggerDayCount")))
                                    : 0;
                    int triggerDayCountRunning =
                            triggerCountMap.containsKey("triggerDayCountRunning")
                                    ? Integer.parseInt(
                                            String.valueOf(
                                                    triggerCountMap.get("triggerDayCountRunning")))
                                    : 0;
                    int triggerDayCountSuc =
                            triggerCountMap.containsKey("triggerDayCountSuc")
                                    ? Integer.parseInt(
                                            String.valueOf(
                                                    triggerCountMap.get("triggerDayCountSuc")))
                                    : 0;
                    int triggerDayCountFail =
                            triggerDayCount - triggerDayCountRunning - triggerDayCountSuc;

                    orthJobLogReport.setRunningCount(triggerDayCountRunning);
                    orthJobLogReport.setSuccessCount(triggerDayCountSuc);
                    orthJobLogReport.setFailCount(triggerDayCountFail);
                }

                // do refresh
                OrthAdminBootstrap.getInstance()
                        .getJobLogReportMapper()
                        .saveOrUpdate(orthJobLogReport);
            }
        } catch (Throwable e) {
            logger.error(
                    ">>>>>>>>>>> orth, JobLogReportHelper(log-report refresh) error:{}",
                    e.getMessage(),
                    e);
        }

        // 2. log-clean: switch open & once each day
        try {
            if (OrthAdminBootstrap.getInstance().getLogretentiondays() > 0
                    && System.currentTimeMillis() - lastCleanLogTime > 24 * 60 * 60 * 1000) {

                // expire-time
                Calendar expiredDay = Calendar.getInstance();
                expiredDay.add(
                        Calendar.DAY_OF_MONTH,
                        -1 * OrthAdminBootstrap.getInstance().getLogretentiondays());
                expiredDay.set(Calendar.HOUR_OF_DAY, 0);
                expiredDay.set(Calendar.MINUTE, 0);
                expiredDay.set(Calendar.SECOND, 0);
                expiredDay.set(Calendar.MILLISECOND, 0);
                Date clearBeforeTime = expiredDay.getTime();

                // clean expired log
                List<Long> logIds = null;
                do {
                    logIds =
                            OrthAdminBootstrap.getInstance()
                                    .getJobLogMapper()
                                    .findClearLogIds(0, 0, clearBeforeTime, 0, 1000);
                    if (logIds != null && !logIds.isEmpty()) {
                        OrthAdminBootstrap.getInstance().getJobLogMapper().clearLog(logIds);
                    }
                } while (logIds != null && !logIds.isEmpty());

                // update clean time
                lastCleanLogTime = System.currentTimeMillis();
            }
        } catch (Exception e) {
            logger.error(
                    ">>>>>>>>>>> orth, JobLogReportHelper(log-clean) error:{}", e.getMessage(), e);
        }
    }

    /** Stops the log report scheduler. */
    public void stop() {
        logReportScheduler.shutdown();
        try {
            if (!logReportScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                logReportScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            logReportScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info(">>>>>>>>>>> orth, job log report thread stop");
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
