package com.abyss.orth.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.abyss.orth.core.log.OrthJobFileAppender;

/**
 * Tests for {@link OrthJobHelper}.
 *
 * <p>Covers: job info retrieval, log operations, shard info, handle result methods, context
 * management.
 */
class OrthJobHelperTest {

    @TempDir Path tempDir;

    private String originalLogPath;

    @BeforeEach
    void setUp() throws IOException {
        // Save and set log path
        originalLogPath = OrthJobFileAppender.getLogPath();
        OrthJobFileAppender.initLogPath(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clear context
        OrthJobContext.setOrthJobContext(null);

        // Restore log path
        if (originalLogPath != null) {
            OrthJobFileAppender.initLogPath(originalLogPath);
        }
    }

    // ==================== Job Info Tests ====================

    @Test
    void testGetJobId_withContext_shouldReturnJobId() {
        // Given
        OrthJobContext context = new OrthJobContext(123, null, 0, 0, null, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        long jobId = OrthJobHelper.getJobId();

        // Then
        assertThat(jobId).isEqualTo(123);
    }

    @Test
    void testGetJobId_withoutContext_shouldReturnNegativeOne() {
        // When - no context set
        long jobId = OrthJobHelper.getJobId();

        // Then
        assertThat(jobId).isEqualTo(-1);
    }

    @Test
    void testGetJobParam_withContext_shouldReturnParam() {
        // Given
        OrthJobContext context = new OrthJobContext(1, "testParam", 0, 0, null, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        String param = OrthJobHelper.getJobParam();

        // Then
        assertThat(param).isEqualTo("testParam");
    }

    @Test
    void testGetJobParam_withoutContext_shouldReturnNull() {
        // When
        String param = OrthJobHelper.getJobParam();

        // Then
        assertThat(param).isNull();
    }

    // ==================== Log Info Tests ====================

    @Test
    void testGetLogId_withContext_shouldReturnLogId() {
        // Given
        OrthJobContext context = new OrthJobContext(1, null, 456L, 0, null, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        long logId = OrthJobHelper.getLogId();

        // Then
        assertThat(logId).isEqualTo(456L);
    }

    @Test
    void testGetLogId_withoutContext_shouldReturnNegativeOne() {
        // When
        long logId = OrthJobHelper.getLogId();

        // Then
        assertThat(logId).isEqualTo(-1);
    }

    @Test
    void testGetLogDateTime_withContext_shouldReturnDateTime() {
        // Given
        OrthJobContext context = new OrthJobContext(1, null, 0, 1234567890L, null, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        long logDateTime = OrthJobHelper.getLogDateTime();

        // Then
        assertThat(logDateTime).isEqualTo(1234567890L);
    }

    @Test
    void testGetLogDateTime_withoutContext_shouldReturnNegativeOne() {
        // When
        long logDateTime = OrthJobHelper.getLogDateTime();

        // Then
        assertThat(logDateTime).isEqualTo(-1);
    }

    @Test
    void testGetLogFileName_withContext_shouldReturnFileName() {
        // Given
        String logFileName = "2024-01-15/123.log";
        OrthJobContext context = new OrthJobContext(1, null, 0, 0, logFileName, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        String fileName = OrthJobHelper.getLogFileName();

        // Then
        assertThat(fileName).isEqualTo(logFileName);
    }

    @Test
    void testGetLogFileName_withoutContext_shouldReturnNull() {
        // When
        String fileName = OrthJobHelper.getLogFileName();

        // Then
        assertThat(fileName).isNull();
    }

    // ==================== Shard Info Tests ====================

    @Test
    void testGetShardIndex_withContext_shouldReturnIndex() {
        // Given
        OrthJobContext context = new OrthJobContext(1, null, 0, 0, null, 2, 5, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        int shardIndex = OrthJobHelper.getShardIndex();

        // Then
        assertThat(shardIndex).isEqualTo(2);
    }

    @Test
    void testGetShardIndex_withoutContext_shouldReturnNegativeOne() {
        // When
        int shardIndex = OrthJobHelper.getShardIndex();

        // Then
        assertThat(shardIndex).isEqualTo(-1);
    }

    @Test
    void testGetShardTotal_withContext_shouldReturnTotal() {
        // Given
        OrthJobContext context = new OrthJobContext(1, null, 0, 0, null, 2, 5, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        int shardTotal = OrthJobHelper.getShardTotal();

        // Then
        assertThat(shardTotal).isEqualTo(5);
    }

    @Test
    void testGetShardTotal_withoutContext_shouldReturnNegativeOne() {
        // When
        int shardTotal = OrthJobHelper.getShardTotal();

        // Then
        assertThat(shardTotal).isEqualTo(-1);
    }

    // ==================== Schedule Time Tests ====================

    @Test
    void testGetScheduleTime_withContext_shouldReturnTime() {
        // Given
        long scheduleTime = System.currentTimeMillis();
        OrthJobContext context = new OrthJobContext(1, null, 0, 0, null, 0, 0, scheduleTime);
        OrthJobContext.setOrthJobContext(context);

        // When
        Long retrievedTime = OrthJobHelper.getScheduleTime();

        // Then
        assertThat(retrievedTime).isEqualTo(scheduleTime);
    }

    @Test
    void testGetScheduleTime_withoutContext_shouldReturnNull() {
        // When
        Long scheduleTime = OrthJobHelper.getScheduleTime();

        // Then
        assertThat(scheduleTime).isNull();
    }

    @Test
    void testGetScheduleTime_withNullScheduleTime_shouldReturnNull() {
        // Given
        OrthJobContext context = new OrthJobContext(1, null, 0, 0, null, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        Long scheduleTime = OrthJobHelper.getScheduleTime();

        // Then
        assertThat(scheduleTime).isNull();
    }

    // ==================== Log Methods Tests ====================

    @Test
    void testLog_withPattern_shouldReturnTrue() throws IOException {
        // Given
        Path logDir = tempDir.resolve("2024-01-15");
        Files.createDirectories(logDir);
        Path logFile = logDir.resolve("123.log");
        String logFileName = logFile.toString();

        OrthJobContext context = new OrthJobContext(123, null, 0, 0, logFileName, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        boolean result = OrthJobHelper.log("Test message: {} and {}", "value1", 42);

        // Then
        assertThat(result).isTrue();

        // Verify log file was created
        assertThat(logFile).exists();

        String logContent = Files.readString(logFile);
        assertThat(logContent).contains("Test message: value1 and 42");
    }

    @Test
    void testLog_withException_shouldReturnTrue() throws IOException {
        // Given
        Path logDir = tempDir.resolve("2024-01-15");
        Files.createDirectories(logDir);
        Path logFile = logDir.resolve("123.log");
        String logFileName = logFile.toString();

        OrthJobContext context = new OrthJobContext(123, null, 0, 0, logFileName, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        Exception exception = new RuntimeException("Test exception");

        // When
        boolean result = OrthJobHelper.log(exception);

        // Then
        assertThat(result).isTrue();

        // Verify exception stack trace in log
        assertThat(logFile).exists();

        String logContent = Files.readString(logFile);
        assertThat(logContent).contains("RuntimeException");
        assertThat(logContent).contains("Test exception");
    }

    @Test
    void testLog_withoutContext_shouldReturnFalse() {
        // When - no context
        boolean result = OrthJobHelper.log("Test message");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testLog_withEmptyLogFileName_shouldReturnFalse() {
        // Given
        OrthJobContext context = new OrthJobContext(123, null, 0, 0, "", 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        boolean result = OrthJobHelper.log("Test message");

        // Then
        assertThat(result).isFalse();
    }

    // ==================== Handle Result Tests ====================

    @Test
    void testHandleSuccess_shouldSetSuccessCode() {
        // Given
        OrthJobContext context = new OrthJobContext(1, null, 0, 0, null, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        boolean result = OrthJobHelper.handleSuccess();

        // Then
        assertThat(result).isTrue();
        assertThat(context.getHandleCode()).isEqualTo(OrthJobContext.HANDLE_CODE_SUCCESS);
    }

    @Test
    void testHandleSuccess_withMessage_shouldSetSuccessCodeAndMessage() {
        // Given
        OrthJobContext context = new OrthJobContext(1, null, 0, 0, null, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        boolean result = OrthJobHelper.handleSuccess("Operation completed successfully");

        // Then
        assertThat(result).isTrue();
        assertThat(context.getHandleCode()).isEqualTo(OrthJobContext.HANDLE_CODE_SUCCESS);
        assertThat(context.getHandleMsg()).isEqualTo("Operation completed successfully");
    }

    @Test
    void testHandleFail_shouldSetFailCode() {
        // Given
        OrthJobContext context = new OrthJobContext(1, null, 0, 0, null, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        boolean result = OrthJobHelper.handleFail();

        // Then
        assertThat(result).isTrue();
        assertThat(context.getHandleCode()).isEqualTo(OrthJobContext.HANDLE_CODE_FAIL);
    }

    @Test
    void testHandleFail_withMessage_shouldSetFailCodeAndMessage() {
        // Given
        OrthJobContext context = new OrthJobContext(1, null, 0, 0, null, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        boolean result = OrthJobHelper.handleFail("Operation failed due to error");

        // Then
        assertThat(result).isTrue();
        assertThat(context.getHandleCode()).isEqualTo(OrthJobContext.HANDLE_CODE_FAIL);
        assertThat(context.getHandleMsg()).isEqualTo("Operation failed due to error");
    }

    @Test
    void testHandleTimeout_shouldSetTimeoutCode() {
        // Given
        OrthJobContext context = new OrthJobContext(1, null, 0, 0, null, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        boolean result = OrthJobHelper.handleTimeout();

        // Then
        assertThat(result).isTrue();
        assertThat(context.getHandleCode()).isEqualTo(OrthJobContext.HANDLE_CODE_TIMEOUT);
    }

    @Test
    void testHandleTimeout_withMessage_shouldSetTimeoutCodeAndMessage() {
        // Given
        OrthJobContext context = new OrthJobContext(1, null, 0, 0, null, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        boolean result = OrthJobHelper.handleTimeout("Operation timed out after 30s");

        // Then
        assertThat(result).isTrue();
        assertThat(context.getHandleCode()).isEqualTo(OrthJobContext.HANDLE_CODE_TIMEOUT);
        assertThat(context.getHandleMsg()).isEqualTo("Operation timed out after 30s");
    }

    @Test
    void testHandleResult_withCustomCode_shouldSetCode() {
        // Given
        OrthJobContext context = new OrthJobContext(1, null, 0, 0, null, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);

        // When
        boolean result = OrthJobHelper.handleResult(999, "Custom result");

        // Then
        assertThat(result).isTrue();
        assertThat(context.getHandleCode()).isEqualTo(999);
        assertThat(context.getHandleMsg()).isEqualTo("Custom result");
    }

    @Test
    void testHandleResult_withoutContext_shouldReturnFalse() {
        // When - no context
        boolean result = OrthJobHelper.handleSuccess();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testHandleResult_withNullMessage_shouldNotSetMessage() {
        // Given
        OrthJobContext context = new OrthJobContext(1, null, 0, 0, null, 0, 0, null);
        OrthJobContext.setOrthJobContext(context);
        context.setHandleMsg("Initial message");

        // When
        boolean result = OrthJobHelper.handleResult(200, null);

        // Then
        assertThat(result).isTrue();
        assertThat(context.getHandleCode()).isEqualTo(200);
        assertThat(context.getHandleMsg()).isEqualTo("Initial message"); // Should not change
    }

    // ==================== Integration Tests ====================

    @Test
    void testFullWorkflow_shouldWorkCorrectly() throws IOException {
        // Given
        Path logDir = tempDir.resolve("2024-01-15");
        Files.createDirectories(logDir);
        Path logFile = logDir.resolve("456.log");
        String logFileName = logFile.toString();

        OrthJobContext context =
                new OrthJobContext(
                        456,
                        "param123",
                        789L,
                        System.currentTimeMillis(),
                        logFileName,
                        0,
                        1,
                        123456789L);
        OrthJobContext.setOrthJobContext(context);

        // When - retrieve all info
        assertThat(OrthJobHelper.getJobId()).isEqualTo(456);
        assertThat(OrthJobHelper.getJobParam()).isEqualTo("param123");
        assertThat(OrthJobHelper.getLogId()).isEqualTo(789L);
        assertThat(OrthJobHelper.getShardIndex()).isEqualTo(0);
        assertThat(OrthJobHelper.getShardTotal()).isEqualTo(1);
        assertThat(OrthJobHelper.getScheduleTime()).isEqualTo(123456789L);

        // Log some messages
        assertThat(OrthJobHelper.log("Job started")).isTrue();
        assertThat(OrthJobHelper.log("Processing item: {}", 1)).isTrue();

        // Set result
        assertThat(OrthJobHelper.handleSuccess("Job completed successfully")).isTrue();

        // Verify
        assertThat(context.getHandleCode()).isEqualTo(OrthJobContext.HANDLE_CODE_SUCCESS);
        assertThat(context.getHandleMsg()).isEqualTo("Job completed successfully");

        // Verify log file
        assertThat(logFile).exists();
        String logContent = Files.readString(logFile);
        assertThat(logContent).contains("Job started");
        assertThat(logContent).contains("Processing item: 1");
    }
}
