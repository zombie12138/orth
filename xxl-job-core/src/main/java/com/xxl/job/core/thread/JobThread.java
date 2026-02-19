package com.xxl.job.core.thread;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.openapi.model.CallbackRequest;
import com.xxl.job.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Job execution thread that processes job triggers from a queue.
 *
 * <p>Each job gets its own dedicated thread that: 1. Initializes the handler on startup 2. Polls
 * trigger queue and executes jobs 3. Handles timeouts via FutureTask 4. Pushes execution results to
 * callback queue 5. Destroys the handler on shutdown
 *
 * <p>Thread lifecycle: init() → [execute loop] → destroy()
 */
public class JobThread extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(JobThread.class);

    // Constants
    private static final int TRIGGER_POLL_TIMEOUT_SECONDS = 3;
    private static final int IDLE_TIMES_THRESHOLD = 30;
    private static final int MAX_HANDLE_MSG_LENGTH = 50000;

    // Job metadata
    private final int jobId;
    private final IJobHandler handler;
    private final LinkedBlockingQueue<TriggerRequest> triggerQueue;
    private final Set<Long> triggerLogIdSet; // Deduplicates triggers by log ID

    // Thread state
    private volatile boolean toStop = false;
    private String stopReason;
    private boolean running = false; // Currently executing a job
    private int idleTimes = 0; // Consecutive idle poll cycles

    /**
     * Constructs a job thread for the specified job.
     *
     * @param jobId the job ID
     * @param handler the job handler to execute
     */
    public JobThread(int jobId, IJobHandler handler) {
        this.jobId = jobId;
        this.handler = handler;
        this.triggerQueue = new LinkedBlockingQueue<>();
        this.triggerLogIdSet = ConcurrentHashMap.newKeySet();

        // Assign descriptive thread name
        this.setName(String.format("orth-job-thread-%d-%d", jobId, System.currentTimeMillis()));
    }

    public IJobHandler getHandler() {
        return handler;
    }

    /**
     * Pushes a new trigger request to the execution queue.
     *
     * @param triggerParam the trigger request
     * @return success response, or failure if duplicate log ID
     */
    public Response<String> pushTriggerQueue(TriggerRequest triggerParam) {
        // Deduplicate by log ID (Set.add returns false if already present)
        if (!triggerLogIdSet.add(triggerParam.getLogId())) {
            logger.info("Duplicate trigger request ignored, logId: {}", triggerParam.getLogId());
            return Response.of(
                    XxlJobContext.HANDLE_CODE_FAIL,
                    "Duplicate trigger request, logId: " + triggerParam.getLogId());
        }

        triggerQueue.add(triggerParam);
        return Response.ofSuccess();
    }

    /**
     * Signals the thread to stop gracefully.
     *
     * <p>Note: Thread.interrupt() only interrupts blocking operations (wait/join/sleep) but doesn't
     * stop a running thread. This method uses a shared volatile flag to signal termination.
     *
     * @param stopReason reason for stopping (logged in callbacks)
     */
    public void toStop(String stopReason) {
        this.toStop = true;
        this.stopReason = stopReason;
    }

    /**
     * Checks if the thread is currently executing a job or has pending triggers.
     *
     * @return true if running or queue not empty
     */
    public boolean isRunningOrHasQueue() {
        return running || !triggerQueue.isEmpty();
    }

    @Override
    public void run() {
        // Initialize handler
        initHandler();

        // Main execution loop
        while (!toStop) {
            running = false;
            idleTimes++;

            TriggerRequest triggerParam = null;
            try {
                // Poll queue with timeout to check toStop signal periodically
                // (cannot use blocking take() as it would prevent graceful shutdown)
                triggerParam = triggerQueue.poll(TRIGGER_POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (triggerParam != null) {
                    running = true;
                    idleTimes = 0;
                    triggerLogIdSet.remove(triggerParam.getLogId());

                    // Build job context
                    XxlJobContext xxlJobContext = buildJobContext(triggerParam);
                    XxlJobContext.setXxlJobContext(xxlJobContext);

                    // Execute job
                    XxlJobHelper.log(
                            "<br>----------- Orth job execute start -----------<br>----------- Param: {}",
                            xxlJobContext.getJobParam());

                    executeJob(triggerParam, xxlJobContext);

                    // Validate and truncate result message
                    validateAndTruncateResult();

                    XxlJobHelper.log(
                            "<br>----------- Orth job execute end(finish) -----------<br>----------- Result: handleCode={}, handleMsg={}",
                            XxlJobContext.getXxlJobContext().getHandleCode(),
                            XxlJobContext.getXxlJobContext().getHandleMsg());

                } else {
                    // Auto-cleanup idle threads
                    if (idleTimes > IDLE_TIMES_THRESHOLD && triggerQueue.isEmpty()) {
                        XxlJobExecutor.removeJobThread(
                                jobId, "Executor idle timeout - no triggers received");
                    }
                }
            } catch (Throwable e) {
                if (toStop) {
                    XxlJobHelper.log("<br>----------- JobThread stopped, reason: {}", stopReason);
                }

                // Capture stack trace
                StringWriter stringWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(stringWriter));
                String errorMsg = stringWriter.toString();

                XxlJobHelper.handleFail(errorMsg);
                XxlJobHelper.log(
                        "<br>----------- JobThread Exception: {}<br>----------- Orth job execute end(error) -----------",
                        errorMsg);
            } finally {
                if (triggerParam != null) {
                    pushCallback(triggerParam);
                }
            }
        }

        // Callback remaining triggers in queue
        callbackRemainingTriggers();

        // Destroy handler
        destroyHandler();

        logger.info("Orth job thread stopped: {}", Thread.currentThread());
    }

    /** Initializes the job handler. */
    private void initHandler() {
        try {
            handler.init();
        } catch (Throwable e) {
            logger.error("Handler init failed", e);
        }
    }

    /** Builds job execution context from trigger request. */
    private XxlJobContext buildJobContext(TriggerRequest triggerParam) {
        String logFileName =
                XxlJobFileAppender.makeLogFileName(
                        new Date(triggerParam.getLogDateTime()), triggerParam.getLogId());
        return new XxlJobContext(
                triggerParam.getJobId(),
                triggerParam.getExecutorParams(),
                triggerParam.getLogId(),
                triggerParam.getLogDateTime(),
                logFileName,
                triggerParam.getBroadcastIndex(),
                triggerParam.getBroadcastTotal(),
                triggerParam.getScheduleTime());
    }

    /** Executes the job with optional timeout handling. */
    private void executeJob(TriggerRequest triggerParam, XxlJobContext xxlJobContext)
            throws Exception {
        int timeout = triggerParam.getExecutorTimeout();
        if (timeout > 0) {
            executeWithTimeout(xxlJobContext, timeout);
        } else {
            handler.execute();
        }
    }

    /** Executes the job with a timeout using FutureTask. */
    private void executeWithTimeout(XxlJobContext xxlJobContext, int timeoutSeconds)
            throws Exception {
        Thread futureThread = null;
        try {
            FutureTask<Boolean> futureTask =
                    new FutureTask<>(
                            () -> {
                                // Re-set context for the new thread
                                XxlJobContext.setXxlJobContext(xxlJobContext);
                                handler.execute();
                                return true;
                            });
            futureThread = new Thread(futureTask);
            futureThread.start();

            futureTask.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            XxlJobHelper.log("<br>----------- Orth job execute timeout");
            XxlJobHelper.log(e);
            XxlJobHelper.handleTimeout("Job execution timeout");
        } finally {
            if (futureThread != null) {
                futureThread.interrupt();
            }
        }
    }

    /** Validates result code and truncates message if too long. */
    private void validateAndTruncateResult() {
        XxlJobContext context = XxlJobContext.getXxlJobContext();
        if (context.getHandleCode() <= 0) {
            XxlJobHelper.handleFail("Job handle result lost");
        } else {
            String handleMsg = context.getHandleMsg();
            if (handleMsg != null && handleMsg.length() > MAX_HANDLE_MSG_LENGTH) {
                context.setHandleMsg(handleMsg.substring(0, MAX_HANDLE_MSG_LENGTH) + "...");
            }
        }
    }

    /** Pushes execution result callback for the trigger. */
    private void pushCallback(TriggerRequest triggerParam) {
        XxlJobContext context = XxlJobContext.getXxlJobContext();
        CallbackRequest callback;

        if (!toStop) {
            // Normal execution completed
            callback =
                    new CallbackRequest(
                            triggerParam.getLogId(),
                            triggerParam.getLogDateTime(),
                            context.getHandleCode(),
                            context.getHandleMsg());
        } else {
            // Job was killed during execution
            callback =
                    new CallbackRequest(
                            triggerParam.getLogId(),
                            triggerParam.getLogDateTime(),
                            XxlJobContext.HANDLE_CODE_FAIL,
                            stopReason + " [job running, killed]");
        }

        TriggerCallbackThread.pushCallBack(callback);
    }

    /** Callbacks remaining triggers in queue that were not executed. */
    private void callbackRemainingTriggers() {
        while (!triggerQueue.isEmpty()) {
            TriggerRequest triggerParam = triggerQueue.poll();
            if (triggerParam != null) {
                TriggerCallbackThread.pushCallBack(
                        new CallbackRequest(
                                triggerParam.getLogId(),
                                triggerParam.getLogDateTime(),
                                XxlJobContext.HANDLE_CODE_FAIL,
                                stopReason + " [job not executed, in queue, killed]"));
            }
        }
    }

    /** Destroys the job handler. */
    private void destroyHandler() {
        try {
            handler.destroy();
        } catch (Throwable e) {
            logger.error("Handler destroy failed", e);
        }
    }
}
