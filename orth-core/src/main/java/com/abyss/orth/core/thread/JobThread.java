package com.abyss.orth.core.thread;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.abyss.orth.core.context.OrthJobContext;
import com.abyss.orth.core.context.OrthJobHelper;
import com.abyss.orth.core.executor.OrthJobExecutor;
import com.abyss.orth.core.handler.IJobHandler;
import com.abyss.orth.core.log.OrthJobFileAppender;
import com.abyss.orth.core.openapi.model.CallbackRequest;
import com.abyss.orth.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Job execution thread that processes job triggers from a queue.
 *
 * <p>Each job gets its own dedicated thread that: 1. Initializes the handler on startup 2. Polls
 * trigger queue and executes jobs 3. Handles timeouts via FutureTask 4. Pushes execution results to
 * callback queue 5. Destroys the handler on shutdown
 *
 * <p>When {@code concurrency > 1}, the main thread acts as a dispatcher: it polls triggers from the
 * queue and submits them to an internal worker pool. Each worker handles its own context setup,
 * execution, and callback independently.
 *
 * <p>Thread lifecycle: init() → [execute loop] → destroy()
 */
public class JobThread extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(JobThread.class);

    // Constants
    private static final int TRIGGER_POLL_TIMEOUT_SECONDS = 3;
    private static final int IDLE_TIMES_THRESHOLD = 30;
    private static final int MAX_HANDLE_MSG_LENGTH = 50000;
    private static final int WORKER_POOL_SHUTDOWN_TIMEOUT_SECONDS = 10;

    // Job metadata
    private final int jobId;
    private final IJobHandler handler;
    private final LinkedBlockingQueue<TriggerRequest> triggerQueue;
    private final Set<Long> triggerLogIdSet; // Deduplicates triggers by log ID

    // Concurrency support
    private final int concurrency;
    private final ExecutorService workerPool; // null when concurrency == 1
    private final AtomicInteger activeCount; // tracks running workers in concurrent mode

    // Thread state
    private volatile boolean toStop = false;
    private String stopReason;
    private boolean running = false; // Currently executing a job (serial mode only)
    private int idleTimes = 0; // Consecutive idle poll cycles

    /**
     * Constructs a job thread for the specified job with serial execution (concurrency = 1).
     *
     * @param jobId the job ID
     * @param handler the job handler to execute
     */
    public JobThread(int jobId, IJobHandler handler) {
        this(jobId, handler, 1);
    }

    /**
     * Constructs a job thread for the specified job with configurable concurrency.
     *
     * @param jobId the job ID
     * @param handler the job handler to execute
     * @param concurrency concurrency level (1 = serial, >1 = concurrent via internal pool)
     */
    public JobThread(int jobId, IJobHandler handler, int concurrency) {
        this.jobId = jobId;
        this.handler = handler;
        this.concurrency = Math.max(1, concurrency);
        this.triggerQueue = new LinkedBlockingQueue<>();
        this.triggerLogIdSet = ConcurrentHashMap.newKeySet();
        this.activeCount = new AtomicInteger(0);

        if (this.concurrency > 1) {
            this.workerPool =
                    new ThreadPoolExecutor(
                            this.concurrency,
                            this.concurrency,
                            60L,
                            TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(),
                            r -> {
                                Thread t = new Thread(r);
                                t.setName(
                                        String.format(
                                                "orth-job-worker-%d-%d",
                                                jobId, System.currentTimeMillis()));
                                return t;
                            });
        } else {
            this.workerPool = null;
        }

        // Assign descriptive thread name
        this.setName(String.format("orth-job-thread-%d-%d", jobId, System.currentTimeMillis()));
    }

    public IJobHandler getHandler() {
        return handler;
    }

    public int getConcurrency() {
        return concurrency;
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
                    OrthJobContext.HANDLE_CODE_FAIL,
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
        if (concurrency > 1) {
            return activeCount.get() > 0 || !triggerQueue.isEmpty();
        }
        return running || !triggerQueue.isEmpty();
    }

    @Override
    public void run() {
        // Initialize handler
        initHandler();

        if (concurrency > 1) {
            runConcurrent();
        } else {
            runSerial();
        }

        // Shutdown worker pool if needed
        shutdownWorkerPool();

        // Callback remaining triggers in queue
        callbackRemainingTriggers();

        // Destroy handler
        destroyHandler();

        logger.info("Orth job thread stopped: {}", Thread.currentThread());
    }

    /** Serial execution loop (concurrency == 1). Unchanged from original behavior. */
    private void runSerial() {
        while (!toStop) {
            running = false;
            idleTimes++;

            TriggerRequest triggerParam = null;
            try {
                triggerParam = triggerQueue.poll(TRIGGER_POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (triggerParam != null) {
                    running = true;
                    idleTimes = 0;
                    triggerLogIdSet.remove(triggerParam.getLogId());

                    OrthJobContext orthJobContext = buildJobContext(triggerParam);
                    OrthJobContext.setOrthJobContext(orthJobContext);

                    OrthJobHelper.log(
                            "<br>----------- Orth job execute start -----------<br>----------- Param: {}",
                            orthJobContext.getJobParam());

                    executeJob(triggerParam, orthJobContext);
                    validateAndTruncateResult();

                    OrthJobHelper.log(
                            "<br>----------- Orth job execute end(finish) -----------<br>----------- Result: handleCode={}, handleMsg={}",
                            OrthJobContext.getOrthJobContext().getHandleCode(),
                            OrthJobContext.getOrthJobContext().getHandleMsg());

                } else {
                    checkIdleCleanup();
                }
            } catch (Throwable e) {
                handleExecutionError(e);
            } finally {
                if (triggerParam != null) {
                    pushCallback(triggerParam);
                }
            }
        }
    }

    /** Concurrent execution loop (concurrency > 1). Dispatches triggers to worker pool. */
    private void runConcurrent() {
        while (!toStop) {
            idleTimes++;

            try {
                TriggerRequest triggerParam =
                        triggerQueue.poll(TRIGGER_POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (triggerParam != null) {
                    idleTimes = 0;
                    triggerLogIdSet.remove(triggerParam.getLogId());
                    activeCount.incrementAndGet();

                    workerPool.execute(
                            () -> {
                                try {
                                    executeTriggerInWorker(triggerParam);
                                } finally {
                                    activeCount.decrementAndGet();
                                }
                            });
                } else {
                    // Auto-cleanup idle threads when no workers are active
                    if (idleTimes > IDLE_TIMES_THRESHOLD
                            && triggerQueue.isEmpty()
                            && activeCount.get() == 0) {
                        OrthJobExecutor.removeJobThread(
                                jobId, "Executor idle timeout - no triggers received");
                    }
                }
            } catch (Throwable e) {
                if (toStop) {
                    logger.info("JobThread stopped during dispatch, reason: {}", stopReason);
                } else {
                    logger.error("JobThread dispatch error", e);
                }
            }
        }
    }

    /** Executes a single trigger in a worker thread (concurrent mode). */
    private void executeTriggerInWorker(TriggerRequest triggerParam) {
        try {
            OrthJobContext orthJobContext = buildJobContext(triggerParam);
            OrthJobContext.setOrthJobContext(orthJobContext);

            OrthJobHelper.log(
                    "<br>----------- Orth job execute start -----------<br>----------- Param: {}",
                    orthJobContext.getJobParam());

            executeJob(triggerParam, orthJobContext);
            validateAndTruncateResult();

            OrthJobHelper.log(
                    "<br>----------- Orth job execute end(finish) -----------<br>----------- Result: handleCode={}, handleMsg={}",
                    OrthJobContext.getOrthJobContext().getHandleCode(),
                    OrthJobContext.getOrthJobContext().getHandleMsg());

        } catch (Throwable e) {
            handleExecutionError(e);
        } finally {
            pushCallback(triggerParam);
        }
    }

    /** Checks and triggers idle cleanup. */
    private void checkIdleCleanup() {
        if (idleTimes > IDLE_TIMES_THRESHOLD && triggerQueue.isEmpty()) {
            OrthJobExecutor.removeJobThread(jobId, "Executor idle timeout - no triggers received");
        }
    }

    /** Handles execution errors. */
    private void handleExecutionError(Throwable e) {
        if (toStop) {
            OrthJobHelper.log("<br>----------- JobThread stopped, reason: {}", stopReason);
        }

        StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        String errorMsg = stringWriter.toString();

        OrthJobHelper.handleFail(errorMsg);
        OrthJobHelper.log(
                "<br>----------- JobThread Exception: {}<br>----------- Orth job execute end(error) -----------",
                errorMsg);
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
    private OrthJobContext buildJobContext(TriggerRequest triggerParam) {
        String logFileName =
                OrthJobFileAppender.makeLogFileName(
                        new Date(triggerParam.getLogDateTime()), triggerParam.getLogId());
        return new OrthJobContext(
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
    private void executeJob(TriggerRequest triggerParam, OrthJobContext orthJobContext)
            throws Exception {
        int timeout = triggerParam.getExecutorTimeout();
        if (timeout > 0) {
            executeWithTimeout(orthJobContext, timeout);
        } else {
            handler.execute();
        }
    }

    /** Executes the job with a timeout using FutureTask. */
    private void executeWithTimeout(OrthJobContext orthJobContext, int timeoutSeconds)
            throws Exception {
        Thread futureThread = null;
        try {
            FutureTask<Boolean> futureTask =
                    new FutureTask<>(
                            () -> {
                                // Re-set context for the new thread
                                OrthJobContext.setOrthJobContext(orthJobContext);
                                handler.execute();
                                return true;
                            });
            futureThread = new Thread(futureTask);
            futureThread.start();

            futureTask.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            OrthJobHelper.log("<br>----------- Orth job execute timeout");
            OrthJobHelper.log(e);
            OrthJobHelper.handleTimeout("Job execution timeout");
        } finally {
            if (futureThread != null) {
                futureThread.interrupt();
            }
        }
    }

    /** Validates result code and truncates message if too long. */
    private void validateAndTruncateResult() {
        OrthJobContext context = OrthJobContext.getOrthJobContext();
        if (context.getHandleCode() <= 0) {
            OrthJobHelper.handleFail("Job handle result lost");
        } else {
            String handleMsg = context.getHandleMsg();
            if (handleMsg != null && handleMsg.length() > MAX_HANDLE_MSG_LENGTH) {
                context.setHandleMsg(handleMsg.substring(0, MAX_HANDLE_MSG_LENGTH) + "...");
            }
        }
    }

    /** Pushes execution result callback for the trigger. */
    private void pushCallback(TriggerRequest triggerParam) {
        OrthJobContext context = OrthJobContext.getOrthJobContext();
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
                            OrthJobContext.HANDLE_CODE_FAIL,
                            stopReason + " [job running, killed]");
        }

        TriggerCallbackThread.pushCallBack(callback);
    }

    /** Shuts down the worker pool if it exists. */
    private void shutdownWorkerPool() {
        if (workerPool == null) {
            return;
        }
        workerPool.shutdownNow();
        try {
            if (!workerPool.awaitTermination(
                    WORKER_POOL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn(
                        "Worker pool for job {} did not terminate within {} seconds",
                        jobId,
                        WORKER_POOL_SHUTDOWN_TIMEOUT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
                                OrthJobContext.HANDLE_CODE_FAIL,
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
