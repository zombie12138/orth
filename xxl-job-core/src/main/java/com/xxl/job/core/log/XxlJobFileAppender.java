package com.xxl.job.core.log;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.core.openapi.model.LogResult;
import com.xxl.tool.core.DateTool;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.io.FileTool;

/**
 * File-based log appender for job execution logs.
 *
 * <p>Directory structure:
 *
 * <ul>
 *   <li>{@code logBasePath/yyyy-MM-dd/logId.log} - Job execution logs
 *   <li>{@code logBasePath/gluesource/jobId_timestamp.ext} - Glue source files (scripts)
 *   <li>{@code logBasePath/callbacklogs/orth-callback-md5.log} - Failed callback persistence
 * </ul>
 */
public class XxlJobFileAppender {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobFileAppender.class);

    /** Default log base path */
    private static String logBasePath = "/data/applogs/orth/jobhandler";

    private static String glueSrcPath = logBasePath.concat(File.separator).concat("gluesource");
    private static String callbackLogPath =
            logBasePath.concat(File.separator).concat("callbacklogs");

    private XxlJobFileAppender() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Initializes log paths and creates directories.
     *
     * @param logPath custom log base path (uses default if null/empty)
     * @throws IOException if directory creation fails
     */
    public static void initLogPath(String logPath) throws IOException {
        // Use custom path if provided
        if (StringTool.isNotBlank(logPath)) {
            logBasePath = logPath.trim();
        }

        // Create base log directory
        File logPathDir = new File(logBasePath);
        FileTool.createDirectories(logPathDir);
        logBasePath = logPathDir.getPath();

        // Create glue source directory
        File glueBaseDir = new File(logPathDir, "gluesource");
        FileTool.createDirectories(glueBaseDir);
        glueSrcPath = glueBaseDir.getPath();

        // Create callback log directory
        File callbackBaseDir = new File(logPathDir, "callbacklogs");
        FileTool.createDirectories(callbackBaseDir);
        callbackLogPath = callbackBaseDir.getPath();
    }

    public static String getLogPath() {
        return logBasePath;
    }

    public static String getGlueSrcPath() {
        return glueSrcPath;
    }

    public static String getCallbackLogPath() {
        return callbackLogPath;
    }

    /**
     * Generates log file path for a job execution.
     *
     * <p>Format: {@code logPath/yyyy-MM-dd/logId.log}
     *
     * @param triggerDate trigger date (determines subdirectory)
     * @param logId log ID (becomes filename)
     * @return absolute log file path
     */
    public static String makeLogFileName(Date triggerDate, long logId) {
        // Create date-based subdirectory (yyyy-MM-dd)
        File logFilePath = new File(getLogPath(), DateTool.formatDate(triggerDate));
        try {
            FileTool.createDirectories(logFilePath);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to create log directory: " + logFilePath.getPath(), e);
        }

        // Return full path: logPath/yyyy-MM-dd/logId.log
        return String.format("%s%s%d.log", logFilePath.getPath(), File.separator, logId);
    }

    /**
     * Appends a log line to the log file.
     *
     * @param logFileName absolute log file path
     * @param appendLog log line to append
     */
    public static void appendLog(String logFileName, String appendLog) {
        if (StringTool.isBlank(logFileName) || appendLog == null) {
            return;
        }

        try {
            FileTool.writeLines(logFileName, List.of(appendLog), true);
        } catch (IOException e) {
            throw new RuntimeException("Failed to append log to file: " + logFileName, e);
        }
    }

    /**
     * Reads log file content starting from a specific line.
     *
     * <p>Used by admin console to display real-time logs. Returns all lines from {@code
     * fromLineNum} to end of file.
     *
     * @param logFileName absolute log file path
     * @param fromLineNum starting line number (1-indexed)
     * @return log result with content and line range
     */
    public static LogResult readLog(String logFileName, int fromLineNum) {
        // Validate parameters
        if (StringTool.isBlank(logFileName)) {
            return new LogResult(fromLineNum, 0, "Log file path not specified", true);
        }
        if (!FileTool.exists(logFileName)) {
            return new LogResult(fromLineNum, 0, "Log file does not exist", true);
        }

        // Read log lines
        StringBuilder logContent = new StringBuilder();
        AtomicInteger toLineNum = new AtomicInteger(0);
        AtomicInteger currentLineNum = new AtomicInteger(0);

        try {
            FileTool.readLines(
                    logFileName,
                    line -> {
                        int lineNum = currentLineNum.incrementAndGet();

                        // Skip lines before fromLineNum
                        if (lineNum < fromLineNum) {
                            return;
                        }

                        // Append line to result
                        toLineNum.set(lineNum);
                        logContent.append(line).append(System.lineSeparator());
                    });
        } catch (IOException e) {
            logger.error(
                    "Failed to read log file: {}, fromLineNum: {}", logFileName, fromLineNum, e);
        }

        return new LogResult(fromLineNum, toLineNum.get(), logContent.toString(), false);
    }
}
