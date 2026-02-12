package com.xxl.job.core.context;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.tool.core.DateTool;

/**
 * Helper utilities for accessing Orth job execution context.
 *
 * <p>Provides convenient static methods to retrieve job metadata, log messages, and set job
 * execution results. All methods safely handle cases where no job context is available.
 */
public class XxlJobHelper {

    private static final Logger logger = LoggerFactory.getLogger("orth-job-logger");

    // ---------------------- Helper Methods ----------------------

    /**
     * Gets the current job context as an Optional.
     *
     * @return Optional containing context, or empty if not in job execution
     */
    private static Optional<XxlJobContext> getContext() {
        return Optional.ofNullable(XxlJobContext.getXxlJobContext());
    }

    // ---------------------- Job Info ----------------------

    /**
     * Gets the current job ID.
     *
     * @return job ID, or -1 if not in job execution context
     */
    public static long getJobId() {
        return getContext().map(XxlJobContext::getJobId).orElse(-1L);
    }

    /**
     * Gets the current job parameters.
     *
     * @return job parameters, or null if not in job execution context
     */
    public static String getJobParam() {
        return getContext().map(XxlJobContext::getJobParam).orElse(null);
    }

    // ---------------------- Log Info ----------------------

    /**
     * Gets the current job log ID.
     *
     * @return log ID, or -1 if not in job execution context
     */
    public static long getLogId() {
        return getContext().map(XxlJobContext::getLogId).orElse(-1L);
    }

    /**
     * Gets the current job log timestamp.
     *
     * @return log timestamp (milliseconds), or -1 if not in job execution context
     */
    public static long getLogDateTime() {
        return getContext().map(XxlJobContext::getLogDateTime).orElse(-1L);
    }

    /**
     * Gets the current job log file name.
     *
     * @return log file name, or null if not in job execution context
     */
    public static String getLogFileName() {
        return getContext().map(XxlJobContext::getLogFileName).orElse(null);
    }

    // ---------------------- Shard Info ----------------------

    /**
     * Gets the current shard index for broadcast/sharding jobs.
     *
     * @return shard index, or -1 if not in job execution context
     */
    public static int getShardIndex() {
        return getContext().map(XxlJobContext::getShardIndex).orElse(-1);
    }

    /**
     * Gets the total shard count for broadcast/sharding jobs.
     *
     * @return total shard count, or -1 if not in job execution context
     */
    public static int getShardTotal() {
        return getContext().map(XxlJobContext::getShardTotal).orElse(-1);
    }

    // ---------------------- Schedule Info ----------------------

    /**
     * Gets the theoretical schedule time (milliseconds) for CRON/FIX_RATE triggers.
     *
     * @return schedule time in milliseconds, or null for manual/API triggers or if not in job
     *     context
     */
    public static Long getScheduleTime() {
        return getContext().map(XxlJobContext::getScheduleTime).orElse(null);
    }

    // ---------------------- SuperTask Info ----------------------

    /**
     * Gets the super parameter for SubTasks.
     *
     * @return super parameter, or null for standalone/SuperTask jobs or if not in job context
     */
    public static String getSuperTaskParam() {
        return getContext().map(XxlJobContext::getSuperTaskParam).orElse(null);
    }

    // ---------------------- Logging ----------------------

    /**
     * Appends a log message with SLF4J-style pattern and arguments.
     *
     * <p>Example: {@code log("Processing {} records with status {}", 100, "SUCCESS")}
     *
     * @param appendLogPattern log message pattern (e.g., "aaa {} bbb {} ccc")
     * @param appendLogArguments pattern arguments
     * @return true if log was written to file, false if only logged to console
     */
    public static boolean log(String appendLogPattern, Object... appendLogArguments) {
        FormattingTuple ft = MessageFormatter.arrayFormat(appendLogPattern, appendLogArguments);
        String appendLog = ft.getMessage();

        StackTraceElement callInfo = new Throwable().getStackTrace()[1];
        return logDetail(callInfo, appendLog);
    }

    /**
     * Appends an exception stack trace to the job log.
     *
     * @param e exception to log
     * @return true if log was written to file, false if only logged to console
     */
    public static boolean log(Throwable e) {
        StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        String appendLog = stringWriter.toString();

        StackTraceElement callInfo = new Throwable().getStackTrace()[1];
        return logDetail(callInfo, appendLog);
    }

    /**
     * Internal method to append detailed log with source location and thread info.
     *
     * <p>Format: {@code yyyy-MM-dd HH:mm:ss [ClassName#MethodName]-[LineNumber]-[ThreadName] log}
     *
     * @param callInfo stack trace element of the caller
     * @param appendLog log message to append
     * @return true if log was written to file, false if only logged to console
     */
    private static boolean logDetail(StackTraceElement callInfo, String appendLog) {
        XxlJobContext xxlJobContext = XxlJobContext.getXxlJobContext();
        if (xxlJobContext == null) {
            return false;
        }

        String formatAppendLog =
                String.format(
                        "%s [%s#%s]-[%d]-[%s] %s",
                        DateTool.formatDateTime(new Date()),
                        callInfo.getClassName(),
                        callInfo.getMethodName(),
                        callInfo.getLineNumber(),
                        Thread.currentThread().getName(),
                        appendLog != null ? appendLog : "");

        String logFileName = xxlJobContext.getLogFileName();

        if (logFileName != null && !logFileName.trim().isEmpty()) {
            XxlJobFileAppender.appendLog(logFileName, formatAppendLog);
            return true;
        } else {
            logger.info(">>>>>>>>>>> {}", formatAppendLog);
            return false;
        }
    }

    // ---------------------- Job Result Handling ----------------------

    /**
     * Marks the job execution as successful (status code 200).
     *
     * @return true if status was set, false if not in job execution context
     */
    public static boolean handleSuccess() {
        return handleResult(XxlJobContext.HANDLE_CODE_SUCCESS, null);
    }

    /**
     * Marks the job execution as successful with a custom message.
     *
     * @param handleMsg result message to record
     * @return true if status was set, false if not in job execution context
     */
    public static boolean handleSuccess(String handleMsg) {
        return handleResult(XxlJobContext.HANDLE_CODE_SUCCESS, handleMsg);
    }

    /**
     * Marks the job execution as failed (status code 500).
     *
     * @return true if status was set, false if not in job execution context
     */
    public static boolean handleFail() {
        return handleResult(XxlJobContext.HANDLE_CODE_FAIL, null);
    }

    /**
     * Marks the job execution as failed with a custom message.
     *
     * @param handleMsg failure message to record
     * @return true if status was set, false if not in job execution context
     */
    public static boolean handleFail(String handleMsg) {
        return handleResult(XxlJobContext.HANDLE_CODE_FAIL, handleMsg);
    }

    /**
     * Marks the job execution as timed out (status code 502).
     *
     * @return true if status was set, false if not in job execution context
     */
    public static boolean handleTimeout() {
        return handleResult(XxlJobContext.HANDLE_CODE_TIMEOUT, null);
    }

    /**
     * Marks the job execution as timed out with a custom message.
     *
     * @param handleMsg timeout message to record
     * @return true if status was set, false if not in job execution context
     */
    public static boolean handleTimeout(String handleMsg) {
        return handleResult(XxlJobContext.HANDLE_CODE_TIMEOUT, handleMsg);
    }

    /**
     * Sets the job execution result with a custom status code and message.
     *
     * @param handleCode result status code (200: success, 500: fail, 502: timeout)
     * @param handleMsg result message (optional)
     * @return true if status was set, false if not in job execution context
     */
    public static boolean handleResult(int handleCode, String handleMsg) {
        XxlJobContext xxlJobContext = XxlJobContext.getXxlJobContext();
        if (xxlJobContext == null) {
            return false;
        }

        xxlJobContext.setHandleCode(handleCode);
        if (handleMsg != null) {
            xxlJobContext.setHandleMsg(handleMsg);
        }
        return true;
    }

    private XxlJobHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
