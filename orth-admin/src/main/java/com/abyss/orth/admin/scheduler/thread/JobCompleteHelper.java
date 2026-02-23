package com.abyss.orth.admin.scheduler.thread;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.abyss.orth.admin.model.JobLog;
import com.abyss.orth.admin.scheduler.config.OrthAdminBootstrap;
import com.abyss.orth.admin.util.I18nUtil;
import com.abyss.orth.core.context.OrthJobContext;
import com.abyss.orth.core.openapi.model.CallbackRequest;
import com.xxl.tool.core.DateTool;
import com.xxl.tool.response.Response;

/**
 * Job completion handler for processing executor callbacks and detecting lost job results.
 *
 * <p>This helper manages two primary responsibilities:
 *
 * <ul>
 *   <li><b>Callback Processing</b>: Receives and processes execution results from executors via
 *       async thread pool
 *   <li><b>Lost Result Detection</b>: Monitors jobs stuck in "running" state and marks them as
 *       failed when executors are offline
 * </ul>
 *
 * <p><b>Lost Result Detection Logic</b>:
 *
 * <ul>
 *   <li>Runs every 60 seconds
 *   <li>Identifies jobs in "running" state for 10+ minutes
 *   <li>Verifies executor is offline (no heartbeat)
 *   <li>Marks job as failed with localized failure message
 * </ul>
 *
 * <p><b>Thread Pool Configuration</b>:
 *
 * <ul>
 *   <li>Core threads: 2
 *   <li>Max threads: 20
 *   <li>Queue capacity: 3000
 *   <li>Rejection policy: Execute in caller thread (blocking backpressure)
 * </ul>
 *
 * @author xuxueli 2015-9-1 18:05:56
 */
public class JobCompleteHelper {
    private static final Logger logger = LoggerFactory.getLogger(JobCompleteHelper.class);

    // Thread pool configuration constants
    private static final int CALLBACK_CORE_POOL_SIZE = 2;
    private static final int CALLBACK_MAX_POOL_SIZE = 20;
    private static final long CALLBACK_KEEP_ALIVE_SECONDS = 30L;
    private static final int CALLBACK_QUEUE_CAPACITY = 3000;

    // Timing constants
    private static final long STARTUP_DELAY_MS = 50L;
    private static final long MONITOR_INTERVAL_SECONDS = 60L;
    private static final int LOST_JOB_TIMEOUT_MINUTES = -10;

    // ---------------------- monitor ----------------------

    private ThreadPoolExecutor callbackThreadPool = null;
    private ScheduledExecutorService monitorScheduler;

    /**
     * Starts the job completion helper with callback thread pool and lost result monitor.
     *
     * <p>Initializes:
     *
     * <ul>
     *   <li>Async thread pool for processing executor callbacks
     *   <li>Scheduled executor for detecting lost job results
     * </ul>
     */
    public void start() {
        // Initialize callback processing thread pool
        callbackThreadPool =
                new ThreadPoolExecutor(
                        CALLBACK_CORE_POOL_SIZE,
                        CALLBACK_MAX_POOL_SIZE,
                        CALLBACK_KEEP_ALIVE_SECONDS,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(CALLBACK_QUEUE_CAPACITY),
                        r -> new Thread(r, "orth-admin-callback-pool-" + r.hashCode()),
                        (r, executor) -> {
                            r.run();
                            logger.warn(
                                    ">>>>>>>>>>> orth, callback processing too fast, "
                                            + "executing in caller thread (backpressure)");
                        });

        // Initialize lost result monitor scheduler
        monitorScheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "orth-admin-lost-result-monitor");
                            t.setDaemon(true);
                            return t;
                        });
        monitorScheduler.scheduleWithFixedDelay(
                safeRunnable("lost-result-monitor", this::processLostJobs),
                STARTUP_DELAY_MS,
                MONITOR_INTERVAL_SECONDS * 1000,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Processes jobs that have lost their results due to executor failures.
     *
     * <p>Identifies jobs in "running" state for more than 10 minutes where the executor is offline,
     * and marks them as failed.
     */
    private void processLostJobs() {
        Date lostJobThreshold = DateTool.addMinutes(new Date(), LOST_JOB_TIMEOUT_MINUTES);
        List<Long> lostJobIds =
                OrthAdminBootstrap.getInstance().getJobLogMapper().findLostJobIds(lostJobThreshold);

        Optional.ofNullable(lostJobIds)
                .filter(ids -> !ids.isEmpty())
                .ifPresent(
                        ids ->
                                ids.stream()
                                        .forEach(
                                                logId -> {
                                                    JobLog failedLog = new JobLog();
                                                    failedLog.setId(logId);
                                                    failedLog.setHandleTime(new Date());
                                                    failedLog.setHandleCode(
                                                            OrthJobContext.HANDLE_CODE_FAIL);
                                                    failedLog.setHandleMsg(
                                                            I18nUtil.getString("joblog_lost_fail"));

                                                    OrthAdminBootstrap.getInstance()
                                                            .getJobCompleter()
                                                            .complete(failedLog);
                                                }));
    }

    /**
     * Stops the job completion helper, shutting down callback thread pool and monitor scheduler.
     *
     * <p>Shutdown sequence:
     *
     * <ul>
     *   <li>Forcefully shuts down callback thread pool
     *   <li>Gracefully shuts down monitor scheduler (waits up to 5 seconds)
     * </ul>
     */
    public void stop() {
        // Shutdown callback processing thread pool
        callbackThreadPool.shutdownNow();

        // Stop monitor scheduler
        monitorScheduler.shutdown();
        try {
            if (!monitorScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                monitorScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitorScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info(">>>>>>>>>>> orth, lost result monitor stopped");
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

    // ---------------------- callback processing ----------------------

    /**
     * Processes callback requests from executors asynchronously.
     *
     * <p>Submits callback processing to thread pool for async handling. Each callback updates job
     * execution status and triggers downstream actions (child jobs, alarms, etc.).
     *
     * @param callbackParamList list of callback requests from executor
     * @return success response (actual processing happens asynchronously)
     */
    public Response<String> callback(List<CallbackRequest> callbackParamList) {
        callbackThreadPool.execute(
                () ->
                        callbackParamList.stream()
                                .forEach(
                                        request -> {
                                            Response<String> result = doCallback(request);
                                            logger.debug(
                                                    ">>>>>>>>> orth callback {}: request={}, "
                                                            + "result={}",
                                                    result.isSuccess() ? "success" : "fail",
                                                    request,
                                                    result);
                                        }));

        return Response.ofSuccess();
    }

    /**
     * Processes a single callback request from executor.
     *
     * <p>Validates the callback, updates job log with execution result, and triggers completion
     * logic (downstream jobs, alarms, etc.). Guards against duplicate callbacks to prevent
     * duplicate child job triggers.
     *
     * @param callbackRequest callback parameters from executor (log ID, handle code, handle
     *     message)
     * @return success if processed, failure if log not found or duplicate callback detected
     */
    private Response<String> doCallback(CallbackRequest callbackRequest) {
        // Load and validate job log
        JobLog log =
                OrthAdminBootstrap.getInstance().getJobLogMapper().load(callbackRequest.getLogId());

        if (log == null) {
            return Response.ofFail("Job log not found for ID: " + callbackRequest.getLogId());
        }

        // Guard against duplicate callbacks to prevent duplicate child job triggers
        if (log.getHandleCode() > 0) {
            return Response.ofFail("Duplicate callback detected, ignoring");
        }

        // Build consolidated handle message
        String consolidatedMsg =
                buildConsolidatedMessage(log.getHandleMsg(), callbackRequest.getHandleMsg());

        // Update log and trigger completion logic
        log.setHandleTime(new Date());
        log.setHandleCode(callbackRequest.getHandleCode());
        log.setHandleMsg(consolidatedMsg);
        OrthAdminBootstrap.getInstance().getJobCompleter().complete(log);

        return Response.ofSuccess();
    }

    /**
     * Builds consolidated handle message by appending new message to existing message.
     *
     * @param existingMsg existing handle message from log (may be null)
     * @param newMsg new handle message from callback (may be null)
     * @return consolidated message with HTML line break separator, or empty string if both null
     */
    private String buildConsolidatedMessage(String existingMsg, String newMsg) {
        StringBuilder messageBuilder = new StringBuilder();

        Optional.ofNullable(existingMsg)
                .ifPresent(msg -> messageBuilder.append(msg).append("<br>"));

        Optional.ofNullable(newMsg).ifPresent(messageBuilder::append);

        return messageBuilder.toString();
    }
}
