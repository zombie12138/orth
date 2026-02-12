package com.xxl.job.core.openapi.impl;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.core.constant.ExecutorBlockStrategyEnum;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.glue.GlueFactory;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.impl.GlueJobHandler;
import com.xxl.job.core.handler.impl.ScriptJobHandler;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.openapi.ExecutorBiz;
import com.xxl.job.core.openapi.model.*;
import com.xxl.job.core.thread.JobThread;
import com.xxl.tool.response.Response;

/**
 * Executor RPC implementation.
 *
 * <p>Handles incoming requests from admin scheduler: job triggers, kills, health checks, and log
 * retrieval.
 */
public class ExecutorBizImpl implements ExecutorBiz {
    private static final Logger logger = LoggerFactory.getLogger(ExecutorBizImpl.class);

    @Override
    public Response<String> beat() {
        return Response.ofSuccess();
    }

    @Override
    public Response<String> idleBeat(IdleBeatRequest idleBeatRequest) {
        JobThread jobThread = XxlJobExecutor.loadJobThread(idleBeatRequest.getJobId());

        if (jobThread != null && jobThread.isRunningOrHasQueue()) {
            return Response.ofFail("Job thread is running or has queued triggers");
        }

        return Response.ofSuccess();
    }

    @Override
    public Response<String> run(TriggerRequest triggerRequest) {
        // Load existing job thread and handler
        JobThread jobThread = XxlJobExecutor.loadJobThread(triggerRequest.getJobId());
        IJobHandler jobHandler = jobThread != null ? jobThread.getHandler() : null;
        String removeOldReason = null;

        // Load or create job handler based on glue type
        HandlerLoadResult handlerResult = loadJobHandler(triggerRequest, jobThread, jobHandler);
        if (!handlerResult.isSuccess()) {
            return Response.of(XxlJobContext.HANDLE_CODE_FAIL, handlerResult.getErrorMessage());
        }

        jobHandler = handlerResult.getJobHandler();
        jobThread = handlerResult.getJobThread();
        removeOldReason = handlerResult.getRemoveOldReason();

        // Apply block strategy if thread already exists
        BlockStrategyResult blockResult =
                applyBlockStrategy(triggerRequest, jobThread, removeOldReason);
        if (!blockResult.isSuccess()) {
            return Response.of(XxlJobContext.HANDLE_CODE_FAIL, blockResult.getErrorMessage());
        }

        jobThread = blockResult.getJobThread();
        removeOldReason = blockResult.getRemoveOldReason();

        // Create new thread if needed
        if (jobThread == null) {
            jobThread =
                    XxlJobExecutor.registJobThread(
                            triggerRequest.getJobId(), jobHandler, removeOldReason);
        }

        // Queue trigger for execution
        return jobThread.pushTriggerQueue(triggerRequest);
    }

    /** Loads or creates job handler based on glue type. */
    private HandlerLoadResult loadJobHandler(
            TriggerRequest triggerRequest, JobThread jobThread, IJobHandler jobHandler) {
        GlueTypeEnum glueTypeEnum = GlueTypeEnum.match(triggerRequest.getGlueType());
        String removeOldReason = null;

        if (GlueTypeEnum.BEAN == glueTypeEnum) {
            return loadBeanHandler(triggerRequest, jobThread, jobHandler);
        } else if (GlueTypeEnum.GLUE_GROOVY == glueTypeEnum) {
            return loadGroovyHandler(triggerRequest, jobThread, jobHandler);
        } else if (glueTypeEnum != null && glueTypeEnum.isScript()) {
            return loadScriptHandler(triggerRequest, jobThread, jobHandler);
        } else {
            return HandlerLoadResult.error("Invalid glue type: " + triggerRequest.getGlueType());
        }
    }

    /** Loads Bean-based job handler. */
    private HandlerLoadResult loadBeanHandler(
            TriggerRequest triggerRequest, JobThread jobThread, IJobHandler jobHandler) {
        IJobHandler newJobHandler =
                XxlJobExecutor.loadJobHandler(triggerRequest.getExecutorHandler());

        // Check if handler changed (need to kill old thread)
        if (jobThread != null && jobHandler != newJobHandler) {
            return HandlerLoadResult.success(
                    newJobHandler, null, "Handler changed - terminating old job thread");
        }

        // Use new handler if current is null
        if (jobHandler == null) {
            jobHandler = newJobHandler;
            if (jobHandler == null) {
                return HandlerLoadResult.error(
                        "Job handler not found: " + triggerRequest.getExecutorHandler());
            }
        }

        return HandlerLoadResult.success(jobHandler, jobThread, null);
    }

    /** Loads Groovy-based job handler. */
    private HandlerLoadResult loadGroovyHandler(
            TriggerRequest triggerRequest, JobThread jobThread, IJobHandler jobHandler) {
        // Check if glue source updated (need to kill old thread)
        if (jobThread != null && !isGroovyHandlerCurrent(jobThread, triggerRequest)) {
            jobThread = null;
            jobHandler = null;
        }

        // Compile new Groovy handler if needed
        if (jobHandler == null) {
            try {
                IJobHandler originJobHandler =
                        GlueFactory.getInstance().loadNewInstance(triggerRequest.getGlueSource());
                jobHandler =
                        new GlueJobHandler(originJobHandler, triggerRequest.getGlueUpdatetime());
            } catch (Exception e) {
                logger.error("Groovy compilation failed", e);
                return HandlerLoadResult.error(e.getMessage());
            }
        }

        return HandlerLoadResult.success(
                jobHandler,
                jobThread,
                jobThread == null ? "Glue source updated - terminating old job thread" : null);
    }

    /** Loads Script-based job handler. */
    private HandlerLoadResult loadScriptHandler(
            TriggerRequest triggerRequest, JobThread jobThread, IJobHandler jobHandler) {
        // Check if script updated (need to kill old thread)
        if (jobThread != null && !isScriptHandlerCurrent(jobThread, triggerRequest)) {
            jobThread = null;
            jobHandler = null;
        }

        // Create new script handler if needed
        if (jobHandler == null) {
            jobHandler =
                    new ScriptJobHandler(
                            triggerRequest.getJobId(),
                            triggerRequest.getGlueUpdatetime(),
                            triggerRequest.getGlueSource(),
                            GlueTypeEnum.match(triggerRequest.getGlueType()));
        }

        return HandlerLoadResult.success(
                jobHandler,
                jobThread,
                jobThread == null ? "Script updated - terminating old job thread" : null);
    }

    /** Checks if Groovy handler is current (not updated). */
    private boolean isGroovyHandlerCurrent(JobThread jobThread, TriggerRequest triggerRequest) {
        return jobThread.getHandler() instanceof GlueJobHandler
                && ((GlueJobHandler) jobThread.getHandler()).getGlueUpdatetime()
                        == triggerRequest.getGlueUpdatetime();
    }

    /** Checks if Script handler is current (not updated). */
    private boolean isScriptHandlerCurrent(JobThread jobThread, TriggerRequest triggerRequest) {
        return jobThread.getHandler() instanceof ScriptJobHandler
                && ((ScriptJobHandler) jobThread.getHandler()).getGlueUpdatetime()
                        == triggerRequest.getGlueUpdatetime();
    }

    /**
     * Applies block strategy to determine if trigger should be queued, discarded, or replace
     * existing.
     */
    private BlockStrategyResult applyBlockStrategy(
            TriggerRequest triggerRequest, JobThread jobThread, String removeOldReason) {
        if (jobThread == null) {
            return BlockStrategyResult.success(null, removeOldReason);
        }

        ExecutorBlockStrategyEnum blockStrategy =
                ExecutorBlockStrategyEnum.match(triggerRequest.getExecutorBlockStrategy(), null);

        if (ExecutorBlockStrategyEnum.DISCARD_LATER == blockStrategy) {
            // Discard new trigger if job is running
            if (jobThread.isRunningOrHasQueue()) {
                return BlockStrategyResult.error(
                        "Block strategy effect: "
                                + ExecutorBlockStrategyEnum.DISCARD_LATER.getTitle());
            }
        } else if (ExecutorBlockStrategyEnum.COVER_EARLY == blockStrategy) {
            // Kill running job and replace with new trigger
            if (jobThread.isRunningOrHasQueue()) {
                return BlockStrategyResult.success(
                        null,
                        "Block strategy effect: "
                                + ExecutorBlockStrategyEnum.COVER_EARLY.getTitle());
            }
        }
        // SERIAL_EXECUTION (default): just queue the trigger

        return BlockStrategyResult.success(jobThread, removeOldReason);
    }

    /** Result of handler loading operation. */
    private static class HandlerLoadResult {
        private final boolean success;
        private final IJobHandler jobHandler;
        private final JobThread jobThread;
        private final String removeOldReason;
        private final String errorMessage;

        private HandlerLoadResult(
                boolean success,
                IJobHandler jobHandler,
                JobThread jobThread,
                String removeOldReason,
                String errorMessage) {
            this.success = success;
            this.jobHandler = jobHandler;
            this.jobThread = jobThread;
            this.removeOldReason = removeOldReason;
            this.errorMessage = errorMessage;
        }

        static HandlerLoadResult success(
                IJobHandler jobHandler, JobThread jobThread, String removeOldReason) {
            return new HandlerLoadResult(true, jobHandler, jobThread, removeOldReason, null);
        }

        static HandlerLoadResult error(String errorMessage) {
            return new HandlerLoadResult(false, null, null, null, errorMessage);
        }

        boolean isSuccess() {
            return success;
        }

        IJobHandler getJobHandler() {
            return jobHandler;
        }

        JobThread getJobThread() {
            return jobThread;
        }

        String getRemoveOldReason() {
            return removeOldReason;
        }

        String getErrorMessage() {
            return errorMessage;
        }
    }

    /** Result of block strategy application. */
    private static class BlockStrategyResult {
        private final boolean success;
        private final JobThread jobThread;
        private final String removeOldReason;
        private final String errorMessage;

        private BlockStrategyResult(
                boolean success, JobThread jobThread, String removeOldReason, String errorMessage) {
            this.success = success;
            this.jobThread = jobThread;
            this.removeOldReason = removeOldReason;
            this.errorMessage = errorMessage;
        }

        static BlockStrategyResult success(JobThread jobThread, String removeOldReason) {
            return new BlockStrategyResult(true, jobThread, removeOldReason, null);
        }

        static BlockStrategyResult error(String errorMessage) {
            return new BlockStrategyResult(false, null, null, errorMessage);
        }

        boolean isSuccess() {
            return success;
        }

        JobThread getJobThread() {
            return jobThread;
        }

        String getRemoveOldReason() {
            return removeOldReason;
        }

        String getErrorMessage() {
            return errorMessage;
        }
    }

    @Override
    public Response<String> kill(KillRequest killRequest) {
        JobThread jobThread = XxlJobExecutor.loadJobThread(killRequest.getJobId());

        if (jobThread != null) {
            XxlJobExecutor.removeJobThread(killRequest.getJobId(), "Admin scheduler kill request");
            return Response.ofSuccess("Job killed successfully");
        }

        return Response.ofSuccess("Job thread not running (already killed or never started)");
    }

    @Override
    public Response<LogResult> log(LogRequest logRequest) {
        // Build log file path: logPath/yyyy-MM-dd/9999.log
        String logFileName =
                XxlJobFileAppender.makeLogFileName(
                        new Date(logRequest.getLogDateTim()), logRequest.getLogId());

        // Read log content starting from specified line
        LogResult logResult = XxlJobFileAppender.readLog(logFileName, logRequest.getFromLineNum());
        return Response.ofSuccess(logResult);
    }
}
