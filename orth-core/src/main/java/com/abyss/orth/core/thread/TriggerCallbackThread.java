package com.abyss.orth.core.thread;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.abyss.orth.core.constant.Const;
import com.abyss.orth.core.context.OrthJobContext;
import com.abyss.orth.core.context.OrthJobHelper;
import com.abyss.orth.core.executor.OrthJobExecutor;
import com.abyss.orth.core.log.OrthJobFileAppender;
import com.abyss.orth.core.openapi.AdminBiz;
import com.abyss.orth.core.openapi.model.CallbackRequest;
import com.xxl.tool.core.ArrayTool;
import com.xxl.tool.core.CollectionTool;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.encrypt.Md5Tool;
import com.xxl.tool.gson.GsonTool;
import com.xxl.tool.io.FileTool;
import com.xxl.tool.response.Response;

/**
 * Background thread for sending job execution callbacks to admin.
 *
 * <p>Two threads run concurrently: 1. **Main callback thread**: Processes callback queue, batches
 * callbacks, and sends to admin 2. **Retry thread**: Retries failed callbacks from persisted files
 * every 30 seconds
 *
 * <p>Failed callbacks are written to disk and retried until successful.
 */
public class TriggerCallbackThread {
    private static final Logger logger = LoggerFactory.getLogger(TriggerCallbackThread.class);
    private static final TriggerCallbackThread instance = new TriggerCallbackThread();

    /** Fail callback file name pattern (MD5 hash used for uniqueness) */
    private static final String FAIL_CALLBACK_FILE_NAME =
            OrthJobFileAppender.getCallbackLogPath()
                    .concat(File.separator)
                    .concat("orth-callback-{x}")
                    .concat(".log");

    public static TriggerCallbackThread getInstance() {
        return instance;
    }

    /** Callback queue for job execution results */
    private final LinkedBlockingQueue<CallbackRequest> callBackQueue = new LinkedBlockingQueue<>();

    /**
     * Pushes a callback request to the queue.
     *
     * @param callback the callback request
     */
    public static void pushCallBack(CallbackRequest callback) {
        getInstance().callBackQueue.add(callback);
        logger.debug("Pushed callback request to queue, logId: {}", callback.getLogId());
    }

    // Thread instances
    private Thread triggerCallbackThread;
    private ScheduledExecutorService retryScheduler;
    private volatile boolean toStop = false;

    /** Starts the callback thread and retry scheduler. */
    public void start() {
        // Validate admin addresses configured
        if (OrthJobExecutor.getAdminBizList() == null) {
            logger.warn("Callback thread not started: admin addresses not configured");
            return;
        }

        // Start main callback thread (blocking queue consumer — stays as bare thread)
        triggerCallbackThread = new Thread(this::runCallbackLoop);
        triggerCallbackThread.setDaemon(true);
        triggerCallbackThread.setName("orth-callback-thread");
        triggerCallbackThread.start();

        // Start retry scheduler
        retryScheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "orth-callback-retry-thread");
                            t.setDaemon(true);
                            return t;
                        });
        retryScheduler.scheduleWithFixedDelay(
                safeRunnable("callback-retry", this::retryFailCallbackFile),
                Const.BEAT_TIMEOUT,
                Const.BEAT_TIMEOUT,
                TimeUnit.SECONDS);
    }

    /** Main callback loop: processes callback queue and sends to admin. */
    private void runCallbackLoop() {
        while (!toStop) {
            try {
                // Wait for callback (blocking)
                CallbackRequest callback = callBackQueue.take();

                // Batch all available callbacks
                List<CallbackRequest> callbackBatch = new ArrayList<>();
                callbackBatch.add(callback);
                callBackQueue.drainTo(callbackBatch);

                // Send batch to admin (will retry on failure)
                doCallback(callbackBatch);
            } catch (Throwable e) {
                if (!toStop) {
                    logger.error("Callback loop error", e);
                }
            }
        }

        // Final callback on shutdown
        try {
            List<CallbackRequest> remainingCallbacks = new ArrayList<>();
            callBackQueue.drainTo(remainingCallbacks);
            if (!remainingCallbacks.isEmpty()) {
                doCallback(remainingCallbacks);
            }
        } catch (Throwable e) {
            if (!toStop) {
                logger.error("Final callback error", e);
            }
        }

        logger.info("Orth callback thread stopped");
    }

    /** Stops the callback thread and retry scheduler gracefully. */
    public void toStop() {
        toStop = true;

        // Stop callback thread
        if (triggerCallbackThread != null) {
            triggerCallbackThread.interrupt();
            try {
                triggerCallbackThread.join();
            } catch (InterruptedException e) {
                logger.error("Callback thread join interrupted", e);
                Thread.currentThread().interrupt();
            }
        }

        // Stop retry scheduler
        if (retryScheduler != null) {
            retryScheduler.shutdown();
            try {
                if (!retryScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    retryScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                retryScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("Orth callback retry thread stopped");
        }
    }

    /**
     * Sends callbacks to admin, retries on failure, persists if all admins fail.
     *
     * @param callbackList callbacks to send
     */
    private void doCallback(List<CallbackRequest> callbackList) {
        boolean success = false;

        // Try all admin endpoints until one succeeds
        for (AdminBiz adminBiz : OrthJobExecutor.getAdminBizList()) {
            try {
                Response<String> response = adminBiz.callback(callbackList);
                if (response != null && response.isSuccess()) {
                    callbackLog(callbackList, "<br>----------- Orth callback success");
                    success = true;
                    break;
                } else {
                    callbackLog(
                            callbackList,
                            "<br>----------- Orth callback failed, response: " + response);
                }
            } catch (Throwable e) {
                callbackLog(callbackList, "<br>----------- Orth callback error: " + e.getMessage());
            }
        }

        // Persist failed callbacks to disk for retry
        if (!success) {
            appendFailCallbackFile(callbackList);
        }
    }

    /**
     * Writes callback log messages to job log files.
     *
     * @param callbackList callbacks to log
     * @param logContent log message
     */
    private void callbackLog(List<CallbackRequest> callbackList, String logContent) {
        for (CallbackRequest callback : callbackList) {
            String logFileName =
                    OrthJobFileAppender.makeLogFileName(
                            new Date(callback.getLogDateTim()), callback.getLogId());
            OrthJobContext.setOrthJobContext(
                    new OrthJobContext(-1, null, -1, -1, logFileName, -1, -1, null));
            OrthJobHelper.log(logContent);
        }
    }

    // ---------------------- Failed Callback Persistence ----------------------

    /**
     * Persists failed callbacks to disk for retry.
     *
     * @param callbackList callbacks that failed
     */
    private void appendFailCallbackFile(List<CallbackRequest> callbackList) {
        if (CollectionTool.isEmpty(callbackList)) {
            return;
        }

        // Serialize callbacks to JSON
        String callbackData = GsonTool.toJson(callbackList);
        String callbackDataMd5 = Md5Tool.md5(callbackData);

        // Generate unique file name using MD5 hash
        String fileName = FAIL_CALLBACK_FILE_NAME.replace("{x}", callbackDataMd5);

        // Write to disk
        try {
            FileTool.writeString(fileName, callbackData);
        } catch (IOException e) {
            logger.error("Failed to persist callback to file: {}", fileName, e);
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

    /** Retries failed callbacks from persisted files. */
    private void retryFailCallbackFile() {
        File callbackLogPath = new File(OrthJobFileAppender.getCallbackLogPath());

        // Validate callback log directory
        if (!callbackLogPath.exists()) {
            return;
        }
        if (!FileTool.isDirectory(callbackLogPath)) {
            FileTool.delete(callbackLogPath);
            return;
        }

        File[] files = callbackLogPath.listFiles();
        if (ArrayTool.isEmpty(files)) {
            return;
        }

        // Retry each failed callback file
        for (File file : files) {
            try {
                // Load callback data
                String callbackData = FileTool.readString(file.getPath());
                if (StringTool.isBlank(callbackData)) {
                    FileTool.delete(file);
                    continue;
                }

                // Parse callbacks
                List<CallbackRequest> callbackList =
                        GsonTool.fromJsonList(callbackData, CallbackRequest.class);

                // Delete file before retry (avoids duplicate retry if successful)
                FileTool.delete(file);

                // Retry callback (will re-persist if still fails)
                doCallback(callbackList);
            } catch (IOException e) {
                logger.error("Failed to retry callback from file: {}", file.getPath(), e);
            }
        }
    }
}
